package com.yue.browser.data.adblock.config

enum class SignalType {
    BLOCKED_AD_DOMAIN,
    WHITELISTED_DOMAIN,
    SUSPICIOUS_REDIRECT,
    JUDOL_KEYWORD,
    SUSPICIOUS_POPUP,
    TRACKER_FOUND,
    MALVERTISING_DOMAIN,
    WEBSOCKET_TRACKER,
    ANTI_ADBLOCK_DETECTED,
    COOKIE_BANNER,
    PERFORMANCE_ANOMALY,
}

data class DetectionSignal(
    val type: SignalType,
    val confidence: Int,
    val source: String?,
    val metadata: Map<String, String> = emptyMap(),
)