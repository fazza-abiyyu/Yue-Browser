package com.yue.browser.presentation

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import com.yue.browser.data.repository.BookmarkRepositoryImpl
import com.yue.browser.data.repository.DownloadRepositoryImpl
import com.yue.browser.data.repository.HistoryRepositoryImpl
import com.yue.browser.data.repository.OfflinePageRepositoryImpl
import com.yue.browser.data.repository.PasswordRepositoryImpl
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.PasswordEntry
import java.io.File

fun BrowserViewModel.configureTabSession(tab: BrowserTab, settingsVal: BrowserSettings) {
    val host = try { Uri.parse(tab.url).host ?: "" } catch (e: Exception) { "" }
    val cleanHost = host.removePrefix("www.").removePrefix("m.").lowercase()
    val isWhitelisted = cleanHost.isNotEmpty() && settingsVal.darkmodeWhitelistedDomains.any {
        cleanHost == it || cleanHost.endsWith(".$it")
    }
    val darkActive = (settingsVal.isDarkModeSimulated || settingsVal.enabledAddons.contains("darkreader")) && !isWhitelisted
    tab.session.setForceDarkMode(darkActive)
    tab.session.setJavaScriptEnabled(settingsVal.isJavaScriptEnabled)
    tab.session.setZoomEnabled(settingsVal.isZoomEnabled)
}

fun BrowserViewModel.isCurrentPageBookmarked(): Boolean {
    val index = activeTabIndex.value
    val currentTabs = tabs.value
    if (index in currentTabs.indices) {
        val url = currentTabs[index].url
        return url != "yue://newtab" && url.isNotBlank() && bookmarkRepository.isBookmarked(url)
    }
    return false
}

