package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.Action
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import com.yue.browser.data.adblock.config.OriginRelation

/**
 * Engine primitive: Suspicious redirect detection.
 * Detects redirect chains that match policy criteria.
 */
class RedirectDetector {

    fun detect(
        context: RequestContext,
        policy: AdBlockPolicy,
    ): List<DetectionSignal> {
        val redirectPolicy = policy.navigation.redirect
        val signals = mutableListOf<DetectionSignal>()

        // Check redirect chain length
        if (context.redirectChain.size > redirectPolicy.maxChain) {
            signals.add(
                DetectionSignal(
                    type = SignalType.SUSPICIOUS_REDIRECT,
                    confidence = 20,
                    source = "RedirectDetector.chainLength",
                    metadata = mapOf(
                        "chainSize" to context.redirectChain.size.toString(),
                        "maxChain" to redirectPolicy.maxChain.toString()
                    )
                )
            )
        }

        // Check based on origin
        when (context.originRelation) {
            OriginRelation.THIRD_PARTY -> {
                if (redirectPolicy.thirdParty == Action.BLOCK || redirectPolicy.thirdParty == Action.WARN) {
                    val confidence = when (redirectPolicy.thirdParty) {
                        Action.BLOCK -> 50
                        Action.WARN -> 30
                        else -> 20
                    }
                    signals.add(
                        DetectionSignal(
                            type = SignalType.SUSPICIOUS_REDIRECT,
                            confidence = confidence,
                            source = "RedirectDetector.thirdParty",
                            metadata = emptyMap()
                        )
                    )
                }
            }
            OriginRelation.FIRST_PARTY -> {
                // First-party redirect is usually less suspicious
            }
            OriginRelation.UNKNOWN -> {
                // No action unless policy says otherwise
            }
        }

        // Check user gesture requirement
        if (redirectPolicy.userGestureRequired && !context.hasUserGesture) {
            signals.add(
                DetectionSignal(
                    type = SignalType.SUSPICIOUS_REDIRECT,
                    confidence = 20,
                    source = "RedirectDetector.noUserGesture",
                    metadata = emptyMap()
                )
            )
        }

        // OAuth exception
        if (redirectPolicy.allowOAuth && isOAuthRedirect(context.url)) {
            // Remove any existing signals for this
            return signals.filter { it.source != "RedirectDetector.thirdParty" }
        }

        return signals
    }

    private fun isOAuthRedirect(url: String): Boolean {
        return url.contains("oauth") ||
            url.contains("auth") && url.contains("callback") ||
            url.contains("login") && url.contains("redirect")
    }
}