package com.yue.browser.data.engine
import android.webkit.WebView
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context

object AdBlockManager {
    private var assetCache = mutableMapOf<String, Set<String>>()
    fun getAsset(context: android.content.Context, path: String): Set<String> {
        if (!assetCache.containsKey(path)) {
            try {
                assetCache[path] = context.assets.open(path).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } catch (e: Exception) { assetCache[path] = emptySet() }
        }
        return assetCache[path]!!
    }
    
    fun getAdDomains(context: android.content.Context): Set<String> {
        return getAsset(context, "filters/ad_domains.txt")
    }
    
        
        val genericSelectors = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val domainSelectors = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>>()
        val wildcardDomainSelectors = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>>()
        val adBlockHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val whitelistHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        var isAdBlockerInitialized = false

        fun ensureAdBlockerInitialized(context: Context) {
            if (!isAdBlockerInitialized) {
                isAdBlockerInitialized = true
                initAdBlocker(context.applicationContext)
            }
        }

        fun copyAssetToFile(context: Context, assetPath: String, outFile: File) {
            try {
                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        fun initAdBlocker(context: Context) {
            // === SYNC BLOCK (selesai sebelum WebView mulai request): ===
            // Data SYNC = default filters dari assets (pasti tersedia).
            // Data ini adalah "safety net" — bahkan jika ASYNC gagal/terlambat,
            // adblock tetap aktif untuk host dan selector umum.
            adBlockHosts.clear()
            adBlockHosts.addAll(AdBlockManager.getAdDomains(context))
            // Fallback hardcoded: host iklan yang sering muncul di situs Indonesia
            adBlockHosts.addAll(listOf(
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "google-analytics.com", "googletagmanager.com", "adsensecustomsearchads.com",
                "pagead2.googlesyndication.com", "pagead.googlesyndication.com",
                "adservice.google.com", "adservice.google.co.id",
                "amazon-adsystem.com", "ad.doubleclick.net", "pubads.g.doubleclick.net",
                "securepubads.g.doubleclick.net", "s0.2mdn.net", "ib.adnxs.com",
                "criteo.com", "criteo.net", "partner.criteo.com",
                "popads.net", "popcash.net", "propellerads.com", "mgid.com",
                "onclickads.net", "exoclick.com", "adsterra.com", "juicyads.com",
                "masonerthor.com", "ibo88.com", "adsystem.com",
                "facebook.com", "cdn.fbsbx.com", "static.xx.fbcdn.net",
                "connect.facebook.net", "graph.facebook.com", "scontent.fbcdn.net",
                "twitter.com", "twimg.com", "cdn.api.twitter.com",
                "tiktokcdn.com", "v16-webapp.tiktok.com", "tiktok.com",
                "disqus.com", "disquscdn.com", "c.disquscdn.com",
                "youtube-nocookie.com", "ytimg.com", "googlevideo.com",
                "admob.com", "app-measurement.com", "firebase.google.com",
                "googletagservices.com", "googlesynergy.com",
                "smartadserver.com", "openx.net", "rubiconproject.com",
                "ssp.yahoo.com", "adnxs.com", "advertising.com",
                "taboola.com", "outbrain.com", "content.ad",
                "zedo.com", "zedo.net", "adsnative.com", "adsafeprotected.com",
                "scorecardresearch.com", "quantserve.com", "chartbeat.com",
                "hotjar.com", "mouseflow.com", "luckyorange.com",
                "wpstats.com", "pixel.wp.com", "gravatar.com",
                "adnow.com", "adnowmedia.com", "megapush.com", "pushnami.com",
                "pushalert.co", "onesignal.com", "onesignal.net",
                "tawk.to", "embed.tawk.to", "widget.tawk.to",
                "statcounter.com", "c.statcounter.com", "histats.com",
                "addthis.com", "s7.addthis.com", "m.addthis.com",
                "sharethis.com", "w.sharethis.com", "s.sharethis.com",
                "sumome.com", "load.sumome.com", "builder.sumome.com",
                "cookiebot.com", "cdn.cookiebot.com", "consent.cookiebot.com",
                "cookieinformation.com", "cdn.cookieinformation.com",
                "cookiepro.com", "cookie-law.info", "cookieconsent.com",
                "onetrust.com", "cdn.cookielaw.org", "privacy-mgmt.com",
                "quantcast.com", "quantcast.mgr.consensu.org", "pixel.quantserve.com",
                "acdn.adnxs.com", "cdn.adnxs.com", "prebid.adnxs.com",
                "secure.adnxs.com", "ib.adnxs-simple.com",
                "static.criteo.net", "widgets.outbrain.com", "cdn.taboola.com",
                "trc.taboolasyndication.com", "trc.taboola.com", "cdn.taboolasyndication.com",
                "api.taboola.com", "disq.us", "links.services.disqus.com",
                "referrer.disqus.com", "c.disquscdn.com"
            ))
            genericSelectors.clear()
            genericSelectors.addAll(AdBlockManager.getAsset(context, "filters/default_generic_selectors.txt"))
            // Fallback hardcoded: selector umum untuk popup/iklan di situs berita Indonesia
            genericSelectors.addAll(listOf(
                "div[id*='ad-']", "div[class*='ad-']", "div[class*='ad_']", "div[id*='ad_']",
                "div[class*='ads-']", "div[id*='ads-']", "div[class*='ads_']", "div[id*='ads_']",
                "div[class*='advert']", "div[id*='advert']", "div[class*='banner']", "div[id*='banner']",
                "div[class*='pop']", "div[id*='pop']", "div[class*='popup']", "div[id*='popup']",
                "div[class*='modal']", "div[id*='modal']", "div[class*='overlay']", "div[id*='overlay']",
                "div[class*='sponsor']", "div[id*='sponsor']", "div[class*='promo']", "div[id*='promo']",
                "ins[class*='adsbygoogle']", "ins.adsbygoogle", "ins[data-ad-client]", "ins[data-ad-slot]",
                "iframe[src*='doubleclick']", "iframe[src*='googlesyndication']", "iframe[src*='googleads']",
                "iframe[src*='adnxs']", "iframe[src*='criteo']", "iframe[src*='mgid']",
                "iframe[src*='popads']", "iframe[src*='popcash']", "iframe[src*='propeller']",
                "iframe[src*='exoclick']", "iframe[src*='adsterra']",
                "script[src*='adsbygoogle']", "script[src*='doubleclick']", "script[src*='googlesyndication']",
                "script[src*='googleads']", "script[src*='google-analytics']", "script[src*='googletagmanager']",
                "a[href*='adsystem']", "a[href*='popads']", "a[href*='popcash']", "a[href*='onclickads']",
                "a[href*='exoclick']", "a[href*='adsterra']", "a[href*='propellerads']", "a[href*='mgid']",
                "img[src*='ad.doubleclick']", "img[src*='googleads']", "img[src*='ads.googlesyndication']",
                "img[src*='criteo']", "img[src*='mgid']",
                "aside[id*='widget-ads']", "aside[class*='widget-ads']", "aside[id*='widget_ads']", "aside[class*='widget_ads']",
                "div[class*='widget-ads']", "div[id*='widget-ads']",
                "div[data-ad]", "div[data-ads]", "div[data-banner]", "div[data-popup]",
                "[data-block-type='ad']", "[data-widget-type='ad']", "[data-ad-status]"
            ))
            android.util.Log.d("AdBlockManager", "SYNC: adBlockHosts=${adBlockHosts.size}, genericSelectors=${genericSelectors.size}")

            // === ASYNC BLOCK (tambah data TAMBAHAN, tidak menghapus yang SYNC): ===
            GlobalScope.launch(Dispatchers.IO) {
                val abpFile = File(context.filesDir, "abpindo_rules.txt")
                if (!abpFile.exists()) {
                    copyAssetToFile(context, "adblock/abpindo.txt", abpFile)
                }
                if (abpFile.exists()) {
                    loadABPindoFromFile(context, abpFile)
                }

                val easyListFile = File(context.filesDir, "easylist_rules.txt")
                if (!easyListFile.exists()) {
                    copyAssetToFile(context, "adblock/easylist.txt", easyListFile)
                }
                if (easyListFile.exists()) {
                    loadABPindoFromFile(context, easyListFile)
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
                            // HANYA tambah, JANGAN clear() data SYNC!
                            loadABPindoFromFile(context, abpFile)
                        }
                    }
                } catch (e: Exception) {
                    // ignore network errors
                }

                // Download EasyList
                try {
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
                                loadABPindoFromFile(context, easyListFile)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore network errors for EasyList
                }
                android.util.Log.d("AdBlockManager", "ASYNC selesai: adBlockHosts=${adBlockHosts.size}, genericSelectors=${genericSelectors.size}, domainSelectors=${domainSelectors.size}")
            }
        }

