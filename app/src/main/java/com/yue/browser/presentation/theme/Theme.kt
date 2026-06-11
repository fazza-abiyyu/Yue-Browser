package com.yue.browser.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb

// Brand colors
val PinkPrimary = Color(0xFFEC4899)
val PinkDark = Color(0xFFDB2777)
val PinkLight = Color(0xFFF9A8D4)

// Light mode
val LightBg = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBg = Color(0xFF191C1D)
val LightOutline = Color(0xFFC3C7CD)

// Dark mode (AMOLED)
val DarkBg = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurface2 = Color(0xFF1A1A1C)
val DarkOnBg = Color(0xFFE3E3E3)
val DarkOutline = Color(0xFF333333)

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
    onError = Color.White
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
    onError = Color.White
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
        content = content
    )
}
