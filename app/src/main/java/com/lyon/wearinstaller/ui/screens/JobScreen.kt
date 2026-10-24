package com.lyon.wearinstaller.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.lyon.wearinstaller.data.JobState
import com.lyon.wearinstaller.ui.components.InfoRow

private const val RING_START_ANGLE = 270f
private const val CHECK_GLYPH = "\u2713"

@Composable
fun JobScreen(
    job: JobState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onBackHome: () -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(job) {
        val window = (context as? Activity)?.window
        if (job is JobState.Working) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    ScreenScaffold { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val working = job as? JobState.Working
            if (working != null) {
                JobProgressRing(progress = working.progress)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (job) {
                    is JobState.Working -> {
                        Text(
                            text = job.detail,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Text(
                            text = phaseLabel(job.phase),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.size(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    is JobState.Succeeded -> {
                        Text(
                            text = stringResource(R.string.install_success),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InfoRow(
                            label = "",
                            value = job.label,
                            centered = true,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Button(
                            onClick = onExitApp,
                            modifier = Modifier.size(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
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

                    is JobState.Failed -> {
                        Text(
                            text = stringResource(R.string.install_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InfoRow(
                            label = stringResource(R.string.install_failed),
                            value = job.detail,
                            centered = true,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (job.detail.isNotBlank()) {
                            Text(
                                text = job.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(0.8f),
                                label = { Text(stringResource(R.string.action_retry)) },
                            )
                            Button(
                                onClick = onBackHome,
                                modifier = Modifier.fillMaxWidth(0.8f),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                label = { Text(stringResource(R.string.action_back_home)) },
                            )
                            if (job.needsDevOptions) {
                                Button(
                                    onClick = onOpenDevOptions,
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    label = { Text(stringResource(R.string.action_open_dev_options)) },
                                )
                            }
                        }
                    }

                    JobState.Idle -> {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Button(
                            onClick = onBackHome,
                            modifier = Modifier.fillMaxWidth(0.8f),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            label = { Text(stringResource(R.string.action_back_home)) },
                        )
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
