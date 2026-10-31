package com.lyon.wearinstaller.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.lyon.wearinstaller.apk.ApkInfo

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

/** 安装确认页/进度页共用的 APK 图标，保证两种页面头部尺寸完全一致。 */
@Composable
fun ApkAvatar(info: ApkInfo?) {
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

/** 安装相关页面共用的按钮尺寸。 */
val InstallActionSize = 56.dp

/**
 * 安装确认页、传输中、安装中、完成、失败共用的页面骨架。
 * 所有状态的图标、文字、按钮位置完全一致，任何一处改动都会同时作用于所有页面。
 */
@Composable
fun InstallPageBody(
    info: ApkInfo?,
    title: String,
    detail: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        ApkAvatar(info)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(2.dp))

        Text(
            text = detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(14.dp))

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                14.dp,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** 安装相关页面共用的圆形按钮，主操作使用 primaryDim 底色。 */
@Composable
fun InstallCircleButton(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    primary: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.wear.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.size(InstallActionSize),
        colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (primary) scheme.primaryDim else scheme.surfaceContainerHigh,
            contentColor = if (primary) scheme.primary else scheme.onSurface,
        ),
        shape = CircleShape,
    ) {
        androidx.wear.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = if (primary) scheme.primary else scheme.onSurface,
        )
    }
}
