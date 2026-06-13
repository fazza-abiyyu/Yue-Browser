package com.yue.browser.data.engine

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.View
import android.graphics.Bitmap
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest

/**
 * ### 1. Tab-based OAuth Popups (Integrated Theme)
 * - **Problem**: When OAuth popups (Google, Discord, etc.) were loaded inside a custom fullscreen Dialog container, it had hardcoded colors and a light grey header that clashed with the dark mode of the website and the browser's theme. Opening it in a standard separate tab historically broke the JS `window.opener` connection.
 * - **Solution**:
 *   - Overhauled popup handling to open popup `WebView` instances directly as **normal tabs** in Yue Browser's main tab layout.
 *   - Implemented `createNewTabWithWebView` in `TabRepositoryImpl` which creates a `SystemWebViewSession` that wraps the pre-existing `WebView` generated in `onCreateWindow`. This preserves the window hierarchy, JS `window.opener` references, and session state.
 *   - Overrode `onCloseWindow` to trigger `session.requestCloseCallback?.invoke()`, which automatically closes the tab when the website performs authentication completion and calls `window.close()`.
 *   - This guarantees that popups match Yue Browser's user interface, support dark mode simulation, and are fully integrated into tab management without any theme mismatch.
 */
class SystemWebChromeClient(
    private val context: android.content.Context,
    private val session: SystemWebViewSession,
    private val settingsRepository: com.yue.browser.domain.repository.SettingsRepository,
    private val isPrivate: Boolean
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        android.util.Log.d("YueConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (at ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
        return super.onConsoleMessage(consoleMessage)
    }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val newUrl = view?.url ?: ""
                if (newUrl == "about:blank" && !session.isDeliberateNewTab) {
                    return
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.progress = newProgress
                session.canGoBack = view?.canGoBack() ?: false
                session.canGoForward = view?.canGoForward() ?: false
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.canGoBack,
                    session.canGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                if (newProgress > 40 && isAdBlockActive) {
                    AdBlockManager.injectCosmeticFilters(context, view, view?.url, currentSettings)
                }
                if (newProgress > 60) {
                    view?.evaluateJavascript(WebViewScriptsVideo.doubleTapScript, null)
                }
            }

            override fun onReceivedTitle(view: WebView?, t: String?) {
                val newUrl = view?.url ?: ""
                if (newUrl == "about:blank" && !session.isDeliberateNewTab) {
                    return
                }
                session.title = if (newUrl == "yue://newtab" || newUrl.isBlank() || newUrl == "about:blank") {
                    "New Tab"
                } else {
                    t ?: "Yue Browser"
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.canGoBack,
                    session.canGoForward
                )
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (icon != null) {
                    session.faviconCallback?.invoke(icon)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                val activity = context as? android.app.Activity ?: return
                
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                customView = view
                customViewCallback = callback
                
                session.view.visibility = View.GONE
                
                val decorView = activity.window.decorView as? android.view.ViewGroup
                decorView?.addView(customView, android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ))
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                val activity = context as? android.app.Activity ?: return
                val decorView = activity.window.decorView as? android.view.ViewGroup
                
                if (customView != null) {
                    decorView?.removeView(customView)
                    customView = null
                }
                
                session.view.visibility = View.VISIBLE
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                // Lock to portrait when exiting fullscreen (no auto-rotate)
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                val activity = context as? android.app.Activity
                activity?.runOnUiThread {
                    session.requestCloseCallback?.invoke()
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                val currentHost = try {
                    android.net.Uri.parse(view?.url).host ?: ""
                } catch (e: Exception) { "" }
                
                val hitTestResult = view?.hitTestResult

                // Only block non-user-gesture popups.
                if (!isUserGesture) {
                    val allowedPopupDomains = hashSetOf(
                        "google.com", "google.co.id", "gstatic.com", "facebook.com", "twitter.com", "x.com",
                        "instagram.com", "github.com", "apple.com", "microsoft.com", "live.com", "disqus.com", 
                        "disquscdn.com", "line.me", "yahoo.com", "discord.com", "whatsapp.com",
                        "cloudflare.com", "cloudflareinsights.com"
                    )
                    val isWhitelisted = allowedPopupDomains.any { currentHost == it || currentHost.endsWith(".$it") }
                    if (!isWhitelisted) {
                        return false
                    }
                }

                // Always block redirect to gambling/judol sites
                val destinationUrl = hitTestResult?.extra
                if (!destinationUrl.isNullOrBlank()) {
                    val destHost = android.net.Uri.parse(destinationUrl).host ?: ""
                    if (AdBlockManager.isJudolHost(context, destHost) || (isAdBlockActive && AdBlockManager.isHostBlocked(context, destHost, settings))) {
                        return false
                    }
                }

                val tempWebView = WebView(context)
                tempWebView.settings.javaScriptEnabled = true
                tempWebView.settings.domStorageEnabled = true
                tempWebView.settings.databaseEnabled = true
                tempWebView.settings.useWideViewPort = true
                tempWebView.settings.loadWithOverviewMode = true
                tempWebView.settings.javaScriptCanOpenWindowsAutomatically = true
                tempWebView.settings.userAgentString = view?.settings?.userAgentString ?: UserAgentManager.getExpectedUserAgent("", false, settingsRepository.settingsFlow.value)
                
                session.newTabWithWebViewCallback?.invoke(tempWebView, isPrivate, currentHost)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
