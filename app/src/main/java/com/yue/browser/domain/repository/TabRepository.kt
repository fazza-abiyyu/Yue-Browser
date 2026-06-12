package com.yue.browser.domain.repository

import com.yue.browser.domain.model.BrowserTab
import kotlinx.coroutines.flow.StateFlow

interface TabRepository {
    val tabsFlow: StateFlow<List<BrowserTab>>
    val activeTabIndexFlow: StateFlow<Int>
    
    fun createNewTab(context: android.content.Context, url: String, isPrivate: Boolean, onLanguageDetected: ((String) -> Unit)? = null, loadImmediately: Boolean = true)
    fun newIncognitoTab(context: android.content.Context)
    fun closeTab(index: Int, context: android.content.Context? = null)
    fun closePrivateTabsOnly()
    fun closeAllTabs(context: android.content.Context? = null)
    fun selectTab(index: Int)
    fun loadUriInActiveTab(url: String)
    fun goBackInActiveTab()
    fun goForwardInActiveTab()
    fun reloadActiveTab()
    fun updateTabThumbnail(index: Int, bitmap: android.graphics.Bitmap)
    fun saveState(context: android.content.Context)
    fun restoreState(context: android.content.Context)
    fun translatePage(sourceLanguage: String, targetLanguage: String)
}
