package com.yue.browser.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.yue.browser.domain.engine.BrowserEngine
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.repository.SettingsRepository
import com.yue.browser.domain.repository.TabRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.yue.browser.data.engine.GeckoViewEngine

class TabRepositoryImpl(
    private val browserEngine: BrowserEngine,
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl.instance
) : TabRepository {
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    override val tabsFlow: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    override val activeTabIndexFlow: StateFlow<Int> = _activeTabIndex.asStateFlow()

    override fun newIncognitoTab(context: Context) {
        createNewTab(context, "yue://newtab", true)
    }

    override fun createNewTab(
        context: Context,
        url: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)?,
        loadImmediately: Boolean
    ) {
        val tabId = UUID.randomUUID().toString()
        val session = browserEngine.createSession(
            context = context,
            id = tabId,
            isPrivate = isPrivate,
            onLanguageDetected = onLanguageDetected,
            onNewTabRequested = { newUrl ->
                createNewTab(context, newUrl, isPrivate, null)
            }
        )

        // Also ensure the engine's global new tab requested uses this TabRepository instance
        if (browserEngine is GeckoViewEngine) {
            GeckoViewEngine.setGlobalOnNewTabRequested { newUrl ->
                createNewTab(context, newUrl, isPrivate, null)
            }
        }

        session.newTabCallback = { newUrl, isPriv ->
            createNewTab(context, newUrl, isPriv, null)
        }

        session.faviconCallback = { favicon ->
            updateTab(tabId) { it.copy(favicon = favicon) }
        }
        
        session.thumbnailCaptureCallback = { bitmap ->
            updateTab(tabId) { it.copy(thumbnail = bitmap) }
        }

        session.stateCallback = { u, t, p, gb, gf ->
            updateTab(tabId) {
                val resetThumbnail = it.url != u
                it.copy(
                    url = u,
                    title = t,
                    progress = p,
                    canGoBack = gb,
                    canGoForward = gf,
                    thumbnail = if (resetThumbnail) null else it.thumbnail
                )
            }
        }

        val initialTab = BrowserTab(
            id = tabId,
            url = url,
            title = if (url == "yue://newtab" || url.isBlank()) "New Tab" else "Loading...",
            session = session,
            isPrivate = isPrivate
        )

        val currentList = _tabs.value.toMutableList()
        currentList.add(initialTab)
        _tabs.value = currentList
        _activeTabIndex.value = currentList.size - 1

        if (url.isNotBlank() && url != "yue://newtab" && loadImmediately) {
            session.loadUrl(url)
        }
    }

    override fun closeTab(index: Int, context: android.content.Context?) {
        val currentList = _tabs.value.toMutableList()
        if (index !in currentList.indices) return

        val tabToClose = currentList[index]
        val isPrivate = tabToClose.isPrivate
        val sameTypeTabs = currentList.filter { it.isPrivate == isPrivate }

        if (sameTypeTabs.size <= 1) {
            if (!isPrivate) {
                // Tab normal terakhir: reset URL (selalu harus ada minimal 1 tab normal)
                tabToClose.session.loadUrl("yue://newtab")
                updateTab(tabToClose.id) { it.copy(url = "yue://newtab", title = "New Tab", progress = 0) }
            } else {
                // === Tab private TERAKHIR: SELALU hapus permanen, PINDAH ke tab NORMAL ===
                val normalTabs = currentList.filter { !it.isPrivate }
                tabToClose.session.destroy()
                currentList.removeAt(index)

                if (normalTabs.isNotEmpty()) {
                    _tabs.value = currentList
                    _activeTabIndex.value = currentList.indexOf(normalTabs.first())
                } else {
                    _tabs.value = currentList
                    _activeTabIndex.value = 0
                    if (context != null) {
                        createNewTab(context, "yue://newtab", isPrivate = false)
                    }
                }
            }
            return
        }

        tabToClose.session.destroy()
        currentList.removeAt(index)

        val currentIndex = _activeTabIndex.value
        _tabs.value = currentList

        if (index == currentIndex) {
            val remainingSameType = currentList.filter { it.isPrivate == isPrivate }
            val firstRemaining = remainingSameType.first()
            _activeTabIndex.value = currentList.indexOf(firstRemaining)
        } else {
            val activeTab = currentList.getOrNull(currentIndex)
            if (activeTab != null) {
                _activeTabIndex.value = currentList.indexOf(activeTab)
            } else {
                _activeTabIndex.value = if (currentIndex > 0) currentIndex - 1 else 0
            }
        }
    }

    override fun closePrivateTabsOnly() {
        val currentList = _tabs.value.toMutableList()
        val privateTabs = currentList.filter { it.isPrivate }
        for (tab in privateTabs) {
            tab.session.destroy()
        }
        val normalTabs = currentList.filter { !it.isPrivate }
        _tabs.value = normalTabs
        _activeTabIndex.value = if (normalTabs.isNotEmpty()) 0 else 0
    }

    override fun closeAllTabs() {
        val currentList = _tabs.value
        for (tab in currentList) {
            tab.session.destroy()
        }
        _tabs.value = emptyList()
        _activeTabIndex.value = 0
    }

    override fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
            val tab = _tabs.value[index]
            updateTab(tab.id) { it.copy(lastAccessed = System.currentTimeMillis()) }
            
            val sessionUrl = tab.session.url
            if ((sessionUrl.isBlank() || sessionUrl == "about:blank") && tab.url != "yue://newtab" && tab.url.isNotBlank()) {
                tab.session.loadUrl(tab.url)
            }
        }
    }

    override fun loadUriInActiveTab(url: String) {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            updateTab(activeTab.id) { it.copy(url = url, lastAccessed = System.currentTimeMillis()) }
            if (url != "yue://newtab" && url.isNotBlank()) {
                activeTab.session.loadUrl(url)
            }
        }
    }

    override fun goBackInActiveTab() {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            val sessionCanGoBack = activeTab.session.canGoBack
            val modelCanGoBack = activeTab.canGoBack
            if (!sessionCanGoBack && !modelCanGoBack) {
                return
            }
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
            val sessionCanGoForward = activeTab.session.canGoForward
            val modelCanGoForward = activeTab.canGoForward
            if (!sessionCanGoForward && !modelCanGoForward) {
                return
            }
            if (activeTab.url == "yue://newtab") {
                return
            }
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
            currentList[index] = currentList[index].copy(thumbnail = bitmap)
            _tabs.value = currentList
        }
    }

    override fun translatePage(targetLanguage: String) {
        val index = _activeTabIndex.value
        val currentTabs = _tabs.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            val currentUrl = activeTab.url
            if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
                val encodedUrl = android.net.Uri.encode(currentUrl)
                val translateUrl = "https://translate.google.com/translate?sl=auto&tl=$targetLanguage&u=$encodedUrl"
                activeTab.session.loadUrl(translateUrl)
            }
        }
    }

    override fun saveState(context: Context) {
        try {
            val root = JSONObject()
            val tabsArray = JSONArray()
            _tabs.value.forEach { tab ->
                val obj = JSONObject()
                obj.put("url", tab.url)
                obj.put("isPrivate", tab.isPrivate)
                obj.put("lastAccessed", tab.lastAccessed)
                tabsArray.put(obj)
            }
            root.put("activeTabIndex", _activeTabIndex.value)
            root.put("tabs", tabsArray)

            val file = File(context.filesDir, "tabs_state.json")
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e("TabRepositoryImpl", "Failed to save state", e)
        }
    }

    override fun restoreState(context: Context) {
        try {
            val file = File(context.filesDir, "tabs_state.json")
            if (!file.exists()) return

            val text = file.readText()
            if (text.isBlank()) return

            val root = JSONObject(text)
            val activeIndex = root.optInt("activeTabIndex", 0)
            val tabsArray = root.optJSONArray("tabs") ?: return

            closeAllTabs()

            for (i in 0 until tabsArray.length()) {
                val obj = tabsArray.getJSONObject(i)
                val url = obj.optString("url", "yue://newtab")
                val isPrivate = obj.optBoolean("isPrivate", false)
                val lastAccessed = obj.optLong("lastAccessed", System.currentTimeMillis())
                val shouldLoad = (i == activeIndex)
                
                createNewTab(context, url, isPrivate, loadImmediately = shouldLoad)
                val currentTabs = _tabs.value
                if (currentTabs.isNotEmpty()) {
                    val lastTab = currentTabs.last()
                    updateTab(lastTab.id) { it.copy(lastAccessed = lastAccessed) }
                }
            }

            if (_tabs.value.isNotEmpty() && activeIndex in _tabs.value.indices) {
                _activeTabIndex.value = activeIndex
            }
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
}
