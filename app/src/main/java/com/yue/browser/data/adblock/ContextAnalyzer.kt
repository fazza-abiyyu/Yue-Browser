package com.yue.browser.data.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import com.yue.browser.data.adblock.config.NavigationContext
import com.yue.browser.data.adblock.config.OriginRelation
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.ResourceType

object ContextAnalyzer {

    fun analyze(
        request: WebResourceRequest,
        navigation: NavigationContext,
        redirectChain: List<String> = emptyList(),
        hasUserGesture: Boolean = false,
        isPopup: Boolean = false,
        isDownload: Boolean = false,
        openerHost: String? = null,
    ): RequestContext {
        val uri = request.url
        val host = uri.host ?: ""
        val scheme = uri.scheme ?: "http"

        val resourceType = mapResourceType(request)
        val initiator = if (request.isForMainFrame) navigation.currentUrl else null
        val originRelation = detectOriginRelation(uri, navigation.currentHost)

        return RequestContext(
            url = uri.toString(),
            host = host,
            scheme = scheme,
            resourceType = resourceType,
            initiator = initiator,
            originRelation = originRelation,
            navigation = navigation,
            redirectChain = redirectChain,
            hasUserGesture = hasUserGesture,
            isPopup = isPopup,
            isDownload = isDownload,
            openerHost = openerHost,
        )
    }

    fun analyze(
        url: String,
        currentUrl: String?,
        currentHost: String?,
        isMainFrame: Boolean,
        hasUserGesture: Boolean,
        isPopup: Boolean,
        isDownload: Boolean,
        redirectChain: List<String>,
        openerHost: String?,
    ): RequestContext {
        val uri = Uri.parse(url)
        val host = uri.host ?: ""
        val scheme = uri.scheme ?: "http"

        val resourceType = if (isMainFrame) ResourceType.MAIN_FRAME else ResourceType.OTHER
        val initiator = if (isMainFrame && currentUrl != null) currentUrl else null
        val originRelation = detectOriginRelation(uri, currentHost)

        return RequestContext(
            url = url,
            host = host,
            scheme = scheme,
            resourceType = resourceType,
            initiator = initiator,
            originRelation = originRelation,
            navigation = NavigationContext(
                currentUrl = currentUrl,
                currentHost = currentHost,
                isAppNavigation = false,
            ),
            redirectChain = redirectChain,
            hasUserGesture = hasUserGesture,
            isPopup = isPopup,
            isDownload = isDownload,
            openerHost = openerHost,
        )
    }

    private fun mapResourceType(request: WebResourceRequest): ResourceType {
        // WebResourceRequest doesn't expose resource type directly
        // We infer from context and URL
        return when {
            request.isForMainFrame -> ResourceType.MAIN_FRAME
            request.url.toString().contains(".js") -> ResourceType.SCRIPT
            request.url.toString().contains(".css") -> ResourceType.STYLESHEET
            request.url.toString().matches(Regex("\\.(png|jpg|jpeg|gif|webp|svg)$")) -> ResourceType.IMAGE
            request.url.toString().matches(Regex("\\.(mp4|webm|mp3|ogg)$")) -> ResourceType.MEDIA
            else -> ResourceType.OTHER
        }
    }

    private fun detectOriginRelation(uri: Uri, currentHost: String?): OriginRelation {
        if (currentHost == null) return OriginRelation.UNKNOWN
        val requestHost = uri.host ?: return OriginRelation.UNKNOWN

        // Normalize domains for comparison
        val normRequest = normalizeDomain(requestHost)
        val normCurrent = normalizeDomain(currentHost)

        // Check if same eTLD+1
        return if (isSameOrigin(normRequest, normCurrent)) {
            OriginRelation.FIRST_PARTY
        } else {
            OriginRelation.THIRD_PARTY
        }
    }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase().trimEnd('.')
    }

    private fun isSameDomain(request: String, current: String): Boolean {
        // Simple check: request ends with current or vice versa
        // Proper eTLD+1 extraction would need a public suffix list
        // For now, this is good enough as heuristic
        return request.endsWith(current) || current.endsWith(request)
    }

    private fun isSameOrigin(request: String, current: String): Boolean {
        // If either is suffix of the other, consider first-party
        // This allows subdomains to be considered same origin
        return request == current ||
            request.endsWith(".$current") ||
            current.endsWith(".$request")
    }
}