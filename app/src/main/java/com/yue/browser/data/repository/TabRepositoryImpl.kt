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
import java.util.UUID

class TabRepositoryImpl(
    private val browserEngine: BrowserEngine,
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl.instance
) : TabRepository {
    internal val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    override val tabsFlow: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    internal val _activeTabIndex = MutableStateFlow(0)
    override val activeTabIndexFlow: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _groups = MutableStateFlow<Map<String, TabGroup>>(emptyMap())
    override val groupsFlow: StateFlow<Map<String, TabGroup>> = _groups.asStateFlow()

    internal var appContext: Context? = null

    @Volatile
    internal var suppressPopupCreation: Boolean = false

    internal val pendingPopupActivation = mutableSetOf<String>()
    internal val prePopupActiveIndices = mutableMapOf<String, Int>()
    internal val visitedIncognitoDomains = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
        TabSessionCallbackHelper.setupSessionCallbacks(
            this, context, session, actualTabId, isPrivate, initialUrl
        )
    }

    override fun createGroup(name: String, colorIndex: Int, tabIds: List<String>): String {
        val groupId = TabGroupHelper.createGroup(_tabs, _groups, name, colorIndex, tabIds)
        autoSave()
        return groupId
    }

    override fun addTabToGroup(tabId: String, groupId: String) {
        TabGroupHelper.addTabToGroup(_tabs, _groups, tabId, groupId)
        autoSave()
    }

    override fun removeTabFromGroup(tabId: String) {
        TabGroupHelper.removeTabFromGroup(_tabs, _groups, tabId)
        autoSave()
    }

    override fun renameGroup(groupId: String, newName: String) {
        TabGroupHelper.renameGroup(_groups, groupId, newName)
        autoSave()
    }

    override fun updateGroupColor(groupId: String, colorIndex: Int) {
        TabGroupHelper.updateGroupColor(_groups, groupId, colorIndex)
        autoSave()
    }

    override fun deleteGroup(groupId: String) {
        TabGroupHelper.deleteGroup(_tabs, _groups, groupId)
        autoSave()
    }

    override fun moveTab(fromIndex: Int, toIndex: Int) {
        TabGroupHelper.moveTab(_tabs, fromIndex, toIndex)
        autoSave()
    }

    override fun createNewTab(
        context: Context,
        url: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)?,
        loadImmediately: Boolean,
        tabId: String?,
        title: String?,
        parentTabId: String?
    ) {
        createNewTabInternal(context, url, isPrivate, onLanguageDetected, loadImmediately, tabId, title, parentTabId, skipAutoSave = false)
    }

    private fun createNewTabInternal(
        context: Context,
        url: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)? = null,
        loadImmediately: Boolean = true,
        tabId: String? = null,
        title: String? = null,
        parentTabId: String? = null,
        skipAutoSave: Boolean = false
    ) {
        this.appContext = context.applicationContext
        try {
            val actualTabId = tabId ?: UUID.randomUUID().toString()
            val session = browserEngine.createSession(
                context = context,
                id = actualTabId,
                isPrivate = isPrivate,
                onLanguageDetected = { detectedLang ->
                    updateTab(actualTabId) {
                        it.copy(translationSource = detectedLang)
                    }
                    onLanguageDetected?.invoke(detectedLang)
                },
                onNewTabRequested = { newUrl ->
                    if (suppressPopupCreation) {
                        Log.d("TabRepositoryImpl", "Popup suppressed (onNewTabRequested): $newUrl")
                    } else {
                        try {
                            createNewTab(context, newUrl, isPrivate, null, parentTabId = actualTabId)
                        } catch (e: Exception) {
                            Log.e("TabRepositoryImpl", "Error creating new tab from onNewTabRequested", e)
                        }
                    }
                }
            )

            setupSessionCallbacks(context, session, actualTabId, isPrivate, url)

            val cachedThumbnail = TabStorageHelper.loadBitmapFromFile(TabStorageHelper.getThumbnailFile(context, actualTabId))
            val cachedFavicon = TabStorageHelper.loadBitmapFromFile(TabStorageHelper.getFaviconFile(context, actualTabId))

            val isRealUrl = url.isNotBlank() && url != "yue://newtab" && url != "about:blank"
            val initialTab = BrowserTab(
                id = actualTabId,
                url = url,
                title = title ?: (if (url == "yue://newtab" || url.isBlank()) "New Tab" else "Loading..."),
                session = session,
                isPrivate = isPrivate,
                thumbnail = cachedThumbnail,
                favicon = cachedFavicon,
                parentTabId = parentTabId,
                hasEverNavigatedAway = isRealUrl
            )

            val currentList = _tabs.value.toMutableList()
            val currentIndex = _activeTabIndex.value
            val insertIndex = if (currentIndex in 0 until currentList.size) currentIndex + 1 else currentList.size
            currentList.add(insertIndex, initialTab)
            _tabs.value = currentList
            _activeTabIndex.value = insertIndex

            if (url.isNotBlank() && url != "yue://newtab" && loadImmediately) {
                val stateFile = TabStorageHelper.getWebViewStateFile(context, actualTabId)
                var restored = false
                if (stateFile.exists()) {
                    restored = restoreWebViewState(context, initialTab)
                }
                if (!restored) {
                    session.loadUrl(url)
                }
            }
            if (!skipAutoSave) {
                autoSave()
            }
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Fatal error in createNewTab", e)
            val currentList = _tabs.value.toMutableList()
            if (currentList.isEmpty()) {
                _activeTabIndex.value = 0
            }
        }
    }
    override fun createNewTabWithWebView(
        context: Context,
        webView: android.webkit.WebView,
        isPrivate: Boolean,
        openerHost: String,
        parentTabId: String?
    ) {
        this.appContext = context.applicationContext
        try {
            val actualTabId = UUID.randomUUID().toString()
            val session = browserEngine.createSession(
                context = context,
                id = actualTabId,
                isPrivate = isPrivate,
                onLanguageDetected = { detectedLang ->
                    updateTab(actualTabId) {
                        it.copy(translationSource = detectedLang)
                    }
                },
                preExistingWebView = webView
            )
            if (session is com.yue.browser.data.engine.SystemWebViewSession) {
                session.openerHost = openerHost
                session.isScriptPopup = webView.getTag(987654321) as? Boolean ?: false
            }

            setupSessionCallbacks(context, session, actualTabId, isPrivate, webView.url ?: "")

            val initialUrl = webView.url ?: ""
            val hasNavigated = initialUrl.isNotBlank() && initialUrl != "yue://newtab" && initialUrl != "about:blank"
            val initialTab = BrowserTab(
                id = actualTabId,
                url = initialUrl,
                title = webView.title ?: "Loading...",
                session = session,
                isPrivate = isPrivate,
                parentTabId = parentTabId,
                hasEverNavigatedAway = hasNavigated
            )

            val currentList = _tabs.value.toMutableList()
            val currentIndex = _activeTabIndex.value
            val insertIndex = if (currentIndex in 0 until currentList.size) currentIndex + 1 else currentList.size
            currentList.add(insertIndex, initialTab)
            _tabs.value = currentList
            // JANGAN langsung aktifkan tab popup — tunggu navigasi sukses dulu
            // (via stateCallback saat onPageStarted).
            if (openerHost.isNotEmpty()) {
                pendingPopupActivation.add(actualTabId)
                prePopupActiveIndices[actualTabId] = currentIndex
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (actualTabId in pendingPopupActivation) {
                            val currentList = _tabs.value
                            val index = currentList.indexOfFirst { it.id == actualTabId }
                            if (index != -1) {
                                val tab = currentList[index]
                                if (tab.url.isBlank() || tab.url == "about:blank" || tab.url == "yue://newtab") {
                                    Log.d("TabRepositoryImpl", "Auto-closing popup tab $actualTabId because it remained blank/unactivated")
                                    closeTab(index, context)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TabRepositoryImpl", "Error in popup timeout auto-close", e)
                    }
                }, 2000)
            } else {
                _activeTabIndex.value = insertIndex
            }

            autoSave()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Error in createNewTabWithWebView", e)
        }
    }

    override fun closeTab(index: Int, context: android.content.Context?) {
        val currentList = _tabs.value.toMutableList()
        if (index !in currentList.indices) {
            Log.d("TabRepositoryImpl", "closeTab: index $index out of bounds, size=${currentList.size}")
            return
        }

        val ctx = context ?: appContext
        val tabToClose = currentList[index]
        val isPrivate = tabToClose.isPrivate
        val oldActiveIndex = _activeTabIndex.value
        Log.d("TabRepositoryImpl", "closeTab: closing tab id=${tabToClose.id} url=${tabToClose.url} isPrivate=$isPrivate tabsSize=${currentList.size} activeIdx=$oldActiveIndex")

        // Bersihkan pending activation jika tab ditutup sebelum sempat diaktifkan
        pendingPopupActivation.remove(tabToClose.id)

        // Destroy session tab yang akan ditutup
        try {
            tabToClose.session.destroy()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Error destroying session during closeTab", e)
        }

        // Hapus tab dari list
        currentList.removeAt(index)
        _tabs.value = currentList
        Log.d("TabRepositoryImpl", "closeTab: tab removed, new size=${currentList.size} newActiveIdx=${_activeTabIndex.value}")
        TabGroupHelper.cleanEmptyGroups(_tabs, _groups)

        if (isPrivate) {
            val remainingPrivate = currentList.any { it.isPrivate }
            if (!remainingPrivate && ctx != null) {
                TabStorageHelper.clearPrivateData(ctx, _tabs.value, visitedIncognitoDomains)
            }
        }

        // === Tentukan active tab yang BARU ===
        // Prinsip: JANGAN otomatis ke tab 0, kecuali memang HANYA ADA tab baru
        val newActiveIndex = when {
            // 1. List masih kosong (semua tab dihapus): buat tab default baru
            currentList.isEmpty() -> {
                0 // akan diupdate createNewTab
            }
            // 2. Tab yang ditutup ADALAH tab aktif: pilih tab di posisi yang sama (atau sebelumnya)
            index == oldActiveIndex -> {
                val parentIdx = tabToClose.parentTabId?.let { pId -> currentList.indexOfFirst { it.id == pId } }
                if (parentIdx != null && parentIdx != -1) {
                    parentIdx
                } else {
                    // Jika tab ini popup yg auto-activated, kembalikan ke tab sebelum popup
                    val prePopupIdx = prePopupActiveIndices.remove(tabToClose.id)
                    if (prePopupIdx != null) {
                        prePopupIdx.coerceAtMost(currentList.size - 1).coerceAtLeast(0)
                    } else if (index in currentList.indices) {
                        index
                    } else {
                        (currentList.size - 1).coerceAtLeast(0)
                    }
                }
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

        if (ctx != null) {
            Thread {
                TabStorageHelper.deleteTabFiles(ctx, tabToClose.id)
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
                        TabStorageHelper.deleteTabFiles(ctx, tab.id)
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error destroying private tab session", e)
            }
        }
        val normalTabs = currentList.filter { !it.isPrivate }
        _tabs.value = normalTabs
        TabGroupHelper.cleanEmptyGroups(_tabs, _groups)
        if (ctx != null) {
            TabStorageHelper.clearPrivateData(ctx, _tabs.value, visitedIncognitoDomains)
        }

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
        // Hapus data private dari file state setelah close private tabs
        ctx?.let { TabStorageHelper.cleanupPrivateTabState(it) }
    }



    override fun closeAllTabs(context: android.content.Context?) {
        val currentList = _tabs.value
        val ctx = context ?: appContext
        for (tab in currentList) {
            try {
                tab.session.destroy()
                if (ctx != null) {
                    Thread {
                        TabStorageHelper.deleteTabFiles(ctx, tab.id)
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
            prePopupActiveIndices.remove(tab.id)
            updateTab(tab.id) { it.copy(lastAccessed = System.currentTimeMillis()) }
            
            val sessionUrl = tab.session.url
            if ((sessionUrl.isBlank() || sessionUrl == "about:blank" || sessionUrl == "yue://newtab") && tab.url != "yue://newtab" && tab.url.isNotBlank()) {
                val context = appContext
                var restored = false
                if (context != null) {
                    val stateFile = TabStorageHelper.getWebViewStateFile(context, tab.id)
                    if (stateFile.exists()) {
                        suppressPopupCreation = true
                        try {
                            restored = restoreWebViewState(context, tab)
                        } finally {
                            suppressPopupCreation = false
                        }
                    }
                }
                if (!restored) {
                    suppressPopupCreation = true
                    try {
                        tab.session.loadUrl(tab.url)
                    } finally {
                        suppressPopupCreation = false
                    }
                }
            }
            autoSave()
        }
    }

    private fun getActiveTab(): BrowserTab? {
        return _tabs.value.getOrNull(_activeTabIndex.value)
    }

    override fun loadUriInActiveTab(url: String) {
        val activeTab = getActiveTab() ?: return
        val prevUrl = activeTab.url
        val wasNewTab = prevUrl == "yue://newtab" || prevUrl == "about:blank"
        val isRealUrl = url != "yue://newtab" && url.isNotBlank()
        updateTab(activeTab.id) {
            it.copy(
                url = url,
                lastAccessed = System.currentTimeMillis(),
                hasEverNavigatedAway = it.hasEverNavigatedAway || (wasNewTab && isRealUrl)
            )
        }
        if (url == "yue://newtab") {
            if (prevUrl != "yue://newtab" && prevUrl != "about:blank") {
                activeTab.session.loadUrl("about:blank")
            }
        } else if (url.isNotBlank()) {
            activeTab.session.loadUrl(url)
        }
        autoSave()
    }

    override fun goBackInActiveTab() {
        val activeTab = getActiveTab() ?: return
        if (activeTab.url != "yue://newtab") {
            activeTab.session.goBack()
        }
    }

    override fun tryBackPressInActiveTab(): Boolean {
        val activeTab = getActiveTab() ?: return false
        return if (activeTab.url == "yue://newtab") false else activeTab.session.tryBackPress()
    }

    override fun goForwardInActiveTab() {
        getActiveTab()?.session?.goForward()
    }

    override fun tryForwardPressInActiveTab(): Boolean {
        val activeTab = getActiveTab() ?: return false
        return if (activeTab.url == "yue://newtab") false else activeTab.session.tryForwardPress()
    }

    override fun reloadActiveTab() {
        val activeTab = getActiveTab() ?: return
        if (activeTab.url != "yue://newtab" && activeTab.url.isNotBlank()) {
            activeTab.session.reload()
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
                    TabStorageHelper.saveBitmapToFile(TabStorageHelper.getThumbnailFile(ctx, tab.id), bitmap, isPng = false)
                }.start()
            }
        }
    }

    override fun translatePage(sourceLanguage: String, targetLanguage: String) {
        val activeTab = getActiveTab() ?: return
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
            val script = com.yue.browser.data.engine.WebViewScripts.getPageTranslationScript(sourceLanguage, targetLanguage)
            activeTab.session.evaluateJavascript(script, null)
        }
    }

    override fun cancelTranslation() {
        val activeTab = getActiveTab() ?: return
        updateTab(activeTab.id) {
            it.copy(
                isTranslated = false,
                translatedDomain = null
            )
        }
        reloadActiveTab()
    }

    private fun restoreWebViewState(context: Context, tab: BrowserTab): Boolean {
        return TabStorageHelper.restoreWebViewState(context, tab) { id, update ->
            updateTab(id, update)
        }
    }

    private fun saveStateInternal(context: Context) {
        TabStorageHelper.saveState(context, _tabs.value, _activeTabIndex.value, _groups.value)
    }

    override fun saveState(context: Context) {
        this.appContext = context.applicationContext
        saveStateInternal(context)
    }

    override fun restoreState(context: Context) {
        this.appContext = context.applicationContext
        TabStorageHelper.migratePreviewsToCacheDir(context)
        val ctx = context
        val isRecreation = com.yue.browser.MainActivity.isProcessRecreation
        if (!isRecreation) {
            TabStorageHelper.clearPrivateData(ctx, _tabs.value, visitedIncognitoDomains)
        }
        try {
            try {
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Exception) {}

            val state = TabStorageHelper.readSavedTabsState(context, isRecreation) ?: return
            _groups.value = state.groups

            try {
                val currentList = _tabs.value
                for (tab in currentList) {
                    try {
                        tab.session.destroy()
                    } catch (e: Exception) {}
                }
                _tabs.value = emptyList()
                _activeTabIndex.value = 0
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error clearing tabs during restore", e)
            }

            suppressPopupCreation = true
            try {
                state.tabs.forEachIndexed { i, tabData ->
                    try {
                        val shouldLoad = (i == state.activeTabIndex)
                        createNewTabInternal(context, tabData.url, tabData.isPrivate, tabId = tabData.id, title = tabData.title, loadImmediately = shouldLoad, parentTabId = tabData.parentTabId, skipAutoSave = true)
                        val currentTabs = _tabs.value
                        if (currentTabs.isNotEmpty()) {
                            val lastTab = currentTabs.last()
                            updateTab(lastTab.id) { it.copy(lastAccessed = tabData.lastAccessed, groupId = tabData.groupId, hasEverNavigatedAway = tabData.hasEverNavigatedAway) }
                        }
                    } catch (e: Exception) {
                        Log.e("TabRepositoryImpl", "Error restoring tab at index $i", e)
                    }
                }

                if (_tabs.value.isNotEmpty() && state.activeTabIndex in _tabs.value.indices) {
                    _activeTabIndex.value = state.activeTabIndex
                }

                if (_tabs.value.isEmpty()) {
                    createNewTabInternal(context, "yue://newtab", isPrivate = false, loadImmediately = false, skipAutoSave = true)
                }
            } finally {
                suppressPopupCreation = false
            }

            saveStateInternal(context)

            Thread {
                val activeIds = _tabs.value.map { it.id }.toSet()
                TabStorageHelper.cleanOrphanTabFiles(context, activeIds)
            }.start()
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to restore state", e)
            suppressPopupCreation = false
        }
    }

    internal fun updateTab(id: String, update: (BrowserTab) -> BrowserTab) {
        val currentList = _tabs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = update(currentList[index])
            _tabs.value = currentList
        }
    }

    internal fun autoSave() {
        val context = appContext ?: return
        saveStateInternal(context)
    }
}
