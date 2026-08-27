package com.yue.browser.data.adblock.config

enum class ResourceType {
    MAIN_FRAME, SUB_FRAME, SCRIPT, IMAGE, STYLESHEET,
    FONT, MEDIA, XHR, FETCH, PING, OTHER
}

enum class OriginRelation { FIRST_PARTY, THIRD_PARTY, UNKNOWN }

data class NavigationContext(
    val currentUrl: String?,
    val currentHost: String?,
    val isAppNavigation: Boolean,
)

data class RequestContext(
    val url: String,
    val host: String,
    val scheme: String,
    val resourceType: ResourceType,
    val initiator: String?,
    val originRelation: OriginRelation,
    val navigation: NavigationContext,
    val redirectChain: List<String>,
    val hasUserGesture: Boolean,
    val isPopup: Boolean,
    val isDownload: Boolean,
    val openerHost: String?,
)