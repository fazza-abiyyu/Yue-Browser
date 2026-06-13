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

import kotlinx.coroutines.launch

import com.yue.browser.domain.model.BookmarkItem
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
    val settings: StateFlow<BrowserSettings> = settingsRepository.settingsFlow
    val history: StateFlow<List<HistoryItem>> = historyRepository.historyFlow
    val bookmarks: StateFlow<List<BookmarkItem>> = bookmarkRepository.bookmarksFlow
    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.downloadsFlow

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
                }
            }
        }
    }

    fun createNewTab(context: android.content.Context, initialUrl: String, isPrivate: Boolean = false) {
        tabRepository.createNewTab(context, initialUrl, isPrivate)
        tabs.value.lastOrNull()?.session?.let { session ->
            val darkActive = settings.value.isDarkModeSimulated || settings.value.enabledAddons.contains("darkreader")
            session.setForceDarkMode(darkActive)
        }
    }

    fun closeTab(index: Int, context: android.content.Context? = null) {
        tabRepository.closeTab(index, context)
    }

    fun closePrivateTabsOnly() {
        tabRepository.closePrivateTabsOnly()
    }

    fun closeAllTabs(context: android.content.Context? = null) {
        tabRepository.closeAllTabs(context)
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

    fun startElementPicker(onElementPicked: (cssSelector: String) -> Unit) {
        val activeTab = tabRepository.tabsFlow.value.getOrNull(tabRepository.activeTabIndexFlow.value) ?: return
        activeTab.session.startElementPicker(onElementPicked)
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
}
