package com.lyon.wearinstaller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.data.ConnectionStatus
import com.lyon.wearinstaller.data.PendingInstall
import com.lyon.wearinstaller.ui.components.InfoRow
import com.lyon.wearinstaller.ui.components.isLargeDisplay

val PAGE_DOTS_HEIGHT = 18.dp

@Composable
fun HomeScreen(
    connection: ConnectionStatus,
    pending: PendingInstall?,
    debugAddress: String,
    onPickFile: () -> Unit,
    bottomPadding: PaddingValues = PaddingValues(0.dp),
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()



    ScreenScaffold(
        scrollState = columnState,
        scrollIndicator = { ScrollIndicator(columnState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(columnState),
            rotaryScrollableBehavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(columnState),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val connected = connection is ConnectionStatus.Online
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                CircleShape,
                            ),
                    )
                    Text(
                        text = stringResource(
                            if (connected) R.string.status_connected else R.string.status_idle,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            item {
                if (connection is ConnectionStatus.Online && connection.profile.model.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.label_device),
                        value = connection.profile.model,
                        centered = true,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                } else if (debugAddress.isNotBlank()) {
                    InfoRow(
                        label = stringResource(R.string.label_debug_address),
                        value = debugAddress,
                        centered = true,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            item {
                InfoRow(
                    label = stringResource(R.string.label_pending),
                    value = pending?.info?.label ?: stringResource(R.string.hint_none),
                    emphasize = pending != null,
                    centered = true,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }

            item {
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = if (pending != null) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
