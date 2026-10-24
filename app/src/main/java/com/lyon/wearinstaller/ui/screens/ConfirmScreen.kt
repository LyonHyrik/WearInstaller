package com.lyon.wearinstaller.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.adb.InstallPhase
import com.lyon.wearinstaller.apk.ApkInfo
import com.lyon.wearinstaller.data.JobState
import com.lyon.wearinstaller.data.PendingInstall

private val ACTION_SIZE = 56.dp
private const val RING_START_ANGLE = 300f
private const val RING_SWEEP = 300f
private const val RING_SWEEP_FULL = 360f

@Composable
fun ConfirmScreen(
    pending: PendingInstall?,
    job: JobState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onCancelIdle: () -> Unit,
    onRetry: () -> Unit,
    onBackHome: () -> Unit,
    onExitApp: () -> Unit,
) {
    val info = pending?.info

    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(job) {
        val window = (context as? Activity)?.window
        if (job is JobState.Working) {
            window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        onDispose {
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    ScreenScaffold { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (job is JobState.Working) {
                JobProgressRing(progress = job.progress)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ApkAvatar(info = info)

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = when (job) {
                        is JobState.Succeeded -> stringResource(R.string.install_success)
                        is JobState.Failed -> stringResource(R.string.install_failed)
                        else -> info?.label ?: ""
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )

                Spacer(modifier = Modifier.size(2.dp))

                Text(
                    text = when (job) {
                        is JobState.Working -> phaseLabel(job.phase)
                        is JobState.Succeeded -> info?.label ?: ""
                        is JobState.Failed -> job.detail
                        else -> info?.let { "${it.versionName} · ${it.readableSize}" } ?: ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )

                Spacer(modifier = Modifier.size(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (job) {
                        is JobState.Working -> {
                            Button(
                                onClick = onCancel,
                                modifier = Modifier.size(ACTION_SIZE),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        is JobState.Succeeded -> {
                            Button(
                                onClick = onExitApp,
                                modifier = Modifier.size(ACTION_SIZE),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryDim,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.action_install),
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        is JobState.Failed -> {
                            Button(
                                onClick = onExitApp,
                                modifier = Modifier.size(ACTION_SIZE),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        JobState.Idle -> {
                            Button(
                                onClick = onCancelIdle,
                                modifier = Modifier.size(ACTION_SIZE),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Button(
                                onClick = onInstall,
                                modifier = Modifier.size(ACTION_SIZE),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryDim,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.action_install),
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.JobProgressRing(progress: Float?) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val indicatorColor = MaterialTheme.colorScheme.primary
    if (progress != null) {
        val sweep = (progress.coerceIn(0f, 1f)) * RING_SWEEP
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .drawBehind {
                    val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = RING_START_ANGLE,
                        sweepAngle = RING_SWEEP,
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
            modifier = Modifier.fillMaxSize().padding(4.dp),
            colors = androidx.wear.compose.material3.ProgressIndicatorColors(
                indicatorBrush = androidx.compose.ui.graphics.SolidColor(indicatorColor),
                trackBrush = androidx.compose.ui.graphics.SolidColor(trackColor),
                overflowTrackBrush = androidx.compose.ui.graphics.SolidColor(trackColor),
                disabledIndicatorBrush = androidx.compose.ui.graphics.SolidColor(indicatorColor),
                disabledTrackBrush = androidx.compose.ui.graphics.SolidColor(trackColor),
                disabledOverflowTrackBrush = androidx.compose.ui.graphics.SolidColor(trackColor),
            ),
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

@Composable
private fun ApkAvatar(info: ApkInfo?) {
    val icon = info?.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            filterQuality = FilterQuality.Low,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        )
    }
}
