package com.yue.browser.data.repository

import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.yue.browser.domain.model.SpeedDialConfig

class SettingsRepositoryImpl : SettingsRepository {
    companion object {
        val instance = SettingsRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null
    private val _settings = MutableStateFlow(BrowserSettings())
    override val settingsFlow: StateFlow<BrowserSettings> = _settings.asStateFlow()

    fun initialize(context: android.content.Context) {
        appContext = context.applicationContext
        if (sharedPreferences != null) {
            loadSettings()
            return
        }
        sharedPreferences = context.getSharedPreferences("yue_browser_settings", android.content.Context.MODE_PRIVATE)
        loadSettings()
    }

    private fun isSystemDarkMode(): Boolean {
        val context = appContext ?: return false
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun loadSettings() {
        val prefs = sharedPreferences ?: return
        val context = appContext ?: return
        val defaultSettings = BrowserSettings()
        
        val savedDesktopDomains = prefs.getStringSet("desktopDomains", defaultSettings.desktopDomains) ?: defaultSettings.desktopDomains
        val desktopDomains = savedDesktopDomains.toMutableSet()
        val appLanguage = prefs.getString("appLanguage", "system") ?: "system"
        val appThemeMode = prefs.getString("appThemeMode", "system") ?: "system"
        
        val systemDark = if (appThemeMode == "system") {
            val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            appThemeMode == "dark"
        }
        val isDark = prefs.getBoolean("isDarkModeSimulated", systemDark)
        val isJs = prefs.getBoolean("isJavaScriptEnabled", defaultSettings.isJavaScriptEnabled)
        val isUserScript = prefs.getBoolean("isUserScriptEnabled", defaultSettings.isUserScriptEnabled)
        val isZoom = prefs.getBoolean("isZoomEnabled", defaultSettings.isZoomEnabled)
        val isBgPlayNormal = prefs.getBoolean("isBackgroundPlayEnabledNormal", defaultSettings.isBackgroundPlayEnabledNormal)
        val isBgPlayPrivate = prefs.getBoolean("isBackgroundPlayEnabledPrivate", defaultSettings.isBackgroundPlayEnabledPrivate)
        val lockedDomains = prefs.getStringSet("lockedDomains", emptySet()) ?: emptySet()
        val webLockPinHash = prefs.getString("webLockPinHash", "") ?: ""
        val webLockAutoLockTimeout = prefs.getString("webLockAutoLockTimeout", "0") ?: "0"
        val webLockMaxAttempts = prefs.getInt("webLockMaxAttempts", defaultSettings.webLockMaxAttempts)
        val webLockLockDurationMinutes = prefs.getInt("webLockLockDurationMinutes", defaultSettings.webLockLockDurationMinutes)
        val webLockAttemptsEnabled = prefs.getBoolean("webLockAttemptsEnabled", defaultSettings.webLockAttemptsEnabled)
        val isVideoSpeedup = prefs.getBoolean("isVideoSpeedupEnabled", defaultSettings.isVideoSpeedupEnabled)
        val videoSpeedupRate = prefs.getFloat("videoSpeedupRate", defaultSettings.videoSpeedupRate)
        val isAutoPip = prefs.getBoolean("isAutoPipEnabled", defaultSettings.isAutoPipEnabled)
        val isVideoOrientationLocked = prefs.getBoolean("isVideoOrientationLocked", defaultSettings.isVideoOrientationLocked)
        val searchUrl = prefs.getString("searchEngineUrl", defaultSettings.searchEngineUrl) ?: defaultSettings.searchEngineUrl
        val isDownloadMultiThread = prefs.getBoolean("isDownloadMultiThread", defaultSettings.isDownloadMultiThread)
        val downloadDirectory = prefs.getString("downloadDirectory", defaultSettings.downloadDirectory) ?: defaultSettings.downloadDirectory
        val isDeletePhysicalFile = prefs.getBoolean("isDeletePhysicalFile", defaultSettings.isDeletePhysicalFile)
        val defaultConnectionCount = prefs.getInt("defaultConnectionCount", defaultSettings.defaultConnectionCount)
        val firstRunCompleted = prefs.getBoolean("firstRunCompleted", defaultSettings.firstRunCompleted)
        val isAdBlock = true // FORCED ON for testing
        val enabledAddons = prefs.getStringSet("enabledAddons", defaultSettings.enabledAddons) ?: defaultSettings.enabledAddons
        
        val savedAdblockWhitelist = prefs.getStringSet("adblockWhitelistedDomains", emptySet()) ?: emptySet()
        val savedDarkmodeWhitelist = prefs.getStringSet("darkmodeWhitelistedDomains", emptySet()) ?: emptySet()
        
        val filtersJson = prefs.getString("customAdBlockFilters", "[]") ?: "[]"
        val customFilters = try {
            val jsonArray = org.json.JSONArray(filtersJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList<String>()
        }

        val speedDialsJson = prefs.getString("speedDials", "") ?: ""
        val speedDials = if (speedDialsJson.isBlank()) {
            defaultSettings.speedDials
        } else {
            try {
                val jsonArray = org.json.JSONArray(speedDialsJson)
                val list = mutableListOf<SpeedDialConfig>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        SpeedDialConfig(
                            name = obj.getString("name"),
                            url = obj.getString("url"),
                            iconLetter = obj.getString("iconLetter"),
                            iconBgColorHex = obj.getString("iconBgColorHex")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                defaultSettings.speedDials
            }
        }

        val addonsMetadataJson = prefs.getString("addonsMetadata", "{}") ?: "{}"
        val addonsMetadata = try {
            val jsonObject = org.json.JSONObject(addonsMetadataJson)
            val map = mutableMapOf<String, Map<String, String>>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val innerObj = jsonObject.getJSONObject(key)
                val innerMap = mutableMapOf<String, String>()
                val innerKeys = innerObj.keys()
                while (innerKeys.hasNext()) {
                    val innerKey = innerKeys.next()
                    innerMap[innerKey] = innerObj.getString(innerKey)
                }
                map[key] = innerMap
            }
            map
        } catch (e: Exception) {
            emptyMap<String, Map<String, String>>()
        }

        val blockedSelectorsJson = prefs.getString("blockedCssSelectors", "{}") ?: "{}"
        val blockedSelectors = try {
            val jsonObject = org.json.JSONObject(blockedSelectorsJson)
            val map = mutableMapOf<String, List<String>>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                val arr = jsonObject.getJSONArray(domain)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                map[domain] = list
            }
            map
        } catch (e: Exception) {
            emptyMap<String, List<String>>()
        }

        _settings.value = BrowserSettings(
            desktopDomains = desktopDomains,
            isDarkModeSimulated = isDark,
            isJavaScriptEnabled = isJs,
            searchEngineUrl = searchUrl,
            isAdBlockEnabled = isAdBlock,
            enabledAddons = enabledAddons,
            isUserScriptEnabled = isUserScript,
            customAdBlockFilters = customFilters,
            speedDials = speedDials,
            addonsMetadata = addonsMetadata,
            blockedCssSelectors = blockedSelectors,
            isZoomEnabled = isZoom,
            isBackgroundPlayEnabledNormal = isBgPlayNormal,
            isBackgroundPlayEnabledPrivate = isBgPlayPrivate,
            lockedDomains = lockedDomains,
            webLockPinHash = webLockPinHash,
            webLockAutoLockTimeout = webLockAutoLockTimeout,
            webLockMaxAttempts = webLockMaxAttempts,
            webLockLockDurationMinutes = webLockLockDurationMinutes,
            webLockAttemptsEnabled = webLockAttemptsEnabled,
            isVideoSpeedupEnabled = isVideoSpeedup,
            videoSpeedupRate = videoSpeedupRate,
            isAutoPipEnabled = isAutoPip,
            isVideoOrientationLocked = isVideoOrientationLocked,
            adblockWhitelistedDomains = savedAdblockWhitelist,
            darkmodeWhitelistedDomains = savedDarkmodeWhitelist,
            isDownloadMultiThread = isDownloadMultiThread,
            downloadDirectory = downloadDirectory,
            isDeletePhysicalFile = isDeletePhysicalFile,
            defaultConnectionCount = defaultConnectionCount,
            appLanguage = appLanguage,
            appThemeMode = appThemeMode,
            firstRunCompleted = firstRunCompleted
        )
    }

    private fun saveSettings() {
        val prefs = sharedPreferences ?: return
        val current = _settings.value
        
        val filtersArray = org.json.JSONArray()
        current.customAdBlockFilters.forEach { filtersArray.put(it) }
        
        val speedDialsArray = org.json.JSONArray()
        current.speedDials.forEach { dial ->
            val obj = org.json.JSONObject()
            obj.put("name", dial.name)
            obj.put("url", dial.url)
            obj.put("iconLetter", dial.iconLetter)
            obj.put("iconBgColorHex", dial.iconBgColorHex)
            speedDialsArray.put(obj)
        }

        val addonsMetadataObj = org.json.JSONObject()
        current.addonsMetadata.forEach { (addonId, map) ->
            val innerObj = org.json.JSONObject()
            map.forEach { (k, v) -> innerObj.put(k, v) }
            addonsMetadataObj.put(addonId, innerObj)
        }

        val blockedSelectorsObj = org.json.JSONObject()
        current.blockedCssSelectors.forEach { (domain, selectors) ->
            val arr = org.json.JSONArray()
            selectors.forEach { arr.put(it) }
            blockedSelectorsObj.put(domain, arr)
        }

        prefs.edit().apply {
            putStringSet("desktopDomains", current.desktopDomains)
            putBoolean("isDarkModeSimulated", current.isDarkModeSimulated)
            putBoolean("isJavaScriptEnabled", current.isJavaScriptEnabled)
            putBoolean("isUserScriptEnabled", current.isUserScriptEnabled)
            putString("searchEngineUrl", current.searchEngineUrl)
            putBoolean("isAdBlockEnabled", current.isAdBlockEnabled)
            putStringSet("enabledAddons", current.enabledAddons)
            putString("customAdBlockFilters", filtersArray.toString())
            putString("speedDials", speedDialsArray.toString())
            putString("addonsMetadata", addonsMetadataObj.toString())
            putString("blockedCssSelectors", blockedSelectorsObj.toString())
            putBoolean("isZoomEnabled", current.isZoomEnabled)
            putBoolean("isBackgroundPlayEnabledNormal", current.isBackgroundPlayEnabledNormal)
            putBoolean("isBackgroundPlayEnabledPrivate", current.isBackgroundPlayEnabledPrivate)
            putStringSet("lockedDomains", current.lockedDomains)
            putString("webLockPinHash", current.webLockPinHash)
            putString("webLockAutoLockTimeout", current.webLockAutoLockTimeout)
            putInt("webLockMaxAttempts", current.webLockMaxAttempts)
            putInt("webLockLockDurationMinutes", current.webLockLockDurationMinutes)
            putBoolean("webLockAttemptsEnabled", current.webLockAttemptsEnabled)
            putBoolean("isVideoSpeedupEnabled", current.isVideoSpeedupEnabled)
            putFloat("videoSpeedupRate", current.videoSpeedupRate)
            putBoolean("isAutoPipEnabled", current.isAutoPipEnabled)
            putBoolean("isVideoOrientationLocked", current.isVideoOrientationLocked)
            putStringSet("adblockWhitelistedDomains", current.adblockWhitelistedDomains)
            putStringSet("darkmodeWhitelistedDomains", current.darkmodeWhitelistedDomains)
            putBoolean("isDownloadMultiThread", current.isDownloadMultiThread)
            putString("downloadDirectory", current.downloadDirectory)
            putBoolean("isDeletePhysicalFile", current.isDeletePhysicalFile)
            putInt("defaultConnectionCount", current.defaultConnectionCount)
            putString("appLanguage", current.appLanguage)
            putString("appThemeMode", current.appThemeMode)
            putBoolean("firstRunCompleted", current.firstRunCompleted)
            apply()
        }
    }

    override fun setDesktopSite(domain: String, enabled: Boolean) {
        val currentDomains = _settings.value.desktopDomains.toMutableSet()
        if (enabled) currentDomains.add(domain) else currentDomains.remove(domain)
        _settings.value = _settings.value.copy(desktopDomains = currentDomains)
        saveSettings()
    }

    override fun setDarkMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(isDarkModeSimulated = enabled)
        saveSettings()
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isJavaScriptEnabled = enabled)
        saveSettings()
    }

