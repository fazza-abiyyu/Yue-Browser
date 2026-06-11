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

        fun initAdBlocker(context: Context) {
            adBlockHosts.clear()
            adBlockHosts.addAll(AdBlockManager.getAdDomains(context))
            genericSelectors.clear()
            genericSelectors.addAll(AdBlockManager.getAsset(context, "filters/default_generic_selectors.txt"))
            
            GlobalScope.launch(Dispatchers.IO) {
                whitelistHosts.clear()
                val file = File(context.filesDir, "adblock_hosts.txt")
                if (file.exists()) {
                    loadHostsFromFile(file)
                }
                
                val abpFile = File(context.filesDir, "abpindo_rules.txt")
                if (abpFile.exists()) {
                    loadABPindoFromFile(context, abpFile)
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
                            genericSelectors.addAll(AdBlockManager.getAsset(context, "filters/default_generic_selectors.txt"))
                            domainSelectors.clear()
                            wildcardDomainSelectors.clear()
                            loadABPindoFromFile(context, abpFile)
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
                                loadABPindoFromFile(context, easyListFile)
                            }
                        }
                    } else if (easyListFile.exists()) {
                        loadABPindoFromFile(context, easyListFile)
                    }
                } catch (e: Exception) {
                    // ignore network errors for EasyList
                }
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

        fun isHostBlocked(context: android.content.Context, host: String, settings: com.yue.browser.domain.model.BrowserSettings): Boolean {
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
                view.evaluateJavascript(WebViewScripts.overlayAdRemoverScript, null)
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

        }
