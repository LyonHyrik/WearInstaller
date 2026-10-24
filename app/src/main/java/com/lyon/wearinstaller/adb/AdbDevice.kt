package com.lyon.wearinstaller.adb

import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbException as DadbException
import dadb.AdbKeyPair
import dadb.AdbStreamOpenException
import dadb.Dadb
import dadb.SyncResult
import okio.source
import java.io.Closeable
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
     * 完整安装流程：推送 → （华为 WATCH 模式）停用系统安装器 → pm install → 还原。
     */
    fun install(
        localFile: File,
        huaweiWatchMode: Boolean,
        onPhase: (InstallPhase) -> Unit,
        onProgress: (Long, Long) -> Unit,
        cleanupPaths: List<String> = emptyList(),
    ): InstallOutcome {
        val remotePath = "/data/local/tmp/wearinstaller_${System.currentTimeMillis()}.apk"
        var disabledInstaller = false
        try {
            onPhase(InstallPhase.PUSHING)
            push(localFile, remotePath, onProgress)

            if (huaweiWatchMode) {
                onPhase(InstallPhase.DISABLING_INSTALLER)
                val disable = shell(DISABLE_INSTALLER_COMMAND)
                if (disable.exitCode == 0) disabledInstaller = true
            }

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
            // 收尾动作一律限时：连接卡住时不能让界面停在「收尾中」。
            // 恢复系统安装器有副作用，给足时间；删临时文件失败无所谓。
            if (disabledInstaller) {
                onPhase(InstallPhase.ENABLING_INSTALLER)
                if (!runBounded(ENABLE_INSTALLER_COMMAND, RESTORE_TIMEOUT_MS)) {
                    AdbLog.e("未能恢复系统安装器，请手动执行：$ENABLE_INSTALLER_COMMAND")
                }
            }
            onPhase(InstallPhase.CLEANING)
            // 临时推送文件 + 调用方要求删除的原始 APK（如 /sdcard/Download 里的那个）
            val targets = (listOf(remotePath) + cleanupPaths)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ") { "\"$it\"" }
            runBounded("rm -f $targets", CLEANUP_TIMEOUT_MS)
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