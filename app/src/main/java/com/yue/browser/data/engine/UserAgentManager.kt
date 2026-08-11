package com.yue.browser.data.engine

object UserAgentManager {
    // === USER-AGENT CHROME MOBILE STANDAR (TANPA NAMA DEVICE) ===
    // Chrome Android asli format (Chrome 136+, pakai User-Agent Reduction):
    //   Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36
    // "K" adalah placeholder device generic sejak Chrome 110 (User-Agent Reduction policy).
    // Tidak ada nama device / Build ID → lebih privat dan tidak terdeteksi WebView.
    // WebView lama pakai "; wv" → DI-BLOCK Cloudflare → kita tidak pakai itu.
    private const val CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
    // Standard Chrome on Android with device model (needed by Spotify, Netflix, etc.)
    private const val CHROME_MOBILE_STANDARD_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
    private const val CHROME_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"


    fun init(context: android.content.Context) {
        // no-op: kept for backward compatibility
    }

    fun getExpectedUserAgent(currentUrl: String, isDesktopMode: Boolean, settings: com.yue.browser.domain.model.BrowserSettings): String {
        val host = try { android.net.Uri.parse(currentUrl).host ?: "" } catch (e: Exception) { "" }
        val baseDomain = host.removePrefix("m.").removePrefix("www.")
        val isDesktopForDomain = baseDomain.isNotEmpty() && settings.desktopDomains.contains(baseDomain)

        val isStreamingService = currentUrl.contains("open.spotify.com") || currentUrl.contains("netflix.com")

        if (isStreamingService) return CHROME_MOBILE_STANDARD_UA

        return if (isDesktopMode || isDesktopForDomain) {
            CHROME_DESKTOP_UA
        } else {
            CHROME_MOBILE_UA
        }
    }

    fun getDefaultMobileUA(): String = CHROME_MOBILE_UA
    fun getDefaultDesktopUA(): String = CHROME_DESKTOP_UA
    fun getDefaultMobileStandardUA(): String = CHROME_MOBILE_STANDARD_UA

    fun getAcceptLanguage(): String {
        return try {
            val loc = java.util.Locale.getDefault()
            val lang = loc.language ?: "id"
            val country = loc.country?.uppercase() ?: "ID"
            "$lang-$country,$lang;q=0.9,en-US;q=0.8,en;q=0.7"
        } catch (e: Exception) {
            "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
        }
    }

    // === HEADERS UNTUK loadUrl additionalHttpHeaders ===
    // KRITIS: WebView Android OTOMATIS menambahkan header:
    //   X-Requested-With: com.yue.browser
    // di SETIAP request (main frame + sub-resource). Ini adalah PENANDA NYATA
    // bahwa request berasal dari app (bukan browser). Cloudflare dan sistem
    // anti-bot modern MENDETEKSI ini dan memblokir.
    //
    // TRIK: Kita tambahkan "X-Requested-With" dengan nilai null/empty string
    // di additionalHttpHeaders. Pada beberapa kombinasi Chrome WebView,
    // ini menimpa nilai default.
    //
    // Catatan tambahan:
    // - User-Agent kita set JUGA via webView.settings.userAgentString (supaya
    //   berlaku untuk SEMUA request, tidak hanya main frame loadUrl)
    // - Accept-Encoding TIDAK kita set manual — biarkan WebView yang menentukan
    //   (biar support brotli/gzip/deflate dengan benar)
    // - Referer kita set kosong untuk direct navigation (new tab), atau
    //   ke URL sendiri untuk reload (seperti Chrome)
    fun getDefaultHeaders(isDesktop: Boolean = false, dntEnabled: Boolean = false): Map<String, String> {
        val headers = java.util.HashMap<String, String>(9)
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        headers["Accept-Language"] = getAcceptLanguage()
        headers["Upgrade-Insecure-Requests"] = "1"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Site"] = "none"
        headers["Sec-Fetch-User"] = "?1"
        // Trik anti "X-Requested-With: com.yue.browser":
        headers["X-Requested-With"] = ""
        if (dntEnabled) {
            headers["DNT"] = "1"
        }
        return headers
    }

    // Headers untuk reload (Sec-Fetch-Site=same-origin karena dari halaman sendiri)
    fun getReloadHeaders(isDesktop: Boolean = false, dntEnabled: Boolean = false): Map<String, String> {
        val headers = java.util.HashMap<String, String>(9)
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        headers["Accept-Language"] = getAcceptLanguage()
        headers["Cache-Control"] = "max-age=0"
        headers["Upgrade-Insecure-Requests"] = "1"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Site"] = "same-origin"
        headers["Sec-Fetch-User"] = "?1"
        headers["X-Requested-With"] = ""
        if (dntEnabled) {
            headers["DNT"] = "1"
        }
        return headers
    }
}
