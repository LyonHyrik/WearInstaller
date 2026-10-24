package com.lyon.wearinstaller.intent

import android.content.Intent
import android.net.Uri
import java.io.File

/** 外部传入的目标：只接受本机可读的 APK 文件（其他应用通过 Intent 分享/打开跳转到本应用）。 */
sealed interface InstallTarget {
    data class LocalFile(val uri: Uri, val displayName: String) : InstallTarget

    val describe: String
        get() = when (this) {
            is LocalFile -> displayName
        }
}

class IntentResolveException(message: String) : Exception(message)

/**
 * 自己识别传入的 Intent：把其他应用传来的 URI 还原成本地 APK。
 *
 * 支持的入口：
 *  - ACTION_VIEW / ACTION_INSTALL_PACKAGE 的 data Uri
 *  - ACTION_SEND / ACTION_SEND_MULTIPLE 的 EXTRA_STREAM（含 ClipData 兜底）
 *  - ACTION_SEND 的 EXTRA_TEXT（仅本地路径，网络地址一律拒绝）
 *
 * 注意：本应用不做网络下载，所以 http/https 会被明确拒绝而不是静默变成别的行为。
 */
object IntentTargetResolver {

    private val NETWORK_SCHEMES = setOf("http", "https", "ftp", "ftps")

    fun resolve(intent: Intent?): InstallTarget? {
        if (intent == null) return null

        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) return fromUri(stream)
                clipDataUri(intent)?.let { return fromUri(it) }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) return fromText(text)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!streams.isNullOrEmpty()) return fromUri(streams.first())
                clipDataUri(intent)?.let { return fromUri(it) }
            }

            Intent.ACTION_VIEW -> {
                intent.data?.let { return fromUri(it) }
            }

            // 系统安装器的入口，虽然已废弃但很多「用 XX 打开」仍然在用
            @Suppress("DEPRECATION")
            Intent.ACTION_INSTALL_PACKAGE -> {
                intent.data?.let { return fromUri(it) }
            }

            null -> {
                intent.data?.let { return fromUri(it) }
            }
        }
        return null
    }

    private fun clipDataUri(intent: Intent): Uri? {
        val clip = intent.clipData ?: return null
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let { return it }
        }
        return null
    }

    private fun fromUri(uri: Uri): InstallTarget {
        val scheme = uri.scheme?.lowercase()
        if (scheme in NETWORK_SCHEMES) {
            throw IntentResolveException(
                "本应用只安装本地 APK，不下载网络地址。请让发送方选择「文件」方式分享。",
            )
        }
        return when (scheme) {
            "content", "file", "android.resource" -> InstallTarget.LocalFile(uri, fileNameOf(uri))
            null -> throw IntentResolveException("无法识别的地址：$uri")
            else -> throw IntentResolveException("不支持的地址类型：$scheme")
        }
    }

    private fun fromText(text: String): InstallTarget {
        val trimmed = text.trim()
        val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme in NETWORK_SCHEMES) {
            throw IntentResolveException("本应用只安装本地 APK，不下载网络地址。")
        }
        if (trimmed.contains("://")) return fromUri(Uri.parse(trimmed))
        if (trimmed.endsWith(".apk", ignoreCase = true)) {
            val name = trimmed.substringAfterLast('/').substringAfterLast('\\')
            return InstallTarget.LocalFile(Uri.fromFile(File(trimmed)), name)
        }
        throw IntentResolveException("无法识别的内容：$trimmed")
    }

    private fun fileNameOf(uri: Uri): String {
        val last = uri.lastPathSegment
        return when {
            !last.isNullOrBlank() -> last.substringAfterLast('/')
            uri.scheme == "file" -> uri.path?.substringAfterLast('/').orEmpty()
            else -> "install.apk"
        }.ifEmpty { "install.apk" }
    }
}
