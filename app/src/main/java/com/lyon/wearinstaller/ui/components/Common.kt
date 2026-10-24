package com.lyon.wearinstaller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

val WearTextFieldHeight = 48.dp

@Composable
fun WearTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    transformation: androidx.wear.compose.material3.SurfaceTransformation? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        decorationBox = { innerTextField ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WearTextFieldHeight),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
                transformation = transformation,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    centered: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            color = if (emphasize) scheme.primary else scheme.onSurface,
            fontSize = 13.sp,
            maxLines = 2,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    transformation: androidx.wear.compose.material3.SurfaceTransformation? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), transformation = transformation) {
        content()
    }
}
