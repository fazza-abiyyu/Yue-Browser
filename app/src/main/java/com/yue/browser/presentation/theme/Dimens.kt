package com.yue.browser.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Token ukuran — docs/ui-redesign-spec.md §5.4

object YueDimens {
    // Skala spacing: 4 / 8 / 12 / 16 / 24 / 32
    val spacingXs: Dp = 4.dp
    val spacingSm: Dp = 8.dp
    val spacingMd: Dp = 12.dp
    val spacingLg: Dp = 16.dp
    val spacingXl: Dp = 24.dp
    val spacingXxl: Dp = 32.dp

    // Bar & pill
    val barHeight: Dp = 48.dp
    val urlPillHeight: Dp = 38.dp
    val touchTargetMin: Dp = 48.dp
    val iconBar: Dp = 24.dp
    val iconBarCompact: Dp = 20.dp

    // Lebar maksimum konten
    val contentMaxWidth: Dp = 600.dp
    val bottomBarMaxWidth: Dp = 720.dp
}
