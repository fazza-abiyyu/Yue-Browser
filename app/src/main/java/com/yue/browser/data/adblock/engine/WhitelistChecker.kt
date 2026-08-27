package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine primitive: Whitelist checking.
 * If domain is whitelisted, override any previous blocking signals.
 */
class WhitelistChecker {
    private val whitelistHosts = ConcurrentHashMap.newKeySet<String>()

    fun addWhitelistHosts(hosts: Collection<String>) {
        hosts.mapTo(whitelistHosts) { it.lowercase(Locale.US) }
    }

    fun clear() {
        whitelistHosts.clear()
    }

    fun contains(host: String): Boolean {
        val lowercaseHost = host.lowercase(java.util.Locale.US)
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

    fun getWhitelistedHosts(): Set<String> {
        return whitelistHosts.toSet()
    }

    fun detect(context: RequestContext): List<DetectionSignal> {
        val lowercaseHost = context.host.lowercase(Locale.US)

        if (isWhitelisted(lowercaseHost)) {
            return listOf(
                DetectionSignal(
                    type = SignalType.WHITELISTED_DOMAIN,
                    confidence = 0,
                    source = "WhitelistChecker",
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
}