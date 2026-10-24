package com.lyon.wearinstaller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.data.AppAccent
import com.lyon.wearinstaller.data.AppSettings
import com.lyon.wearinstaller.ui.components.CenteredLabel
import com.lyon.wearinstaller.ui.components.InfoRow
import com.lyon.wearinstaller.ui.components.SectionCard
import com.lyon.wearinstaller.ui.components.WearListHeader
import com.lyon.wearinstaller.ui.components.WearTextField
import com.lyon.wearinstaller.ui.theme.labelRes
import com.lyon.wearinstaller.ui.theme.swatch

private val SWATCH_SIZE = 28.dp
private val SELECTED_RING_SIZE = SWATCH_SIZE + 4.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    bottomPadding: PaddingValues = PaddingValues(0.dp),
) {
    var debugAddress by remember(settings.debugAddress) { mutableStateOf(settings.debugAddress) }

    val title = stringResource(R.string.settings_title)
    val scrollState = rememberScrollState()

    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WearListHeader(text = title, modifier = Modifier.fillMaxWidth())

            CenteredLabel(
                text = stringResource(R.string.setting_link_mode),
                modifier = Modifier.fillMaxWidth(),
            )

            WearTextField(
                value = debugAddress,
                onValueChange = { debugAddress = it },
                placeholder = stringResource(R.string.placeholder_debug_address),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onUpdate { it.copy(debugAddress = debugAddress.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.action_apply), maxLines = 1) },
            )

            SwitchButton(
                checked = settings.deleteSourceApk,
                onCheckedChange = { value -> onUpdate { it.copy(deleteSourceApk = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setting_delete_source_apk)) },
                secondaryLabel = { Text(stringResource(R.string.setting_delete_source_apk_hint)) },
            )

            SwitchButton(
                checked = settings.huaweiWatchMode,
                onCheckedChange = { value -> onUpdate { it.copy(huaweiWatchMode = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setting_huawei)) },
                secondaryLabel = { Text(stringResource(R.string.setting_huawei_hint)) },
            )

            SwitchButton(
                checked = settings.autoOpenDevOptions,
                onCheckedChange = { value -> onUpdate { it.copy(autoOpenDevOptions = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setting_auto_dev_options)) },
            )

            CenteredLabel(
                text = stringResource(R.string.setting_theme_color),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                AccentSwatches(
                    selected = settings.accent,
                    onSelect = { accent -> onUpdate { it.copy(accent = accent) } },
                )
            }

            Spacer(modifier = Modifier.height(bottomPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun AccentSwatches(
    selected: AppAccent,
    onSelect: (AppAccent) -> Unit,
) {
    val accents = listOf(
        AppAccent.GOOGLE_BLUE,
        AppAccent.DYNAMIC,
        AppAccent.GOOGLE_RED,
        AppAccent.GOOGLE_YELLOW,
        AppAccent.GOOGLE_GREEN,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accents.forEach { accent ->
            val isSelected = accent == selected
            val size = if (isSelected) SELECTED_RING_SIZE else SWATCH_SIZE
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else Color.Transparent,
                    )
                    .clickable(role = Role.Button) { onSelect(accent) }
                    .semantics { contentDescription = accent.labelRes.toString() }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH_SIZE)
                        .clip(CircleShape)
                        .background(accent.swatch),
                )
            }
        }
    }
}
