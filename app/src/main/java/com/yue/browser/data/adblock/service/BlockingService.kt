package com.yue.browser.data.adblock.service

import android.content.Context
import com.yue.browser.data.adblock.engine.HostBlocker
import com.yue.browser.data.adblock.engine.WhitelistChecker
import com.yue.browser.data.adblock.engine.KeywordDetector
import com.yue.browser.domain.model.BrowserSettings
import java.util.Locale

/**
 * Service: Core blocking checks — host, judol, custom filters.
 * Extracted from AdBlockEngine for separation of concerns.
 */
class BlockingService(
    private val hostBlocker: HostBlocker,
    private val whitelistChecker: WhitelistChecker,
    private val keywordDetector: KeywordDetector,
) {
    private val adKeywords = hashSetOf(
        "adsystem", "popads", "popcash", "clickase", "onclickads",
        "exoclick", "adsterra", "propellerads", "mgid", "adtrue",
        "juicyads", "masonerthor", "ibo88"
    )

    private val judolBrandKeywords = hashSetOf(
        "ibo88", "dewaslot", "rajaslot", "judol88", "bosslot", "bossjudi",
        "gacortoto", "slotonline888", "agenjudi88", "bandar88", "mpo88",
        "togel123", "togel4d", "joker123", "joker388", "spadegaming"
    )

    fun isHostBlocked(context: Context, host: String, settings: BrowserSettings?): Boolean {
        val lowercaseHost = host.lowercase(Locale.US)
        
        // User whitelist check
        if (settings != null) {
            val cleanHost = lowercaseHost.removePrefix("www.").removePrefix("m.")
            val isWhitelisted = settings.adblockWhitelistedDomains.any {
                cleanHost == it || cleanHost.endsWith(".$it")
            }
            if (isWhitelisted) return false
        }

        // Whitelist check
        if (isWhitelistedHost(lowercaseHost)) return false

        // Blocked host check
        if (isBlockedHost(lowercaseHost)) return true

        // Custom user filters
        if (settings != null && settings.isAdBlockEnabled) {
            if (settings.customAdBlockFilters.isNotEmpty()) {
                val isCustomAd = settings.customAdBlockFilters.any {
                    lowercaseHost == it || lowercaseHost.endsWith(".$it") || lowercaseHost.contains(it)
                }
                if (isCustomAd) return true
            }
        }

        // Keyword fallback
        if (adKeywords.any { lowercaseHost.contains(it) }) {
            return true
        }

        return false
    }

    fun isJudolHost(context: Context, host: String): Boolean {
        val lowerHost = host.lowercase(Locale.US)

        // Check against blocked hosts
        if (isBlockedHost(lowerHost)) return true

        // Brand-name keyword fallback
        if (judolBrandKeywords.any { lowerHost.contains(it) }) return true

        // Keyword-based detection from assets
        val strictKeywords = readAssetLines(context, "filters/gambling_strict.txt")
        for (keyword in strictKeywords) {
            if (lowerHost.contains(keyword)) {
                return true
            }
        }

        // Moderate keywords — require at least 2 matches
        val moderateKeywords = readAssetLines(context, "filters/gambling_moderate.txt")
        var moderateMatches = 0
        for (keyword in moderateKeywords) {
            if (lowerHost.contains(keyword)) {
                moderateMatches++
            }
        }

        return moderateMatches >= 2
    }

    fun isCustomFilterBlocked(host: String, settings: BrowserSettings?): Boolean {
        if (settings == null || !settings.isAdBlockEnabled) return false
        val lowercaseHost = host.lowercase(Locale.US)
        return settings.customAdBlockFilters.any {
            lowercaseHost == it || lowercaseHost.endsWith(".$it")
        }
    }

    fun isAdblockActive(settings: BrowserSettings?): Boolean {
        return settings != null && (settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock"))
    }

    private fun isWhitelistedHost(lowercaseHost: String): Boolean {
        if (whitelistChecker.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (whitelistChecker.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    private fun isBlockedHost(lowercaseHost: String): Boolean {
        if (hostBlocker.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (hostBlocker.contains(tempHost)) {
                return true
            }
        }
        return false
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