package com.lyon.wearinstaller.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

data class ApkInfo(
    val file: File,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val icon: ImageBitmap?,
    val debuggable: Boolean,
    val sourceLabel: String,
) {
    val readableSize: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / 1024.0 / 1024.0)
            sizeBytes >= 1024L -> "%.0f KB".format(sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

object ApkInspector {

    fun inspect(context: Context, file: File, sourceLabel: String): ApkInfo {
        val packageManager = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        } catch (_: Throwable) {
        }
        if (packageInfo == null) {
            try {
                packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } catch (_: Throwable) {
            }
        }

        if (packageInfo == null) {
            throw ApkParseException("无法解析安装包信息")
        }

        val appInfo = packageInfo.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
        }

        var label = sourceLabel.replace(".apk", "", ignoreCase = true)
        if (appInfo != null) {
            val loaded = runCatching { appInfo.loadLabel(packageManager).toString() }.getOrNull()
            if (!loaded.isNullOrBlank()) {
                label = loaded
            }
        } else if (!packageInfo.packageName.isNullOrBlank()) {
            label = packageInfo.packageName
        }

        var icon: ImageBitmap? = null
        if (appInfo != null) {
            val drawable = runCatching { appInfo.loadIcon(packageManager) }.getOrNull()
            if (drawable != null) {
                icon = runCatching { loadIcon(context, drawable) }.getOrNull()
            }
        }

        return ApkInfo(
            file = file,
            packageName = packageInfo.packageName.orEmpty(),
            label = label,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            minSdk = appInfo?.minSdkVersion ?: 0,
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            sizeBytes = file.length(),
            icon = icon,
            debuggable = (appInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
            sourceLabel = sourceLabel,
        )
    }

    private fun loadIcon(context: Context, drawable: Drawable): ImageBitmap? {
        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let { return it.asImageBitmap() }
        }
        val size = 128.dpToPx(context)
        return runCatching {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.recoverCatching {
            drawable.toBitmap(size, size).asImageBitmap()
        }.getOrNull()
    }

    private fun Int.dpToPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (this * density).toInt().coerceIn(48, 256)
    }
}

class ApkParseException(message: String) : Exception(message)
