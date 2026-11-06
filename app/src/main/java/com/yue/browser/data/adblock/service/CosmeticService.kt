package com.yue.browser.data.adblock.service

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.engine.AdRemovalScripts
import com.yue.browser.data.adblock.engine.CosmeticFilterEngine
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service: Aggressive cosmetic filtering with JS-based removal.
 * Uses both CSS hiding AND active DOM element removal for max effectiveness.
 */
class CosmeticService(
    private val cosmeticEngine: CosmeticFilterEngine,
    private val settingsRepo: SettingsRepository? = null,
) {
    private val tag = "CosmeticService"

    /**
     * Build CSS hide rules dari user-picked selectors (persistent via picker).
     * Returns empty string kalau tidak ada user selectors untuk host ini.
     */
    fun buildUserBlockedCss(host: String): String {
        val repo = settingsRepo ?: return ""
        val userSelectors = repo.getBlockedCssSelectors(host)
        if (userSelectors.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("/* User-picked selectors for $host */\n")
        sb.append(userSelectors.joinToString(", ") { it })
        sb.append(" { display: none !important; visibility: hidden !important; opacity: 0 !important; }\n")
        return sb.toString()
    }

    fun getCosmeticCSS(url: String, policy: AdBlockPolicy?): String {
        if (policy == null) return ""
        if (!policy.cosmetic.enabled) return ""
        if (url.isBlank() || url.startsWith("yue://")) return ""

        val uri = try {
            android.net.Uri.parse(url)
        } catch (e: Exception) {
            return ""
        }
        val host = uri.host?.lowercase(java.util.Locale.US) ?: return ""

        if (host.contains("youtube.com") || host.contains("spotify.com") ||
            host.contains("netflix.com") || host.contains("disneyplus.com") ||
            host.contains("primevideo.com") || host.contains("hulu.com") ||
            host.contains("apple.com")
        ) return ""

        val docContext = DocumentContext(url = url, host = host)
        val rules = cosmeticEngine.getCosmeticRules(docContext, policy)
        return cosmeticEngine.generateCss(rules, policy.cosmetic.generic.maxRules)
    }

    fun injectCosmeticFilters(
        context: Context,
        view: WebView?,
        url: String?,
        settings: BrowserSettings?,
        policy: AdBlockPolicy?,
        onBlockCount: ((Int) -> Unit)? = null,
    ) {
        val currentSettings = settings ?: return
        if (!currentSettings.isAdBlockEnabled) return
        val host = try {
            url?.let { android.net.Uri.parse(it).host?.lowercase(java.util.Locale.US) } ?: ""
        } catch (e: Exception) { "" }
        val cleanHost = host.removePrefix("www.").removePrefix("m.")
        if (cleanHost.isNotEmpty() && currentSettings.adblockWhitelistedDomains.any {
            cleanHost == it || cleanHost.endsWith(".$it")
        }) {
            return
        }
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val cosmeticCss = getCosmeticCSS(url ?: "", policy)
            val userCss = buildUserBlockedCss(cleanHost)
            val combinedCss = cosmeticCss + userCss
            val combinedScript = AdRemovalScripts.buildCombinedScript(combinedCss)
            val count = countCssSelectors(combinedCss)

            view?.post {
                try {
                    view.evaluateJavascript(combinedScript) { _ ->
                        onBlockCount?.invoke(count)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error evaluating combined adblock script", e)
                }
            }
        }
    }

    private fun countCssSelectors(css: String): Int {
        return css.count { it == ',' } + 1
    }
}