package com.yue.browser.data.adblock

import com.yue.browser.data.adblock.config.DetectionSignal
import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.config.CosmeticRule
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.ScriptletRule

interface RuleRepository {
    /**
     * Match network rules against request context.
     * Returns list of detection signals from matched rules.
     */
    fun matchNetwork(context: RequestContext): List<DetectionSignal>

    /**
     * Match cosmetic rules after page load.
     */
    fun matchCosmetic(
        context: RequestContext,
        documentContext: DocumentContext,
    ): List<CosmeticRule>

    /**
     * Match scriptlet rules after page load.
     */
    fun matchScriptlet(
        context: RequestContext,
        documentContext: DocumentContext,
    ): List<ScriptletRule>

    /**
     * Reload rules from local cache. Called after sync completes.
     */
    suspend fun reload()

    /**
     * Check if repository is ready to process requests.
     */
    fun isReady(): Boolean
}