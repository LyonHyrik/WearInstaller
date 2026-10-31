package com.lyon.wearinstaller.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.lyon.wearinstaller.adb.AdbClient
import com.lyon.wearinstaller.adb.AdbException
import com.lyon.wearinstaller.adb.AdbKeyStore
import com.lyon.wearinstaller.adb.AdbLog
import com.lyon.wearinstaller.adb.DeviceProfile
import com.lyon.wearinstaller.adb.InstallPhase
import com.lyon.wearinstaller.apk.ApkInfo
import com.lyon.wearinstaller.apk.ApkInspector
import com.lyon.wearinstaller.intent.IntentResolveException
import com.lyon.wearinstaller.intent.IntentTargetResolver
import com.lyon.wearinstaller.intent.InstallTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Online(val profile: DeviceProfile) : ConnectionStatus
}

data class PendingInstall(val info: ApkInfo, val target: InstallTarget)

sealed interface JobState {
    data object Idle : JobState
    data class Working(
        val phase: InstallPhase,
        val progress: Float?,
        val detail: String,
        val cancelling: Boolean = false,
    ) : JobState
    data class Succeeded(val label: String, val packageName: String) : JobState
    data class Failed(val code: String?, val detail: String, val needsDevOptions: Boolean) : JobState
}

sealed interface UiMessage {
    data class Text(val value: String) : UiMessage
    data object InvalidApk : UiMessage
    data object NoFilePicker : UiMessage
    data object PairCodeMissing : UiMessage
    data object PairFailed : UiMessage
    data object AddressInvalid : UiMessage
}

data class UiState(
    val connection: ConnectionStatus = ConnectionStatus.Idle,
    val lastError: String? = null,
    val pending: PendingInstall? = null,
    val job: JobState = JobState.Idle,
    val preparing: Boolean = false,
    val message: UiMessage? = null,
    val needsDevOptions: Boolean = false,
    val externalLaunch: Boolean = false,
)

/**
 * 应用层状态机：把 Intent 输入、APK 准备、连接、安装串起来。
 *
 * 之所以放在 Application 作用域而不是 ViewModel 里，是因为安装过程要跨越
 * 屏幕旋转、熄屏、甚至界面退出。
 */
