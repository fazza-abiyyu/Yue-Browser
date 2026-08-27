package com.yue.browser.data.adblock.config

enum class BlockMode { OFF, BASIC, BALANCED, AGGRESSIVE }
enum class Action { ALLOW, BLOCK, WARN }
enum class PopupPolicy { PERMISSIVE, SMART, STRICT }

data class ThirdPartyPolicy(
    val blockTrackers: Boolean,
    val blockAds: Boolean,
    val blockMalvertising: Boolean,
)

data class FirstPartyPolicy(val enabled: Boolean)

data class UnknownPolicy(val action: Action)

data class NetworkPolicy(
    val mode: BlockMode,
    val thirdParty: ThirdPartyPolicy,
    val firstParty: FirstPartyPolicy,
    val unknown: UnknownPolicy,
)

data class RedirectPolicy(
    val maxChain: Int,
    val thirdParty: Action,
    val sameSite: Action,
    val userGestureRequired: Boolean,
    val allowOAuth: Boolean,
)

data class PopupNavigationPolicy(val policy: PopupPolicy)

data class NavigationPolicy(
    val popup: PopupNavigationPolicy,
    val redirect: RedirectPolicy,
)

data class CosmeticPolicy(
    val enabled: Boolean,
    val strategy: String,
    val generic: GenericCosmeticConfig,
    val iframe: IframeConfig,
)

data class GenericCosmeticConfig(val maxRules: Int)

data class IframeConfig(val enabled: Boolean)

data class CookieBannerPolicy(
    val enabled: Boolean,
    val maxRules: Int,
)

data class WebSocketPolicy(
    val blockTracking: Boolean,
    val blockAnalytics: Boolean,
)

data class ScriptletPolicy(val enabled: Boolean)

data class YoutubePolicy(
    val enabled: Boolean,
    val strategy: String,
    val skipHosts: List<String>,
)

data class AntiAdblockPolicy(val enabled: Boolean)

data class RiskScoringPolicy(
    val blockThreshold: Int,
    val warnThreshold: Int,
    val signalScores: Map<String, Int>,
)

data class AdBlockPolicy(
    val policyVersion: Int,
    val engineVersion: Int,
    val profile: String,
    val network: NetworkPolicy,
    val navigation: NavigationPolicy,
    val cosmetic: CosmeticPolicy,
    val cookieBanner: CookieBannerPolicy,
    val webSocket: WebSocketPolicy,
    val scriptlet: ScriptletPolicy,
    val youtube: YoutubePolicy,
    val antiAdblock: AntiAdblockPolicy,
    val riskScoring: RiskScoringPolicy,
)