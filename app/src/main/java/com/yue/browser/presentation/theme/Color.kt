package com.yue.browser.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Token warna — docs/ui-redesign-spec.md §5.1 (standar premium, pink dipertahankan, AMOLED murni)

// Brand (dipertahankan — keputusan 2026-11-07)
val PinkPrimary = Color(0xFFEC4899)
val PinkDark = Color(0xFFDB2777)
val PinkLight = Color(0xFFF9A8D4)
val BrandContainerAlpha = 0.12f

// Light mode
val LightBg = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBg = Color(0xFF191C1D)
val LightOutline = Color(0xFFC3C7CD)

// Dark mode (AMOLED murni — bg/surface tetap #000000)
val DarkBg = Color(0xFF000000)
val DarkSurface = Color(0xFF000000)
val DarkSurface2 = Color(0xFF000000)
val DarkOnBg = Color(0xFFE3E3E3)
val DarkOutline = Color(0xFF1A1A1A)
val DarkPill = Color(0xFF1A1A1C)

// Semantic
val SemanticSuccess = Color(0xFF4CAF50)
val SemanticWarning = Color(0xFFF59E0B)

// Warna URL di baris saran
val UrlSchemeLight = Color(0xFFD93025)
val UrlSchemeDark = Color(0xFFF28B82)

// Palet kategoris bersama (speed dial + grup tab — menggantikan dua palet hardcoded)
val CategoryPalette = listOf(
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
)

/** Varian hex untuk speed dial yang menyimpan warna per-dial (ikonBgColorHex). */
val CategoryPaletteHex = listOf("3B82F6", "EF4444", "F59E0B", "10B981", "8B5CF6", "EC4899", "06B6D4", "F97316")

/**
 * Slot warna yang tidak ada di Material 3 [androidx.compose.material3.ColorScheme]:
 * mode privat, semantic status, warna URL, dan palet kategoris.
 */
@Immutable
data class YueColors(
    val privateAccent: Color,
    val privateSurface: Color,
    val privateBorder: Color,
    val privateOnSurface: Color,
    val semanticSuccess: Color,
    val semanticWarning: Color,
    val urlScheme: Color,
    val categoryPalette: List<Color>,
)

val LightYueColors = YueColors(
    privateAccent = Color(0xFFFF002C),
    privateSurface = Color(0xFFF5F5F5),
    privateBorder = Color(0xFFD8D8DC),
    privateOnSurface = Color(0xFF1A1A1A),
    semanticSuccess = SemanticSuccess,
    semanticWarning = SemanticWarning,
    urlScheme = UrlSchemeLight,
    categoryPalette = CategoryPalette,
)

val DarkYueColors = YueColors(
    privateAccent = Color(0xFFFF002C),
    privateSurface = Color(0xFF1A1A1C),
    privateBorder = Color(0xFF1A1A1A),
    privateOnSurface = Color.White,
    semanticSuccess = SemanticSuccess,
    semanticWarning = SemanticWarning,
    urlScheme = UrlSchemeDark,
    categoryPalette = CategoryPalette,
)

val LocalYueColors = staticCompositionLocalOf { LightYueColors }

val MaterialTheme.yueColors: YueColors
    @Composable get() = LocalYueColors.current