    override fun setUserScriptEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isUserScriptEnabled = enabled)
        saveSettings()
    }

    override fun setSearchEngineUrl(url: String) {
        _settings.value = _settings.value.copy(searchEngineUrl = url)
        saveSettings()
    }

    override fun setAdBlockEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isAdBlockEnabled = enabled)
        saveSettings()
    }

    override fun setAddonEnabled(addonId: String, enabled: Boolean) {
        val current = _settings.value.enabledAddons.toMutableSet()
        if (enabled) {
            current.add(addonId)
        } else {
            current.remove(addonId)
        }
        _settings.value = _settings.value.copy(enabledAddons = current)
        saveSettings()
    }

    override fun saveAddonMetadata(addonId: String, name: String, version: String, author: String, description: String) {
        val currentMetadata = _settings.value.addonsMetadata.toMutableMap()
        currentMetadata[addonId] = mapOf(
            "name" to name,
            "version" to version,
            "author" to author,
            "description" to description
        )
        _settings.value = _settings.value.copy(addonsMetadata = currentMetadata)
        saveSettings()
    }

    override fun addCustomAdBlockFilter(filter: String) {
        val trimmed = filter.trim()
        if (trimmed.isNotBlank() && !trimmed.contains(" ") && !_settings.value.customAdBlockFilters.contains(trimmed)) {
            _settings.value = _settings.value.copy(
                customAdBlockFilters = _settings.value.customAdBlockFilters + trimmed
            )
            saveSettings()
        }
    }

    override fun removeCustomAdBlockFilter(filter: String) {
        _settings.value = _settings.value.copy(
            customAdBlockFilters = _settings.value.customAdBlockFilters - filter
        )
        saveSettings()
    }

    fun addBlockedCssSelector(domain: String, selector: String) {
        val cleanDomain = domain.removePrefix("www.").trim()
        val current = _settings.value.blockedCssSelectors.toMutableMap()
        val existing = current[cleanDomain]?.toMutableList() ?: mutableListOf()
        if (!existing.contains(selector)) {
            existing.add(selector)
            current[cleanDomain] = existing
            _settings.value = _settings.value.copy(blockedCssSelectors = current)
            saveSettings()
        }
    }

    fun removeBlockedCssSelector(domain: String, selector: String) {
        val cleanDomain = domain.removePrefix("www.").trim()
        val current = _settings.value.blockedCssSelectors.toMutableMap()
        val existing = current[cleanDomain]?.toMutableList() ?: return
        existing.remove(selector)
        if (existing.isEmpty()) current.remove(cleanDomain) else current[cleanDomain] = existing
        _settings.value = _settings.value.copy(blockedCssSelectors = current)
        saveSettings()
    }

    fun getBlockedSelectorsForDomain(domain: String): List<String> {
        val cleanDomain = domain.removePrefix("www.").trim()
        return _settings.value.blockedCssSelectors[cleanDomain] ?: emptyList()
    }

    override fun addSpeedDial(name: String, url: String) {
        val trimmedName = name.trim()
        var trimmedUrl = url.trim()
        if (trimmedName.isNotBlank() && trimmedUrl.isNotBlank()) {
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                trimmedUrl = "https://$trimmedUrl"
            }
            val firstLetter = if (trimmedName.isNotEmpty()) trimmedName.take(1).uppercase() else "Y"
            val randomColors = listOf("EA4335", "34A853", "4285F4", "FBBC05", "FF0000", "24292E", "FF4500", "72777D")
            val color = randomColors.random()
            
            if (!_settings.value.speedDials.any { it.url == trimmedUrl }) {
                _settings.value = _settings.value.copy(
                    speedDials = _settings.value.speedDials + SpeedDialConfig(trimmedName, trimmedUrl, firstLetter, color)
                )
                saveSettings()
            }
        }
    }

    override fun removeSpeedDial(url: String) {
        _settings.value = _settings.value.copy(
            speedDials = _settings.value.speedDials.filter { it.url != url }
        )
        saveSettings()
    }

    override fun clearBrowserData(context: android.content.Context, cookies: Boolean, cache: Boolean) {
        if (cookies) {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
            }
        }
        if (cache) {
            try {
                context.cacheDir.deleteRecursively()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun setZoomEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isZoomEnabled = enabled)
        saveSettings()
    }

    override fun setBackgroundPlayEnabledNormal(enabled: Boolean) {
        _settings.value = _settings.value.copy(isBackgroundPlayEnabledNormal = enabled)
        saveSettings()
    }

    override fun setBackgroundPlayEnabledPrivate(enabled: Boolean) {
        _settings.value = _settings.value.copy(isBackgroundPlayEnabledPrivate = enabled)
        saveSettings()
    }

    // ====== Web Lock ======

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun addLockedDomain(domain: String) {
        var cleaned = domain.trim().lowercase()
        // Parse as URL to strip protocol/path/query if present
        try {
            val uri = android.net.Uri.parse(cleaned)
            if (uri.scheme != null || uri.host != null) {
                val host = uri.host ?: cleaned
                cleaned = host
            }
        } catch (_: Exception) { }
        cleaned = cleaned.removePrefix("www.").trimEnd('/')
        if (cleaned.isBlank()) return
        val current = _settings.value.lockedDomains.toMutableSet()
        current.add(cleaned)
        _settings.value = _settings.value.copy(lockedDomains = current)
        saveSettings()
    }

    override fun removeLockedDomain(domain: String) {
        var cleaned = domain.trim().lowercase()
        try {
            val uri = android.net.Uri.parse(cleaned)
            if (uri.scheme != null || uri.host != null) {
                val host = uri.host ?: cleaned
                cleaned = host
            }
        } catch (_: Exception) { }
        cleaned = cleaned.removePrefix("www.").trimEnd('/')
        val current = _settings.value.lockedDomains.toMutableSet()
        current.remove(cleaned)
        _settings.value = _settings.value.copy(lockedDomains = current)
        saveSettings()
    }

    override fun setWebLockPin(pin: String) {
        val hash = sha256(pin)
        _settings.value = _settings.value.copy(webLockPinHash = hash)
        saveSettings()
    }

    override fun verifyWebLockPin(pin: String): Boolean {
        val stored = _settings.value.webLockPinHash
        if (stored.isBlank()) return false
        return sha256(pin) == stored
    }

    override fun isWebLockPinSet(): Boolean {
        return _settings.value.webLockPinHash.isNotBlank()
    }

    override fun setWebLockAutoLockTimeout(timeoutMinutes: String) {
        _settings.value = _settings.value.copy(webLockAutoLockTimeout = timeoutMinutes)
        saveSettings()
    }

    override fun setWebLockMaxAttempts(attempts: Int) {
        _settings.value = _settings.value.copy(webLockMaxAttempts = attempts.coerceIn(1, 20))
        saveSettings()
    }

    override fun setWebLockLockDurationMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(webLockLockDurationMinutes = minutes.coerceIn(1, 60))
        saveSettings()
    }

    override fun setWebLockAttemptsEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(webLockAttemptsEnabled = enabled)
        saveSettings()
    }

    override fun setVideoSpeedupEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isVideoSpeedupEnabled = enabled)
        saveSettings()
    }

    override fun setVideoSpeedupRate(rate: Float) {
        _settings.value = _settings.value.copy(videoSpeedupRate = rate)
        saveSettings()
    }

    override fun setAutoPipEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isAutoPipEnabled = enabled)
        saveSettings()
    }

    override fun setVideoOrientationLocked(enabled: Boolean) {
        _settings.value = _settings.value.copy(isVideoOrientationLocked = enabled)
        saveSettings()
    }

    private fun getBaseDomain(domain: String): String {
        val clean = domain.removePrefix("www.").removePrefix("m.").trim().lowercase()
        val parts = clean.split(".")
        if (parts.size <= 2) return clean
        
        val last = parts.last()
        val secondLast = parts[parts.size - 2]
        
        val isDoubleTld = (last.length == 2 && secondLast.length <= 3) || 
                          (last.length == 3 && secondLast == "co") ||
                          (last.length == 2 && secondLast == "com")
                          
        return if (isDoubleTld && parts.size >= 3) {
            parts.subList(parts.size - 3, parts.size).joinToString(".")
        } else {
            parts.subList(parts.size - 2, parts.size).joinToString(".")
        }
    }

    override fun addAdblockWhitelistedDomain(domain: String) {
        val baseDomain = getBaseDomain(domain)
        val current = _settings.value.adblockWhitelistedDomains.toMutableSet()
        if (current.add(baseDomain)) {
            _settings.value = _settings.value.copy(adblockWhitelistedDomains = current)
            saveSettings()
        }
    }

    override fun removeAdblockWhitelistedDomain(domain: String) {
        val baseDomain = getBaseDomain(domain)
        val current = _settings.value.adblockWhitelistedDomains.toMutableSet()
        if (current.remove(baseDomain)) {
            _settings.value = _settings.value.copy(adblockWhitelistedDomains = current)
            saveSettings()
        }
    }

    override fun addDarkmodeWhitelistedDomain(domain: String) {
        val baseDomain = getBaseDomain(domain)
        val current = _settings.value.darkmodeWhitelistedDomains.toMutableSet()
        if (current.add(baseDomain)) {
            _settings.value = _settings.value.copy(darkmodeWhitelistedDomains = current)
            saveSettings()
        }
    }

    override fun removeDarkmodeWhitelistedDomain(domain: String) {
        val baseDomain = getBaseDomain(domain)
        val current = _settings.value.darkmodeWhitelistedDomains.toMutableSet()
        if (current.remove(baseDomain)) {
            _settings.value = _settings.value.copy(darkmodeWhitelistedDomains = current)
            saveSettings()
        }
    }

    override fun setDownloadMultiThread(enabled: Boolean) {
        _settings.value = _settings.value.copy(isDownloadMultiThread = enabled)
        saveSettings()
    }

    override fun setDownloadDirectory(dir: String) {
        _settings.value = _settings.value.copy(downloadDirectory = dir)
        saveSettings()
    }

    override fun setDeletePhysicalFile(enabled: Boolean) {
        _settings.value = _settings.value.copy(isDeletePhysicalFile = enabled)
        saveSettings()
    }

    override fun setFirstRunCompleted() {
        _settings.value = _settings.value.copy(firstRunCompleted = true)
        saveSettings()
    }

    override fun setDefaultConnectionCount(count: Int) {
        _settings.value = _settings.value.copy(defaultConnectionCount = count.coerceIn(1, 16))
        saveSettings()
    }

    override fun setAppLanguage(lang: String) {
        _settings.value = _settings.value.copy(appLanguage = lang)
        saveSettings()
    }

    override fun setAppThemeMode(theme: String) {
        val systemDark = if (theme == "system") {
            isSystemDarkMode()
        } else {
            theme == "dark"
        }
        _settings.value = _settings.value.copy(
            appThemeMode = theme,
            isDarkModeSimulated = systemDark
        )
        saveSettings()
    }

    fun applySettings(settings: BrowserSettings) {
        _settings.value = settings
        saveSettings()
    }
}
