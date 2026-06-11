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
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository
import com.yue.browser.data.repository.HistoryRepositoryImpl
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
    internal val onLanguageDetected: ((String) -> Unit)?
) : BrowserSession {

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

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((Bitmap) -> Unit)? = null

    private var longPressDetector: android.view.GestureDetector? = null
    internal var isDesktopMode = false
    
    private fun readAssetFile(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private val webViewInstance = object : WebView(context) {
        private var isLongPressActive = false

        fun setLongPressActive(active: Boolean) {
            isLongPressActive = active
            if (active) {
                val time = android.os.SystemClock.uptimeMillis()
                val cancelEvent = android.view.MotionEvent.obtain(
                    time, time, android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0
                )
                super.onTouchEvent(cancelEvent)
                cancelEvent.recycle()
            }
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                isLongPressActive = false
            }
            
            longPressDetector?.onTouchEvent(event)
            
            if (isLongPressActive) {
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP || 
                    event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                    isLongPressActive = false
                }
                return true
            }
            
            return super.onTouchEvent(event)
        }
    }

    override val view: View
        get() = webViewInstance

    init {
        AdBlockManager.ensureAdBlockerInitialized(context)

        // Lock to portrait by default — only allow rotation in fullscreen video
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val currentSettings = settingsRepository.settingsFlow.value
        val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
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
        }
        if (isPrivate) {
            webViewInstance.settings.databaseEnabled = false
            webViewInstance.settings.domStorageEnabled = false
        }

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webViewInstance, true)
        // Apply dark mode immediately when session is created!
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
                    // Ambil cookies sesi WebView untuk URL ini (fix 403 karena butuh auth/cookie)
                    val cookies = try {
                        android.webkit.CookieManager.getInstance().getCookie(url)
                    } catch (_: Exception) { null }
                    // Ambil user-agent asli WebView
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

        // Setup gestures
        longPressDetector = android.view.GestureDetector(context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    webViewInstance.setLongPressActive(true)
                    val tapX = e.x
                    val viewW = webViewInstance.width.toFloat().coerceAtLeast(1f)
                    val seekDelta = if (tapX < viewW / 2f) -5 else 5
                    val seekScript = """
                        (function(){
                            var v = null;
                            var vids = document.querySelectorAll('video');
                            for (var i = 0; i < vids.length; i++) {
                                if (!vids[i].paused) {
                                    v = vids[i];
                                    break;
                                }
                            }
                            if (!v) {
                                var container = document.querySelector('.jwplayer, [id*="jwplayer"], [class*="jw-"], .video-js, .plyr');
                                if (container) {
                                    v = container.querySelector('video');
                                }
                            }
                            if (!v && vids.length > 0) {
                                v = vids[0];
                            }
                            if (v) {
                                v.currentTime = Math.max(0, Math.min(
                                    isFinite(v.duration) ? v.duration : 99999,
                                    v.currentTime + $seekDelta
                                ));
                            }
                        })();
                    """.trimIndent()
                    webViewInstance.post { webViewInstance.evaluateJavascript(seekScript, null) }
                }
            }
        )

        var customView: View? = null
        var customViewCallback: WebChromeClient.CustomViewCallback? = null

        webViewInstance.webChromeClient = SystemWebChromeClient(context, this@SystemWebViewSession, settingsRepository, isPrivate)

        webViewInstance.webViewClient = SystemWebViewClient(context, this@SystemWebViewSession, settingsRepository, isPrivate)
    }



    override fun loadUrl(url: String) {
        updateUserAgent(url)
        if (url == "yue://newtab") {
            webViewInstance.loadUrl("about:blank")
        } else {
            val expectedUA = getExpectedUserAgent(url)
            if (expectedUA != null && !url.contains("addons.mozilla.org") && !url.contains("chromewebstore")) {
                val headers = mutableMapOf<String, String>()
                val isWechatOverride = url.contains("weixin") || url.contains("wechat")
                if (isWechatOverride) {
                    headers["X-Requested-With"] = "XMLHttpRequest"
                } else {
                    if (expectedUA.contains("Windows") || expectedUA.contains("Macintosh") || expectedUA.contains("X11")) {
                        headers["Sec-CH-UA-Mobile"] = "?0"
                        headers["Sec-CH-UA-Platform"] = "\"Windows\""
                    } else {
                        headers["Sec-CH-UA-Mobile"] = "?1"
                        headers["Sec-CH-UA-Platform"] = "\"Android\""
                    }
                }
                webViewInstance.loadUrl(url, headers)
            } else {
                webViewInstance.loadUrl(url)
            }
        }
    }

    override fun goBack() {
        webViewInstance.goBack()
    }

    override fun goForward() {
        webViewInstance.goForward()
    }

    override fun reload() {
        updateUserAgent(url)
        webViewInstance.reload()
    }

    override fun destroy() {
        webViewInstance.destroy()
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        webViewInstance.evaluateJavascript(script, callback)
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        webViewInstance.settings.javaScriptEnabled = enabled
    }

    override fun setForceDarkMode(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            webViewInstance.settings.isAlgorithmicDarkeningAllowed = enabled
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            webViewInstance.settings.forceDark = if (enabled) {
                android.webkit.WebSettings.FORCE_DARK_ON
            } else {
                android.webkit.WebSettings.FORCE_DARK_OFF
            }
        }
    }

    override fun setDesktopModeEnabled(enabled: Boolean) {
        isDesktopMode = enabled
        updateUserAgent(this.url)
    }

    internal fun updateUserAgent(currentUrl: String) {
        val expectedUA = getExpectedUserAgent(currentUrl)
        webViewInstance.settings.userAgentString = expectedUA
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

    private var elementPickerCallback: ((String) -> Unit)? = null

    override fun startElementPicker(onElementPicked: (cssSelector: String) -> Unit) {
        elementPickerCallback = onElementPicked
        // Register JS interface for picker
        webViewInstance.post {
            webViewInstance.removeJavascriptInterface("YuePicker")
            webViewInstance.addJavascriptInterface(object {
                @JavascriptInterface
                fun onPicked(cssSelector: String) {
                    val cb = elementPickerCallback ?: return
                    webViewInstance.post {
                        cb(cssSelector)
                        // Immediately hide the picked element by injecting CSS
                        val quotedSelector = org.json.JSONObject.quote(cssSelector)
                        val hideScript = "(function() { try { var style = document.getElementById('__yue_blocked_css__'); if (!style) { style = document.createElement('style'); style.id = '__yue_blocked_css__'; document.head.appendChild(style); } style.textContent += $quotedSelector + ' { display: none !important; visibility: hidden !important; }\\n'; } catch(e) {} })();"
                        webViewInstance.evaluateJavascript(hideScript, null)
                    }
                }
                @JavascriptInterface
                fun onCancelled() {
                    elementPickerCallback = null
                }
            }, "YuePicker")

            webViewInstance.evaluateJavascript(WebViewScriptsVideo.elementPickerScript, null)
        }
    }

    override fun stopElementPicker() {
        elementPickerCallback = null
        webViewInstance.post {
            webViewInstance.evaluateJavascript(
                "(function() { if (window.__yuePicker__) { window.__yuePicker__.stop(); } })();",
                null
            )
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
                    
                    // Aggressive debounce: only enable pull-to-refresh after staying at top for 700ms+
                    // AND must not have scrolled away during that period
                    var enableRunnable: Runnable? = null
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    var lastLeftTopTime: Long = 0
                    val DEBOUNCE_MS = 700L
                    
                    fun scheduleEnable() {
                        enableRunnable?.let { handler.removeCallbacks(it) }
                        lastLeftTopTime = System.currentTimeMillis()
                        enableRunnable = Runnable {
                            val elapsed = System.currentTimeMillis() - lastLeftTopTime
                            // Must have stayed at top for full DEBOUNCE_MS without leaving
                            if (!this.isEnabled && wv.scrollY <= 2 && elapsed >= DEBOUNCE_MS) {
                                this.isEnabled = true
                            } else {
                                this.isEnabled = false
                            }
                        }.also { handler.postDelayed(it, DEBOUNCE_MS) }
                    }
                    
                    fun cancelEnable() {
                        enableRunnable?.let { handler.removeCallbacks(it) }
                        enableRunnable = null
                        if (this.isEnabled) this.isEnabled = false
                    }

                    // Enforce bottom padding
                    // val density = wv.context.resources.displayMetrics.density
                    // val paddingPx = (80 * density).toInt()
                    // wv.clipToPadding = false
                    // wv.setPadding(0, 0, 0, paddingPx)
                    
                    // Setup YueScroll JavaScriptInterface
                    wv.removeJavascriptInterface("YueScroll")
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onScrollChanged(visible: Boolean) {
                            wv.post {
                                currentOnScrollChanged(visible)
                            }
                        }
                    }, "YueScroll")

                    // Setup Scroll listener — lightweight, only act on state transitions
                    var isAtTop = true
                    wv.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        val nowAtTop = scrollY <= 2
                        // Only act when state changes (avoid per-frame overhead)
                        if (nowAtTop != isAtTop) {
                            isAtTop = nowAtTop
                            if (nowAtTop) {
                                scheduleEnable()
                                currentOnScrollChanged(true)
                            } else {
                                cancelEnable()
                                currentOnScrollChanged(false)
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
                        wv.scrollY > 5
                    }
                    // Require very large drag distance to trigger refresh
                    setDistanceToTriggerSync((400 * ctx.resources.displayMetrics.density).toInt())
                    setSlingshotDistance((250 * ctx.resources.displayMetrics.density).toInt())
                    setProgressViewOffset(false, 0, (80 * ctx.resources.displayMetrics.density).toInt())
                    isEnabled = false
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
                // Note: isEnabled is managed by debounce logic in scroll listener, not here
            },
            modifier = modifier.graphicsLayer {
                clip = true
            }
        )
    }


}
