package com.lyon.wearinstaller

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lyon.wearinstaller.ui.WearInstallerRoot
import com.lyon.wearinstaller.ui.theme.WearInstallerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incoming = MutableStateFlow<Intent?>(null)

    companion object {
        @Volatile
        var currentScrollableState: ScrollableState? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launch = intent
        incoming.value = launch

        val container = (application as WearInstallerApp).container
        val versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull()
            .orEmpty()

        setContent {
            val settings by container.settings.settings.collectAsState()
            WearInstallerTheme(accent = settings.accent) {
                val intent by incoming.collectAsState()
                WearInstallerRoot(
                    container = container,
                    incomingIntent = intent,
                    versionName = versionName,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = intent
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d("WearInstaller", "dispatchKeyEvent keyCode=${event.keyCode}")
            val container = (application as WearInstallerApp).container
            val state = container.coordinator.state.value
            val job = state.job
            when (job) {
                is com.lyon.wearinstaller.data.JobState.Working -> {
                    container.coordinator.cancelInstall()
                    return true
                }
                is com.lyon.wearinstaller.data.JobState.Succeeded -> {
                    container.coordinator.resetJob()
                    if (state.externalLaunch) finish()
                    return true
                }
                is com.lyon.wearinstaller.data.JobState.Failed -> {
                    container.coordinator.resetJob()
                    // 已取消页按钮是“返回”回主页；失败页按钮与成功页一致，外部打开则退出应用
                    if (job.code != "CANCELLED" && state.externalLaunch) finish()
                    return true
                }
                com.lyon.wearinstaller.data.JobState.Idle -> {
                    if (state.pending != null) {
                        container.coordinator.startInstall()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        Log.d("WearInstaller", "onGenericMotionEvent action=${event?.action}")
        val handled = super.onGenericMotionEvent(event)
        if (!handled && event != null) {
            val scrollable = currentScrollableState
            if (scrollable != null) {
                val axisVal = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                val delta = axisVal * 5000f
                if (delta != 0f) {
                    val consumed = scrollable.dispatchRawDelta(delta)
                    Log.d("WearInstaller", "manual scroll axis=$axisVal delta=$delta consumed=$consumed hash=${scrollable.hashCode()}")
                    return true
                }
            } else {
                Log.d("WearInstaller", "manual scroll: no scrollable state")
            }
        }
        return handled
    }
}
