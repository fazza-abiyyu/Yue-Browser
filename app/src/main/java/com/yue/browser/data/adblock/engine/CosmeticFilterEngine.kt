package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.CosmeticRule
import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.config.CssSelectorSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Engine primitive: Cosmetic filtering (CSS hiding).
 * Loads selectors from rules and generates injectable CSS.
 */
class CosmeticFilterEngine {
    private val genericSelectors = ConcurrentHashMap.newKeySet<String>()
    private val domainSelectors = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val wildcardDomainSelectors = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val cookieBannerRemover = CookieBannerRemover()

    fun addGenericSelector(selector: String) {
        if (isDangerousSelector(selector)) return
        genericSelectors.add(selector)
    }

    fun addGenericSelectors(selectors: Collection<String>) {
        selectors.filterNotTo(genericSelectors) { isDangerousSelector(it) }
    }

    fun addDomainSelector(domain: String, selector: String) {
        if (isDangerousSelector(selector)) return
        val cleanDomain = domain.lowercase(Locale.US)
        domainSelectors.getOrPut(cleanDomain) { CopyOnWriteArrayList() }.add(selector)
    }

    fun addWildcardDomainSelector(pattern: String, selector: String) {
        if (isDangerousSelector(selector)) return
        val cleanPattern = pattern.lowercase(Locale.US)
        wildcardDomainSelectors.getOrPut(cleanPattern) { CopyOnWriteArrayList() }.add(selector)
    }

    fun clear() {
        genericSelectors.clear()
        domainSelectors.clear()
        wildcardDomainSelectors.clear()
        cookieBannerRemover.clear()
    }

    fun getCosmeticRules(
        documentContext: DocumentContext,
        policy: AdBlockPolicy,
    ): List<CosmeticRule> {
        if (!policy.cosmetic.enabled) {
            return emptyList()
        }

        val host = documentContext.host.lowercase(Locale.US)
        val rules = mutableListOf<CosmeticRule>()

        // Add generic selectors (limited by policy)
        val maxRules = policy.cosmetic.generic.maxRules
        genericSelectors.take(maxRules).mapTo(rules) {
            CosmeticRule(selector = it, domain = null, isGeneric = true)
        }

        // Add domain-specific selectors
        var tempHost = host
        while (tempHost.isNotEmpty()) {
            domainSelectors[tempHost]?.let { list ->
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

        // Add wildcard matching
        for ((pattern, list) in wildcardDomainSelectors) {
            if (matchWildcardDomain(host, pattern)) {
                list.mapTo(rules) {
                    CosmeticRule(selector = it, domain = pattern, isGeneric = false)
                }
            }
        }

        // Add cookie banner rules if enabled
        if (policy.cookieBanner.enabled) {
            val bannerRules = cookieBannerRemover.getBannerRules(documentContext)
            val limitedBannerRules = bannerRules.take(policy.cookieBanner.maxRules)
            limitedBannerRules.mapTo(rules) {
                it.copy(isCookieBanner = true)
            }
        }

        return rules
    }

    fun generateCss(rules: List<CosmeticRule>, maxChunkSize: Int): String {
        if (rules.isEmpty()) return ""

        val filtered = rules.filter { it.selector.isNotBlank() }

        val regularRules = filtered.filter { !it.isCookieBanner }
        val cookieBannerRules = filtered.filter { it.isCookieBanner }

        val cssBuilder = StringBuilder()

        if (regularRules.isNotEmpty()) {
            val chunked = regularRules.chunked(maxChunkSize)
            for (chunk in chunked) {
                val joined = chunk.joinToString(", ") { it.selector }
                if (joined.isNotBlank()) {
                    cssBuilder.append(joined)
                        .append(" { display: none !important; visibility: hidden !important; opacity: 0 !important; ")
                        .append("max-height: 0 !important; max-width: 0 !important; overflow: hidden !important; }\n")
                }
            }
        }

        if (cookieBannerRules.isNotEmpty()) {
            val chunked = cookieBannerRules.chunked(maxChunkSize)
            for (chunk in chunked) {
                val joined = chunk.joinToString(", ") { it.selector }
                if (joined.isNotBlank()) {
                    cssBuilder.append(joined)
                        .append(" { display: none !important; visibility: hidden !important; opacity: 0 !important; ")
                        .append("max-height: 0 !important; max-width: 0 !important; overflow: hidden !important; z-index: -1 !important; }\n")
                }
            }
        }

        return cssBuilder.toString()
    }

    fun injectCookieBannerRemover(documentContext: DocumentContext, maxRules: Int): String {
        val rules = cookieBannerRemover.getBannerRules(documentContext)
        val limited = rules.take(maxRules)
        if (limited.isEmpty()) return ""

        val cssBuilder = StringBuilder()
        val joined = limited.joinToString(", ") { it.selector }
        if (joined.isNotBlank()) {
            cssBuilder.append(joined)
                .append(" { display: none !important; height: 0 !important; ")
                .append("min-height: 0 !important; visibility: hidden !important; ")
                .append("opacity: 0 !important; z-index: -1 !important; }\n")
        }
        return cssBuilder.toString()
    }

    private fun isDangerousSelector(selector: String): Boolean {
        val s = selector.trim().lowercase(Locale.US)
        if (s.isEmpty()) return true

        // Only reject selectors that SPECIFICALLY target consent/agreement banners
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
            "[id*=\"cookie-banner\"", "[id*=\"gdpr-modal\"", "[id*=\"consent-modal\"",
            "[aria-label*=\"cookie\"", "[aria-label*=\"consent\"",
        )
        for (pattern in consentBannerPatterns) {
            if (s.contains(pattern)) return true
        }

        // Also reject selectors that target agreement/verification modals
        val agreementPatterns = listOf(
            ".agreement-modal", ".agreement-popup",
            ".terms-modal", ".terms-popup",
            ".verification-modal", ".verification-popup",
            ".human-verification", ".bot-check",
            "[class*=\"agreement\"].*[modal]", "[class*=\"terms\"].*[modal]",
        )
        for (pattern in agreementPatterns) {
            if (s.contains(pattern)) return true
        }

        // Dangerous if it's just a top-level tag without any class/id
        if (!s.contains(".") && !s.contains("#") && !s.contains("[") && !s.contains(":")) {
            return true
        }

        // Dangerous if it targets body/html with no qualification
        if (s == "body" || s == "html" ||
            s.startsWith("body ") || s.startsWith("html ") ||
            s.startsWith("body >") || s.startsWith("html >")) {
            if (!s.contains(".") && !s.contains("#") && !s.contains(":") && !s.contains("[")) {
                return true
            }
        }

        return false
    }

    private fun matchWildcardDomain(host: String, pattern: String): Boolean {
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

    fun getCssSelectorSet(): CssSelectorSet {
        return CssSelectorSet(
            generic = genericSelectors.toList(),
            domainSpecific = domainSelectors.mapValues { it.value.toList() },
            wildcard = wildcardDomainSelectors.mapValues { it.value.toList() },
        )
    }

    fun size(): Triple<Int, Int, Int> = Triple(
        genericSelectors.size,
        domainSelectors.size,
        wildcardDomainSelectors.size
    )
}