class InstallCoordinator(
    private val context: Context,
    val settings: SettingsRepository,
    val keyStore: AdbKeyStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = AdbClient(keyStore)
    private val incomingDir = File(context.cacheDir, "incoming").apply { mkdirs() }

    init {
        // 进程刚起来时不可能有正在等待安装的 APK，直接清掉上次残留
        purgeIncoming()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun update(transform: (UiState) -> UiState) {
        val next = transform(_state.value)
        AdbLog.i("state -> ${describe(next)}")
        _state.value = next
    }

    private fun describe(state: UiState): String {
        val connection = when (val c = state.connection) {
            ConnectionStatus.Idle -> "idle"
            ConnectionStatus.Connecting -> "connecting"
            is ConnectionStatus.Online -> "online(${c.profile.model})"
        }
        val job = when (val j = state.job) {
            JobState.Idle -> "idle"
            is JobState.Working -> "working(${j.phase}, ${j.progress ?: -1f})"
            is JobState.Succeeded -> "succeeded(${j.packageName})"
            is JobState.Failed -> "failed(${j.code})"
        }
        return "conn=$connection job=$job preparing=${state.preparing} lastError=${state.lastError} message=${state.message}"
    }

    private var job: Job? = null
    private var currentDevice: com.lyon.wearinstaller.adb.AdbDevice? = null
    private var devOptionsOpened: Boolean = false
    @Volatile private var userCancelled = false
    // 每次开始安装都自增；取消/重置后旧协程的回调与收尾都被判定为过期，不再写状态
    @Volatile private var jobGeneration = 0

    // ---------------- 输入 ----------------

    fun handleIntent(intent: Intent?) {
        val target = try {
            IntentTargetResolver.resolve(intent)
        } catch (e: IntentResolveException) {
            showMessage(UiMessage.Text(e.message.orEmpty()))
            return
        } ?: return
        when (target) {
            is InstallTarget.LocalFile -> prepareFromUri(target.uri, target.describe, fromIntent = true)
        }
    }

    fun prepareFromUri(uri: Uri, label: String? = null, fromIntent: Boolean = false) {
        if (_state.value.preparing) return
        update { it.copy(preparing = true, message = null, externalLaunch = fromIntent) }
        scope.launch {
            val result = runCatching {
                val display = queryDisplayName(uri).takeIf { it.isNotBlank() }
                    ?: label?.takeIf { it.isNotBlank() }
                    ?: "install.apk"
                val file = copyToCache(uri, display)
                val info = ApkInspector.inspect(context, file, display)
                info to localTarget(uri, display)
            }
            val value = _state.value
            _state.value = value.copy(preparing = false)
            result
                .onSuccess { (info, target) ->
                    update { it.copy(pending = PendingInstall(info, target)) }
                }
                .onFailure { error ->
                    AdbLog.e("读取 APK 失败：${error.message ?: error.toString()}", error)
                    update { it.copy(message = errorMessageFor(error)) }
                }
        }
    }

    fun clearPending() {
        update { it.copy(pending = null) }
    }

    // ---------------- 连接 ----------------

    fun testConnection() {
        if (_state.value.connection is ConnectionStatus.Connecting) return
        // 不把状态切到 Connecting，避免 UI 闪烁；静默检测
        scope.launch {
            try {
                val device = withTimeout(CONNECT_TIMEOUT_MS) { openDevice() }
                val profile = withTimeout(SHELL_TIMEOUT_MS) { device.probeProfile() }
                device.close()
                update { it.copy(connection = ConnectionStatus.Online(profile), lastError = null) }
            } catch (e: Throwable) {
                update { it.copy(connection = ConnectionStatus.Idle) }
                if (e.isUserCancellation()) return@launch
                if (!devOptionsOpened) {
                    devOptionsOpened = true
                    update { it.copy(needsDevOptions = true) }
                }
                reportFailure(e)
            }
        }
    }

    /** 单独执行配对（设置页「立即配对」按钮）。 */
    fun pairNow(onDone: (Boolean) -> Unit) {
        val config = settings.current
        val endpoint = config.pairEndpoint
        if (endpoint == null) {
            showMessage(UiMessage.AddressInvalid)
            onDone(false)
            return
        }
        if (config.pairCode.isBlank()) {
            showMessage(UiMessage.PairCodeMissing)
            onDone(false)
            return
        }
        scope.launch {
            val ok = runCatching {
                client.pair(endpoint.host, endpoint.port, config.pairCode.trim())
            }.isSuccess
            if (!ok) showMessage(UiMessage.PairFailed)
            onDone(ok)
        }
    }

    private suspend fun openDevice() =
        withContext(Dispatchers.IO) {
            val config = settings.current
            val endpoint = config.debugEndpoint ?: throw AdbException(
                "调试地址格式不正确",
                hint = AdbException.Hint.PROTOCOL,
            )
            client.open(endpoint.host, endpoint.port)
        }

    // ---------------- 安装 ----------------

    fun startInstall() {
        val pending = _state.value.pending ?: return
        if (job?.isActive == true) return
        userCancelled = false
        val generation = ++jobGeneration
        update { it.copy(job = JobState.Working(InstallPhase.PUSHING, 0f, pending.info.label)) }
        job = scope.launch {
            var device: com.lyon.wearinstaller.adb.AdbDevice? = null
            // 取消或重置后 generation 会变，旧协程的任何回写都必须丢弃
            fun stale() = generation != jobGeneration
            try {
                device = withTimeout(CONNECT_TIMEOUT_MS) { openDevice() }
                currentDevice = device
                val profile = withTimeout(SHELL_TIMEOUT_MS) { device.probeProfile() }
                if (stale()) return@launch
                update { it.copy(connection = ConnectionStatus.Online(profile), lastError = null) }

                val deleteSource = settings.current.deleteSourceApk
                // 开关打开时才把来源文件交给 adb rm；应用自己的缓存副本无条件删除。
                val sourceToDelete = if (deleteSource) sourceDeleteCandidates(pending) else emptyList()

                val outcome = withTimeout(INSTALL_TIMEOUT_MS) {
                    device.install(
                        localFile = pending.info.file,
                        huaweiWatchMode = settings.current.huaweiWatchMode,
                        cleanupPaths = sourceToDelete,
                        onPhase = { phase ->
                            if (!stale()) {
                                update { it.copy(
                                    job = JobState.Working(phase, null, pending.info.label),
                                ) }
                            }
                        },
                        onProgress = { sent, total ->
                            val ratio = if (total > 0) sent.toFloat() / total else 0f
                            val current = _state.value.job
                            // 只在整数百分比变化时写状态：一次 push 的回调非常密集，
                            // 全量写会把重组挤爆，进度环反而看起来不动。
                            val percent = (ratio.coerceIn(0f, 1f) * 100).toInt()
                            if (!stale() &&
                                current is JobState.Working &&
                                !current.cancelling &&
                                current.phase == InstallPhase.PUSHING &&
                                current.progress?.let { (it * 100).toInt() } != percent
                            ) {
                                update { it.copy(
                                    job = JobState.Working(InstallPhase.PUSHING, ratio, pending.info.label),
                                ) }
                            }
                        },
                    )
                }
                device.close()
                device = null
                currentDevice = null

                if (stale()) return@launch

                if (userCancelled) {
                    finishCancelled()
                    return@launch
                }

                if (outcome.success) {
                    // 安装成功就清掉本地缓存副本（无条件），顺带把之前遗留的一起清掉
                    purgeIncoming()
                    if (deleteSource) deleteSourceDocument(pending)
                    update { it.copy(
                        job = JobState.Succeeded(pending.info.label, pending.info.packageName),
                    ) }
                } else {
                    if (userCancelled) {
                        finishCancelled()
                        return@launch
                    }
                    update { it.copy(
                        job = JobState.Failed(outcome.failureCode, outcome.message, false),
                    ) }
                }
            } catch (e: Throwable) {
                runCatching { device?.close() }
                currentDevice = null
                if (stale()) return@launch
                if (userCancelled || e.isUserCancellation()) {
                    finishCancelled()
                    return@launch
                }
                reportFailure(e, inInstall = true, fallbackTitle = pending.info.label)
            }
        }
    }

    fun cancelInstall() {
        val current = _state.value.job
        if (current !is JobState.Working || current.cancelling) return
        userCancelled = true
        // 旧协程从此刻起不允许再写状态（避免把“取消中”顶回“安装中”）
        val generation = ++jobGeneration
        val label = _state.value.pending?.info?.label ?: current.detail

        if (current.phase == InstallPhase.PUSHING) {
            // 还没开始安装：取消只放弃这次传输，直接回到确认页，不再显示“已取消安装”
            job?.cancel()
            job = null
            runCatching { currentDevice?.cancelInstall() }
            runCatching { currentDevice?.close() }
            currentDevice = null
            // 缓存副本要留着，回到确认页后还能重新安装
            update { it.copy(job = JobState.Idle, externalLaunch = false) }
            return
        }

        // 已经开始安装：先显示“取消中”，等安装流程收尾后再落到“已取消安装”
        update { it.copy(
            job = JobState.Working(current.phase, current.progress, label, cancelling = true),
        ) }
        job?.cancel()
        job = null
        runCatching { currentDevice?.cancelInstall() }
        runCatching { currentDevice?.close() }
        currentDevice = null
        // 取消后没有保留缓存的必要，否则 cache/incoming 会越攒越大
        purgeIncoming()
        scope.launch {
            delay(CANCEL_SETTLE_MS)
            if (generation == jobGeneration) finishCancelled()
        }
    }

    /** 取消流程的唯一落点：只从“取消中”切到“已取消安装”，避免重复写入。 */
    private fun finishCancelled() {
        val state = _state.value
        val current = state.job
        if (current !is JobState.Working || !current.cancelling) return
        val label = state.pending?.info?.label ?: current.detail
        update { it.copy(
            job = JobState.Failed("CANCELLED", label, false),
            externalLaunch = false,
        ) }
    }

    fun resetJob() {
        jobGeneration++
        update { it.copy(job = JobState.Idle, pending = null, externalLaunch = false, needsDevOptions = false) }
    }

    fun consumeDevOptionsRequest() {
        update { it.copy(needsDevOptions = false) }
    }

    fun showMessage(message: UiMessage) {
        update { it.copy(message = message) }
    }

    fun consumeMessage() {
        update { it.copy(message = null) }
    }

    /**
     * `cancelInstall()` 取消协程后，阻塞调用返回时抛出的 `CancellationException`
     * （文案是 "StandaloneCoroutine was cancelled"）属于正常取消，不该当成失败弹给用户；
     * 但 `withTimeout` 抛出的 `TimeoutCancellationException` 是真的超时，仍然要上报。
     */
    private fun Throwable.isUserCancellation(): Boolean =
        this is kotlinx.coroutines.CancellationException &&
            this !is kotlinx.coroutines.TimeoutCancellationException

    // ---------------- 内部 ----------------

    /**
     * 连接类失败只把错误写回首页状态卡；
     * 只有安装过程中失败才会切到失败页，避免「测试连接」失败被当成安装失败。
     */
    private fun reportFailure(
        error: Throwable,
        inInstall: Boolean = false,
        fallbackTitle: String? = null,
    ) {
        val adb = error as? AdbException
        val needsDevOptions = adb?.hint == AdbException.Hint.WIRELESS_DEBUG_OFF
        val detail = (fallbackTitle?.let { "$it：" } ?: "") + (error.message ?: error.toString())
        AdbLog.e("失败：$detail", error)
        update { current ->
            current.copy(
                connection = ConnectionStatus.Idle,
                lastError = detail,
                job = if (inInstall) {
                    JobState.Failed(adb?.hint?.name, detail, needsDevOptions)
                } else {
                    current.job
                },
                needsDevOptions = needsDevOptions || current.needsDevOptions,
            )
        }
    }

    private fun errorMessageFor(error: Throwable): UiMessage = when (error) {
        is com.lyon.wearinstaller.apk.ApkParseException -> UiMessage.InvalidApk
        is java.io.FileNotFoundException -> UiMessage.Text("无法读取所选文件")
        else -> UiMessage.Text(error.message.orEmpty())
    }

    /** 把 content:// 或 file:// 的 URI 复制到缓存目录，后续所有操作只认本地文件。 */
    private suspend fun copyToCache(uri: Uri, displayName: String): File =
        withContext(Dispatchers.IO) {
            val file = File(incomingDir, safeName(displayName))
            // ContentResolver 只认 content:// 与 android.resource://，
            // 其他应用也可能直接发 file://，这种情况直接读文件。
            val opened = if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path?.let { File(it).inputStream() }
            } else {
                runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            } ?: throw java.io.FileNotFoundException("无法读取所选文件")
            opened.use { input ->
                file.outputStream().buffered().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            file
        }

    /** 清空缓存目录：安装成功、取消、以及进程启动时都会调用，避免 APK 副本越攒越多。 */
    private fun purgeIncoming() {
        runCatching {
            incomingDir.listFiles()?.forEach { file ->
                val ok = file.delete()
                AdbLog.i("清理缓存 ${file.name} -> $ok")
            }
        }
    }

/**
     * 待安装 APK 的原始路径候选，用来交给 `adb rm`。
 *
 * 系统选择器给的是 content://，拿不到路径，所以要多方尝试：
 *  1. file:// 直接就是路径
 *  2. SAF 文档 ID 里的 `raw:` 前缀是真实路径
 *  3. MediaStore 的 `_data` 列（部分机型/版本仍会返回）
 *  4. 兜底用文件名去共享存储常见目录里找
 */
    private fun sourceDeleteCandidates(pending: PendingInstall): List<String> {
        val uri = (pending.target as? InstallTarget.LocalFile)?.uri ?: return emptyList()
        val out = LinkedHashSet<String>()
        if (uri.scheme.equals("file", ignoreCase = true)) {
            uri.path?.let { out += it }
        }
        if (uri.scheme.equals("content", ignoreCase = true)) {
            runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?.takeIf { it.startsWith("raw:") }
                ?.removePrefix("raw:")
                ?.let { out += it }
            runCatching {
                context.contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.let { out += it }
                }
            }
        }
        val name = pending.info.label.takeIf { it.endsWith(".apk", ignoreCase = true) }
        if (name != null) {
            out += "/sdcard/Download/$name"
            out += "/storage/emulated/0/Download/$name"
        }
        AdbLog.i("待删除的来源路径：$out")
        return out.filter { it.endsWith(".apk", ignoreCase = true) }.toList()
    }

    /** 系统选择器给的 content:// 无法走 adb rm，尝试直接让文档提供方删掉。 */
    private fun deleteSourceDocument(pending: PendingInstall) {
        val uri = (pending.target as? InstallTarget.LocalFile)?.uri ?: return
        if (!uri.scheme.equals("content", ignoreCase = true)) return
        val ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .onFailure { AdbLog.i("删除来源文档失败：${it.message}") }
            .isSuccess
        AdbLog.i("删除来源文档 $uri -> $ok")
    }

    private fun queryDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "install.apk"
        }
        if (uri.scheme != "content") return uri.lastPathSegment ?: "install.apk"
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "install.apk"
    }

    private fun safeName(raw: String): String {
        // 去掉百分号编码（来自 URI 片段）和路径分隔符，只留可读文件名
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        val cleaned = decoded.substringAfterLast('/').substringAfterLast('\\').trim()
            .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_")
            .take(80)
            .ifEmpty { "install.apk" }
        return if (cleaned.endsWith(".apk", true)) cleaned else "$cleaned.apk"
    }

    private fun localTarget(uri: Uri, label: String): InstallTarget = InstallTarget.LocalFile(uri, label)

    private companion object {
        /** 任何一步都不允许无限等待，否则界面会一直停在「连接中」。 */
        const val CONNECT_TIMEOUT_MS = 45_000L
        const val SHELL_TIMEOUT_MS = 30_000L
        const val INSTALL_TIMEOUT_MS = 600_000L
        /** 「取消中」至少停留这么久，让用户看到取消已经生效，再切到「已取消安装」。 */
        const val CANCEL_SETTLE_MS = 600L
    }
}