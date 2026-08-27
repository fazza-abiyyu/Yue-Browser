package com.yue.browser.data.adblock.pipeline

import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.Decision
import com.yue.browser.data.adblock.config.DecisionAction
import com.yue.browser.data.adblock.config.DecisionReason
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.SignalType
import com.yue.browser.data.adblock.config.RequestContext

/**
 * Decision Engine: takes context + policy + signals → Decision.
 *
 * Strategy decides, rules describe, context explains, engine executes.
 */
object DecisionEngine {

    fun decide(
        context: RequestContext,
        policy: AdBlockPolicy,
        signals: List<DetectionSignal>,
    ): Decision {
        if (signals.isEmpty()) {
            return Decision(
                action = DecisionAction.ALLOW,
                totalScore = 0,
                reasons = emptyList(),
            )
        }

        // Check for whitelist override — if any signal is WHITELISTED, always allow
        val whitelistSignals = signals.filter { it.type == SignalType.WHITELISTED_DOMAIN }
        if (whitelistSignals.isNotEmpty()) {
            return Decision(
                action = DecisionAction.ALLOW,
                totalScore = 0,
                reasons = whitelistSignals.map { signalToReason(it) },
            )
        }

        // Calculate total score
        var totalScore = 0
        val reasonList = mutableListOf<DecisionReason>()
        val signalScores = policy.riskScoring.signalScores

        for (signal in signals) {
            val baseScore = signalScores[signal.type.name] ?: 0
            // Adjust confidence: if signal has confidence < 100, scale the score
            val adjustedScore = baseScore * signal.confidence / 100
            totalScore += adjustedScore

            reasonList.add(
                DecisionReason(
                    signalType = signal.type,
                    confidence = signal.confidence,
                    source = signal.source,
                    metadata = signal.metadata,
                )
            )
        }

        // Determine action based on threshold
        val action = when {
            totalScore >= policy.riskScoring.blockThreshold -> DecisionAction.BLOCK
            totalScore >= policy.riskScoring.warnThreshold -> DecisionAction.WARN
            else -> DecisionAction.ALLOW
        }

        return Decision(
            action = action,
            totalScore = totalScore,
            reasons = reasonList,
        )
    }

    private fun signalToReason(signal: DetectionSignal): DecisionReason {
        return DecisionReason(
            signalType = signal.type,
            confidence = signal.confidence,
            source = signal.source,
            metadata = signal.metadata,
        )
    }
}