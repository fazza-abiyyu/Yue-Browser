package com.yue.browser.data.engine

import android.webkit.WebViewClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.util.Locale

class SystemWebViewClient(
    private val context: android.content.Context,
    private val session: SystemWebViewSession,
    private val settingsRepository: com.yue.browser.domain.repository.SettingsRepository,
    private val isPrivate: Boolean
) : WebViewClient() {

    private val wechatHosts = listOf(
        "weixin.qq.com", "open.weixin.qq.com", "login.weixin.qq.com",
        "pay.weixin.qq.com", "mp.weixin.qq.com", "wx.qq.com",
        "accounts.weixin.qq.com", "api.weixin.qq.com",
        "wechat.com", "open.wechat.com"
    )

    private val allowedRedirectDomains = hashSetOf(
        "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com",
        "instagram.com", "github.com", "apple.com", "microsoft.com", "disqus.com",
        "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
        "youtube.com", "youtu.be", "reddit.com", "wikipedia.org", "stackoverflow.com",
        "cloudflare.com", "cloudflareinsights.com", "akamaized.net"
    )

    private val knownAdHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
        "adservice.google.com", "adservice.google.co.id",
        "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "googlesynergy.com",
        "tpc.googlesyndication.com", "pagead2.doubleclick.net",
        "ad.doubleclick.net", "securepubads.g.doubleclick.net",
        "pubads.g.doubleclick.net", "pagead2.googlesyndication.com"
    )

    // === NAVIGATOR OVERRIDE: buat WebView SEPERTI Chrome Mobile ASLI ===
    // Cloudflare & sistem anti-bot modern memeriksa properti navigator.*
    // (webdriver, languages, platform, vendor, hardwareConcurrency, plugins,
    // mimeTypes, maxTouchPoints, deviceMemory). Jika ada yang "aneh" → 403.
    // Kita inject script INI PALING AWAL di onPageStarted (sebelum halaman
    // sempat membaca navigator). Pakai Object.defineProperty agar bisa
    // override properti read-only seperti navigator.webdriver dan navigator.userAgent.
    private fun getNavigatorOverrideScript(userAgent: String, isDesktop: Boolean, acceptLangs: String): String {
        val platformStr = if (isDesktop) "Win32" else "Linux armv8l"
        val platformUAData = if (isDesktop) "Windows" else "Android"
        val maxTouchPts = if (isDesktop) "0" else "5"
        val langArray = acceptLangs.split(",").joinToString(",") { "\"$it\"" }
        val primaryLang = acceptLangs.split(",")[0]
        val uaCompat = if (isDesktop)
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        else
            userAgent

        // JavaScript yang akan di-eval di onPageStarted.
        // - Pakai IIFE dengan try/catch agar jika gagal tidak crash halaman.
        // - Object.defineProperty bisa override properti read-only.
        // - "delete Object.getPrototypeOf(navigator).webdriver" adalah trik
        //   untuk menghapus default get webdriver() yang mengembalikan true
        //   di banyak WebView.
        return """
(function() {
    try {
        var ua = '$userAgent';
        var lang = '$primaryLang';
        var langs = [$langArray];
        var platform = '$platformStr';
        var platformUA = '$platformUAData';
        var mtp = $maxTouchPts;
        var isDesk = ${if (isDesktop) "true" else "false"};

        try { delete Object.getPrototypeOf(navigator).webdriver; } catch(e) {}
        try { delete window.navigator.webdriver; } catch(e) {}
        Object.defineProperty(navigator, 'webdriver', {
            get: function() { return false; },
            configurable: true
        });
        Object.defineProperty(navigator, 'userAgent', {
            get: function() { return ua; },
            configurable: true
        });
        Object.defineProperty(navigator, 'appVersion', {
            get: function() { return ua.substring(ua.indexOf(' ') + 1); },
            configurable: true
        });
        Object.defineProperty(navigator, 'language', {
            get: function() { return lang; },
            configurable: true
        });
        Object.defineProperty(navigator, 'languages', {
            get: function() { return langs; },
            configurable: true
        });
        Object.defineProperty(navigator, 'platform', {
            get: function() { return platform; },
            configurable: true
        });
        Object.defineProperty(navigator, 'vendor', {
            get: function() { return 'Google Inc.'; },
            configurable: true
        });
        Object.defineProperty(navigator, 'vendorSub', {
            get: function() { return ''; },
            configurable: true
        });
        Object.defineProperty(navigator, 'product', {
            get: function() { return 'Gecko'; },
            configurable: true
        });
        Object.defineProperty(navigator, 'productSub', {
            get: function() { return '20030107'; },
            configurable: true
        });
        Object.defineProperty(navigator, 'hardwareConcurrency', {
            get: function() { return 8; },
            configurable: true
        });
        Object.defineProperty(navigator, 'maxTouchPoints', {
            get: function() { return mtp; },
            configurable: true
        });
        Object.defineProperty(navigator, 'onLine', {
            get: function() { return true; },
            configurable: true
        });
        try {
            Object.defineProperty(navigator, 'deviceMemory', {
                get: function() { return 4; },
                configurable: true
            });
        } catch(e) {}
        try {
            Object.defineProperty(navigator, 'appCodeName', {
                get: function() { return 'Mozilla'; },
                configurable: true
            });
        } catch(e) {}
        try {
            Object.defineProperty(navigator, 'appName', {
                get: function() { return 'Netscape'; },
                configurable: true
            });
        } catch(e) {}

        // === Fake window.chrome ===
        // Chrome punya window.chrome sebagai object kompleks.
        // Tanpa ini, situs dengan kuat mendeteksi WebView.
        try {
            var chromeObj = {
                runtime: {
                    onMessage: { addListener: function() {} },
                    sendMessage: function() {},
                    onConnect: { addListener: function() {} }
                },
                loadTimes: function() { return { commitLoadTime: Date.now()/1000, requestTime: Date.now()/1000, startLoadTime: Date.now()/1000, firstPaintTime: 0, firstPaintAfterLoadTime: 0, navigationType: 'Other', wasNpnNegotiated: true, npnNegotiatedProtocol: '', wasAlternateProtocolAvailable: false, connectionEstablishmentTime: 0 }; },
                csi: function() { return { startE: Date.now(), startTs: Date.now()/1000, onloadT: Date.now()/1000, pageT: 0.1, tran: 15, webfont_e: 0.2, webfont_l: 1 }; },
                app: {
                    isInstalled: false,
                    InstallState: { DISABLED: 'DISABLED', INSTALLED: 'INSTALLED', NOT_INSTALLED: 'NOT_INSTALLED' },
                    RunningState: { CANNOT_RUN: 'CANNOT_RUN', READY_TO_RUN: 'READY_TO_RUN', RUNNING: 'RUNNING' }
                },
                webstore: {
                    install: function(url, onSuccess, onFailure) {},
                    onInstallStageChanged: { addListener: function() {} },
                    onDownloadProgress: { addListener: function() {} }
                }
            };
            Object.defineProperty(window, 'chrome', {
                get: function() { return chromeObj; },
                configurable: true
            });
        } catch(e) {}

        // === Fake window.InstallTrigger ===
        try {
            Object.defineProperty(window, 'InstallTrigger', {
                get: function() { return undefined; },
                configurable: true
            });
        } catch(e) {}

        // === Permissions override (WebView sering berbeda) ===
        try {
            var originalQuery = navigator.permissions && navigator.permissions.query;
            if (originalQuery) {
                var _query = function(perm) {
                    return Promise.resolve({ state: 'prompt', onchange: null });
                };
                navigator.permissions.query = _query.bind(navigator.permissions);
            }
        } catch(e) {}

    } catch(e) {
        // ignore
    }
})();
        """.trimIndent()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        try {
            val newUrl = request?.url?.toString() ?: return false
            val host = request.url.host ?: ""

            val settings = settingsRepository.settingsFlow.value

            if (AdBlockManager.isJudolHost(context, host) || AdBlockManager.isHostBlocked(context, host, settings)) {
                return true
            }

            val baseDomain = host.removePrefix("m.").removePrefix("www.")
            val isDesktop = settings.desktopDomains.contains(baseDomain)
            if (isDesktop && host.startsWith("m.")) {
                val desktopUrl = newUrl.replace("://m.", "://www.") + (if (newUrl.contains("?")) "&" else "?") + "force_desktop=1"
                val desktopUA = UserAgentManager.getExpectedUserAgent(newUrl, true, settings)
                view?.settings?.userAgentString = desktopUA
                val extraHeaders = HashMap<String, String>()
                extraHeaders["User-Agent"] = desktopUA
                extraHeaders.putAll(UserAgentManager.getDefaultHeaders())
                view?.loadUrl(desktopUrl, extraHeaders)
                return true
            }

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

            val currentUrl = view?.url
            var isBlockedThirdParty = false
            if (currentUrl != null && currentUrl.startsWith("http")) {
                val currentHost = android.net.Uri.parse(currentUrl).host ?: ""
                val currentBase = currentHost.removePrefix("www.").removePrefix("m.")
                val targetBase = host.removePrefix("www.").removePrefix("m.")
                val isSameSite = currentBase == targetBase || host.endsWith(".$currentHost") || currentHost.endsWith(".$host")
                if (currentHost.isNotEmpty() && !isSameSite) {
                    if (AdBlockManager.isJudolHost(context, host)) {
                        return true
                    }
                    val hitTestResult = view.hitTestResult
                    val hitType = hitTestResult.type
                    val isRealLink = hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                     hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    val isWhitelisted = allowedRedirectDomains.any { host == it || host.endsWith(".$it") }
                    if (!isRealLink && !isWhitelisted) {
                        android.util.Log.d("AdBlock", "Blocked automatic third-party redirect: $currentHost -> $host")
                        isBlockedThirdParty = true
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

            if (isBlockedThirdParty) {
                return true
            }

            val expectedUA = UserAgentManager.getExpectedUserAgent(newUrl, session.isDesktopMode, settingsRepository.settingsFlow.value)
            view?.settings?.userAgentString = expectedUA

            return false
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in shouldOverrideUrlLoading", e)
            return false
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        try {
            val url = request?.url ?: return null
            val scheme = url.scheme?.lowercase(Locale.US) ?: ""

            // === MAIN FRAME: JANGAN PERNAH DI-BLOCK dari sini ===
            // Situs seperti sakuranovel.id dan bilibili memerlukan main frame
            // untuk melewati Cloudflare challenge. Hanya block SUB-RESOURCE
            // (gambar iklan, script iklan, tracker) yang jelas iklan.
            val isMainFrame = request.isForMainFrame
            if (isMainFrame) return null

            // android-webview-video-poster: internal marker → allow
            if (scheme == "android-webview-video-poster") {
                val emptyResponse = WebResourceResponse("image/png", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                val responseHeaders = mutableMapOf<String, String>()
                responseHeaders["Access-Control-Allow-Origin"] = "*"
                responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                responseHeaders["Access-Control-Allow-Headers"] = "*"
                responseHeaders["Cache-Control"] = "no-store"
                emptyResponse.responseHeaders = responseHeaders
                emptyResponse.setStatusCodeAndReasonPhrase(200, "OK")
                return emptyResponse
            }

            val host = url.host ?: return null
            val lowercaseHost = host.lowercase(java.util.Locale.US)

            // === BLOCK: Youtube ad sub-resources ===
            val urlStr = url.toString()
            if (lowercaseHost.contains("youtube.com") && (
                    urlStr.contains("/pagead/") ||
                    urlStr.contains("/ptracking") ||
                    urlStr.contains("ad_break") ||
                    urlStr.contains("adunit") ||
                    urlStr.contains("adserve") ||
                    urlStr.contains("adservice") ||
                    urlStr.contains("doubleclick") ||
                    urlStr.contains("googlesyndication") ||
                    urlStr.contains("googlead") ||
                    urlStr.contains("/api/stats/ads") ||
                    urlStr.contains("/get_midroll_info") ||
                    urlStr.contains("&ad_type=") ||
                    urlStr.contains("&ad_preroll") ||
                    urlStr.contains("&admodule=") ||
                    urlStr.contains("?ad_") ||
                    urlStr.contains("yt.ad") ||
                    urlStr.contains("/videostats/playback") ||
                    urlStr.contains("&vis=") ||
                    urlStr.contains("&adframe=") ||
                    urlStr.contains("&masthead=")
                )) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }

            // === BLOCK: Known ad hosts (hardcoded, pasti iklan) ===
            if (knownAdHosts.any { lowercaseHost == it || lowercaseHost.endsWith(".$it") }) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }

            // === BLOCK: isJudolHost + AdBlockManager.isHostBlocked ===
            // isHostBlocked saat ini TIDAK bergantung ke flag adblock aktif/tidak
            // (selalu cek ke adBlockHosts yang di-populate secara SYNC di init).
            // Jadi tidak ada race condition. Judol host = explicit block dari user.
            if (AdBlockManager.isJudolHost(context, host)) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }

            val settings = settingsRepository.settingsFlow.value
            if (AdBlockManager.isHostBlocked(context, host, settings)) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }

            return null
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in shouldInterceptRequest", e)
            return null
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in doUpdateVisitedHistory", e)
        }
    }

    override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
        try {
            super.onPageStarted(view, u, favicon)
            val newUrl = u ?: ""

            val currentSettingsForBg = settingsRepository.settingsFlow.value
            val isDarkForBg = currentSettingsForBg.isDarkModeSimulated || currentSettingsForBg.enabledAddons.contains("darkreader")
            val bgColorForPage = if (isDarkForBg) android.graphics.Color.parseColor("#000000") else android.graphics.Color.parseColor("#FFFFFF")
            view?.setBackgroundColor(bgColorForPage)

            val expectedUA = UserAgentManager.getExpectedUserAgent(newUrl, session.isDesktopMode, currentSettingsForBg)
            val startedHost = try { android.net.Uri.parse(newUrl).host?.lowercase(Locale.US) ?: "" } catch (e: Exception) { "" }
            val isWechatStarted = startedHost.contains("weixin") || startedHost.contains("wechat") ||
                wechatHosts.any { startedHost.endsWith(it) }

            // === INJECT NAVIGATOR OVERRIDE: PALING AWAL, SEBELUM HALAMAN EXECUTE JS-nya ===
            // Ini adalah INJEKSI PALING PENTING untuk lewat Cloudflare.
            // Kita panggil evaluateJavascript DI ATAS (sebelum injectCosmeticFilters dll)
            // agar navigator.* sudah ter-override sebelum situs membacanya.
            val navigatorScript = if (isWechatStarted) {
                WebViewScripts.wechatOverrideScript
            } else {
                val isDesktopUA = expectedUA.contains("Windows") || expectedUA.contains("Macintosh") || expectedUA.contains("X11")
                val acceptLangs = UserAgentManager.getAcceptLanguage()
                getNavigatorOverrideScript(expectedUA, isDesktopUA, acceptLangs)
            }

            view?.post {
                try {
                    // === INJECT 1: navigator.* override (PALING AWAL!) ===
                    view.evaluateJavascript(navigatorScript, null)

                    // === INJECT 2: Dark background jika mode gelap ===
                    if (isDarkForBg && u != null && !u.startsWith("yue://")) {
                        view.evaluateJavascript(
                            """
                            (function() {
                                try {
                                    if (!document.documentElement) return;
                                    var s = document.createElement('style');
                                    s.setAttribute('data-yue-dark-bg', '1');
                                    s.textContent = 'html, body { background-color: #000 !important; }';
                                    document.documentElement.appendChild(s);
                                } catch(e) {}
                            })();
                            """.trimIndent(), null
                        )
                    }

                    // === INJECT 3: YouTube ad block ===
                    AdBlockManager.injectYouTubeAdBlock(view, newUrl)

                    // === INJECT 4: Cosmetic filters (SELALU, tidak bergantung flag) ===
                    val currentSettings = settingsRepository.settingsFlow.value
                    AdBlockManager.injectCosmeticFilters(context, view, u, currentSettings)
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebViewClient", "Error in onPageStarted post block", e)
                }
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
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in onPageStarted", e)
        }
    }

    override fun onPageFinished(view: WebView?, u: String?) {
        try {
            super.onPageFinished(view, u)
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

            view?.post {
                try {
                    val currentSettings = settingsRepository.settingsFlow.value
                    // SELALU panggil injectCosmeticFilters di onPageFinished (backup untuk onPageStarted)
                    AdBlockManager.injectCosmeticFilters(context, view, u, currentSettings)
                    AdBlockManager.injectYouTubeAdBlock(view, u)

                    val matchingScripts = com.yue.browser.data.repository.UserScriptRepositoryImpl.instance.getMatchingScripts(newUrl)
                    UserScriptEngine.injectScripts(view, matchingScripts, context)

                    view.evaluateJavascript(WebViewScriptsVideo.doubleTapScript, null)

                    if (newUrl.contains("chromewebstore.google.com") || newUrl.contains("addons.mozilla.org") || newUrl.contains("microsoftedge.microsoft.com")) {
                        val enabledAddonsJson = currentSettings.enabledAddons.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                        view.evaluateJavascript(WebViewScripts.getExtensionStoreInstallerScript(enabledAddonsJson), null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebViewClient", "Error in onPageFinished post block", e)
                }
            }

            if (!isPrivate && normalizedUrl != "yue://newtab" && normalizedUrl.isNotBlank()) {
                val pageTitle = view?.title ?: normalizedUrl
                com.yue.browser.data.repository.HistoryRepositoryImpl.instance.addHistory(normalizedUrl, pageTitle)
            }

            view?.evaluateJavascript(
                """
                (function() {
                    try {
                        var lang = document.documentElement.lang || '';
                        var bodyLang = document.body.lang || '';
                        return lang || bodyLang || 'unknown';
                    } catch(e) { return 'unknown'; }
                })()
                """.trimIndent(),
                { result ->
                    try {
                        val detectedLang = result?.replace("\"", "") ?: "unknown"
                        if (detectedLang != "unknown" && detectedLang != "id" && detectedLang != "en") {
                            session.onLanguageDetected?.invoke(detectedLang)
                        }
                    } catch (e: Exception) {
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in onPageFinished", e)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in onReceivedError", e)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
        errorResponse: android.webkit.WebResourceResponse?
    ) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewClient", "Error in onReceivedHttpError", e)
        }
    }
}
