package com.yue.browser.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yue.browser.data.engine.SystemWebViewEngine
import com.yue.browser.data.repository.SettingsRepositoryImpl
import com.yue.browser.data.repository.TabRepositoryImpl
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.repository.SettingsRepository
import com.yue.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.yue.browser.domain.model.BookmarkItem
import com.yue.browser.domain.model.OfflinePageItem
import com.yue.browser.domain.model.TabGroup
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.model.PasswordEntry
import com.yue.browser.domain.repository.BookmarkRepository
import com.yue.browser.domain.repository.OfflinePageRepository
import com.yue.browser.domain.repository.DownloadRepository
import com.yue.browser.domain.repository.HistoryRepository
import com.yue.browser.domain.repository.PasswordRepository
import com.yue.browser.data.repository.BookmarkRepositoryImpl
import com.yue.browser.data.repository.OfflinePageRepositoryImpl
import com.yue.browser.data.repository.DownloadRepositoryImpl
import com.yue.browser.data.repository.HistoryRepositoryImpl
import com.yue.browser.data.repository.PasswordRepositoryImpl

class BrowserViewModel(
    internal val tabRepository: TabRepository = TabRepositoryImpl(
        browserEngine = SystemWebViewEngine(SettingsRepositoryImpl.instance),
        settingsRepository = SettingsRepositoryImpl.instance
    ),
    internal val settingsRepository: SettingsRepository = SettingsRepositoryImpl.instance,
    internal val historyRepository: HistoryRepository = HistoryRepositoryImpl.instance,
    internal val bookmarkRepository: BookmarkRepository = BookmarkRepositoryImpl.instance,
    internal val offlinePageRepository: OfflinePageRepository = OfflinePageRepositoryImpl.instance,
    internal val downloadRepository: DownloadRepository = DownloadRepositoryImpl.instance,
    internal val passwordRepository: PasswordRepository = PasswordRepositoryImpl.instance
) : ViewModel() {

    val tabs: StateFlow<List<BrowserTab>> = tabRepository.tabsFlow
    val activeTabIndex: StateFlow<Int> = tabRepository.activeTabIndexFlow
    val groups: StateFlow<Map<String, TabGroup>> = tabRepository.groupsFlow
    val settings: StateFlow<BrowserSettings> = settingsRepository.settingsFlow
    val history: StateFlow<List<HistoryItem>> = historyRepository.historyFlow
    val bookmarks: StateFlow<List<BookmarkItem>> = bookmarkRepository.bookmarksFlow
    val offlinePages: StateFlow<List<OfflinePageItem>> = offlinePageRepository.offlinePagesFlow
    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.downloadsFlow
    val passwords: StateFlow<List<PasswordEntry>> = passwordRepository.passwordsFlow

    private val webLockManager = WebLockManager(settingsRepository, viewModelScope)

    data class FindInPageResult(val activeMatchOrdinal: Int, val numberOfMatches: Int)

    private val _findInPageResult = MutableStateFlow<FindInPageResult?>(null)
    val findInPageResult: StateFlow<FindInPageResult?> = _findInPageResult.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun createGroup(name: String, colorIndex: Int, tabIds: List<String>): String {
        return tabRepository.createGroup(name, colorIndex, tabIds)
    }

    fun addTabToGroup(tabId: String, groupId: String) {
        tabRepository.addTabToGroup(tabId, groupId)
    }

    fun removeTabFromGroup(tabId: String) {
        tabRepository.removeTabFromGroup(tabId)
    }

    fun renameGroup(groupId: String, newName: String) {
        tabRepository.renameGroup(groupId, newName)
    }

    fun updateGroupColor(groupId: String, colorIndex: Int) {
        tabRepository.updateGroupColor(groupId, colorIndex)
    }

    fun deleteGroup(groupId: String) {
        tabRepository.deleteGroup(groupId)
    }

    fun moveTab(fromIndex: Int, toIndex: Int) {
        tabRepository.moveTab(fromIndex, toIndex)
    }

    // Dialog & overlay states managed locally in UI presentation
    val showTabSwitcher = mutableStateOf(false)
    val showSearchOverlay = mutableStateOf(false)
    val showMenuSheet = mutableStateOf(false)
    val showSettingsScreen = mutableStateOf(false)
    val showHistoryScreen = mutableStateOf(false)
    val showBookmarksScreen = mutableStateOf(false)
    val showDownloadsScreen = mutableStateOf(false)
    val showWelcomeScreen = mutableStateOf(false)

    init {
        viewModelScope.launch {
            settings.collect { settingsVal ->
                tabs.value.forEach { tab ->
                    configureTabSession(tab, settingsVal)
                }
            }
        }
        // Check first-run on init
        checkFirstRun()
    }

    private fun checkFirstRun() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.value
            val isFirstRun = !settings.firstRunCompleted
            if (isFirstRun) {
                showWelcomeScreen.value = true
            }
        }
    }

    fun dismissWelcomeScreen() {
        showWelcomeScreen.value = false
        settingsRepository.setFirstRunCompleted()
    }

    fun createNewTab(context: android.content.Context, initialUrl: String, isPrivate: Boolean = false) {
        tabRepository.createNewTab(context, initialUrl, isPrivate)
        tabs.value.lastOrNull()?.let { tab ->
            configureTabSession(tab, settings.value)
        }
    }

    var lastClosedTab: kotlinx.coroutines.flow.MutableStateFlow<ClosedTabInfo?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    data class ClosedTabInfo(val url: String, val title: String, val isPrivate: Boolean)

    fun closeTab(index: Int, context: android.content.Context? = null, notifyUndo: Boolean = true) {
        val tab = tabs.value.getOrNull(index)
        val tabId = tab?.id
        if (notifyUndo && tab != null && tab.url != "yue://newtab") {
            lastClosedTab.value = ClosedTabInfo(tab.url, tab.title, tab.isPrivate)
        }
        tabRepository.closeTab(index, context)
        if (tabId != null) onTabClosed(tabId)
    }

    fun undoCloseTab(context: android.content.Context) {
        val info = lastClosedTab.value ?: return
        lastClosedTab.value = null
        createNewTab(context, info.url, info.isPrivate)
    }

    fun closePrivateTabsOnly() {
        val currentTabs = tabs.value
        val privateTabIds = currentTabs.filter { it.isPrivate }.map { it.id }
        tabRepository.closePrivateTabsOnly()
        privateTabIds.forEach { onTabClosed(it) }
    }

    fun closeAllTabs(context: android.content.Context? = null) {
        val allTabIds = tabs.value.map { it.id }
        tabRepository.closeAllTabs(context)
        allTabIds.forEach { onTabClosed(it) }
    }

    fun selectTab(index: Int) {
        tabRepository.selectTab(index)
    }

    fun loadUriInActiveTab(url: String) {
        tabRepository.loadUriInActiveTab(url)
    }

    fun goBackInActiveTab() {
        tabRepository.goBackInActiveTab()
    }

    fun tryBackPressInActiveTab(): Boolean {
        return tabRepository.tryBackPressInActiveTab()
    }

    fun handleBackNavigation(): Boolean {
        val currentTabs = tabs.value
        val index = activeTabIndex.value
        if (index !in currentTabs.indices) return false

        val activeTab = currentTabs[index]
        android.util.Log.d("BackHandler", "handleBackNavigation: url=${activeTab.url}, parent=${activeTab.parentTabId}")
        if (activeTab.url == "yue://newtab") return false

        // Try WebView back
        val backResult = tabRepository.tryBackPressInActiveTab()
        android.util.Log.d("BackHandler", "tryBackPressInActiveTab result=$backResult")
        if (backResult) return true

        // If child tab, close it (no undo)
        if (activeTab.parentTabId != null && currentTabs.any { it.id == activeTab.parentTabId }) {
            android.util.Log.d("BackHandler", "closing child tab: ${activeTab.id}")
            closeTab(index, null, notifyUndo = false)
            return true
        }

        // Navigate to new tab page
        android.util.Log.d("BackHandler", "navigating to newtab")
        tabRepository.loadUriInActiveTab("yue://newtab")
        return true
    }

    fun goForwardInActiveTab() {
        tabRepository.goForwardInActiveTab()
    }

    fun tryForwardPressInActiveTab(): Boolean {
        return tabRepository.tryForwardPressInActiveTab()
    }

    fun reloadActiveTab() {
        tabRepository.reloadActiveTab()
    }

    fun updateTabThumbnail(index: Int, bitmap: android.graphics.Bitmap) {
        tabRepository.updateTabThumbnail(index, bitmap)
    }

    fun toggleDesktopSite(domain: String, enabled: Boolean) {
        settingsRepository.setDesktopSite(domain, enabled)
    }

    fun addAdblockWhitelistedDomain(domain: String) = settingsRepository.addAdblockWhitelistedDomain(domain)
    fun removeAdblockWhitelistedDomain(domain: String) = settingsRepository.removeAdblockWhitelistedDomain(domain)
    fun addDarkmodeWhitelistedDomain(domain: String) = settingsRepository.addDarkmodeWhitelistedDomain(domain)
    fun removeDarkmodeWhitelistedDomain(domain: String) = settingsRepository.removeDarkmodeWhitelistedDomain(domain)

    fun toggleDarkMode(enabled: Boolean) {
        settingsRepository.setDarkMode(enabled)
        reloadActiveTab()
    }

    fun toggleJavaScript(enabled: Boolean) {
        settingsRepository.setJavaScriptEnabled(enabled)
    }

    fun toggleZoom(enabled: Boolean) {
        settingsRepository.setZoomEnabled(enabled)
    }

    fun setSearchEngineUrl(url: String) {
        settingsRepository.setSearchEngineUrl(url)
    }

    fun toggleAdBlock(enabled: Boolean) {
        settingsRepository.setAdBlockEnabled(enabled)
    }

    fun toggleUserScript(enabled: Boolean) {
        settingsRepository.setUserScriptEnabled(enabled)
    }

    fun toggleAddon(addonId: String, enabled: Boolean) {
        settingsRepository.setAddonEnabled(addonId, enabled)
    }

    fun saveAddonMetadata(addonId: String, name: String, version: String, author: String, description: String) {
        settingsRepository.saveAddonMetadata(addonId, name, version, author, description)
    }

    fun addCustomFilter(filter: String) {
        settingsRepository.addCustomAdBlockFilter(filter)
    }

    fun removeCustomFilter(filter: String) {
        settingsRepository.removeCustomAdBlockFilter(filter)
    }

    fun syncAdBlockFilters(context: android.content.Context, onComplete: () -> Unit) {
        com.yue.browser.data.engine.AdBlockManager.syncFilters(context, onComplete)
    }

    fun addSpeedDial(name: String, url: String) {
        settingsRepository.addSpeedDial(name, url)
    }

    fun removeSpeedDial(url: String) {
        settingsRepository.removeSpeedDial(url)
    }

    fun clearBrowserData(context: android.content.Context, cookies: Boolean, cache: Boolean) {
        settingsRepository.clearBrowserData(context, cookies, cache)
    }

    fun addBlockedCssSelector(domain: String, selector: String) {
        (settingsRepository as? com.yue.browser.data.repository.SettingsRepositoryImpl)
            ?.addBlockedCssSelector(domain, selector)
        // Re-inject CSS ke semua tab yang sudah terbuka agar elemen langsung
        // hilang tanpa harus refresh manual.
        reinjectCosmeticFiltersAllTabs()
    }

    private fun reinjectCosmeticFiltersAllTabs() {
        val allTabs = tabRepository.tabsFlow.value
        val settings = settingsRepository.settingsFlow.value
        allTabs.forEach { tab ->
            val session = tab.session
            if (session is com.yue.browser.data.engine.SystemWebViewSession) {
                session.reinjectCosmeticFilters(settings)
            }
        }
    }

    fun removeBlockedCssSelector(domain: String, selector: String) {
        (settingsRepository as? com.yue.browser.data.repository.SettingsRepositoryImpl)
            ?.removeBlockedCssSelector(domain, selector)
    }

    fun startElementPicker(onElementsPicked: (cssSelectors: List<String>) -> Unit, onCancel: () -> Unit = {}) {
        val activeTab = tabRepository.tabsFlow.value.getOrNull(tabRepository.activeTabIndexFlow.value) ?: return
        val isDark = settings.value.isDarkModeSimulated
        activeTab.session.startElementPicker(onElementsPicked, onCancel, isDark)
    }

    fun stopElementPicker() {
        val activeTab = tabRepository.tabsFlow.value.getOrNull(tabRepository.activeTabIndexFlow.value) ?: return
        activeTab.session.stopElementPicker()
    }

    fun findInPage(query: String) {
        val webView = getActiveWebView() ?: return
        if (query.isBlank()) {
            webView.clearMatches()
            _findInPageResult.value = null
            return
        }
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            _findInPageResult.value = if (numberOfMatches == 0) null
                else FindInPageResult(activeMatchOrdinal, numberOfMatches)
        }
        webView.findAllAsync(query)
        webView.findNext(true)
    }

    fun findInPageNext(forward: Boolean) {
        getActiveWebView()?.findNext(forward)
    }

    fun clearFindInPage() {
        getActiveWebView()?.clearMatches()
        _findInPageResult.value = null
    }

    private fun getActiveWebView(): android.webkit.WebView? {
        val index = activeTabIndex.value
        val currentTabs = tabs.value
        if (index !in currentTabs.indices) return null
        return currentTabs[index].session.view as? android.webkit.WebView
    }

    fun saveTabs(context: android.content.Context) {
        tabRepository.saveState(context)
    }

    fun restoreTabs(context: android.content.Context) {
        tabRepository.restoreState(context)
    }



    fun newIncognitoTab(context: android.content.Context) {
        tabRepository.newIncognitoTab(context)
    }

    fun translatePage(sourceLanguage: String, targetLanguage: String) {
        tabRepository.translatePage(sourceLanguage, targetLanguage)
    }

    fun cancelTranslation() {
        tabRepository.cancelTranslation()
    }



    fun toggleBackgroundPlayNormal(enabled: Boolean) {
        settingsRepository.setBackgroundPlayEnabledNormal(enabled)
    }

    fun toggleBackgroundPlayPrivate(enabled: Boolean) {
        settingsRepository.setBackgroundPlayEnabledPrivate(enabled)
    }

    fun toggleVideoSpeedup(enabled: Boolean) {
        settingsRepository.setVideoSpeedupEnabled(enabled)
    }

    fun setVideoSpeedupRate(rate: Float) {
        settingsRepository.setVideoSpeedupRate(rate)
    }

    fun setDownloadMultiThread(enabled: Boolean) {
        settingsRepository.setDownloadMultiThread(enabled)
    }

    fun setDownloadDirectory(dir: String) {
        settingsRepository.setDownloadDirectory(dir)
    }

    fun setDeletePhysicalFile(enabled: Boolean) {
        settingsRepository.setDeletePhysicalFile(enabled)
    }



    // ====== Web Lock ======
    fun notifyUserInteraction() {
        webLockManager.notifyUserInteraction()
    }

    fun isDomainLockedForTab(tabId: String, domain: String): Boolean {
        return webLockManager.isDomainLockedForTab(tabId, domain)
    }

    fun unlockDomainForTab(tabId: String, domain: String) {
        webLockManager.unlockDomainForTab(tabId, domain)
    }

    fun lockAllTabs() {
        webLockManager.lockAllTabs()
    }

    fun reLockDomainForTab(tabId: String, domain: String) {
        webLockManager.reLockDomainForTab(tabId, domain)
    }

    fun onTabClosed(tabId: String) {
        webLockManager.onTabClosed(tabId)
    }

    fun addLockedDomain(domain: String) {
        settingsRepository.addLockedDomain(domain)
    }

    fun removeLockedDomain(domain: String) {
        settingsRepository.removeLockedDomain(domain)
        webLockManager.removeUnlockedDomain(domain)
    }

    fun setWebLockAutoLockTimeout(timeoutMinutes: String) {
        settingsRepository.setWebLockAutoLockTimeout(timeoutMinutes)
    }

    fun setupWebLockPin(pin: String) {
        settingsRepository.setWebLockPin(pin)
    }

    fun verifyWebLockPin(pin: String): Boolean {
        return settingsRepository.verifyWebLockPin(pin)
    }

    fun isWebLockPinSet(): Boolean {
        return settingsRepository.isWebLockPinSet()
    }

    fun isCurrentUrlLocked(): Boolean {
        val index = activeTabIndex.value
        val tab = tabs.value.getOrNull(index) ?: return false
        val url = tab.url
        if (url.isBlank() || url == "yue://newtab") return false
        val host = try { android.net.Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
        return isDomainLockedForTab(tab.id, host)
    }

    fun exportData(): String {
        val bookmarkRepo = bookmarkRepository as BookmarkRepositoryImpl
        val settingsRepo = settingsRepository as SettingsRepositoryImpl
        val passwordRepo = passwordRepository as PasswordRepositoryImpl
        return ExportImportHelper.exportToJson(
            settings = settingsRepo.settingsFlow.value,
            bookmarks = bookmarkRepo.bookmarksFlow.value,
            passwords = passwordRepo.passwordsFlow.value
        )
    }

    fun importData(json: String): ExportImportHelper.ImportResult {
        val bookmarkRepo = bookmarkRepository as BookmarkRepositoryImpl
        val settingsRepo = settingsRepository as SettingsRepositoryImpl
        val passwordRepo = passwordRepository as PasswordRepositoryImpl
        return ExportImportHelper.importFromJson(json, settingsRepo, bookmarkRepo, passwordRepo)
    }

    fun exportPasswords(passwords: List<PasswordEntry>): String {
        return ExportImportHelper.exportToJson(
            settings = settingsRepository.settingsFlow.value,
            bookmarks = emptyList(),
            passwords = passwords
        )
    }

    fun exportPasswordsCsv(passwords: List<PasswordEntry>): String {
        return ExportImportHelper.exportPasswordsCsv(passwords)
    }

    fun importPasswordsCsv(csv: String): ExportImportHelper.ImportResult {
        val passwordRepo = passwordRepository as PasswordRepositoryImpl
        return ExportImportHelper.importPasswordsCsv(csv, passwordRepo)
    }

    fun importPasswords(json: String): ExportImportHelper.ImportResult {
        val passwordRepo = passwordRepository as PasswordRepositoryImpl
        return ExportImportHelper.importPasswords(json, passwordRepo)
    }
}
