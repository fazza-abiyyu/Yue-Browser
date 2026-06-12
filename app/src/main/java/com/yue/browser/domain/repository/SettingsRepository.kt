package com.yue.browser.domain.repository

import com.yue.browser.domain.model.BrowserSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settingsFlow: StateFlow<BrowserSettings>
    fun setUserScriptEnabled(enabled: Boolean)
    fun setDesktopSite(domain: String, enabled: Boolean)
    fun setDarkMode(enabled: Boolean)
    fun setJavaScriptEnabled(enabled: Boolean)
    fun setSearchEngineUrl(url: String)
    fun setAdBlockEnabled(enabled: Boolean)
    fun setAddonEnabled(addonId: String, enabled: Boolean)
    fun saveAddonMetadata(addonId: String, name: String, version: String, author: String, description: String)
    fun addCustomAdBlockFilter(filter: String)
    fun removeCustomAdBlockFilter(filter: String)
    fun addSpeedDial(name: String, url: String)
    fun removeSpeedDial(url: String)
    fun clearBrowserData(context: android.content.Context, cookies: Boolean, cache: Boolean)
}
