package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.presentation.theme.DarkPill
import com.yue.browser.presentation.theme.ShapePill
import com.yue.browser.presentation.theme.YueDimens
import com.yue.browser.presentation.theme.yueColors

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
    onHomeClick: () -> Unit = {},
    canUndoClose: Boolean = false,
    onUndoClick: () -> Unit = {},
    onUrlClick: () -> Unit,
    onUrlLongClick: () -> Unit,
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit,
    searchEngineUrl: String = "https://www.google.com/search?q=",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pillInteraction = remember { MutableInteractionSource() }
    val yue = MaterialTheme.yueColors
    val barBgColor = if (activeTab.isPrivate) yue.privateSurface else MaterialTheme.colorScheme.surface
    val pillBgColor = if (activeTab.isPrivate) yue.privateSurface else if (isDarkMode) DarkPill else MaterialTheme.colorScheme.surfaceVariant
    val onBgColor = if (activeTab.isPrivate) yue.privateOnSurface else MaterialTheme.colorScheme.onBackground
    val accentColor = if (activeTab.isPrivate) yue.privateAccent else MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBgColor)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .widthIn(max = YueDimens.bottomBarMaxWidth)
                    .fillMaxWidth()
                    .height(YueDimens.barHeight)
                    .padding(horizontal = 6.dp)
            ) {
                // Back — long-press = Forward; habis history → home (feedback user 2026-11-07)
                val backEnabled = (activeTab.canGoBack || activeTab.session.combinedCanGoBack) && !isStartPage
                val forwardEnabled = (activeTab.canGoForward || activeTab.session.combinedCanGoForward) && !isStartPage
                val backActive = backEnabled || forwardEnabled || !isStartPage
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        tint = if (backActive) onBgColor else onBgColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(YueDimens.iconBar)
                    )
                }

                // Forward — kembali ke bar (feedback user 2026-11-07)
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        tint = if (forwardEnabled) onBgColor else onBgColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(YueDimens.iconBar)
                    )
                }

                // Undo — pulihkan tab terakhir yang ditutup (muncul hanya saat ada)
                AnimatedVisibility(
                    visible = canUndoClose,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onUndoClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = stringResource(R.string.tab_undo_action),
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Central URL pill — terisi, tanpa outline
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
                        .height(YueDimens.urlPillHeight)
                        .padding(horizontal = 6.dp)
                        .clip(ShapePill)
                        .background(pillBgColor)
                        .combinedClickable(
                            interactionSource = pillInteraction,
                            indication = rememberRipple(color = accentColor),
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
                                    .padding(end = 4.dp),
                                isPrivate = activeTab.isPrivate
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.secure),
                                tint = onBgColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = host,
                            color = if (isStartPage) onBgColor.copy(alpha = 0.6f) else onBgColor,
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
                    // Tab count — chip terisi, portal ke tab switcher
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .defaultMinSize(minWidth = 30.dp)
                            .clip(ShapePill)
                            .background(pillBgColor)
                            .clickable(onClick = onTabSwitcherClick),
                        contentAlignment = Alignment.Center
                    ) {
                        val tabCount = tabs.count { it.isPrivate == activeTab.isPrivate }
                        Text(
                            text = tabCount.toString(),
                            color = if (showTabSwitcher) accentColor else onBgColor,
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
                            tint = onBgColor,
                            modifier = Modifier.size(YueDimens.iconBar)
                        )
                    }
                }
            }
        }
    }
}
