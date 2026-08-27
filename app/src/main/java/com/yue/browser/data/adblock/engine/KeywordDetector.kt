package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine primitive: Keyword-based detection for URL/host.
 * Used for fallback detection when domain isn't in blocklist.
 */
class KeywordDetector {
    private val strictKeywords = ConcurrentHashMap.newKeySet<String>()
    private val moderateKeywords = ConcurrentHashMap.newKeySet<String>()

    fun addStrictKeywords(keywords: Collection<String>) {
        keywords.mapTo(strictKeywords) { it.lowercase(Locale.US) }
    }

    fun addModerateKeywords(keywords: Collection<String>) {
        moderateKeywords.mapTo(moderateKeywords) { it.lowercase(Locale.US) }
    }

    fun clear() {
        strictKeywords.clear()
        moderateKeywords.clear()
    }

    fun detect(context: RequestContext, minModerateMatches: Int): List<DetectionSignal> {
        val lowerHost = context.host.lowercase(Locale.US)
        val lowerUrl = context.url.lowercase(Locale.US)

        // Strict keywords — any match = signal
        val strictMatches = strictKeywords.count {
            lowerHost.contains(it) || lowerUrl.contains(it)
        }
        if (strictMatches > 0) {
            return listOf(
                DetectionSignal(
                    type = SignalType.JUDOL_KEYWORD,
                    confidence = 60,
                    source = "KeywordDetector.strict",
                    metadata = mapOf("matches" to strictMatches.toString())
                )
            )
        }

        // Moderate keywords — need min matches
        val moderateMatches = moderateKeywords.count {
            lowerHost.contains(it) || lowerUrl.contains(it)
        }
        if (moderateMatches >= minModerateMatches) {
            return listOf(
                DetectionSignal(
                    type = SignalType.JUDOL_KEYWORD,
                    confidence = 30,
                    source = "KeywordDetector.moderate",
                    metadata = mapOf("matches" to moderateMatches.toString())
                )
            )
        }

        return emptyList()
    }

    fun size(): Pair<Int, Int> = Pair(strictKeywords.size, moderateKeywords.size)
}