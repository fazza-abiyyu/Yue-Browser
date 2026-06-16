package com.yue.browser.presentation

import com.yue.browser.domain.model.BookmarkItem
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.PasswordEntry
import com.yue.browser.domain.model.SpeedDialConfig
import com.yue.browser.data.repository.BookmarkRepositoryImpl
import com.yue.browser.data.repository.PasswordRepositoryImpl
import com.yue.browser.data.repository.SettingsRepositoryImpl
import org.json.JSONArray
import org.json.JSONObject

object ExportImportHelper {

    private const val FORMAT_VERSION = 1

    fun exportToJson(
        settings: BrowserSettings,
        bookmarks: List<BookmarkItem>,
        passwords: List<PasswordEntry> = emptyList()
    ): String {
        val root = JSONObject()

        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        // General settings
        val settingsObj = JSONObject()
        settingsObj.put("desktopDomains", JSONArray(settings.desktopDomains.toList()))
        settingsObj.put("isDarkModeSimulated", settings.isDarkModeSimulated)
        settingsObj.put("isJavaScriptEnabled", settings.isJavaScriptEnabled)
        settingsObj.put("searchEngineUrl", settings.searchEngineUrl)
        settingsObj.put("isAdBlockEnabled", settings.isAdBlockEnabled)
        settingsObj.put("isZoomEnabled", settings.isZoomEnabled)
        settingsObj.put("isBackgroundPlayEnabledNormal", settings.isBackgroundPlayEnabledNormal)
        settingsObj.put("isBackgroundPlayEnabledPrivate", settings.isBackgroundPlayEnabledPrivate)
        settingsObj.put("isUserScriptEnabled", settings.isUserScriptEnabled)
        root.put("settings", settingsObj)

        // WebLock
        val weblockObj = JSONObject()
        weblockObj.put("lockedDomains", JSONArray(settings.lockedDomains.toList()))
        weblockObj.put("pinHash", settings.webLockPinHash)
        weblockObj.put("autoLockTimeout", settings.webLockAutoLockTimeout)
        root.put("weblock", weblockObj)

        // Adblock
        val adblockObj = JSONObject()
        adblockObj.put("customFilters", JSONArray(settings.customAdBlockFilters))

        val selectorsObj = JSONObject()
        settings.blockedCssSelectors.forEach { (domain, selectors) ->
            selectorsObj.put(domain, JSONArray(selectors))
        }
        adblockObj.put("blockedCssSelectors", selectorsObj)
        root.put("adblock", adblockObj)

        // Speed Dials
        val dialsArray = JSONArray()
        settings.speedDials.forEach { dial ->
            val dialObj = JSONObject()
            dialObj.put("name", dial.name)
            dialObj.put("url", dial.url)
            dialObj.put("iconLetter", dial.iconLetter)
            dialObj.put("iconBgColorHex", dial.iconBgColorHex)
            dialsArray.put(dialObj)
        }
        root.put("speedDials", dialsArray)

        // Bookmarks
        val bookmarksArray = JSONArray()
        bookmarks.forEach { bm ->
            val bmObj = JSONObject()
            bmObj.put("url", bm.url)
            bmObj.put("title", bm.title)
            bookmarksArray.put(bmObj)
        }
        root.put("bookmarks", bookmarksArray)

        // Passwords
        val passwordsArray = JSONArray()
        passwords.forEach { pw ->
            val pwObj = JSONObject()
            pwObj.put("id", pw.id)
            pwObj.put("name", pw.name)
            pwObj.put("url", pw.url)
            pwObj.put("username", pw.username)
            pwObj.put("password", pw.password)
            pwObj.put("note", pw.note)
            pwObj.put("createdAt", pw.createdAt)
            passwordsArray.put(pwObj)
        }
        root.put("passwords", passwordsArray)

        return root.toString(2)
    }

    data class ImportResult(
        val success: Boolean,
        val message: String
    )

