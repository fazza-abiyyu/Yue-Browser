package com.yue.browser.domain.repository

import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup
import kotlinx.coroutines.flow.StateFlow

interface TabRepository {
    val tabsFlow: StateFlow<List<BrowserTab>>
    val activeTabIndexFlow: StateFlow<Int>
    val groupsFlow: StateFlow<Map<String, TabGroup>>
    
    fun createGroup(name: String, colorIndex: Int, tabIds: List<String>): String
    fun addTabToGroup(tabId: String, groupId: String)
    fun removeTabFromGroup(tabId: String)
    fun renameGroup(groupId: String, newName: String)
    fun updateGroupColor(groupId: String, colorIndex: Int)
    fun deleteGroup(groupId: String)
    fun moveTab(fromIndex: Int, toIndex: Int)
    
    fun createNewTab(
        context: android.content.Context,
        url: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)? = null,
        loadImmediately: Boolean = true,
        tabId: String? = null,
        title: String? = null,
        parentTabId: String? = null
    )
    fun createNewTabWithWebView(
        context: android.content.Context,
        webView: android.webkit.WebView,
        isPrivate: Boolean,
        openerHost: String,
        parentTabId: String? = null
    )
    fun newIncognitoTab(context: android.content.Context)
    fun closeTab(index: Int, context: android.content.Context? = null)
    fun closePrivateTabsOnly()
    fun closeAllTabs(context: android.content.Context? = null)
    fun selectTab(index: Int)
    fun loadUriInActiveTab(url: String)
    fun goBackInActiveTab()
    fun tryBackPressInActiveTab(): Boolean
    fun tryForwardPressInActiveTab(): Boolean
    fun goForwardInActiveTab()
    fun reloadActiveTab()
    fun updateTabThumbnail(index: Int, bitmap: android.graphics.Bitmap)
    fun saveState(context: android.content.Context)
    fun restoreState(context: android.content.Context)
    fun translatePage(sourceLanguage: String, targetLanguage: String)
    fun cancelTranslation()
}
