package com.lyon.wearinstaller.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.lyon.wearinstaller.R
import com.lyon.wearinstaller.data.AppAccent

private val WearExpressiveScheme = ColorScheme(
    primary = Color(0xFF7EE7DA),
    primaryDim = Color(0xFF00504A),
    primaryContainer = Color(0xFF00302C),
    onPrimary = Color(0xFF00382F),
    onPrimaryContainer = Color(0xFF9FFFF2),
    secondary = Color(0xFFB0C9C5),
    secondaryDim = Color(0xFF31413E),
    secondaryContainer = Color(0xFF1B2B29),
    onSecondary = Color(0xFF1B332F),
    onSecondaryContainer = Color(0xFFCCE5E1),
    tertiary = Color(0xFFFFD8A8),
    tertiaryDim = Color(0xFF5A3C00),
    tertiaryContainer = Color(0xFF7A5200),
    onTertiary = Color(0xFF3F2D00),
    onTertiaryContainer = Color(0xFFFFDFB2),
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF202026),
    onSurface = Color(0xFFE6E3E9),
    onSurfaceVariant = Color(0xFFBFC9C6),
    outline = Color(0xFF8A938F),
    outlineVariant = Color(0xFF3C4644),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E3E9),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFF8C0009),
    errorContainer = Color(0xFF5F1210),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

private data class AccentRoles(
    val primary: Color,
    val primaryDim: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val tertiary: Color,
    val tertiaryDim: Color,
    val tertiaryContainer: Color,
    val onTertiary: Color,
    val onTertiaryContainer: Color,
)

private val GoogleBlueRoles = AccentRoles(
    primary = Color(0xFFA8C7FA),
    primaryDim = Color(0xFF1B4C8F),
    primaryContainer = Color(0xFF0C3B77),
    onPrimary = Color(0xFF0A2A5E),
    onPrimaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFFBFD4FF),
    tertiaryDim = Color(0xFF27466E),
    tertiaryContainer = Color(0xFF1F3A5C),
    onTertiary = Color(0xFF12253D),
    onTertiaryContainer = Color(0xFFD9E6FF),
)

private val GoogleRedRoles = AccentRoles(
    primary = Color(0xFFF2B8B5),
    primaryDim = Color(0xFF8C1D18),
    primaryContainer = Color(0xFF93000A),
    onPrimary = Color(0xFF601410),
    onPrimaryContainer = Color(0xFFF9DEDC),
    tertiary = Color(0xFFFFB4A8),
    tertiaryDim = Color(0xFF7C3B2D),
    tertiaryContainer = Color(0xFF5C3227),
    onTertiary = Color(0xFF3B1A12),
    onTertiaryContainer = Color(0xFFFFDAD2),
)

private val GoogleYellowRoles = AccentRoles(
    primary = Color(0xFFE8C24A),
    primaryDim = Color(0xFF6B5300),
    primaryContainer = Color(0xFF574300),
    onPrimary = Color(0xFF3B2E00),
    onPrimaryContainer = Color(0xFFFFE39B),
    tertiary = Color(0xFFF0DCA0),
    tertiaryDim = Color(0xFF5C4F25),
    tertiaryContainer = Color(0xFF463A18),
    onTertiary = Color(0xFF2E2609),
    onTertiaryContainer = Color(0xFFFFEFC4),
)

private val GoogleGreenRoles = AccentRoles(
    primary = Color(0xFF7BD88F),
    primaryDim = Color(0xFF146C2E),
    primaryContainer = Color(0xFF0E5A26),
    onPrimary = Color(0xFF00391A),
    onPrimaryContainer = Color(0xFFB6F2C2),
    tertiary = Color(0xFFA8E6B4),
    tertiaryDim = Color(0xFF2C5B37),
    tertiaryContainer = Color(0xFF204429),
    onTertiary = Color(0xFF12301B),
    onTertiaryContainer = Color(0xFFC3F2CD),
)

val AppAccent.swatch: Color
    get() = when (this) {
        AppAccent.DYNAMIC -> Color(0xFF7EE7DA)
        AppAccent.GOOGLE_BLUE -> Color(0xFF4285F4)
        AppAccent.GOOGLE_RED -> Color(0xFFEA4335)
        AppAccent.GOOGLE_YELLOW -> Color(0xFFFBBC04)
        AppAccent.GOOGLE_GREEN -> Color(0xFF34A853)
    }

@get:StringRes
val AppAccent.labelRes: Int
    get() = when (this) {
        AppAccent.DYNAMIC -> R.string.accent_dynamic
        AppAccent.GOOGLE_BLUE -> R.string.accent_blue
        AppAccent.GOOGLE_RED -> R.string.accent_red
        AppAccent.GOOGLE_YELLOW -> R.string.accent_yellow
        AppAccent.GOOGLE_GREEN -> R.string.accent_green
    }

private fun AccentRoles.applyTo(scheme: ColorScheme): ColorScheme = scheme.copy(
    primary = primary,
    primaryDim = primaryDim,
    primaryContainer = primaryContainer,
    onPrimary = onPrimary,
    onPrimaryContainer = onPrimaryContainer,
    tertiary = tertiary,
    tertiaryDim = tertiaryDim,
    tertiaryContainer = tertiaryContainer,
    onTertiary = onTertiary,
    onTertiaryContainer = onTertiaryContainer,
)

@Composable
fun WearInstallerTheme(accent: AppAccent = AppAccent.GOOGLE_BLUE, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(context, accent) {
        when (accent) {
            AppAccent.DYNAMIC ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dynamicColorScheme(context) ?: WearExpressiveScheme
                } else {
                    WearExpressiveScheme
                }
            AppAccent.GOOGLE_BLUE -> GoogleBlueRoles.applyTo(WearExpressiveScheme)
            AppAccent.GOOGLE_RED -> GoogleRedRoles.applyTo(WearExpressiveScheme)
            AppAccent.GOOGLE_YELLOW -> GoogleYellowRoles.applyTo(WearExpressiveScheme)
            AppAccent.GOOGLE_GREEN -> GoogleGreenRoles.applyTo(WearExpressiveScheme)
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
