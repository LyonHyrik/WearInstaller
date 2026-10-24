package com.lyon.wearinstaller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.ui.components.BodyText
import com.lyon.wearinstaller.ui.components.InfoRow
import com.lyon.wearinstaller.ui.components.WearListHeader
import com.lyon.wearinstaller.ui.components.headerListItem
import com.lyon.wearinstaller.ui.components.listItem
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

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize().focusable(),
            state = listState,
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
            rotaryScrollableBehavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(listState),
        ) {
            item {
                WearListHeader(
                    text = title,
                    modifier = headerListItem(transformationSpec),
                )
            }
            item {
                InfoRow(
                    label = stringResource(R.string.setting_version),
                    value = versionName,
                    modifier = listItem(transformationSpec, 4.dp),
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
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = columnState,
        scrollIndicator = { ScrollIndicator(columnState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = columnState,
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(columnState),
            rotaryScrollableBehavior = androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults.behavior(columnState),
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
                Button(
                    onClick = onOpen,
                    modifier = buttonListItem(transformationSpec),
                    label = { Text(stringResource(R.string.action_open_dev_options), maxLines = 2) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = buttonListItem(transformationSpec),
                    label = { Text(stringResource(R.string.action_not_now), maxLines = 1) },
                )
            }
        }
    }
}
