package com.lyon.wearinstaller.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

const val LARGE_DISPLAY_BREAKPOINT = 225

@Composable
fun isLargeDisplay(): Boolean = LocalConfiguration.current.screenWidthDp >= LARGE_DISPLAY_BREAKPOINT

fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.listItem(
    spec: TransformationSpec,
    minimumVerticalPadding: androidx.compose.ui.unit.Dp = 0.dp,
): Modifier = Modifier
    .fillMaxWidth()
    .minimumVerticalContentPadding(minimumVerticalPadding)

@Composable
fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.cardListItem(spec: TransformationSpec): Modifier =
    listItem(spec, androidx.wear.compose.material3.CardDefaults.minimumVerticalListContentPadding)

@Composable
fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.headerListItem(spec: TransformationSpec): Modifier =
    listItem(spec, androidx.wear.compose.material3.ListHeaderDefaults.minimumTopListContentPadding)

@Composable
fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.buttonListItem(spec: TransformationSpec): Modifier =
    listItem(spec, androidx.wear.compose.material3.ButtonDefaults.minimumVerticalListContentPadding)

@Composable
fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.textFieldListItem(spec: TransformationSpec): Modifier =
    listItem(spec, androidx.wear.compose.material3.CardDefaults.minimumVerticalListContentPadding)

@Composable
fun CenteredLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        modifier = modifier,
    )
}

@Composable
fun WearListHeader(
    text: String,
    modifier: Modifier,
    transformation: SurfaceTransformation? = null,
) {
    ListHeader(
        modifier = modifier,
        transformation = transformation,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}
