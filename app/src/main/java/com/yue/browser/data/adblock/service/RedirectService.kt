package com.yue.browser.data.adblock.service

import android.content.Context
import android.webkit.WebView
import com.yue.browser.domain.model.BrowserSettings
import java.util.Locale

/**
 * Service: Redirect and navigation blocking logic.
 * Extracted from AdBlockEngine for separation of concerns.
 */
class RedirectService(
    private val blockingService: BlockingService,
) {
    private val allowedRedirectDomains = hashSetOf(
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

    private val allowedPopupDomains = hashSetOf(
        "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", "x.com",
        "instagram.com", "github.com", "apple.com", "microsoft.com", "live.com", "disqus.com",
        "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
        "cloudflare.com", "cloudflareinsights.com"
    )

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

    private val oauthPathPatterns = listOf(
        "/oauth", "/oauth2", "/authorize", "/auth/", "/login",
        "/connect/", "/callback", "/sso", "/saml", "/openid",
        "/signin", "/sign-in", "/signup", "/sign-up",
        "/token", "/identity", "/account", "/session"
    )

    private val adUrlKeywords = listOf(
        "/ads/", "/advert/", "/popunder", "/popup-", "/ad-box", "-ad-300x250",
        "/googleads", "/pagead/", "googlesyndication.com", "doubleclick.net", "adsterra",
        "exoclick", "mgid.com", "propellerads", "adsystem", "popads", "popcash", "juicyads",
        "masonerthor", "onclickads", "connect.facebook.net/en_us/fbevents.js",
        "google-analytics.com/analytics.js"
    )

    fun isUrlRedirectingToBlocked(context: Context, url: String, settings: BrowserSettings?): Boolean {
        try {
            val uri = android.net.Uri.parse(url) ?: return false
            if (!uri.isHierarchical) return false

            // Check if the main host itself is blocked
            val host = uri.host ?: ""
            if (host.isNotEmpty()) {
                if (blockingService.isJudolHost(context, host) || blockingService.isHostBlocked(context, host, settings)) {
                    return true
                }
            }

            // Check query parameters for redirect targets
            val redirectParams = hashSetOf("q", "url", "link", "to", "redirect", "dest", "destination", "target", "go")
            val paramNames = uri.queryParameterNames
            for (paramName in paramNames) {
                if (!redirectParams.contains(paramName.lowercase(Locale.US))) {
                    continue
                }
                val value = uri.getQueryParameter(paramName) ?: continue
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    val targetUri = android.net.Uri.parse(value)
                    val targetHost = targetUri?.host ?: ""
                    if (targetHost.isNotEmpty()) {
                        if (blockingService.isJudolHost(context, targetHost) || blockingService.isHostBlocked(context, targetHost, settings)) {
                            android.util.Log.d("RedirectService", "Blocked redirect target URL in query parameter '$paramName': $targetHost")
                            return true
                        }
                    }
                } else {
                    val potentialHost = value.trim().lowercase(Locale.US)
                    if (potentialHost.contains(".") && !potentialHost.contains(" ")) {
                        if (blockingService.isJudolHost(context, potentialHost) || blockingService.isHostBlocked(context, potentialHost, settings)) {
                            android.util.Log.d("RedirectService", "Blocked redirect target host in query parameter '$paramName': $potentialHost")
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

    fun isSearchEngineWithJudolQuery(context: Context, url: String): Boolean {
        try {
            val uri = android.net.Uri.parse(url) ?: return false
            val host = uri.host ?: return false
            val lowerHost = host.lowercase(Locale.US)
            if (lowerHost.contains("google.") || lowerHost.contains("yahoo.") ||
                lowerHost.contains("bing.") || lowerHost.contains("duckduckgo.")
            ) {
                val qVal = uri.getQueryParameter("q") ?: uri.getQueryParameter("query") ?: ""
                if (qVal.isNotEmpty()) {
                    val lowerQ = qVal.lowercase(Locale.US)
                    val strictKeywords = readAssetLines(context, "filters/gambling_strict.txt")
                    val moderateKeywords = readAssetLines(context, "filters/gambling_moderate.txt")
                    val hasJudolKeyword = strictKeywords.any { lowerQ.contains(it) } ||
                        moderateKeywords.filter { lowerQ.contains(it) }.size >= 2
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

    fun isDownloadFileUrl(url: String): Boolean {
        val path = try {
            android.net.Uri.parse(url).lastPathSegment?.lowercase() ?: ""
        } catch (_: Exception) { return false }
        val dot = path.lastIndexOf('.')
        if (dot == -1 || dot >= path.length - 1) return false
        val ext = path.substring(dot)
        return ext in downloadExtensions
    }

    fun isOAuthOrLoginUrl(url: String, host: String): Boolean {
        val lower = url.lowercase()
        val lowerHost = host.lowercase()
        if (lowerHost.startsWith("accounts.") || lowerHost.startsWith("login.") ||
            lowerHost.startsWith("auth.") || lowerHost.startsWith("sso.") ||
            lowerHost.startsWith("id.") || lowerHost.startsWith("identity.")
        ) {
            return true
        }
        return oauthPathPatterns.any { lower.contains(it) }
    }

    fun isUrlPathBlocked(url: String): Boolean {
        val lowerUrl = url.lowercase(Locale.US)
        return adUrlKeywords.any { lowerUrl.contains(it) }
    }

    fun isThirdPartyRedirectBlocked(
        currentUrl: String?,
        targetUrl: String,
        targetHost: String,
        openerHost: String?,
        settings: BrowserSettings?,
        isAppNav: Boolean,
        hasGesture: Boolean,
        hitTestResult: WebView.HitTestResult?,
    ): Boolean {
        if (!blockingService.isAdblockActive(settings)) return false
        if (currentUrl == null || !currentUrl.startsWith("http")) return false

        val currentHost = try {
            android.net.Uri.parse(currentUrl).host ?: return false
        } catch (_: Exception) { return false }

        val currentBase = currentHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
        val targetBase = targetHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
        val isSameSite = currentBase == targetBase ||
            targetHost.endsWith(".$currentHost") || currentHost.endsWith(".$targetHost")

        val isOpenerSameSite = if (!openerHost.isNullOrEmpty()) {
            val openerBase = openerHost.lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
            targetBase == openerBase ||
                targetHost.endsWith(".$openerHost") || openerHost.endsWith(".$targetHost")
        } else false

        if (currentHost.isEmpty() || isSameSite || isOpenerSameSite) return false

        if (isOAuthOrLoginUrl(targetUrl, targetHost) || isOAuthOrLoginUrl(currentUrl, currentHost)) return false

        val hitType = hitTestResult?.type ?: WebView.HitTestResult.UNKNOWN_TYPE
        val isRealLink = hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
            hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val isDownloadUrl = isDownloadFileUrl(targetUrl)
        val isWhitelisted = allowedRedirectDomains.any { targetHost == it || targetHost.endsWith(".${it}") }

        // Check if target URL is a redirect chain (URL shortener with encoded target in query param)
        // e.g. shrinkearn.com/full?url=base64encoded or adf.ly/f/?url=...
        val isRedirectChain = isRedirectChain(targetUrl)

        // Check if any query param looks like a download link
        val redirectChainHasDownload = isRedirectChainWithDownload(targetUrl)

        if (isRedirectChain || redirectChainHasDownload) return false

        return !isAppNav && !isRealLink && !hasGesture && !isDownloadUrl && !isWhitelisted
    }

    private fun isRedirectChain(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val params = uri.queryParameterNames
            if (params.isEmpty()) return false
            params.any { param ->
                val value = uri.getQueryParameter(param) ?: return@any false
                // URL-encoded redirect: http:// or https:// in param
                if (value.startsWith("http://") || value.startsWith("https://")) return@any true
                // Base64-encoded redirect: try decode and check if it starts with http
                if (value.length > 20 && value.length % 4 == 0 && value.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                    try {
                        val decoded = android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val decodedStr = String(decoded, Charsets.UTF_8)
                        if (decodedStr.startsWith("http://") || decodedStr.startsWith("https://")) return@any true
                    } catch (_: Exception) { }
                }
                false
            }
        } catch (_: Exception) { false }
    }

    private fun isRedirectChainWithDownload(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            val params = uri.queryParameterNames
            if (params.isEmpty()) return false
            params.any { param ->
                val value = uri.getQueryParameter(param) ?: return@any false
                isDownloadFileUrl(value)
            }
        } catch (_: Exception) { false }
    }

    fun shouldBlockScriptPopupNavigation(
        openerHost: String?,
        targetHost: String,
        isScriptPopup: Boolean,
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
        isUserGesture: Boolean,
    ): Boolean {
        if (isUserGesture) return false
        return allowedPopupDomains.none { currentHost == it || currentHost.endsWith(".$it") }
    }

    private fun readAssetLines(context: Context, path: String): List<String> {
        return try {
            context.assets.open(path)
                .bufferedReader()
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            emptyList()
        }
    }
}