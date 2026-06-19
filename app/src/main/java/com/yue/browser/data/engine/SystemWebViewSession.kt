package com.yue.browser.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
class SystemWebViewSession(
    private val context: Context,
    override val id: String,
    override val isPrivate: Boolean,
    private val settingsRepository: SettingsRepository,
    internal val onLanguageDetected: ((String) -> Unit)?,
    private val preExistingWebView: WebView? = null
) : BrowserSession {

    companion object {
        private val activePrivateSessions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        /**
         * Bersihkan semua cookies (global). Dipanggil dari Settings > Clear Data,
         * BUKAN dari incognito cleanup — karena WebView cuma punya 1 cookie store
         * global, nge-clear cookies incognito berarti ngehapus juga cookie normal.
         * Incognito cuma bersihin localStorage/sessionStorage per-session via JS.
         */
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

    // SPA history tracking: tracks pushState depth reported from JavaScript
    // combinedWebCanGoBack/Forward: includes WebView native + SPA depth
    @Volatile
    private var spaDepth: Int = 0

    internal fun combinedCanGoBack(): Boolean = canGoBack || spaDepth > 0
    internal fun combinedCanGoForward(): Boolean = canGoForward || spaDepth < 0
    internal fun resetSpaDepth() { spaDepth = 0 }

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((Bitmap) -> Unit)? = null
    override var newTabWithWebViewCallback: ((WebView, Boolean, String) -> Unit)? = null
    override var requestCloseCallback: (() -> Unit)? = null
    var openerHost: String? = null

    internal var isDesktopMode = false
    internal var isDeliberateNewTab = false
    internal var lastOverrideTime: Long = 0
    internal var lastOverrideUrl: String = ""
    // Tracks the URL where onReceivedHttpError fired (e.g. 403).
    // Used in onReceivedError to distinguish a real server error from
    // an abort we caused ourselves via loadUrl() + return true.
    internal var lastHttpErrorUrl: String = ""
    // Tracks the last URL that was auto-retried after a transient server error
    // (e.g. Cloudflare 403). We only retry once to avoid infinite loops.
    internal var lastAutoRetryUrl: String = ""

    private fun readAssetFile(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private val webViewInstance = preExistingWebView ?: WebView(context)

    override val view: View
        get() = webViewInstance

    @Volatile
    var isDestroyed: Boolean = false
        private set


    init {
        if (isPrivate) {
            activePrivateSessions.add(id)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                try {
                    val profileStore = ProfileStore.getInstance()
                    profileStore.getOrCreateProfile("incognito_profile")
                    WebViewCompat.setProfile(webViewInstance, "incognito_profile")
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebViewSession", "Failed to set private profile on WebView", e)
                }
            }
        }
        val currentSettings = settingsRepository.settingsFlow.value
        val initialUA = UserAgentManager.getExpectedUserAgent("", false, currentSettings)

        AdBlockManager.ensureAdBlockerInitialized(context)

        val activity = context as? android.app.Activity
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val isDarkActive = currentSettings.isDarkModeSimulated || currentSettings.enabledAddons.contains("darkreader")

        val bgColor = if (isDarkActive) android.graphics.Color.parseColor("#000000") else android.graphics.Color.parseColor("#FFFFFF")
        webViewInstance.setBackgroundColor(bgColor)

        webViewInstance.settings.apply {
            javaScriptEnabled = currentSettings.isJavaScriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(true)
            setJavaScriptCanOpenWindowsAutomatically(false)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            userAgentString = initialUA
            // === BROWSER-LIKE SETTINGS (penting untuk Cloudflare/anti-bot) ===
            // WebView dengan setting "seperti Chrome" lebih mudah lolos challenge.
            setGeolocationEnabled(false)
            textZoom = 100
            setSupportZoom(currentSettings.isZoomEnabled)
            builtInZoomControls = currentSettings.isZoomEnabled
            displayZoomControls = false
            // defaultTextEncodingName: UTF-8 (default sudah benar)
        }

        if (isPrivate) {
            webViewInstance.settings.databaseEnabled = true
            webViewInstance.settings.domStorageEnabled = true
        }

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webViewInstance, true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            webViewInstance.settings.isAlgorithmicDarkeningAllowed = isDarkActive
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            webViewInstance.settings.forceDark = if (isDarkActive) {
                android.webkit.WebSettings.FORCE_DARK_ON
            } else {
                android.webkit.WebSettings.FORCE_DARK_OFF
            }
        }
        webViewInstance.addJavascriptInterface(SystemWebViewAddonsInterface(context, this@SystemWebViewSession, settingsRepository), "YueAddons")
        webViewInstance.addJavascriptInterface(SystemWebViewMediaSessionInterface(context, this@SystemWebViewSession), "YueMediaSession")
        webViewInstance.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onFormDetected(json: String) {
                passwordDetectedFieldsJson = json
                webViewInstance.post {
                    onPasswordFieldsDetectedCallback?.invoke(json)
                }
            }
            @android.webkit.JavascriptInterface
            fun onFormSubmitted(json: String) {
                webViewInstance.post {
                    onPasswordFormSubmittedCallback?.invoke(json)
                }
            }
            @android.webkit.JavascriptInterface
            fun onAutofillDismissed() {
                webViewInstance.post {
                    onAutofillDismissedCallback?.invoke()
                }
            }
        }, "YuePasswordDetect")

        webViewInstance.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onStateChanged() {
                webViewInstance.post {
                    try {
                        val currentUrl = webViewInstance.url ?: ""
                        val canGoBackVal = webViewInstance.canGoBack()
                        val canGoForwardVal = webViewInstance.canGoForward()
                        val isHistoryNav = canGoBackVal || canGoForwardVal
                        if (currentUrl.isNotEmpty() && (currentUrl != "about:blank" || isDeliberateNewTab || isHistoryNav)) {
                            val normalizedUrl = if (currentUrl == "about:blank") "yue://newtab" else currentUrl
                            url = normalizedUrl
                            title = webViewInstance.title ?: ""
                            canGoBack = canGoBackVal
                            canGoForward = canGoForwardVal

                            stateCallback?.invoke(
                                normalizedUrl,
                                title,
                                progress,
                                combinedCanGoBack(),
                                combinedCanGoForward()
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SystemWebViewSession", "Error in YueState.onStateChanged", e)
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun onSpaDepthChanged(depth: Int) {
                webViewInstance.post {
                    try {
                        spaDepth = depth
                        val currentUrl = webViewInstance.url ?: ""
                        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                            stateCallback?.invoke(
                                url,
                                title,
                                progress,
                                combinedCanGoBack(),
                                combinedCanGoForward()
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SystemWebViewSession", "Error in YueState.onSpaDepthChanged", e)
                    }
                }
            }
        }, "YueState")

        webViewInstance.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val actualAddonId = when {
                url.contains("ublock") || url.contains("cjpalhdlnbpafiamejdnhcphjbkeiagm") -> "ublock"
                url.contains("darkreader") || url.contains("eimadpcaloflhjddepbbgoikcjaggafg") -> "darkreader"
                url.contains("translator") || url.contains("mchibihcapipjolgdaiegimacnlaaldg") -> "translator"
                else -> null
            }
            if (actualAddonId != null) {
                GlobalScope.launch(Dispatchers.Main) {
                    settingsRepository.setAddonEnabled(actualAddonId, true)
                    val name = when (actualAddonId) {
                        "ublock" -> "uBlock Origin Lite"
                        "darkreader" -> "Dark Reader"
                        "translator" -> "Page Translator"
                        else -> "Add-on"
                    }
                    android.widget.Toast.makeText(context, "$name berhasil dipasang!", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                try {
                    val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                    val cookies = try {
                        android.webkit.CookieManager.getInstance().getCookie(url)
                    } catch (_: Exception) { null }
                    val webViewUA = try {
                        webViewInstance.settings.userAgentString
                    } catch (_: Exception) { null }
                    com.yue.browser.data.repository.DownloadRepositoryImpl.instance.let { repo ->
                        repo.initialize(context)
                        repo.setGlobalWebViewUserAgent(webViewUA ?: userAgent)
                        repo.startDownload(url, fileName, context, 4, cookies, webViewUA ?: userAgent)
                    }
                    android.widget.Toast.makeText(context, "Memulai download: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }



        var customView: View? = null
        var customViewCallback: WebChromeClient.CustomViewCallback? = null

        webViewInstance.webChromeClient = SystemWebChromeClient(context, this@SystemWebViewSession, settingsRepository, isPrivate)

        webViewInstance.webViewClient = SystemWebViewClient(context, this@SystemWebViewSession, settingsRepository, isPrivate)
    }



    // === loadUrl: SET USER-AGENT DULU, baru load dengan headers lengkap ===
    // Urutan KRITIS:
    // 1. webView.settings.userAgentString = UA_CHROME → berlaku untuk SEMUA request
    // 2. webView.loadUrl(url, extraHeaders) → tambahan headers main frame
    //
    // Tanpa step #1, UA di sub-resource (favicon, CSS, JS, gambar) masih
    // bisa berisi UA default (dengan "; wv") → situs blokir sub-resource → halaman rusak.
    override fun loadUrl(url: String) {
        updateUserAgent(url) // Step 1: Set UA di WebView settings (GLOBAL)
        if (url == "yue://newtab") {
            isDeliberateNewTab = true
            webViewInstance.loadUrl("about:blank")
        } else {
            isDeliberateNewTab = false
            // Step 2: Build headers main frame (Accept, Accept-Language, Sec-Fetch-*, X-Requested-With)
            val extraHeaders = buildMainFrameHeaders()
            try {
                webViewInstance.loadUrl(url, extraHeaders)
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "loadUrl with headers failed, fallback to plain loadUrl", e)
                webViewInstance.loadUrl(url)
            }
        }
    }

    override fun goBack() {
        isDeliberateNewTab = false
        updateUserAgent(webViewInstance.url ?: "")
        webViewInstance.goBack()
    }

    override fun goForward() {
        isDeliberateNewTab = false
        updateUserAgent(webViewInstance.url ?: "")
        webViewInstance.goForward()
    }

    override fun tryBackPress(): Boolean {
        if (isDestroyed) return false
        // First try WebView native back (handles pushState and full navigations)
        if (canGoBack) {
            goBack()
            return true
        }
        // Fallback: try JavaScript history.back() for SPA pages
        // where WebView's internal canGoBack doesn't detect pushState
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
        if (canGoForward) {
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
        updateUserAgent(url)
        // Untuk reload: gunakan getReloadHeaders() yang punya Cache-Control: max-age=0
        // dan Sec-Fetch-Site=same-origin (mirip Chrome saat user tekan F5).
        try {
            val extraHeaders = buildMainFrameHeaders(reload = true)
            webViewInstance.loadUrl(webViewInstance.url ?: url, extraHeaders)
        } catch (e: Exception) {
            webViewInstance.reload()
        }
    }

    private fun buildMainFrameHeaders(reload: Boolean = false): Map<String, String> {
        val headers = HashMap<String, String>()
        try {
            // === OVERRIDE USER-AGENT di HTTP HEADER LEVEL JUGA ===
            // Beberapa situs membaca User-Agent dari HTTP header (bukan dari navigator.userAgent).
            // Kita pastikan nilainya sama dengan UA Chrome yang di-set di webView.settings.
            val currentSettings = settingsRepository.settingsFlow.value
            val currentUA = UserAgentManager.getExpectedUserAgent(
                this@SystemWebViewSession.url,
                isDesktopMode,
                currentSettings
            )
            headers["User-Agent"] = currentUA
            if (reload) {
                headers.putAll(UserAgentManager.getReloadHeaders(isDesktopMode))
            } else {
                headers.putAll(UserAgentManager.getDefaultHeaders(isDesktopMode))
            }
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewSession", "buildMainFrameHeaders failed", e)
        }
        return headers
    }

    override fun destroy() {
        isDestroyed = true
        if (isPrivate) {
            activePrivateSessions.remove(id)
            // Hapus localStorage/sessionStorage untuk session ini via JS
            try {
                webViewInstance.evaluateJavascript(
                    "try { localStorage.clear(); sessionStorage.clear(); } catch(e) {};",
                    null
                )
            } catch (_: Exception) { }
            // NOTE: Cookie cleanup tidak dilakukan di sini karena WebView punya
            // 1 cookie store global. Clear cookies dari sini akan ngehapus
            // juga cookie tab normal. Cookie cuma dibersihkan dari
            // TabRepositoryImpl setelah mastiin nggak ada tab normal tersisa.
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

    // === UPDATE USER-AGENT: GLOBAL, berlaku untuk SEMUA request (main frame + sub-resource) ===
    internal fun updateUserAgent(currentUrl: String) {
        val expectedUA = getExpectedUserAgent(currentUrl)
        if (webViewInstance.settings.userAgentString != expectedUA) {
            webViewInstance.settings.userAgentString = expectedUA
        }
        webViewInstance.settings.useWideViewPort = true
        webViewInstance.settings.loadWithOverviewMode = true
    }

    private fun getExpectedUserAgent(currentUrl: String): String {
        return UserAgentManager.getExpectedUserAgent(currentUrl, isDesktopMode, settingsRepository.settingsFlow.value)
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
    private var elementPickerCallback: ((List<String>) -> Unit)? = null
    @Volatile
    private var elementPickerCancelCallback: (() -> Unit)? = null

    @Volatile
    var passwordDetectedFieldsJson: String? = null
    var onPasswordFieldsDetectedCallback: ((String) -> Unit)? = null
    var onPasswordFormSubmittedCallback: ((String) -> Unit)? = null
    var onAutofillDismissedCallback: (() -> Unit)? = null

    override fun startElementPicker(onElementsPicked: (cssSelectors: List<String>) -> Unit, onCancel: () -> Unit, isDark: Boolean) {
        elementPickerCallback = onElementsPicked
        elementPickerCancelCallback = onCancel
        webViewInstance.post {
            val labelHapus = context.getString(com.yue.browser.R.string.picker_hapus)
            val labelSelected = context.getString(com.yue.browser.R.string.picker_selected_count)
            val labelHint = context.getString(com.yue.browser.R.string.picker_hint)
            webViewInstance.evaluateJavascript(WebViewScriptsVideo.elementPickerScript(isDark, labelHapus, labelSelected, labelHint), null)
        }
    }

    override fun stopElementPicker() {
        elementPickerCallback = null
        elementPickerCancelCallback = null
        webViewInstance.post {
            webViewInstance.evaluateJavascript(
                "(function() { if (window.__yuePicker__) { window.__yuePicker__.stop(); } })();",
                null
            )
        }
    }

    internal fun handleElementPickerSubmit(selectorsJson: String) {
        val cb = elementPickerCallback ?: return
        try {
            val arr = org.json.JSONArray(selectorsJson)
            val selectors = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                selectors.add(arr.getString(i))
            }
            webViewInstance.post {
                cb(selectors)
                val combined = selectors.joinToString(", ")
                val escaped = org.json.JSONObject.quote(combined)
                val hideScript = "(function() { try { var style = document.getElementById('__yue_blocked_css__'); if (!style) { style = document.createElement('style'); style.id = '__yue_blocked_css__'; document.head.appendChild(style); } style.textContent += $escaped + ' { display: none !important; visibility: hidden !important; }\\n'; } catch(e) {} })();"
                webViewInstance.evaluateJavascript(hideScript, null)
                stopElementPicker()
            }
        } catch (e: Exception) {
            android.util.Log.e("ElementPicker", "Error parsing selector JSON", e)
        }
    }

    internal fun handleElementPickerCancel() {
        val cancel = elementPickerCancelCallback
        webViewInstance.post {
            cancel?.invoke()
        }
        stopElementPicker()
    }

    /**
     * Re-inject cosmetic filters (CSS hide rules) into this WebView using the
     * latest settings. Called by BrowserViewModel after the user blocks an element,
     * so the element disappears in all open tabs immediately without a page refresh.
     */
    fun reinjectCosmeticFilters(settings: com.yue.browser.domain.model.BrowserSettings) {
        val currentUrl = url
        if (currentUrl.isBlank() || currentUrl.startsWith("yue://") || currentUrl.startsWith("data:")) return
        webViewInstance.post {
            try {
                com.yue.browser.data.engine.AdBlockManager.injectCosmeticFilters(
                    context, webViewInstance, currentUrl, settings
                )
            } catch (e: Exception) {
                android.util.Log.e("SystemWebViewSession", "Error re-injecting cosmetic filters", e)
            }
        }
    }

    @Composable
    override fun Render(
        modifier: Modifier,
        onScrollChanged: (visible: Boolean) -> Unit,
        onReload: () -> Unit,
        isGone: Boolean
    ) {
        val currentOnScrollChanged by rememberUpdatedState(onScrollChanged)
        AndroidView(
            factory = { ctx ->
                androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                    val wv = webViewInstance
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)

                    var startedAtTop = false
                    wv.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                val isInTopOneThird = event.y < wv.height / 3f
                                startedAtTop = isInTopOneThird && !wv.canScrollVertically(-1)
                            }
                        }
                        false
                    }

                    wv.removeJavascriptInterface("YueScroll")
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onScrollChanged(visible: Boolean) {
                            wv.post {
                                currentOnScrollChanged(visible)
                            }
                        }
                    }, "YueScroll")

                    var isNavVisible = true
                    val scrollThreshold = 15
                    wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                        val diff = scrollY - oldScrollY
                        if (scrollY <= 2) {
                            if (!isNavVisible) {
                                isNavVisible = true
                                currentOnScrollChanged(true)
                            }
                        } else if (Math.abs(diff) > scrollThreshold) {
                            if (diff > 0 && scrollY > 50) {
                                if (isNavVisible) {
                                    isNavVisible = false
                                    currentOnScrollChanged(false)
                                }
                            } else if (diff < 0) {
                                if (!isNavVisible) {
                                    isNavVisible = true
                                    currentOnScrollChanged(true)
                                }
                            }
                        }
                    }

                    addView(wv, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    setOnRefreshListener {
                        onReload()
                    }
                    setOnChildScrollUpCallback { _, _ ->
                        !startedAtTop || wv.canScrollVertically(-1)
                    }
                    setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
                    setSlingshotDistance((80 * ctx.resources.displayMetrics.density).toInt())
                    setProgressViewOffset(false, 0, (40 * ctx.resources.displayMetrics.density).toInt())
                    isEnabled = true
                }
            },
            update = { swipeRefreshLayout ->
                if (progress >= 100 && swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }

                val targetVisibility = if (isGone) View.GONE else View.VISIBLE
                if (swipeRefreshLayout.visibility != targetVisibility) {
                    swipeRefreshLayout.visibility = targetVisibility
                }
            },
            modifier = modifier.graphicsLayer {
                clip = true
            }
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
}
