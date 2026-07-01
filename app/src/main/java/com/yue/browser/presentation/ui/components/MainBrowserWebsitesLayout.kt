package com.yue.browser.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.ui.BrowserWebView
import com.yue.browser.presentation.ui.WebLockOverlay
import com.yue.browser.presentation.ui.isBiometricAvailable
import com.yue.browser.presentation.ui.showBiometricPrompt

@Composable
internal fun MainBrowserWebsitesLayout(
    viewModel: BrowserViewModel,
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    settings: BrowserSettings,
    historyList: List<HistoryItem>,
    activeTab: BrowserTab,
    isStartPage: Boolean,
    isInPip: Boolean,
    isBottomBarVisible: Boolean,
    onBottomBarVisibleChange: (Boolean) -> Unit,
    isFullscreenOverlayVisible: Boolean,
    showWebLockOverlay: Boolean,
    onWebLockOverlayChange: (Boolean) -> Unit,
    webLockOverlayDomain: String,
    showTabSwitcher: Boolean,
    onTabSwitcherChange: (Boolean) -> Unit,
    showSearchOverlay: Boolean,
    onSearchOverlayChange: (Boolean) -> Unit,
    showMenuSheet: Boolean,
    onMenuSheetChange: (Boolean) -> Unit,
    showSiteSettingsDialog: Boolean,
    onSiteSettingsDialogChange: (Boolean) -> Unit,
    showFindInPage: Boolean,
    onFindInPageChange: (Boolean) -> Unit,
    findInPageQuery: String,
    onFindInPageQueryChange: (String) -> Unit,
    findInPageResult: BrowserViewModel.FindInPageResult?,
    activeMediaSessionId: String?,
    context: android.content.Context,
    translateBar: @Composable (BrowserTab, Modifier) -> Unit = { _, _ -> }
) {
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp > 600

    var isSplitActive by remember { mutableStateOf(false) }
    var leftTabId by remember { mutableStateOf<String?>(null) }
    var rightTabId by remember { mutableStateOf<String?>(null) }
    var showDashboardOnRight by remember { mutableStateOf(false) }
    var pendingNewTabAsSecondary by remember { mutableStateOf(false) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var rowWidthPx by remember { mutableStateOf(0f) }
    var activePane by remember { mutableStateOf(0) } // 0 for Left, 1 for Right

    // Initialize leftTabId when split is activated
    LaunchedEffect(isSplitActive) {
        if (isSplitActive) {
            showDashboardOnRight = rightTabId == null
            if (leftTabId == null) {
                leftTabId = tabs.getOrNull(activeTabIndex)?.id
            }
        } else {
            leftTabId = null
            rightTabId = null
            showDashboardOnRight = false
            splitRatio = 0.5f
        }
    }

    // Dynamic CSS and DOM injection to isolate the video element in PiP mode
    LaunchedEffect(isInPip) {
        val js = if (isInPip) {
            """
            (function() {
                window.__yue_in_pip__ = true;
                var video = document.querySelector('video');
                if (!video) return;
                
                // Inject style sheet to force widescreen centering and override any inline styles/transformations
                var style = document.getElementById('yue-pip-video-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'yue-pip-video-style';
                    style.textContent = 'video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important; transform: none !important; z-index: 2147483647 !important; background: black !important; object-fit: contain !important; } body { overflow: hidden !important; }';
                    document.documentElement.appendChild(style);
                }
                
                if (window._yue_pip_active) return;
                window._yue_pip_active = true;
                window._yue_pip_parent = video.parentNode;
                window._yue_pip_next_sibling = video.nextSibling;
                window._yue_original_body_overflow = document.body.style.overflow;
                
                // Hide all direct children of body except the video
                window._yue_pip_hidden_elements = [];
                var children = document.body.children;
                for (var i = 0; i < children.length; i++) {
                    var child = children[i];
                    if (child !== video && child.tagName !== 'SCRIPT' && child.tagName !== 'STYLE') {
                        child._yue_original_display = child.style.display;
                        child.style.setProperty('display', 'none', 'important');
                        window._yue_pip_hidden_elements.push(child);
                    }
                }
                
                // Move video to body
                document.body.appendChild(video);
                
                // Play it again because moving it in the DOM pauses it
                try {
                    video.play();
                } catch(e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                window.__yue_in_pip__ = false;
                
                // Remove the style sheet
                var style = document.getElementById('yue-pip-video-style');
                if (style) style.remove();
                
                var video = document.querySelector('video');
                if (!video) return;
                if (!window._yue_pip_active) return;
                
                try {
                    var wasPaused = video.paused;
                    if (window._yue_pip_parent && window._yue_pip_next_sibling) {
                        window._yue_pip_parent.insertBefore(video, window._yue_pip_next_sibling);
                    } else if (window._yue_pip_parent) {
                        window._yue_pip_parent.appendChild(video);
                    }
                    // Restore body children visibility
                    if (window._yue_pip_hidden_elements) {
                        window._yue_pip_hidden_elements.forEach(function(el) {
                            el.style.display = el._yue_original_display || '';
                        });
                    }
                    document.body.style.overflow = window._yue_original_body_overflow || '';
                    
                    // Play it again if it was playing before
                    if (!wasPaused) {
                        video.play();
                    }
                } catch(e) {}
                window._yue_pip_active = false;
            })();
            """.trimIndent()
        }
        try {
            val webView = activeTab.session.view as? android.webkit.WebView
            webView?.evaluateJavascript(js, null)
        } catch (e: Exception) {}
    }

    // Handle tab switching from the top tab strip:
    // If the user selects a tab not currently in the split view, replace the active pane's tab.
    LaunchedEffect(activeTabIndex, tabs) {
        val currentActiveId = tabs.getOrNull(activeTabIndex)?.id
        if (isSplitActive && currentActiveId != null) {
            if (currentActiveId != leftTabId && currentActiveId != rightTabId) {
                if (activePane == 1) {
                    rightTabId = currentActiveId
                    showDashboardOnRight = false
                } else {
                    leftTabId = currentActiveId
                }
            }
        }
    }

    // Close split screen if either of the split tabs is closed
    LaunchedEffect(tabs, leftTabId, rightTabId, isSplitActive, showDashboardOnRight) {
        if (isSplitActive) {
            val hasLeft = leftTabId != null && tabs.any { it.id == leftTabId }
            val hasRight = showDashboardOnRight || (rightTabId != null && tabs.any { it.id == rightTabId })
            if (!hasLeft || !hasRight) {
                isSplitActive = false
                leftTabId = null
                rightTabId = null
                showDashboardOnRight = false
            }
        }
    }

    // Handle pending new tab for the right pane
    LaunchedEffect(tabs) {
        if (pendingNewTabAsSecondary && isSplitActive) {
            val targetIdx = activeTabIndex
            if (targetIdx in tabs.indices) {
                rightTabId = tabs[targetIdx].id
                pendingNewTabAsSecondary = false
            }
        }
    }

    val columnModifier = if (isInPip) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
    }
    Column(modifier = columnModifier) {
        // Progress Bar
        if (!isInPip) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.Transparent)
            ) {
                if (activeTab.progress < 100 && !isStartPage) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(activeTab.progress / 100f)
                            .background(if (activeTab.isPrivate) Color.Red else MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Tablet/Desktop Top Bar
        if (isTablet && !isInPip) {
            BrowserTopBar(
                tabs = tabs,
                activeTabIndex = activeTabIndex,
                isDarkMode = settings.isDarkModeSimulated,
                onTabClick = { viewModel.selectTab(it) },
                onCloseTabClick = { viewModel.closeTab(it, context, notifyUndo = true) },
                onNewTabClick = { viewModel.createNewTab(context, "yue://newtab", isPrivate = activeTab.isPrivate) },
                onBackClick = { viewModel.tryBackPressInActiveTab() },
                onForwardClick = { viewModel.tryForwardPressInActiveTab() },
                onReloadClick = { activeTab.session.reload() },
                onHomeClick = { viewModel.loadUriInActiveTab("yue://newtab") },
                onUrlClick = { onSearchOverlayChange(true) },
                onMenuClick = {
                    try {
                        val webView = try { activeTab.session.view } catch (e: Exception) { null }
                        webView?.clearFocus()
                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        if (webView != null) {
                            try {
                                imm?.hideSoftInputFromWindow(webView.windowToken, 0)
                            } catch (e: Exception) { /* ignore */ }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainBrowserScreen", "Error in menu click", e)
                    }
                    onMenuSheetChange(true)
                },
                isSplitActive = isSplitActive,
                onSplitToggle = {
                    if (isSplitActive) {
                        isSplitActive = false
                        leftTabId = null
                        rightTabId = null
                        showDashboardOnRight = false
                    } else {
                        isSplitActive = true
                        showDashboardOnRight = true
                        leftTabId = tabs.getOrNull(activeTabIndex)?.id
                        rightTabId = null
                    }
                },
                secondaryTabId = rightTabId,
                activeTab = activeTab,
                isStartPage = isStartPage,
                searchEngineUrl = settings.searchEngineUrl
            )
        }

        // Main webview body or native home screen (wrapped in Box for lock overlay)
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds()
        ) {
            val showSplit = isSplitActive && !isInPip
            LaunchedEffect(isInPip, isSplitActive) {
                android.util.Log.d("YuePip", "showSplit calculated: $showSplit, isSplitActive: $isSplitActive, isInPip: $isInPip")
            }

            if (showSplit) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            rowWidthPx = coordinates.size.width.toFloat()
                        }
                ) {
                    // Left Pane (Primary Tab)
                    val leftTabIdx = tabs.indexOfFirst { it.id == leftTabId }
                    val leftTab = tabs.getOrNull(leftTabIdx)
                    if (leftTab != null) {
                        Box(
                            modifier = Modifier
                                .weight(splitRatio)
                                .fillMaxHeight()
                                .border(
                                    width = 1.dp,
                                    color = if (activePane == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    activePane = 0
                                    if (leftTabIdx != -1 && leftTabIdx != activeTabIndex) {
                                        viewModel.selectTab(leftTabIdx)
                                    }
                                }
                        ) {
                            val isTabStartPage = leftTab.url.isBlank() || leftTab.url == "about:blank" || leftTab.url == "yue://newtab"
                            if (!isTabStartPage) {
                                BrowserWebView(
                                    activeTab = leftTab,
                                    onReload = { leftTab.session.reload() },
                                    onScrollChanged = { visible -> if (activePane == 0) onBottomBarVisibleChange(visible) },
                                    isGone = isFullscreenOverlayVisible || showWebLockOverlay,
                                    modifier = Modifier.fillMaxSize(),
                                    onTouch = {
                                        activePane = 0
                                        if (leftTabIdx != -1 && leftTabIdx != activeTabIndex) {
                                            viewModel.selectTab(leftTabIdx)
                                        }
                                    }
                                )
                                translateBar(leftTab, Modifier.fillMaxSize().zIndex(10f))
                            } else {
                                val combinedSpeedDials = rememberCombinedSpeedDials(settings, historyList)
                                NewTabHomeScreen(
                                    speedDials = combinedSpeedDials,
                                    searchEngineUrl = settings.searchEngineUrl,
                                    onSearchClick = { onSearchOverlayChange(true) },
                                    onSpeedDialClick = { url ->
                                        viewModel.selectTab(leftTabIdx)
                                        viewModel.loadUriInActiveTab(url)
                                    },
                                    isIncognito = leftTab.isPrivate,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Vertical Divider (Draggable)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                            .zIndex(1f)
                            .pointerInput(rowWidthPx) {
                                if (rowWidthPx > 0f) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newRatio = splitRatio + (dragAmount / rowWidthPx)
                                        splitRatio = newRatio.coerceIn(0.2f, 0.8f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }

                    // Right Pane (Secondary Tab or Dashboard)
                    Box(
                        modifier = Modifier
                            .weight(1f - splitRatio)
                            .fillMaxHeight()
                            .border(
                                width = 1.dp,
                                color = if (activePane == 1 && !showDashboardOnRight) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                            )
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                activePane = 1
                                val rightTabIdx = tabs.indexOfFirst { it.id == rightTabId }
                                if (rightTabIdx != -1 && rightTabIdx != activeTabIndex) {
                                    viewModel.selectTab(rightTabIdx)
                                }
                            }
                    ) {
                        if (showDashboardOnRight) {
                            SplitDashboard(
                                tabs = tabs,
                                activeTabIndex = activeTabIndex,
                                secondaryTabIndex = -1,
                                settings = settings,
                                historyList = historyList,
                                onTabSelected = { selectedIdx ->
                                    val selectedId = tabs.getOrNull(selectedIdx)?.id
                                    rightTabId = selectedId
                                    showDashboardOnRight = false
                                    activePane = 1
                                    if (selectedIdx != -1) {
                                        viewModel.selectTab(selectedIdx)
                                    }
                                },
                                onUrlSelected = { url ->
                                    viewModel.createNewTab(context, url, isPrivate = activeTab.isPrivate)
                                    pendingNewTabAsSecondary = true
                                    showDashboardOnRight = false
                                },
                                onClose = {
                                    val leftIdx = tabs.indexOfFirst { it.id == leftTabId }
                                    if (leftIdx != -1 && leftIdx != activeTabIndex) {
                                        viewModel.selectTab(leftIdx)
                                    }
                                    isSplitActive = false
                                    leftTabId = null
                                    rightTabId = null
                                    showDashboardOnRight = false
                                },
                                onSearchClick = {
                                    viewModel.createNewTab(context, "yue://newtab", isPrivate = activeTab.isPrivate)
                                    pendingNewTabAsSecondary = true
                                    showDashboardOnRight = false
                                    onSearchOverlayChange(true)
                                }
                            )
                        } else {
                            val rightTabIdx = tabs.indexOfFirst { it.id == rightTabId }
                            val secTab = tabs.getOrNull(rightTabIdx)
                            if (secTab != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    BrowserWebView(
                                        activeTab = secTab,
                                        onReload = { secTab.session.reload() },
                                        onScrollChanged = { visible -> if (activePane == 1) onBottomBarVisibleChange(visible) },
                                        isGone = isFullscreenOverlayVisible || showWebLockOverlay,
                                        modifier = Modifier.fillMaxSize(),
                                        onTouch = {
                                            activePane = 1
                                            if (rightTabIdx != -1 && rightTabIdx != activeTabIndex) {
                                                viewModel.selectTab(rightTabIdx)
                                            }
                                        }
                                    )
                                    translateBar(secTab, Modifier.fillMaxSize().zIndex(10f))
                                    // Close button overlay
                                    IconButton(
                                        onClick = {
                                            val leftIdx = tabs.indexOfFirst { it.id == leftTabId }
                                            if (leftIdx != -1 && leftIdx != activeTabIndex) {
                                                viewModel.selectTab(leftIdx)
                                            }
                                            isSplitActive = false
                                            leftTabId = null
                                            rightTabId = null
                                            showDashboardOnRight = false
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close split",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Render only the active tab and the tab playing background media to avoid CPU/memory stutter
                tabs.forEachIndexed { idx, tab ->
                    val isTabStartPage = tab.url.isBlank() || tab.url == "about:blank" || tab.url == "yue://newtab"
                    if (!isTabStartPage) {
                        val shouldRender = idx == activeTabIndex || tab.id == activeMediaSessionId
                        if (shouldRender) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                BrowserWebView(
                                    activeTab = tab,
                                    onReload = { tab.session.reload() },
                                    onScrollChanged = { visible -> if (idx == activeTabIndex) onBottomBarVisibleChange(visible) },
                                    isGone = idx != activeTabIndex || isFullscreenOverlayVisible || showWebLockOverlay,
                                    modifier = Modifier.fillMaxSize()
                                )
                                translateBar(tab, Modifier.fillMaxSize().zIndex(10f))
                            }
                        }
                    }
                }

                if (isStartPage && !isInPip) {
                    val combinedSpeedDials = rememberCombinedSpeedDials(settings, historyList)
                    NewTabHomeScreen(
                        speedDials = combinedSpeedDials,
                        searchEngineUrl = settings.searchEngineUrl,
                        onSearchClick = { onSearchOverlayChange(true) },
                        onSpeedDialClick = { url ->
                            viewModel.loadUriInActiveTab(url)
                        },
                        isIncognito = activeTab.isPrivate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Find in Page bar
            if (showFindInPage && !isStartPage) {
                FindInPageBar(
                    query = findInPageQuery,
                    onQueryChange = onFindInPageQueryChange,
                    onNext = { viewModel.findInPageNext(true) },
                    onPrevious = { viewModel.findInPageNext(false) },
                    onClose = {
                        onFindInPageChange(false)
                        onFindInPageQueryChange("")
                        viewModel.clearFindInPage()
                    },
                    result = findInPageResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp, start = 8.dp, end = 8.dp)
                )
            }

            // Web Lock Overlay — only over webview, nav bar remains accessible
            if (showWebLockOverlay) {
                val hasBio = isBiometricAvailable(context)
                WebLockOverlay(
                    domain = webLockOverlayDomain,
                    onUnlocked = {
                        viewModel.unlockDomainForTab(activeTab.id, webLockOverlayDomain)
                        onWebLockOverlayChange(false)
                    },
                    onVerifyPin = { pin -> viewModel.verifyWebLockPin(pin) },
                    hasBiometric = hasBio,
                    isDark = settings.isDarkModeSimulated,
                    onBiometricRequest = {
                        val fragActivity = context as? androidx.fragment.app.FragmentActivity
                        if (fragActivity != null) {
                            showBiometricPrompt(
                                activity = fragActivity,
                                onSuccess = {
                                    viewModel.unlockDomainForTab(activeTab.id, webLockOverlayDomain)
                                    onWebLockOverlayChange(false)
                                },
                                onFailed = {}
                            )
                        }
                    },
                    maxAttempts = settings.webLockMaxAttempts,
                    lockDurationMinutes = settings.webLockLockDurationMinutes,
                    attemptsEnabled = settings.webLockAttemptsEnabled
                )
            }
        }
        if (!isInPip && !isTablet) {
            BrowserBottomBar(
                isVisible = isBottomBarVisible,
                activeTab = activeTab,
                isStartPage = isStartPage,
                showTabSwitcher = showTabSwitcher,
                isDarkMode = settings.isDarkModeSimulated,
                tabs = tabs,
                onBackClick = { viewModel.tryBackPressInActiveTab() },
                onForwardClick = { viewModel.tryForwardPressInActiveTab() },
                onUrlClick = { onSearchOverlayChange(true) },
                onUrlLongClick = { if (!isStartPage) onSiteSettingsDialogChange(true) },
                searchEngineUrl = settings.searchEngineUrl,
                onTabSwitcherClick = {
                    if (!showTabSwitcher) {
                        val currentTab = tabs.getOrNull(activeTabIndex)
                        if (currentTab != null && currentTab.url != "yue://newtab" && currentTab.url.isNotBlank()) {
                            currentTab.session.captureThumbnail { bitmap ->
                                viewModel.updateTabThumbnail(activeTabIndex, bitmap)
                            }
                        }
                    }
                    onTabSwitcherChange(!showTabSwitcher)
                },
                onMenuClick = {
                    try {
                        val webView = try { activeTab.session.view } catch (e: Exception) { null }
                        webView?.clearFocus()
                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        if (webView != null) {
                            try {
                                imm?.hideSoftInputFromWindow(webView.windowToken, 0)
                            } catch (e: Exception) { /* ignore */ }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainBrowserScreen", "Error in menu click", e)
                    }
                    onMenuSheetChange(true)
                }
            )
        }
    }
}

@Composable
fun SplitDashboard(
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    secondaryTabIndex: Int,
    settings: BrowserSettings,
    historyList: List<HistoryItem>,
    onTabSelected: (Int) -> Unit,
    onUrlSelected: (String) -> Unit,
    onClose: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Tabs, 1: Frequently Visited

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(16.dp)
    ) {
        // Header with Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close split screen",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                .clickable { onSearchClick() }
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Search or enter web address",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tabs Header Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { selectedSection = 0 }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Tabs you've opened",
                    fontSize = 14.sp,
                    fontWeight = if (selectedSection == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedSection == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedSection == 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .width(40.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { selectedSection = 1 }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Frequently visited",
                    fontSize = 14.sp,
                    fontWeight = if (selectedSection == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedSection == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedSection == 1) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .width(40.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content Grid
        if (selectedSection == 0) {
            // Opened Tabs
            val otherTabs = remember(tabs, activeTabIndex) {
                tabs.mapIndexed { idx, tab -> idx to tab }.filter { it.first != activeTabIndex }
            }

            if (otherTabs.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No other open tabs",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(otherTabs.size) { i ->
                        val (originalIdx, tab) = otherTabs[i]
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { onTabSelected(originalIdx) }
                        ) {
                            // Thumbnail placeholder / image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.thumbnail != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = tab.thumbnail!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = tab.title.take(1).uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            // Favicon + Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.title.take(1).uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title.ifBlank { "Untitled" },
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Frequently Visited (Speed Dials)
            val combinedSpeedDials = rememberCombinedSpeedDials(settings, historyList)
            if (combinedSpeedDials.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No frequently visited sites",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(combinedSpeedDials.size) { i ->
                        val dial = combinedSpeedDials[i]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUrlSelected(dial.url) }
                                .padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dial.iconLetter,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = dial.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}


