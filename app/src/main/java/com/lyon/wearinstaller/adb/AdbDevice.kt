package com.lyon.wearinstaller.adb

import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbException as DadbException
import dadb.AdbKeyPair
import dadb.AdbStreamOpenException
import dadb.AdbStream
import dadb.Dadb
import dadb.SyncResult
import okio.source
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException

/** ADB 客户端入口：负责配对与建立到设备的连接。 */
class AdbClient(private val keyStore: AdbKeyStore) {

    /**
     * 无线调试「配对模式」：向配对地址提交配对码，把本机公钥登记到设备上。
     * 设备端会弹出「始终允许来自此电脑的调试」之类的确认框。
     */
    fun pair(host: String, port: Int, code: String) {
        AdbPairer.pair(host, port, code, keyStore)
    }

    /** 直连模式：连上调试地址并完成认证。 */
    fun open(host: String, port: Int): AdbDevice {
        AdbLog.d("连接 $host:$port")
        val dadb = try {
            Dadb.create(
                host = host,
                port = port,
                keyPair = keyStore.adbKeyPair(),
                connectTimeout = CONNECT_TIMEOUT_MS,
                socketTimeout = SOCKET_TIMEOUT_MS,
            )
        } catch (t: Throwable) {
            throw t.asAdbException(host, port)
        }
        return AdbDevice(dadb)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val SOCKET_TIMEOUT_MS = 60_000
    }
}

/** 一台已连接的设备。 */
class AdbDevice internal constructor(private val dadb: Dadb) : Closeable {
    @Volatile
    private var lastRemotePath: String? = null

    /** 读取设备型号等基础信息，用于界面上展示「连上了哪台设备」。 */
    fun probeProfile(): DeviceProfile {
        val result = shell(
            "getprop ro.product.model; getprop ro.build.version.release; " +
                "getprop ro.build.version.sdk; getprop ro.build.display.id"
        )
        val lines = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return DeviceProfile(
            model = lines.getOrNull(0) ?: result.output.trim().ifEmpty { "未知设备" },
            androidVersion = lines.getOrNull(1).orEmpty(),
            sdk = lines.getOrNull(2)?.toIntOrNull() ?: 0,
            buildId = lines.getOrNull(3).orEmpty(),
            banner = connectionString(),
        )
    }

    /** 执行 shell 命令，返回退出码与输出。 */
    fun shell(command: String): ShellResult {
        AdbLog.d("shell: $command")
        val response = try {
            dadb.shell(command)
        } catch (t: Throwable) {
            throw t.asAdbException(null, null)
        }
        return ShellResult(exitCode = response.exitCode, output = response.allOutput.trim())
    }

