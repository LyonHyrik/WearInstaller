package com.lyon.wearinstaller.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 无线调试的两种连接方式。 */
enum class LinkMode {
    /** 直接用调试地址连接（本机固定调试端口时用这个）。 */
    DIRECT,

    /** 先用配对地址 + 配对码登记公钥，再连接调试地址（Android 11+ 随机端口时用这个）。 */
    PAIRING,
}

/**
 * 应用主题色。
 *
 * `DYNAMIC` 跟随表盘动态取色（Wear OS 3+），其余是 Google 自家的品牌强调色：
 * 蓝 #4285F4、红 #EA4335、黄 #FBBC04、绿 #34A853。
 */
enum class AppAccent {
    GOOGLE_BLUE,
    DYNAMIC,
    GOOGLE_RED,
    GOOGLE_YELLOW,
    GOOGLE_GREEN,
}

data class AppSettings(
    val linkMode: LinkMode = LinkMode.DIRECT,
    val debugAddress: String = DEFAULT_DEBUG_ADDRESS,
    val pairAddress: String = "",
    val pairCode: String = "",
    val deleteSourceApk: Boolean = false,
    val huaweiWatchMode: Boolean = false,
    val autoOpenDevOptions: Boolean = false,
    val accent: AppAccent = AppAccent.GOOGLE_BLUE,
) {
    val debugEndpoint: Endpoint? get() = Endpoint.parse(debugAddress, DEFAULT_DEBUG_PORT)
    val pairEndpoint: Endpoint? get() = Endpoint.parse(pairAddress, DEFAULT_DEBUG_PORT)
}

data class Endpoint(val host: String, val port: Int) {
    val display: String get() = "$host:$port"

    companion object {
        fun parse(raw: String, defaultPort: Int): Endpoint? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val normalized = if (text.startsWith("[")) {
                text // [::1]:5555
            } else {
                text.replaceFirst("：", ":")
            }
            val separator = normalized.lastIndexOf(':')
            if (separator <= 0) {
                return Endpoint(normalized, defaultPort).takeIf { isHostLike(normalized) }
            }
            val host = normalized.substring(0, separator).trim().removePrefix("[").removeSuffix("]")
            val port = normalized.substring(separator + 1).trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return Endpoint(host, port)
        }

        private fun isHostLike(value: String): Boolean =
            value.isNotEmpty() && !value.contains(' ') && value != "localhost:"
    }
}

const val DEFAULT_DEBUG_ADDRESS = "localhost:5555"
const val DEFAULT_DEBUG_PORT = 5555

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wearinstaller_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_LINK_MODE, next.linkMode.name)
            .putString(KEY_DEBUG_ADDRESS, next.debugAddress)
            .putString(KEY_PAIR_ADDRESS, next.pairAddress)
            .putBoolean(KEY_DELETE_SOURCE_APK, next.deleteSourceApk)
            .putBoolean(KEY_HUAWEI, next.huaweiWatchMode)
            .putBoolean(KEY_AUTO_DEV_OPTIONS, next.autoOpenDevOptions)
            .putString(KEY_ACCENT, next.accent.name)
            .apply()
    }

    /** 配对码是临时有效的，只放在内存里。 */
    fun setPairCode(code: String) {
        _settings.value = _settings.value.copy(pairCode = code)
    }

    private fun load(): AppSettings {
        val stored = AppSettings(
            linkMode = runCatching {
                LinkMode.valueOf(prefs.getString(KEY_LINK_MODE, LinkMode.DIRECT.name)!!)
            }.getOrDefault(LinkMode.DIRECT),
            debugAddress = prefs.getString(KEY_DEBUG_ADDRESS, DEFAULT_DEBUG_ADDRESS) ?: DEFAULT_DEBUG_ADDRESS,
            pairAddress = prefs.getString(KEY_PAIR_ADDRESS, "") ?: "",
            deleteSourceApk = prefs.getBoolean(KEY_DELETE_SOURCE_APK, false),
            huaweiWatchMode = prefs.getBoolean(KEY_HUAWEI, false),
            autoOpenDevOptions = prefs.getBoolean(KEY_AUTO_DEV_OPTIONS, false),
            accent = runCatching {
                AppAccent.valueOf(prefs.getString(KEY_ACCENT, AppAccent.GOOGLE_BLUE.name)!!)
            }.getOrDefault(AppAccent.GOOGLE_BLUE),
        )
        return stored.copy(debugAddress = stored.debugAddress.ifBlank { DEFAULT_DEBUG_ADDRESS })
    }

    private companion object {
        const val KEY_LINK_MODE = "link_mode"
        const val KEY_DEBUG_ADDRESS = "debug_address"
        const val KEY_PAIR_ADDRESS = "pair_address"
        const val KEY_DELETE_SOURCE_APK = "delete_source_apk"
        const val KEY_HUAWEI = "huawei_watch_mode"
        const val KEY_AUTO_DEV_OPTIONS = "auto_open_dev_options"
        const val KEY_ACCENT = "accent"
    }
}