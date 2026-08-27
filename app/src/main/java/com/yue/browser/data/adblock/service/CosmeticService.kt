package com.yue.browser.data.adblock.service

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.engine.AdRemovalScripts
import com.yue.browser.data.adblock.engine.CosmeticFilterEngine
import com.yue.browser.data.engine.WebViewScripts
import com.yue.browser.domain.model.BrowserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service: Aggressive cosmetic filtering with JS-based removal.
 * Uses both CSS hiding AND active DOM element removal for max effectiveness.
 */
class CosmeticService(
    private val cosmeticEngine: CosmeticFilterEngine,
) {
    private val tag = "CosmeticService"

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
        if (url != null) {
            val host = try {
                android.net.Uri.parse(url).host?.lowercase(java.util.Locale.US) ?: ""
            } catch (e: Exception) { "" }
            val cleanHost = host.removePrefix("www.").removePrefix("m.")
            if (cleanHost.isNotEmpty() && currentSettings.adblockWhitelistedDomains.any {
                cleanHost == it || cleanHost.endsWith(".$it")
            }) {
                return
            }
        }
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val css = getCosmeticCSS(url ?: "", policy)
            val cssScript = if (css.isNotBlank()) {
                AdRemovalScripts.buildCssInjectionScript(css)
            } else ""

            val aggressiveScript = AdRemovalScripts.buildAggressiveAdRemovalScript()

            view?.post {
                try {
                    if (cssScript.isNotBlank()) {
                        view.evaluateJavascript(cssScript) { result ->
                            val count = countCssSelectors(css)
                            onBlockCount?.invoke(count)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error evaluating CSS injection", e)
                }
                try {
                    view.evaluateJavascript(aggressiveScript) { result ->
                        Log.d(tag, "Aggressive remover result: $result")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error evaluating aggressive ad remover", e)
                }
                try {
                    view.evaluateJavascript(WebViewScripts.overlayAdRemoverScript, null)
                } catch (e: Exception) {
                    Log.e(tag, "Error evaluating overlay ad remover", e)
                }
            }
        }
    }

    private fun countCssSelectors(css: String): Int {
        return css.count { it == ',' } + 1
    }
}