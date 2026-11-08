package com.yue.browser.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Token tipografi — docs/ui-redesign-spec.md §5.2
// Baseline ukuran mengikuti UI saat ini; visual baru berubah ketika inline `fontSize`
// di call-site dipindah ke token ini (tahap 2 §9).
val YueTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 80.sp), // wordmark welcome
    displayMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 40.sp), // wordmark home
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), // judul layar
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp), // judul item list/settings
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)
