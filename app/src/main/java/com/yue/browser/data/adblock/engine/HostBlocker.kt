package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine primitive: Host-based ad blocking.
 * Detects blocked ad domains from rule set and returns detection signal.
 */
class HostBlocker {
    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()
    private val whitelistHosts = ConcurrentHashMap.newKeySet<String>()

    fun addBlockedHost(host: String) {
        blockedHosts.add(host.lowercase(Locale.US))
    }

    fun addBlockedHosts(hosts: Collection<String>) {
        hosts.mapTo(blockedHosts) { it.lowercase(Locale.US) }
    }

    fun addWhitelistHost(host: String) {
        whitelistHosts.add(host.lowercase(Locale.US))
    }

    fun addWhitelistHosts(hosts: Collection<String>) {
        hosts.mapTo(whitelistHosts) { it.lowercase(Locale.US) }
    }

    fun clear() {
        blockedHosts.clear()
        whitelistHosts.clear()
    }

    fun clearHosts() {
        blockedHosts.clear()
    }

    fun clearWhitelist() {
        whitelistHosts.clear()
    }

    fun detect(context: RequestContext, userWhitelist: Set<String> = emptySet()): List<DetectionSignal> {
        val lowercaseHost = context.host.lowercase(Locale.US)

        // Check user whitelist first
        val cleanHost = lowercaseHost.removePrefix("www.").removePrefix("m.")
        if (userWhitelist.any { cleanHost == it || cleanHost.endsWith(".$it") }) {
            return listOf()
        }

        // Check policy whitelist
        if (isWhitelisted(lowercaseHost)) {
            return listOf()
        }

        // Check blocked
        if (isBlocked(lowercaseHost)) {
            return listOf(
                DetectionSignal(
                    type = SignalType.BLOCKED_AD_DOMAIN,
                    confidence = 100,
                    source = "HostBlocker",
                    metadata = mapOf("host" to context.host)
                )
            )
        }

        return emptyList()
    }

    private fun isWhitelisted(lowercaseHost: String): Boolean {
        if (whitelistHosts.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (whitelistHosts.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    private fun isBlocked(lowercaseHost: String): Boolean {
        if (blockedHosts.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (blockedHosts.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    fun size(): Int = blockedHosts.size

    fun sizeWithWhitelist(): Pair<Int, Int> = Pair(blockedHosts.size, whitelistHosts.size)

    fun contains(host: String): Boolean {
        val lowercaseHost = host.lowercase(java.util.Locale.US)
        if (blockedHosts.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (blockedHosts.contains(tempHost)) {
                return true
            }
        }
        return false
    }
}