package com.yue.browser.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    context: android.content.Context
) {
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

        // Main webview body or native home screen (wrapped in Box for lock overlay)
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds()
        ) {
            // Render only the active tab and the tab playing background media to avoid CPU/memory stutter
            tabs.forEachIndexed { idx, tab ->
                val isTabStartPage = tab.url.isBlank() || tab.url == "about:blank" || tab.url == "yue://newtab"
                if (!isTabStartPage) {
                    val shouldRender = idx == activeTabIndex || tab.id == activeMediaSessionId
                    if (shouldRender) {
                        BrowserWebView(
                            activeTab = tab,
                            onReload = { tab.session.reload() },
                            onScrollChanged = { visible -> if (idx == activeTabIndex) onBottomBarVisibleChange(visible) },
                            isGone = idx != activeTabIndex || isFullscreenOverlayVisible || showWebLockOverlay,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (isStartPage && !isInPip) {
                val combinedSpeedDials = rememberCombinedSpeedDials(settings, historyList)
                NewTabHomeScreen(
                    speedDials = combinedSpeedDials,
                    onSearchClick = { onSearchOverlayChange(true) },
                    onSpeedDialClick = { url ->
                        viewModel.loadUriInActiveTab(url)
                    },
                    isIncognito = activeTab.isPrivate,
                    modifier = Modifier.fillMaxSize()
                )
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
                    }
                )
            }
        }
        if (!isInPip) {
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
