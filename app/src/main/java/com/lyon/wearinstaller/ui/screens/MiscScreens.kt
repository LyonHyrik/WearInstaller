package com.lyon.wearinstaller.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.ui.components.BodyText
import com.lyon.wearinstaller.ui.components.InfoRow
import com.lyon.wearinstaller.ui.components.WearListHeader
import com.lyon.wearinstaller.ui.components.buttonListItem
import com.lyon.wearinstaller.ui.components.headerListItem
import com.lyon.wearinstaller.ui.components.listItem

@Composable
fun AboutScreen(
    versionName: String,
) {
    val title = stringResource(R.string.app_name)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var avatar by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        try {
            val url = java.net.URL("https://github.com/LyonHyrik.png")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                avatar = bmp?.asImageBitmap()
            }
        } catch (_: Throwable) {
        }
    }

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
        ) {
            item {
                WearListHeader(
                    text = title,
                    modifier = headerListItem(transformationSpec),
                )
            }
            item {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
            item {
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar!!,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "LyonHyrik",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                BodyText(
                    text = "github.com/LyonHyrik",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun DevOptionsScreen(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.dev_options_title)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
        ) {
            item {
                WearListHeader(
                    text = title,
                    modifier = headerListItem(transformationSpec),
                )
            }
            item {
                BodyText(
                    text = stringResource(R.string.dev_options_body),
                    modifier = listItem(transformationSpec),
                )
            }
            item {
                androidx.wear.compose.material3.Button(
                    onClick = onOpen,
                    modifier = buttonListItem(transformationSpec),
                    label = { Text(stringResource(R.string.action_open_dev_options), maxLines = 2) },
                )
            }
            item {
                androidx.wear.compose.material3.FilledTonalButton(
                    onClick = onDismiss,
                    modifier = buttonListItem(transformationSpec),
                    label = { Text(stringResource(R.string.action_not_now), maxLines = 1) },
                )
            }
        }
    }
}
