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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.yue.browser.domain.model.BookmarkItem
import com.yue.browser.domain.model.TabGroup
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.repository.BookmarkRepository
import com.yue.browser.domain.repository.DownloadRepository
import com.yue.browser.domain.repository.HistoryRepository
import com.yue.browser.data.repository.BookmarkRepositoryImpl
import com.yue.browser.data.repository.DownloadRepositoryImpl
import com.yue.browser.data.repository.HistoryRepositoryImpl

class BrowserViewModel(
    private val tabRepository: TabRepository = TabRepositoryImpl(
        browserEngine = SystemWebViewEngine(SettingsRepositoryImpl.instance),
        settingsRepository = SettingsRepositoryImpl.instance
    ),
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl.instance,
    private val historyRepository: HistoryRepository = HistoryRepositoryImpl.instance,
    private val bookmarkRepository: BookmarkRepository = BookmarkRepositoryImpl.instance,
    private val downloadRepository: DownloadRepository = DownloadRepositoryImpl.instance
) : ViewModel() {

    val tabs: StateFlow<List<BrowserTab>> = tabRepository.tabsFlow
    val activeTabIndex: StateFlow<Int> = tabRepository.activeTabIndexFlow
    val groups: StateFlow<Map<String, TabGroup>> = tabRepository.groupsFlow
    val settings: StateFlow<BrowserSettings> = settingsRepository.settingsFlow
    val history: StateFlow<List<HistoryItem>> = historyRepository.historyFlow
    val bookmarks: StateFlow<List<BookmarkItem>> = bookmarkRepository.bookmarksFlow
    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.downloadsFlow

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

    init {
        viewModelScope.launch {
            settings.collect { settingsVal ->
                tabs.value.forEach { tab ->
                    val darkActive = settingsVal.isDarkModeSimulated || settingsVal.enabledAddons.contains("darkreader")
                    tab.session.setForceDarkMode(darkActive)
                    tab.session.setJavaScriptEnabled(settingsVal.isJavaScriptEnabled)
                    tab.session.setZoomEnabled(settingsVal.isZoomEnabled)
                }
            }
        }
    }

    fun createNewTab(context: android.content.Context, initialUrl: String, isPrivate: Boolean = false) {
        tabRepository.createNewTab(context, initialUrl, isPrivate)
        tabs.value.lastOrNull()?.session?.let { session ->
            val darkActive = settings.value.isDarkModeSimulated || settings.value.enabledAddons.contains("darkreader")
            session.setForceDarkMode(darkActive)
            session.setJavaScriptEnabled(settings.value.isJavaScriptEnabled)
            session.setZoomEnabled(settings.value.isZoomEnabled)
        }
    }

    fun closeTab(index: Int, context: android.content.Context? = null) {
        val tabId = tabs.value.getOrNull(index)?.id
        tabRepository.closeTab(index, context)
        if (tabId != null) onTabClosed(tabId)
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

    fun goForwardInActiveTab() {
        tabRepository.goForwardInActiveTab()
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

    fun toggleDarkMode(enabled: Boolean) {
        settingsRepository.setDarkMode(enabled)
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

    fun saveTabs(context: android.content.Context) {
        tabRepository.saveState(context)
    }

    fun restoreTabs(context: android.content.Context) {
        tabRepository.restoreState(context)
    }

    fun isCurrentPageBookmarked(): Boolean {
        val index = activeTabIndex.value
        val currentTabs = tabs.value
        if (index in currentTabs.indices) {
            val url = currentTabs[index].url
            return url != "yue://newtab" && url.isNotBlank() && bookmarkRepository.isBookmarked(url)
        }
        return false
    }

    fun toggleBookmark(context: android.content.Context) {
        val index = activeTabIndex.value
        val currentTabs = tabs.value
        if (index in currentTabs.indices) {
            val tab = currentTabs[index]
            val url = tab.url
            if (url != "yue://newtab" && url.isNotBlank()) {
                if (bookmarkRepository.isBookmarked(url)) {
                    bookmarkRepository.removeBookmark(url)
                    android.widget.Toast.makeText(context, "Dihapus dari bookmark", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val title = tab.title.ifBlank { url }
                    bookmarkRepository.addBookmark(url, title)
                    android.widget.Toast.makeText(context, "Berhasil di bookmark", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    fun removeBookmark(url: String) {
        bookmarkRepository.removeBookmark(url)
    }

    fun removeHistory(url: String) {
        historyRepository.removeHistory(url)
    }

    fun clearHistory() {
        historyRepository.clearHistory()
    }

    fun initializeDownloads(context: android.content.Context) {
        (downloadRepository as DownloadRepositoryImpl).initialize(context)
    }

    fun initializeHistory(context: android.content.Context) {
        (historyRepository as HistoryRepositoryImpl).initialize(context)
    }

    fun startDownload(url: String, fileName: String, context: android.content.Context, connectionCount: Int = 4, cookies: String? = null, webViewUserAgent: String? = null) {
        downloadRepository.startDownload(url, fileName, context, connectionCount, cookies, webViewUserAgent)
    }

    fun pauseDownload(id: String) {
        downloadRepository.pauseDownload(id)
    }

    fun resumeDownload(id: String, context: android.content.Context) {
        downloadRepository.resumeDownload(id, context)
    }

    fun cancelDownload(id: String) {
        downloadRepository.cancelDownload(id)
    }

    fun removeDownload(id: String) {
        downloadRepository.removeDownload(id)
    }

    fun replaceUrlAndResume(id: String, newUrl: String, context: android.content.Context) {
        downloadRepository.replaceUrlAndResume(id, newUrl, context)
    }

    fun rewriteFile(id: String, context: android.content.Context) {
        downloadRepository.rewriteFile(id, context)
    }

    fun setConnectionCount(id: String, count: Int) {
        downloadRepository.setConnectionCount(id, count)
    }

    fun rebuildChunksAndResume(id: String, newConnectionCount: Int, context: android.content.Context) {
        downloadRepository.rebuildChunksAndResume(id, newConnectionCount, context)
    }

    fun toggleBackgroundPlayNormal(enabled: Boolean) {
        settingsRepository.setBackgroundPlayEnabledNormal(enabled)
    }

    fun toggleBackgroundPlayPrivate(enabled: Boolean) {
        settingsRepository.setBackgroundPlayEnabledPrivate(enabled)
    }

    // ====== Web Lock ======
    // Per-tab unlocked domains: tabId -> domain -> unlockTimestampMillis
    private val _unlockedDomainsByTab = mutableMapOf<String, MutableMap<String, Long>>()

    private var lastInteractionTimeMillis = System.currentTimeMillis()

    fun notifyUserInteraction() {
        lastInteractionTimeMillis = System.currentTimeMillis()
    }

    fun isDomainLockedForTab(tabId: String, domain: String): Boolean {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        val settings = settingsRepository.settingsFlow.value
        val isLocked = settings.lockedDomains.any { cleanDomain == it || cleanDomain.endsWith(".$it") || it.endsWith(".$cleanDomain") }
        if (!isLocked) return false
        val timeoutMinutes = settings.webLockAutoLockTimeout.toIntOrNull() ?: 0
        if (timeoutMinutes == 0) return true
        val unlocked = _unlockedDomainsByTab[tabId] ?: return true
        // Check unlock: traverse up domain hierarchy so subdomains inherit parent unlock
        var checkDomain = cleanDomain
        while (checkDomain.isNotEmpty()) {
            val unlockTime = unlocked[checkDomain]
            if (unlockTime != null) {
                val timeoutMs = timeoutMinutes * 60 * 1000L
                if (System.currentTimeMillis() - unlockTime > timeoutMs) {
                    unlocked.remove(checkDomain)
                    return true
                }
                return false
            }
            val dotIndex = checkDomain.indexOf('.')
            if (dotIndex == -1) break
            checkDomain = checkDomain.substring(dotIndex + 1)
        }
        return true
    }

    fun unlockDomainForTab(tabId: String, domain: String) {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        _unlockedDomainsByTab.getOrPut(tabId) { mutableMapOf() }[cleanDomain] = System.currentTimeMillis()
    }

    fun lockAllTabs() {
        _unlockedDomainsByTab.clear()
    }

    fun reLockDomainForTab(tabId: String, domain: String) {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        _unlockedDomainsByTab[tabId]?.remove(cleanDomain)
    }

    fun onTabClosed(tabId: String) {
        _unlockedDomainsByTab.remove(tabId)
    }

    fun addLockedDomain(domain: String) {
        settingsRepository.addLockedDomain(domain)
    }

    fun removeLockedDomain(domain: String) {
        settingsRepository.removeLockedDomain(domain)
        // Also remove from all session unlocks
        val cleaned = domain.removePrefix("www.").lowercase()
        _unlockedDomainsByTab.values.forEach { it.remove(cleaned) }
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

    // Idle timer: periodically check if user has been inactive beyond the timeout
    private var idleTimerJob: kotlinx.coroutines.Job? = null

    private fun startIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = viewModelScope.launch {
            while (true) {
                delay(5000) // check every 5 seconds
                val settings = settingsRepository.settingsFlow.value
                val timeoutMinutes = settings.webLockAutoLockTimeout.toIntOrNull() ?: 0
                if (timeoutMinutes <= 0) continue
                val timeoutMs = timeoutMinutes * 60 * 1000L
                if (System.currentTimeMillis() - lastInteractionTimeMillis > timeoutMs) {
                    lockAllTabs()
                }
            }
        }
    }

    init {
        startIdleTimer()
    }
}
