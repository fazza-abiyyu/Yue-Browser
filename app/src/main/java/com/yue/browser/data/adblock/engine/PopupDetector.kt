package com.yue.browser.data.adblock.engine

import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.PopupPolicy
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.SignalType

/**
 * Engine primitive: Suspicious popup detection.
 */
class PopupDetector {

    fun detect(
        context: RequestContext,
        policy: AdBlockPolicy,
    ): List<DetectionSignal> {
        if (!context.isPopup) {
            return emptyList()
        }

        return when (policy.navigation.popup.policy) {
            PopupPolicy.STRICT -> {
                // Almost all popups without user gesture are suspicious
                if (!context.hasUserGesture) {
                    listOf(
                        DetectionSignal(
                            type = SignalType.SUSPICIOUS_POPUP,
                            confidence = 40,
                            source = "PopupDetector.strictNoGesture",
                            metadata = emptyMap()
                        )
                    )
                } else {
                    listOf(
                        DetectionSignal(
                            type = SignalType.SUSPICIOUS_POPUP,
                            confidence = 20,
                            source = "PopupDetector.strict",
                            metadata = emptyMap()
                        )
                    )
                }
            }
            PopupPolicy.SMART -> {
                if (!context.hasUserGesture) {
                    listOf(
                        DetectionSignal(
                            type = SignalType.SUSPICIOUS_POPUP,
                            confidence = 30,
                            source = "PopupDetector.smartNoGesture",
                            metadata = emptyMap()
                        )
                    )
                } else {
                    emptyList()
                }
            }
            PopupPolicy.PERMISSIVE -> {
                emptyList()
            }
        }
    }
}