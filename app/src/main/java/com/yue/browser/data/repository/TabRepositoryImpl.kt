package com.yue.browser.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.yue.browser.domain.engine.BrowserEngine
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup
import com.yue.browser.domain.repository.SettingsRepository
import com.yue.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TabRepositoryImpl(
    private val browserEngine: BrowserEngine,
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl.instance
) : TabRepository {
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    override val tabsFlow: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    override val activeTabIndexFlow: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _groups = MutableStateFlow<Map<String, TabGroup>>(emptyMap())
    override val groupsFlow: StateFlow<Map<String, TabGroup>> = _groups.asStateFlow()

    private var appContext: Context? = null

    override fun newIncognitoTab(context: Context) {
        createNewTab(context, "yue://newtab", true)
    }

    private fun setupSessionCallbacks(
        context: Context,
        session: BrowserSession,
        actualTabId: String,
        isPrivate: Boolean,
        initialUrl: String
    ) {
        session.newTabCallback = { newUrl, isPriv ->
            try {
                createNewTab(context, newUrl, isPriv, null)
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error in newTabCallback", e)
            }
        }
        if (session is com.yue.browser.data.engine.SystemWebViewSession) {
            session.newTabWithWebViewCallback = { tempWebView, isPriv, opHost ->
                try {
                    createNewTabWithWebView(context, tempWebView, isPriv, opHost)
                } catch (e: Exception) {
                    Log.e("TabRepositoryImpl", "Error in newTabWithWebViewCallback", e)
                }
            }
            session.requestCloseCallback = {
                try {
                    val currentList = _tabs.value
                    val index = currentList.indexOfFirst { it.id == actualTabId }
                    if (index != -1) {
                        closeTab(index, context)
                    }
                } catch (e: Exception) {
                    Log.e("TabRepositoryImpl", "Error in requestCloseCallback", e)
                }
            }
        }

        session.faviconCallback = { favicon ->
            try {
                updateTab(actualTabId) { it.copy(favicon = favicon) }
                Thread {
                    saveBitmapToFile(getFaviconFile(context, actualTabId), favicon, isPng = true)
                }.start()
            } catch (e: Exception) {
                // Ignore — favicon updates are non-critical
            }
        }

        session.thumbnailCaptureCallback = { bitmap ->
            try {
                updateTab(actualTabId) { it.copy(thumbnail = bitmap) }
                Thread {
                    saveBitmapToFile(getThumbnailFile(context, actualTabId), bitmap, isPng = false)
                }.start()
            } catch (e: Exception) {
                // Ignore — thumbnail updates are non-critical
            }
        }

        session.stateCallback = { u, t, p, gb, gf ->
            try {
                var changed = false
                updateTab(actualTabId) {
                    if (it.url != u || it.title != t) {
                        changed = true
                    }
                    val resetThumbnail = it.url != u
                    
                    val oldHost = try { android.net.Uri.parse(it.url).host ?: "" } catch(e: Exception) { "" }
                    val newHost = try { android.net.Uri.parse(u).host ?: "" } catch(e: Exception) { "" }
                    val isSameDomain = oldHost.isNotEmpty() && newHost.isNotEmpty() &&
                            (oldHost.removePrefix("www.").removePrefix("m.") == newHost.removePrefix("www.").removePrefix("m."))
                    
                    val stillTranslated = it.isTranslated && isSameDomain
                    
                    it.copy(
                        url = u,
                        title = t,
                        progress = p,
                        canGoBack = gb,
                        canGoForward = gf,
                        thumbnail = if (resetThumbnail) null else it.thumbnail,
                        isTranslated = stillTranslated,
                        translatedDomain = if (stillTranslated) it.translatedDomain else null
                    )
                }
                if (changed) {
                    if (initialUrl != u) {
                        Thread {
                            getThumbnailFile(context, actualTabId).delete()
                        }.start()
                    }
                    autoSave()
                }
            } catch (e: Exception) {
                // Ignore — state updates are frequent, don't crash on transient issues
            }
        }
    }

    override fun createGroup(name: String, colorIndex: Int, tabIds: List<String>): String {
        val newGroupId = UUID.randomUUID().toString()
        val group = TabGroup(id = newGroupId, name = name, colorIndex = colorIndex)
        
        val updatedGroups = _groups.value.toMutableMap()
        updatedGroups[newGroupId] = group
        _groups.value = updatedGroups
        
        val currentTabs = _tabs.value.toMutableList()
        tabIds.forEach { id ->
            val idx = currentTabs.indexOfFirst { it.id == id }
            if (idx != -1) {
                currentTabs[idx] = currentTabs[idx].copy(groupId = newGroupId)
            }
        }
        _tabs.value = currentTabs
        autoSave()
        return newGroupId
    }

    override fun addTabToGroup(tabId: String, groupId: String) {
        val currentTabs = _tabs.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == tabId }
        if (idx != -1 && _groups.value.containsKey(groupId)) {
            currentTabs[idx] = currentTabs[idx].copy(groupId = groupId)
            _tabs.value = currentTabs
            autoSave()
        }
    }

    override fun removeTabFromGroup(tabId: String) {
        val currentTabs = _tabs.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == tabId }
        if (idx != -1) {
            currentTabs[idx] = currentTabs[idx].copy(groupId = null)
            _tabs.value = currentTabs
            cleanEmptyGroups()
            autoSave()
        }
    }

    override fun renameGroup(groupId: String, newName: String) {
        val currentGroups = _groups.value.toMutableMap()
        val group = currentGroups[groupId]
        if (group != null) {
            currentGroups[groupId] = group.copy(name = newName)
            _groups.value = currentGroups
            autoSave()
        }
    }

    override fun updateGroupColor(groupId: String, colorIndex: Int) {
        val currentGroups = _groups.value.toMutableMap()
        val group = currentGroups[groupId]
        if (group != null) {
            currentGroups[groupId] = group.copy(colorIndex = colorIndex)
            _groups.value = currentGroups
            autoSave()
        }
    }

    override fun deleteGroup(groupId: String) {
        val currentGroups = _groups.value.toMutableMap()
        if (currentGroups.remove(groupId) != null) {
            _groups.value = currentGroups
            
            val currentTabs = _tabs.value.toMutableList()
            currentTabs.forEachIndexed { idx, tab ->
                if (tab.groupId == groupId) {
                    currentTabs[idx] = tab.copy(groupId = null)
                }
            }
            _tabs.value = currentTabs
            autoSave()
        }
    }

    override fun moveTab(fromIndex: Int, toIndex: Int) {
        val currentList = _tabs.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val tab = currentList.removeAt(fromIndex)
            currentList.add(toIndex, tab)
            _tabs.value = currentList
            autoSave()
        }
    }

    private fun cleanEmptyGroups() {
        val activeGroupIds = _tabs.value.mapNotNull { it.groupId }.toSet()
        val currentGroups = _groups.value
        val updatedGroups = currentGroups.filterKeys { it in activeGroupIds }
        if (updatedGroups.size != currentGroups.size) {
            _groups.value = updatedGroups
            autoSave()
        }
    }

    override fun createNewTab(
        context: Context,
        url: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)?,
        loadImmediately: Boolean,
        tabId: String?,
        title: String?
    ) {
        this.appContext = context.applicationContext
        try {
            val actualTabId = tabId ?: UUID.randomUUID().toString()
            val session = browserEngine.createSession(
                context = context,
                id = actualTabId,
                isPrivate = isPrivate,
                onLanguageDetected = onLanguageDetected,
                onNewTabRequested = { newUrl ->
                    try {
                        createNewTab(context, newUrl, isPrivate, null)
                    } catch (e: Exception) {
                        Log.e("TabRepositoryImpl", "Error creating new tab from onNewTabRequested", e)
                    }
                }
            )

            setupSessionCallbacks(context, session, actualTabId, isPrivate, url)

            val cachedThumbnail = loadBitmapFromFile(getThumbnailFile(context, actualTabId))
            val cachedFavicon = loadBitmapFromFile(getFaviconFile(context, actualTabId))

            val initialTab = BrowserTab(
                id = actualTabId,
                url = url,
                title = title ?: (if (url == "yue://newtab" || url.isBlank()) "New Tab" else "Loading..."),
                session = session,
                isPrivate = isPrivate,
                thumbnail = cachedThumbnail,
                favicon = cachedFavicon
            )

            val currentList = _tabs.value.toMutableList()
            currentList.add(initialTab)
            _tabs.value = currentList
            _activeTabIndex.value = currentList.size - 1

            if (url.isNotBlank() && url != "yue://newtab" && loadImmediately) {
                session.loadUrl(url)
            }
            autoSave()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Fatal error in createNewTab", e)
            // Fallback: pastikan aplikasi tidak crash total — list tab tetap konsisten
            val currentList = _tabs.value.toMutableList()
            if (currentList.isEmpty()) {
                // Buat tab kosong sebagai fallback darurat
                _activeTabIndex.value = 0
            }
        }
    }
    override fun createNewTabWithWebView(
        context: Context,
        webView: android.webkit.WebView,
        isPrivate: Boolean,
        openerHost: String
    ) {
        this.appContext = context.applicationContext
        try {
            val actualTabId = UUID.randomUUID().toString()
            val session = browserEngine.createSession(
                context = context,
                id = actualTabId,
                isPrivate = isPrivate,
                preExistingWebView = webView
            )
            if (session is com.yue.browser.data.engine.SystemWebViewSession) {
                session.openerHost = openerHost
            }

            setupSessionCallbacks(context, session, actualTabId, isPrivate, webView.url ?: "")

            val initialTab = BrowserTab(
                id = actualTabId,
                url = webView.url ?: "",
                title = webView.title ?: "Loading...",
                session = session,
                isPrivate = isPrivate
            )

            val currentList = _tabs.value.toMutableList()
            currentList.add(initialTab)
            _tabs.value = currentList
            _activeTabIndex.value = currentList.size - 1

            autoSave()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Error in createNewTabWithWebView", e)
        }
    }

    override fun closeTab(index: Int, context: android.content.Context?) {
        val currentList = _tabs.value.toMutableList()
        if (index !in currentList.indices) return

        val tabToClose = currentList[index]
        val isPrivate = tabToClose.isPrivate
        val oldActiveIndex = _activeTabIndex.value

        // Destroy session tab yang akan ditutup
        try {
            tabToClose.session.destroy()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Error destroying session during closeTab", e)
        }

        // Hapus tab dari list
        currentList.removeAt(index)
        _tabs.value = currentList
        cleanEmptyGroups()

        // === Tentukan active tab yang BARU ===
        // Prinsip: JANGAN otomatis ke tab 0, kecuali memang HANYA ADA tab baru
        val newActiveIndex = when {
            // 1. List masih kosong (semua tab dihapus): buat tab default baru
            currentList.isEmpty() -> {
                0 // akan diupdate createNewTab
            }
            // 2. Tab yang ditutup ADALAH tab aktif: pilih tab di posisi yang sama (atau sebelumnya)
            index == oldActiveIndex -> {
                if (index in currentList.indices) index else (currentList.size - 1).coerceAtLeast(0)
            }
            // 3. Tab yang ditutup ADA SEBELUM tab aktif: geser indeks aktif ke kiri 1
            index < oldActiveIndex -> {
                (oldActiveIndex - 1).coerceAtLeast(0)
            }
            // 4. Tab yang ditutup ADA SESUDAH tab aktif: indeks aktif TETAP SAMA
            else -> {
                oldActiveIndex.coerceAtMost(currentList.size - 1).coerceAtLeast(0)
            }
        }
        _activeTabIndex.value = newActiveIndex

        val ctx = context ?: appContext
        if (ctx != null) {
            Thread {
                deleteTabFiles(ctx, tabToClose.id)
            }.start()
        }

        // === Post-processing: buat tab default jika SEMUA tab dihapus ===
        if (currentList.isEmpty() && ctx != null) {
            createNewTab(ctx, "yue://newtab", isPrivate = false)
        }
        autoSave()
    }

    override fun closePrivateTabsOnly() {
        val currentList = _tabs.value.toMutableList()
        val oldActiveIndex = _activeTabIndex.value
        val privateTabs = currentList.filter { it.isPrivate }
        val ctx = appContext
        for (tab in privateTabs) {
            try {
                tab.session.destroy()
                if (ctx != null) {
                    Thread {
                        deleteTabFiles(ctx, tab.id)
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error destroying private tab session", e)
            }
        }
        val normalTabs = currentList.filter { !it.isPrivate }
        _tabs.value = normalTabs
        cleanEmptyGroups()

        if (normalTabs.isNotEmpty()) {
            // Jika tab aktif sebelumnya adalah normal, hitung shift akibat penghapusan tab private
            val activeTabWasPrivate = currentList.getOrNull(oldActiveIndex)?.isPrivate == true
            if (activeTabWasPrivate) {
                // Tab aktif adalah private: pindah ke tab normal pertama di list
                _activeTabIndex.value = 0
            } else {
                // Tab aktif adalah normal: hitung berapa banyak tab private dihapus SEBELUM index aktif
                val privateTabsBeforeActive = currentList.take(oldActiveIndex).count { it.isPrivate }
                val newIndex = (oldActiveIndex - privateTabsBeforeActive).coerceAtLeast(0).coerceAtMost(normalTabs.size - 1)
                _activeTabIndex.value = newIndex
            }
        } else {
            _activeTabIndex.value = 0
        }
        autoSave()
    }

    override fun closeAllTabs(context: android.content.Context?) {
        val currentList = _tabs.value
        val ctx = context ?: appContext
        for (tab in currentList) {
            try {
                tab.session.destroy()
                if (ctx != null) {
                    Thread {
                        deleteTabFiles(ctx, tab.id)
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error destroying session during closeAllTabs", e)
            }
        }
        _tabs.value = emptyList()
        _groups.value = emptyMap()
        _activeTabIndex.value = 0
        // Setelah semua tab dihapus, buat tab default baru jika context tersedia
        if (ctx != null) {
            createNewTab(ctx, "yue://newtab", isPrivate = false)
        }
        autoSave()
    }

    override fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
            val tab = _tabs.value[index]
            updateTab(tab.id) { it.copy(lastAccessed = System.currentTimeMillis()) }
            
            val sessionUrl = tab.session.url
            if ((sessionUrl.isBlank() || sessionUrl == "about:blank" || sessionUrl == "yue://newtab") && tab.url != "yue://newtab" && tab.url.isNotBlank()) {
                tab.session.loadUrl(tab.url)
            }
            autoSave()
        }
    }

    override fun loadUriInActiveTab(url: String) {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            val prevUrl = activeTab.url
            updateTab(activeTab.id) { it.copy(url = url, lastAccessed = System.currentTimeMillis()) }
            if (url == "yue://newtab") {
                if (prevUrl != "yue://newtab" && prevUrl != "about:blank") {
                    activeTab.session.loadUrl("about:blank")
                }
            } else if (url.isNotBlank()) {
                activeTab.session.loadUrl(url)
            }
            autoSave()
        }
    }

    override fun goBackInActiveTab() {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            if (activeTab.url == "yue://newtab") {
                return
            }
            activeTab.session.goBack()
        }
    }

    override fun goForwardInActiveTab() {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            activeTab.session.goForward()
        }
    }

    override fun reloadActiveTab() {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            if (activeTab.url != "yue://newtab" && activeTab.url.isNotBlank()) {
                activeTab.session.reload()
            }
        }
    }

    override fun updateTabThumbnail(index: Int, bitmap: Bitmap) {
        val currentList = _tabs.value.toMutableList()
        if (index in currentList.indices) {
            val tab = currentList[index]
            currentList[index] = tab.copy(thumbnail = bitmap)
            _tabs.value = currentList
            val ctx = appContext
            if (ctx != null) {
                Thread {
                    saveBitmapToFile(getThumbnailFile(ctx, tab.id), bitmap, isPng = false)
                }.start()
            }
        }
    }

    override fun translatePage(sourceLanguage: String, targetLanguage: String) {
        val index = _activeTabIndex.value
        val currentTabs = _tabs.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            val currentUrl = activeTab.url
            if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
                val host = try { android.net.Uri.parse(currentUrl).host ?: "" } catch(e: Exception) { "" }
                updateTab(activeTab.id) {
                    it.copy(
                        isTranslated = true,
                        translationSource = sourceLanguage,
                        translationTarget = targetLanguage,
                        translatedDomain = host
                    )
                }
                
                val script = """
                    (async function() {
                        const sourceLang = '$sourceLanguage';
                        const targetLang = '$targetLanguage';
                        const ignoreTags = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'IFRAME', 'TEXTAREA', 'INPUT']);
                        
                        if (!window.__translationCallbacks) {
                            window.__translationCallbacks = {};
                            window.onTranslationCompleted = function(translatedText, callbackId) {
                                const cb = window.__translationCallbacks[callbackId];
                                if (cb) {
                                    cb(translatedText);
                                    delete window.__translationCallbacks[callbackId];
                                }
                            };
                            window.onTranslationFailed = function(callbackId) {
                                delete window.__translationCallbacks[callbackId];
                            };
                        }

                        function getTextNodes(node) {
                            const textNodes = [];
                            const walk = document.createTreeWalker(
                                node,
                                NodeFilter.SHOW_TEXT,
                                {
                                    acceptNode: function(n) {
                                        if (n.parentNode && ignoreTags.has(n.parentNode.tagName)) {
                                            return NodeFilter.FILTER_REJECT;
                                        }
                                        if (n.textContent.trim().length === 0) {
                                            return NodeFilter.FILTER_REJECT;
                                        }
                                        return NodeFilter.FILTER_ACCEPT;
                                    }
                                }
                            );
                            let n;
                            while (n = walk.nextNode()) {
                                textNodes.push(n);
                            }
                            return textNodes;
                        }

                        const textNodes = getTextNodes(document.body);
                        if (textNodes.length === 0) return;

                        let currentBatch = [];
                        let currentLength = 0;
                        const batches = [];

                        for (const node of textNodes) {
                            const text = node.textContent;
                            if (currentLength + text.length > 3000) {
                                batches.push(currentBatch);
                                currentBatch = [];
                                currentLength = 0;
                            }
                            currentBatch.push(node);
                            currentLength += text.length;
                        }
                        if (currentBatch.length > 0) {
                            batches.push(currentBatch);
                        }

                        function translateTextNative(text) {
                            return new Promise((resolve, reject) => {
                                const callbackId = Math.random().toString(36).substring(2);
                                window.__translationCallbacks[callbackId] = resolve;
                                if (window.YueAddons && window.YueAddons.translateText) {
                                    window.YueAddons.translateText(text, sourceLang, targetLang, callbackId);
                                } else {
                                    reject("YueAddons not found");
                                }
                            });
                        }

                        const promises = batches.map(async (batch) => {
                            const texts = batch.map(n => n.textContent);
                            const delimiter = " ||| ";
                            const combinedText = texts.join(delimiter);
                            try {
                                const translated = await translateTextNative(combinedText);
                                if (translated) {
                                    const translatedTexts = translated.split(/\s*\|\|\|\s*/);
                                    for (let i = 0; i < batch.length; i++) {
                                        if (translatedTexts[i]) {
                                            batch[i].textContent = translatedTexts[i].trim();
                                        }
                                    }
                                }
                            } catch (e) {
                                console.error(e);
                            }
                        });
                        await Promise.all(promises);
                    })();
                """.trimIndent()
                activeTab.session.evaluateJavascript(script, null)
            }
        }
    }

    override fun cancelTranslation() {
        val index = _activeTabIndex.value
        val currentTabs = _tabs.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            updateTab(activeTab.id) {
                it.copy(
                    isTranslated = false,
                    translatedDomain = null
                )
            }
            reloadActiveTab()
        }
    }

    override fun saveState(context: Context) {
        this.appContext = context.applicationContext
        try {
            val root = JSONObject()
            val tabsArray = JSONArray()
            val activeTabId = _tabs.value.getOrNull(_activeTabIndex.value)?.id
            var savedActiveIndex = 0
            var savedIndexCounter = 0

            _tabs.value.forEach { tab ->
                if (!tab.isPrivate) {
                    val obj = JSONObject()
                    obj.put("id", tab.id)
                    obj.put("title", tab.title)
                    obj.put("url", tab.url)
                    obj.put("isPrivate", tab.isPrivate)
                    obj.put("lastAccessed", tab.lastAccessed)
                    if (tab.groupId != null) {
                        obj.put("groupId", tab.groupId)
                    }
                    tabsArray.put(obj)

                    if (tab.id == activeTabId) {
                        savedActiveIndex = savedIndexCounter
                    }
                    savedIndexCounter++
                }
            }
            root.put("activeTabIndex", savedActiveIndex)
            root.put("tabs", tabsArray)

            val groupsObj = JSONObject()
            _groups.value.forEach { (id, group) ->
                val groupJson = JSONObject()
                groupJson.put("id", group.id)
                groupJson.put("name", group.name)
                groupJson.put("colorIndex", group.colorIndex)
                groupsObj.put(id, groupJson)
            }
            root.put("groups", groupsObj)

            val file = File(context.filesDir, "tabs_state.json")
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to save state", e)
        }
    }

    override fun restoreState(context: Context) {
        this.appContext = context.applicationContext
        migratePreviewsToCacheDir(context)
        try {
            val file = File(context.filesDir, "tabs_state.json")
            if (!file.exists()) return

            val text = file.readText()
            if (text.isBlank()) return

            val root = JSONObject(text)
            val activeIndex = root.optInt("activeTabIndex", 0)
            val tabsArray = root.optJSONArray("tabs") ?: return

            // Restore groups
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
            _groups.value = restoredGroups

            // Bersihkan tab yang ada (tanpa membuat tab default baru)
            try {
                val currentList = _tabs.value
                for (tab in currentList) {
                    try {
                        tab.session.destroy()
                    } catch (e: Exception) {
                        // Ignore — tab yang di-destroy sebelumnya
                    }
                }
                _tabs.value = emptyList()
                _activeTabIndex.value = 0
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error clearing tabs during restore", e)
            }

            // Restore tab-tab yang tersimpan
            for (i in 0 until tabsArray.length()) {
                try {
                    val obj = tabsArray.getJSONObject(i)
                    val tabId = obj.optString("id", UUID.randomUUID().toString())
                    val title = obj.optString("title", "New Tab")
                    val url = obj.optString("url", "yue://newtab")
                    val isPrivate = obj.optBoolean("isPrivate", false)
                    val lastAccessed = obj.optLong("lastAccessed", System.currentTimeMillis())
                    val shouldLoad = (i == activeIndex)
                    val groupId = if (obj.has("groupId")) obj.optString("groupId", null) else null

                    createNewTab(context, url, isPrivate, tabId = tabId, title = title, loadImmediately = shouldLoad)
                    val currentTabs = _tabs.value
                    if (currentTabs.isNotEmpty()) {
                        val lastTab = currentTabs.last()
                        updateTab(lastTab.id) { it.copy(lastAccessed = lastAccessed, groupId = groupId) }
                    }
                } catch (e: Exception) {
                    Log.e("TabRepositoryImpl", "Error restoring tab at index $i", e)
                    // Lanjutkan ke tab berikutnya — jangan gagalkan seluruh restore
                }
            }

            if (_tabs.value.isNotEmpty() && activeIndex in _tabs.value.indices) {
                _activeTabIndex.value = activeIndex
            }

            // Safety: jika setelah restore tidak ada tab normal, buat satu
            if (_tabs.value.none { !it.isPrivate }) {
                createNewTab(context, "yue://newtab", isPrivate = false, loadImmediately = false)
            }

            // Clean up any orphan preview files that do not correspond to any active restored tab
            Thread {
                cleanOrphanTabFiles(context)
            }.start()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to restore state", e)
        }
    }

    private fun updateTab(id: String, update: (BrowserTab) -> BrowserTab) {
        val currentList = _tabs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = update(currentList[index])
            _tabs.value = currentList
        }
    }

    private fun autoSave() {
        val context = appContext ?: return
        Thread {
            saveState(context)
        }.start()
    }

    private fun getThumbnailFile(context: Context, tabId: String): File {
        val dir = File(context.cacheDir, "tab_previews")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${tabId}_thumbnail.jpg")
    }

    private fun getFaviconFile(context: Context, tabId: String): File {
        val dir = File(context.cacheDir, "tab_previews")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${tabId}_favicon.png")
    }

    private fun saveBitmapToFile(file: File, bitmap: Bitmap, isPng: Boolean) {
        try {
            java.io.FileOutputStream(file).use { out ->
                if (isPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
            }
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to save bitmap to file: ${file.absolutePath}", e)
        }
    }

    private fun migratePreviewsToCacheDir(context: Context) {
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
                Log.e("TabRepositoryImpl", "Failed to migrate file ${file.name}", e)
            }
        }
        try {
            oldDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to delete old tab_previews directory", e)
        }
    }

    private fun loadBitmapFromFile(file: File): Bitmap? {
        return try {
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to load bitmap from file: ${file.absolutePath}", e)
            null
        }
    }

    private fun deleteTabFiles(context: Context, tabId: String) {
        try {
            getThumbnailFile(context, tabId).delete()
            getFaviconFile(context, tabId).delete()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to delete files for tab $tabId", e)
        }
    }

    private fun cleanOrphanTabFiles(context: Context) {
        try {
            val dir = File(context.cacheDir, "tab_previews")
            if (!dir.exists()) return
            val activeIds = _tabs.value.map { it.id }.toSet()
            val files = dir.listFiles() ?: return
            for (file in files) {
                val tabId = file.name.substringBefore("_thumbnail.jpg").substringBefore("_favicon.png")
                if (tabId.isNotBlank() && tabId !in activeIds) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to clean orphan tab files", e)
        }
    }
}