    fun importFromJson(
        json: String,
        settingsRepo: SettingsRepositoryImpl,
        bookmarkRepo: BookmarkRepositoryImpl,
        passwordRepo: PasswordRepositoryImpl? = null
    ): ImportResult {
        return try {
            val root = JSONObject(json)

            val version = root.optInt("version", 0)
            if (version != FORMAT_VERSION) {
                return ImportResult(false, "Unsupported format version: $version")
            }

            // Build settings from JSON
            val settingsObj = root.optJSONObject("settings")
            val currentSettings = settingsRepo.settingsFlow.value

            // Single-value settings → override
            val newSettings = currentSettings.copy(
                isDarkModeSimulated = settingsObj?.optBoolean("isDarkModeSimulated", currentSettings.isDarkModeSimulated)
                    ?: currentSettings.isDarkModeSimulated,
                isJavaScriptEnabled = settingsObj?.optBoolean("isJavaScriptEnabled", currentSettings.isJavaScriptEnabled)
                    ?: currentSettings.isJavaScriptEnabled,
                searchEngineUrl = settingsObj?.optString("searchEngineUrl", currentSettings.searchEngineUrl)
                    ?: currentSettings.searchEngineUrl,
                isAdBlockEnabled = settingsObj?.optBoolean("isAdBlockEnabled", currentSettings.isAdBlockEnabled)
                    ?: currentSettings.isAdBlockEnabled,
                isZoomEnabled = settingsObj?.optBoolean("isZoomEnabled", currentSettings.isZoomEnabled)
                    ?: currentSettings.isZoomEnabled,
                isBackgroundPlayEnabledNormal = settingsObj?.optBoolean("isBackgroundPlayEnabledNormal", currentSettings.isBackgroundPlayEnabledNormal)
                    ?: currentSettings.isBackgroundPlayEnabledNormal,
                isBackgroundPlayEnabledPrivate = settingsObj?.optBoolean("isBackgroundPlayEnabledPrivate", currentSettings.isBackgroundPlayEnabledPrivate)
                    ?: currentSettings.isBackgroundPlayEnabledPrivate,
                isUserScriptEnabled = settingsObj?.optBoolean("isUserScriptEnabled", currentSettings.isUserScriptEnabled)
                    ?: currentSettings.isUserScriptEnabled
            )

            // Collection fields → merge (union)
            val newDesktopDomains = if (settingsObj?.has("desktopDomains") == true) {
                val imported = jsonArrayToStringSet(settingsObj.optJSONArray("desktopDomains"))
                (currentSettings.desktopDomains + imported)
            } else currentSettings.desktopDomains

            val newLockedDomains: Set<String>
            val newPinHash: String
            val newAutoLockTimeout: String
            val weblockObj = root.optJSONObject("weblock")
            if (weblockObj != null) {
                val imported = jsonArrayToStringSet(weblockObj.optJSONArray("lockedDomains"))
                newLockedDomains = currentSettings.lockedDomains + imported
                newPinHash = weblockObj.optString("pinHash", "").ifBlank { currentSettings.webLockPinHash }
                newAutoLockTimeout = weblockObj.optString("autoLockTimeout", "").ifBlank { currentSettings.webLockAutoLockTimeout }
            } else {
                newLockedDomains = currentSettings.lockedDomains
                newPinHash = currentSettings.webLockPinHash
                newAutoLockTimeout = currentSettings.webLockAutoLockTimeout
            }

            // Adblock filters → merge (append unique)
            val adblockObj = root.optJSONObject("adblock")
            val newCustomFilters: List<String>
            val newBlockedSelectors: Map<String, List<String>>
            if (adblockObj != null) {
                val existingFilters = currentSettings.customAdBlockFilters.toSet()
                val importedFilters = mutableListOf<String>()
                val filtersArr = adblockObj.optJSONArray("customFilters")
                if (filtersArr != null) {
                    for (i in 0 until filtersArr.length()) importedFilters.add(filtersArr.getString(i))
                }
                newCustomFilters = (existingFilters + importedFilters).toList()

                val mergedSelectors = currentSettings.blockedCssSelectors.toMutableMap()
                val selectorsObj = adblockObj.optJSONObject("blockedCssSelectors")
                if (selectorsObj != null) {
                    val keys = selectorsObj.keys()
                    while (keys.hasNext()) {
                        val domain = keys.next()
                        val arr = selectorsObj.getJSONArray(domain)
                        val existing = mergedSelectors[domain]?.toMutableSet() ?: mutableSetOf()
                        for (i in 0 until arr.length()) existing.add(arr.getString(i))
                        mergedSelectors[domain] = existing.toList()
                    }
                }
                newBlockedSelectors = mergedSelectors
            } else {
                newCustomFilters = currentSettings.customAdBlockFilters
                newBlockedSelectors = currentSettings.blockedCssSelectors
            }

            // Speed Dials → merge (skip if URL already exists)
            val dialsArray = root.optJSONArray("speedDials")
            val newSpeedDials = if (dialsArray != null && dialsArray.length() > 0) {
                val existingUrls = currentSettings.speedDials.map { it.url }.toSet()
                val mergedDials = currentSettings.speedDials.toMutableList()
                for (i in 0 until dialsArray.length()) {
                    val obj = dialsArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url !in existingUrls) {
                        mergedDials.add(
                            SpeedDialConfig(
                                name = obj.optString("name", ""),
                                url = url,
                                iconLetter = obj.optString("iconLetter", ""),
                                iconBgColorHex = obj.optString("iconBgColorHex", "4285F4")
                            )
                        )
                    }
                }
                mergedDials
            } else currentSettings.speedDials

            val mergedSettings = newSettings.copy(
                desktopDomains = newDesktopDomains,
                lockedDomains = newLockedDomains,
                webLockPinHash = newPinHash,
                webLockAutoLockTimeout = newAutoLockTimeout,
                customAdBlockFilters = newCustomFilters,
                blockedCssSelectors = newBlockedSelectors,
                speedDials = newSpeedDials
            )

            settingsRepo.applySettings(mergedSettings)

            // Bookmarks → merge (skip if URL already exists)
            val bookmarksArray = root.optJSONArray("bookmarks")
            if (bookmarksArray != null && bookmarksArray.length() > 0) {
                for (i in 0 until bookmarksArray.length()) {
                    val obj = bookmarksArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (!bookmarkRepo.isBookmarked(url)) {
                        bookmarkRepo.addBookmark(url, obj.optString("title", ""))
                    }
                }
            }

            // Passwords → import
            if (passwordRepo != null) {
                val passwordsArray = root.optJSONArray("passwords")
                if (passwordsArray != null && passwordsArray.length() > 0) {
                    for (i in 0 until passwordsArray.length()) {
                        val obj = passwordsArray.getJSONObject(i)
                        val entry = PasswordEntry(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", ""),
                            url = obj.optString("url", ""),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            note = obj.optString("note", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                        passwordRepo.addPassword(entry)
                    }
                }
            }

            ImportResult(true, "Import successful")
        } catch (e: Exception) {
            ImportResult(false, "Import failed: ${e.message}")
        }
    }

    private fun jsonArrayToStringSet(arr: JSONArray?): Set<String> {
        val set = mutableSetOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) set.add(arr.getString(i))
        }
        return set
    }
}