    /**
     * 完整安装流程：优先用 shell 通道通过 `cat > /data/local/tmp/...apk` 写入（绕过 sync 协议），失败时回退到 push + pm install。
     */
    fun install(
        localFile: File,
        huaweiWatchMode: Boolean,
        onPhase: (InstallPhase) -> Unit,
        onProgress: (Long, Long) -> Unit,
        cleanupPaths: List<String> = emptyList(),
    ): InstallOutcome {
        var disabledInstaller = false
        var remotePath: String? = null
        try {
            if (huaweiWatchMode) {
                onPhase(InstallPhase.DISABLING_INSTALLER)
                val disable = shell(DISABLE_INSTALLER_COMMAND)
                if (disable.exitCode == 0) disabledInstaller = true
            }

            val shellWriteResult = runCatching { installViaShellCat(localFile, onPhase, onProgress) }
            if (shellWriteResult.isSuccess) {
                val res = shellWriteResult.getOrThrow()
                if (res.remotePath != null) {
                    remotePath = res.remotePath
                    onPhase(InstallPhase.INSTALLING)
                    val result = shell("pm install -r -t -g \"$remotePath\"")
                    val output = result.output
                    val failure = INSTALL_FAILURE.find(output)
                    val success = output.contains("Success") && failure == null
                    if (success) {
                        return InstallOutcome(success = true, failureCode = null, message = output)
                    }
                    val code: String = failure?.value ?: inferFailureCode(output, result.exitCode) ?: ""
                    return InstallOutcome(
                        success = false,
                        failureCode = code,
                        message = output.ifEmpty { "pm install 未返回结果（退出码 ${result.exitCode}）" },
                    )
                }
            } else {
                AdbLog.d("shell cat 写入失败，回退到 push+install：${shellWriteResult.exceptionOrNull()?.message}")
            }
            onProgress(0, localFile.length())

            onPhase(InstallPhase.PUSHING)
            remotePath = "/data/local/tmp/wearinstaller_${System.currentTimeMillis()}.apk"
            push(localFile, remotePath, onProgress)

            onPhase(InstallPhase.INSTALLING)
            val result = shell("pm install -r -t -g \"$remotePath\"")
            val output = result.output
            val failure = INSTALL_FAILURE.find(output)
            val success = output.contains("Success") && failure == null

            return if (success) {
                InstallOutcome(success = true, failureCode = null, message = output)
            } else {
                val code: String = failure?.value ?: inferFailureCode(output, result.exitCode) ?: ""
                InstallOutcome(
                    success = false,
                    failureCode = code,
                    message = output.ifEmpty { "pm install 未返回结果（退出码 ${result.exitCode}）" },
                )
            }
        } finally {
            if (disabledInstaller) {
                onPhase(InstallPhase.ENABLING_INSTALLER)
                if (!runBounded(ENABLE_INSTALLER_COMMAND, RESTORE_TIMEOUT_MS)) {
                    AdbLog.e("未能恢复系统安装器，请手动执行：$ENABLE_INSTALLER_COMMAND")
                }
            }
            onPhase(InstallPhase.CLEANING)
            val targets = ((if (remotePath != null) listOf(remotePath) else emptyList()) + cleanupPaths)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ") { "\"$it\"" }
            if (targets.isNotBlank()) {
                runBounded("rm -f $targets", CLEANUP_TIMEOUT_MS)
            }
        }
    }

    private data class ShellWriteResult(val remotePath: String)

    private fun installViaShellCat(
        localFile: File,
        onPhase: (InstallPhase) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): ShellWriteResult {
        val total = localFile.length()
        if (total <= 0) throw AdbException("APK 文件为空", hint = AdbException.Hint.PROTOCOL)
        onPhase(InstallPhase.PUSHING)
        val remotePath = "/data/local/tmp/wearinstaller_${System.currentTimeMillis()}.apk"
        lastRemotePath = remotePath
        val command = "cat > \"$remotePath\""
        AdbLog.d("shell cat 写入：$command（$total 字节）")
        val stream = try {
            dadb.open("shell:$command")
        } catch (t: Throwable) {
            throw t.asAdbException(null, null)
        }
        try {
            localFile.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var sent: Long = 0
                val sink = stream.sink
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    sink.write(buf, 0, read)
                    sink.flush()
                    sent += read
                    onProgress(sent, total)
                }
                runCatching { sink.flush() }
            }
            runCatching { stream.close() }
            onProgress(total, total)
            return ShellWriteResult(remotePath)
        } catch (t: Throwable) {
            runCatching { stream.close() }
            throw t
        }
    }

    fun cancelInstall(remotePath: String? = null) {
        val path = remotePath ?: lastRemotePath
        runCatching {
            shell("pkill -9 -f 'pm install' 2>/dev/null; pkill -9 -f 'cat >' 2>/dev/null")
        }
        if (path != null) {
            runCatching { shell("rm -f \"$path\"") }
        }
    }

    private fun installViaStream(
        localFile: File,
        onPhase: (InstallPhase) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): InstallOutcome {
        val total = localFile.length()
        if (total <= 0) throw AdbException("APK 文件为空", hint = AdbException.Hint.PROTOCOL)
        onPhase(InstallPhase.PUSHING)
        val command = "pm install -S $total -r -t -g"
        AdbLog.d("流式安装：$command")
        val stream = try {
            dadb.open("shell:$command")
        } catch (t: Throwable) {
            throw t.asAdbException(null, null)
        }
        try {
            val output = StringBuilder()
            val outThread = Thread {
                try {
                    val source = stream.source
                    val buf = okio.Buffer()
                    while (true) {
                        val read = source.read(buf, 4096)
                        if (read <= 0) break
                        output.append(buf.readUtf8())
                        if (buf.size > 0) {
                            output.append(buf.readUtf8())
                        }
                    }
                } catch (e: Exception) {
                    AdbLog.d("流式输出读取结束：${e.message}")
                }
            }
            outThread.isDaemon = true
            outThread.start()

            localFile.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var sent: Long = 0
                val sink = stream.sink
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    sink.write(buf, 0, read)
                    sink.flush()
                    sent += read
                    onProgress(sent, total)
                }
                runCatching { sink.flush() }
            }
            runCatching { stream.close() }
            outThread.join(8000)
            val outStr = output.toString().trim()
            AdbLog.d("流式安装输出：$outStr")
            val failure = INSTALL_FAILURE.find(outStr)
            val success = outStr.contains("Success") && failure == null
            return if (success) {
                onPhase(InstallPhase.INSTALLING)
                InstallOutcome(success = true, failureCode = null, message = outStr)
            } else {
                val code: String = failure?.value ?: inferFailureCode(outStr, -1) ?: ""
                InstallOutcome(
                    success = false,
                    failureCode = code,
                    message = outStr.ifEmpty { "pm install 流式安装未返回结果" },
                )
            }
        } catch (t: Throwable) {
            runCatching { stream.close() }
            throw t
        }
    }

    /**
     * 在独立线程里执行一条 shell 命令并限时等待。
     *
     * 之所以不用协程超时：dadb 是阻塞式 socket 调用，只有阻塞在 socket 读上，
     * 协程的取消根本传不进去。这里用线程 + join 给一个硬上限。
     *
     * @return 是否在限时内正常结束
     */
    private fun runBounded(command: String, timeoutMs: Long): Boolean {
        var failure: Throwable? = null
        val worker = Thread {
            failure = runCatching { shell(command) }.exceptionOrNull()
        }
        worker.isDaemon = true
        worker.name = "adb-cleanup"
        worker.start()
        worker.join(timeoutMs)
        if (worker.isAlive) {
            AdbLog.e("命令超时（${timeoutMs}ms），放弃等待：$command")
            worker.interrupt()
            return false
        }
        failure?.let { AdbLog.e("命令失败：$command", it) }
        return failure == null
    }

    override fun close() {
        runCatching { dadb.close() }
    }

    /**
     * 把本地文件通过 `sync:` 服务推到设备上。
     *
     * 显式传 mode：dadb 的默认实现会用 `java.nio.file` 读权限位，那个 API 在 Android 上不存在。
     */
    private fun push(localFile: File, remotePath: String, onProgress: (Long, Long) -> Unit) {
        val total = localFile.length()
        AdbLog.d("push $remotePath（$total 字节）")
        val counting = CountingSource(localFile.source(), total, onProgress)
        val result = try {
            dadb.push(
                source = counting,
                remotePath = remotePath,
                mode = SYNC_FILE_MODE,
                lastModifiedMs = localFile.lastModified(),
            )
        } catch (t: Throwable) {
            throw t.asAdbException(null, null)
        }
        when (result) {
            is SyncResult.Failure -> throw AdbException(
                "传输失败：${result.reason}",
                hint = AdbException.Hint.PROTOCOL,
            )

            SyncResult.Success -> onProgress(total, total)
        }
    }

    private fun connectionString(): String = runCatching { dadb.toString() }.getOrDefault("")

    companion object {
        /** S_IFREG | 0644 */
        private const val SYNC_FILE_MODE = 33188

        private const val RESTORE_TIMEOUT_MS = 15_000L
        private const val CLEANUP_TIMEOUT_MS = 5_000L

        private val INSTALL_FAILURE = Regex("Failure\\s*\\[?([A-Z_]+)]?")

        /** 华为 WATCH 需要绕过系统安装器的安装检测。 */
        const val DISABLE_INSTALLER_COMMAND = "pm disable-user com.android.packageinstaller"
        const val ENABLE_INSTALLER_COMMAND = "pm enable com.android.packageinstaller"

        private fun inferFailureCode(output: String, exitCode: Int): String? {
            if (output.contains("Unknown option")) return "BAD_COMMAND"
            if (output.contains("Permission Denial") || output.contains("SecurityException")) {
                return "PERMISSION_DENIED"
            }
            if (exitCode != 0 && output.isBlank()) return "COMMAND_FAILED"
            return null
        }
    }
}

