package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yue.browser.presentation.BrowserViewModel
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.UndoCloseTabBanner(
    viewModel: BrowserViewModel,
    isBottomBarVisible: Boolean,
    isDarkMode: Boolean,
    context: android.content.Context
) {
    val lastClosed by viewModel.lastClosedTab.collectAsState()
    LaunchedEffect(lastClosed) {
        if (lastClosed != null) {
            delay(4000)
            viewModel.lastClosedTab.value = null
        }
    }
    if (lastClosed != null) {
        val cardBg = if (isDarkMode) Color(0xFF1A1A1C) else Color(0xFFF0F1F2)
        val cardText = if (isDarkMode) Color(0xFFE3E3E3) else Color(0xFF191C1D)
        val accentPink = Color(0xFFEC4899)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isBottomBarVisible) 120.dp else 80.dp, start = 16.dp, end = 16.dp)
                .zIndex(30f)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBg
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        tint = accentPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.tab_undo_closed),
                        style = MaterialTheme.typography.bodySmall,
                        color = cardText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            viewModel.undoCloseTab(context)
                            viewModel.lastClosedTab.value = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = accentPink
                        )
                    ) {
                        Text(
                            stringResource(R.string.tab_undo_action),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { viewModel.lastClosedTab.value = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = if (isDarkMode) Color.LightGray.copy(alpha = 0.7f) else Color(0xFF667889),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
