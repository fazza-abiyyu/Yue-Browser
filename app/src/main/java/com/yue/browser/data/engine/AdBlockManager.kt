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
            if (isAdBlockerInitialized) {
                return
            }
            isAdBlockerInitialized = true

            // === SYNC BLOCK (selesai sebelum WebView mulai request): ===
            // Data SYNC = default filters dari assets (pasti tersedia).
            // Data ini adalah "safety net" — bahkan jika ASYNC gagal/terlambat,
            // adblock tetap aktif untuk host dan selector umum.
            adBlockHosts.clear()
            adBlockHosts.addAll(AdBlockManager.getAdDomains(context))

            whitelistHosts.clear()
            whitelistHosts.addAll(listOf(
                "html-load.com", "html-load.cc", "css-load.com", "content-loader.com", "img-load.com"
            ))

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
                "masonerthor.com", "ibo88.com", "ibo88.fun", "ibo88.net", "ibo88.org", "adsystem.com",
                "connect.facebook.net",
                "cdn.api.twitter.com",
                "admob.com", "app-measurement.com",
                "googletagservices.com", "googlesynergy.com",
                "smartadserver.com", "openx.net", "rubiconproject.com",
                "adnxs.com", "advertising.com",
                "taboola.com", "outbrain.com", "content.ad",
                "zedo.com", "zedo.net", "adsnative.com", "adsafeprotected.com",
                "scorecardresearch.com", "quantserve.com", "chartbeat.com",
                "hotjar.com", "mouseflow.com", "luckyorange.com",
                "wpstats.com", "pixel.wp.com",
                "adnow.com", "adnowmedia.com", "megapush.com", "pushnami.com",
                "pushalert.co", "onesignal.com", "onesignal.net",
                "statcounter.com", "c.statcounter.com", "histats.com",
                "acdn.adnxs.com", "cdn.adnxs.com", "prebid.adnxs.com",
                "secure.adnxs.com", "ib.adnxs-simple.com",
                "static.criteo.net", "widgets.outbrain.com", "cdn.taboola.com",
                "trc.taboolasyndication.com", "trc.taboola.com", "cdn.taboolasyndication.com",
                "api.taboola.com", "disq.us", "links.services.disqus.com",
                "referrer.disqus.com",
                // Video outstream ads and sponsored player networks
                "teads.tv", "teads.com", "a.teads.tv", "aniview.com", "cdn.aniview.com",
                "player.aniview.com", "connatix.com", "elements.connatix.com", "cnx.io",
                "api.cnx.io", "primis.tech", "primis.tv", "live.primis.tech", "ex.co",
                "player.ex.co", "vidoomy.com", "playwire.com", "config.playwire.com",
                "spotxchange.com", "spotx.tv", "springserve.com", "anyclip.com",
                "player.anyclip.com", "tremorvideo.com", "brid.tv", "services.brid.tv",
                "video-clump.com", "taboolasyndication.com", "outbrainimg.com",
                "log.outbrain.com", "odb.outbrain.com", "servicer.mgid.com",
                "jsc.mgid.com", "mgid.ru", "revcontent.com", "assets.revcontent.com",
                "trends.revcontent.com", "adblade.com", "web.adblade.com", "api.content.ad",
                "innity.com", "innity.net", "yieldmo.com", "gumgum.com", "undertone.com",
                "sovrn.com", "lijit.com", "infolinks.com", "buysellads.com",
                "nativeads.com", "media.net", "adcolony.com", "unityads.unity3d.com",
                "applovin.com", "ironsrc.com", "vungle.com", "chartboost.com", "adform.net"
            ))
            genericSelectors.clear()
            genericSelectors.addAll(AdBlockManager.getAsset(context, "filters/default_generic_selectors.txt"))
            // Fallback hardcoded: selector umum untuk popup/iklan di situs berita Indonesia dan luar negeri
            genericSelectors.addAll(listOf(
                "div.ad-box", "div.ad-container", "div.ad-slot", "div.ad-wrapper",
                "div#ad-box", "div#ad-container", "div#ad-slot", "div#ad-wrapper",
                "div.ads-box", "div.ads-container", "div.ads-slot", "div.ads-wrapper",
                "div#ads-box", "div#ads-container", "div#ads-slot", "div#ads-wrapper",
                "div.google-ads", "div#google-ads",
                "ins[class*='adsbygoogle']", "ins.adsbygoogle", "ins[data-ad-client]", "ins[data-ad-slot]",
                "iframe[src*='doubleclick']", "iframe[src*='googlesyndication']", "iframe[src*='googleads']",
                "iframe[src*='adnxs']", "iframe[src*='criteo']", "iframe[src*='mgid']",
                "iframe[src*='popads']", "iframe[src*='popcash']", "iframe[src*='propeller']",
                "iframe[src*='exoclick']", "iframe[src*='adsterra']",
                "iframe[src*='.cfd/']", "iframe[src*='.cyou/']", "iframe[src*='.clickase/']",
                "iframe[src*='epigynylirate']",
                "script[src*='adsbygoogle']", "script[src*='doubleclick']", "script[src*='googlesyndication']",
                "script[src*='googleads']", "script[src*='google-analytics']", "script[src*='googletagmanager']",
                "a[href*='adsystem']", "a[href*='popads']", "a[href*='popcash']", "a[href*='onclickads']",
                "a[href*='exoclick']", "a[href*='adsterra']", "a[href*='propellerads']", "a[href*='mgid']",
                "img[src*='ad.doubleclick']", "img[src*='googleads']", "img[src*='ads.googlesyndication']",
                "img[src*='criteo']", "img[src*='mgid']",
                "aside[id*='widget-ads']", "aside[class*='widget-ads']", "aside[id*='widget_ads']", "aside[class*='widget_ads']",
                "div[class*='widget-ads']", "div[id*='widget-ads']",
                "div[data-ad-status]", "[data-block-type='ad']", "[data-widget-type='ad']",
                // Sponsored widgets (Taboola, Outbrain, MGID, Revcontent, Content.ad, Adblade)
                "div[id^='taboola-']", "div.trc_rbox", ".taboola-widget", ".taboola-placeholder", "div.trc_related_container", "div.trc_rbox_container", "div.taboola-ad",
                ".OUTBRAIN", "div[id^='outbrain_']", ".outbrain-widget", ".outbrain-placeholder",
                "div[id^='mgid_']", ".mgid-widget", "div[class*='mgid-']",
                "div[id^='rcjsload']", ".revcontent-widget", "div[class*='revcontent']",
                "div[id^='adblade']", ".adblade-widget",
                "div[id^='contentad']", ".content-ad-widget",
                "div[id^='zadv']", ".zedo-ad",
                // Video Outstream Players (Teads, Primis, AnyClip, Connatix, Ex.co, Playwire, Brid.tv, Vidoomy)
                "div[id^='teads']", ".teads-player", ".teads-ad", "div[class*='teads-']",
                "div[id^='primis']", ".primis-player-container", ".primis_container",
                "div[id^='anyclip']", ".anyclip-player", "div#anyclip-widget", "div[class*='anyclip-']",
                "div[id^='cnx-']", ".cnx-player", "iframe[src*='connatix.com']",
                "div[id^='exco']", ".exco-player", "div[class*='exco-']",
                "div[id^='pw-']", ".pw-player", "div[id*='playwire']", "div[class*='playwire-']",
                "div[id^='brid-']", ".brid-player",
                "div[id^='vidoomy']", "div[class*='vidoomy']",
                // Generic video ads & sticky players
                "div[class*='outstream-player']", "div[class*='outstream-video']", "div[class*='outstream-ad']", "div[id*='outstream']",
                "div[class*='sticky-player']", "div[class*='sticky-video']", "div[class*='floating-player']", "div[class*='floating-video']",
                "div[class*='video-ad-container']", "div[class*='video-ad-player']", "div[id*='video-ad']", "div[class*='video-ad-']",
                // Sponsored & Promoted Ad elements
                "div[class*='sponsored']", "div[id*='sponsored']", "section[class*='sponsored']", "li[class*='sponsored']",
                "div[class*='promoted']", "div[id*='promoted']", "section[class*='promoted']", "li[class*='promoted']",
                "div[class*='sponsorship']", "div[id*='sponsorship']",
                "[data-testid*='sponsored']", "[data-testid*='promoted']",
                "[aria-label*='sponsored']", "[aria-label*='promoted']",
                "div[class*='ad-label']", "span[class*='ad-label']",
                "div[class*='sponsored-post']", "div[class*='sponsored-ad']", "div[class*='promoted-ad']"
            ))
            android.util.Log.d("AdBlockManager", "SYNC: adBlockHosts=${adBlockHosts.size}, genericSelectors=${genericSelectors.size}")

            // === ASYNC BLOCK (load dari file lokal, SYNC block handle download): ===
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

                val annoyanceFile = File(context.filesDir, "fanboy_annoyance.txt")
                if (annoyanceFile.exists()) {
                    loadABPindoFromFile(context, annoyanceFile)
                }

                val antiAdblockFile = File(context.filesDir, "anti_adblock_rules.txt")
                if (antiAdblockFile.exists()) {
                    loadABPindoFromFile(context, antiAdblockFile)
                }

                android.util.Log.d("AdBlockManager", "ASYNC load selesai: adBlockHosts=${adBlockHosts.size}, genericSelectors=${genericSelectors.size}, domainSelectors=${domainSelectors.size}")
            }
        }

        fun syncFilters(context: Context, onComplete: (() -> Unit)? = null) {
            syncFiltersOnLaunch(context, onComplete)
        }

        fun syncFiltersOnLaunch(context: Context, onComplete: (() -> Unit)? = null) {
            val filterLists = listOf(
                FilterListConfig(
                    url = "https://raw.githubusercontent.com/ABPindo/indonesianadblockrules/master/subscriptions/abpindo.txt",
                    fileName = "abpindo_rules.txt",
                    minSize = 1000,
                    timeout = 15000
                ),
                FilterListConfig(
                    url = "https://easylist.to/easylist/easylist.txt",
                    fileName = "easylist_rules.txt",
                    minSize = 10000,
                    timeout = 20000
                ),
                FilterListConfig(
                    url = "https://easylist.to/easylist/fanboy-annoyance.txt",
                    fileName = "fanboy_annoyance.txt",
                    minSize = 1000,
                    timeout = 20000
                ),
                FilterListConfig(
                    url = "https://easylist-downloads.adblockplus.org/antiadblockfilters.txt",
                    fileName = "anti_adblock_rules.txt",
                    minSize = 1000,
                    timeout = 20000
                )
            )

            var completedCount = 0
            filterLists.forEach { config ->
                GlobalScope.launch(Dispatchers.IO) {
                    syncSingleFilterList(context, config)
                    synchronized(this@AdBlockManager) {
                        completedCount++
                        if (completedCount == filterLists.size) {
                            android.util.Log.d("AdBlockManager", "Sync on launch selesai: adBlockHosts=${adBlockHosts.size}")
                            onComplete?.invoke()
                        }
                    }
                }
            }
        }

        private data class FilterListConfig(
            val url: String,
            val fileName: String,
            val minSize: Long,
            val timeout: Int
        )

        private fun syncSingleFilterList(context: Context, config: FilterListConfig) {
            try {
                val outputFile = File(context.filesDir, config.fileName)
                val twelveHoursMs = 12 * 60 * 60 * 1000L
                if (outputFile.exists() && (System.currentTimeMillis() - outputFile.lastModified()) < twelveHoursMs) {
                    loadABPindoFromFile(context, outputFile)
                    return
                }

                val url = URL(config.url)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = config.timeout
                connection.readTimeout = config.timeout + 10000
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (connection.responseCode == 200) {
                    val tempFile = File(context.filesDir, "${config.fileName}.tmp")
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > config.minSize) {
                        tempFile.renameTo(outputFile)
                        loadABPindoFromFile(context, outputFile)
                    } else if (outputFile.exists()) {
                        loadABPindoFromFile(context, outputFile)
                    }
                } else if (outputFile.exists()) {
                    loadABPindoFromFile(context, outputFile)
                }
            } catch (e: Exception) {
                try {
                    val outputFile = File(context.filesDir, config.fileName)
                    if (outputFile.exists()) {
                        loadABPindoFromFile(context, outputFile)
                    }
                } catch (_: Exception) {}
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
                if (!s.contains(".") && !s.contains("#") && !s.contains(":") && !s.contains("[")) {
                    return true
                }
            }
            return false
        }

        private val MAX_HOST_RULES = 100000
        private val MAX_SELECTOR_RULES = 100000

        fun loadABPindoFromFile(context: android.content.Context, file: File) {
            try {
                var lineCount = 0
                file.forEachLine { line ->
                    lineCount++
                    if (adBlockHosts.size >= MAX_HOST_RULES) return@forEachLine
                    if (domainSelectors.size >= MAX_SELECTOR_RULES) return@forEachLine
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
            // Skip cosmetic injection for DRM-protected streaming sites \u2014 DOM manipulation
            // can trigger playback protection checks (e.g. Spotify "Playback dinonaktifkan")
            if (host.contains("youtube.com") || host.contains("spotify.com") ||
                host.contains("netflix.com") || host.contains("disneyplus.com") ||
                host.contains("primevideo.com") || host.contains("hulu.com") ||
                host.contains("apple.com")) return ""
            
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
            val lowercaseHost = host.toLowerCase(Locale.US)
            if (settings != null) {
                val cleanHost = lowercaseHost.removePrefix("www.").removePrefix("m.")
                val isWhitelisted = settings.adblockWhitelistedDomains.any {
                    cleanHost == it || cleanHost.endsWith(".$it")
                }
                if (isWhitelisted) return false
            }
            
            // SELALU cek adBlockHosts — tidak bergantung pada flag isAdBlockEnabled.
            // Ini mencegah race condition saat settingsFlow.value = default state.
            
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
            
            // Hardcoded brand-name keyword fallback: catch ibo88.fun, ibo88.xyz, etc.
            // regardless of TLD. These are very specific, low false-positive risk.
            val brandKeywords = hashSetOf(
                "ibo88", "dewaslot", "rajaslot", "judol88", "bosslot", "bossjudi",
                "gacortoto", "slotonline888", "agenjudi88", "bandar88", "mpo88",
                "togel123", "togel4d", "joker123", "joker388", "spadegaming"
            )
            if (brandKeywords.any { lowerHost.contains(it) }) return true
            
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

        fun isUrlRedirectingToBlocked(context: android.content.Context, url: String, settings: com.yue.browser.domain.model.BrowserSettings? = null): Boolean {
            try {
                val uri = android.net.Uri.parse(url) ?: return false
                if (!uri.isHierarchical) return false
                
                // 1. Check if the main host itself is blocked
                val host = uri.host ?: ""
                if (host.isNotEmpty()) {
                    if (isJudolHost(context, host) || isHostBlocked(context, host, settings)) {
                        return true
                    }
                }
                
                // 2. Check query parameters for redirect targets
                val redirectParams = hashSetOf("q", "url", "link", "to", "redirect", "dest", "destination", "target", "go")
                val paramNames = uri.queryParameterNames
                for (paramName in paramNames) {
                    if (!redirectParams.contains(paramName.toLowerCase(Locale.US))) {
                        continue
                    }
                    val value = uri.getQueryParameter(paramName) ?: continue
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        val targetUri = android.net.Uri.parse(value)
                        val targetHost = targetUri?.host ?: ""
                        if (targetHost.isNotEmpty()) {
                            if (isJudolHost(context, targetHost) || isHostBlocked(context, targetHost, settings)) {
                                android.util.Log.d("AdBlockManager", "Blocked redirect target URL in query parameter '$paramName': $targetHost")
                                return true
                            }
                        }
                    } else {
                        // Check if the parameter value is a raw hostname (e.g., q=dewaslot88.com)
                        val potentialHost = value.trim().toLowerCase(Locale.US)
                        if (potentialHost.contains(".") && !potentialHost.contains(" ")) {
                            if (isJudolHost(context, potentialHost) || isHostBlocked(context, potentialHost, settings)) {
                                android.util.Log.d("AdBlockManager", "Blocked redirect target host in query parameter '$paramName': $potentialHost")
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Safe fallback
            }
            return false
        }

        fun isSearchEngineWithJudolQuery(context: android.content.Context, url: String): Boolean {
            try {
                val uri = android.net.Uri.parse(url) ?: return false
                val host = uri.host ?: return false
                val lowerHost = host.toLowerCase(Locale.US)
                if (lowerHost.contains("google.") || lowerHost.contains("yahoo.") || lowerHost.contains("bing.") || lowerHost.contains("duckduckgo.")) {
                    val qVal = uri.getQueryParameter("q") ?: uri.getQueryParameter("query") ?: ""
                    if (qVal.isNotEmpty()) {
                        val lowerQ = qVal.toLowerCase(Locale.US)
                        val verySpecificGambling = getAsset(context, "filters/gambling_strict.txt")
                        val moderateGambling = getAsset(context, "filters/gambling_moderate.txt")
                        val hasJudolKeyword = verySpecificGambling.any { lowerQ.contains(it) } || 
                                              moderateGambling.filter { lowerQ.contains(it) }.size >= 2
                        if (hasJudolKeyword) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            return false
        }

        val allowedRedirectDomains = hashSetOf(
            "google.com", "google.co.id", "gstatic.com", "googleapis.com", "accounts.google.com",
            "facebook.com", "facebook.net", "fbcdn.net",
            "twitter.com", "x.com", "twimg.com",
            "instagram.com",
            "github.com", "github.io",
            "apple.com", "appleid.apple.com",
            "microsoft.com", "live.com", "microsoftonline.com", "login.microsoftonline.com",
            "yahoo.com", "login.yahoo.com",
            "discord.com",
            "whatsapp.com",
            "line.me",
            "tiktok.com",
            "spotify.com", "accounts.spotify.com", "open.spotify.com",
            "auth0.com", "okta.com", "onelogin.com", "pingidentity.com",
            "salesforce.com",
            "amazon.com", "amazon.co.id", "sellercentral.amazon.com",
            "disqus.com", "disquscdn.com",
            "reddit.com", "stackoverflow.com",
            "wikipedia.org",
            "youtube.com", "youtu.be",
            "speedtest.net", "ookla.com", "fast.com", "openspeedtest.com", "testmy.net",
            "cloudflare.com", "cloudflareinsights.com", "akamaized.net"
        )

        val allowedPopupDomains = hashSetOf(
            "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", "x.com",
            "instagram.com", "github.com", "apple.com", "microsoft.com", "live.com", "disqus.com",
            "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
            "cloudflare.com", "cloudflareinsights.com"
        )

        private val oauthPathPatterns = listOf(
            "/oauth", "/oauth2", "/authorize", "/auth/", "/login",
            "/connect/", "/callback", "/sso", "/saml", "/openid",
            "/signin", "/sign-in", "/signup", "/sign-up",
            "/token", "/identity", "/account", "/session"
        )

        fun isOAuthOrLoginUrl(url: String, host: String): Boolean {
            val lower = url.lowercase()
            val lowerHost = host.lowercase()
            if (lowerHost.startsWith("accounts.") || lowerHost.startsWith("login.") ||
                lowerHost.startsWith("auth.") || lowerHost.startsWith("sso.") ||
                lowerHost.startsWith("id.") || lowerHost.startsWith("identity.")) {
                return true
            }
            return oauthPathPatterns.any { lower.contains(it) }
        }

        private val downloadExtensions = setOf(
            ".apk", ".aab", ".xapk", ".apks",
            ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".mp3", ".mp4", ".m4a", ".m4v", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".webm",
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg",
            ".exe", ".msi", ".deb", ".rpm", ".dmg",
            ".iso", ".img",
            ".epub", ".mobi", ".azw3",
            ".csv", ".json", ".xml",
            ".torrent",
            ".crx", ".xpi"
        )

        fun isDownloadFileUrl(url: String): Boolean {
            val path = try {
                android.net.Uri.parse(url).lastPathSegment?.lowercase() ?: ""
            } catch (_: Exception) { return false }
            val dot = path.lastIndexOf('.')
            if (dot == -1 || dot >= path.length - 1) return false
            val ext = path.substring(dot)
            return ext in downloadExtensions
        }

        fun isAdblockActive(settings: com.yue.browser.domain.model.BrowserSettings?): Boolean {
            return settings != null && (settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock"))
        }

        fun isCustomFilterBlocked(host: String, settings: com.yue.browser.domain.model.BrowserSettings?): Boolean {
            if (settings == null || !settings.isAdBlockEnabled) return false
            val lowercaseHost = host.lowercase(Locale.US)
            return settings.customAdBlockFilters.any {
                lowercaseHost == it || lowercaseHost.endsWith(".$it")
            }
        }

        fun isThirdPartyRedirectBlocked(
            currentUrl: String?,
            targetUrl: String,
            targetHost: String,
            openerHost: String?,
            settings: com.yue.browser.domain.model.BrowserSettings?,
            isAppNav: Boolean,
            hasGesture: Boolean,
            hitTestResult: android.webkit.WebView.HitTestResult?
        ): Boolean {
            if (!isAdblockActive(settings)) return false
            if (currentUrl == null || !currentUrl.startsWith("http")) return false

            val currentHost = try {
                android.net.Uri.parse(currentUrl).host ?: return false
            } catch (_: Exception) { return false }

            val currentBase = currentHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            val targetBase = targetHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            val isSameSite = currentBase == targetBase || targetHost.endsWith(".$currentHost") || currentHost.endsWith(".$targetHost")

            val isOpenerSameSite = if (!openerHost.isNullOrEmpty()) {
                val openerBase = openerHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
                targetBase == openerBase || targetHost.endsWith(".$openerHost") || openerHost.endsWith(".$targetHost")
            } else false

            if (currentHost.isEmpty() || isSameSite || isOpenerSameSite) return false

            if (isOAuthOrLoginUrl(targetUrl, targetHost) || isOAuthOrLoginUrl(currentUrl, currentHost)) return false

            val hitType = hitTestResult?.type ?: android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
            val isRealLink = hitType == android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                             hitType == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val isDownloadUrl = isDownloadFileUrl(targetUrl)
            val isWhitelisted = allowedRedirectDomains.any { targetHost == it || targetHost.endsWith(".${it}") }

            return !isAppNav && !isRealLink && !hasGesture && !isDownloadUrl && !isWhitelisted
        }

        fun shouldBlockScriptPopupNavigation(
            openerHost: String?,
            targetHost: String,
            isScriptPopup: Boolean
        ): Boolean {
            if (openerHost.isNullOrEmpty() || !isScriptPopup) return false
            val destHost = targetHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            val cleanOpener = openerHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            val isSameDomain = destHost == cleanOpener || destHost.endsWith(".$cleanOpener")
            if (isSameDomain) return false
            return allowedPopupDomains.none { destHost == it || destHost.endsWith(".$it") }
        }

        fun shouldBlockPopup(
            currentHost: String,
            isUserGesture: Boolean
        ): Boolean {
            if (isUserGesture) return false
            return allowedPopupDomains.none { currentHost == it || currentHost.endsWith(".$it") }
        }


        fun injectCosmeticFilters(context: android.content.Context, view: WebView?, url: String?, settings: com.yue.browser.domain.model.BrowserSettings? = null) {
            val currentSettings = settings ?: com.yue.browser.data.repository.SettingsRepositoryImpl.instance.settingsFlow.value
            if (!currentSettings.isAdBlockEnabled) return
            if (url != null) {
                val host = try { android.net.Uri.parse(url).host?.lowercase(Locale.US) ?: "" } catch(e: Exception) { "" }
                val cleanHost = host.removePrefix("www.").removePrefix("m.")
                if (cleanHost.isNotEmpty() && currentSettings.adblockWhitelistedDomains.any {
                    cleanHost == it || cleanHost.endsWith(".$it")
                }) {
                    return
                }
            }
            val css = getCosmeticCSS(context, url, currentSettings)
            val styleScript = if (css.isNotBlank()) {
                val cssToQuote = css.take(150000)
                val quotedCss = try {
                    org.json.JSONObject.quote(cssToQuote)
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Error quoting CSS", e)
                    "\"\""
                }
                """
                (function() {
                    try {
                        var css = $quotedCss;
                        var selectors = css.split('{}').filter(function(s) { return s.trim().length > 0; }).map(function(s) { return s.trim(); });
                        
                        function injectStyle() {
                            try {
                                if (document.head) {
                                    var style = document.getElementById('yue-adblock-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'yue-adblock-style';
                                        style.textContent = css;
                                        document.head.appendChild(style);
                                    } else if (style.textContent !== css) {
                                        style.textContent = css;
                                    }
                                }
                            } catch(e) {}
                        }
                        
                        function hideMatchingElements() {
                            try {
                                for (var i = 0; i < selectors.length; i++) {
                                    try {
                                        var els = document.querySelectorAll(selectors[i]);
                                        for (var j = 0; j < els.length; j++) {
                                            els[j].style.display = 'none !important';
                                            els[j].style.visibility = 'hidden !important';
                                            els[j].style.opacity = '0 !important';
                                        }
                                    } catch(e) {}
                                }
                            } catch(e) {}
                        }
                        
                        injectStyle();
                        hideMatchingElements();
                        
                        if (!window.yueCosmeticObserver) {
                            window.yueCosmeticObserver = new MutationObserver(function(mutations) {
                                try {
                                    hideMatchingElements();
                                } catch(e) {}
                            });
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
        if (u.includes('youtubei/v1/player') || u.includes('youtubei/v1/browse') || u.includes('youtubei/v1/next') || u.includes('googlevideo.com') || u.includes('ytimg.com')) {
            return _fetch(r, o);
        }
        if (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||u.includes('/get_midroll')||u.includes('yt.ads')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('youtube.com/api/stats/ads')||u.includes('youtube.com/pagead/')) {
            return Promise.resolve(new Response('',{status:204}));
        }
        } catch(e) {}
        return _fetch(r, o);
    };
    var _open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, u) {
        try {
        if (typeof u === 'string') {
            if (u.includes('youtubei/v1/player') || u.includes('youtubei/v1/browse') || u.includes('youtubei/v1/next') || u.includes('googlevideo.com') || u.includes('ytimg.com')) {
                return _open.apply(this, arguments);
            }
            if (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||u.includes('/get_midroll')||u.includes('yt.ads')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('youtube.com/api/stats/ads')||u.includes('youtube.com/pagead/')) {
                u = '//localhost/blocked?' + Date.now();
            }
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
    function hideAdUI() {
        try {
        var p = document.querySelector('.html5-video-player')||document.querySelector('.ytd-player')||document.querySelector('.ytm-video-player');
        var isAd = p && (p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'));
        var v = document.querySelector('video');
        if (isAd) {
            if (v) { v.style.setProperty('opacity','0','important'); v.style.setProperty('pointer-events','none','important'); }
            if (p) { p.style.setProperty('background','transparent','important'); }
            var adE = p.querySelectorAll('.ytp-ad-player-overlay,.ytp-ad-overlay-container,.ytp-ad-text-overlay,.ytp-ad-image-overlay,.ytp-ad-action-interrupt-slot,.ytp-ad-survey-layer,.ytp-ad-progress,.ytp-ad-text,.ytp-ad-badge');
            for (var i=0;i<adE.length;i++){adE[i].style.setProperty('display','none','important');}
        } else {
            if (v) { v.style.setProperty('opacity','1','important'); v.style.removeProperty('pointer-events'); }
            if (p) { p.style.removeProperty('background'); }
        }
        } catch(e) {}
    }
    function checkAndSkipAd() {
        try {
        var v = document.querySelector('video');
        if (!v) return;
        var player = document.querySelector('.html5-video-player') || document.querySelector('.ytd-player') || document.querySelector('.ytm-video-player');
        var isAdVideo = false;
        if (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
            isAdVideo = true;
        }
        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad,.ytp-ad-skip-button-container button,.ytm-skip-ad,.ytm-ad-skip-button');
        var hasSkipBtn = false;
        for (var i = 0; i < skipBtns.length; i++) {
            if (skipBtns[i].offsetParent !== null) { 
                hasSkipBtn = true; 
                try { skipBtns[i].click(); } catch(e) {}
            }
        }
        if (isAdVideo || hasSkipBtn) {
            if (v.duration > 0 && v.currentTime < v.duration - 0.5) { 
                try { v.currentTime = v.duration - 0.1; } catch(e) {} 
            }
            if (v.paused) { try { v.play(); } catch(e) {} }
        }
        hideAdUI();
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
        checkAndSkipAd();
        } catch(e) {}
    });
    if (document.documentElement) obs.observe(document.documentElement, {childList:true, subtree:true});
    } catch(e) {}
    try {
    var s = document.createElement('style');
    s.id = 'yue-yt-adblock';
    s.textContent = 'ytd-video-masthead-ad-v3-renderer,ytd-ad-slot-renderer,ytd-action-companion-ad-renderer,ytd-promoted-video-renderer,ytd-in-feed-ad-layout-renderer,ytd-display-ad-renderer,ytd-banner-promo-renderer,ytd-video-ad,.video-ads,.ytp-ad-module,#masthead-ad,.ytp-ad-image-overlay,.ytp-ad-text-overlay,.ytd-companion-ad-renderer,.ytd-search-pyv-renderer,.ytp-ad-survey-layer,.ytp-ad-action-interrupt-slot,.ytm-masthead-ad,.ytm-ad-badge,.ytm-promoted-video,.ytm-display-ad,.ytm-companion-ad,.ytm-ad-slot,.ytm-video-ad,.ytm-promoted-video-container,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-item-renderer,ytm-carousel-promoted-item-renderer,ytm-brand-video-singleton-renderer,ytm-brand-video-shelf-renderer,ytm-in-feed-ad-layout-renderer,ytm-ad-layout-renderer,ytm-sponsored-card,ytm-promoted-product-renderer,.ytp-ad-player-overlay,.ytp-ad-overlay-container,.ytp-ad-progress,.ytp-ad-text,#player-ads,.ytp-ad-notification,.ytp-ad-visit-website-button,.ytp-ad-badge,.ytp-ad-button,ytm-rich-item-renderer:has(.ytm-ad-badge),ytm-rich-section-renderer:has(.ytm-ad-badge),ytm-item-section-renderer:has(.ytm-ad-badge),ytm-rich-item-renderer:has([class*="ad-badge"]),ytm-rich-section-renderer:has([class*="ad-badge"]),ytm-item-section-renderer:has([class*="ad-badge"]),ytm-rich-item-renderer:has(.ytm-ad-label),ytm-rich-section-renderer:has(.ytm-ad-label),ytm-item-section-renderer:has(.ytm-ad-label){display:none!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;z-index:-1!important;position:absolute!important;top:-9999px!important}';
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
    setInterval(patchConfig, 1000);
    
    // Event-driven video ad skipping
    document.addEventListener('play', checkAndSkipAd, true);
    document.addEventListener('playing', checkAndSkipAd, true);
    document.addEventListener('timeupdate', checkAndSkipAd, true);
    
    setInterval(function() {
        checkAndSkipAd();
        hideSponsored();
    }, 100);
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
                        val rawCss = String(cssBytes, Charsets.UTF_8)
                        val quotedCss = try {
                            org.json.JSONObject.quote(rawCss)
                        } catch (e: Exception) {
                            android.util.Log.e("AdBlockManager", "Error quoting translator CSS", e)
                            "\"\""
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
                            style.textContent = $quotedCss;
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
