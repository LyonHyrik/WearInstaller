package com.lyon.wearinstaller.ui.screens

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.adb.InstallPhase
import com.lyon.wearinstaller.apk.ApkInfo
import com.lyon.wearinstaller.data.JobState
import com.lyon.wearinstaller.ui.components.InstallCircleButton
import com.lyon.wearinstaller.ui.components.InstallPageBody

private const val RING_START_ANGLE = 270f

@Composable
fun JobScreen(
    job: JobState,
    info: ApkInfo?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onBackHome: () -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val wakeLock = remember {
        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WearInstaller:install",
        )
    }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(job) {
        val window = (context as? Activity)?.window
        if (job is JobState.Working) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!wakeLock.isHeld) {
                runCatching { wakeLock.acquire(10 * 60 * 1000L) }
            }
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (wakeLock.isHeld) {
                runCatching { wakeLock.release() }
            }
        }
        runCatching { focusRequester.requestFocus() }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (wakeLock.isHeld) {
                runCatching { wakeLock.release() }
            }
        }
    }

    val isCancelled = job is JobState.Failed && job.code == "CANCELLED"

    ScreenScaffold { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (job) {
                            is JobState.Working -> {
                                onCancel()
                                true
                            }
                            is JobState.Succeeded -> {
                                onExitApp()
                                true
                            }
                            is JobState.Failed -> {
                                onBackHome()
                                true
                            }
                            JobState.Idle -> false
                        }
                    } else {
                        false
                    }
                },
        ) {
            if (job is JobState.Working) {
                JobProgressRing(progress = job.progress)
            }

            InstallPageBody(
                info = info,
                title = when (job) {
                    is JobState.Succeeded -> stringResource(R.string.install_success)
                    is JobState.Failed -> if (isCancelled) stringResource(R.string.install_cancelled) else stringResource(R.string.install_failed)
                    is JobState.Working -> if (job.cancelling) stringResource(R.string.phase_cancelling) else info?.label ?: job.detail
                    JobState.Idle -> stringResource(R.string.app_name)
                },
                detail = when (job) {
                    is JobState.Working -> if (job.cancelling) info?.label ?: job.detail else phaseLabel(job.phase)
                    is JobState.Succeeded -> info?.label ?: ""
                    is JobState.Failed -> if (isCancelled) job.detail.ifBlank { info?.label ?: "" } else job.detail
                    JobState.Idle -> ""
                },
                contentPadding = contentPadding,
            ) {
                when (job) {
                    is JobState.Working -> InstallCircleButton(
                        onClick = onCancel,
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        primary = false,
                    )

                    is JobState.Succeeded -> InstallCircleButton(
                        onClick = onExitApp,
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.action_install),
                        primary = true,
                    )

                    is JobState.Failed -> {
                        if (!isCancelled) {
                            InstallCircleButton(
                                onClick = onRetry,
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_retry),
                                primary = true,
                            )
                        }
                        InstallCircleButton(
                            onClick = onBackHome,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back_home),
                            primary = false,
                        )
                        if (!isCancelled && job.needsDevOptions) {
                            InstallCircleButton(
                                onClick = onOpenDevOptions,
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_open_dev_options),
                                primary = false,
                            )
                        }
                    }

                    JobState.Idle -> InstallCircleButton(
                        onClick = onBackHome,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back_home),
                        primary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.JobProgressRing(progress: Float?) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val indicatorColor = MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(durationMillis = 200),
        label = "progress",
    )
    if (progress != null) {
        val sweep = animatedProgress * 360f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .drawBehind {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = RING_START_ANGLE,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                    )
                    drawArc(
                        color = indicatorColor,
                        startAngle = RING_START_ANGLE,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = stroke,
                    )
                },
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            strokeWidth = 4.dp,
        )
    }
}

@Composable
private fun phaseLabel(phase: InstallPhase): String = when (phase) {
    InstallPhase.PUSHING -> stringResource(R.string.phase_pushing)
    InstallPhase.INSTALLING -> stringResource(R.string.phase_installing)
    InstallPhase.DISABLING_INSTALLER -> stringResource(R.string.phase_installing)
    InstallPhase.ENABLING_INSTALLER -> stringResource(R.string.phase_installing)
    InstallPhase.CLEANING -> stringResource(R.string.phase_installing)
    else -> ""
}
