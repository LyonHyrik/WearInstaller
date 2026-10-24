package com.lyon.wearinstaller.adb

import android.util.Log

/** 统一的调试日志前缀，方便在 logcat 里过滤整个 ADB 流程。 */
internal object AdbLog {
    const val TAG = "WearInstallerAdb"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
    }
}
