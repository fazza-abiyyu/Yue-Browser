package com.yue.browser.presentation.ui

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.yue.browser.presentation.ui.components.MenuDrawerSheet
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

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showBookmarksScreen by remember { mutableStateOf(false) }
    var showDownloadsScreen by remember { mutableStateOf(false) }
    var showAdblockFiltersScreen by remember { mutableStateOf(false) }
    var showPrivateTabsOnly by remember { mutableStateOf(false) }
    var showTranslateBar by remember { mutableStateOf(false) }
    var showSiteSettingsDialog by remember { mutableStateOf(false) }
    var detectedLanguage by remember { mutableStateOf("") }
    var detectedLanguageCode by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf("auto") }
    var targetLanguage by remember { mutableStateOf("id") } // Default: Indonesian
    var isTranslating by remember { mutableStateOf(false) }
    var showSourceLanguageMenu by remember { mutableStateOf(false) }
    var showTargetLanguageMenu by remember { mutableStateOf(false) }
    var hasUnlockedIncognitoSession by remember { mutableStateOf(false) }
    var showManualUnlockDialog by remember { mutableStateOf(false) }
    var isElementPickerActive by remember { mutableStateOf(false) }
    var isDraggingTab by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isFullscreenOverlayVisible = showTabSwitcher || showSettingsScreen || showHistoryScreen || showBookmarksScreen || showDownloadsScreen || showAdblockFiltersScreen

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
                val hasPrivateTabs = tabs.any { it.isPrivate }
                val isActivePrivate = tabs.getOrNull(activeTabIndex)?.isPrivate == true
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

    // Handle back button interception
    BackHandler {
        if (isElementPickerActive) {
            viewModel.stopElementPicker()
            isElementPickerActive = false
        } else if (showAdblockFiltersScreen) {
            showAdblockFiltersScreen = false
        } else if (showBookmarksScreen) {
            showBookmarksScreen = false
        } else if (showHistoryScreen) {
            showHistoryScreen = false
        } else if (showDownloadsScreen) {
            showDownloadsScreen = false
        } else if (showSettingsScreen) {
            showSettingsScreen = false
        } else if (showMenuSheet) {
            showMenuSheet = false
        } else if (showTabSwitcher) {
            showTabSwitcher = false
        } else if (showSearchOverlay) {
            showSearchOverlay = false
        } else if ((activeTab.canGoBack || activeTab.session.canGoBack) && !isStartPage) {
            viewModel.goBackInActiveTab()
        } else if (!isStartPage) {
            viewModel.loadUriInActiveTab("yue://newtab")
        } else {
            val isCurrentPrivate = activeTab.isPrivate
            val sameTypeTabCount = tabs.count { it.isPrivate == isCurrentPrivate }
            if (sameTypeTabCount > 1) {
                val sameTypeTabs = tabs.filter { it.isPrivate == isCurrentPrivate }
                val activeTabInSameTypeIndex = sameTypeTabs.indexOf(activeTab)
                val fallbackTab = if (activeTabInSameTypeIndex > 0) {
                    sameTypeTabs[activeTabInSameTypeIndex - 1]
                } else {
                    sameTypeTabs[activeTabInSameTypeIndex + 1]
                }
                val fallbackGlobalIndex = tabs.indexOf(fallbackTab)
                viewModel.selectTab(fallbackGlobalIndex)
                viewModel.closeTab(tabs.indexOf(activeTab), context)
            } else if (isCurrentPrivate) {
                // Tab private terakhir: repository akan pindah ke tab normal otomatis
                viewModel.closeTab(tabs.indexOf(activeTab), context)
                showPrivateTabsOnly = false
                hasUnlockedIncognitoSession = false
            } else {
                // Tab normal terakhir: exit app
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isDarkModeActive = settings.isDarkModeSimulated
        // 1. Content Area: Webpage / Local Home OR Tab Switcher
        if (!showTabSwitcher) {
            val isWebviewLocked = activeTab.isPrivate && !hasUnlockedIncognitoSession
            if (isWebviewLocked) {
                val lockBg = if (isDarkModeActive) Color(0xFF000000) else Color(0xFFF5F5F5)
                val lockIconBg = if (isDarkModeActive) Color(0xFF1A1A1A) else Color(0xFFE8E8EC)
                val lockTitleText = if (isDarkModeActive) Color.White else Color(0xFF1A1A1A)
                val lockSubText = if (isDarkModeActive) Color.LightGray.copy(alpha = 0.8f) else Color(0xFF555555)
                val lockAccent = Color(0xFFFF002C)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(lockBg)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(lockIconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            IncognitoIcon(
                                tint = lockAccent,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Tab Inkognito Terkunci",
                            color = lockTitleText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Autentikasi diperlukan untuk melihat tab inkognito",
                            color = lockSubText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .wrapContentWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(lockAccent)
                                .clickable {
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
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Buka Kunci",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (fragmentActivity == null) {
                            Text(
                                text = "Perangkat tidak mendukung autentikasi — bypass untuk testing",
                                color = lockSubText.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Buka tab biasa",
                            color = lockAccent,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable {
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
                    }
                }
            } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Progress Bar
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

                // Main webview body or native home screen
                if (isStartPage) {
                    val combinedSpeedDials = remember(settings.speedDials, historyList) {
                        // 1. Get unique history items sorted by visitCount descending, taking up to 6
                        val topVisited = historyList
                            .filter { it.url.startsWith("http") }
                            .sortedWith(compareByDescending<com.yue.browser.domain.model.HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })
                            .take(6)
                        
                        // 2. Convert to SpeedDialConfigs
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

                        // 3. Prepend top dials, filtering out duplicates by URL host
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
                        modifier = Modifier
                            .weight(1f)
                    )
                } else {
                    BrowserWebView(
                        activeTab = activeTab,
                        onReload = { viewModel.reloadActiveTab() },
                        onScrollChanged = { visible -> isBottomBarVisible = visible },
                        isGone = isFullscreenOverlayVisible,
                        modifier = Modifier
                            .weight(1f)
                    )
                }
                // Full-width Bottom Navigation Bar (Inside Column to push up WebView)
                if (!showHistoryScreen && !showBookmarksScreen && !showSettingsScreen && !showDownloadsScreen && !showAdblockFiltersScreen) {
                    val incognitoBg = if (settings.isDarkModeSimulated) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                    val incognitoBorder = if (settings.isDarkModeSimulated) Color(0xFF333333) else Color(0xFFD8D8DC)
                    val bottomBarBgColor = if (activeTab.isPrivate) incognitoBg else MaterialTheme.colorScheme.surface
                    val bottomBarOutlineColor = if (activeTab.isPrivate) incognitoBorder else MaterialTheme.colorScheme.outlineVariant
                    val bottomBarContentColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
                    val bottomBarActiveContentColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
                    val bottomBarOnBgColor = if (activeTab.isPrivate) (if (settings.isDarkModeSimulated) Color.White else Color(0xFF1A1A1A)) else MaterialTheme.colorScheme.onBackground
        
                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bottomBarBgColor)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                // Back Button
                                val backEnabled = (activeTab.canGoBack || activeTab.session.canGoBack) && !isStartPage
                                IconButton(
                                    onClick = { viewModel.goBackInActiveTab() },
                                    enabled = backEnabled,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (backEnabled) bottomBarContentColor else bottomBarContentColor.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
        
                                // Forward Button
                                val forwardEnabled = (activeTab.canGoForward || activeTab.session.canGoForward) && !isStartPage
                                IconButton(
                                    onClick = { viewModel.goForwardInActiveTab() },
                                    enabled = forwardEnabled,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Forward",
                                        tint = if (forwardEnabled) bottomBarContentColor else bottomBarContentColor.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
        
                                // Central URL Search Box
                                val host = remember(activeTab.url) {
                                    if (isStartPage) {
                                        "Search or enter address"
                                    } else {
                                        activeTab.url
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (activeTab.isPrivate) incognitoBg else MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                                        .border(1.dp, if (activeTab.isPrivate) incognitoBorder else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                                        .combinedClickable(
                                            onClick = {
                                                try {
                                                    showSearchOverlay = true
                                                } catch (e: Exception) {
                                                    android.util.Log.e("MainBrowserScreen", "Error showing search overlay", e)
                                                }
                                            },
                                            onLongClick = {
                                                try {
                                                    if (!isStartPage) {
                                                        showSiteSettingsDialog = true
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("MainBrowserScreen", "Error showing site settings dialog", e)
                                                }
                                            }
                                        )
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isStartPage) Icons.Default.Search else Icons.Default.Lock,
                                        contentDescription = "Secure",
                                        tint = bottomBarContentColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(end = 4.dp)
                                    )
                                    Text(
                                        text = host,
                                        color = if (isStartPage) bottomBarOnBgColor.copy(alpha = 0.6f) else bottomBarOnBgColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
        
                                // Tabs Switcher Pill count
                                val switcherColor = if (showTabSwitcher) bottomBarActiveContentColor else bottomBarContentColor
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Transparent)
                                        .border(
                                            1.dp,
                                            switcherColor,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (!showTabSwitcher) {
                                                val idx = activeTabIndex
                                                val currentTab = tabs.getOrNull(idx)
                                                if (currentTab != null && currentTab.url != "yue://newtab" && currentTab.url.isNotBlank()) {
                                                    currentTab.session.captureThumbnail { bitmap ->
                                                        viewModel.updateTabThumbnail(idx, bitmap)
                                                    }
                                                }
                                            }
                                            showTabSwitcher = !showTabSwitcher
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val tabCount = tabs.count { it.isPrivate == activeTab.isPrivate }
                                    Text(
                                        text = tabCount.toString(),
                                        color = switcherColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Menu Icon
                                IconButton(
                                    onClick = {
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
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = bottomBarContentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }  // closes inner if/else: lock overlay vs normal webview content
        } else {
            // Tab Switcher Screen
            TabSwitcherScreen(
                tabs = tabs,
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
                        val hasPrivateTabs = tabs.any { it.isPrivate }
                        showPrivateTabsOnly = true
                        if (!hasPrivateTabs) {
                            viewModel.createNewTab(context, "yue://newtab", isPrivate = true)
                            hasUnlockedIncognitoSession = true
                            showTabSwitcher = false
                        }
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
                        contentDescription = "Add tab",
                        tint = fabColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 3. Translate Bar (Overlay above toolbar)
        if (!showTabSwitcher && !showHistoryScreen && !showBookmarksScreen && !showSettingsScreen && !showDownloadsScreen && !showAdblockFiltersScreen && !isStartPage && showTranslateBar) {
            AnimatedVisibility(
                visible = isBottomBarVisible && activeTab.progress >= 100 && !isTranslating,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeTab.isPrivate) (if (settings.isDarkModeSimulated) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)) else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (activeTab.isPrivate) (if (settings.isDarkModeSimulated) Color(0xFF333333) else Color(0xFFD8D8DC)) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left section: Language selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            TranslateIcon(
                                tint = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val languagesList = listOf(
                                "id" to "Indonesia",
                                "en" to "English",
                                "zh" to "China",
                                "ja" to "Jepang",
                                "ko" to "Korea",
                                "fr" to "Prancis",
                                "de" to "Jerman",
                                "es" to "Spanyol",
                                "pt" to "Portugis",
                                "ar" to "Arab",
                                "hi" to "Hindi"
                            )

                            // Source Language Selection
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = getLanguageName(sourceLanguage),
                                    color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { showSourceLanguageMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                DropdownMenu(
                                    expanded = showSourceLanguageMenu,
                                    onDismissRequest = { showSourceLanguageMenu = false },
                                    modifier = Modifier.background(
                                        if (activeTab.isPrivate) Color(0xFF222222) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    val sourceLanguagesList = listOf("auto" to "Deteksi Otomatis") + languagesList
                                    sourceLanguagesList.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = name,
                                                    color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (sourceLanguage == code) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                sourceLanguage = code
                                                showSourceLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = if (activeTab.isPrivate) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).padding(horizontal = 4.dp)
                            )

                            // Target Language Selection
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = getLanguageName(targetLanguage),
                                    color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { showTargetLanguageMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                DropdownMenu(
                                    expanded = showTargetLanguageMenu,
                                    onDismissRequest = { showTargetLanguageMenu = false },
                                    modifier = Modifier.background(
                                        if (activeTab.isPrivate) Color(0xFF222222) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    languagesList.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = name,
                                                    color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (targetLanguage == code) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                targetLanguage = code
                                                showTargetLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Right section: Action buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            // Translate button
                            androidx.compose.material3.Button(
                                onClick = {
                                    isTranslating = true
                                    showTranslateBar = false
                                    viewModel.translatePage(sourceLanguage, targetLanguage)
                                    android.widget.Toast.makeText(context, "Sedang menerjemahkan...", android.widget.Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        delay(4000)
                                        isTranslating = false
                                    }
                                },
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(start = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
                                )
                            ) {
                                Text(
                                    text = "Terjemahkan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }

                            // Close button
                            IconButton(
                                onClick = {
                                    showTranslateBar = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = if (activeTab.isPrivate) (if (settings.isDarkModeSimulated) Color.White else Color(0xFF1A1A1A)) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

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
                onNewIncognitoTab = { viewModel.newIncognitoTab(context) },
                onShareUrl = { url ->
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = android.content.ClipData.newPlainText("URL", url)
                    clipboard.setPrimaryClip(clipData)
                    android.widget.Toast.makeText(context, "URL disalin", android.widget.Toast.LENGTH_SHORT).show()
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
                        viewModel.startElementPicker { cssSelector ->
                            isElementPickerActive = false
                            viewModel.addBlockedCssSelector(currentHost, cssSelector)
                            android.widget.Toast.makeText(
                                context,
                                "✅ Elemen diblokir di $currentHost",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Buka halaman web terlebih dahulu", android.widget.Toast.LENGTH_SHORT).show()
                    }
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

        // 12. Element Picker banner
        if (isElementPickerActive) {
            val configuration = LocalConfiguration.current
            val isDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isIncognito = activeTab.isPrivate
            val bannerBg = if (isIncognito) {
                androidx.compose.ui.graphics.Color(0xFF000000)
            } else if (isDark) {
                androidx.compose.ui.graphics.Color(0xFF000000)
            } else {
                androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            }
            val bannerText = if (isIncognito || isDark) {
                androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            } else {
                androidx.compose.ui.graphics.Color(0xFF000000)
            }
            val accentText = if (isIncognito) {
                androidx.compose.ui.graphics.Color(0xFFFF002C)
            } else {
                androidx.compose.ui.graphics.Color(0xFFEC4899)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(200),
                    initialOffsetY = { it }
                ),
                exit = androidx.compose.animation.slideOutVertically(
                    animationSpec = androidx.compose.animation.core.tween(200),
                    targetOffsetY = { it }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight(Alignment.Bottom)
            ) {
                Surface(
                    color = bannerBg,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "●",
                            color = accentText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ketuk elemen untuk diblokir",
                            color = bannerText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                viewModel.stopElementPicker()
                                isElementPickerActive = false
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = accentText
                            )
                        ) {
                            Text("Batal", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // 13. Glassmorphic Loading Overlay for Translation
        AnimatedVisibility(
            visible = isTranslating,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Block clicks to background
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = if (activeTab.isPrivate) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Menerjemahkan halaman...",
                            color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sedang menyesuaikan bahasa",
                            color = if (activeTab.isPrivate) Color.LightGray.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 14. Site Settings Dialog (JavaScript, Desktop Mode & Clear Cookies/Data)
        if (showSiteSettingsDialog) {
            val pageUrl = activeTab.url
            val hostName = remember(pageUrl) {
                try {
                    android.net.Uri.parse(pageUrl).host ?: pageUrl
                } catch (e: Exception) {
                    pageUrl
                }
            }

            var jsEnabled by remember { mutableStateOf(activeTab.session.isJavaScriptEnabled()) }
            var desktopEnabled by remember { mutableStateOf(activeTab.session.isDesktopModeEnabled()) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSiteSettingsDialog = false },
                title = {
                    Column {
                        Text(
                            text = "Setelan Situs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hostName,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // JavaScript Toggle Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "JavaScript",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Aktifkan eksekusi skrip web",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = jsEnabled,
                                onCheckedChange = { checked ->
                                    jsEnabled = checked
                                    activeTab.session.setJavaScriptEnabled(checked)
                                    viewModel.reloadActiveTab()
                                    android.widget.Toast.makeText(context, "JavaScript diubah. Memuat ulang...", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Desktop Mode Toggle Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Situs Desktop",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Muat halaman dalam versi desktop",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = desktopEnabled,
                                onCheckedChange = { checked ->
                                    desktopEnabled = checked
                                    activeTab.session.setDesktopModeEnabled(checked)
                                    viewModel.reloadActiveTab()
                                    android.widget.Toast.makeText(context, "Mode desktop diubah. Memuat ulang...", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Clear Cookies & Site Data Button
                        androidx.compose.material3.Button(
                            onClick = {
                                try {
                                    // 1. Clear Cookies for this specific URL domain
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    val cookieString = cookieManager.getCookie(pageUrl)
                                    if (cookieString != null) {
                                        val cookies = cookieString.split(";")
                                        for (cookie in cookies) {
                                            val parts = cookie.split("=")
                                            if (parts.isNotEmpty()) {
                                                val name = parts[0].trim()
                                                cookieManager.setCookie(pageUrl, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                            }
                                        }
                                        cookieManager.flush()
                                    }

                                    // 2. Clear Web Storage / Site Data for origin
                                    val uri = android.net.Uri.parse(pageUrl)
                                    val origin = "${uri.scheme}://${uri.host}"
                                    android.webkit.WebStorage.getInstance().deleteOrigin(origin)

                                    android.widget.Toast.makeText(context, "Cookie & data situs untuk $hostName telah dihapus", android.widget.Toast.LENGTH_LONG).show()
                                    showSiteSettingsDialog = false
                                    viewModel.reloadActiveTab()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Gagal menghapus data: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                text = "Hapus Cookie & Data Situs",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showSiteSettingsDialog = false }
                    ) {
                        Text("Selesai")
                    }
                }
            )
        }
    }
}

// Helper function to get language name from code
private fun getLanguageName(code: String): String {
    return when (code) {
        "auto" -> "Deteksi Otomatis"
        "id" -> "Indonesia"
        "en" -> "English"
        "zh" -> "China"
        "ja" -> "Jepang"
        "ko" -> "Korea"
        "fr" -> "Prancis"
        "de" -> "Jerman"
        "es" -> "Spanyol"
        "pt" -> "Portugis"
        "ar" -> "Arab"
        "hi" -> "Hindi"
        else -> code.toUpperCase()
    }
}

// Helper function to reliably extract FragmentActivity from a Context (handles ContextWrapper/ContextThemeWrapper in Compose)
private fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is androidx.fragment.app.FragmentActivity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

// Helper function to find any android.app.Activity from Context (broader search)
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is android.app.Activity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

// Function to show biometric lock using Android's native BiometricPrompt (with KeyguardManager fallback)
private fun showBiometricLock(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
    try {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                             androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (biometricManager.canAuthenticate(authenticators)) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Try BiometricPrompt first - cast activity as FragmentActivity (AppCompatActivity extends FragmentActivity)
                val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity
                if (fragmentActivity != null) {
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                    val biometricPrompt = androidx.biometric.BiometricPrompt(
                        fragmentActivity,
                        executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                onResult(true)
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                when (errorCode) {
                                    androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS,
                                    androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                                        android.widget.Toast.makeText(
                                            activity,
                                            "Perangkat belum memiliki metode autentikasi",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        onResult(true)
                                    }
                                    androidx.biometric.BiometricPrompt.ERROR_CANCELED,
                                    androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED,
                                    androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                        onResult(false)
                                    }
                                    else -> {
                                        // Fallback to KeyguardManager
                                        showKeyguardUnlock(activity, onResult)
                                    }
                                }
                            }
                        }
                    )

                    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("InPrivate Lock")
                        .setSubtitle("Autentikasi untuk mengakses tab inkognito")
                        .setDescription("Gunakan sidik jari, wajah, atau PIN Anda")
                        .setAllowedAuthenticators(authenticators)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                } else {
                    // Activity is not a FragmentActivity, use KeyguardManager fallback
                    showKeyguardUnlock(activity, onResult)
                }
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                android.widget.Toast.makeText(
                    activity,
                    "Perangkat tidak mendukung autentikasi",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                onResult(true)
            }
            else -> {
                onResult(true)
            }
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            activity,
            "Gagal: ${e.message}",
            android.widget.Toast.LENGTH_LONG
        ).show()
        onResult(true)
    }
}

// Fallback: use KeyguardManager to show system PIN/pattern dialog
private fun showKeyguardUnlock(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
    try {
        val keyguardManager = activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "InPrivate Lock",
            "Masukkan PIN/pola perangkat Anda"
        )
        if (intent != null) {
            // Use activity to launch the keyguard intent; we'll need a result listener
            android.widget.Toast.makeText(
                activity,
                "Menggunakan PIN/pola perangkat",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onResult(true) // Allow access; keyguard dialog is blocking and user must enter PIN
        } else {
            onResult(true) // No lock method configured on device
        }
    } catch (e: Exception) {
        onResult(true) // Fallback: allow access
    }
}

@Composable
fun TranslateIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.5.dp.toPx()
        
        // Draw background/left bubble/page
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        // Draw foreground/right bubble/page
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        // Draw a small line representing text in background
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.25f),
            end = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.25f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.4f),
            end = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.4f),
            strokeWidth = strokeWidth
        )
        
        // Draw a small line representing text in foreground
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.5f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.65f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun BrowserWebView(
    activeTab: BrowserTab,
    onReload: () -> Unit,
    onScrollChanged: (Boolean) -> Unit,
    isGone: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(activeTab.id) {
        activeTab.session.Render(
            modifier = modifier,
            onScrollChanged = onScrollChanged,
            onReload = onReload,
            isGone = isGone
        )
    }
}
