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
    private val onLanguageDetected: ((String) -> Unit)?
) : BrowserSession {

    override var url: String = if (isPrivate) "yue://newtab" else ""
        private set
    override var title: String = "New Tab"
        private set
    override var progress: Int = 0
        private set
    override var canGoBack: Boolean = false
        private set
    override var canGoForward: Boolean = false
        private set

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((Bitmap) -> Unit)? = null

    private var longPressDetector: android.view.GestureDetector? = null
    private var isDesktopMode = false
    
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
        ensureAdBlockerInitialized(context)

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

        webViewInstance.addJavascriptInterface(AddonsJSInterface(), "YueAddons")

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

        webViewInstance.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val newUrl = view?.url ?: ""
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                this@SystemWebViewSession.progress = newProgress
                this@SystemWebViewSession.canGoBack = view?.canGoBack() ?: false
                this@SystemWebViewSession.canGoForward = view?.canGoForward() ?: false
                
                stateCallback?.invoke(
                    normalizedUrl,
                    this@SystemWebViewSession.title,
                    this@SystemWebViewSession.progress,
                    this@SystemWebViewSession.canGoBack,
                    this@SystemWebViewSession.canGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                if (newProgress > 40 && isAdBlockActive) {
                    injectCosmeticFilters(view, view?.url, currentSettings)
                }
                if (newProgress > 60) {
                    view?.evaluateJavascript(doubleTapScript, null)
                }
            }

            override fun onReceivedTitle(view: WebView?, t: String?) {
                val newUrl = view?.url ?: ""
                this@SystemWebViewSession.title = if (newUrl == "yue://newtab" || newUrl.isBlank() || newUrl == "about:blank") {
                    "New Tab"
                } else {
                    t ?: "Yue Browser"
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                stateCallback?.invoke(
                    normalizedUrl,
                    this@SystemWebViewSession.title,
                    this@SystemWebViewSession.progress,
                    this@SystemWebViewSession.canGoBack,
                    this@SystemWebViewSession.canGoForward
                )
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (icon != null) {
                    faviconCallback?.invoke(icon)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                val activity = context as? android.app.Activity ?: return
                
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                customView = view
                customViewCallback = callback
                
                webViewInstance.visibility = View.GONE
                
                val decorView = activity.window.decorView as? android.view.ViewGroup
                decorView?.addView(customView, android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ))
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                val activity = context as? android.app.Activity ?: return
                val decorView = activity.window.decorView as? android.view.ViewGroup
                
                if (customView != null) {
                    decorView?.removeView(customView)
                    customView = null
                }
                
                webViewInstance.visibility = View.VISIBLE
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                // Lock to portrait when exiting fullscreen (no auto-rotate)
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                val currentHost = try {
                    android.net.Uri.parse(view?.url).host ?: ""
                } catch (e: Exception) { "" }
                
                val hitTestResult = view?.hitTestResult
                val type = hitTestResult?.type
                val isRealLinkClick = type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                     type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

                // Always block non-user-gesture popups or non-real-link clicks
                if (!isUserGesture || !isRealLinkClick) {
                    val allowedPopupDomains = hashSetOf(
                        "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", "x.com",
                        "instagram.com", "github.com", "apple.com", "microsoft.com", "live.com", "disqus.com", 
                        "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
                        "cloudflare.com", "cloudflareinsights.com"
                    )
                    val isWhitelisted = allowedPopupDomains.any { currentHost == it || currentHost.endsWith(".$it") }
                    if (!isWhitelisted) {
                        return false
                    }
                }

                // Always block redirect to gambling/judol sites
                val destinationUrl = hitTestResult?.extra
                if (!destinationUrl.isNullOrBlank()) {
                    val destHost = android.net.Uri.parse(destinationUrl).host ?: ""
                    if (isJudolHost(destHost) || (isAdBlockActive && isHostBlocked(destHost, settings))) {
                        return false
                    }
                }

                val tempWebView = WebView(context)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        wv: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val newUrl = request?.url?.toString() ?: ""
                        val host = request?.url?.host ?: ""
                        
                        // Always block judol redirects
                        if (isJudolHost(host) || (isAdBlockActive && isHostBlocked(host, settings))) {
                            tempWebView.destroy()
                            return true
                        }
                        
                        // Block cross-site redirects from non-whitelisted sources
                        if (currentHost.isNotEmpty() && host != currentHost && !host.endsWith(".$currentHost")) {
                            val allowedCrossSite = hashSetOf(
                                "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", "x.com",
                                "instagram.com", "github.com", "apple.com", "microsoft.com", "live.com", "disqus.com",
                                "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
                                "youtube.com", "youtu.be", "reddit.com", "wikipedia.org", "stackoverflow.com",
                                "cloudflare.com", "cloudflareinsights.com", "akamaized.net"
                            )
                            val isAllowed = allowedCrossSite.any { host == it || host.endsWith(".$it") }
                            if (!isAllowed) {
                                tempWebView.destroy()
                                return true
                            }
                        }
                        
                        newTabCallback?.invoke(newUrl, isPrivate)
                        tempWebView.destroy()
                        return true
                    }
                }
                
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webViewInstance.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val newUrl = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""
                
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                
                // Always block judol/gambling hosts
                if (isJudolHost(host) || (isAdBlockActive && isHostBlocked(host, settings))) {
                    return true
                }
                
                val baseDomain = host.removePrefix("m.").removePrefix("www.")
                val isDesktop = settings.desktopDomains.contains(baseDomain)
                if (isDesktop && host.startsWith("m.")) {
                    val desktopUrl = newUrl.replace("://m.", "://www.") + (if (newUrl.contains("?")) "&" else "?") + "force_desktop=1"
                    val headers = mutableMapOf<String, String>()
                    headers["Sec-CH-UA-Mobile"] = "?0"
                    headers["Sec-CH-UA-Platform"] = "\"Windows\""
                    view?.loadUrl(desktopUrl, headers)
                    return true
                }

                // Direct download file link interceptor
                if (newUrl.contains(".xpi") || newUrl.contains(".crx") || newUrl.contains("/downloads/file/")) {
                    val actualAddonId = when {
                        newUrl.contains("ublock") || newUrl.contains("cjpalhdlnbpafiamejdnhcphjbkeiagm") -> "ublock"
                        newUrl.contains("darkreader") || newUrl.contains("eimadpcaloflhjddepbbgoikcjaggafg") -> "darkreader"
                        newUrl.contains("translator") || newUrl.contains("mchibihcapipjolgdaiegimacnlaaldg") -> "translator"
                        else -> null
                    }
                    if (actualAddonId != null) {
                        settingsRepository.setAddonEnabled(actualAddonId, true)
                        val name = when (actualAddonId) {
                            "ublock" -> "uBlock Origin Lite"
                            "darkreader" -> "Dark Reader"
                            "translator" -> "Page Translator"
                            else -> "Add-on"
                        }
                        android.widget.Toast.makeText(context, "$name berhasil dipasang!", android.widget.Toast.LENGTH_LONG).show()
                        return true
                    }
                }

                // Check and apply User-Agent change dynamically before loading
                val expectedUA = getExpectedUserAgent(newUrl)
                val currentUA = view?.settings?.userAgentString
                if (currentUA != expectedUA) {
                    view?.settings?.userAgentString = expectedUA
                    view?.settings?.useWideViewPort = true
                    view?.settings?.loadWithOverviewMode = true
                    if (expectedUA != null) {
                        val headers = mutableMapOf<String, String>()
                        val isWechatOverride = host.contains("weixin") || host.contains("wechat") ||
                            listOf("weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
                                "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
                                "accounts.weixin.qq.com", "api.weixin.qq.com",
                                "wechat.com", "open.wechat.com").any { host.endsWith(it) }
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
                        view?.loadUrl(newUrl, headers)
                    } else {
                        view?.loadUrl(newUrl)
                    }
                    return true
                }

                // Always block automatic third-party redirects (not user-initiated clicks)
                val currentUrl = view?.url
                if (currentUrl != null && currentUrl.startsWith("http")) {
                    val currentHost = android.net.Uri.parse(currentUrl).host ?: ""
                    if (currentHost.isNotEmpty() && host != currentHost && !host.endsWith(".$currentHost")) {
                        // Always block judol redirects
                        if (isJudolHost(host)) {
                            return true
                        }
                        
                        val hitTestResult = view.hitTestResult
                        val hitType = hitTestResult.type
                        val isRealLink = hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
                                         hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        
                        val allowedRedirectDomains = hashSetOf(
                            "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", 
                            "instagram.com", "github.com", "apple.com", "microsoft.com", "disqus.com", 
                            "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
                            "youtube.com", "youtu.be", "reddit.com", "wikipedia.org", "stackoverflow.com",
                            "cloudflare.com", "cloudflareinsights.com", "akamaized.net"
                        )
                        val isWhitelisted = allowedRedirectDomains.any { host == it || host.endsWith(".$it") }
                        
                        if (!isRealLink && !isWhitelisted) {
                            android.util.Log.d("AdBlock", "Blocked automatic third-party redirect: $currentHost -> $host")
                            return true
                        }
                    }
                }
                
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://") && !newUrl.startsWith("about:") && !newUrl.startsWith("yue://")) {
                    try {
                        val intent = android.content.Intent.parseUri(newUrl, android.content.Intent.URI_INTENT_SCHEME)
                        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                        intent.component = null
                        context.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val settings = settingsRepository.settingsFlow.value
                val url = request?.url
                val scheme = url?.scheme?.lowercase(Locale.US) ?: ""
                if (scheme == "android-webview-video-poster") {
                    val emptyResponse = WebResourceResponse(
                        "image/png",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                    val responseHeaders = mutableMapOf<String, String>()
                    responseHeaders["Access-Control-Allow-Origin"] = "*"
                    responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                    responseHeaders["Access-Control-Allow-Headers"] = "*"
                    responseHeaders["Cache-Control"] = "no-store"
                    emptyResponse.responseHeaders = responseHeaders
                    emptyResponse.setStatusCodeAndReasonPhrase(200, "OK")
                    return emptyResponse
                }
                val host = url?.host ?: return null
                val lowercaseHost = host.lowercase(java.util.Locale.US)

                // WeChat proxy: strip X-Requested-With header agar tidak terdeteksi
                val wechatDomains = setOf(
                    "weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
                    "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
                    "accounts.weixin.qq.com", "api.weixin.qq.com",
                    "res.wx.qq.com", "rescdn.qqmail.com",
                    "wechat.com", "open.wechat.com"
                )
                val isWechatHost = wechatDomains.any { lowercaseHost == it || lowercaseHost.endsWith(".$it") } 
                    || lowercaseHost.contains("weixin") || lowercaseHost.contains("wechat")
                if (isWechatHost) {
                    try {
                        val conn = URL(url.toString()).openConnection() as HttpURLConnection
                        conn.requestMethod = request?.method ?: "GET"
                        conn.doInput = true
                        val reqHeaders = request?.requestHeaders
                        if (reqHeaders != null) {
                            for ((headerKey, headerValue) in reqHeaders) {
                                if (headerKey.equals("X-Requested-With", true)) continue
                                if (headerKey.equals("User-Agent", true)) {
                                    conn.setRequestProperty(headerKey, view?.settings?.userAgentString ?: headerValue)
                                } else {
                                    conn.setRequestProperty(headerKey, headerValue)
                                }
                            }
                        }
                        // Forward cookies
                        val cookies = android.webkit.CookieManager.getInstance().getCookie(url.toString())
                        if (cookies != null) conn.setRequestProperty("Cookie", cookies)
                        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.instanceFollowRedirects = false
                        val responseCode = conn.responseCode
                        val contentType = conn.contentType ?: "text/html"
                        val contentEncoding = conn.contentEncoding ?: "UTF-8"
                        val inputStream: InputStream? = if (responseCode in 200..299) {
                            conn.inputStream
                        } else {
                            conn.errorStream
                        }
                        if (inputStream != null && responseCode in 200..399) {
                            val response = android.webkit.WebResourceResponse(contentType, contentEncoding, inputStream)
                            // Forward response headers (minus X-Requested-With)
                            val respHeaders = mutableMapOf<String, String>()
                            val headerFields = conn.headerFields
                            for ((hKey, hValues) in headerFields) {
                                if (hKey != null && !hKey.equals("X-Requested-With", true) && hValues.isNotEmpty()) {
                                    respHeaders[hKey] = hValues.first()
                                }
                            }
                            response.responseHeaders = respHeaders
                            return response
                        }
                    } catch (e: Exception) { 
                        // Fallback ke default handling
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                


                if (isJudolHost(host) || (settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")) && isHostBlocked(host, settings)) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream("".toByteArray())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                if (isAdBlockActive) {
                    injectCosmeticFilters(view, url, currentSettings)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, u, isReload)
                val newUrl = u ?: ""
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                this@SystemWebViewSession.url = normalizedUrl
                this@SystemWebViewSession.canGoBack = view?.canGoBack() ?: false
                this@SystemWebViewSession.canGoForward = view?.canGoForward() ?: false
                
                stateCallback?.invoke(
                    normalizedUrl,
                    this@SystemWebViewSession.title,
                    this@SystemWebViewSession.progress,
                    this@SystemWebViewSession.canGoBack,
                    this@SystemWebViewSession.canGoForward
                )
            }

            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                val newUrl = u ?: ""

                val currentSettingsForBg = settingsRepository.settingsFlow.value
                val isDarkForBg = currentSettingsForBg.isDarkModeSimulated || currentSettingsForBg.enabledAddons.contains("darkreader")
                val bgColorForPage = if (isDarkForBg) android.graphics.Color.parseColor("#000000") else android.graphics.Color.parseColor("#FFFFFF")
                view?.setBackgroundColor(bgColorForPage)

                if (isDarkForBg && u != null && !u.startsWith("yue://")) {
                    view?.evaluateJavascript("""
                        (function() {
                            var s = document.createElement('style');
                            s.setAttribute('data-yue-dark-bg', '1');
                            s.textContent = 'html, body { background-color: #000 !important; }';
                            document.documentElement.appendChild(s);
                        })();
                    """.trimIndent(), null)
                }

                val expectedUA = getExpectedUserAgent(newUrl)
                val startedHost = try {
                    android.net.Uri.parse(newUrl).host?.lowercase(Locale.US) ?: ""
                } catch (e: Exception) { "" }
                val isWechatStarted = startedHost.contains("weixin") || startedHost.contains("wechat") ||
                    listOf("weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
                        "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
                        "accounts.weixin.qq.com", "api.weixin.qq.com",
                        "wechat.com", "open.wechat.com").any { startedHost.endsWith(it) }
                if (isWechatStarted) {
                    // Remove YueAddons JS interface agar tidak terdeteksi WeChat
                    view?.removeJavascriptInterface("YueAddons")
                    view?.evaluateJavascript("""
                        (function() {
                            try { Object.defineProperty(window, 'YueAddons', { value: undefined, writable: false, configurable: true }); } catch(e) {}
                            try { delete window.YueAddons; } catch(e) {}
                            try { Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'plugins', { get: function() { return [1,2,3,4,5]; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'languages', { get: function() { return ['zh-CN','zh','en']; } }); } catch(e) {}
                            if (!window.chrome) {
                                try { window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} }; } catch(e) {}
                            }
                            try { Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'vendorSub', { get: function() { return ''; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'productSub', { get: function() { return '20030107'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'product', { get: function() { return 'Gecko'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'appVersion', { get: function() { return navigator.userAgent; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'appName', { get: function() { return 'Netscape'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'appCodeName', { get: function() { return 'Mozilla'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'doNotTrack', { get: function() { return '1'; } }); } catch(e) {}
                            if (navigator.userAgentData) {
                                try { Object.defineProperty(navigator.userAgentData, 'mobile', { get: function() { return true; } }); } catch(e) {}
                                try { Object.defineProperty(navigator.userAgentData, 'platform', { get: function() { return 'Android'; } }); } catch(e) {}
                            }
                            try { document.documentElement.setAttribute('data-useragent', navigator.userAgent); } catch(e) {}
                        })();
                    """.trimIndent(), null)
                } else {
                    // Re-add YueAddons untuk non-WeChat pages (karena sudah di-remove waktu WeChat login)
                    try { view?.addJavascriptInterface(this@SystemWebViewSession.AddonsJSInterface(), "YueAddons"); } catch(e: Exception) {}
                    
                    val isDesktopUA = expectedUA.contains("Windows") || expectedUA.contains("Macintosh") || expectedUA.contains("X11")
                    val platformStr = if (isDesktopUA) "Win32" else "Linux armv8l"
                    val isMobileVal = !isDesktopUA
                    val platformUAData = if (isDesktopUA) "Windows" else "Android"
                    view?.evaluateJavascript("""
                        (function() {
                            try { Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'plugins', { 
                                get: function() { 
                                    var p = [1,2,3,4,5];
                                    p.item = function(i) { return this[i]; };
                                    p.namedItem = function(n) { return null; };
                                    return p;
                                } 
                            }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'languages', { get: function() { return ['id-ID', 'id', 'en-US', 'en']; } }); } catch(e) {}
                            if (!window.chrome) {
                                try { window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} }; } catch(e) {}
                            }
                            try { Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'platform', { get: function() { return '$platformStr'; } }); } catch(e) {}
                            try { Object.defineProperty(navigator, 'userAgent', { get: function() { return '$expectedUA'; } }); } catch(e) {}
                            if (navigator.userAgentData) {
                                try { Object.defineProperty(navigator.userAgentData, 'mobile', { get: function() { return $isMobileVal; } }); } catch(e) {}
                                try { Object.defineProperty(navigator.userAgentData, 'platform', { get: function() { return '$platformUAData'; } }); } catch(e) {}
                            }
                        })();
                    """.trimIndent(), null)
                }
                
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                updateUserAgent(normalizedUrl)
                this@SystemWebViewSession.url = normalizedUrl
                this@SystemWebViewSession.progress = 0
                this@SystemWebViewSession.canGoBack = view?.canGoBack() ?: false
                this@SystemWebViewSession.canGoForward = view?.canGoForward() ?: false
                
                stateCallback?.invoke(
                    normalizedUrl,
                    this@SystemWebViewSession.title,
                    this@SystemWebViewSession.progress,
                    this@SystemWebViewSession.canGoBack,
                    this@SystemWebViewSession.canGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                val hostForInject = android.net.Uri.parse(u).host?.removePrefix("www.") ?: ""
                val hasUserBlockedSelectors = currentSettings.blockedCssSelectors[hostForInject].isNullOrEmpty().not()
                if (isAdBlockActive || hasUserBlockedSelectors) {
                    injectCosmeticFilters(view, u, currentSettings)
                }
            }

            override fun onPageFinished(view: WebView?, u: String?) {
                val newUrl = u ?: ""
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                this@SystemWebViewSession.url = normalizedUrl
                this@SystemWebViewSession.progress = 100
                this@SystemWebViewSession.canGoBack = view?.canGoBack() ?: false
                this@SystemWebViewSession.canGoForward = view?.canGoForward() ?: false
                
                stateCallback?.invoke(
                    normalizedUrl,
                    this@SystemWebViewSession.title,
                    this@SystemWebViewSession.progress,
                    this@SystemWebViewSession.canGoBack,
                    this@SystemWebViewSession.canGoForward
                )

                val finishedHost = try {
                    android.net.Uri.parse(u)?.host?.lowercase(Locale.US) ?: ""
                } catch (e: Exception) { "" }
                val wechatLoginDomains2 = listOf(
                    "weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
                    "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
                    "accounts.weixin.qq.com", "api.weixin.qq.com",
                    "wechat.com", "open.wechat.com"
                )
                val isWechatLogin = wechatLoginDomains2.any { finishedHost.endsWith(it) } || finishedHost.contains("weixin") || finishedHost.contains("wechat")
                if (isWechatLogin) {
                    view?.evaluateJavascript("""
                        (function() {
                            try { delete window.YueAddons; } catch(e) {}
                            try { window.YueAddons = undefined; } catch(e) {}
                            try { if (window.webkit && window.webkit.messageHandlers) delete window.webkit.messageHandlers; } catch(e) {}
                            try {
                                Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } });
                            } catch(e) {}
                            try {
                                var styles = document.querySelectorAll('[data-yue-injected]');
                                for (var i = 0; i < styles.length; i++) {
                                    styles[i].remove();
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                val hostForInject = android.net.Uri.parse(u).host?.removePrefix("www.") ?: ""
                val hasUserBlockedSelectors = currentSettings.blockedCssSelectors[hostForInject].isNullOrEmpty().not()
                if (isAdBlockActive || hasUserBlockedSelectors) {
                    injectCosmeticFilters(view, u, currentSettings)
                }
                
                if (currentSettings.enabledAddons.contains("translator")) {
                    injectTranslatorAddon(view, u, context)
                }
                
                view?.evaluateJavascript(doubleTapScript, null)
                
                if (!isPrivate && normalizedUrl != "yue://newtab" && normalizedUrl.isNotBlank()) {
                    val pageTitle = view?.title ?: normalizedUrl
                    HistoryRepositoryImpl.instance.addHistory(normalizedUrl, pageTitle)
                }
                
                // Capture thumbnail after page load
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    captureThumbnail { bitmap ->
                        thumbnailCaptureCallback?.invoke(bitmap)
                    }
                }, 500)
                
                view?.evaluateJavascript(
                    """
                    (function() {
                        var lang = document.documentElement.lang || '';
                        var bodyLang = document.body.lang || '';
                        return lang || bodyLang || 'unknown';
                    })()
                    """.trimIndent(),
                    { result ->
                        try {
                            val detectedLang = result?.replace("\"", "") ?: "unknown"
                            if (detectedLang != "unknown" && detectedLang != "id" && detectedLang != "en") {
                                onLanguageDetected?.invoke(detectedLang)
                            }
                        } catch (e: Exception) {
                            // Ignore callback errors
                        }
                    }
                )

                if (newUrl.contains("chromewebstore.google.com") || newUrl.contains("addons.mozilla.org") || newUrl.contains("microsoftedge.microsoft.com")) {
                    val enabledAddonsJson = currentSettings.enabledAddons.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var enabledAddons = $enabledAddonsJson;
                            var currentUrl = window.location.href;
                            var pageAddonId = null;
                            if (currentUrl.includes("ublock") || currentUrl.includes("cjpalhdlnbpafiamejdnhcphjbkeiagm")) {
                                pageAddonId = "ublock";
                            } else if (currentUrl.includes("darkreader") || currentUrl.includes("eimadpcaloflhjddepbbgoikcjaggafg")) {
                                pageAddonId = "darkreader";
                            } else if (currentUrl.includes("translator") || currentUrl.includes("mchibihcapipjolgdaiegimacnlaaldg")) {
                                pageAddonId = "translator";
                            }

                            var style = document.createElement('style');
                            style.innerHTML = `
                                .e-f-u-Md, button:disabled, [role="button"]:disabled, .g-Nd-Hf-v, [aria-disabled="true"], [disabled] { 
                                    pointer-events: auto !important; 
                                    cursor: pointer !important; 
                                    opacity: 1 !important; 
                                    background-color: #0b57d0 !important;
                                    color: #ffffff !important;
                                }
                            `;
                            document.head.appendChild(style);

                            function setupInstallHook() {
                                var isAlreadyInstalled = pageAddonId && enabledAddons.indexOf(pageAddonId) !== -1;
                                var buttons = document.querySelectorAll('button, [role="button"], a');
                                buttons.forEach(function(btn) {
                                    var text = (btn.textContent || '').trim().toLowerCase();
                                    var isInstallButton = text === 'dapatkan' || 
                                                          text.includes('add to chrome') || 
                                                          text.includes('tambahkan ke chrome') || 
                                                          text === 'get' || 
                                                          text.includes('add to firefox') || 
                                                          text.includes('tambahkan ke firefox') || 
                                                          text.includes('download file') || 
                                                          text.includes('download the new firefox') ||
                                                          text.includes('download firefox') ||
                                                          btn.classList.contains('AMInstallButton');
                                    
                                    if (isInstallButton) {
                                        if (isAlreadyInstalled) {
                                            btn.textContent = '✓ Terpasang (Yue Browser)';
                                            btn.style.backgroundColor = '#1e8e3e';
                                            btn.style.color = '#ffffff';
                                            btn.style.cursor = 'default';
                                            btn.disabled = true;
                                            btn.setAttribute('disabled', 'true');
                                            btn.style.pointerEvents = 'none';
                                        } else {
                                            if (btn.disabled) btn.disabled = false;
                                            btn.removeAttribute('aria-disabled');
                                            btn.removeAttribute('disabled');
                                            if (btn.style) {
                                                btn.style.pointerEvents = 'auto';
                                                btn.style.opacity = '1';
                                            }
                                            if (!btn.dataset.yueHooked) {
                                                btn.dataset.yueHooked = 'true';
                                                btn.addEventListener('click', function(e) {
                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                    if (window.YueAddons) {
                                                        window.YueAddons.installAddon(window.location.href);
                                                        // Immediately turn to green "Terpasang"
                                                        btn.textContent = '✓ Terpasang (Yue Browser)';
                                                        btn.style.backgroundColor = '#1e8e3e';
                                                        btn.style.color = '#ffffff';
                                                        btn.style.cursor = 'default';
                                                        btn.disabled = true;
                                                        btn.setAttribute('disabled', 'true');
                                                        btn.style.pointerEvents = 'none';
                                                        enabledAddons.push(pageAddonId);
                                                    }
                                                }, true);
                                            }
                                        }
                                    }
                                });
                            }
                            setupInstallHook();
                            setInterval(setupInstallHook, 1000);
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val isMainFrame = request?.isForMainFrame ?: true
                if (!isMainFrame) return
                val failingUrl = request?.url?.toString() ?: view?.url ?: ""
                if (failingUrl.startsWith("yue://")) return
                val errorHtml = getCustomErrorHtml(failingUrl)
                val baseUrl = if (failingUrl.isNotBlank()) {
                    try { android.net.Uri.parse(failingUrl).scheme + "://" + android.net.Uri.parse(failingUrl).host } catch(_: Exception) { null }
                } else null
                try {
                    view?.loadDataWithBaseURL(baseUrl, errorHtml, "text/html", "UTF-8", null)
                } catch (_: Exception) {
                    view?.loadData(errorHtml, "text/html", "UTF-8")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val isMainFrame = request?.isForMainFrame ?: true
                if (!isMainFrame) return
                val failingUrl = request?.url?.toString() ?: view?.url ?: ""
                if (failingUrl.startsWith("yue://")) return
                val statusCode = errorResponse?.statusCode ?: 0
                if (statusCode < 400) return
                val errorHtml = getCustomHttpErrorHtml(failingUrl, statusCode)
                val baseUrl = if (failingUrl.isNotBlank()) {
                    try { android.net.Uri.parse(failingUrl).scheme + "://" + android.net.Uri.parse(failingUrl).host } catch(_: Exception) { null }
                } else null
                try {
                    view?.loadDataWithBaseURL(baseUrl, errorHtml, "text/html", "UTF-8", null)
                } catch (_: Exception) {
                    view?.loadData(errorHtml, "text/html", "UTF-8")
                }
            }
        }
    }

    private fun getCustomErrorHtml(failedUrl: String?): String {
        val displayUrl = if (failedUrl.isNullOrBlank()) "situs ini" else failedUrl.take(80)
        val bgColor = "#000000"
        val moonColor = "#1A1A1A"
        val moonHighlight = "#2A2A2A"
        val crackColor = "#000000"
        val craterColor = "#0F0F0F"
        val accentColor = if (isPrivate) "#FF002C" else "#EC4899"
        val accentHover = if (isPrivate) "#FF3355" else "#FF6FB5"
        val glowColor = if (isPrivate) "rgba(255,0,44,0.22)" else "rgba(236,72,153,0.22)"
        val textColor = "#F0F0F5"
        val subTextColor = "#8E8E9E"
        val cardBg = "rgba(22,22,28,0.75)"
        val cardBorder = "rgba(255,255,255,0.06)"
        val btnSecondaryBg = "rgba(255,255,255,0.05)"
        val btnSecondaryBorder = "rgba(255,255,255,0.08)"

        return """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Yue Browser — Tidak dapat terhubung</title>
<style>
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body {
  margin: 0; padding: 0;
  background: $bgColor;
  color: $textColor;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-font-smoothing: antialiased;
}
body {
  background:
    radial-gradient(ellipse 600px 400px at 50% 28%, $glowColor, transparent 70%),
    $bgColor;
}
.wrap {
  padding: 32px 28px 24px;
  max-width: 420px;
  width: 100%;
  text-align: center;
}
.moon-wrap {
  width: 140px;
  height: 140px;
  margin: 0 auto 28px;
  position: relative;
  animation: floatY 4s ease-in-out infinite;
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
.moon-wrap::before {
  content: "";
  position: absolute;
  inset: -22px;
  border-radius: 50%;
  background: radial-gradient(circle, $glowColor 0%, transparent 72%);
  z-index: 0;
}
.moon-svg {
  width: 140px;
  height: 140px;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 8px 28px rgba(0,0,0,0.5));
}
.title {
  font-size: 22px;
  font-weight: 800;
  margin: 0 0 10px;
  color: $textColor;
  letter-spacing: -0.3px;
}
.sub {
  font-size: 14px;
  line-height: 1.6;
  color: $subTextColor;
  margin: 0 auto 24px;
  max-width: 320px;
}
.url-box {
  background: $cardBg;
  border: 1px solid $cardBorder;
  border-radius: 12px;
  padding: 12px 14px;
  margin: 0 0 28px;
  font-size: 12px;
  color: $subTextColor;
  text-align: left;
  word-break: break-all;
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.btns {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.btn {
  display: inline-block;
  padding: 13px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
  text-decoration: none;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
  border: none;
  letter-spacing: 0.2px;
}
.btn:active { transform: scale(0.96); }
.btn-primary {
  background: linear-gradient(135deg, $accentColor 0%, $accentHover 100%);
  color: #FFFFFF;
  box-shadow: 0 4px 16px ${if (isPrivate) "rgba(233,69,96,0.45)" else "rgba(236,72,153,0.45)"}, inset 0 1px 0 rgba(255,255,255,0.2);
}
.btn-secondary {
  background: $btnSecondaryBg;
  color: $textColor;
  border: 1px solid $btnSecondaryBorder;
}
.brand {
  margin-top: 32px;
  font-size: 11px;
  color: $subTextColor;
  letter-spacing: 3px;
  text-transform: uppercase;
  opacity: 0.55;
  font-weight: 600;
}
.brand-dot {
  color: $accentColor;
  margin: 0 2px;
}
</style>
</head><body>
  <div class="wrap">
    <div class="moon-wrap">
      <svg class="moon-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <radialGradient id="moonGrad" cx="35%" cy="35%" r="70%">
            <stop offset="0%" stop-color="$moonHighlight"/>
            <stop offset="65%" stop-color="$moonColor"/>
            <stop offset="100%" stop-color="$moonColor"/>
          </radialGradient>
          <filter id="moonShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="3"/>
            <feOffset dx="0" dy="4" result="offsetblur"/>
            <feComponentTransfer><feFuncA type="linear" slope="0.35"/></feComponentTransfer>
            <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
        </defs>
        <circle cx="100" cy="100" r="78" fill="url(#moonGrad)" filter="url(#moonShadow)"/>
        <ellipse cx="72" cy="78" rx="11" ry="9" fill="$craterColor" opacity="0.9"/>
        <ellipse cx="128" cy="88" rx="7" ry="6" fill="$craterColor" opacity="0.75"/>
        <ellipse cx="82" cy="122" rx="9" ry="7" fill="$craterColor" opacity="0.8"/>
        <ellipse cx="130" cy="130" rx="5" ry="4" fill="$craterColor" opacity="0.65"/>
        <ellipse cx="110" cy="65" rx="4" ry="3" fill="$craterColor" opacity="0.55"/>
        <circle cx="95" cy="108" r="3" fill="$craterColor" opacity="0.7"/>
        <circle cx="140" cy="112" r="2.5" fill="$craterColor" opacity="0.6"/>
        <path d="M100 32 L97 50 L103 68 L96 86 L105 104 L98 122 L104 140 L101 158 L106 168"
              stroke="$crackColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M103 68 L120 72 L135 62 L148 70"
              stroke="$crackColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
        <path d="M96 86 L80 92 L68 84 L55 90"
              stroke="$crackColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
        <path d="M101 128 L88 135 L75 128"
              stroke="$crackColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.7"/>
        <path d="M104 112 L118 118 L132 112"
              stroke="$crackColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.7"/>
        <path d="M68 84 L62 100 L58 115"
              stroke="$crackColor" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
        <path d="M148 70 L152 85 L148 98"
              stroke="$crackColor" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
        <path d="M 155 100 A 55 55 0 0 1 100 155 A 78 78 0 0 0 155 100 Z"
              fill="#000000" opacity="0.18"/>
        <circle cx="72" cy="78" r="2" fill="$accentColor" opacity="0.85"/>
      </svg>
    </div>
    <h1 class="title">Tidak dapat terhubung</h1>
    <p class="sub">Periksa koneksi internet Anda atau coba muat ulang halaman.</p>
    <div class="url-box">$displayUrl</div>
    <div class="btns">
      <a href="$failedUrl" class="btn btn-primary">Coba lagi</a>
      <button class="btn btn-secondary" onclick="history.back()">Kembali</button>
    </div>
    <div class="brand">YUE<span class="brand-dot">•</span>BROWSER</div>
  </div>
</body></html>
        """.trimIndent()
    }

    private fun getCustomHttpErrorHtml(failedUrl: String?, errorCode: Int): String {
        val title = when (errorCode) {
            400 -> "Permintaan tidak valid"
            401 -> "Anda perlu login"
            403 -> "Akses ditolak"
            404 -> "Halaman tidak ditemukan"
            408 -> "Waktu koneksi habis"
            in 500..599 -> "Situs mengalami gangguan"
            else -> "Terjadi kesalahan"
        }
        val subtitle = when (errorCode) {
            404 -> "Halaman yang Anda cari mungkin telah dipindahkan atau dihapus."
            403 -> "Situs menolak akses dari browser ini."
            408 -> "Situs terlalu lama merespon. Coba sebentar lagi."
            in 500..599 -> "Server situs sedang dalam masalah. Coba sebentar lagi."
            else -> "Kesalahan HTTP $errorCode saat memuat halaman."
        }
        val displayUrl = if (failedUrl.isNullOrBlank()) "situs ini" else failedUrl.take(80)
        val bgColor = "#000000"
        val moonColor = "#1A1A1A"
        val moonHighlight = "#2A2A2A"
        val crackColor = "#000000"
        val craterColor = "#0F0F0F"
        val accentColor = if (isPrivate) "#FF002C" else "#EC4899"
        val accentHover = if (isPrivate) "#FF3355" else "#FF6FB5"
        val glowColor = if (isPrivate) "rgba(255,0,44,0.22)" else "rgba(236,72,153,0.22)"
        val textColor = "#F0F0F5"
        val subTextColor = "#8E8E9E"
        val cardBg = "rgba(22,22,28,0.75)"
        val cardBorder = "rgba(255,255,255,0.06)"
        val btnSecondaryBg = "rgba(255,255,255,0.05)"
        val btnSecondaryBorder = "rgba(255,255,255,0.08)"

        return """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Yue Browser — $title</title>
<style>
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body {
  margin: 0; padding: 0;
  background: $bgColor;
  color: $textColor;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-font-smoothing: antialiased;
}
body {
  background:
    radial-gradient(ellipse 600px 400px at 50% 28%, $glowColor, transparent 70%),
    $bgColor;
}
.wrap {
  padding: 32px 28px 24px;
  max-width: 420px;
  width: 100%;
  text-align: center;
}
.moon-wrap {
  width: 140px;
  height: 140px;
  margin: 0 auto 28px;
  position: relative;
  animation: floatY 4s ease-in-out infinite;
}
@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
.moon-wrap::before {
  content: "";
  position: absolute;
  inset: -22px;
  border-radius: 50%;
  background: radial-gradient(circle, $glowColor 0%, transparent 72%);
  z-index: 0;
}
.moon-svg {
  width: 140px;
  height: 140px;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 8px 28px rgba(0,0,0,0.5));
}
.code-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 38px;
  font-weight: 900;
  color: $accentColor;
  letter-spacing: -1px;
  text-shadow: 0 2px 10px ${if (isPrivate) "rgba(233,69,96,0.45)" else "rgba(236,72,153,0.45)"};
  z-index: 2;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", system-ui, sans-serif;
}
.title {
  font-size: 22px;
  font-weight: 800;
  margin: 0 0 10px;
  color: $textColor;
  letter-spacing: -0.3px;
}
.sub {
  font-size: 14px;
  line-height: 1.6;
  color: $subTextColor;
  margin: 0 auto 24px;
  max-width: 320px;
}
.url-box {
  background: $cardBg;
  border: 1px solid $cardBorder;
  border-radius: 12px;
  padding: 12px 14px;
  margin: 0 0 28px;
  font-size: 12px;
  color: $subTextColor;
  text-align: left;
  word-break: break-all;
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.btns {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.btn {
  display: inline-block;
  padding: 13px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
  text-decoration: none;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
  border: none;
  letter-spacing: 0.2px;
}
.btn:active { transform: scale(0.96); }
.btn-primary {
  background: linear-gradient(135deg, $accentColor 0%, $accentHover 100%);
  color: #FFFFFF;
  box-shadow: 0 4px 16px ${if (isPrivate) "rgba(233,69,96,0.45)" else "rgba(236,72,153,0.45)"}, inset 0 1px 0 rgba(255,255,255,0.2);
}
.btn-secondary {
  background: $btnSecondaryBg;
  color: $textColor;
  border: 1px solid $btnSecondaryBorder;
}
.brand {
  margin-top: 32px;
  font-size: 11px;
  color: $subTextColor;
  letter-spacing: 3px;
  text-transform: uppercase;
  opacity: 0.55;
  font-weight: 600;
}
.brand-dot {
  color: $accentColor;
  margin: 0 2px;
}
</style>
</head><body>
  <div class="wrap">
    <div class="moon-wrap">
      <svg class="moon-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <radialGradient id="moonGrad" cx="35%" cy="35%" r="70%">
            <stop offset="0%" stop-color="$moonHighlight"/>
            <stop offset="65%" stop-color="$moonColor"/>
            <stop offset="100%" stop-color="$moonColor"/>
          </radialGradient>
          <filter id="moonShadow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="3"/>
            <feOffset dx="0" dy="4" result="offsetblur"/>
            <feComponentTransfer><feFuncA type="linear" slope="0.35"/></feComponentTransfer>
            <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
        </defs>
        <circle cx="100" cy="100" r="78" fill="url(#moonGrad)" filter="url(#moonShadow)"/>
        <ellipse cx="72" cy="78" rx="11" ry="9" fill="$craterColor" opacity="0.9"/>
        <ellipse cx="128" cy="88" rx="7" ry="6" fill="$craterColor" opacity="0.75"/>
        <ellipse cx="82" cy="122" rx="9" ry="7" fill="$craterColor" opacity="0.8"/>
        <ellipse cx="130" cy="130" rx="5" ry="4" fill="$craterColor" opacity="0.65"/>
        <ellipse cx="110" cy="65" rx="4" ry="3" fill="$craterColor" opacity="0.55"/>
        <circle cx="95" cy="108" r="3" fill="$craterColor" opacity="0.7"/>
        <circle cx="140" cy="112" r="2.5" fill="$craterColor" opacity="0.6"/>
        <path d="M100 32 L97 50 L103 68 L96 86 L105 104 L98 122 L104 140 L101 158 L106 168"
              stroke="$crackColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M103 68 L120 72 L135 62 L148 70"
              stroke="$crackColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
        <path d="M96 86 L80 92 L68 84 L55 90"
              stroke="$crackColor" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>
        <path d="M101 128 L88 135 L75 128"
              stroke="$crackColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.7"/>
        <path d="M104 112 L118 118 L132 112"
              stroke="$crackColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.7"/>
        <path d="M68 84 L62 100 L58 115"
              stroke="$crackColor" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
        <path d="M148 70 L152 85 L148 98"
              stroke="$crackColor" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
        <path d="M 155 100 A 55 55 0 0 1 100 155 A 78 78 0 0 0 155 100 Z"
              fill="#000000" opacity="0.18"/>
        <circle cx="72" cy="78" r="2" fill="$accentColor" opacity="0.85"/>
      </svg>
      <div class="code-overlay">$errorCode</div>
    </div>

    <h1 class="title">$title</h1>
    <p class="sub">$subtitle</p>
    <div class="url-box">$displayUrl</div>
    <div class="btns">
      <a href="$failedUrl" class="btn btn-primary">Coba lagi</a>
      <button class="btn btn-secondary" onclick="history.back()">Kembali</button>
    </div>
    <div class="brand">YUE<span class="brand-dot">•</span>BROWSER</div>
  </div>
</body></html>
        """.trimIndent()
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

    private fun updateUserAgent(currentUrl: String) {
        val expectedUA = getExpectedUserAgent(currentUrl)
        webViewInstance.settings.userAgentString = expectedUA
        webViewInstance.settings.useWideViewPort = true
        webViewInstance.settings.loadWithOverviewMode = true
    }

    private fun getExpectedUserAgent(currentUrl: String): String {
        val host = try { android.net.Uri.parse(currentUrl).host ?: "" } catch (e: Exception) { "" }
        val baseDomain = host.removePrefix("m.").removePrefix("www.")
        val settings = settingsRepository.settingsFlow.value
        val isDesktopForDomain = baseDomain.isNotEmpty() && settings.desktopDomains.contains(baseDomain)
        
        val isMozillaStore = currentUrl.contains("addons.mozilla.org")
        val isChromeStore = currentUrl.contains("chromewebstore.google.com")
        val isEdgeStore = currentUrl.contains("microsoftedge.microsoft.com/addons")
        val isExtensionStore = isMozillaStore || isChromeStore || isEdgeStore
        
        val wechatLoginDomains = listOf(
            "weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
            "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
            "accounts.weixin.qq.com", "api.weixin.qq.com",
            "wechat.com", "open.wechat.com"
        )
        val isWechat = wechatLoginDomains.any { host.endsWith(it) } || host.contains("weixin") || host.contains("wechat")
        
        if (isWechat) {
            // Chrome Mobile realistis — agar WeChat tidak deteksi sebagai WebView custom
            val osVersion = android.os.Build.VERSION.RELEASE
            val device = android.os.Build.MODEL
            val manufacturer = android.os.Build.MANUFACTURER
            return "Mozilla/5.0 (Linux; Android $osVersion; $manufacturer $device) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36"
        }
        
        return if (isDesktopMode || isDesktopForDomain || isExtensionStore) {
            when {
                isMozillaStore -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0"
                isEdgeStore -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
                else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
        } else {
            val osVersion = android.os.Build.VERSION.RELEASE
            val device = android.os.Build.MODEL
            val manufacturer = android.os.Build.MANUFACTURER
            "Mozilla/5.0 (Linux; Android $osVersion; $manufacturer $device) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
    }

    inner class AddonsJSInterface {
        @JavascriptInterface
        fun installAddon(addonUrl: String) {
            val actualAddonId = when {
                addonUrl.contains("ublock") || addonUrl.contains("cjpalhdlnbpafiamejdnhcphjbkeiagm") -> "ublock"
                addonUrl.contains("darkreader") || addonUrl.contains("eimadpcaloflhjddepbbgoikcjaggafg") -> "darkreader"
                addonUrl.contains("translator") || addonUrl.contains("mchibihcapipjolgdaiegimacnlaaldg") -> "translator"
                else -> null
            }
            
            webViewInstance.post {
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
                    GlobalScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Add-on ini tidak didukung di Yue Browser", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
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

            val pickerScript = """
                (function() {
                    if (window.__yuePicker__) { window.__yuePicker__.stop(); }
                    
                    var overlay = null;
                    var lastTarget = null;
                    var banner = null;
                    
                    function getCssSelector(el) {
                        if (!el || el === document.body) return 'body';
                        var path = [];
                        var cur = el;
                        while (cur && cur !== document.body && cur !== document) {
                            var tag = cur.tagName.toLowerCase();
                            var id = cur.id ? '#' + CSS.escape(cur.id) : '';
                            if (id) {
                                path.unshift(tag + id);
                                break;
                            }
                            var cls = Array.from(cur.classList)
                                .filter(function(c) { return c && !c.startsWith('__yue'); })
                                .slice(0, 2)
                                .map(function(c) { return '.' + CSS.escape(c); })
                                .join('');
                            var sibs = Array.from(cur.parentNode ? cur.parentNode.children : []).filter(function(s) { return s.tagName === cur.tagName; });
                            var idx = sibs.length > 1 ? ':nth-of-type(' + (sibs.indexOf(cur) + 1) + ')' : '';
                            path.unshift(tag + cls + idx);
                            cur = cur.parentElement;
                            if (path.length > 5) break;
                        }
                        return path.join(' > ');
                    }
                    
                    function showBanner() {
                        banner = document.createElement('div');
                        banner.id = '__yue_picker_banner__';
                        banner.style.cssText = 'display:none;';
                        document.body.appendChild(banner);
                    }
                    
                    function mousemoveHandler(e) {
                        var el = e.target;
                        if (el === banner || (banner && banner.contains(el))) return;
                        if (el === overlay) return;
                        if (overlay) { overlay.remove(); overlay = null; }
                        if (!el || el === document.body || el === document.documentElement) return;
                        
                        var rect = el.getBoundingClientRect();
                        overlay = document.createElement('div');
                        overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;box-sizing:border-box;border:2px solid #e94560;background:rgba(233,69,96,0.12);transition:none;border-radius:2px;';
                        overlay.style.top = rect.top + 'px';
                        overlay.style.left = rect.left + 'px';
                        overlay.style.width = rect.width + 'px';
                        overlay.style.height = rect.height + 'px';
                        document.body.appendChild(overlay);
                        lastTarget = el;
                    }
                    
                    function clickHandler(e) {
                        var el = e.target;
                        if (el === banner || (banner && banner.contains(el))) return;
                        e.preventDefault();
                        e.stopPropagation();
                        e.stopImmediatePropagation();
                        if (!el || el === document.body) return;
                        var sel = getCssSelector(el);
                        window.__yuePicker__.stop();
                        if (window.YuePicker) { YuePicker.onPicked(sel); }
                    }
                    
                    function stop() {
                        document.removeEventListener('mousemove', mousemoveHandler, true);
                        document.removeEventListener('touchmove', touchMoveHandler, true);
                        document.removeEventListener('click', clickHandler, true);
                        document.removeEventListener('touchend', touchEndHandler, true);
                        if (overlay) { overlay.remove(); overlay = null; }
                        if (banner) { banner.remove(); banner = null; }
                        window.__yuePicker__ = null;
                    }
                    
                    function touchMoveHandler(e) {
                        var touch = e.touches[0];
                        var el = document.elementFromPoint(touch.clientX, touch.clientY);
                        if (!el || el === banner || (banner && banner.contains(el))) return;
                        if (overlay) { overlay.remove(); overlay = null; }
                        if (el === document.body || el === document.documentElement) return;
                        var rect = el.getBoundingClientRect();
                        overlay = document.createElement('div');
                        overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;box-sizing:border-box;border:2px solid #e94560;background:rgba(233,69,96,0.12);border-radius:2px;';
                        overlay.style.top = rect.top + 'px';
                        overlay.style.left = rect.left + 'px';
                        overlay.style.width = rect.width + 'px';
                        overlay.style.height = rect.height + 'px';
                        document.body.appendChild(overlay);
                        lastTarget = el;
                    }
                    
                    function touchEndHandler(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (!lastTarget || lastTarget === document.body) return;
                        var sel = getCssSelector(lastTarget);
                        window.__yuePicker__.stop();
                        if (window.YuePicker) { YuePicker.onPicked(sel); }
                    }
                    
                    window.__yuePicker__ = { stop: stop };
                    showBanner();
                    document.addEventListener('mousemove', mousemoveHandler, true);
                    document.addEventListener('touchmove', touchMoveHandler, { passive: true, capture: true });
                    document.addEventListener('click', clickHandler, true);
                    document.addEventListener('touchend', touchEndHandler, { passive: false, capture: true });
                })();
            """.trimIndent()
            webViewInstance.evaluateJavascript(pickerScript, null)
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

    companion object {
        private val adDomains = hashSetOf(
            "muralssouth.shop",
            "doubleclick.net",
            "googleads.g.doubleclick.net",
            "googlesyndication.com",
            "adservice.google.com",
            "adsbygoogle.cloudfront.net",
            "pagead2.googlesyndication.com",
            "adclick.g.doubleclick.net",
            "ad.doubleclick.net",
            "adnxs.com",
            "quantserve.com",
            "scorecardresearch.com",
            "amazon-adsystem.com",
            "taboola.com",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "clickase.com",
            "onclickads.net",
            "adserver.com",
            "adservice.com",
            "adform.net",
            "advertising.com",
            "masonerthor.com",
            "a-ads.com",
            "exoclick.com",
            "juicyads.com",
            "adsterra.com",
            "mgid.com",
            "adkeeper.com",
            "adtrue.com",
            "propellerads.com",
            // Judol / gambling sites
            "bandarxl.com",
            "dewacuan.com",
            "388hero.com",
            "gaza88.com",
            "rusia777.com",
            "ratu89.com",
            "kaikoslot.com",
            "pentaslot.com",
            "ibet88.com",
            "sbobet.com",
            "maxbet.com",
            "m88.com",
            "188bet.com",
            "12bet.com",
            "w88.com",
            "fun88.com",
            "cmd368.com",
            "dafabet.com",
            "bet365.com",
            "1xbet.com",
            "bolabet.com",
            "joker123.com",
            "pragmaticplay.net",
            "pgsoft.com",
            "habanero.com",
            "spadegaming.com",
            "microgaming.com",
            "playtech.com",
            "evolutiongaming.com",
            "asiabet.com",
            "indobet.com",
            "qq8821.com",
            "qq188.com",
            "qq288.com",
            "qq388.com",
            "qq777.com",
            "qq888.com",
            "qq998.com",
            "agen338.com",
            "agen777.com",
            "agen888.com",
            "agen999.com",
            "bola88.com",
            "bola99.com",
            "casino88.com",
            "casino99.com",
            "domino88.com",
            "domino99.com",
            "poker88.com",
            "poker99.com",
            "togel88.com",
            "togel99.com",
            "slot88.com",
            "slot99.com",
            "slot777.com",
            "slot888.com",
            "slot999.com",
            "judi88.com",
            "judi99.com",
            "judi777.com",
            "judi888.com",
            "situsjudi.com",
            "situsjudionline.com",
            "bandarjudi.com",
            "bandarjudionline.com",
            "agenjudi.com",
            "agenjudionline.com",
            "daftarjudi.com",
            "linkjudi.com",
            "alternatifjudi.com",
            "slotgacor.com",
            "slotgacor88.com",
            "slotgacor99.com",
            "slotgacor777.com",
            "slotgacor888.com",
            "slotmaxwin.com",
            "slotterbaru.com",
            "slotterpercaya.com",
            "situslot.com",
            "situslot88.com",
            "situslot99.com",
            "situslot777.com",
            "situslot888.com",
            "agenslot.com",
            "agenslot88.com",
            "agenslot99.com",
            "bandarslot.com",
            "bandarslot88.com",
            "bandarslot99.com",
            "daftarslot.com",
            "daftarslot88.com",
            "linkslot.com",
            "linkslot88.com",
            "alternatifslot.com",
            "deposlot.com",
            "deposlot88.com",
            "gacor88.com",
            "gacor99.com",
            "gacor777.com",
            "gacor888.com",
            "gacor999.com",
            "maxwin88.com",
            "maxwin99.com",
            "maxwin777.com",
            "maxwin888.com",
            "scatter88.com",
            "scatter99.com",
            "scatter777.com",
            "scatter888.com",
            "rtpslot.com",
            "rtpslot88.com",
            "rtpslot99.com",
            "rtpslot777.com",
            "rtpslot888.com",
            "zeus88.com",
            "zeus99.com",
            "zeus777.com",
            "zeus888.com",
            "olympus88.com",
            "olympus99.com",
            "olympus777.com",
            "olympus888.com",
            "mahjong88.com",
            "mahjong99.com",
            "mahjong777.com",
            "mahjong888.com",
            "starlight88.com",
            "starlight99.com",
            "starlight777.com",
            "starlight888.com",
            "sweetbonanza.com",
            "bonanza88.com",
            "bonanza99.com",
            "bonanza777.com",
            "bonanza888.com",
            "wildwest88.com",
            "wildwest99.com",
            "wildwest777.com",
            "wildwest888.com",
            "aztec88.com",
            "aztec99.com",
            "aztec777.com",
            "aztec888.com",
            "caishen88.com",
            "caishen99.com",
            "caishen777.com",
            "caishen888.com",
            "naga88.com",
            "naga99.com",
            "naga777.com",
            "naga888.com",
            "naga999.com",
            "dragon88.com",
            "dragon99.com",
            "dragon777.com",
            "dragon888.com",
            "dragon999.com",
            "phoenix88.com",
            "phoenix99.com",
            "phoenix777.com",
            "phoenix888.com",
            "tiger88.com",
            "tiger99.com",
            "tiger777.com",
            "tiger888.com",
            "lion88.com",
            "lion99.com",
            "lion777.com",
            "lion888.com",
            "panda88.com",
            "panda99.com",
            "panda777.com",
            "panda888.com",
            "koi88.com",
            "koi99.com",
            "koi777.com",
            "koi888.com",
            "lucky88.com",
            "lucky99.com",
            "lucky777.com",
            "lucky888.com",
            "lucky999.com",
            "fortune88.com",
            "fortune99.com",
            "fortune777.com",
            "fortune888.com",
            "gold88.com",
            "gold99.com",
            "gold777.com",
            "gold888.com",
            "gold999.com",
            "platinum88.com",
            "platinum99.com",
            "platinum777.com",
            "platinum888.com",
            "diamond88.com",
            "diamond99.com",
            "diamond777.com",
            "diamond888.com",
            "crystal88.com",
            "crystal99.com",
            "crystal777.com",
            "crystal888.com",
            "ruby88.com",
            "ruby99.com",
            "ruby777.com",
            "ruby888.com",
            "emerald88.com",
            "emerald99.com",
            "emerald777.com",
            "emerald888.com",
            "sapphire88.com",
            "sapphire99.com",
            "sapphire777.com",
            "sapphire888.com",
            "raja88.com",
            "raja99.com",
            "raja777.com",
            "raja888.com",
            "raja999.com",
            "sultan88.com",
            "sultan99.com",
            "sultan777.com",
            "sultan888.com",
            "raja slot.com",
            "rajaslot.com",
            "rajaslot88.com",
            "rajaslot99.com",
            "rajaslot777.com",
            "rajaslot888.com",
            "sultanslot.com",
            "sultanslot88.com",
            "sultanslot99.com",
            "sultanslot777.com",
            "sultanslot888.com",
            "dewaslot.com",
            "dewaslot88.com",
            "dewaslot99.com",
            "dewaslot777.com",
            "dewaslot888.com",
            "dewa99.com",
            "dewa88.com",
            "dewa777.com",
            "dewa888.com",
            "dewa999.com",
            "hokislot.com",
            "hokislot88.com",
            "hokislot99.com",
            "hokislot777.com",
            "hokislot888.com",
            "hoki88.com",
            "hoki99.com",
            "hoki777.com",
            "hoki888.com",
            "hoki999.com",
            "jp88.com",
            "jp99.com",
            "jp777.com",
            "jp888.com",
            "jp999.com",
            "jackpot88.com",
            "jackpot99.com",
            "jackpot777.com",
            "jackpot888.com",
            "jackpot999.com",
            "win88.com",
            "win99.com",
            "win777.com",
            "win888.com",
            "win999.com",
            "menang88.com",
            "menang99.com",
            "menang777.com",
            "menang888.com",
            "menang999.com",
            "gampangmenang.com",
            "gampangmaxwin.com",
            "gampangjp.com",
            "pastimenang.com",
            "pastimaxwin.com",
            "pastijp.com",
            "auto88.com",
            "auto99.com",
            "auto777.com",
            "auto888.com",
            "auto999.com",
            "autowin88.com",
            "autowin99.com",
            "autowin777.com",
            "autowin888.com",
            "autowin999.com",
            "ibox99.com",
            "ibox88.com",
            "ibox777.com",
            "ibox888.com",
            "ibox999.com",
            "ibo88.com",
            "ibo99.com",
            "ibo777.com",
            "ibo888.com",
            "ibo999.com",
            "ibcbet.com",
            "ibc88.com",
            "ibc99.com",
            "ibc777.com",
            "ibc888.com",
            "ibc999.com",
            "s128.com",
            "s1288.com",
            "s12888.com",
            "sv388.com",
            "sv3888.com",
            "sv38888.com",
            "cf88.com",
            "cf888.com",
            "cf8888.com",
            "idnplay.com",
            "idnplay88.com",
            "idnplay99.com",
            "idnpoker.com",
            "idnpoker88.com",
            "idnpoker99.com",
            "idnlive.com",
            "idnlive88.com",
            "idnlive99.com",
            "idntogel.com",
            "idntogel88.com",
            "idntogel99.com",
            "idnslot.com",
            "idnslot88.com",
            "idnslot99.com",
            "idncash.com",
            "idncash88.com",
            "idncash99.com",
            "idnscore.com",
            "idnscore88.com",
            "idnscore99.com",
            "pokerrepublik.com",
            "pokerpelangi.com",
            "pokerhebat.com",
            "pokerjago.com",
            "pokerjago88.com",
            "pokerjago99.com",
            "pokermania.com",
            "pokermania88.com",
            "pokermania99.com",
            "pokerlounge.com",
            "pokerlounge88.com",
            "pokerlounge99.com",
            "qqpokeronline.com",
            "qqpokeronline88.com",
            "qqpokeronline99.com",
            "vippoker.com",
            "vippoker88.com",
            "vippoker99.com",
            "pro88.com",
            "pro888.com",
            "pro99.com",
            "pro999.com",
            "master88.com",
            "master888.com",
            "master99.com",
            "master999.com",
            "elite88.com",
            "elite888.com",
            "elite99.com",
            "elite999.com",
            "premium88.com",
            "premium888.com",
            "premium99.com",
            "premium999.com",
            "vip88.com",
            "vip888.com",
            "vip99.com",
            "vip999.com",
            "royal88.com",
            "royal888.com",
            "royal99.com",
            "royal999.com",
            "imperial88.com",
            "imperial888.com",
            "imperial99.com",
            "imperial999.com",
            "empire88.com",
            "empire888.com",
            "empire99.com",
            "empire999.com",
            "king88.com",
            "king888.com",
            "king99.com",
            "king999.com",
            "queen88.com",
            "queen888.com",
            "queen99.com",
            "queen999.com",
            "prince88.com",
            "prince888.com",
            "prince99.com",
            "prince999.com",
            "princess88.com",
            "princess888.com",
            "princess99.com",
            "princess999.com",
            "cuan88.com",
            "cuan888.com",
            "cuan99.com",
            "cuan999.com",
            "cuan777.com",
            "cuanslot.com",
            "cuanslot88.com",
            "cuanslot99.com",
            "cuanslot777.com",
            "cuanslot888.com",
            "pasti88.com",
            "pasti888.com",
            "pasti99.com",
            "pasti999.com",
            "pasti777.com",
            "pastislot.com",
            "pastislot88.com",
            "pastislot99.com",
            "pastislot777.com",
            "pastislot888.com",
            "gampang88.com",
            "gampang888.com",
            "gampang99.com",
            "gampang999.com",
            "gampang777.com",
            "gampangslot.com",
            "gampangslot88.com",
            "gampangslot99.com",
            "gampangslot777.com",
            "gampangslot888.com",
            "anti88.com",
            "anti888.com",
            "anti99.com",
            "anti999.com",
            "anti777.com",
            "antirungkad.com",
            "antirungkad88.com",
            "antirungkad99.com",
            "rungkad88.com",
            "rungkad99.com",
            "rungkad777.com",
            "rungkad888.com",
            "rungkad999.com",
            "gacorx500.com",
            "gacorx1000.com",
            "x500.com",
            "x1000.com",
            "x500slot.com",
            "x1000slot.com",
            "slotx500.com",
            "slotx1000.com",
            "akunpro.com",
            "akunpro88.com",
            "akunpro99.com",
            "akunpro777.com",
            "akunpro888.com",
            "akunpro999.com",
            "akunvip.com",
            "akunvip88.com",
            "akunvip99.com",
            "akunvip777.com",
            "akunvip888.com",
            "akunvip999.com",
            "akungacor.com",
            "akungacor88.com",
            "akungacor99.com",
            "akungacor777.com",
            "akungacor888.com",
            "akungacor999.com",
            "akunjp.com",
            "akunjp88.com",
            "akunjp99.com",
            "akunjp777.com",
            "akunjp888.com",
            "akunjp999.com",
            "polaslot.com",
            "polaslot88.com",
            "polaslot99.com",
            "polaslot777.com",
            "polaslot888.com",
            "polagacor.com",
            "polagacor88.com",
            "polagacor99.com",
            "polagacor777.com",
            "polagacor888.com",
            "bocoranslot.com",
            "bocoranslot88.com",
            "bocoranslot99.com",
            "bocoranslot777.com",
            "bocoranslot888.com",
            "infoslot.com",
            "infoslot88.com",
            "infoslot99.com",
            "infoslot777.com",
            "infoslot888.com",
            "infoslot999.com",
            "jadwal slot.com",
            "jadwalslot.com",
            "jadwalslot88.com",
            "jadwalslot99.com",
            "jadwalslot777.com",
            "jadwalslot888.com",
            "jamgacor.com",
            "jamgacor88.com",
            "jamgacor99.com",
            "jamgacor777.com",
            "jamgacor888.com",
            "jamhoki.com",
            "jamhoki88.com",
            "jamhoki99.com",
            "jamhoki777.com",
            "jamhoki888.com",
            "modalreceh.com",
            "modalreceh88.com",
            "modalreceh99.com",
            "modalreceh777.com",
            "modalreceh888.com",
            "depo10.com",
            "depo10k.com",
            "depo20.com",
            "depo20k.com",
            "depo25.com",
            "depo25k.com",
            "depo50.com",
            "depo50k.com",
            "depo100.com",
            "depo100k.com",
            "minimaldepo.com",
            "minimaldepo10.com",
            "minimaldepo20.com",
            "minimaldepo25.com",
            "minimaldepo50.com",
            "bonusnewmember.com",
            "bonusnewmember88.com",
            "bonusnewmember99.com",
            "bonusnewmember777.com",
            "bonusnewmember888.com",
            "bonusdeposit.com",
            "bonusdeposit88.com",
            "bonusdeposit99.com",
            "bonusdeposit777.com",
            "bonusdeposit888.com",
            "bonusmingguan.com",
            "bonusmingguan88.com",
            "bonusmingguan99.com",
            "bonusharian.com",
            "bonusharian88.com",
            "bonusharian99.com",
            "cashback88.com",
            "cashback99.com",
            "cashback777.com",
            "cashback888.com",
            "cashback999.com",
            "rollingan88.com",
            "rollingan99.com",
            "rollingan777.com",
            "rollingan888.com",
            "rollingan999.com",
            "turnover88.com",
            "turnover99.com",
            "turnover777.com",
            "turnover888.com",
            "turnover999.com",
            "referral88.com",
            "referral99.com",
            "referral777.com",
            "referral888.com",
            "referral999.com",
            "komisi88.com",
            "komisi99.com",
            "komisi777.com",
            "komisi888.com",
            "komisi999.com",
            "agenresmi.com",
            "agenresmi88.com",
            "agenresmi99.com",
            "agenresmi777.com",
            "agenresmi888.com",
            "agenresmi999.com",
            "situsresmi.com",
            "situsresmi88.com",
            "situsresmi99.com",
            "situsresmi777.com",
            "situsresmi888.com",
            "situsresmi999.com",
            "bandarresmi.com",
            "bandarresmi88.com",
            "bandarresmi99.com",
            "bandarresmi777.com",
            "bandarresmi888.com",
            "bandarresmi999.com",
            "linkresmi.com",
            "linkresmi88.com",
            "linkresmi99.com",
            "linkresmi777.com",
            "linkresmi888.com",
            "linkresmi999.com",
            "daftarresmi.com",
            "daftarresmi88.com",
            "daftarresmi99.com",
            "daftarresmi777.com",
            "daftarresmi888.com",
            "daftarresmi999.com",
            "loginresmi.com",
            "loginresmi88.com",
            "loginresmi99.com",
            "loginresmi777.com",
            "loginresmi888.com",
            "loginresmi999.com",
            "alternatifresmi.com",
            "alternatifresmi88.com",
            "alternatifresmi99.com",
            "alternatifresmi777.com",
            "alternatifresmi888.com",
            "alternatifresmi999.com",
            "terbaru88.com",
            "terbaru99.com",
            "terbaru777.com",
            "terbaru888.com",
            "terbaru999.com",
            "terbaruslot.com",
            "terbaruslot88.com",
            "terbaruslot99.com",
            "terbaruslot777.com",
            "terbaruslot888.com",
            "terbaruslot999.com",
            "terbaru2024.com",
            "terbaru2025.com",
            "terbaru2026.com",
            "terpercaya88.com",
            "terpercaya99.com",
            "terpercaya777.com",
            "terpercaya888.com",
            "terpercaya999.com",
            "terpercayaslot.com",
            "terpercayaslot88.com",
            "terpercayaslot99.com",
            "terpercayaslot777.com",
            "terpercayaslot888.com",
            "terpercayaslot999.com",
            "terlengkap88.com",
            "terlengkap99.com",
            "terlengkap777.com",
            "terlengkap888.com",
            "terlengkap999.com",
            "terlengkapslot.com",
            "terlengkapslot88.com",
            "terlengkapslot99.com",
            "terlengkapslot777.com",
            "terlengkapslot888.com",
            "terlengkapslot999.com",
            "terbesar88.com",
            "terbesar99.com",
            "terbesar777.com",
            "terbesar888.com",
            "terbesar999.com",
            "terbesarslot.com",
            "terbesarslot88.com",
            "terbesarslot99.com",
            "terbesarslot777.com",
            "terbesarslot888.com",
            "terbesarslot999.com",
            "no1slot.com",
            "no1slot88.com",
            "no1slot99.com",
            "no1slot777.com",
            "no1slot888.com",
            "no1slot999.com",
            "no1indonesia.com",
            "no1indonesia88.com",
            "no1indonesia99.com",
            "no1indonesia777.com",
            "no1indonesia888.com",
            "no1indonesia999.com",
            "serverluar.com",
            "serverluar88.com",
            "serverluar99.com",
            "serverluar777.com",
            "serverluar888.com",
            "serverluar999.com",
            "serverthailand.com",
            "serverthailand88.com",
            "serverthailand99.com",
            "serverthailand777.com",
            "serverthailand888.com",
            "serverthailand999.com",
            "serverkamboja.com",
            "serverkamboja88.com",
            "serverkamboja99.com",
            "serverkamboja777.com",
            "serverkamboja888.com",
            "serverkamboja999.com",
            "serverfilipina.com",
            "serverfilipina88.com",
            "serverfilipina99.com",
            "serverfilipina777.com",
            "serverfilipina888.com",
            "serverfilipina999.com",
            "servervietnam.com",
            "servervietnam88.com",
            "servervietnam99.com",
            "servervietnam777.com",
            "servervietnam888.com",
            "servervietnam999.com",
            "serverjepang.com",
            "serverjepang88.com",
            "serverjepang99.com",
            "serverjepang777.com",
            "serverjepang888.com",
            "serverjepang999.com",
            "serverchina.com",
            "serverchina88.com",
            "serverchina99.com",
            "serverchina777.com",
            "serverchina888.com",
            "serverchina999.com",
            "serverdubai.com",
            "serverdubai88.com",
            "serverdubai99.com",
            "serverdubai777.com",
            "serverdubai888.com",
            "serverdubai999.com",
            "servereropa.com",
            "servereropa88.com",
            "servereropa99.com",
            "servereropa777.com",
            "servereropa888.com",
            "servereropa999.com",
            "serveramerika.com",
            "serveramerika88.com",
            "serveramerika99.com",
            "serveramerika777.com",
            "serveramerika888.com",
            "serveramerika999.com",
            "serverrussia.com",
            "serverrussia88.com",
            "serverrussia99.com",
            "serverrussia777.com",
            "serverrussia888.com",
            "serverrussia999.com",
            "serverinternasional.com",
            "serverinternasional88.com",
            "serverinternasional99.com",
            "serverinternasional777.com",
            "serverinternasional888.com",
            "serverinternasional999.com",
            "serverluarnegeri.com",
            "serverluarnegeri88.com",
            "serverluarnegeri99.com",
            "serverluarnegeri777.com",
            "serverluarnegeri888.com",
            "serverluarnegeri999.com",
            "provider88.com",
            "provider888.com",
            "provider99.com",
            "provider999.com",
            "providerslot.com",
            "providerslot88.com",
            "providerslot99.com",
            "providerslot777.com",
            "providerslot888.com",
            "providerslot999.com",
            "providerterbaik.com",
            "providerterbaik88.com",
            "providerterbaik99.com",
            "providerterbaik777.com",
            "providerterbaik888.com",
            "providerterbaik999.com",
            "providerterlengkap.com",
            "providerterlengkap88.com",
            "providerterlengkap99.com",
            "providerterlengkap777.com",
            "providerterlengkap888.com",
            "providerterlengkap999.com",
            "providerterbaru.com",
            "providerterbaru88.com",
            "providerterbaru99.com",
            "providerterbaru777.com",
            "providerterbaru888.com",
            "providerterbaru999.com",
            "providerterpercaya.com",
            "providerterpercaya88.com",
            "providerterpercaya99.com",
            "providerterpercaya777.com",
            "providerterpercaya888.com",
            "providerterpercaya999.com",
            "rtp88.com",
            "rtp888.com",
            "rtp99.com",
            "rtp999.com",
            "rtp777.com",
            "livechat88.com",
            "livechat888.com",
            "livechat99.com",
            "livechat999.com",
            "livechat777.com",
            "whatsapp88.com",
            "whatsapp888.com",
            "whatsapp99.com",
            "whatsapp999.com",
            "whatsapp777.com",
            "telegram88.com",
            "telegram888.com",
            "telegram99.com",
            "telegram999.com",
            "telegram777.com",
            "line88.com",
            "line888.com",
            "line99.com",
            "line999.com",
            "line777.com",
            "cs88.com",
            "cs888.com",
            "cs99.com",
            "cs999.com",
            "cs777.com",
            "customerservice88.com",
            "customerservice888.com",
            "customerservice99.com",
            "customerservice999.com",
            "customerservice777.com",
            "support88.com",
            "support888.com",
            "support99.com",
            "support999.com",
            "support777.com",
            "bantuan88.com",
            "bantuan888.com",
            "bantuan99.com",
            "bantuan999.com",
            "bantuan777.com",
            "layanan88.com",
            "layanan888.com",
            "layanan99.com",
            "layanan999.com",
            "layanan777.com",
            "pelayanan88.com",
            "pelayanan888.com",
            "pelayanan99.com",
            "pelayanan999.com",
            "pelayanan777.com",
            "member88.com",
            "member888.com",
            "member99.com",
            "member999.com",
            "member777.com",
            "newmember88.com",
            "newmember888.com",
            "newmember99.com",
            "newmember999.com",
            "newmember777.com",
            "memberbaru88.com",
            "memberbaru888.com",
            "memberbaru99.com",
            "memberbaru999.com",
            "memberbaru777.com",
            "daftar88.com",
            "daftar888.com",
            "daftar99.com",
            "daftar999.com",
            "daftar777.com",
            "login88.com",
            "login888.com",
            "login99.com",
            "login999.com",
            "login777.com",
            "register88.com",
            "register888.com",
            "register99.com",
            "register999.com",
            "register777.com",
            "pendaftaran88.com",
            "pendaftaran888.com",
            "pendaftaran99.com",
            "pendaftaran999.com",
            "pendaftaran777.com",
            "deposit88.com",
            "deposit888.com",
            "deposit99.com",
            "deposit999.com",
            "deposit777.com",
            "withdraw88.com",
            "withdraw888.com",
            "withdraw99.com",
            "withdraw999.com",
            "withdraw777.com",
            "withdrawal88.com",
            "withdrawal888.com",
            "withdrawal99.com",
            "withdrawal999.com",
            "withdrawal777.com",
            "penarikan88.com",
            "penarikan888.com",
            "penarikan99.com",
            "penarikan999.com",
            "penarikan777.com",
            "topup88.com",
            "topup888.com",
            "topup99.com",
            "topup999.com",
            "topup777.com",
            "isidana88.com",
            "isidana888.com",
            "isidana99.com",
            "isidana999.com",
            "isidana777.com",
            "isikredit88.com",
            "isikredit888.com",
            "isikredit99.com",
            "isikredit999.com",
            "isikredit777.com",
            "pulsa88.com",
            "pulsa888.com",
            "pulsa99.com",
            "pulsa999.com",
            "pulsa777.com",
            "depositpulsa.com",
            "depositpulsa88.com",
            "depositpulsa888.com",
            "depositpulsa99.com",
            "depositpulsa999.com",
            "depositpulsa777.com",
            "depositovo.com",
            "depositovo88.com",
            "depositovo888.com",
            "depositovo99.com",
            "depositovo999.com",
            "depositovo777.com",
            "depositgopay.com",
            "depositgopay88.com",
            "depositgopay888.com",
            "depositgopay99.com",
            "depositgopay999.com",
            "depositgopay777.com",
            "depositdana.com",
            "depositdana88.com",
            "depositdana888.com",
            "depositdana99.com",
            "depositdana999.com",
            "depositdana777.com",
            "depositlinkaja.com",
            "depositlinkaja88.com",
            "depositlinkaja888.com",
            "depositlinkaja99.com",
            "depositlinkaja999.com",
            "depositlinkaja777.com",
            "depositshopee.com",
            "depositshopee88.com",
            "depositshopee888.com",
            "depositshopee99.com",
            "depositshopee999.com",
            "depositshopee777.com",
            "depositbca.com",
            "depositbca88.com",
            "depositbca888.com",
            "depositbca99.com",
            "depositbca999.com",
            "depositbca777.com",
            "depositbri.com",
            "depositbri88.com",
            "depositbri888.com",
            "depositbri99.com",
            "depositbri999.com",
            "depositbri777.com",
            "depositbni.com",
            "depositbni88.com",
            "depositbni888.com",
            "depositbni99.com",
            "depositbni999.com",
            "depositbni777.com",
            "depositmandiri.com",
            "depositmandiri88.com",
            "depositmandiri888.com",
            "depositmandiri99.com",
            "depositmandiri999.com",
            "depositmandiri777.com",
            "depositdanamon.com",
            "depositdanamon88.com",
            "depositdanamon888.com",
            "depositdanamon99.com",
            "depositdanamon999.com",
            "depositdanamon777.com",
            "depositcimb.com",
            "depositcimb88.com",
            "depositcimb888.com",
            "depositcimb99.com",
            "depositcimb999.com",
            "depositcimb777.com",
            "depositpermata.com",
            "depositpermata88.com",
            "depositpermata888.com",
            "depositpermata99.com",
            "depositpermata999.com",
            "depositpermata777.com",
            "depositpanin.com",
            "depositpanin88.com",
            "depositpanin888.com",
            "depositpanin99.com",
            "depositpanin999.com",
            "depositpanin777.com",
            "depositmega.com",
            "depositmega88.com",
            "depositmega888.com",
            "depositmega99.com",
            "depositmega999.com",
            "depositmega777.com",
            "depositbsi.com",
            "depositbsi88.com",
            "depositbsi888.com",
            "depositbsi99.com",
            "depositbsi999.com",
            "depositbsi777.com",
            "depositmuamalat.com",
            "depositmuamalat88.com",
            "depositmuamalat888.com",
            "depositmuamalat99.com",
            "depositmuamalat999.com",
            "depositmuamalat777.com",
            "depositbtn.com",
            "depositbtn88.com",
            "depositbtn888.com",
            "depositbtn99.com",
            "depositbtn999.com",
            "depositbtn777.com",
            "depositbjb.com",
            "depositbjb88.com",
            "depositbjb888.com",
            "depositbjb99.com",
            "depositbjb999.com",
            "depositbjb777.com",
            "depositbpd.com",
            "depositbpd88.com",
            "depositbpd888.com",
            "depositbpd99.com",
            "depositbpd999.com",
            "depositbpd777.com",
            "depositqris.com",
            "depositqris88.com",
            "depositqris888.com",
            "depositqris99.com",
            "depositqris999.com",
            "depositqris777.com"
        )

        private val defaultGenericSelectors = hashSetOf(
            "amp-ad", "ins.adsbygoogle", ".ad-banner", "div[id*=\"google_ads_\"]", "iframe[src*=\"doubleclick\"]",
            ".adspost", ".ads-post", "#ads-post", "#ads_post", ".ads_post_center", ".ads-content", ".ads-wrapper",
            ".ad-box", ".ad-slot", ".ad-container",
            ".popup-ads", ".pop-ads",
            ".ad-overlay", "#interstitial",
            ".modal-ads", ".ads-modal", "#ads-overlay",
            // OneSignal bell / push notification button (all variants)
            ".onesignal-bell-container", ".onesignal-bell-launcher", "#onesignal-bell-container",
            "#onesignal-popover-container", ".onesignal-popover-container",
            "#onesignal-slidedown-container", ".onesignal-slidedown-container",
            "#slidedown-container", ".onesignal-reset",
            // Generic push notification prompts
            ".push-notification-bar", ".notification-bar", ".push-bar",
            // Gambling / slot ads - link targets (use more specific patterns)
            "a[href*=\"slotgacor\"]", "a[href*=\"slot-gacor\"]", "a[href*=\"slot_maxwin\"]",
            "a[href*=\"dewacuan\"]", "a[href*=\"388hero\"]", "a[href*=\"gaza88\"]", "a[href*=\"rusia777\"]",
            "a[href*=\"pentaslot\"]", "a[href*=\"ratu89\"]", "a[href*=\"kaikoslot\"]",
            "a[href*=\"bandarxl\"]", "a[href*=\"bandar-xl\"]", "a[href*=\"gaza-88\"]",
            "a[href*=\"agenjudionline\"]", "a[href*=\"bandarjudionline\"]", "a[href*=\"situsjudionline\"]",
            "a[href*=\"slotmaxwin\"]", "a[href*=\"gacor777\"]", "a[href*=\"gacor888\"]", "a[href*=\"gacor999\"]",
            // Gambling images - specific patterns only (avoid "hero", "388" alone — too common)
            "img[src*=\"slotgacor\"]", "img[src*=\"slot-gacor\"]", "img[src*=\"dewacuan\"]",
            "img[src*=\"388hero\"]", "img[src*=\"bandarxl\"]",
            "img[src*=\"gaza88\"]", "img[src*=\"rusia777\"]", "img[src*=\"ratu89\"]",
            "img[src*=\"kaikoslot\"]",
            "img[alt*=\"slot gacor\"]", "img[alt*=\"gacor\"]",
            // Iframes with ad content
            "iframe[src*=\"slot\"]", "iframe[src*=\"gacor\"]", "iframe[src*=\"ads\"]",
            "iframe[src*=\"pop\"]", "iframe[src*=\"click\"]",
            // Common ad div patterns
            "div[class*=\"bixbox\"] iframe",
            "div[id*=\"advert\"]", "div[class*=\"advert\"]",
            "div[id*=\"sponsor\"]", "div[class*=\"sponsor\"]",
            // Specific gambling container classes (conservative)
            "div[class*=\"slotgacor\"]", "div[class*=\"gacor\"]", "div[class*=\"slot-gacor\"]", "div[class*=\"banner-ad\"]",
            // Komikcast specific gambling banners
            "div[class*=\"banner\"] > a[href*=\"slot\"]",
            "div[class*=\"banner\"] > a[href*=\"gacor\"]",
            "div[class*=\"banner\"] > a[href*=\"dewacuan\"]",
            "a > img[src*=\"slotgacor\"]",
            "a > img[src*=\"slot-gacor\"]",
            "a > img[src*=\"dewacuan\"]",
            "a > img[src*=\"gaza88\"]",
            "a > img[src*=\"bandarxl\"]"
        )

        private val genericSelectors = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val domainSelectors = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>>()
        private val wildcardDomainSelectors = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>>()
        private val adBlockHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val whitelistHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private var isAdBlockerInitialized = false


        

        private fun ensureAdBlockerInitialized(context: Context) {
            if (!isAdBlockerInitialized) {
                isAdBlockerInitialized = true
                initAdBlocker(context.applicationContext)
            }
        }

        private fun initAdBlocker(context: Context) {
            adBlockHosts.clear()
            adBlockHosts.addAll(adDomains)
            genericSelectors.clear()
            genericSelectors.addAll(defaultGenericSelectors)
            
            GlobalScope.launch(Dispatchers.IO) {
                whitelistHosts.clear()
                val file = File(context.filesDir, "adblock_hosts.txt")
                if (file.exists()) {
                    loadHostsFromFile(file)
                }
                
                val abpFile = File(context.filesDir, "abpindo_rules.txt")
                if (abpFile.exists()) {
                    loadABPindoFromFile(abpFile)
                }
                
                try {
                    val url = URL("https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    if (connection.responseCode == 200) {
                        val tempFile = File(context.filesDir, "adblock_hosts.tmp")
                        connection.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 1000) {
                            tempFile.renameTo(file)
                            loadHostsFromFile(file)
                        }
                    }
                } catch (e: Exception) {
                    // ignore network errors
                }

                try {
                    val url = URL("https://raw.githubusercontent.com/ABPindo/indonesianadblockrules/master/subscriptions/abpindo.txt")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    if (connection.responseCode == 200) {
                        val tempFile = File(context.filesDir, "abpindo_rules.tmp")
                        connection.inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 1000) {
                            tempFile.renameTo(abpFile)
                            genericSelectors.clear()
                            genericSelectors.addAll(defaultGenericSelectors)
                            domainSelectors.clear()
                            wildcardDomainSelectors.clear()
                            loadABPindoFromFile(abpFile)
                        }
                    }
                } catch (e: Exception) {
                    // ignore network errors
                }

                // Download EasyList
                try {
                    val easyListFile = File(context.filesDir, "easylist_rules.txt")
                    val shouldDownload = !easyListFile.exists() ||
                        (System.currentTimeMillis() - easyListFile.lastModified()) > 24 * 60 * 60 * 1000L
                    if (shouldDownload) {
                        val url = URL("https://easylist.to/easylist/easylist.txt")
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 20000
                        connection.readTimeout = 30000
                        connection.setRequestProperty("Accept-Encoding", "identity")
                        if (connection.responseCode == 200) {
                            val tempFile = File(context.filesDir, "easylist_rules.tmp")
                            connection.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 10000) {
                                tempFile.renameTo(easyListFile)
                                loadABPindoFromFile(easyListFile)
                            }
                        }
                    } else if (easyListFile.exists()) {
                        loadABPindoFromFile(easyListFile)
                    }
                } catch (e: Exception) {
                    // ignore network errors for EasyList
                }
            }
        }

        private fun loadHostsFromFile(file: File) {
            try {
                val hosts = hashSetOf<String>()
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val host = parts[1].trim().toLowerCase(Locale.US)
                            if (host.isNotEmpty() && host != "localhost") {
                                hosts.add(host)
                            }
                        }
                    }
                }
                if (hosts.isNotEmpty()) {
                    adBlockHosts.addAll(hosts)
                }
            } catch (e: Exception) {
                // ignore read errors
            }
        }

        private fun isDangerousSelector(selector: String): Boolean {
            val s = selector.trim().toLowerCase(Locale.US)
            if (s.isEmpty()) return true
            val dangerousElements = hashSetOf(
                "html", "body", "div", "span", "a", "img", "iframe", "p", "section", "main", "header", "footer", "article", "aside",
                "#content", ".content", "#main", ".main", "#wrapper", ".wrapper", "#app", ".app", "#page", ".page",
                "#container", ".container", ".body", "#body", ".site", "#site", ".layout", "#layout",
                "body > div", "html > body"
            )
            if (dangerousElements.contains(s)) return true
            if (!s.contains(".") && !s.contains("#") && !s.contains("[") && !s.contains(":")) {
                return true
            }
            if (s == "body" || s == "html" || s.startsWith("body ") || s.startsWith("html ") || s.startsWith("body >") || s.startsWith("html >")) {
                if (!s.contains(".") && !s.contains("#")) {
                    return true
                }
            }
            return false
        }

        private fun loadABPindoFromFile(file: File) {
            try {
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                        return@forEachLine
                    }

                    if (trimmed.startsWith("@@||")) {
                        val endIdx = trimmed.indexOfAny(charArrayOf('^', '$', '/'))
                        if (endIdx != -1 && trimmed[endIdx] == '/') {
                            return@forEachLine
                        }
                        val host = if (endIdx != -1) {
                            trimmed.substring(4, endIdx)
                        } else {
                            trimmed.substring(4)
                        }.trim().toLowerCase(Locale.US)
                        
                        if (host.isNotEmpty()) {
                            whitelistHosts.add(host)
                        }
                        return@forEachLine
                    }
                    
                    if (trimmed.startsWith("||")) {
                        val endIdx = trimmed.indexOfAny(charArrayOf('^', '$', '/'))
                        if (endIdx != -1 && trimmed[endIdx] == '/') {
                            return@forEachLine
                        }
                        val host = if (endIdx != -1) {
                            trimmed.substring(2, endIdx)
                        } else {
                            trimmed.substring(2)
                        }.trim().toLowerCase(Locale.US)
                        
                        if (host.isNotEmpty()) {
                            adBlockHosts.add(host)
                        }
                        return@forEachLine
                    }
                    
                    if (trimmed.contains("##")) {
                        val parts = trimmed.split("##", limit = 2)
                        if (parts.size == 2) {
                            val domainsStr = parts[0].trim()
                            val selector = parts[1].trim()
                            if (selector.isNotEmpty() && !isDangerousSelector(selector)) {
                                if (domainsStr.isEmpty()) {
                                    // Skip generic rules from large files to avoid bloated CSS injection payloads in WebViews
                                    // genericSelectors.add(selector)
                                } else {
                                    val domains = domainsStr.split(",")
                                    for (domain in domains) {
                                        val d = domain.trim().toLowerCase(Locale.US)
                                        if (d.isNotEmpty() && !d.startsWith("~")) {
                                            if (d.contains("*")) {
                                                wildcardDomainSelectors.getOrPut(d) { java.util.concurrent.CopyOnWriteArrayList() }.add(selector)
                                            } else {
                                                domainSelectors.getOrPut(d) { java.util.concurrent.CopyOnWriteArrayList() }.add(selector)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore read errors
            }
        }

        private fun matchWildcardDomain(host: String, pattern: String): Boolean {
            val regexStr = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$"
            return try {
                val regex = Regex(regexStr)
                if (host.matches(regex)) return true
                var temp = host
                while (temp.contains(".")) {
                    if (temp.matches(regex)) return true
                    temp = temp.substringAfter(".")
                }
                false
            } catch (e: Exception) {
                false
            }
        }

        private fun getCosmeticCSS(url: String?, settings: com.yue.browser.domain.model.BrowserSettings? = null): String {
            if (url == null || url.isBlank() || url.startsWith("yue://")) return ""
            val uri = try {
                android.net.Uri.parse(url)
            } catch (e: Exception) {
                null
            }
            val host = uri?.host?.lowercase(Locale.US) ?: return ""
            
            val selectors = mutableSetOf<String>()
            selectors.addAll(genericSelectors)
            
            var tempHost = host
            while (tempHost.isNotEmpty()) {
                val list = domainSelectors[tempHost]
                if (list != null) {
                    selectors.addAll(list)
                }
                if (tempHost.contains(".")) {
                    tempHost = tempHost.substringAfter(".")
                } else {
                    break
                }
            }

            for ((pattern, list) in wildcardDomainSelectors) {
                if (matchWildcardDomain(host, pattern)) {
                    selectors.addAll(list)
                }
            }

            // Add user-picked blocked selectors for this domain
            if (settings != null) {
                val cleanHost = host.removePrefix("www.")
                val userBlocked = settings.blockedCssSelectors[cleanHost]
                    ?: settings.blockedCssSelectors[host]
                userBlocked?.forEach { 
                    if (!isDangerousSelector(it)) {
                        selectors.add(it)
                    }
                }
            }
            
            var css = ""
            if (selectors.isNotEmpty()) {
                val joined = selectors.filter { it.isNotBlank() }.joinToString(", ")
                if (joined.isNotBlank()) {
                    css = "$joined { display: none !important; height: 0 !important; min-height: 0 !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; }"
                }
            }


            
            return css
        }

        private fun isHostBlocked(host: String, settings: com.yue.browser.domain.model.BrowserSettings): Boolean {
            val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
            if (!isAdBlockActive) return false
            val lowercaseHost = host.toLowerCase(Locale.US)
            
            if (whitelistHosts.contains(lowercaseHost)) return false
            var tempHost = lowercaseHost
            while (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
                if (whitelistHosts.contains(tempHost)) {
                    return false
                }
            }

            if (adBlockHosts.contains(lowercaseHost)) return true
            
            tempHost = lowercaseHost
            while (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
                if (adBlockHosts.contains(tempHost)) {
                    return true
                }
            }
            
            if (settings.customAdBlockFilters.isNotEmpty()) {
                val isCustomAd = settings.customAdBlockFilters.any { 
                    lowercaseHost == it || lowercaseHost.endsWith(".$it") || lowercaseHost.contains(it) 
                }
                if (isCustomAd) return true
            }

            val adKeywords = hashSetOf("adsystem", "popads", "popcash", "clickase", "onclickads", "exoclick", "adsterra", "propellerads", "mgid", "adtrue", "juicyads", "masonerthor", "ibo88")
            if (adKeywords.any { lowercaseHost.contains(it) }) {
                return true
            }
            
            return false
        }

        private fun isJudolHost(host: String): Boolean {
            val lowerHost = host.toLowerCase(Locale.US)
            
            // Check against known judol domains (most reliable)
            if (adBlockHosts.contains(lowerHost)) return true
            var tempHost = lowerHost
            while (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
                if (adBlockHosts.contains(tempHost)) return true
            }
            
            // Conservative keyword-based detection:
            // - Very specific gambling keywords (single match = block)
            // - Moderate keywords (require 2+ matches)
            val verySpecificGambling = hashSetOf(
                "gacor777", "gacor888", "gacor999",
                "dewacuan", "388hero", "gaza88", "rusia777", "ratu89", "kaikoslot",
                "pentaslot", "agenjudionline", "bandarjudionline", "situsjudionline",
                "slotgacor", "slotmaxwin", "slot-gacor"
            )
            
            for (keyword in verySpecificGambling) {
                if (lowerHost.contains(keyword)) {
                    return true
                }
            }
            
            // Moderate keywords — require at least 2 matches to reduce false positives
            val moderateGambling = hashSetOf(
                "slot", "gacor", "judi", "togel", "maxwin", "scatter", "cuan"
            )
            
            var moderateMatches = 0
            for (keyword in moderateGambling) {
                if (lowerHost.contains(keyword)) {
                    moderateMatches++
                }
            }
            
            if (moderateMatches >= 2) {
                return true
            }
            
            return false
        }

        private val overlayAdRemoverScript = """
            (function() {
                if (window.yueOverlayRemoverInitialized) return;
                window.yueOverlayRemoverInitialized = true;

                var originalOpen = window.open;
                window.open = function(url, name, specs, replace) {
                    if (!url || url === 'about:blank') {
                        console.log('YueBlock: Blocked empty/suspicious window.open call');
                        return null; 
                    }
                    try {
                        var currentHost = window.location.hostname;
                        var targetUrl = new URL(url, window.location.href);
                        var targetHost = targetUrl.hostname;
                        if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                            var adKeywords = ['click', 'pop', 'ads', 'promo', 'affiliate', 'banner', 'doubleclick', 'onclick', 'redirect', 'bonus', 'gacor', 'slot', 'cuan', '388hero', 'dewa', 'judi', 'togel', 'casino', 'bet', 'poker', 'maxwin', 'scatter', 'dewacuan', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin'];
                            var isAd = adKeywords.some(function(k) { return url.toLowerCase().includes(k); });
                            if (isAd) {
                                console.log('YueBlock: Blocked third-party ad/gambling window.open:', url);
                                return null;
                            }
                        }
                    } catch(e) {}
                    return originalOpen.apply(this, arguments);
                };

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var anchor = target.closest ? target.closest('a') : null;
                    if (anchor) {
                        var href = anchor.getAttribute('href');
                        if (href && !href.startsWith('javascript:') && !href.startsWith('#')) {
                            try {
                                var currentHost = window.location.hostname;
                                var targetUrl = new URL(href, window.location.href);
                                var targetHost = targetUrl.hostname;
                                
                                if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                                    var style = window.getComputedStyle(anchor);
                                    var rect = anchor.getBoundingClientRect();
                                    var viewWidth = window.innerWidth || document.documentElement.clientWidth;
                                    var viewHeight = window.innerHeight || document.documentElement.clientHeight;
                                    
                                    var opacity = parseFloat(style.opacity);
                                    var isTransparent = opacity < 0.1 || style.backgroundColor === 'transparent' || style.color === 'transparent';
                                    var coversLargeArea = (rect.width * rect.height) > (viewWidth * viewHeight * 0.25);
                                    var hasNoText = anchor.textContent.trim().length === 0;
                                    
                                    var isSuspicious = (isTransparent && (coversLargeArea || hasNoText)) || 
                                                       anchor.classList.contains('ad-link') || 
                                                       /click|pop|ads|direct|gacor|slot|bet|judi|togel|casino|poker|maxwin|scatter|cuan|dewacuan|388hero|gaza88|rusia777|kaikoslot|pentaslot|agenjudionline|bandarjudionline|situsjudionline|slotgacor|slotmaxwin/i.test(href);
                                    
                                    if (isSuspicious && coversLargeArea) {
                                        console.log('YueBlock: Blocked clickjack redirect:', href);
                                        e.preventDefault();
                                        e.stopPropagation();
                                        e.stopImmediatePropagation();
                                    }
                                }
                            } catch(err) {}
                        }
                    }
                }, true);

                (function() {
                    var lastScrollTop = 0;
                    var threshold = 25;
                    document.addEventListener('scroll', function(e) {
                        var target = e.target;
                        if (!target) return;
                        var scrollTop = 0;
                        if (target === document || target === window || target === document.documentElement || target === document.body) {
                            scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
                        } else if (target.scrollTop !== undefined) {
                            scrollTop = target.scrollTop;
                        } else {
                            return;
                        }
                        if (scrollTop === 0) {
                            if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                window.YueScroll.onScrollChanged(true);
                            }
                            lastScrollTop = 0;
                            return;
                        }
                        var diff = scrollTop - lastScrollTop;
                        if (Math.abs(diff) > threshold) {
                            if (diff > 0 && scrollTop > 50) {
                                if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                    window.YueScroll.onScrollChanged(false);
                                }
                            } else {
                                if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                    window.YueScroll.onScrollChanged(true);
                                }
                            }
                            lastScrollTop = scrollTop;
                        }
                    }, true);
                })();

                var adPatterns = [
                    /\bad[s]?[-_]?(overlay|popup|modal|banner|layer|wrap|container|box|frame|block|widget|unit|slot)/i,
                    /\b(popup|pop-up|pop_up)[-_]?ad/i,
                    /\b(overlay|modal|interstitial)[-_]?(ad|ads|iklan)/i,
                    /\biklan[-_]?(popup|overlay|modal)/i,
                    /\bads?[-_]?(top|bottom|float|fixed|sticky|layer|floating)/i,
                    /(onesignal|notification-bell|notify-bell|push-bell|bell-launcher|bell-container|propush|webpush|web-push|push-notif|notification-icon)/i,
                    /(float|floating)[-_]?(btn|button|icon|widget|ad)/i,
                    /(fab|floating-action)[-_]?(btn|button)/i
                ];

                function isAdNode(el) {
                    if (!el || el.nodeType !== 1) return false;
                    var id = (el.id || '').toLowerCase();
                    var cls = (el.className && typeof el.className === 'string' ? el.className : '').toLowerCase();
                    var combined = id + ' ' + cls;
                    
                    // Check patterns
                    for (var i = 0; i < adPatterns.length; i++) {
                        if (adPatterns[i].test(combined)) return true;
                    }
                    
                    // Check content/text for bell/notification
                    var text = (el.textContent || '').toLowerCase();
                    if ((text.includes('bell') || text.includes('notif') || text.includes('notification')) && text.length < 50) {
                        return true;
                    }
                    
                    // Check for common ad attribute names
                    var attrs = ['data-ad', 'data-popup', 'data-overlay', 'data-bell', 'data-notification'];
                    for (var a = 0; a < attrs.length; a++) {
                        if (el.hasAttribute(attrs[a])) return true;
                    }
                    
                    return false;
                }

                function removeOverlayAds() {
                    var all = document.querySelectorAll('div, section, aside, iframe, a');
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        try {
                            var style = window.getComputedStyle(el);
                            var pos = style.position;
                            var zIndex = parseInt(style.zIndex) || 0;
                            var w = el.offsetWidth;
                            var h = el.offsetHeight;
                            var isLarge = w > (window.innerWidth * 0.4) && h > 120;
                            var isFixed = (pos === 'fixed' || pos === 'sticky');
                            
                            // Only remove LARGE fixed/sticky overlays that are CLEARLY ads
                            // NEVER remove small icon buttons, FABs, nav icons — too many legit UI elements
                            if (isFixed && zIndex > 100 && isLarge && isAdNode(el)) {
                                el.remove();
                            }
                        } catch (e) {}
                    }
                }

                removeOverlayAds();
                
                // Run less frequently — only a few times on load
                setTimeout(removeOverlayAds, 500);
                setTimeout(removeOverlayAds, 2000);
                setTimeout(removeOverlayAds, 4000);
                setTimeout(removeOverlayAds, 6000);

                // Anti-Adblock Killer & Ad Remover
                function killAntiAdblock() {
                    // Use ONLY very specific gambling keyword patterns — never general words
                    var adKeywords = ['gacor777', 'gacor888', 'gacor999', 'dewacuan', '388hero', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'ratu89', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin', 'slot-gacor', 'bandarxl'];
                    
                    try {
                        var imgs = document.querySelectorAll('img');
                        for (var i = 0; i < imgs.length; i++) {
                            var img = imgs[i];
                            var src = (img.src || '').toLowerCase();
                            var dataSrc = (img.getAttribute('data-src') || '').toLowerCase();
                            var lazySrc = (img.getAttribute('data-lazy-src') || '').toLowerCase();
                            var alt = (img.alt || '').toLowerCase();
                            
                            var isAd = adKeywords.some(function(k) { 
                                return src.includes(k) || dataSrc.includes(k) || lazySrc.includes(k) || alt.includes(k); 
                            });
                            
                            if (isAd) {
                                img.style.setProperty('display', 'none', 'important');
                                // Only hide direct anchor parent — never hide grandparent divs
                                var anchor = img.closest('a');
                                if (anchor) {
                                    anchor.style.setProperty('display', 'none', 'important');
                                }
                            }
                        }
                    } catch (e) {}

                    try {
                        var elements = document.querySelectorAll('div, section, aside, span');
                        for(var i=0; i<elements.length; i++) {
                            var el = elements[i];
                            if (el.querySelectorAll('img').length > 1) continue;
                            
                            if(el.innerText && el.innerText.length < 500) {
                                var text = el.innerText.toLowerCase();
                                if(text.includes('adblock detected') || text.includes('disable your ad blocker') || text.includes('turn off your ad blocker')) {
                                    el.remove();
                                }
                            }
                        }
                    } catch (e) {}

                    if(document.body && document.body.style.overflow === 'hidden') {
                        document.body.style.overflow = '';
                    }
                    if(document.documentElement && document.documentElement.style.overflow === 'hidden') {
                        document.documentElement.style.overflow = '';
                    }

                    try {
                        var links = document.querySelectorAll('a');
                        for(var j=0; j<links.length; j++) {
                            var l = links[j];
                            var href = l.href ? l.href.toLowerCase() : '';
                            var isAdLink = adKeywords.some(function(k) { return href.includes(k); });
                            
                            if (href && isAdLink) {
                                l.style.setProperty('display', 'none', 'important');
                            }
                        }
                    } catch (e) {}
                }
                
                killAntiAdblock();
                setInterval(killAntiAdblock, 2000);

                var observer = new MutationObserver(function(mutations) {
                    for (var m = 0; m < mutations.length; m++) {
                        var added = mutations[m].addedNodes;
                        for (var n = 0; n < added.length; n++) {
                            var node = added[n];
                            if (node.nodeType === 1) {
                                try {
                                    var style = window.getComputedStyle(node);
                                    var pos = style.position;
                                    var zIndex = parseInt(style.zIndex) || 0;
                                    var w = node.offsetWidth || 0;
                                    var h = node.offsetHeight || 0;
                                    var isFixed = (pos === 'fixed' || pos === 'sticky' || pos === 'absolute');
                                    var isSmallSquare = w < 120 && h < 120 && w > 20 && h > 20;
                                    if (isFixed && zIndex > 100 && (isAdNode(node) || (node.tagName === 'IFRAME' && ((w > window.innerWidth * 0.5 && h > 60) || isSmallSquare)))) {
                                        node.remove();
                                    }
                                } catch(e) {}
                            }
                        }
                    }
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            })();
        """.trimIndent()

        private fun injectCosmeticFilters(view: WebView?, url: String?, settings: com.yue.browser.domain.model.BrowserSettings? = null) {
            val currentSettings = settings ?: com.yue.browser.data.repository.SettingsRepositoryImpl.instance.settingsFlow.value
            val css = getCosmeticCSS(url, currentSettings)
            val styleScript = if (css.isNotBlank()) {
                val escapedCss = css.replace("\\", "\\\\").replace("'", "\\'")
                """
                (function() {
                    var css = '$escapedCss';
                    function injectStyle() {
                        if (document.head) {
                            var style = document.getElementById('yue-adblock-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'yue-adblock-style';
                                style.innerHTML = css;
                                document.head.appendChild(style);
                            } else if (style.innerHTML !== css) {
                                style.innerHTML = css;
                            }
                        }
                    }
                    injectStyle();
                    if (!window.yueCosmeticObserver) {
                        window.yueCosmeticObserver = new MutationObserver(function() { injectStyle(); });
                        window.yueCosmeticObserver.observe(document.documentElement, { childList: true, subtree: true });
                    }
                })();
                """.trimIndent()
            } else ""
            view?.post {
                if (styleScript.isNotBlank()) view.evaluateJavascript(styleScript, null)
                view.evaluateJavascript(overlayAdRemoverScript, null)
            }
        }
        
        private fun injectTranslatorAddon(view: WebView?, url: String?, context: android.content.Context) {
            if (url == null) return
            val host = try { android.net.Uri.parse(url).host ?: "" } catch(e: Exception) { "" }
            if (host.contains("youtube")) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val cssStream = context.assets.open("addons/translator/styles.css")
                        val cssBytes = ByteArray(cssStream.available())
                        cssStream.read(cssBytes)
                        cssStream.close()
                        val cssString = String(cssBytes, Charsets.UTF_8).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")

                        val jsStream = context.assets.open("addons/translator/content.js")
                        val jsBytes = ByteArray(jsStream.available())
                        jsStream.read(jsBytes)
                        jsStream.close()
                        val jsString = String(jsBytes, Charsets.UTF_8)
                        
                        val injectScript = """
                        (function() {
                            if (document.getElementById('yue-translator-style')) return;
                            var style = document.createElement('style');
                            style.id = 'yue-translator-style';
                            style.innerHTML = '$cssString';
                            document.head.appendChild(style);
                            
                            $jsString
                        })();
                        """.trimIndent()
                        
                        launch(Dispatchers.Main) {
                            view?.evaluateJavascript(injectScript, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private val doubleTapScript = """
            (function() {
                if (window.yueDoubleTapSeekingInitialized) return;
                window.yueDoubleTapSeekingInitialized = true;

                var lastTapTime = 0;

                function findVideo(touchTarget) {
                    var el = touchTarget;
                    while (el && el !== document.body) {
                        if (el.tagName === 'VIDEO') return el;
                        el = el.parentElement;
                    }
                    var container = touchTarget.closest
                        ? touchTarget.closest('.jwplayer, [id*="jwplayer"], [class*="jw-"], .video-js, .plyr, [class*="vjs-"], [class*="player"]')
                        : null;
                    if (container) {
                        var v = container.querySelector('video');
                        if (v) return v;
                    }
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) {
                        if (!vids[i].paused) return vids[i];
                    }
                    return vids[0] || null;
                }

                function getPlayerRect(video) {
                    var rect = video.getBoundingClientRect();
                    if (rect.width > 0) return rect;
                    var node = video.parentElement;
                    while (node && node !== document.body) {
                        rect = node.getBoundingClientRect();
                        if (rect.width > 0) return rect;
                        node = node.parentElement;
                    }
                    return rect;
                }

                function seekVideo(video, clientX) {
                    var rect = getPlayerRect(video);
                    var x = clientX - rect.left;
                    if (x < rect.width / 2) {
                        video.currentTime = Math.max(0, video.currentTime - 5);
                    } else {
                        video.currentTime = Math.min(isFinite(video.duration) ? video.duration : 999999, video.currentTime + 5);
                    }
                }

                function onTouchEnd(e) {
                    var now = Date.now();
                    var diff = now - lastTapTime;
                    lastTapTime = now;

                    if (diff < 400 && diff > 30) {
                        var touch = e.changedTouches ? e.changedTouches[0] : null;
                        if (!touch) return;
                        var video = findVideo(e.target);
                        if (video) {
                            seekVideo(video, touch.clientX);
                            e.stopImmediatePropagation();
                            e.stopPropagation();
                            e.preventDefault();
                            lastTapTime = 0;
                        }
                    }
                }

                function attach(el) {
                    if (el._yueTap) return;
                    el._yueTap = true;
                    el.addEventListener('touchend', onTouchEnd, { passive: false, capture: true });
                }

                function attachToAll() {
                    document.querySelectorAll('video').forEach(attach);
                    document.querySelectorAll([
                        '.jwplayer',
                        '[id*="jwplayer"]',
                        '.jw-overlays',
                        '.jw-media',
                        '.jw-wrapper',
                        '[class*="jw-"]',
                        '.video-js',
                        '.vjs-tech',
                        '.vjs-control-bar',
                        '[class*="vjs-"]',
                        '.plyr',
                        '.plyr__video-wrapper',
                        '[class*="player-container"]',
                        '[class*="video-container"]',
                        '[class*="videoWrapper"]'
                    ].join(',')).forEach(attach);
                }

                attachToAll();

                var observer = new MutationObserver(function() { attachToAll(); });
                observer.observe(document.documentElement, { childList: true, subtree: true });

                document.addEventListener('touchend', onTouchEnd, { passive: false, capture: true });
            })();
        """.trimIndent()
    }
}
