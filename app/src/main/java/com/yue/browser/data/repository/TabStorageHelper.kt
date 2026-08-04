package com.yue.browser.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.webkit.WebView
import com.yue.browser.data.engine.SystemWebViewSession
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RestoredTab(
    val id: String,
    val title: String,
    val url: String,
    val lastAccessed: Long,
    val groupId: String?,
    val parentTabId: String?,
    val isPrivate: Boolean,
    val hasEverNavigatedAway: Boolean
)

data class RestoredState(
    val activeTabIndex: Int,
    val tabs: List<RestoredTab>,
    val groups: Map<String, TabGroup>
)

data class TabStateData(
    val id: String,
    val title: String,
    val url: String,
    val lastAccessed: Long,
    val groupId: String?,
    val parentTabId: String?,
    val isPrivate: Boolean,
    val bundleBytes: ByteArray?,
    val hasEverNavigatedAway: Boolean = false
)

object TabStorageHelper {
    private const val PREFS_NAME = "yue_incognito_prefs"
    private const val VISITED_DOMAINS_KEY = "visited_domains"

    private val stateWriteLock = Any()

    fun getWebViewStateFile(context: Context, tabId: String): File {
        val dir = File(context.filesDir, "webview_states")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${tabId}_state.dat")
    }

    fun getWebViewStateBundle(context: Context, tabId: String): android.os.Bundle? {
        val file = getWebViewStateFile(context, tabId)
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val parcel = android.os.Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val bundle = android.os.Bundle()
                bundle.readFromParcel(parcel)
                bundle
            } finally {
                parcel.recycle()
            }
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to read webview state bundle for tab $tabId", e)
            null
        }
    }

    fun writeTabsState(
        context: Context,
        activeTabId: String?,
        tabStates: List<TabStateData>,
        groupsData: Map<String, TabGroup>
    ) {
        Thread {
            synchronized(stateWriteLock) {
                try {
                    val root = JSONObject()
                    val tabsArray = JSONArray()
                    var savedActiveIndex = 0
                    var savedIndexCounter = 0

                    tabStates.forEach { tabData ->
                        val obj = JSONObject()
                        obj.put("id", tabData.id)
                        obj.put("title", tabData.title)
                        obj.put("url", tabData.url)
                        obj.put("isPrivate", tabData.isPrivate)
                        obj.put("lastAccessed", tabData.lastAccessed)
                        if (tabData.groupId != null) {
                            obj.put("groupId", tabData.groupId)
                        }
                        if (tabData.parentTabId != null) {
                            obj.put("parentTabId", tabData.parentTabId)
                        }
                        obj.put("hasEverNavigatedAway", tabData.hasEverNavigatedAway)
                        tabsArray.put(obj)

                        if (tabData.id == activeTabId) {
                            savedActiveIndex = savedIndexCounter
                        }
                        savedIndexCounter++

                        tabData.bundleBytes?.let { bytes ->
                            val file = getWebViewStateFile(context, tabData.id)
                            file.writeBytes(bytes)
                        }
                    }

                    root.put("activeTabIndex", savedActiveIndex)
                    root.put("tabs", tabsArray)

                    val groupsObj = JSONObject()
                    groupsData.forEach { (id, group) ->
                        val groupJson = JSONObject()
                        groupJson.put("id", group.id)
                        groupJson.put("name", group.name)
                        groupJson.put("colorIndex", group.colorIndex)
                        groupsObj.put(id, groupJson)
                    }
                    root.put("groups", groupsObj)

                    val file = File(context.filesDir, "tabs_state.json")
                    val tempFile = File(context.filesDir, "tabs_state.json.tmp")
                    tempFile.writeText(root.toString())
                    if (!tempFile.renameTo(file)) {
                        file.delete()
                        if (!tempFile.renameTo(file)) {
                            file.writeText(root.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TabStorageHelper", "Failed to save state on background thread", e)
                }
            }
        }.start()
    }

    fun readSavedTabsState(context: Context, isProcessRecreation: Boolean): RestoredState? {
        try {
            val file = File(context.filesDir, "tabs_state.json")
            if (!file.exists()) return null

            val text = file.readText()
            if (text.isBlank()) return null

            val root = JSONObject(text)
            val activeIndex = root.optInt("activeTabIndex", 0)
            val tabsArray = root.optJSONArray("tabs") ?: return null

            val restoredGroups = mutableMapOf<String, TabGroup>()
            val groupsObj = root.optJSONObject("groups")
            if (groupsObj != null) {
                val keys = groupsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val groupJson = groupsObj.getJSONObject(key)
                    val gId = groupJson.optString("id", key)
                    val gName = groupJson.optString("name", "Group")
                    val gColorIndex = groupJson.optInt("colorIndex", 0)
                    restoredGroups[key] = TabGroup(id = gId, name = gName, colorIndex = gColorIndex)
                }
            }

            val restoredTabs = mutableListOf<RestoredTab>()
            for (i in 0 until tabsArray.length()) {
                val obj = tabsArray.getJSONObject(i)
                val isPrivate = obj.optBoolean("isPrivate", false)
                if (isPrivate && !isProcessRecreation) {
                    val tabId = obj.optString("id", "")
                    if (tabId.isNotEmpty()) {
                        try {
                            getWebViewStateFile(context, tabId).delete()
                            getThumbnailFile(context, tabId).delete()
                        } catch (_: Exception) {}
                    }
                    continue
                }

                restoredTabs.add(
                    RestoredTab(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "New Tab"),
                        url = obj.optString("url", "yue://newtab"),
                        lastAccessed = obj.optLong("lastAccessed", System.currentTimeMillis()),
                        groupId = if (obj.has("groupId")) obj.optString("groupId") else null,
                        parentTabId = if (obj.has("parentTabId")) obj.optString("parentTabId") else null,
                        isPrivate = isPrivate,
                        hasEverNavigatedAway = obj.optBoolean("hasEverNavigatedAway", false)
                    )
                )
            }

            return RestoredState(activeIndex, restoredTabs, restoredGroups)
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to read saved tabs state", e)
            return null
        }
    }

    fun cleanupPrivateTabState(context: Context) {
        try {
            val file = File(context.filesDir, "tabs_state.json")
            if (!file.exists()) return
            val text = file.readText()
            if (text.isBlank()) return
            val root = JSONObject(text)
            val tabsArray = root.optJSONArray("tabs") ?: return
            val filteredTabs = JSONArray()
            val activeIndex = root.optInt("activeTabIndex", 0)
            var newActiveIndex = activeIndex
            var offset = 0
            for (i in 0 until tabsArray.length()) {
                val obj = tabsArray.getJSONObject(i)
                val isPrivate = obj.optBoolean("isPrivate", false)
                if (!isPrivate) {
                    val newObj = JSONObject()
                    newObj.put("id", obj.optString("id", ""))
                    newObj.put("title", obj.optString("title", "New Tab"))
                    newObj.put("url", obj.optString("url", "yue://newtab"))
                    newObj.put("isPrivate", false)
                    if (obj.has("lastAccessed")) newObj.put("lastAccessed", obj.optLong("lastAccessed", System.currentTimeMillis()))
                    if (obj.has("groupId")) newObj.put("groupId", obj.optString("groupId", ""))
                    filteredTabs.put(newObj)
                    if (i < activeIndex) offset++
                } else if (i < activeIndex) {
                    newActiveIndex--
                }
            }
            newActiveIndex = newActiveIndex.coerceIn(0, (filteredTabs.length() - 1).coerceAtLeast(0))
            root.put("activeTabIndex", newActiveIndex)
            root.put("tabs", filteredTabs)
            
            val tempFile = File(context.filesDir, "tabs_state.json.tmp")
            tempFile.writeText(root.toString())
            if (!tempFile.renameTo(file)) {
                file.delete()
                if (!tempFile.renameTo(file)) {
                    file.writeText(root.toString())
                }
            }
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Error cleaning private tab state", e)
        }
    }

    fun getThumbnailFile(context: Context, tabId: String): File {
        val dir = File(context.cacheDir, "tab_previews")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${tabId}_thumbnail.jpg")
    }

    fun getFaviconFile(context: Context, tabId: String): File {
        val dir = File(context.cacheDir, "tab_previews")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${tabId}_favicon.png")
    }

    fun saveBitmapToFile(file: File, bitmap: Bitmap, isPng: Boolean) {
        try {
            java.io.FileOutputStream(file).use { out ->
                if (isPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
            }
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to save bitmap to file: ${file.absolutePath}", e)
        }
    }

    fun loadBitmapFromFile(file: File): Bitmap? {
        return try {
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to load bitmap from file: ${file.absolutePath}", e)
            null
        }
    }

    fun deleteTabFiles(context: Context, tabId: String) {
        try {
            getThumbnailFile(context, tabId).delete()
            getFaviconFile(context, tabId).delete()
            getWebViewStateFile(context, tabId).delete()
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to delete files for tab $tabId", e)
        }
    }

    fun migratePreviewsToCacheDir(context: Context) {
        val oldDir = File(context.filesDir, "tab_previews")
        if (!oldDir.exists()) return

        val newDir = File(context.cacheDir, "tab_previews")
        if (!newDir.exists()) {
            newDir.mkdirs()
        }

        val files = oldDir.listFiles() ?: return
        for (file in files) {
            try {
                if (file.name.endsWith("_thumbnail.png")) {
                    val tabId = file.name.substringBefore("_thumbnail.png")
                    val newFile = File(newDir, "${tabId}_thumbnail.jpg")
                    if (!newFile.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            java.io.FileOutputStream(newFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                            }
                            bitmap.recycle()
                        }
                    }
                } else if (file.name.endsWith("_favicon.png")) {
                    val newFile = File(newDir, file.name)
                    if (!newFile.exists()) {
                        file.renameTo(newFile)
                    }
                }
            } catch (e: Exception) {
                Log.e("TabStorageHelper", "Failed to migrate file ${file.name}", e)
            }
        }
        try {
            oldDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to delete old tab_previews directory", e)
        }
    }

    fun cleanOrphanTabFiles(context: Context, activeIds: Set<String>) {
        try {
            val dir = File(context.cacheDir, "tab_previews")
            if (!dir.exists()) return
            val files = dir.listFiles() ?: return
            for (file in files) {
                val tabId = file.name.substringBefore("_thumbnail.jpg").substringBefore("_favicon.png")
                if (tabId.isNotBlank() && tabId !in activeIds) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to clean orphan tab files", e)
        }
    }

    fun clearPrivateData(
        context: Context,
        tabs: List<BrowserTab>,
        visitedIncognitoDomains: MutableSet<String>
    ) {
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    try {
                        androidx.webkit.ProfileStore.getInstance().deleteProfile("incognito_profile")
                        Log.d("TabStorageHelper", "Successfully deleted incognito profile")
                    } catch (e: Exception) {
                        Log.e("TabStorageHelper", "Error deleting incognito profile on UI thread", e)
                    }
                }
            } else {
                val cookieManager = android.webkit.CookieManager.getInstance()
                
                // Ambil domain dari RAM + disk
                val diskDomains = loadVisitedIncognitoDomains(context)
                val allPrivateDomains = visitedIncognitoDomains + diskDomains

                // Ambil semua domain yang saat ini terbuka di tab normal
                val openNormalDomains = tabs.filter { !it.isPrivate }.mapNotNull { tab ->
                    try {
                        android.net.Uri.parse(tab.url).host?.removePrefix("www.")?.removePrefix("m.")?.lowercase()
                    } catch (_: Exception) {
                        null
                    }
                }.toSet()

                // Bersihkan domain yang tidak terbuka di tab normal
                val domainsToClear = allPrivateDomains.filter { it !in openNormalDomains }
                for (domain in domainsToClear) {
                    val httpUrl = "http://$domain/"
                    val httpsUrl = "https://$domain/"
                    listOf(httpUrl, httpsUrl).forEach { url ->
                        val cookieString = cookieManager.getCookie(url)
                        if (cookieString != null) {
                            val cookies = cookieString.split(";")
                            for (cookie in cookies) {
                                val parts = cookie.split("=")
                                if (parts.isNotEmpty()) {
                                    val name = parts[0].trim()
                                    cookieManager.setCookie(url, "$name=; Domain=$domain; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    cookieManager.setCookie(url, "$name=; Domain=.$domain; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                }
                            }
                        }
                    }
                }

                // Flush remaining cookies
                cookieManager.flush()
            }
            
            // Bersihkan tracker
            visitedIncognitoDomains.clear()
            saveVisitedIncognitoDomains(context, emptySet())
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Error clearing private data", e)
        }
    }

    fun saveVisitedIncognitoDomains(context: Context, domains: Set<String>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(VISITED_DOMAINS_KEY, domains).apply()
        } catch (_: Exception) {}
    }

    fun loadVisitedIncognitoDomains(context: Context): Set<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getStringSet(VISITED_DOMAINS_KEY, emptySet()) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun saveState(
        context: Context,
        tabs: List<BrowserTab>,
        activeTabIndex: Int,
        groups: Map<String, TabGroup>
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val activeTabId = tabs.getOrNull(activeTabIndex)?.id
                val tabStates = tabs.map { tab ->
                    val session = tab.session as? SystemWebViewSession
                    val view = session?.view as? WebView
                    val bundleBytes = if (view != null) {
                        val bundle = Bundle()
                        val list = view.saveState(bundle)
                        if (list != null) {
                            val parcel = Parcel.obtain()
                            try {
                                bundle.writeToParcel(parcel, 0)
                                parcel.marshall()
                            } finally {
                                parcel.recycle()
                            }
                        } else null
                    } else null

                    TabStateData(
                        id = tab.id,
                        title = tab.title,
                        url = tab.url,
                        lastAccessed = tab.lastAccessed,
                        groupId = tab.groupId,
                        parentTabId = tab.parentTabId,
                        isPrivate = tab.isPrivate,
                        bundleBytes = bundleBytes,
                        hasEverNavigatedAway = tab.hasEverNavigatedAway
                    )
                }

                val groupsData = groups.toMap()
                writeTabsState(context, activeTabId, tabStates, groupsData)
            } catch (e: Exception) {
                Log.e("TabStorageHelper", "Failed to capture state on main thread", e)
            }
        }
    }

    fun restoreWebViewState(
        context: Context,
        tab: BrowserTab,
        updateTab: (String, (BrowserTab) -> BrowserTab) -> Unit
    ): Boolean {
        val session = tab.session as? SystemWebViewSession ?: return false
        val view = session.view as? WebView ?: return false
        val bundle = getWebViewStateBundle(context, tab.id) ?: return false
        try {
            val restoredList = view.restoreState(bundle)
            if (restoredList != null) {
                session.url = view.url ?: tab.url
                session.title = view.title ?: tab.title
                session.updateNavigationState(view)
                
                updateTab(tab.id) {
                    it.copy(
                        url = session.url,
                        title = session.title,
                        canGoBack = session.canGoBack,
                        canGoForward = session.canGoForward
                    )
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("TabStorageHelper", "Failed to restore webview state for tab ${tab.id}", e)
        }
        return false
    }
}
