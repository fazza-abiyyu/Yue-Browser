package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserBottomBar(
    isVisible: Boolean,
    activeTab: BrowserTab,
    isStartPage: Boolean,
    showTabSwitcher: Boolean,
    isDarkMode: Boolean,
    tabs: List<BrowserTab>,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onUrlClick: () -> Unit,
    onUrlLongClick: () -> Unit,
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit,
    searchEngineUrl: String = "https://www.google.com/search?q=",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val incognitoBg = if (isDarkMode) Color(0xFF000000) else Color(0xFFF5F5F5)
    val incognitoBorder = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFD8D8DC)
    val bottomBarBgColor = if (activeTab.isPrivate) incognitoBg else MaterialTheme.colorScheme.surface
    val bottomBarOutlineColor = if (activeTab.isPrivate) incognitoBorder else MaterialTheme.colorScheme.outlineVariant
    val bottomBarContentColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
    val bottomBarActiveContentColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
    val bottomBarOnBgColor = if (activeTab.isPrivate) (if (isDarkMode) Color.White else Color(0xFF1A1A1A)) else MaterialTheme.colorScheme.onBackground

    var showForwardHistoryMenu by remember { mutableStateOf(false) }
    val forwardHistory = remember(activeTab) {
        (activeTab.session as? com.yue.browser.data.engine.SystemWebViewSession)?.getForwardHistory() ?: emptyList()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bottomBarBgColor)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
            ) {
                // Back & Forward buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backEnabled = (activeTab.canGoBack || activeTab.session.combinedCanGoBack) && !isStartPage
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(enabled = backEnabled, onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = if (backEnabled) bottomBarContentColor else bottomBarContentColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val forwardEnabled = (activeTab.canGoForward || activeTab.session.combinedCanGoForward) && !isStartPage
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(enabled = forwardEnabled, onClick = onForwardClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = stringResource(R.string.forward),
                            tint = if (forwardEnabled) bottomBarContentColor else bottomBarContentColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showForwardHistoryMenu && forwardHistory.isNotEmpty(),
                        onDismissRequest = { showForwardHistoryMenu = false },
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        forwardHistory.take(10).forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = item.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    showForwardHistoryMenu = false
                                    (activeTab.session as? com.yue.browser.data.engine.SystemWebViewSession)?.navigateToHistoryItem(item.steps)
                                }
                            )
                        }
                    }
                }

                // Central URL Search Box
                val host = remember(activeTab.url, context) {
                    if (isStartPage) {
                        context.getString(R.string.search_or_enter_address)
                    } else {
                        activeTab.url
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (activeTab.isPrivate) incognitoBg else MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .border(1.dp, if (activeTab.isPrivate) incognitoBorder else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = onUrlClick,
                            onLongClick = onUrlLongClick
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
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.secure),
                                tint = bottomBarContentColor,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = host,
                            color = if (isStartPage) bottomBarOnBgColor.copy(alpha = 0.6f) else bottomBarOnBgColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tabs & Menu buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val switcherColor = if (showTabSwitcher) bottomBarActiveContentColor else bottomBarContentColor
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Transparent)
                            .border(1.dp, switcherColor, RoundedCornerShape(8.dp))
                            .clickable(onClick = onTabSwitcherClick),
                        contentAlignment = Alignment.Center
                    ) {
                        val tabCount = tabs.count { it.isPrivate == activeTab.isPrivate }
                        Text(
                            text = tabCount.toString(),
                            color = switcherColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { translationY = -2f }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.menu),
                            tint = bottomBarContentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