/** 统计已推送字节数，用于展示进度。 */
private class CountingSource(
    source: okio.Source,
    private val total: Long,
    private val onProgress: (Long, Long) -> Unit,
) : okio.ForwardingSource(source) {

    private var sent = 0L
    private var reads = 0
    private var lastLogged = -1

    /**
     * 必须自己限制每次读取的字节数：`okio.RealBufferedSource.read(sink, byteCount)`
     * 会一直读到 byteCount 满足为止，dadb 传进来的 byteCount 很大时一次就把整个文件读完了，
     * 进度会从 0 直接跳到 100%，中间的传输过程完全看不到。
     */
    override fun read(sink: okio.Buffer, byteCount: Long): Long {
        val read = super.read(sink, if (byteCount > READ_CHUNK) READ_CHUNK else byteCount)
        if (read > 0) {
            sent += read
            reads++
            val percent = if (total > 0) (sent * 100 / total).toInt() else 0
            if (reads % 5 == 0 || percent != lastLogged || read < READ_CHUNK) {
                lastLogged = percent
                AdbLog.d("push 进度 $percent%（$sent/$total，read $reads 次）")
            }
            onProgress(sent, total)
        } else {
            AdbLog.d("push 结束：read $reads 次，共 $sent/$total 字节")
        }
        return read
    }

    private companion object {
        /** 64KB：既能反映真实进度，又不会让回调太密集。 */
        private const val READ_CHUNK = 64L * 1024L
    }
}

