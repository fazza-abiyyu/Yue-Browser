package com.yue.browser.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository
import java.util.Collections

class SystemWebViewSession(
    internal val context: Context,
    override val id: String,
    override val isPrivate: Boolean,
    internal val settingsRepository: SettingsRepository,
    internal val onLanguageDetected: ((String) -> Unit)?,
    private val preExistingWebView: WebView? = null
) : BrowserSession {

    companion object {
        private val activePrivateSessions = Collections.synchronizedSet(mutableSetOf<String>())

        fun clearAllCookies() {
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.removeAllCookies {
                        cookieManager.flush()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    cookieManager.removeAllCookie()
                }
            } catch (_: Exception) { }
        }
    }

    override var url: String = if (isPrivate) "yue://newtab" else ""
        internal set
    override var title: String = "New Tab"
        internal set
    override var progress: Int = 0
        internal set
    override var canGoBack: Boolean = false
        internal set
    override var canGoForward: Boolean = false
        internal set

    @Volatile
    internal var spaDepth: Int = 0

    override val combinedCanGoBack: Boolean get() {
        val bgb = bflCanGoBack()
        val result = canGoBack || spaDepth > 0 || bgb
        android.util.Log.d("NavState", "combinedCanGoBack: canGoBack=$canGoBack spaDepth=$spaDepth bflCanGoBack=$bgb => $result")
        return result
    }
    override val combinedCanGoForward: Boolean get() {
        val bgf = bflCanGoForward()
        val result = canGoForward || spaDepth < 0 || bgf
        android.util.Log.d("NavState", "combinedCanGoForward: canGoForward=$canGoForward spaDepth=$spaDepth bflCanGoForward=$bgf => $result")
        return result
    }
    internal fun resetSpaDepth() { spaDepth = 0 }

    internal fun bflCanGoBack(): Boolean {
        return try {
            val bfl = webViewInstance.copyBackForwardList()
            val idx = bfl.currentIndex
            android.util.Log.d("NavState", "bflCanGoBack: size=${bfl.size} currentIndex=$idx => ${idx > 0}")
            idx > 0
        } catch (e: Exception) {
            android.util.Log.e("NavState", "bflCanGoBack error", e)
            false
        }
    }

    internal fun bflCanGoForward(): Boolean {
        return try {
            val bfl = webViewInstance.copyBackForwardList()
            val idx = bfl.currentIndex
            val sz = bfl.size
            android.util.Log.d("NavState", "bflCanGoForward: size=$sz currentIndex=$idx => ${idx < sz - 1}")
            idx < sz - 1
        } catch (e: Exception) {
            android.util.Log.e("NavState", "bflCanGoForward error", e)
            false
        }
    }

    internal fun updateNavigationState(view: WebView) {
        val bfl = view.copyBackForwardList()
        val rawBack = view.canGoBack()
        val bflBack = bfl.currentIndex > 0
        val rawForward = view.canGoForward()
        val bflForward = bfl.currentIndex < bfl.size - 1
        canGoBack = rawBack || bflBack
        canGoForward = rawForward || bflForward
        android.util.Log.d("NavState", "updateNavigationState: rawBack=$rawBack bflBack=$bflBack set=canGoBack=$canGoBack | rawForward=$rawForward bflForward=$bflForward set=canGoForward=$canGoForward")
    }

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((Bitmap) -> Unit)? = null
    override var newTabWithWebViewCallback: ((WebView, Boolean, String) -> Unit)? = null
    override var requestCloseCallback: (() -> Unit)? = null
    var openerHost: String? = null
    var isScriptPopup: Boolean = false

    internal var isDesktopMode = false
    internal var isDeliberateNewTab = false
    internal var isAppNavigation = false
    internal var lastOverrideTime: Long = 0
    internal var lastOverrideUrl: String = ""
    internal var lastHttpErrorUrl: String = ""
    internal var lastAutoRetryUrl: String = ""
    internal var lastFailedUrl: String? = null
    internal var isShowingErrorPage: Boolean = false
    internal var hasReceivedErrorForCurrentLoad: Boolean = false

    internal val webViewInstance = preExistingWebView ?: object : WebView(context) {
        override fun onWindowVisibilityChanged(visibility: Int) {
            val currentSettings = settingsRepository.settingsFlow.value
            val isBgPlayEnabled = if (isPrivate) {
                currentSettings.isBackgroundPlayEnabledPrivate
            } else {
                currentSettings.isBackgroundPlayEnabledNormal
            }
            if (isBgPlayEnabled) {
                super.onWindowVisibilityChanged(View.VISIBLE)
            } else {
                super.onWindowVisibilityChanged(visibility)
            }
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            val currentSettings = settingsRepository.settingsFlow.value
            val isBgPlayEnabled = if (isPrivate) {
                currentSettings.isBackgroundPlayEnabledPrivate
            } else {
                currentSettings.isBackgroundPlayEnabledNormal
            }
            if (isBgPlayEnabled) {
                super.onVisibilityChanged(changedView, View.VISIBLE)
            } else {
                super.onVisibilityChanged(changedView, visibility)
            }
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            val currentSettings = settingsRepository.settingsFlow.value
            val isBgPlayEnabled = if (isPrivate) {
                currentSettings.isBackgroundPlayEnabledPrivate
            } else {
                currentSettings.isBackgroundPlayEnabledNormal
            }
            if (isBgPlayEnabled) {
                super.onWindowFocusChanged(true)
            } else {
                super.onWindowFocusChanged(hasWindowFocus)
            }
        }
    }

    internal var settingsJob: kotlinx.coroutines.Job? = null

    override val view: View
        get() = webViewInstance

    @Volatile
    internal var isDestroyed: Boolean = false

    init {
        if (isPrivate) {
            activePrivateSessions.add(id)
            setupPrivateProfile()
        }
        val currentSettings = settingsRepository.settingsFlow.value
        val initialUA = getExpectedUserAgent("")

        observeSettingsChanges()
        setupJavaScriptInterfaces()
        setupWebClients()
        configureWebViewSettings(currentSettings, initialUA)
        setupDownloadListener()
        setupDocumentStartScripts(currentSettings)
    }

    override fun loadUrl(url: String) {
        lastFailedUrl = null
        isShowingErrorPage = false
        hasReceivedErrorForCurrentLoad = false
        val upgradedUrl = if (settingsRepository.settingsFlow.value.isHttpsOnlyModeEnabled && url.startsWith("http://")) {
            "https://" + url.substring(7)
        } else {
            url
        }
        updateUserAgent(upgradedUrl)
        if (upgradedUrl == "yue://newtab") {
            isDeliberateNewTab = true
            webViewInstance.loadUrl("about:blank")
        } else {
            isDeliberateNewTab = false
            isAppNavigation = true
            val extraHeaders = buildMainFrameHeaders(targetUrl = upgradedUrl)
            try {
                webViewInstance.loadUrl(upgradedUrl, extraHeaders)
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "loadUrl with headers failed, fallback to plain loadUrl", e)
                webViewInstance.loadUrl(upgradedUrl)
            }
        }
    }

    override fun goBack() {
        lastFailedUrl = null
        isShowingErrorPage = false
        hasReceivedErrorForCurrentLoad = false
        isDeliberateNewTab = false
        updateUserAgent(webViewInstance.url ?: "")
        webViewInstance.goBack()
    }

    override fun goForward() {
        lastFailedUrl = null
        isShowingErrorPage = false
        hasReceivedErrorForCurrentLoad = false
        isDeliberateNewTab = false
        updateUserAgent(webViewInstance.url ?: "")
        webViewInstance.goForward()
    }

    override fun tryBackPress(): Boolean {
        if (isDestroyed) return false
        if (canGoBack || bflCanGoBack()) {
            goBack()
            return true
        }
        if (spaDepth > 0) {
            webViewInstance.post {
                try {
                    webViewInstance.evaluateJavascript(WebViewScripts.getSpaBackScript(), null)
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebViewSession", "Error in tryBackPress JS fallback", e)
                }
            }
            spaDepth = (spaDepth - 1).coerceAtLeast(0)
            return true
        }
        return false
    }

    override fun tryForwardPress(): Boolean {
        if (isDestroyed) return false
        if (canGoForward || bflCanGoForward()) {
            goForward()
            return true
        }
        if (spaDepth < 0) {
            webViewInstance.post {
                try {
                    webViewInstance.evaluateJavascript(WebViewScripts.getSpaForwardScript(), null)
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebViewSession", "Error in tryForwardPress JS fallback", e)
                }
            }
            spaDepth = (spaDepth + 1).coerceAtMost(0)
            return true
        }
        return false
    }

    override fun reload() {
        isDeliberateNewTab = false
        val failedUrl = lastFailedUrl
        val target = if (!failedUrl.isNullOrEmpty()) failedUrl else (webViewInstance.url ?: url)
        updateUserAgent(target)
        try {
            val extraHeaders = buildMainFrameHeaders(targetUrl = target, reload = true)
            webViewInstance.loadUrl(target, extraHeaders)
        } catch (e: Exception) {
            webViewInstance.reload()
        }
    }

    override fun destroy() {
        isDestroyed = true
        settingsJob?.cancel()
        if (isPrivate) {
            activePrivateSessions.remove(id)
            try {
                webViewInstance.evaluateJavascript(
                    "try { localStorage.clear(); sessionStorage.clear(); } catch(e) {};",
                    null
                )
            } catch (_: Exception) { }
        }
        try {
            webViewInstance.stopLoading()
        } catch (_: Exception) { /* ignore */ }
        try {
            webViewInstance.removeAllViews()
        } catch (_: Exception) { /* ignore */ }
        MediaSessionManager.releaseSession(context, id)
        webViewInstance.destroy()
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (isDestroyed) return
        webViewInstance.post {
            if (isDestroyed) return@post
            try {
                webViewInstance.evaluateJavascript(script, callback)
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "Error in evaluateJavascript: $script", e)
            }
        }
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        webViewInstance.settings.javaScriptEnabled = enabled
    }

    override fun setForceDarkMode(enabled: Boolean) {
        val darkActive = enabled
        val bgColor = if (darkActive) android.graphics.Color.parseColor("#000000") else android.graphics.Color.parseColor("#FFFFFF")
        webViewInstance.post {
            if (isDestroyed) return@post
            try {
                webViewInstance.setBackgroundColor(bgColor)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    webViewInstance.settings.isAlgorithmicDarkeningAllowed = darkActive
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    webViewInstance.settings.forceDark = if (darkActive) {
                        android.webkit.WebSettings.FORCE_DARK_ON
                    } else {
                        android.webkit.WebSettings.FORCE_DARK_OFF
                    }
                }
                webViewInstance.postInvalidate()

                val script = if (darkActive) {
                    """
                    (function() {
                        try {
                            if (!document.documentElement) return;
                            var style = document.querySelector('style[data-yue-dark-bg]');
                            if (!style) {
                                var s = document.createElement('style');
                                s.setAttribute('data-yue-dark-bg', '1');
                                s.textContent = 'html, body { background-color: #000 !important; }';
                                document.documentElement.appendChild(s);
                            }
                        } catch(e) {}
                    })();
                    """.trimIndent()
                } else {
                    """
                    (function() {
                        try {
                            var style = document.querySelector('style[data-yue-dark-bg]');
                            if (style) {
                                style.parentNode.removeChild(style);
                            }
                        } catch(e) {}
                    })();
                    """.trimIndent()
                }
                webViewInstance.evaluateJavascript(script, null)
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "Error in setForceDarkMode post", e)
            }
        }
    }

    override fun setDesktopModeEnabled(enabled: Boolean) {
        isDesktopMode = enabled
        updateUserAgent(this.url)
    }

    override fun captureThumbnail(callback: (Bitmap) -> Unit) {
        webViewInstance.post {
            try {
                val w = webViewInstance.width
                val h = webViewInstance.height
                if (w > 0 && h > 0) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    webViewInstance.draw(canvas)
                    callback(bitmap)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @Volatile
    internal var elementPickerCallback: ((List<String>) -> Unit)? = null
    @Volatile
    internal var elementPickerCancelCallback: (() -> Unit)? = null

    @Volatile
    var passwordDetectedFieldsJson: String? = null
    var onPasswordFieldsDetectedCallback: ((String) -> Unit)? = null
    var onPasswordFormSubmittedCallback: ((String) -> Unit)? = null
    var onAutofillDismissedCallback: (() -> Unit)? = null

    override fun startElementPicker(
        onElementsPicked: (cssSelectors: List<String>) -> Unit,
        onCancel: () -> Unit,
        isDark: Boolean
    ) {
        startElementPickerHelper(onElementsPicked, onCancel, isDark)
    }

    override fun stopElementPicker() {
        stopElementPickerHelper()
    }

    @Composable
    override fun Render(
        modifier: Modifier,
        onScrollChanged: (visible: Boolean) -> Unit,
        onReload: () -> Unit,
        isGone: Boolean,
        onTouch: () -> Unit
    ) {
        SystemWebViewRenderer(
            webViewInstance = webViewInstance,
            progress = progress,
            isGone = isGone,
            modifier = modifier,
            onScrollChanged = onScrollChanged,
            onReload = onReload,
            onTouch = onTouch
        )
    }

    override fun isJavaScriptEnabled(): Boolean {
        return webViewInstance.settings.javaScriptEnabled
    }

    override fun isDesktopModeEnabled(): Boolean {
        return isDesktopMode
    }

    override fun setZoomEnabled(enabled: Boolean) {
        webViewInstance.settings.apply {
            setSupportZoom(enabled)
            builtInZoomControls = enabled
            displayZoomControls = false
        }
    }

    override fun getTextZoom(): Int {
        return webViewInstance.settings.textZoom
    }

    override fun setTextZoom(zoomPercent: Int) {
        webViewInstance.settings.textZoom = zoomPercent
    }

    fun reinjectCosmeticFilters(settings: com.yue.browser.domain.model.BrowserSettings) {
        val currentUrl = url
        if (currentUrl.isBlank() || currentUrl.startsWith("yue://") || currentUrl.startsWith("data:")) return
        webViewInstance.post {
            try {
                AdBlockManager.injectCosmeticFilters(
                    context, webViewInstance, currentUrl, settings
                )
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "Error re-injecting cosmetic filters", e)
            }
        }
    }

    fun getBackHistory(): List<HistoryItemInfo> {
        val webView = webViewInstance
        val list = webView.copyBackForwardList()
        val currentIndex = list.currentIndex
        val history = mutableListOf<HistoryItemInfo>()
        for (i in currentIndex - 1 downTo 0) {
            val item = list.getItemAtIndex(i) ?: continue
            history.add(HistoryItemInfo(item.title ?: item.url, item.url, i - currentIndex))
        }
        return history
    }

    fun getForwardHistory(): List<HistoryItemInfo> {
        val webView = webViewInstance
        val list = webView.copyBackForwardList()
        val currentIndex = list.currentIndex
        val size = list.size
        val history = mutableListOf<HistoryItemInfo>()
        for (i in currentIndex + 1 until size) {
            val item = list.getItemAtIndex(i) ?: continue
            history.add(HistoryItemInfo(item.title ?: item.url, item.url, i - currentIndex))
        }
        return history
    }

    fun navigateToHistoryItem(steps: Int) {
        if (steps != 0) {
            isDeliberateNewTab = false
            updateUserAgent(webViewInstance.url ?: "")
            webViewInstance.goBackOrForward(steps)
        }
    }

    data class HistoryItemInfo(val title: String, val url: String, val steps: Int)
}
