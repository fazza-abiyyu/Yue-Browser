package com.yue.browser.data.engine

import android.content.Context
import com.yue.browser.domain.engine.BrowserEngine
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository

class SystemWebViewEngine(
    private val settingsRepository: SettingsRepository
) : BrowserEngine {
    override fun createSession(
        context: Context,
        id: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)?,
        onNewTabRequested: ((String) -> Unit)?
    ): BrowserSession {
        return SystemWebViewSession(context, id, isPrivate, settingsRepository, onLanguageDetected)
    }
    
    override fun clearCache(context: Context) {
        android.os.Handler(context.mainLooper).post {
            try {
                android.webkit.WebView(context).clearCache(true)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    
    override fun clearCookies(context: Context) {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }
}