        fun loadHostsFromFile(file: File) {
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

        fun isDangerousSelector(context: android.content.Context, selector: String): Boolean {
            val s = selector.trim().toLowerCase(Locale.US)
            if (s.isEmpty()) return true
            val dangerousElements = AdBlockManager.getAsset(context, "filters/dangerous_elements.txt")
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

        fun loadABPindoFromFile(context: android.content.Context, file: File) {
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
                            if (selector.isNotEmpty() && !isDangerousSelector(context, selector)) {
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

        fun matchWildcardDomain(host: String, pattern: String): Boolean {
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

        fun getCosmeticCSS(context: android.content.Context, url: String?, settings: com.yue.browser.domain.model.BrowserSettings? = null): String {
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
                    if (!isDangerousSelector(context, it)) {
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

        fun isHostBlocked(context: android.content.Context, host: String, settings: com.yue.browser.domain.model.BrowserSettings?): Boolean {
            // SELALU cek adBlockHosts — tidak bergantung pada flag isAdBlockEnabled.
            // Ini mencegah race condition saat settingsFlow.value = default state.
            val lowercaseHost = host.toLowerCase(Locale.US)
            
            // Whitelist (user explicitly said don't block):
            if (whitelistHosts.contains(lowercaseHost)) return false
            var tempHost = lowercaseHost
            while (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
                if (whitelistHosts.contains(tempHost)) {
                    return false
                }
            }

            // Blocked host (from SYNC+ASYNC init):
            if (adBlockHosts.contains(lowercaseHost)) return true
            tempHost = lowercaseHost
            while (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
                if (adBlockHosts.contains(tempHost)) {
                    return true
                }
            }

            // Custom user filters + enabled addons (hanya jika settings tersedia):
            if (settings != null) {
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                if (isAdBlockActive) {
                    if (settings.customAdBlockFilters.isNotEmpty()) {
                        val isCustomAd = settings.customAdBlockFilters.any {
                            lowercaseHost == it || lowercaseHost.endsWith(".$it") || lowercaseHost.contains(it)
                        }
                        if (isCustomAd) return true
                    }
                }
            }

            // Keyword fallback — selalu aktif:
            val adKeywords = hashSetOf("adsystem", "popads", "popcash", "clickase", "onclickads", "exoclick", "adsterra", "propellerads", "mgid", "adtrue", "juicyads", "masonerthor", "ibo88")
            if (adKeywords.any { lowercaseHost.contains(it) }) {
                return true
            }

            return false
        }

        fun isJudolHost(context: android.content.Context, host: String): Boolean {
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
            val verySpecificGambling = AdBlockManager.getAsset(context, "filters/gambling_strict.txt")
            
            for (keyword in verySpecificGambling) {
                if (lowerHost.contains(keyword)) {
                    return true
                }
            }
            
            // Moderate keywords — require at least 2 matches to reduce false positives
            val moderateGambling = AdBlockManager.getAsset(context, "filters/gambling_moderate.txt")
            
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

    
        fun injectCosmeticFilters(context: android.content.Context, view: WebView?, url: String?, settings: com.yue.browser.domain.model.BrowserSettings? = null) {
            val currentSettings = settings ?: com.yue.browser.data.repository.SettingsRepositoryImpl.instance.settingsFlow.value
            val css = getCosmeticCSS(context, url, currentSettings)
            val styleScript = if (css.isNotBlank()) {
                val safeCss = try {
                    css
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .replace("\t", " ")
                        .take(60000)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Error escaping CSS", e)
                    ""
                }
                """
                (function() {
                    try {
                        var css = '$safeCss';
                        function injectStyle() {
                            try {
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
                            } catch(e) {}
                        }
                        injectStyle();
                        if (!window.yueCosmeticObserver) {
                            window.yueCosmeticObserver = new MutationObserver(function() { try { injectStyle(); } catch(e) {} });
                            if (document.documentElement) window.yueCosmeticObserver.observe(document.documentElement, { childList: true, subtree: true });
                        }
                    } catch(e) {}
                })();
                """.trimIndent()
            } else ""
            view?.post {
                try {
                    if (styleScript.isNotBlank()) view.evaluateJavascript(styleScript, null)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Error evaluating style script", e)
                }
                try {
                    view.evaluateJavascript(WebViewScripts.overlayAdRemoverScript, null)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Error evaluating overlay ad remover", e)
                }
            }
        }
        
        fun injectYouTubeAdBlock(view: WebView?, url: String?) {
            if (url == null) return
            val host = try { android.net.Uri.parse(url).host ?: "" } catch(e: Exception) { "" }
            if (!host.contains("youtube.com")) return
            val js = """
(function() {
    try {
    if (window._yue_yt_adblock) return;
    window._yue_yt_adblock = true;
    var _fetch = window.fetch.bind(window);
    window.fetch = function(r, o) {
        try {
        var u = (typeof r === 'string') ? r : (r && r.url ? r.url : '');
        if (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||(u.includes('googlevideo')&&u.includes('&ad='))||u.includes('/get_midroll')||u.includes('yt.ad')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('/videostats/playback')||u.includes('youtube.com/api/stats/ads')||(u.includes('youtube.com')&&u.includes('ads'))) {
            return Promise.resolve(new Response('',{status:204}));
        }
        } catch(e) {}
        return _fetch(r, o);
    };
    var _open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, u) {
        try {
        if (typeof u === 'string' && (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||(u.includes('googlevideo')&&u.includes('&ad='))||u.includes('/get_midroll')||u.includes('yt.ad')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('/videostats/playback')||u.includes('youtube.com/api/stats/ads')||(u.includes('youtube.com')&&u.includes('ads')))) {
            u = '//localhost/blocked?' + Date.now();
        }
        } catch(e) {}
        return _open.apply(this, arguments);
    };
    function hideSponsored() {
        try {
        var elements = document.querySelectorAll('ytm-rich-section-renderer, ytm-rich-item-renderer, ytm-item-section-renderer, ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, ytm-promoted-item-renderer, [class*="promoted"], [class*="sponsored"]');
        elements.forEach(function(el) {
            try {
            if (el.querySelector('.ytm-ad-badge') || el.querySelector('[class*="ad-badge"]') || el.querySelector('[class*="-ad-"]') || el.querySelector('.ytm-ad-label')) {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0', 'important');
                return;
            }
            var text = el.innerText || '';
            if (text.includes('Sponsored') || text.includes('Bersponsor') || text.includes('Iklan') || text.includes('Promoted') || text.includes('Sponsor')) {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0', 'important');
            }
            } catch(e) {}
        });
        } catch(e) {}
    }
    try {
    var obs = new MutationObserver(function(m) {
        try {
        for (var i = 0; i < m.length; i++) {
            for (var j = 0; j < m[i].addedNodes.length; j++) {
                var n = m[i].addedNodes[j];
                if (n.tagName === 'SCRIPT' && n.src && (n.src.includes('doubleclick')||n.src.includes('pagead')||n.src.includes('googleads')||n.src.includes('googlesyndication'))) {
                    try { n.remove(); } catch(e) {}
                }
            }
        }
        hideSponsored();
        } catch(e) {}
    });
    if (document.documentElement) obs.observe(document.documentElement, {childList:true, subtree:true});
    } catch(e) {}
    try {
    var s = document.createElement('style');
    s.id = 'yue-yt-adblock';
    s.textContent = 'ytd-video-masthead-ad-v3-renderer,ytd-ad-slot-renderer,ytd-action-companion-ad-renderer,ytd-promoted-video-renderer,ytd-in-feed-ad-layout-renderer,ytd-display-ad-renderer,ytd-banner-promo-renderer,ytd-video-ad,.video-ads,.ytp-ad-module,#masthead-ad,.ytp-ad-player-overlay,.ytp-ad-overlay-container,.ytp-ad-image-overlay,.ytp-ad-text-overlay,.ytd-companion-ad-renderer,.ad-container,.ytd-search-pyv-renderer,.ytp-ad-survey-layer,.ytp-ad-action-interrupt-slot,.ytp-ad-skip-button-container,.ytm-masthead-ad,.ytm-ad-badge,.ytm-promoted-video,.ytm-display-ad,.ytm-companion-ad,.ytm-ad-slot,.ytm-video-ad,.ytm-promoted-video-container,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-item-renderer,ytm-carousel-promoted-item-renderer,ytm-brand-video-singleton-renderer,ytm-brand-video-shelf-renderer,ytm-in-feed-ad-layout-renderer,ytm-ad-layout-renderer,ytm-sponsored-card,ytm-promoted-product-renderer,[class*="ytp-ad-"],[class*="ytm-ad-"],[class*="ad-container"],[class*="ad-badge"],[class*="promoted-video"],[class*="display-ad"],[id*="masthead-ad"]{display:none!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;z-index:-1!important;position:absolute!important;top:-9999px!important}';
    if (document.documentElement) document.documentElement.appendChild(s);
    } catch(e) {}
    var patchConfig = function() {
        try {
        if (window.yt && window.yt.config_ && window.yt.config_.INNERTUBE_CONTEXT && window.yt.config_.INNERTUBE_CONTEXT.client) {
            var c = window.yt.config_.INNERTUBE_CONTEXT.client;
            c.adSignals = undefined; try { delete c.adSignals; } catch(e) {}
        }
        if (window.yt && window.yt.config_) {
            try { window.yt.config_.adAcknowledge = undefined; delete window.yt.config_.adAcknowledge; } catch(e) {}
            try { window.yt.config_.adManager = undefined; delete window.yt.config_.adManager; } catch(e) {}
            try { window.yt.config_.adsense = undefined; delete window.yt.config_.adsense; } catch(e) {}
            try { window.yt.config_.pageid = undefined; delete window.yt.config_.pageid; } catch(e) {}
        }
        if (window.ytcfg) {
            try { window.ytcfg.set('ADS_ALLOWED', false); } catch(e) {}
        }
        } catch(e) {}
    };
    patchConfig();
    setInterval(patchConfig, 1500);
    setInterval(function() {
        try {
        var v = document.querySelector('video');
        if (!v) return;
        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad,.ytp-ad-skip-button-container button,button[class*="skip"],.ytp-ad-skip-button-slot,.ytm-skip-ad,.ytm-ad-skip-button');
        var hasSkipBtn = false;
        for (var i = 0; i < skipBtns.length && !hasSkipBtn; i++) {
            if (skipBtns[i].offsetParent !== null) { hasSkipBtn = true; }
        }
        var isAdVideo = v.classList.contains('ad-showing') || v.classList.contains('ad-interrupting');
        if (!isAdVideo && document.querySelector('.ytp-ad-player-overlay,.ytp-ad-module,.ad-showing,.ad-interrupting')) {
            isAdVideo = true;
        }
        if (isAdVideo || hasSkipBtn) {
            if (v.duration > 0) { try { v.currentTime = v.duration - 0.1; } catch(e) {} }
            if (v.paused) { try { v.play(); } catch(e) {} }
            for (var i = 0; i < skipBtns.length; i++) {
                if (skipBtns[i].offsetParent !== null) { try { skipBtns[i].click(); } catch(e) {} }
            }
        }
        hideSponsored();
        } catch(e) {}
    }, 250);
    } catch(e) {}
})();
            """.trimIndent()
            view?.post {
                try {
                    view.evaluateJavascript(js, null)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Error evaluating YouTube adblock", e)
                }
            }
        }

        fun injectTranslatorAddon(view: WebView?, url: String?, context: android.content.Context) {
            if (url == null) return
            val host = try { android.net.Uri.parse(url).host ?: "" } catch(e: Exception) { "" }
            if (host.contains("youtube")) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val cssStream = context.assets.open("addons/translator/styles.css")
                        val cssBytes = ByteArray(cssStream.available())
                        cssStream.read(cssBytes)
                        cssStream.close()
                        val cssString = try {
                            String(cssBytes, Charsets.UTF_8)
                                .replace("\\", "\\\\")
                                .replace("'", "\\'")
                                .replace("\"", "\\\"")
                                .replace("\n", " ")
                                .replace("\r", " ")
                                .replace("\t", " ")
                        } catch (e: Exception) {
                            android.util.Log.e("AdBlockManager", "Error escaping translator CSS", e)
                            ""
                        }

                        val jsStream = context.assets.open("addons/translator/content.js")
                        val jsBytes = ByteArray(jsStream.available())
                        jsStream.read(jsBytes)
                        jsStream.close()
                        val jsString = try { String(jsBytes, Charsets.UTF_8) } catch (e: Exception) {
                            android.util.Log.e("AdBlockManager", "Error reading translator JS", e)
                            ""
                        }

                        val injectScript = """
                        (function() {
                            try {
                            if (document.getElementById('yue-translator-style')) return;
                            var style = document.createElement('style');
                            style.id = 'yue-translator-style';
                            style.innerHTML = '$cssString';
                            document.head.appendChild(style);
                            $jsString
                            } catch(e) {}
                        })();
                        """.trimIndent()

                        launch(Dispatchers.Main) {
                            try {
                                view?.evaluateJavascript(injectScript, null)
                            } catch (e: Exception) {
                                android.util.Log.e("AdBlockManager", "Error evaluating translator script", e)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AdBlockManager", "Translator addon error", e)
                    }
                }
            }
        }

        }
