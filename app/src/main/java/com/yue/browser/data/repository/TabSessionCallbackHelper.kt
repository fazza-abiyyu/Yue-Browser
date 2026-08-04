package com.yue.browser.data.repository

import android.content.Context
import android.util.Log
import com.yue.browser.domain.engine.BrowserSession

object TabSessionCallbackHelper {
    fun setupSessionCallbacks(
        repository: TabRepositoryImpl,
        context: Context,
        session: BrowserSession,
        actualTabId: String,
        isPrivate: Boolean,
        initialUrl: String
    ) {
        session.newTabCallback = { newUrl, isPriv ->
            if (repository.suppressPopupCreation) {
                Log.d("TabSessionCallbackHelper", "Popup suppressed during restore/load: $newUrl")
            } else {
                try {
                    repository.createNewTab(context, newUrl, isPriv, null, parentTabId = actualTabId)
                } catch (e: Exception) {
                    Log.e("TabSessionCallbackHelper", "Error in newTabCallback", e)
                }
            }
        }
        if (session is com.yue.browser.data.engine.SystemWebViewSession) {
            session.newTabWithWebViewCallback = { tempWebView, isPriv, opHost ->
                if (repository.suppressPopupCreation) {
                    Log.d("TabSessionCallbackHelper", "Popup (with WebView) suppressed during restore/load")
                    try {
                        tempWebView.stopLoading()
                        tempWebView.loadUrl("about:blank")
                    } catch (_: Exception) {}
                } else {
                    try {
                        repository.createNewTabWithWebView(context, tempWebView, isPriv, opHost, parentTabId = actualTabId)
                    } catch (e: Exception) {
                        Log.e("TabSessionCallbackHelper", "Error in newTabWithWebViewCallback", e)
                    }
                }
            }
            session.requestCloseCallback = {
                try {
                    val currentList = repository._tabs.value
                    val index = currentList.indexOfFirst { it.id == actualTabId }
                    Log.d("TabSessionCallbackHelper", "requestCloseCallback: actualTabId=$actualTabId, index=$index, tabs=${currentList.map { it.id }}")
                    if (index != -1) {
                        repository.closeTab(index, context)
                        Log.d("TabSessionCallbackHelper", "requestCloseCallback: closeTab done, tabs after=${repository._tabs.value.map { it.id }}")
                    }
                } catch (e: Exception) {
                    Log.e("TabSessionCallbackHelper", "Error in requestCloseCallback", e)
                }
            }
        }

        session.faviconCallback = { favicon ->
            try {
                repository.updateTab(actualTabId) { it.copy(favicon = favicon) }
                Thread {
                    TabStorageHelper.saveBitmapToFile(TabStorageHelper.getFaviconFile(context, actualTabId), favicon, isPng = true)
                }.start()
            } catch (e: Exception) {
                // Ignore — favicon updates are non-critical
            }
        }

        session.thumbnailCaptureCallback = { bitmap ->
            try {
                repository.updateTab(actualTabId) { it.copy(thumbnail = bitmap) }
                Thread {
                    TabStorageHelper.saveBitmapToFile(TabStorageHelper.getThumbnailFile(context, actualTabId), bitmap, isPng = false)
                }.start()
            } catch (e: Exception) {
                // Ignore — thumbnail updates are non-critical
            }
        }

        session.stateCallback = { u, t, p, gb, gf ->
            try {
                if (isPrivate && u.startsWith("http")) {
                    val host = try { android.net.Uri.parse(u).host } catch(e: Exception) { null }
                    if (host != null) {
                        val cleanHost = host.removePrefix("www.").removePrefix("m.").lowercase()
                        if (repository.visitedIncognitoDomains.add(cleanHost)) {
                            repository.appContext?.let { TabStorageHelper.saveVisitedIncognitoDomains(it, repository.visitedIncognitoDomains) }
                        }
                    }
                }
                var changed = false
                var prevUrl = ""
                repository.updateTab(actualTabId) {
                    prevUrl = it.url
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
                    if (!isPrivate && prevUrl != u && u.isNotBlank() && !u.startsWith("yue://") && !u.startsWith("about:")) {
                        com.yue.browser.data.repository.HistoryRepositoryImpl.instance.addHistory(u, t)
                    }
                    if (initialUrl != u) {
                        Thread {
                            TabStorageHelper.getThumbnailFile(context, actualTabId).delete()
                        }.start()
                    }
                    if (!repository.suppressPopupCreation) {
                        repository.autoSave()
                    }
                }
                // Aktifkan tab popup yang pending setelah navigasi pertama sukses
                if (actualTabId in repository.pendingPopupActivation &&
                    u.isNotEmpty() && u != "about:blank" && !u.startsWith("yue://")) {
                    repository.pendingPopupActivation.remove(actualTabId)
                    val tabIndex = repository._tabs.value.indexOfFirst { it.id == actualTabId }
                    if (tabIndex != -1) {
                        repository._activeTabIndex.value = tabIndex
                    }
                }
            } catch (e: Exception) {
                // Ignore — state updates are frequent, don't crash on transient issues
            }
        }
    }
}
