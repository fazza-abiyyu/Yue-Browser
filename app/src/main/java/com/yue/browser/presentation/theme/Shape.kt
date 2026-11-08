package com.yue.browser.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Token bentuk — docs/ui-redesign-spec.md §5.3 (ala One UI)

val YueShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), // tile: ikon file, quick-setting
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), // card: kartu tab, kartu pengaturan
    large = RoundedCornerShape(24.dp), // dialog
    extraLarge = RoundedCornerShape(28.dp), // sheet
)

/** Pill penuh: URL pill, search pill, chip. */
val ShapePill = RoundedCornerShape(percent = 50)
