package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.ResourceType
import com.yue.browser.data.adblock.config.SignalType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine primitive: WebSocket connection blocking.
 * Detects WebSocket connections to known tracking/analytics domains.
 */
class WebSocketBlocker {
    private val blockedWsHosts = ConcurrentHashMap.newKeySet<String>()
    private val wsPatterns = ConcurrentHashMap.newKeySet<String>()

    fun addBlockedWsHost(host: String) {
        blockedWsHosts.add(host.lowercase(Locale.US))
    }

    fun addBlockedWsHosts(hosts: Collection<String>) {
        hosts.mapTo(blockedWsHosts) { it.lowercase(Locale.US) }
    }

    fun addWsPattern(pattern: String) {
        wsPatterns.add(pattern.lowercase(Locale.US))
    }

    fun addWsPatterns(patterns: Collection<String>) {
        patterns.mapTo(wsPatterns) { it.lowercase(Locale.US) }
    }

    fun clear() {
        blockedWsHosts.clear()
        wsPatterns.clear()
    }

    fun detect(context: RequestContext, policy: AdBlockPolicy): List<DetectionSignal> {
        if (context.resourceType != ResourceType.FETCH &&
            context.resourceType != ResourceType.XHR) {
            return emptyList()
        }

        val lowerHost = context.host.lowercase(Locale.US)
        val lowerUrl = context.url.lowercase(Locale.US)

        if (isBlockedHost(lowerHost)) {
            return listOf(
                DetectionSignal(
                    type = SignalType.WEBSOCKET_TRACKER,
                    confidence = 100,
                    source = "WebSocketBlocker.host",
                    metadata = mapOf("host" to context.host)
                )
            )
        }

        for (pattern in wsPatterns) {
            if (lowerUrl.contains(pattern)) {
                return listOf(
                    DetectionSignal(
                        type = SignalType.WEBSOCKET_TRACKER,
                        confidence = 80,
                        source = "WebSocketBlocker.pattern",
                        metadata = mapOf("pattern" to pattern)
                    )
                )
            }
        }

        return emptyList()
    }

    private fun isBlockedHost(lowercaseHost: String): Boolean {
        if (blockedWsHosts.contains(lowercaseHost)) return true
        var tempHost = lowercaseHost
        while (tempHost.contains(".")) {
            tempHost = tempHost.substringAfter(".")
            if (blockedWsHosts.contains(tempHost)) {
                return true
            }
        }
        return false
    }

    fun size(): Int = blockedWsHosts.size
}