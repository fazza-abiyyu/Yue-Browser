package com.yue.browser.data.adblock.pipeline

import com.yue.browser.data.adblock.config.Action
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.Decision
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.PopupPolicy
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType
import com.yue.browser.data.adblock.engine.AntiAdblockBypass
import com.yue.browser.data.adblock.engine.HostBlocker
import com.yue.browser.data.adblock.engine.KeywordDetector
import com.yue.browser.data.adblock.engine.PopupDetector
import com.yue.browser.data.adblock.engine.RedirectDetector
import com.yue.browser.data.adblock.engine.WebSocketBlocker
import com.yue.browser.data.adblock.engine.WhitelistChecker

/**
 * Strategy Pipeline: orchestrates engine primitives in priority order.
 *
 * Pipeline determines which primitives to run based on the active policy.
 * Each primitive returns DetectionSignal(s), which are collected and
 * fed to DecisionEngine for final decision.
 */
class StrategyPipeline(
    private val hostBlocker: HostBlocker,
    private val whitelistChecker: WhitelistChecker,
    private val keywordDetector: KeywordDetector,
    private val redirectDetector: RedirectDetector,
    private val popupDetector: PopupDetector,
    private val webSocketBlocker: WebSocketBlocker,
    private val antiAdblockBypass: AntiAdblockBypass,
) {
    private val userWhitelist = mutableSetOf<String>()

    fun setUserWhitelist(domains: Set<String>) {
        userWhitelist.clear()
        userWhitelist.addAll(domains)
    }

    /**
     * Execute the full strategy pipeline for a given request context.
     */
    fun execute(
        context: RequestContext,
        policy: AdBlockPolicy,
    ): Decision {
        val signals = mutableListOf<DetectionSignal>()

        // [priority 100] HostBlocker — check if domain is in blocklist
        signals.addAll(hostBlocker.detect(context, userWhitelist))

        // [priority 95] WhitelistChecker — check if domain is whitelisted
        signals.addAll(whitelistChecker.detect(context))

        // [priority 90] RedirectDetector — check for suspicious redirects
        if (policy.navigation.redirect.thirdParty != Action.ALLOW) {
            signals.addAll(redirectDetector.detect(context, policy))
        }

        // [priority 85] WebSocketBlocker — check for WebSocket trackers
        if (policy.webSocket.blockTracking || policy.webSocket.blockAnalytics) {
            signals.addAll(webSocketBlocker.detect(context, policy))
        }

        // [priority 80] PopupDetector — check for suspicious popups
        if (policy.navigation.popup.policy != PopupPolicy.PERMISSIVE) {
            signals.addAll(popupDetector.detect(context, policy))
        }

        // [priority 75] AntiAdblockBypass — check for anti-adblock scripts
        if (policy.antiAdblock.enabled) {
            signals.addAll(antiAdblockBypass.detect(context, policy))
        }

        // [priority 70] KeywordDetector — check for keyword matches
        signals.addAll(keywordDetector.detect(context, minModerateMatches = 2))

        // [priority 60] DecisionEngine — evaluate all signals
        return DecisionEngine.decide(context, policy, signals)
    }
}