/** 把 dadb 的异常翻译成带提示的 [AdbException]，供界面给出可执行的建议。 */
internal fun Throwable.asAdbException(host: String?, port: Int?): Throwable {
    if (this is AdbException) return this
    val target = if (host != null) "$host:$port" else "设备"
    return when (this) {
        is AdbAuthException -> AdbException(
            "调试授权未通过：设备拒绝了本机的调试密钥。请确认屏幕上的「允许 USB 调试吗」弹窗并勾选「始终允许」。",
            this,
            AdbException.Hint.UNAUTHORIZED,
        )

        is AdbStreamOpenException -> AdbException(
            "设备拒绝打开 $target 上的通道：${message.orEmpty()}",
            this,
            AdbException.Hint.PROTOCOL,
        )

        is AdbConnectException -> AdbException(
            "无法连接 $target：${rootMessage()}",
            this,
            AdbException.Hint.CONNECTION,
        )

        is DadbException -> AdbException(
            "与 $target 的 ADB 会话出错：${rootMessage()}",
            this,
            AdbException.Hint.PROTOCOL,
        )

        is IOException -> AdbException(
            "与 $target 的通信中断：${rootMessage()}",
            this,
            AdbException.Hint.CONNECTION,
        )

        else -> this
    }
}

private fun Throwable.rootMessage(): String {
    var cause: Throwable? = this
    var message = this.message
    while (cause != null) {
        cause.message?.let { message = it }
        cause = cause.cause?.takeIf { it !== this }
    }
    return message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}

data class ShellResult(val exitCode: Int, val output: String)

data class DeviceProfile(
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val buildId: String,
    val banner: String,
)

enum class InstallPhase {
    PUSHING,
    DISABLING_INSTALLER,
    INSTALLING,
    ENABLING_INSTALLER,
    CLEANING,
}

data class InstallOutcome(
    val success: Boolean,
    val failureCode: String?,
    val message: String,
)