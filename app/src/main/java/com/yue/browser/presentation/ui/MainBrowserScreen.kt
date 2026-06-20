package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.res.Configuration
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.SpeedDialConfig
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.data.engine.MediaSessionManager
import com.yue.browser.presentation.ui.components.BottomTranslateBar
import com.yue.browser.presentation.ui.components.BrowserBottomBar
import com.yue.browser.presentation.ui.components.IncognitoLockScreen
import com.yue.browser.presentation.ui.components.MenuDrawerSheet
import com.yue.browser.presentation.ui.components.SiteSettingsDialog
import com.yue.browser.presentation.ui.components.TopTranslateBar
import com.yue.browser.presentation.ui.components.NewTabHomeScreen
import com.yue.browser.presentation.ui.components.SearchOverlay
import com.yue.browser.presentation.ui.components.formatUrlOrQuery
import com.yue.browser.presentation.ui.SettingsScreen
import com.yue.browser.presentation.ui.HistoryScreen
import com.yue.browser.presentation.ui.BookmarksScreen
import com.yue.browser.presentation.ui.DownloadsScreen

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
        val mappedLang = if (systemLang == "in") "id" else systemLang
        val supportedLangs = listOf("id", "en", "zh", "ja", "ko", "fr", "de", "es", "pt", "ar", "hi")
        if (mappedLang in supportedLangs) mappedLang else "id"
    }
    var targetLanguage by remember { mutableStateOf(defaultTargetLanguage) } // Default: Indonesian or system language
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

    // Track which tabs have locked domains (for tab switcher preview lock overlay)
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
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Permission launcher for notifications (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* No-op: just let user grant or deny */ }

    val activity = context as? android.app.Activity
    val fragmentActivity = context.findFragmentActivity()

    // isIncognitoLocked: true hanya ketika TabSwitcher menampilkan private tabs,
    // session belum di-unlock, DAN ADA tab private yang perlu dilindungi (jumlah > 0)
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
                // Baca state LIVE dari ViewModel, bukan dari capture closure
                val currentTabs = viewModel.tabs.value
                val currentIndex = viewModel.activeTabIndex.value
                val hasPrivateTabs = currentTabs.any { it.isPrivate }
                val isActivePrivate = currentTabs.getOrNull(currentIndex)?.isPrivate == true
                if (hasPrivateTabs) {
                    hasUnlockedIncognitoSession = false // Reset: perlu unlock lagi tiap kali app kembali dari background
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

    // Jangan auto-pindah ke mode private jika lock masih aktif atau user sedang di tab switcher (biarkan user decide manual)
    LaunchedEffect(activeTabIndex, tabs) {
        val activeTab = tabs.getOrNull(activeTabIndex)
        if (activeTab != null && activeTab.isPrivate && hasUnlockedIncognitoSession && !showTabSwitcher) {
            showPrivateTabsOnly = true
        }
    }

    LaunchedEffect(activeTabIndex) {
        showTranslateBar = false
    }

    // Safety net: auto-exit private mode jika tidak ada tab private lagi
    // Juga auto-reset hasUnlockedIncognitoSession ketika tidak ada tab private
    LaunchedEffect(tabs) {
        val hasPrivateTabs = tabs.any { it.isPrivate }
        if (!hasPrivateTabs) {
            if (showPrivateTabsOnly) {
                showPrivateTabsOnly = false
            }
            hasUnlockedIncognitoSession = false
        }
    }

    // Initialize first tab with native start page if empty
    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            viewModel.restoreTabs(context)
            val restoredTabs = viewModel.tabs.value

            // Ensure there is always at least one normal tab
            val hasNormalTabs = restoredTabs.any { !it.isPrivate }
            if (!hasNormalTabs) {
                viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
            }

            val hasPrivateTabs = restoredTabs.any { it.isPrivate }
            val activeTab = restoredTabs.getOrNull(viewModel.activeTabIndex.value)
            if (hasPrivateTabs) {
                hasUnlockedIncognitoSession = false // Reset session unlock di startup
                if (showPrivateTabsOnly || activeTab?.isPrivate == true) {
                    showTabSwitcher = true
                    showPrivateTabsOnly = true
                }
            }
        }
        viewModel.initializeDownloads(context)
        viewModel.initializeHistory(context)
        viewModel.initializePasswords(context)

        // Request notification permission for download notifications (Android 13+)
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
    val activeTab = safeActiveTab ?: return@MainBrowserScreen  // Safety: keluar composable jika tidak ada tab aktif
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

    // Web Lock: cek setiap kali URL berubah
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

    // Re-lock semua tab saat app kembali ke foreground
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.lockAllTabs()
                // Cek ulang apakah halaman aktif saat ini perlu overlay
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

    // Handle back button interception — ALL states must be captured so back never accidentally closes the app
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
            showTabSwitcher -> showTabSwitcher = false
            showSearchOverlay -> showSearchOverlay = false
            // Web navigation: WebView back (native + SPA) takes priority when not on start page
            activeTab.session.combinedCanGoBack && !isStartPage -> {
                android.util.Log.d("BackHandler", "combinedCanGoBack=true, calling tryBackPressInActiveTab")
                viewModel.tryBackPressInActiveTab()
            }
            // Not on start page and can't go back in WebView: handle tab-level navigation
            !isStartPage -> {
                android.util.Log.d("BackHandler", "combinedCanGoBack=false, calling handleBackNavigation")
                viewModel.handleBackNavigation()
            }
            // On start page: close tab if navigated away or has parent, else send to background
            else -> {
                android.util.Log.d("BackHandler", "on start page, isStartPage=true")
                val currentActiveTab = tabs.getOrNull(activeTabIndex)
                val shouldClose = currentActiveTab != null && (
                    currentActiveTab.hasEverNavigatedAway ||
                    (currentActiveTab.parentTabId != null && tabs.any { it.id == currentActiveTab.parentTabId })
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
        // 1. Content Area: Webpage / Local Home OR Tab Switcher
        val isWebviewLocked = activeTab.isPrivate && !hasUnlockedIncognitoSession
        if (isWebviewLocked && !isInPip && !showTabSwitcher) {
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
                                    onScrollChanged = { visible -> if (idx == activeTabIndex) isBottomBarVisible = visible },
                                    isGone = idx != activeTabIndex || isFullscreenOverlayVisible || showWebLockOverlay,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    if (isStartPage && !isInPip) {
                        val combinedSpeedDials = remember(settings.speedDials, historyList) {
                            val topVisited = historyList
                                .filter { it.url.startsWith("http") }
                                .sortedWith(compareByDescending<com.yue.browser.domain.model.HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })
                                .take(6)

                            val topDials = topVisited.map { item ->
                                val uri = try { android.net.Uri.parse(item.url) } catch (e: Exception) { null }
                                val host = uri?.host ?: item.url
                                val cleanHost = host.removePrefix("www.").removePrefix("m.").substringBefore("/")

                                val letter = (item.title.trim().firstOrNull() ?: cleanHost.firstOrNull() ?: 'W')
                                    .toString().uppercase(java.util.Locale.US)

                                val cleanName = if (item.title.isNotBlank() && !item.title.startsWith("http") && item.title.length < 30) {
                                    item.title
                                } else {
                                    cleanHost.substringBefore(".")
                                }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }

                                val colors = listOf("4285F4", "34A853", "FBBC05", "EA4335", "9C27B0", "3F51B5", "00BCD4", "E91E63")
                                val colorIndex = Math.abs(cleanHost.hashCode()) % colors.size
                                val colorHex = colors[colorIndex]

                                SpeedDialConfig(
                                    name = cleanName,
                                    url = item.url,
                                    iconLetter = letter,
                                    iconBgColorHex = colorHex
                                )
                            }

                            val result = mutableListOf<SpeedDialConfig>()
                            val addedHosts = mutableSetOf<String>()

                            topDials.forEach { dial ->
                                val host = try { android.net.Uri.parse(dial.url).host } catch (e: Exception) { null } ?: dial.url
                                val cleanHost = host.removePrefix("www.").removePrefix("m.")
                                if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                                    result.add(dial)
                                    addedHosts.add(cleanHost)
                                }
                            }

                            settings.speedDials.forEach { dial ->
                                val host = try { android.net.Uri.parse(dial.url).host } catch (e: Exception) { null } ?: dial.url
                                val cleanHost = host.removePrefix("www.").removePrefix("m.")
                                if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                                    result.add(dial)
                                    addedHosts.add(cleanHost)
                                }
                            }
                            result.take(6)
                        }

                        NewTabHomeScreen(
                            speedDials = combinedSpeedDials,
                            onSearchClick = { showSearchOverlay = true },
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
                            onQueryChange = {
                                findInPageQuery = it
                                viewModel.findInPage(it)
                            },
                            onNext = { viewModel.findInPageNext(true) },
                            onPrevious = { viewModel.findInPageNext(false) },
                            onClose = {
                                showFindInPage = false
                                findInPageQuery = ""
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
                                showWebLockOverlay = false
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
                                            showWebLockOverlay = false
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
                        onUrlClick = { showSearchOverlay = true },
                        onUrlLongClick = { if (!isStartPage) showSiteSettingsDialog = true },
                        onTabSwitcherClick = {
                            if (!showTabSwitcher) {
                                val currentTab = tabs.getOrNull(activeTabIndex)
                                if (currentTab != null && currentTab.url != "yue://newtab" && currentTab.url.isNotBlank()) {
                                    currentTab.session.captureThumbnail { bitmap ->
                                        viewModel.updateTabThumbnail(activeTabIndex, bitmap)
                                    }
                                }
                            }
                            showTabSwitcher = !showTabSwitcher
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
                            showMenuSheet = true
                        }
                    )
                }
            }
        }

        if (showTabSwitcher) {
            // Tab Switcher Screen
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
                        hasUnlockedIncognitoSession = true
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
                            // Semua tab private habis: keluar private mode, pindah ke normal
                            showPrivateTabsOnly = false
                            hasUnlockedIncognitoSession = false
                            showTabSwitcher = false
                        }
                        // MASIH ada tab private: user TETAP di tab switcher (belum pilih tab mana)
                    } else {
                        if (shownTypeRemaining == 0) {
                            // Semua tab normal habis: buat tab default baru
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        }
                        // MASIH ada tab normal: user TETAP di tab switcher
                    }
                },
                onCloseAll = {
                    if (showPrivateTabsOnly) {
                        // Di mode private: hanya hapus PRIVATE tabs, pindah ke normal
                        viewModel.closePrivateTabsOnly()
                        showPrivateTabsOnly = false
                        hasUnlockedIncognitoSession = false
                        // Jika tidak ada tab normal sama sekali, buat satu
                        if (viewModel.tabs.value.none { !it.isPrivate }) {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = false)
                        }
                    } else {
                        // Di mode normal: hapus SEMUA, buat tab normal baru
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

        // 2. Center FAB for adding new tab (Only visible in Tab Switcher, hidden if incognito locked or dragging)
        if (showTabSwitcher && !isDraggingTab && !(showPrivateTabsOnly && !hasUnlockedIncognitoSession)) {
            val fabColor = if (showPrivateTabsOnly) Color(0xFFFF002C) else Color(0xFFEC4899)
            val fabBg = if (showPrivateTabsOnly) (if (settings.isDarkModeSimulated) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)) else MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
            val fabBorder = if (showPrivateTabsOnly) (if (settings.isDarkModeSimulated) Color(0xFF333333) else Color(0xFFD8D8DC)) else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(fabBg)
                        .border(1.dp, fabBorder, RoundedCornerShape(18.dp))
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
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

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

        // Custom settings drawer overlay
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
                isDarkMode = settings.isDarkModeSimulated,
                onDarkModeToggle = { viewModel.toggleDarkMode(it) },
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
                currentUrl = activeTab.url
            )
        }

        // 5. Fullscreen URL search overlay
        if (showSearchOverlay) {
            SearchOverlay(
                initialInput = activeTab.url,
                history = historyList,
                bookmarks = bookmarkList,
                onRemoveHistory = { url -> viewModel.removeHistory(url) },
                onDismiss = { showSearchOverlay = false },
                onSearch = { query ->
                    android.util.Log.d("YueUrl", "SearchOverlay onSearch query='$query'")
                    val trimmed = query.trim()
                    if (trimmed.isEmpty()) {
                        if (activeTab.url != "yue://newtab" && activeTab.url.isNotBlank()) {
                            viewModel.reloadActiveTab()
                        }
                    } else {
                        val destination = formatUrlOrQuery(trimmed, settings.searchEngineUrl)
                        android.util.Log.d("YueUrl", "SearchOverlay destination='$destination'")
                        viewModel.loadUriInActiveTab(destination)
                    }
                    showSearchOverlay = false
                },
                isDarkMode = settings.isDarkModeSimulated,
                searchEngineUrl = settings.searchEngineUrl,
                isPrivate = activeTab.isPrivate
            )
        }

        // 6. Settings Screen Overlay
        if (showSettingsScreen) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettingsScreen = false },
                onAdblockFiltersClick = {
                    showSettingsScreen = false
                    showAdblockFiltersScreen = true
                },
                onLockedWebsitesClick = {
                    showSettingsScreen = false
                    showLockedWebsitesScreen = true
                },
                onPasswordManagerClick = {
                    val hasBio = isBiometricAvailable(context)
                    if (hasBio) {
                        val fragActivity = context as? androidx.fragment.app.FragmentActivity
                        if (fragActivity != null) {
                            showBiometricPrompt(
                                activity = fragActivity,
                                onSuccess = {
                                    showSettingsScreen = false
                                    showPasswordManagerScreen = true
                                },
                                onFailed = {},
                                title = "Password Manager",
                                subtitle = "Authenticate to access saved passwords"
                            )
                        } else {
                            showSettingsScreen = false
                            showPasswordManagerScreen = true
                        }
                    } else {
                        showSettingsScreen = false
                        showPasswordManagerScreen = true
                    }
                },
                onPlaybackSettingsClick = {
                    showSettingsScreen = false
                    showPlaybackSettingsScreen = true
                }
            )
        }

        if (showPlaybackSettingsScreen) {
            PlaybackSettingsScreen(
                viewModel = viewModel,
                onBack = {
                    showPlaybackSettingsScreen = false
                    showSettingsScreen = true
                }
            )
        }

        // 7. History Screen Overlay
        if (showHistoryScreen) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { showHistoryScreen = false }
            )
        }

        // 8. Bookmarks Screen Overlay
        if (showBookmarksScreen) {
            BookmarksScreen(
                viewModel = viewModel,
                onBack = { showBookmarksScreen = false }
            )
        }

        // Offline Pages Screen Overlay
        if (showOfflinePagesScreen) {
            OfflinePagesScreen(
                viewModel = viewModel,
                onBack = { showOfflinePagesScreen = false }
            )
        }

        // 9. Downloads Screen Overlay
        if (showDownloadsScreen) {
            DownloadsScreen(
                viewModel = viewModel,
                onBack = { showDownloadsScreen = false },
                context = context
            )
        }

        // 10. Adblock Filters Screen Overlay (child of Settings)
        if (showAdblockFiltersScreen) {
            AdblockFiltersScreen(
                viewModel = viewModel,
                onBack = {
                    showAdblockFiltersScreen = false
                    showSettingsScreen = true
                }
            )
        }

        // 11. Locked Websites Screen Overlay (child of Settings)
        if (showLockedWebsitesScreen) {
            LockedWebsitesScreen(
                viewModel = viewModel,
                onBack = {
                    showLockedWebsitesScreen = false
                    showSettingsScreen = true
                }
            )
        }

        // 12. Password Manager Screen
        if (showPasswordManagerScreen) {
            PasswordManagerScreen(
                viewModel = viewModel,
                onBack = { showPasswordManagerScreen = false }
            )
        }

        // Undo close tab banner
        val lastClosed by viewModel.lastClosedTab.collectAsState()
        LaunchedEffect(lastClosed) {
            if (lastClosed != null) {
                delay(4000)
                viewModel.lastClosedTab.value = null
            }
        }
        if (lastClosed != null) {
            val isDarkBg = settings.isDarkModeSimulated
            val cardBg = if (isDarkBg) Color(0xFF1A1A1C) else Color(0xFFF0F1F2)
            val cardText = if (isDarkBg) Color(0xFFE3E3E3) else Color(0xFF191C1D)
            val accentPink = Color(0xFFEC4899)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isBottomBarVisible) 120.dp else 80.dp, start = 16.dp, end = 16.dp)
                    .zIndex(30f)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
                    }
                }
            }
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

@Composable
private fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    result: BrowserViewModel.FindInPageResult?,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme
    val surfaceColor = color.surface
    val onSurfaceColor = color.onSurface
    val onSurfaceVariantColor = color.onSurfaceVariant
    val surfaceVariantColor = color.surfaceVariant

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val barShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .shadow(8.dp, barShape)
            .clip(barShape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = if (isSystemDark) color.primary.copy(alpha = 0.6f) else color.outlineVariant,
                shape = barShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = onSurfaceVariantColor
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = onSurfaceColor
                ),
                cursorBrush = SolidColor(onSurfaceColor),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.find_in_page_hint),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = onSurfaceVariantColor
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (result != null) {
                Text(
                    text = "${result.activeMatchOrdinal + 1}/${result.numberOfMatches}",
                    fontSize = 15.sp,
                    color = onSurfaceColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = onSurfaceVariantColor)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.forward), modifier = Modifier.size(18.dp), tint = onSurfaceVariantColor)
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceVariantColor)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2715",
                    fontSize = 16.sp,
                    color = onSurfaceVariantColor
                )
            }
        }
    }
}
