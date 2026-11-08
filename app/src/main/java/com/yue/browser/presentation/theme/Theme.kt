package com.yue.browser.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb

// Konstanta warna & token ada di Color.kt / Shape.kt / Type.kt / Dimens.kt (docs/ui-redesign-spec.md §5)

private val LightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PinkLight.copy(alpha = 0.3f),
    onPrimaryContainer = PinkDark,
    secondary = Color(0xFF65748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEF4),
    onSecondaryContainer = Color(0xFF1D2B3E),
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = Color(0xFFF0F1F2),
    onSurfaceVariant = Color(0xFF4D6172),
    outline = LightOutline,
    outlineVariant = Color(0xFFD8D8DC),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    surfaceTint = Color.Transparent,
    // Menu/dropdown: putih bersih di mode terang (surfaceContainer default M3 terlalu gelap)
    surfaceContainer = Color(0xFF00FF00),
    surfaceContainerHigh = Color(0xFF00FF00),
    surfaceContainerHighest = Color(0xFF00FF00),
    surfaceContainerLow = Color(0xFF00FF00),
    surfaceContainerLowest = Color(0xFF00FF00)
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PinkDark.copy(alpha = 0.3f),
    onPrimaryContainer = PinkLight,
    secondary = Color(0xFF8FA3B8),
    onSecondary = Color(0xFF0F1D2E),
    secondaryContainer = Color(0xFF2A384B),
    onSecondaryContainer = Color(0xFFC5D6EB),
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF444446),
    outlineVariant = DarkOutline,
    error = Color(0xFFEF5350),
    onError = Color.White,
    surfaceTint = Color.Transparent,
    // AMOLED: kontainer ter-elevasi pakai DarkPill agar menu terlihat
    surfaceContainer = DarkPill,
    surfaceContainerHigh = DarkPill,
    surfaceContainerHighest = Color(0xFF242428),
    surfaceContainerLow = DarkPill,
    surfaceContainerLowest = Color(0xFF0A0A0C)
)

@Composable
fun YueTheme(isDarkMode: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (isDarkMode) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = colors.background.toArgb()
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !isDarkMode
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = YueShapes,
        typography = YueTypography,
    ) {
        CompositionLocalProvider(
            LocalYueColors provides if (isDarkMode) DarkYueColors else LightYueColors,
            content = content,
        )
    }
}
