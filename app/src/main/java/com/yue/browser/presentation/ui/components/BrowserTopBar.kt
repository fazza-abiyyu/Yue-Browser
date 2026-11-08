package com.yue.browser.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.R
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.presentation.theme.DarkPill
import com.yue.browser.presentation.theme.ShapePill
import com.yue.browser.presentation.theme.yueColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserTopBar(
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    isDarkMode: Boolean,
    onTabClick: (Int) -> Unit,
    onCloseTabClick: (Int) -> Unit,
    onNewTabClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    onHomeClick: () -> Unit,
    onUrlClick: () -> Unit,
    onUrlLongClick: () -> Unit = {},
    onMenuClick: () -> Unit,
    activeTab: BrowserTab,
    isStartPage: Boolean,
    searchEngineUrl: String,
    isSplitActive: Boolean,
    onSplitToggle: () -> Unit,
    secondaryTabId: String?,
    modifier: Modifier = Modifier
) {
    val pillInteraction = remember { MutableInteractionSource() }
    val yue = MaterialTheme.yueColors
    // Latar/pill/teks navbar diselaraskan dengan mode normal; aksen tetap merah di private (feedback user 2026-11-08)
    val barBgColor = MaterialTheme.colorScheme.surface
    val barOutlineColor = MaterialTheme.colorScheme.outlineVariant
    val contentColor = MaterialTheme.colorScheme.onBackground
    val accentColor = if (activeTab.isPrivate) yue.privateAccent else MaterialTheme.colorScheme.primary
    // Back/forward/menu ikut aksen merah di private (feedback user 2026-11-08)
    val navIconColor = if (activeTab.isPrivate) accentColor else contentColor
    val pillBgColor = if (isDarkMode) DarkPill else MaterialTheme.colorScheme.surfaceVariant
    val onBgColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBgColor)
            .border(bottom = 1.dp, color = barOutlineColor)
    ) {
        // 1. Tab Strip
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(top = 4.dp, start = 8.dp, end = 8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    val isActive = index == activeTabIndex
                    val tabBgColor = if (isActive) {
                        barBgColor
                    } else {
                        Color.Transparent
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .widthIn(max = 160.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(tabBgColor)
                            .clickable { onTabClick(index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        // Icon / Letter
                        val isTabStartPage = tab.url.isBlank() || tab.url == "about:blank" || tab.url == "yue://newtab"
                        val letter = if (isTabStartPage) "Y" else {
                            try {
                                android.net.Uri.parse(tab.url).host?.take(1)?.uppercase() ?: "W"
                            } catch (e: Exception) {
                                "W"
                            }
                        }
                        
                        if (tab.id == secondaryTabId) {
                            Icon(
                                imageVector = Icons.Default.ViewWeek,
                                contentDescription = "Split tab",
                                tint = accentColor,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (tab.isPrivate) yue.privateAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tab.isPrivate) yue.privateAccent else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Title
                        val displayTitle = if (isTabStartPage) "New Tab" else tab.title.ifBlank { tab.url }
                        Text(
                            text = displayTitle,
                            fontSize = 13.sp,
                            color = onBgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        // Close button
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = onBgColor.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .clickable { onCloseTabClick(index) }
                        )
                    }
                }
            }

            // New Tab Button (+)
            IconButton(
                onClick = onNewTabClick,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New tab",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Navigation & Address Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp)
        ) {
            // Back — long-press = Forward; habis history → home (feedback user 2026-11-07)
            val backEnabled = (activeTab.canGoBack || activeTab.session.combinedCanGoBack) && !isStartPage
            val forwardEnabled = (activeTab.canGoForward || activeTab.session.combinedCanGoForward) && !isStartPage
            val backActive = backEnabled || forwardEnabled || !isStartPage
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = backActive,
                        onClick = { if (backEnabled) onBackClick() else onHomeClick() },
                        onLongClick = { if (forwardEnabled) onForwardClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (backActive) navIconColor else navIconColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Forward — kembali ke bar (feedback user 2026-11-07)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = forwardEnabled,
                        onClick = { if (forwardEnabled) onForwardClick() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = stringResource(R.string.forward),
                    tint = if (forwardEnabled) navIconColor else navIconColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Reload button
            IconButton(
                onClick = onReloadClick,
                enabled = !isStartPage,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = if (!isStartPage) contentColor else contentColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Home button
            IconButton(
                onClick = onHomeClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Address bar
            val host = if (isStartPage) {
                stringResource(R.string.search_or_enter_address)
            } else {
                activeTab.url
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(ShapePill)
                    .background(pillBgColor)
                    .combinedClickable(
                        interactionSource = pillInteraction,
                        indication = rememberRipple(color = accentColor),
                        onClick = { onUrlClick() },
                        onLongClick = { onUrlLongClick() }
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isStartPage) {
                        com.yue.browser.presentation.ui.SearchEngineIcon(
                            url = searchEngineUrl,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp),
                            isPrivate = activeTab.isPrivate
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.secure),
                            tint = contentColor,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = host,
                        color = if (isStartPage) onBgColor.copy(alpha = 0.6f) else onBgColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Split Tab Button
            IconButton(
                onClick = onSplitToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewWeek,
                    contentDescription = "Split Tab",
                    tint = if (isSplitActive) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Menu button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = navIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Extension to draw a single bottom border easily in Compose
private fun Modifier.border(bottom: androidx.compose.ui.unit.Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidth = bottom.toPx()
    val y = size.height - strokeWidth / 2
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, y),
        end = androidx.compose.ui.geometry.Offset(size.width, y),
        strokeWidth = strokeWidth
    )
}
