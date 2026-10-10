package com.yue.browser.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import com.yue.browser.data.adblock.AdBlockEngine
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
        return canGoBack || spaDepth > 0 || bgb
    }
    override val combinedCanGoForward: Boolean get() {
        val bgf = bflCanGoForward()
        return canGoForward || spaDepth < 0 || bgf
    }
    internal fun resetSpaDepth() { spaDepth = 0 }

    internal fun bflCanGoBack(): Boolean {
        return try {
            val bfl = webViewInstance.copyBackForwardList()
            bfl.currentIndex > 0
        } catch (e: Exception) {
            false
        }
    }

    internal fun bflCanGoForward(): Boolean {
        return try {
            val bfl = webViewInstance.copyBackForwardList()
            val idx = bfl.currentIndex
            idx < bfl.size - 1
        } catch (e: Exception) {
            false
        }
    }

    internal fun updateNavigationState(view: WebView) {
        val bfl = view.copyBackForwardList()
        val rawBack = view.canGoBack()
        val bflBack = bfl.currentIndex > 0
        val rawForward = view.canGoForward()
        val bflForward = bfl.currentIndex < bfl.size - 1
        val newBack = rawBack || bflBack
        val newForward = rawForward || bflForward
        if (newBack != canGoBack || newForward != canGoForward) {
            android.util.Log.d("NavState", "updateNavigationState: back=$newBack forward=$newForward")
        }
        canGoBack = newBack
        canGoForward = newForward
    }

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((Bitmap) -> Unit)? = null
    override var newTabWithWebViewCallback: ((WebView, Boolean, String) -> Unit)? = null
    override var requestCloseCallback: (() -> Unit)? = null
    var openerHost: String? = null
    var isScriptPopup: Boolean = false
    var isInRedirectChain: Boolean = false

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
    internal var suppressAutoSave: Boolean = false

    // Track ad redirect URLs to skip on back navigation
    private val skippedRedirectUrls = mutableSetOf<String>()
    fun markRedirectUrlForSkip(url: String) {
        val host = try { android.net.Uri.parse(url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
        if (host.isNotEmpty()) {
            skippedRedirectUrls.add(host)
        }
    }
    fun shouldSkipOnBack(url: String): Boolean {
        val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { null }
        val host = uri?.host?.lowercase() ?: ""
        if (host.isEmpty()) return false

        // Skip kalau sudah pernah ditandai oleh redirect chain detection
        // (runtime-tracked, paling aman)
        if (skippedRedirectUrls.contains(host)) return true

        val safeHostPrefixes = setOf(
            "google", "youtube", "github", "wikipedia", "reddit", "facebook",
            "instagram", "twitter", "x.com", "microsoft", "apple", "amazon",
            "detik", "kompas", "tempo", "tokopedia", "shopee", "gojek", "grab",
            "traveloka", "bpkb", "sbs", "sbs-bank", "sbs.com.au",
        )
        val isSafeHost = safeHostPrefixes.any { host.startsWith(it) }
        if (isSafeHost) return false  // langsung skip semua heuristic kalau host kita tahu aman

        val tld = host.substringAfterLast('.', "").lowercase()

        // Keyword unik judol — tidak ada kemungkinan false positive di domain konten normal
        val judolKeywords = setOf(
            "judi", "judol", "slot", "sbobet", "poker", "casino",
            "bandar", "togel", "prediksi", "rtp", "gacor",
            "hoki", "dewa", "bos88", "hikari", "dw", "mgm",
            "bonus88", "388hero", "agenslot", "rtpslot",
            "betting", "bettingonline", "livecasino",
        )
        val hasJudolKeyword = judolKeywords.any { host.contains(it) }

        // Heuristic: HANYA skip kalau kombinasi dua sinyal kuat:
        //   (TLD suspect + ada judol keyword di hostname)
        //   ATAU (TLD suspect + referral query param)
        // Jadi domain sbs/gg/bet normal TIDAK akan kena — harus ada sinyal lain.
        val suspectJudolTlds = setOf("bet", "sbs", "gg", "vip", "club")
        val isSuspectJudolTld = tld in suspectJudolTlds

        if (isSuspectJudolTld && hasJudolKeyword) {
            android.util.Log.d("SystemWebViewSession", "shouldSkipOnBack: suspect TLD + judol keyword → $url")
            return true
        }

        val hasRefParam = uri?.queryParameterNames?.any {
            it.equals("ref", true) || it.equals("aff", true) || it.equals("affiliate", true) || it.equals("promo", true) || it.equals("sponsor", true)
        } == true
        val judolRefTlds = setOf("bet", "sbs", "gg", "vip", "club", "online", "site")
        if (hasRefParam && tld in judolRefTlds) {
            android.util.Log.d("SystemWebViewSession", "shouldSkipOnBack: referral param + suspect TLD → $url")
            return true
        }

        // Heuristic standalone: hostname judol keyword (cukup kuat sendirian)
        // ini untuk domain .com / .net yang nggak pakai TLD judol tapi jelas judol
        if (hasJudolKeyword) {
            android.util.Log.d("SystemWebViewSession", "shouldSkipOnBack: judol keyword only → $url")
            return true
        }

        // Heuristic standalone: TLD yang hampir pasti ad/redirect, jarang dipakai konten normal
        // (tanpa keyword tambahan karena TLD ini sendiri sudah cukup unik)
        val pureAdTlds = setOf("win", "cash")
        if (tld in pureAdTlds) {
            android.util.Log.d("SystemWebViewSession", "shouldSkipOnBack: pure ad TLD ($tld) → $url")
            return true
        }

        // --- HEURISTIC 4: Exact domain list (yang kita tahu pasti) ---
        val adDomains = listOf(
            // --- Shortlink / adlink (indolink, adsafelink dll) ---
            "shrinkearn.com", "shrinkme.io", "linkvertise.com", "work.ink",
            "onshort.xyz", "curiouscat.live", "cuty.io", "clk.sh",
            "tii.gg", "clicky.me", "clicky.gg", "clck.ru",
            "urlshortz.com", "safelinkreview.com", "freehck.com",
            "protectearn.com", "adsco.re", "adsafelink.net",
            "prize.win", "promo.xyz", "exo.click",
            "indolinkku.com", "adlabs6.com", "linkkitaaa.com",
            "zonaprize.net", "prizejet.net", "ngaceng.org",
            "saefulgomemen.com", "bagiurl.net", "bagiurl.com",
            "safelink.info", "safelinkreview.com", "adlinkly.com",
            "dwindra.com", "vinaurl.com", "vinaurl.net",
            "anaghost.com", "shorturllink.xyz", "shortener.link",
            "kenceng.in", "cyberlama.com", "nusagr8.com",

            // --- Popup / redirect network ---
            "traffic.99hits.com", "popsunder.com", "popads.net",
            "popcash.net", "propellerads.com", "zeropark.com",
            "clickadilla.com", "popin.cc", "popin.io",
            "clickadu.com", "evomedia.net", "hitleap.com",

            // --- Judol / slot / casino (skip saat back) ---
            "bonus888.com", "388hero.com", "kaikoslot.com",
            "rtpslot88.com", "rtpslotlive.com", "situsrtp.com",
            "rtpgacor.com", "polaslotgacor.com", "agenslot.com",
            "slotmania.com", "slot88.com", "slot77.com",
            "dewa99.com", "dewa303.com", "dewagame.com",
            "juragan99.com", "juraganslot.com", "bos88.com",
            "lazimbiz.com", "bandarjudol.com", "bandartogel.com",
            "prediksitogel.com", "togelmaster.com",
            "maxbet.com", "cmd368.com", "cmd368.me",
            "okbet.com", "ok368.com", "okdewa.com",
            "sbobet.com", "sbo88.com",
            "bet365.com", "betfair.com",
            "wm88.com", "winning368.com",
            "hoki311.com", "hoki338.com", "hoki69.com",
            "m8bet.com", "m88.com",

            // --- Popup / redirect (direct ad landing page) ---
            "dollarshaveclub.com",
            "hellofresh.com", "audible.com", "gamefly.com",
            "crashforgeeks.com", "mmoexp.com", "raid-haven.com",
            "lootcakes.com", "admybrand.com", "admyrant.com",

            // --- Ad network (server-side redirect) ---
            "doubleclick.net", "adnxs.com", "adform.com",
            "adtech.com", "smartadserver.com", "valueclick.com",
            "cpmstar.com", "sitemedia.com", "medianet.com",
            "taboola.com", "outbrain.com", "mgid.com",
            "revcontent.com", "content.ad", "zedo.com",
        )
        for (domain in adDomains) {
            if (host == domain || host.endsWith(".$domain")) return true
        }
        val adKeywords = listOf(
            "popads", "popcash", "propellerads", "clickadilla",
            "exoclick", "adsterra", "juicyads",
            "adshelter", "trafficjunky", "zeropark",
        )
        for (keyword in adKeywords) {
            if (host.contains(keyword)) return true
        }
        return false
    }
    fun clearSkippedRedirects() {
        skippedRedirectUrls.clear()
    }

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

        override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
            val connection = super.onCreateInputConnection(outAttrs)
            if (isPrivate) {
                outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            return connection
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

        try {
            // Skip semua consecutive ad entries ke belakang — chain popup judol
            // biasanya 2-3 entries panjang (popup ad → redirect ad → konten asli).
            val bfl = webViewInstance.copyBackForwardList()
            var skipCount = 0
            // Cek berapa entry berturut-turut di belakang yang harus di-skip
            for (i in bfl.currentIndex - 1 downTo 0) {
                val prevUrl = bfl.getItemAtIndex(i).url
                if (shouldSkipOnBack(prevUrl)) {
                    skipCount++
                    android.util.Log.d("SystemWebViewSession", "Will skip ad redirect on back (#$skipCount): $prevUrl")
                } else {
                    break
                }
            }
            if (skipCount >= 2) {
                // Chain panjang: kirim semua goBack sekaligus via post chain
                // supaya WebView sempat update BFL di antara tiap navigasi.
                android.util.Log.d("SystemWebViewSession", "Skipping $skipCount consecutive ad entries + current with post chain")
                // Navigate dari current page (skipCount ad entries di belakang) = total skipCount+1 step
                var remaining = skipCount + 1
                fun chainBack() {
                    if (remaining > 0 && webViewInstance.canGoBack()) {
                        webViewInstance.goBack()
                        remaining--
                        if (remaining > 0) {
                            webViewInstance.postDelayed({ chainBack() }, 80)
                        } else {
                            updateUserAgent(webViewInstance.url ?: "")
                        }
                    }
                }
                chainBack()
                return
            } else if (skipCount == 1) {
                // Skip 1 + current = 2 step
                android.util.Log.d("SystemWebViewSession", "Skipping 1 ad redirect + current (2 steps)")
                webViewInstance.goBack()
                webViewInstance.postDelayed({
                    if (webViewInstance.canGoBack()) {
                        webViewInstance.goBack()
                    }
                    updateUserAgent(webViewInstance.url ?: "")
                }, 80)
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewSession", "Error checking back history", e)
        }

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
        // Reset SEMUA state error/retry — reload harus fresh, tidak peduli
        // error terakhir apa. Retry lama bisa bikin page stuck di blank.
        lastFailedUrl = null
        isShowingErrorPage = false
        hasReceivedErrorForCurrentLoad = false
        lastAutoRetryUrl = ""
        lastHttpErrorUrl = ""
        suppressAutoSave = false

        // Coba ambil URL asli dari history entry (sebelum error page inject).
        // WebView.url setelah loadDataWithBaseURL (error page) = failingUrl,
        // tapi kita cuma mau reload URL asli dari history.
        val target = try {
            val bfl = webViewInstance.copyBackForwardList()
            bfl.currentItem?.url
                ?: webViewInstance.url
                ?: url
        } catch (_: Exception) {
            webViewInstance.url ?: url
        } ?: url

        updateUserAgent(target)

        // Set lastOverrideTime/lastOverrideUrl agar shouldOverrideUrlLoading
        // tahu ini reload dari kita sendiri dan tidak di-intercept ulang
        // oleh isBlockExternalAppRedirectsEnabled (yang bisa nge-post ulang
        // loadUrl via mainHandler.post dan bikin delay/page blank).
        lastOverrideTime = System.currentTimeMillis()
        lastOverrideUrl = target

        android.util.Log.d("SystemWebViewSession", "reload() → loading $target")

        try {
            val extraHeaders = buildMainFrameHeaders(targetUrl = target, reload = true)
            webViewInstance.loadUrl(target, extraHeaders)
        } catch (e: Exception) {
            android.util.Log.e("SystemWebViewSession", "reload() loadUrl failed, fallback to WebView.reload()", e)
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
        // Remove WebView from its parent BEFORE destroying
        // Otherwise: "WebView.destroy() called while WebView is still attached to window"
        try {
            val parent = webViewInstance.parent as? android.view.ViewGroup
            parent?.removeView(webViewInstance)
        } catch (_: Exception) { }
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
                AdBlockEngine.injectCosmeticFilters(
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
