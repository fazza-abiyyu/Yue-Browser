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
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ExportImportHelper {

    private const val FORMAT_VERSION = 1
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_SIZE = 128

    fun exportToJson(
        settings: BrowserSettings,
        bookmarks: List<BookmarkItem>,
        passwords: List<PasswordEntry> = emptyList(),
        masterPassword: String? = null
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
        settingsObj.put("isDownloadMultiThread", settings.isDownloadMultiThread)
        settingsObj.put("downloadDirectory", settings.downloadDirectory)
        settingsObj.put("isDeletePhysicalFile", settings.isDeletePhysicalFile)
        settingsObj.put("defaultConnectionCount", settings.defaultConnectionCount)
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
        if (passwords.isNotEmpty()) {
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
            val plaintext = passwordsArray.toString()
            if (!masterPassword.isNullOrBlank()) {
                val encrypted = encryptData(plaintext, masterPassword)
                root.put("passwords", JSONObject().apply {
                    put("encrypted", true)
                    put("data", encrypted)
                })
            } else {
                root.put("passwords", JSONObject().apply {
                    put("encrypted", false)
                    put("data", plaintext)
                })
            }
        } else {
            root.put("passwords", JSONObject().apply {
                put("encrypted", false)
                put("data", "[]")
            })
        }

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
        passwordRepo: PasswordRepositoryImpl? = null,
        masterPassword: String? = null,
        skipPasswords: Boolean = false
    ): ImportResult {
        return try {
            val root = JSONObject(json)

            val version = root.optInt("version", 0)
            if (version != FORMAT_VERSION) {
                return ImportResult(false, "Unsupported format version: $version")
            }

            // Check passwords early — verify before applying anything
            var decryptedPasswords: String? = null
            if (passwordRepo != null && !skipPasswords) {
                val passwordsObj = root.optJSONObject("passwords")
                if (passwordsObj != null && passwordsObj.optBoolean("encrypted", false)) {
                    val dataStr = passwordsObj.optString("data", "[]")
                    if (masterPassword.isNullOrBlank()) {
                        return ImportResult(false, "NEED_PASSWORD")
                    }
                    decryptedPasswords = try {
                        decryptData(dataStr, masterPassword)
                    } catch (e: Exception) {
                        return ImportResult(false, "WRONG_PASSWORD")
                    }
                }
            }

            // Build settings from JSON
            val settingsObj = root.optJSONObject("settings")
            val currentSettings = settingsRepo.settingsFlow.value

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
                    ?: currentSettings.isUserScriptEnabled,
                isDownloadMultiThread = settingsObj?.optBoolean("isDownloadMultiThread", currentSettings.isDownloadMultiThread)
                    ?: currentSettings.isDownloadMultiThread,
                downloadDirectory = settingsObj?.optString("downloadDirectory", currentSettings.downloadDirectory)
                    ?: currentSettings.downloadDirectory,
                isDeletePhysicalFile = settingsObj?.optBoolean("isDeletePhysicalFile", currentSettings.isDeletePhysicalFile)
                    ?: currentSettings.isDeletePhysicalFile,
                defaultConnectionCount = settingsObj?.optInt("defaultConnectionCount", currentSettings.defaultConnectionCount)
                    ?: currentSettings.defaultConnectionCount
            )

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

            // Bookmarks
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

            // Passwords
            if (passwordRepo != null && !skipPasswords) {
                val passwordsObj = root.optJSONObject("passwords")
                if (passwordsObj != null) {
                    val dataStr = decryptedPasswords ?: passwordsObj.optString("data", "[]")
                    val passwordsArray = JSONArray(dataStr)
                    for (i in 0 until passwordsArray.length()) {
                        val obj = passwordsArray.getJSONObject(i)
                        passwordRepo.addPassword(PasswordEntry(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", ""),
                            url = obj.optString("url", ""),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            note = obj.optString("note", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        ))
                    }
                }
            }

            ImportResult(true, "Import successful")
        } catch (e: Exception) {
            ImportResult(false, "Import failed: ${e.message}")
        }
    }

    private fun encryptData(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = salt + iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decryptData(encoded: String, password: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val salt = combined.sliceArray(0 until SALT_SIZE)
        val iv = combined.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
        val ciphertext = combined.sliceArray(SALT_SIZE + IV_SIZE until combined.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun exportPasswordsCsv(passwords: List<PasswordEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("name,url,username,password,note")
        passwords.forEach { pw ->
            sb.appendLine(
                listOf(
                    escapeCsv(pw.name),
                    escapeCsv(pw.url),
                    escapeCsv(pw.username),
                    escapeCsv(pw.password),
                    escapeCsv(pw.note)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun importPasswordsCsv(csv: String, passwordRepo: PasswordRepositoryImpl): ImportResult {
        return try {
            val lines = csv.lines().filter { it.isNotBlank() }
            if (lines.size < 2) return ImportResult(false, "CSV must have header + at least 1 entry")
            
            val headerLine = lines[0]
            val headers = parseCsvLine(headerLine)?.map { it.lowercase().trim() }
            
            val hasHeaders = headers != null && headers.contains("url") && headers.contains("password")
            
            val urlIdx = if (hasHeaders) headers!!.indexOf("url") else 1
            val usernameIdx = if (hasHeaders) headers!!.indexOfFirst { it == "username" || it == "login" || it == "user" } else 2
            val passwordIdx = if (hasHeaders) headers!!.indexOf("password") else 3
            val nameIdx = if (hasHeaders) headers!!.indexOfFirst { it == "name" || it == "title" } else 0
            val noteIdx = if (hasHeaders) headers!!.indexOfFirst { it == "note" || it == "notes" || it == "extra" } else 4
            
            var count = 0
            for (i in 1 until lines.size) {
                val parts = parseCsvLine(lines[i]) ?: continue
                val url = parts.getOrNull(urlIdx)?.trim() ?: ""
                val password = parts.getOrNull(passwordIdx) ?: ""
                val username = if (usernameIdx != -1) parts.getOrNull(usernameIdx) ?: "" else ""
                val name = if (nameIdx != -1) parts.getOrNull(nameIdx) ?: "" else ""
                val note = if (noteIdx != -1) parts.getOrNull(noteIdx) ?: "" else ""
                
                if (password.isNotBlank() && url.isNotBlank()) {
                    passwordRepo.addPassword(
                        PasswordEntry(
                            name = name.ifBlank { url },
                            url = url,
                            username = username,
                            password = password,
                            note = note
                        )
                    )
                    count++
                }
            }
            ImportResult(true, "Imported $count passwords")
        } catch (e: Exception) {
            ImportResult(false, "CSV import failed: ${e.message}")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun parseCsvLine(line: String): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    fun importPasswords(json: String, passwordRepo: PasswordRepositoryImpl): ImportResult {
        return try {
            val root = JSONObject(json)
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