fun BrowserViewModel.toggleBookmark(context: Context) {
    val index = activeTabIndex.value
    val currentTabs = tabs.value
    if (index in currentTabs.indices) {
        val tab = currentTabs[index]
        val url = tab.url
        if (url != "yue://newtab" && url.isNotBlank()) {
            if (bookmarkRepository.isBookmarked(url)) {
                bookmarkRepository.removeBookmark(url)
                Toast.makeText(context, context.getString(com.yue.browser.R.string.bookmark_removed), Toast.LENGTH_SHORT).show()
            } else {
                val title = tab.title.ifBlank { url }
                bookmarkRepository.addBookmark(url, title)
                Toast.makeText(context, context.getString(com.yue.browser.R.string.bookmark_added), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun BrowserViewModel.removeBookmark(url: String) {
    bookmarkRepository.removeBookmark(url)
}

fun BrowserViewModel.isCurrentPageSavedOffline(): Boolean {
    val index = activeTabIndex.value
    val currentTabs = tabs.value
    if (index in currentTabs.indices) {
        val url = currentTabs[index].url
        return url != "yue://newtab" && url.isNotBlank() && offlinePageRepository.isSavedOffline(url)
    }
    return false
}

fun BrowserViewModel.saveCurrentPageOffline(context: Context) {
    val index = activeTabIndex.value
    val currentTabs = tabs.value
    if (index in currentTabs.indices) {
        val tab = currentTabs[index]
        val url = tab.url
        if (url == "yue://newtab" || url.isBlank() || url.startsWith("file://")) {
            Toast.makeText(context, context.getString(com.yue.browser.R.string.offline_page_save_cannot), Toast.LENGTH_SHORT).show()
            return
        }

        val webView = tab.session.view as? WebView
        if (webView == null) {
            Toast.makeText(context, context.getString(com.yue.browser.R.string.offline_page_capture_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val dir = File(context.filesDir, "offline_pages")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val sanitizedTitle = tab.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
        val fileName = "offline_${System.currentTimeMillis()}_$sanitizedTitle.mht"
        val file = File(dir, fileName)
        val absolutePath = file.absolutePath

        webView.saveWebArchive(absolutePath, false) { path ->
            if (path != null) {
                val title = tab.title.ifBlank { url }
                offlinePageRepository.addOfflinePage(url, title, path)
                Toast.makeText(context, context.getString(com.yue.browser.R.string.offline_page_saved_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(com.yue.browser.R.string.offline_page_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun BrowserViewModel.removeOfflinePage(id: String) {
    offlinePageRepository.removeOfflinePage(id)
}

fun BrowserViewModel.removeHistory(url: String) {
    historyRepository.removeHistory(url)
}

fun BrowserViewModel.clearHistory() {
    historyRepository.clearHistory()
}

fun BrowserViewModel.initializeDownloads(context: Context) {
    (downloadRepository as DownloadRepositoryImpl).initialize(context)
}

fun BrowserViewModel.initializeHistory(context: Context) {
    (historyRepository as HistoryRepositoryImpl).initialize(context)
}

fun BrowserViewModel.initializePasswords(context: Context) {
    (passwordRepository as PasswordRepositoryImpl).initialize(context)
}

fun BrowserViewModel.addPassword(entry: PasswordEntry) {
    passwordRepository.addPassword(entry)
}

fun BrowserViewModel.updatePassword(entry: PasswordEntry) {
    passwordRepository.updatePassword(entry)
}

fun BrowserViewModel.deletePassword(id: String) {
    passwordRepository.deletePassword(id)
}

fun BrowserViewModel.getPasswordForUrl(url: String): PasswordEntry? {
    return passwordRepository.getPasswordForUrl(url)
}

fun BrowserViewModel.startDownload(
    url: String,
    fileName: String,
    context: Context,
    connectionCount: Int = 4,
    cookies: String? = null,
    webViewUserAgent: String? = null
) {
    downloadRepository.startDownload(url, fileName, context, connectionCount, cookies, webViewUserAgent)
}

fun BrowserViewModel.pauseDownload(id: String) {
    downloadRepository.pauseDownload(id)
}

fun BrowserViewModel.resumeDownload(id: String, context: Context) {
    downloadRepository.resumeDownload(id, context)
}

fun BrowserViewModel.cancelDownload(id: String) {
    downloadRepository.cancelDownload(id)
}

fun BrowserViewModel.removeDownload(id: String) {
    downloadRepository.removeDownload(id)
}

fun BrowserViewModel.replaceUrlAndResume(id: String, newUrl: String, context: Context) {
    downloadRepository.replaceUrlAndResume(id, newUrl, context)
}

fun BrowserViewModel.rewriteFile(id: String, context: Context) {
    downloadRepository.rewriteFile(id, context)
}

fun BrowserViewModel.setConnectionCount(id: String, count: Int) {
    downloadRepository.setConnectionCount(id, count)
}

fun BrowserViewModel.rebuildChunksAndResume(id: String, newConnectionCount: Int, context: Context) {
    downloadRepository.rebuildChunksAndResume(id, newConnectionCount, context)
}

fun BrowserViewModel.setHttpsOnlyModeEnabled(enabled: Boolean) {
    settingsRepository.setHttpsOnlyModeEnabled(enabled)
}

fun BrowserViewModel.setDoNotTrackEnabled(enabled: Boolean) {
    settingsRepository.setDoNotTrackEnabled(enabled)
}

fun BrowserViewModel.setBlockThirdPartyCookiesEnabled(enabled: Boolean) {
    settingsRepository.setBlockThirdPartyCookiesEnabled(enabled)
}

fun BrowserViewModel.setFingerprintProtectionEnabled(enabled: Boolean) {
    settingsRepository.setFingerprintProtectionEnabled(enabled)
}

fun BrowserViewModel.setReferrerControlEnabled(enabled: Boolean) {
    settingsRepository.setReferrerControlEnabled(enabled)
}

fun BrowserViewModel.setSafeBrowsingEnabled(enabled: Boolean) {
    settingsRepository.setSafeBrowsingEnabled(enabled)
}

fun BrowserViewModel.setSitePermission(domain: String, permissionType: String, granted: Boolean?) {
    settingsRepository.setSitePermission(domain, permissionType, granted)
}
