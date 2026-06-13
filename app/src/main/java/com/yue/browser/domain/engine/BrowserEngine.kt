package com.yue.browser.domain.engine

import android.content.Context

interface BrowserEngine {
    fun createSession(
        context: Context,
        id: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)? = null,
        onNewTabRequested: ((String) -> Unit)? = null,
        preExistingWebView: android.webkit.WebView? = null
    ): BrowserSession
    fun clearCache(context: Context)
    fun clearCookies(context: Context)
}
