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
    fun setZoomEnabled(enabled: Boolean)
    fun setBackgroundPlayEnabledNormal(enabled: Boolean)
    fun setBackgroundPlayEnabledPrivate(enabled: Boolean)
    // Web Lock
    fun addLockedDomain(domain: String)
    fun removeLockedDomain(domain: String)
    fun setWebLockPin(pin: String)
    fun verifyWebLockPin(pin: String): Boolean
    fun isWebLockPinSet(): Boolean
    fun setWebLockAutoLockTimeout(timeoutMinutes: String)
    fun setWebLockMaxAttempts(attempts: Int)
    fun setWebLockLockDurationMinutes(minutes: Int)
    fun setWebLockAttemptsEnabled(enabled: Boolean)
    // Playback Settings
    fun setVideoSpeedupEnabled(enabled: Boolean)
    fun setVideoSpeedupRate(rate: Float)
    fun setAutoPipEnabled(enabled: Boolean)
    fun setVideoOrientationLocked(enabled: Boolean)
    
    // Whitelist
    fun addAdblockWhitelistedDomain(domain: String)
    fun removeAdblockWhitelistedDomain(domain: String)
    fun addDarkmodeWhitelistedDomain(domain: String)
    fun removeDarkmodeWhitelistedDomain(domain: String)
    
    // Download Settings
    fun setDownloadMultiThread(enabled: Boolean)
    fun setDownloadDirectory(dir: String)
    fun setDeletePhysicalFile(enabled: Boolean)
    fun setDefaultConnectionCount(count: Int)
 
    // First Run
    fun setFirstRunCompleted()

    // App language & theme
    fun setAppLanguage(lang: String)
    fun setAppThemeMode(theme: String)
}
