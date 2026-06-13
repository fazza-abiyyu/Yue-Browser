package com.yue.browser.data.engine

object UserAgentManager {
    // === USER-AGENT CHROME MOBILE SANGAT STANDAR ===
    // Chrome Android asli format:
    //   Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/AP1A.240505.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36
    // WebView Android default:
    //   Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/ABC123; wv) AppleWebKit/537.36 ... Chrome/xxx.0.xxxxx.xx Mobile Safari/537.36
    //                                                          ^^
    //                                        "; wv" = PENANDA WEVIEW → DI-BLOCK CLOUDFLARE!
    //
    // KITA HARDCODE UA TANPA "; wv" DENGAN DETAIL DEVICE YANG WAJAR.
    private const val CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/AP1A.240505.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36"
    private const val CHROME_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Safari/537.36"
    private const val FIREFOX_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    private const val EDGE_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"

    fun init(context: android.content.Context) {
        // no-op: kept for backward compatibility
    }

    fun getExpectedUserAgent(currentUrl: String, isDesktopMode: Boolean, settings: com.yue.browser.domain.model.BrowserSettings): String {
        val host = try { android.net.Uri.parse(currentUrl).host ?: "" } catch (e: Exception) { "" }
        val baseDomain = host.removePrefix("m.").removePrefix("www.")
        val isDesktopForDomain = baseDomain.isNotEmpty() && settings.desktopDomains.contains(baseDomain)

        val isMozillaStore = currentUrl.contains("addons.mozilla.org")
        val isEdgeStore = currentUrl.contains("microsoftedge.microsoft.com/addons")

        if (isMozillaStore) return FIREFOX_DESKTOP_UA
        if (isEdgeStore) return EDGE_DESKTOP_UA

        return if (isDesktopMode || isDesktopForDomain) {
            CHROME_DESKTOP_UA
        } else {
            CHROME_MOBILE_UA
        }
    }

    fun getDefaultMobileUA(): String = CHROME_MOBILE_UA
    fun getDefaultDesktopUA(): String = CHROME_DESKTOP_UA

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
    fun getDefaultHeaders(isDesktop: Boolean = false): Map<String, String> {
        val headers = java.util.HashMap<String, String>(8)
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        headers["Accept-Language"] = getAcceptLanguage()
        headers["Upgrade-Insecure-Requests"] = "1"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Site"] = "none"
        headers["Sec-Fetch-User"] = "?1"
        // Trik anti "X-Requested-With: com.yue.browser":
        headers["X-Requested-With"] = ""
        return headers
    }

    // Headers untuk reload (Sec-Fetch-Site=same-origin karena dari halaman sendiri)
    fun getReloadHeaders(isDesktop: Boolean = false): Map<String, String> {
        val headers = java.util.HashMap<String, String>(8)
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        headers["Accept-Language"] = getAcceptLanguage()
        headers["Cache-Control"] = "max-age=0"
        headers["Upgrade-Insecure-Requests"] = "1"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Site"] = "same-origin"
        headers["Sec-Fetch-User"] = "?1"
        headers["X-Requested-With"] = ""
        return headers
    }
}
