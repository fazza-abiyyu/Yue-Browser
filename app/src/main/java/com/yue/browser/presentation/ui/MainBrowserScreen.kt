package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.presentation.ui.tabswitcher.TabSwitcherScreen
import com.yue.browser.presentation.*
import com.yue.browser.data.engine.MediaSessionManager
import com.yue.browser.presentation.ui.components.BottomTranslateBar
import com.yue.browser.presentation.ui.components.BrowserBottomBar
import com.yue.browser.presentation.ui.components.IncognitoLockScreen
import com.yue.browser.presentation.ui.components.MenuDrawerSheet
import com.yue.browser.presentation.ui.components.WelcomeScreen
import com.yue.browser.presentation.ui.components.SiteSettingsDialog
import com.yue.browser.presentation.ui.components.TopTranslateBar
import com.yue.browser.presentation.ui.components.SearchOverlay
import com.yue.browser.presentation.ui.components.formatUrlOrQuery
import com.yue.browser.presentation.ui.components.MainBrowserScreensOverlays
import com.yue.browser.presentation.ui.components.MainBrowserWebsitesLayout
import com.yue.browser.presentation.ui.components.UndoCloseTabBanner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainBrowserScreen(
    viewModel: BrowserViewModel
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val bookmarkList by viewModel.bookmarks.collectAsState()
    val activeMediaSessionId by MediaSessionManager.activeMediaSessionId.collectAsState()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showBookmarksScreen by remember { mutableStateOf(false) }
    var showOfflinePagesScreen by remember { mutableStateOf(false) }
    var showDownloadsScreen by remember { mutableStateOf(false) }
    var showAdblockFiltersScreen by remember { mutableStateOf(false) }
    var showPlaybackSettingsScreen by remember { mutableStateOf(false) }
    var showPrivateTabsOnly by remember { mutableStateOf(false) }
    var showTranslateBar by remember { mutableStateOf(false) }
    var showSiteSettingsDialog by remember { mutableStateOf(false) }
    var detectedLanguage by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf("auto") }
    val defaultTargetLanguage = remember {
        val systemLang = java.util.Locale.getDefault().language
        val mappedLang = if (systemLang == "id") "in" else systemLang
        val supportedLangs = listOf("in", "en", "zh", "ja", "ko", "fr", "de", "es", "pt", "ar", "hi")
        if (mappedLang in supportedLangs) mappedLang else "in"
    }
    var targetLanguage by remember { mutableStateOf(defaultTargetLanguage) }
    var isTranslating by remember { mutableStateOf(false) }
    var showSourceLanguageMenu by remember { mutableStateOf(false) }
    var showTargetLanguageMenu by remember { mutableStateOf(false) }
    var hasUnlockedIncognitoSession by remember { mutableStateOf(false) }
    var showManualUnlockDialog by remember { mutableStateOf(false) }
    var isElementPickerActive by remember { mutableStateOf(false) }
    var isDraggingTab by remember { mutableStateOf(false) }
    
    // Web Lock
    var showLockedWebsitesScreen by remember { mutableStateOf(false) }
    var showWebLockOverlay by remember { mutableStateOf(false) }
    var webLockOverlayDomain by remember { mutableStateOf("") }
    var showPasswordManagerScreen by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    var findInPageQuery by remember { mutableStateOf("") }
    val findInPageResult by viewModel.findInPageResult.collectAsState()
    val scope = rememberCoroutineScope()

    val lockedTabIds by remember(tabs, settings.lockedDomains, settings.webLockAutoLockTimeout) {
        derivedStateOf {
            tabs.filter { tab ->
                val url = tab.url
                if (url.isBlank() || url == "yue://newtab") false
                else {
                    val host = try { android.net.Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
                    host.isNotBlank() && viewModel.isDomainLockedForTab(tab.id, host)
                }
            }.map { it.id }.toSet()
        }
    }

    val isFullscreenOverlayVisible = showTabSwitcher || showSettingsScreen || showHistoryScreen || showBookmarksScreen || showOfflinePagesScreen || showDownloadsScreen || showAdblockFiltersScreen || showPasswordManagerScreen || showPlaybackSettingsScreen

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val activity = context as? android.app.Activity
    val fragmentActivity = context.findFragmentActivity()

    val isIncognitoLocked by remember {
        derivedStateOf {
            showPrivateTabsOnly && !hasUnlockedIncognitoSession && tabs.any { it.isPrivate }
        }
    }
    val window = activity?.window
    LaunchedEffect(activeTabIndex, tabs, settings.isDarkModeSimulated) {
        val activeTab = tabs.getOrNull(activeTabIndex)
        val isPrivate = activeTab?.isPrivate == true
        val isDark = settings.isDarkModeSimulated
        window?.let { w ->
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            val view = w.decorView
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(w, view)
            windowInsetsController.isAppearanceLightNavigationBars = !isPrivate && !isDark
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                val currentTabs = viewModel.tabs.value
                val currentIndex = viewModel.activeTabIndex.value
                val hasPrivateTabs = currentTabs.any { it.isPrivate }
                val isActivePrivate = currentTabs.getOrNull(currentIndex)?.isPrivate == true
                if (hasPrivateTabs) {
                    hasUnlockedIncognitoSession = false
                    if (showPrivateTabsOnly || isActivePrivate) {
                        showTabSwitcher = true
                        showPrivateTabsOnly = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activeTabIndex, tabs) {
        val activeTab = tabs.getOrNull(activeTabIndex)
        if (activeTab != null && activeTab.isPrivate && hasUnlockedIncognitoSession && !showTabSwitcher) {
            showPrivateTabsOnly = true
        }
    }

    LaunchedEffect(activeTabIndex, tabs, showTabSwitcher, showPrivateTabsOnly) {
        val activeTab = tabs.getOrNull(activeTabIndex)
        val isViewingPrivate = if (showTabSwitcher) {
            showPrivateTabsOnly
        } else {
            activeTab?.isPrivate == true
        }
        if (!isViewingPrivate) {
            hasUnlockedIncognitoSession = false
        }
    }

    LaunchedEffect(activeTabIndex) {
        showTranslateBar = false
    }

    LaunchedEffect(tabs) {
        val hasPrivateTabs = tabs.any { it.isPrivate }
        if (!hasPrivateTabs) {
            if (showPrivateTabsOnly) {
                showPrivateTabsOnly = false
            }
            hasUnlockedIncognitoSession = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.showDownloadsRequest.collect {
            showDownloadsScreen = true
        }
    }

    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            viewModel.restoreTabs(context)
            val restoredTabs = viewModel.tabs.value
            val hasNormalTabs = restoredTabs.any { !it.isPrivate }
            if (!hasNormalTabs) {
                viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
            }
            val hasPrivateTabs = restoredTabs.any { it.isPrivate }
            val activeTab = restoredTabs.getOrNull(viewModel.activeTabIndex.value)
            if (hasPrivateTabs) {
                hasUnlockedIncognitoSession = false
                if (showPrivateTabsOnly || activeTab?.isPrivate == true) {
                    showTabSwitcher = true
                    showPrivateTabsOnly = true
                }
            }
        }
        viewModel.initializeDownloads(context)
        viewModel.initializeHistory(context)
        viewModel.initializePasswords(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                try {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) { }
            }
        }
    }

    if (tabs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val safeActiveTab = tabs.getOrNull(activeTabIndex) ?: tabs.getOrNull(0)
    val activeTab = safeActiveTab ?: return@MainBrowserScreen
    val isStartPage = activeTab.url == "yue://newtab"
    var isBottomBarVisible by remember(activeTab.id, isStartPage) { mutableStateOf(true) }

    LaunchedEffect(activeTab.url, activeTab.progress) {
        if (activeTab.isTranslated && activeTab.progress >= 100) {
            viewModel.translatePage(activeTab.translationSource, activeTab.translationTarget)
        }
    }

    LaunchedEffect(activeTab.id, activeTab.translationSource) {
        if (activeTab.translationSource != "auto" && activeTab.translationSource.isNotBlank()) {
            sourceLanguage = activeTab.translationSource
        } else {
            sourceLanguage = "auto"
        }
    }

    LaunchedEffect(activeTab.url, activeTab.id, settings.lockedDomains) {
        val url = activeTab.url
        if (url.isBlank() || url == "yue://newtab") {
            showWebLockOverlay = false
            return@LaunchedEffect
        }
        val host = try { android.net.Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        if (host.isNotBlank() && viewModel.isDomainLockedForTab(activeTab.id, host)) {
            webLockOverlayDomain = host
            showWebLockOverlay = true
        } else {
            showWebLockOverlay = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.lockAllTabs()
                val url = tabs.getOrNull(activeTabIndex)?.url ?: ""
                val host = try { android.net.Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
                val tabId = tabs.getOrNull(activeTabIndex)?.id ?: ""
                if (host.isNotBlank() && viewModel.isDomainLockedForTab(tabId, host)) {
                    webLockOverlayDomain = host
                    showWebLockOverlay = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        when {
            isElementPickerActive -> {
                viewModel.stopElementPicker()
                isElementPickerActive = false
            }
            showTranslateBar -> {
                showTranslateBar = false
                viewModel.cancelTranslation()
            }
            showWebLockOverlay -> {
                showWebLockOverlay = false
                viewModel.loadUriInActiveTab("yue://newtab")
            }
            showLockedWebsitesScreen -> {
                showLockedWebsitesScreen = false
                showSettingsScreen = true
            }
            showPasswordManagerScreen -> {
                showPasswordManagerScreen = false
                showSettingsScreen = true
            }
            showPlaybackSettingsScreen -> {
                showPlaybackSettingsScreen = false
                showSettingsScreen = true
            }
            showAdblockFiltersScreen -> {
                showAdblockFiltersScreen = false
                showSettingsScreen = true
            }
            showBookmarksScreen -> showBookmarksScreen = false
            showOfflinePagesScreen -> showOfflinePagesScreen = false
            showHistoryScreen -> showHistoryScreen = false
            showDownloadsScreen -> showDownloadsScreen = false
            showSettingsScreen -> showSettingsScreen = false
            showMenuSheet -> showMenuSheet = false
            showTabSwitcher -> {
                viewModel.lastClosedTab.value = null
                showTabSwitcher = false
            }
            showSearchOverlay -> showSearchOverlay = false
            activeTab.session.combinedCanGoBack && !isStartPage -> {
                viewModel.tryBackPressInActiveTab()
            }
            !isStartPage -> {
                viewModel.handleBackNavigation()
            }
            else -> {
                val currentActiveTab = tabs.getOrNull(activeTabIndex)
                val shouldClose = currentActiveTab != null && (
                    currentActiveTab.parentTabId != null && tabs.any { it.id == currentActiveTab.parentTabId }
                )
                if (shouldClose) {
                    viewModel.closeTab(activeTabIndex, context, notifyUndo = false)
                } else {
                    val currentActivity = activity ?: context.findActivity()
                    currentActivity?.moveTaskToBack(true)
                }
            }
        }
    }

    val isInPip by viewModel.isInPipMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                        viewModel.notifyUserInteraction()
                    }
                }
            }
    ) {
        val isDarkModeActive = settings.isDarkModeSimulated
        val isWebviewLocked = activeTab.isPrivate && !hasUnlockedIncognitoSession
        val showWelcomeScreen = viewModel.showWelcomeScreen.value
        if (showWelcomeScreen) {
            WelcomeScreen(onStartClick = { viewModel.dismissWelcomeScreen() })
        } else if (isWebviewLocked && !isInPip && !showTabSwitcher) {
            IncognitoLockScreen(
                isDarkModeActive = isDarkModeActive,
                showNoAuthBypassText = fragmentActivity == null,
                onBiometricUnlock = {
                    val currentActivity = activity ?: context.findActivity()
                    if (currentActivity != null && fragmentActivity != null) {
                        showBiometricLock(currentActivity) { success ->
                            if (success) {
                                hasUnlockedIncognitoSession = true
                            }
                        }
                    } else {
                        hasUnlockedIncognitoSession = true
                    }
                },
                onOpenNormalTab = {
                    val firstNormalIdx = tabs.indexOfFirst { !it.isPrivate }
                    if (firstNormalIdx >= 0) {
                        viewModel.selectTab(firstNormalIdx)
                        showPrivateTabsOnly = false
                    } else {
                        viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        showPrivateTabsOnly = false
                    }
                }
            )
        } else {
            MainBrowserWebsitesLayout(
                viewModel = viewModel,
                tabs = tabs,
                activeTabIndex = activeTabIndex,
                settings = settings,
                historyList = historyList,
                activeTab = activeTab,
                isStartPage = isStartPage,
                isInPip = isInPip,
                isBottomBarVisible = isBottomBarVisible,
                onBottomBarVisibleChange = { isBottomBarVisible = it },
                isFullscreenOverlayVisible = isFullscreenOverlayVisible,
                showWebLockOverlay = showWebLockOverlay,
                onWebLockOverlayChange = { showWebLockOverlay = it },
                webLockOverlayDomain = webLockOverlayDomain,
                showTabSwitcher = showTabSwitcher,
                onTabSwitcherChange = { showTabSwitcher = it },
                showSearchOverlay = showSearchOverlay,
                onSearchOverlayChange = { showSearchOverlay = it },
                showMenuSheet = showMenuSheet,
                onMenuSheetChange = { showMenuSheet = it },
                showSiteSettingsDialog = showSiteSettingsDialog,
                onSiteSettingsDialogChange = { showSiteSettingsDialog = it },
                showFindInPage = showFindInPage,
                onFindInPageChange = { showFindInPage = it },
                findInPageQuery = findInPageQuery,
                onFindInPageQueryChange = { findInPageQuery = it },
                findInPageResult = findInPageResult,
                activeMediaSessionId = activeMediaSessionId,
                context = context,
                translateBar = { tab, modifier ->
                    if (tab.id == activeTab.id) {
                        Box(modifier = modifier) {
                            TopTranslateBar(
                                modifier = Modifier.align(Alignment.TopCenter).zIndex(4f),
                                activeTab = activeTab,
                                isStartPage = isStartPage,
                                showTabSwitcher = showTabSwitcher,
                                showHistoryScreen = showHistoryScreen,
                                showBookmarksScreen = showBookmarksScreen,
                                showSettingsScreen = showSettingsScreen,
                                showDownloadsScreen = showDownloadsScreen,
                                showAdblockFiltersScreen = showAdblockFiltersScreen,
                                isTranslating = isTranslating,
                                isBottomBarVisible = isBottomBarVisible,
                                isDarkMode = settings.isDarkModeSimulated,
                                onCancel = {
                                    viewModel.cancelTranslation()
                                    showTranslateBar = false
                                },
                                onRetry = {
                                    isTranslating = true
                                    viewModel.translatePage(activeTab.translationSource, activeTab.translationTarget)
                                    scope.launch {
                                        delay(4000)
                                        isTranslating = false
                                    }
                                }
                            )

                            BottomTranslateBar(
                                modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f),
                                showTranslateBar = showTranslateBar,
                                activeTab = activeTab,
                                isStartPage = isStartPage,
                                showTabSwitcher = showTabSwitcher,
                                showHistoryScreen = showHistoryScreen,
                                showBookmarksScreen = showBookmarksScreen,
                                showSettingsScreen = showSettingsScreen,
                                showDownloadsScreen = showDownloadsScreen,
                                showAdblockFiltersScreen = showAdblockFiltersScreen,
                                isBottomBarVisible = isBottomBarVisible,
                                isDarkMode = settings.isDarkModeSimulated,
                                sourceLanguage = sourceLanguage,
                                targetLanguage = targetLanguage,
                                showSourceLanguageMenu = showSourceLanguageMenu,
                                showTargetLanguageMenu = showTargetLanguageMenu,
                                isTranslating = isTranslating,
                                onSourceLanguageChange = { sourceLanguage = it },
                                onTargetLanguageChange = { targetLanguage = it },
                                onSourceLanguageMenuChange = { showSourceLanguageMenu = it },
                                onTargetLanguageMenuChange = { showTargetLanguageMenu = it },
                                onTranslate = {
                                    isTranslating = true
                                    viewModel.translatePage(sourceLanguage, targetLanguage)
                                    scope.launch {
                                        delay(4000)
                                        isTranslating = false
                                    }
                                },
                                onDismiss = { showTranslateBar = false }
                            )
                        }
                    }
                }
            )
        }

        if (showTabSwitcher) {
            TabSwitcherScreen(
                tabs = tabs,
                lockedTabIds = lockedTabIds,
                activeTabIndex = activeTabIndex,
                showPrivateTabsOnly = showPrivateTabsOnly,
                groups = groups,
                onDragStateChanged = { isDraggingTab = it },
                onCreateGroup = { name, color, tabIds -> viewModel.createGroup(name, color, tabIds) },
                onAddTabToGroup = { tabId, groupId -> viewModel.addTabToGroup(tabId, groupId) },
                onRemoveTabFromGroup = { tabId -> viewModel.removeTabFromGroup(tabId) },
                onRenameGroup = { groupId, newName -> viewModel.renameGroup(groupId, newName) },
                onUpdateGroupColor = { groupId, colorIndex -> viewModel.updateGroupColor(groupId, colorIndex) },
                onDeleteGroup = { groupId -> viewModel.deleteGroup(groupId) },
                onMoveTab = { from, to -> viewModel.moveTab(from, to) },
                onCreateTabInGroup = { gId ->
                    viewModel.createNewTab(context, "yue://newtab", isPrivate = showPrivateTabsOnly)
                    val lastTab = viewModel.tabs.value.lastOrNull()
                    if (lastTab != null) {
                        viewModel.addTabToGroup(lastTab.id, gId)
                    }
                    showTabSwitcher = false
                },
                onPrivateToggle = { isPrivate ->
                    if (!showPrivateTabsOnly && isPrivate) {
                        showPrivateTabsOnly = true
                        hasUnlockedIncognitoSession = false
                    } else if (showPrivateTabsOnly && !isPrivate) {
                        showPrivateTabsOnly = false
                        hasUnlockedIncognitoSession = false
                        val firstNormalIdx = viewModel.tabs.value.indexOfFirst { !it.isPrivate }
                        if (firstNormalIdx >= 0) {
                            viewModel.selectTab(firstNormalIdx)
                        } else {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        }
                    } else {
                        showPrivateTabsOnly = isPrivate
                    }
                },
                onTabSelect = { index ->
                    viewModel.selectTab(index)
                    showTabSwitcher = false
                },
                onTabClose = { index ->
                    viewModel.closeTab(index, context)
                    val afterTabs = viewModel.tabs.value
                    val shownTypeRemaining = afterTabs.count { it.isPrivate == showPrivateTabsOnly }
                    if (showPrivateTabsOnly) {
                        if (shownTypeRemaining == 0) {
                            showPrivateTabsOnly = false
                            hasUnlockedIncognitoSession = false
                            showTabSwitcher = false
                        }
                    } else {
                        if (shownTypeRemaining == 0) {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        }
                    }
                },
                onCloseAll = {
                    if (showPrivateTabsOnly) {
                        viewModel.closePrivateTabsOnly()
                        showPrivateTabsOnly = false
                        hasUnlockedIncognitoSession = false
                        if (viewModel.tabs.value.none { !it.isPrivate }) {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        }
                    } else {
                        viewModel.closeAllTabs(context)
                    }
                    showTabSwitcher = false
                },
                onSettingsClick = { showMenuSheet = true },
                isAppDarkMode = settings.isDarkModeSimulated,
                isIncognitoLocked = isIncognitoLocked,
                onUnlock = {
                    val currentActivity = activity ?: context.findActivity()
                    if (currentActivity != null && fragmentActivity != null) {
                        showBiometricLock(currentActivity) { success ->
                            if (success) {
                                hasUnlockedIncognitoSession = true
                            }
                        }
                    } else {
                        hasUnlockedIncognitoSession = true
                    }
                },
            )
        }

        val isOtherOverlayVisible = showSettingsScreen || showHistoryScreen || showBookmarksScreen || showOfflinePagesScreen || showDownloadsScreen || showAdblockFiltersScreen || showPasswordManagerScreen || showPlaybackSettingsScreen || showLockedWebsitesScreen
        if (showTabSwitcher && !isDraggingTab && !isIncognitoLocked && !isOtherOverlayVisible) {
            val fabColor = if (showPrivateTabsOnly) Color(0xFFFF002C) else Color(0xFFEC4899)
            val fabBg = if (showPrivateTabsOnly) {
                if (settings.isDarkModeSimulated) Color(0xFF1A1A1C).copy(alpha = 0.8f) else Color(0xFFF5F5F5).copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(fabBg)
                        .border(1.5.dp, fabColor, CircleShape)
                        .clickable {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = showPrivateTabsOnly)
                            if (showPrivateTabsOnly) {
                                hasUnlockedIncognitoSession = true
                            }
                            showTabSwitcher = false
                            showSearchOverlay = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_tab),
                        tint = fabColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }



        if (showMenuSheet) {
            val currentUrl = activeTab?.url ?: "yue://newtab"
            val rawDomain = try { android.net.Uri.parse(currentUrl).host ?: "" } catch(e: Exception) { "" }
            val currentDomain = rawDomain.removePrefix("m.").removePrefix("www.")
            MenuDrawerSheet(
                version = "Version 4.2.0-stable",
                isDesktopSite = settings.desktopDomains.contains(currentDomain),
                onDesktopSiteToggle = {
                    if (currentDomain.isNotEmpty()) {
                        viewModel.toggleDesktopSite(currentDomain, it)
                        if (it && currentUrl.contains("m.$currentDomain")) {
                            val newUrl = currentUrl.replace("m.$currentDomain", "www.$currentDomain") + (if (currentUrl.contains("?")) "&" else "?") + "force_desktop=1"
                            viewModel.loadUriInActiveTab(newUrl)
                        } else {
                            viewModel.reloadActiveTab()
                        }
                    }
                },
                isDarkMode = settings.appThemeMode == "dark",
                onDarkModeToggle = { wantDark ->
                    viewModel.setAppThemeMode(if (wantDark) "dark" else "light")
                },
                onDismiss = { showMenuSheet = false },
                onNavigate = { link ->
                    viewModel.loadUriInActiveTab(link)
                    showMenuSheet = false
                },
                onSettingsClick = { showSettingsScreen = true },
                onBookmarksClick = { showBookmarksScreen = true },
                onHistoryClick = { showHistoryScreen = true },
                onDownloadsClick = { showDownloadsScreen = true },
                onAddBookmarkClick = { ctx -> viewModel.toggleBookmark(ctx) },
                onNewIncognitoTab = {
                    viewModel.createNewTab(context, "yue://newtab", isPrivate = true)
                    hasUnlockedIncognitoSession = true
                    showMenuSheet = false
                },
                onShareUrl = { url ->
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = android.content.ClipData.newPlainText("URL", url)
                    clipboard.setPrimaryClip(clipData)
                    android.widget.Toast.makeText(context, context.getString(R.string.browser_url_copied), android.widget.Toast.LENGTH_SHORT).show()
                },
                onTranslateClick = {
                    showTranslateBar = true
                },
                onBlockSelectorClick = {
                    val currentHost = try {
                        android.net.Uri.parse(activeTab.url).host ?: ""
                    } catch (e: Exception) { "" }
                    if (currentHost.isNotEmpty() && !activeTab.url.startsWith("yue://")) {
                        isElementPickerActive = true
                        viewModel.startElementPicker(
                            onElementsPicked = { cssSelectors ->
                                isElementPickerActive = false
                                cssSelectors.forEach { viewModel.addBlockedCssSelector(currentHost, it) }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.browser_elements_blocked, cssSelectors.size, currentHost),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onCancel = {
                                isElementPickerActive = false
                            }
                        )
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.browser_open_webpage_first), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveOfflineClick = {
                    viewModel.saveCurrentPageOffline(context)
                    showMenuSheet = false
                },
                onOfflinePagesClick = {
                    showOfflinePagesScreen = true
                    showMenuSheet = false
                },
                onFindInPageClick = {
                    showFindInPage = true
                    showMenuSheet = false
                },
                currentUrl = activeTab.url,
                activeTab = activeTab
            )
        }

        if (showSearchOverlay) {
            SearchOverlay(
                initialInput = activeTab.url,
                history = historyList,
                bookmarks = bookmarkList,
                onRemoveHistory = { url -> viewModel.removeHistory(url) },
                onDismiss = { showSearchOverlay = false },
                onSearch = { query ->
                    val trimmed = query.trim()
                    if (trimmed.isEmpty()) {
                        if (activeTab.url != "yue://newtab" && activeTab.url.isNotBlank()) {
                            viewModel.reloadActiveTab()
                        }
                    } else {
                        val destination = formatUrlOrQuery(trimmed, settings.searchEngineUrl)
                        viewModel.loadUriInActiveTab(destination)
                    }
                    showSearchOverlay = false
                },
                isDarkMode = settings.isDarkModeSimulated,
                searchEngineUrl = settings.searchEngineUrl,
                isPrivate = activeTab.isPrivate
            )
        }

        MainBrowserScreensOverlays(
            viewModel = viewModel,
            showSettingsScreen = showSettingsScreen,
            onSettingsScreenChange = { showSettingsScreen = it },
            showPlaybackSettingsScreen = showPlaybackSettingsScreen,
            onPlaybackSettingsScreenChange = { showPlaybackSettingsScreen = it },
            showHistoryScreen = showHistoryScreen,
            onHistoryScreenChange = { showHistoryScreen = it },
            showBookmarksScreen = showBookmarksScreen,
            onBookmarksScreenChange = { showBookmarksScreen = it },
            showOfflinePagesScreen = showOfflinePagesScreen,
            onOfflinePagesScreenChange = { showOfflinePagesScreen = it },
            showDownloadsScreen = showDownloadsScreen,
            onDownloadsScreenChange = { showDownloadsScreen = it },
            showAdblockFiltersScreen = showAdblockFiltersScreen,
            onAdblockFiltersScreenChange = { showAdblockFiltersScreen = it },
            showLockedWebsitesScreen = showLockedWebsitesScreen,
            onLockedWebsitesScreenChange = { showLockedWebsitesScreen = it },
            showPasswordManagerScreen = showPasswordManagerScreen,
            onPasswordManagerScreenChange = { showPasswordManagerScreen = it },
            context = context
        )

        if (showTabSwitcher) {
            UndoCloseTabBanner(
                viewModel = viewModel,
                isBottomBarVisible = isBottomBarVisible,
                isDarkMode = settings.isDarkModeSimulated,
                context = context
            )
        }

        if (showSiteSettingsDialog) {
            SiteSettingsDialog(
                activeTab = activeTab,
                settings = settings,
                viewModel = viewModel,
                onDismiss = { showSiteSettingsDialog = false },
                onWebsiteLocked = { cleanHost ->
                    android.widget.Toast.makeText(context, context.getString(R.string.browser_website_locked, cleanHost), android.widget.Toast.LENGTH_SHORT).show()
                },
                onWebsiteUnlocked = { cleanHost ->
                    android.widget.Toast.makeText(context, context.getString(R.string.browser_website_unlocked, cleanHost), android.widget.Toast.LENGTH_SHORT).show()
                },
                onPinCreated = { cleanHost ->
                    android.widget.Toast.makeText(context, context.getString(R.string.browser_pin_created, cleanHost), android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
