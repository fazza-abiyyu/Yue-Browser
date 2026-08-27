package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine primitive: Anti-adblock detection bypass.
 * Detects anti-adblock scripts and provides signals for bypassing them.
 */
class AntiAdblockBypass {
    private val antiAdblockDomains = ConcurrentHashMap.newKeySet<String>()
    private val antiAdblockPatterns = ConcurrentHashMap.newKeySet<String>()
    private val whitelistedBypassDomains = ConcurrentHashMap.newKeySet<String>()

    fun addAntiAdblockDomain(domain: String) {
        antiAdblockDomains.add(domain.lowercase(Locale.US))
    }

    fun addAntiAdblockDomains(domains: Collection<String>) {
        domains.mapTo(antiAdblockDomains) { it.lowercase(Locale.US) }
    }

    fun addAntiAdblockPattern(pattern: String) {
        antiAdblockPatterns.add(pattern.lowercase(Locale.US))
    }

    fun addAntiAdblockPatterns(patterns: Collection<String>) {
        patterns.mapTo(antiAdblockPatterns) { it.lowercase(Locale.US) }
    }

    fun addWhitelistedBypassDomain(domain: String) {
        whitelistedBypassDomains.add(domain.lowercase(Locale.US))
    }

    fun clear() {
        antiAdblockDomains.clear()
        antiAdblockPatterns.clear()
        whitelistedBypassDomains.clear()
    }

    fun detect(context: RequestContext, policy: AdBlockPolicy): List<DetectionSignal> {
        val lowerHost = context.host.lowercase(Locale.US)
        val lowerUrl = context.url.lowercase(Locale.US)

        if (isWhitelistedForBypass(lowerHost)) {
            return listOf(
                DetectionSignal(
                    type = SignalType.WHITELISTED_DOMAIN,
                    confidence = 0,
                    source = "AntiAdblockBypass.whitelisted",
                    metadata = emptyMap()
                )
            )
        }

        if (isAntiAdblockDomain(lowerHost)) {
            return listOf(
                DetectionSignal(
                    type = SignalType.ANTI_ADBLOCK_DETECTED,
                    confidence = 70,
                    source = "AntiAdblockBypass.domain",
                    metadata = mapOf("host" to context.host)
                )
            )
        }

        for (pattern in antiAdblockPatterns) {
            if (lowerUrl.contains(pattern)) {
                return listOf(
                    DetectionSignal(
                        type = SignalType.ANTI_ADBLOCK_DETECTED,
                        confidence = 50,
                        source = "AntiAdblockBypass.pattern",
                        metadata = mapOf("pattern" to pattern)
                    )
                )
            }
        }

        return emptyList()
    }

    private fun isAntiAdblockDomain(lowercaseHost: String): Boolean {
        if (antiAdblockDomains.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (antiAdblockDomains.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    private fun isWhitelistedForBypass(lowercaseHost: String): Boolean {
        if (whitelistedBypassDomains.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (whitelistedBypassDomains.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    fun size(): Triple<Int, Int, Int> = Triple(
        antiAdblockDomains.size,
        antiAdblockPatterns.size,
        whitelistedBypassDomains.size
    )
}