package com.yue.browser.data.engine

object UserAgentManager {
    fun getExpectedUserAgent(currentUrl: String, isDesktopMode: Boolean, settings: com.yue.browser.domain.model.BrowserSettings): String {
        val host = try { android.net.Uri.parse(currentUrl).host ?: "" } catch (e: Exception) { "" }
        val baseDomain = host.removePrefix("m.").removePrefix("www.")
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
}
