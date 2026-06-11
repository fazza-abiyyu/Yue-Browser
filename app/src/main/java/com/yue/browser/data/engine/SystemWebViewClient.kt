package com.yue.browser.data.engine

import android.webkit.WebViewClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

        class SystemWebViewClient(
    private val context: android.content.Context,
    private val session: SystemWebViewSession,
    private val settingsRepository: com.yue.browser.domain.repository.SettingsRepository,
    private val isPrivate: Boolean
) : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val newUrl = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""
                
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                
                // Always block judol/gambling hosts
                if (AdBlockManager.isJudolHost(context, host) || (isAdBlockActive && AdBlockManager.isHostBlocked(context, host, settings))) {
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
                val expectedUA = UserAgentManager.getExpectedUserAgent(newUrl, session.isDesktopMode, settingsRepository.settingsFlow.value)
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
                        if (AdBlockManager.isJudolHost(context, host)) {
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
                


                if (AdBlockManager.isJudolHost(context, host) || (settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")) && AdBlockManager.isHostBlocked(context, host, settings)) {
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
                    AdBlockManager.injectCosmeticFilters(context, view, url, currentSettings)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, u, isReload)
                val newUrl = u ?: ""
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.url = normalizedUrl
                session.canGoBack = view?.canGoBack() ?: false
                session.canGoForward = view?.canGoForward() ?: false
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.canGoBack,
                    session.canGoForward
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

                val expectedUA = UserAgentManager.getExpectedUserAgent(newUrl, session.isDesktopMode, settingsRepository.settingsFlow.value)
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
                    view?.evaluateJavascript(WebViewScripts.wechatOverrideScript, null)
                } else {
                    // Re-add YueAddons untuk non-WeChat pages (karena sudah di-remove waktu WeChat login)
                    try { view?.addJavascriptInterface(SystemWebViewAddonsInterface(context, session, settingsRepository), "YueAddons"); } catch(e: Exception) {}
                    
                    val isDesktopUA = expectedUA.contains("Windows") || expectedUA.contains("Macintosh") || expectedUA.contains("X11")
                    val platformStr = if (isDesktopUA) "Win32" else "Linux armv8l"
                    val isMobileVal = !isDesktopUA
                    val platformUAData = if (isDesktopUA) "Windows" else "Android"
                    view?.evaluateJavascript(WebViewScripts.getPlatformOverrideScript(platformStr, expectedUA, isMobileVal, platformUAData), null)
                }
                
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.updateUserAgent(normalizedUrl)
                session.url = normalizedUrl
                session.progress = 0
                session.canGoBack = view?.canGoBack() ?: false
                session.canGoForward = view?.canGoForward() ?: false
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.canGoBack,
                    session.canGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                val hostForInject = android.net.Uri.parse(u).host?.removePrefix("www.") ?: ""
                val hasUserBlockedSelectors = currentSettings.blockedCssSelectors[hostForInject].isNullOrEmpty().not()
                if (isAdBlockActive || hasUserBlockedSelectors) {
                    AdBlockManager.injectCosmeticFilters(context, view, u, currentSettings)
                }
            }

            override fun onPageFinished(view: WebView?, u: String?) {
                val newUrl = u ?: ""
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.url = normalizedUrl
                session.progress = 100
                session.canGoBack = view?.canGoBack() ?: false
                session.canGoForward = view?.canGoForward() ?: false
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.canGoBack,
                    session.canGoForward
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
                    AdBlockManager.injectCosmeticFilters(context, view, u, currentSettings)
                }
                
                if (currentSettings.enabledAddons.contains("translator")) {
                    /* session.injectTranslatorAddon */
                }
                
                view?.evaluateJavascript(WebViewScriptsVideo.doubleTapScript, null)
                
                if (!isPrivate && normalizedUrl != "yue://newtab" && normalizedUrl.isNotBlank()) {
                    val pageTitle = view?.title ?: normalizedUrl
                    com.yue.browser.data.repository.HistoryRepositoryImpl.instance.addHistory(normalizedUrl, pageTitle)
                }
                
                // Capture thumbnail after page load
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    session.captureThumbnail { bitmap ->
                        null?.invoke(bitmap)
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
                                session.onLanguageDetected?.invoke(detectedLang)
                            }
                        } catch (e: Exception) {
                            // Ignore callback errors
                        }
                    }
                )

                if (newUrl.contains("chromewebstore.google.com") || newUrl.contains("addons.mozilla.org") || newUrl.contains("microsoftedge.microsoft.com")) {
                    val enabledAddonsJson = currentSettings.enabledAddons.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    view?.evaluateJavascript(
                        WebViewScripts.getExtensionStoreInstallerScript(enabledAddonsJson),
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
                val errorHtml = WebViewErrorPage.getCustomErrorHtml(failingUrl, settingsRepository.settingsFlow.value.let { it.isDarkModeSimulated || it.enabledAddons.contains("darkreader") }, isPrivate)
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
                val errorHtml = WebViewErrorPage.getCustomHttpErrorHtml(failingUrl, statusCode, settingsRepository.settingsFlow.value.let { it.isDarkModeSimulated || it.enabledAddons.contains("darkreader") }, isPrivate)
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
