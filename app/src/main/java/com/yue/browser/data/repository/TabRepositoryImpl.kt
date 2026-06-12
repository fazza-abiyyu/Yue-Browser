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
        try {
            val tabId = UUID.randomUUID().toString()
            val session = browserEngine.createSession(
                context = context,
                id = tabId,
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

            session.newTabCallback = { newUrl, isPriv ->
                try {
                    createNewTab(context, newUrl, isPriv, null)
                } catch (e: Exception) {
                    Log.e("TabRepositoryImpl", "Error in newTabCallback", e)
                }
            }

            session.faviconCallback = { favicon ->
                try {
                    updateTab(tabId) { it.copy(favicon = favicon) }
                } catch (e: Exception) {
                    // Ignore — favicon updates are non-critical
                }
            }

            session.thumbnailCaptureCallback = { bitmap ->
                try {
                    updateTab(tabId) { it.copy(thumbnail = bitmap) }
                } catch (e: Exception) {
                    // Ignore — thumbnail updates are non-critical
                }
            }

            session.stateCallback = { u, t, p, gb, gf ->
                try {
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
                } catch (e: Exception) {
                    // Ignore — state updates are frequent, don't crash on transient issues
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

        // === Post-processing: buat tab default jika SEMUA tab dihapus ===
        if (currentList.isEmpty() && context != null) {
            createNewTab(context, "yue://newtab", isPrivate = false)
        } else {
            // Post-processing tambahan untuk tab private:
            // Jika tab yang ditutup ADALAH tab private TERAKHIR:
            // (Tidak perlu perubahan khusus; MainBrowserScreen sudah menangani keluar mode private)
        }
    }

    override fun closePrivateTabsOnly() {
        val currentList = _tabs.value.toMutableList()
        val oldActiveIndex = _activeTabIndex.value
        val privateTabs = currentList.filter { it.isPrivate }
        for (tab in privateTabs) {
            try {
                tab.session.destroy()
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error destroying private tab session", e)
            }
        }
        val normalTabs = currentList.filter { !it.isPrivate }
        _tabs.value = normalTabs

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
    }

    override fun closeAllTabs(context: android.content.Context?) {
        val currentList = _tabs.value
        for (tab in currentList) {
            try {
                tab.session.destroy()
            } catch (e: Exception) {
                Log.e("TabRepositoryImpl", "Error destroying session during closeAllTabs", e)
            }
        }
        _tabs.value = emptyList()
        _activeTabIndex.value = 0
        // Setelah semua tab dihapus, buat tab default baru jika context tersedia
        if (context != null) {
            createNewTab(context, "yue://newtab", isPrivate = false)
        }
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
            val prevUrl = activeTab.url
            updateTab(activeTab.id) { it.copy(url = url, lastAccessed = System.currentTimeMillis()) }
            if (url == "yue://newtab") {
                if (prevUrl != "yue://newtab" && prevUrl != "about:blank") {
                    activeTab.session.loadUrl("about:blank")
                }
            } else if (url.isNotBlank()) {
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

    override fun translatePage(sourceLanguage: String, targetLanguage: String) {
        val index = _activeTabIndex.value
        val currentTabs = _tabs.value
        if (index in currentTabs.indices) {
            val activeTab = currentTabs[index]
            val currentUrl = activeTab.url
            if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
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
