package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.config.CosmeticRule
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Engine primitive: Aggressive ad/banner removal.
 * Handles cookie banners, popups, floating ads, interstitials, and all ad overlays.
 */
class CookieBannerRemover {
    private val bannerSelectors = ConcurrentHashMap.newKeySet<String>()
    private val generalAdSelectors = ConcurrentHashMap.newKeySet<String>()
    private val providerDomains = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    fun addBannerSelector(selector: String) {
        if (isSafeSelector(selector)) {
            bannerSelectors.add(selector)
        }
    }

    fun addBannerSelectors(selectors: Collection<String>) {
        selectors.filter { isSafeSelector(it) }.mapTo(bannerSelectors) { it }
    }

    fun addGeneralAdSelector(selector: String) {
        if (isSafeSelector(selector)) {
            generalAdSelectors.add(selector)
        }
    }

    fun addGeneralAdSelectors(selectors: Collection<String>) {
        selectors.filter { isSafeSelector(it) }.mapTo(generalAdSelectors) { it }
    }

    fun addProviderDomain(domain: String, selector: String) {
        if (isSafeSelector(selector)) {
            val cleanDomain = domain.lowercase(Locale.US)
            providerDomains.getOrPut(cleanDomain) { CopyOnWriteArrayList() }.add(selector)
        }
    }

    fun clear() {
        bannerSelectors.clear()
        generalAdSelectors.clear()
        providerDomains.clear()
    }

    fun getBannerRules(documentContext: DocumentContext): List<CosmeticRule> {
        val host = documentContext.host.lowercase(Locale.US)
        val rules = mutableListOf<CosmeticRule>()

        bannerSelectors.mapTo(rules) {
            CosmeticRule(selector = it, domain = null, isGeneric = true)
        }

        generalAdSelectors.mapTo(rules) {
            CosmeticRule(selector = it, domain = null, isGeneric = true)
        }

        var tempHost = host
        while (tempHost.isNotEmpty()) {
            providerDomains[tempHost]?.let { list ->
                list.mapTo(rules) {
                    CosmeticRule(selector = it, domain = tempHost, isGeneric = false)
                }
            }
            if (tempHost.contains(".")) {
                tempHost = tempHost.substringAfter(".")
            } else {
                break
            }
        }

        return rules
    }

    fun generateBannerCss(documentContext: DocumentContext, maxRules: Int): String {
        val rules = getBannerRules(documentContext)
        val limited = rules.take(maxRules)
        if (limited.isEmpty()) return ""

        val cssBuilder = StringBuilder()
        val joined = limited.joinToString(", ") { it.selector }
        if (joined.isNotBlank()) {
            cssBuilder.append(joined)
                // SAFETY: Minimal hiding props only.
                // No height/min-height/overflow — these cause permanent layout corruption.
                .append(" { display: none !important; visibility: hidden !important; opacity: 0 !important; }\n")
        }
        return cssBuilder.toString()
    }

    private fun isSafeSelector(selector: String): Boolean {
        val s = selector.trim().lowercase(Locale.US)
        if (s.isEmpty()) return false
        if (s.length > 500) return false

        // Reject selectors that target consent/agreement/cookie/preference/legal elements
        // Expanded list — these are NEVER ads; false positives here break site UX catastrophically
        val consentBannerPatterns = listOf(
            ".cookie-banner", ".cookiebanner", ".cookie-banner-container",
            ".gdpr-modal", ".gdpr-banner", ".gdpr-consent",
            ".consent-banner", ".consent-modal", ".consent-container",
            ".onetrust-banner", ".onetrust-modal",
            ".cookiebot-banner", ".cookiebot-modal",
            ".cookie-notice", ".cookienotice", ".cookie-notice-container",
            "#cookie-banner", "#gdpr-modal", "#consent-modal",
            "[data-testid=\"cookie-banner\"", "[data-testid=\"gdpr-banner\"",
            "[class*=\"cookie-banner\"", "[class*=\"gdpr-banner\"", "[class*=\"consent-banner\"",
            // Generic attribute selectors targeting consent words = massive false-positive risk
            "[class*=\"cookie\"", "[class*=\"cookies\"",
            "[class*=\"consent\"", "[class*=\"gdpr\"",
            "[class*=\"preference\"", "[class*=\"privacy\"", "[class*=\"policy\"",
            "[class*=\"preferensi\"", "[class*=\"kebijakan\"", "[class*=\"persyaratan\"",
            "[class*=\"verifikasi\"",
            "[id*=\"cookie\"", "[id*=\"consent\"", "[id*=\"gdpr\"",
            "[id*=\"preference\"", "[id*=\"privacy\"",
            "[id*=\"preferensi\"",
            // Funding Choices / Google Consent
            "[class*=\"fundingchoices\"", "[id*=\"fundingchoices\"", "[class*=\"funding-choices\"",
        )
        for (pattern in consentBannerPatterns) {
            if (s.contains(pattern)) return false
        }

        if (!s.contains(".") && !s.contains("#") && !s.contains("[") && !s.contains(":") && !s.contains("*")) {
            return false
        }
        if (s.startsWith("body") || s.startsWith("html")) {
            if (!s.contains(".") && !s.contains("#") && !s.contains(":") && !s.contains("[")) {
                return false
            }
        }
        if (s.contains("{") || s.contains("}")) return false
        return true
    }

    fun size(): Triple<Int, Int, Int> = Triple(bannerSelectors.size, generalAdSelectors.size, providerDomains.size)
}
