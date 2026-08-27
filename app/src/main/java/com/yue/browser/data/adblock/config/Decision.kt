package com.yue.browser.data.adblock.config

import com.yue.browser.data.adblock.config.Action

enum class DecisionAction { ALLOW, BLOCK, WARN }

data class DecisionReason(
    val signalType: SignalType,
    val confidence: Int,
    val source: String?,
    val metadata: Map<String, String>,
)

data class Decision(
    val action: DecisionAction,
    val totalScore: Int,
    val reasons: List<DecisionReason>,
)