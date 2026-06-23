package com.yue.browser.data.engine

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.View
import android.graphics.Bitmap
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.JsPromptResult

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

    private var fullscreenContainer: FullscreenContainer? = null
    private var lockButton: android.view.View? = null
    private var speedupBadge: android.widget.TextView? = null
    private var isOrientationLocked = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    private fun showLockButton(button: android.view.View) {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        button.animate().cancel()
        if (button.visibility != android.view.View.VISIBLE) {
            button.alpha = 0f
            button.visibility = android.view.View.VISIBLE
        }
        button.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        val runnable = Runnable {
            hideLockButton(button)
        }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, 2000)
    }

    private fun hideLockButton(button: android.view.View) {
        button.animate().cancel()
        button.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { button.visibility = android.view.View.GONE }
            .start()
    }

    private fun showSpeedupBadge(rate: Float) {
        val badge = speedupBadge ?: return
        val formattedRate = String.format(java.util.Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')
        val template = context.getString(com.yue.browser.R.string.video_speedup_indicator)
        val displayText = template.replace("%1\$s", formattedRate)
        val htmlText = "$displayText <font color='#EC4899'>&raquo;</font>"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            badge.text = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            badge.text = android.text.Html.fromHtml(htmlText)
        }

        badge.animate().cancel()
        if (badge.visibility != android.view.View.VISIBLE) {
            badge.alpha = 0f
            badge.visibility = android.view.View.VISIBLE
        }
        badge.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideSpeedupBadge() {
        val badge = speedupBadge ?: return
        badge.animate().cancel()
        badge.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { badge.visibility = android.view.View.GONE }
            .start()
    }

    private fun findActivity(ctx: android.content.Context): android.app.Activity? {
        var current = ctx
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) {
                return current
            }
            current = current.baseContext
        }
        return current as? android.app.Activity
    }

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        android.util.Log.d("YueConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (at ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
        return super.onConsoleMessage(consoleMessage)
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        when (message) {
            "__YuePicker__" -> {
                session.handleElementPickerSubmit(defaultValue ?: "")
                result?.confirm("")
                return true
            }
            "__YuePickerCancel__" -> {
                session.handleElementPickerCancel()
                result?.confirm("")
                return true
            }
        }
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val newUrl = view?.url ?: ""
                if ((newUrl.isBlank() || newUrl == "about:blank") && !session.isDeliberateNewTab) {
                    return
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.progress = newProgress
                if (view != null) session.updateNavigationState(view)
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.combinedCanGoBack,
                    session.combinedCanGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                if (newProgress > 40 && isAdBlockActive) {
                    AdBlockManager.injectCosmeticFilters(context, view, view?.url, currentSettings)
                }
            }

            override fun onReceivedTitle(view: WebView?, t: String?) {
                val newUrl = view?.url ?: ""
                if ((newUrl.isBlank() || newUrl == "about:blank") && !session.isDeliberateNewTab) {
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
                    session.combinedCanGoBack,
                    session.combinedCanGoForward
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
                val activity = findActivity(context) ?: return
                if (activity.isFinishing || activity.isDestroyed) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                customView = view
                customViewCallback = callback
                
                session.view.visibility = View.GONE
                
                // Create custom container wrapping customView, the lock button, and speedup badge
                val container = FullscreenContainer(context)
                fullscreenContainer = container
                
                container.addView(customView, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
                
                // Create lock button (smaller size 48dp, padding 15dp)
                val density = context.resources.displayMetrics.density
                val buttonSize = (48 * density).toInt()
                val padding = (15 * density).toInt()
                
                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#40000000")) // 25% alpha black
                    setStroke(
                        (1f * density).toInt(),
                        android.graphics.Color.parseColor("#4DFFFFFF") // Subtle 30% alpha white line
                    )
                }
                
                val isLockedPref = settingsRepository.settingsFlow.value.isVideoOrientationLocked
                val initialIcon = if (isLockedPref) {
                    com.yue.browser.R.drawable.ic_orientation_lock
                } else {
                    com.yue.browser.R.drawable.ic_orientation_unlock
                }
                val btn = android.widget.ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                        leftMargin = (16 * density).toInt()
                    }
                    background = bgDrawable
                    setImageResource(initialIcon)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    setPadding(padding, padding, padding, padding)
                    elevation = 8 * density
                    alpha = 0f
                    visibility = android.view.View.GONE
                }
                
                btn.setOnClickListener {
                    isOrientationLocked = !isOrientationLocked
                    settingsRepository.setVideoOrientationLocked(isOrientationLocked)
                    val act = findActivity(context)
                    if (act != null) {
                        if (isOrientationLocked) {
                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            btn.setImageResource(com.yue.browser.R.drawable.ic_orientation_lock)
                        } else {
                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            btn.setImageResource(com.yue.browser.R.drawable.ic_orientation_unlock)
                        }
                    }
                    
                    // Vibrate haptic feedback
                    try {
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(40)
                        }
                    } catch (_: Exception) {}
                    
                    // Reschedule auto-hide
                    hideRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        hideLockButton(btn)
                    }
                    hideRunnable = runnable
                    mainHandler.postDelayed(runnable, 2000)
                }
                
                // Create speedup badge
                val badge = android.widget.TextView(context).apply {
                    val badgeBg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(android.graphics.Color.parseColor("#4D000000")) // 30% alpha black, matching speedup badge theme
                        cornerRadius = 16 * density
                    }
                    background = badgeBg
                    
                    // Padding: 5dp top/bottom, 12dp left/right
                    val padTopBottom = (5 * density).toInt()
                    val padLeftRight = (12 * density).toInt()
                    setPadding(padLeftRight, padTopBottom, padLeftRight, padTopBottom)
                    
                    setTextColor(android.graphics.Color.parseColor("#E6FFFFFF")) // 90% alpha white
                    textSize = 11f // 11sp
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    
                    val lp = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                        topMargin = (16 * density).toInt()
                    }
                    layoutParams = lp
                    
                    visibility = android.view.View.GONE
                    alpha = 0f
                }
                
                container.addView(btn)
                container.addView(badge)
                container.lockButton = btn
                this.lockButton = btn
                this.speedupBadge = badge
                isOrientationLocked = isLockedPref
                
                container.onTouchScreen = {
                    val lBtn = this.lockButton
                    if (lBtn != null) {
                        if (lBtn.visibility == android.view.View.VISIBLE && lBtn.alpha > 0.5f) {
                            hideLockButton(lBtn)
                        } else {
                            showLockButton(lBtn)
                        }
                    }
                }
                
                container.onSpeedupStart = {
                    val settings = settingsRepository.settingsFlow.value
                    if (settings.isVideoSpeedupEnabled) {
                        // 1. Show native speedup badge
                        showSpeedupBadge(settings.videoSpeedupRate)
                        // 2. Evaluate Javascript to speed up video
                        val formattedRate = String.format(java.util.Locale.US, "%.2f", settings.videoSpeedupRate).trimEnd('0').trimEnd('.')
                        val js = """
                            (function() {
                                window.__yue_is_speeding_up__ = true;
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    var v = videos[i];
                                    if (!v.paused && !v.ended) {
                                        if (v.__yue_original_rate__ === undefined) {
                                            v.__yue_original_rate__ = v.playbackRate || 1.0;
                                        }
                                        var targetRate = $formattedRate;
                                        var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                        try { setter.call(v, targetRate); } catch(e) { v.playbackRate = targetRate; }
                                    }
                                }
                            })();
                        """.trimIndent()
                        (session.view as? android.webkit.WebView)?.evaluateJavascript(js, null)
                        
                        // Vibrate brief haptic feedback on hold start
                        try {
                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(40)
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                container.onSpeedupEnd = {
                    val settings = settingsRepository.settingsFlow.value
                    if (settings.isVideoSpeedupEnabled) {
                        // 1. Hide native speedup badge
                        hideSpeedupBadge()
                        // 2. Evaluate Javascript to restore original speed
                        val js = """
                            (function() {
                                window.__yue_is_speeding_up__ = false;
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    var v = videos[i];
                                    if (v.__yue_original_rate__ !== undefined) {
                                        var orig = v.__yue_original_rate__;
                                        delete v.__yue_original_rate__;
                                        var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                        try { setter.call(v, orig); } catch(e) { v.playbackRate = orig; }
                                    }
                                }
                            })();
                        """.trimIndent()
                        (session.view as? android.webkit.WebView)?.evaluateJavascript(js, null)
                    }
                }
                
                // Signal JS that we're in Android fullscreen (suppress JS badge)
                try {
                    (session.view as? android.webkit.WebView)?.evaluateJavascript("window.__yue_in_fullscreen__ = true;", null)
                } catch (_: Exception) {}

                try {
                    val decorView = activity.window.decorView as? android.view.ViewGroup
                    decorView?.addView(container, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebChromeClient", "Error adding custom view container", e)
                    customView = null
                    customViewCallback = null
                    fullscreenContainer = null
                    lockButton = null
                    speedupBadge = null
                    session.view.visibility = View.VISIBLE
                    return
                }
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                
                if (isOrientationLocked) {
                    val config = context.resources.configuration
                    if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    } else {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                } else {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                
                // Show initially for 2 seconds
                showLockButton(btn)
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                hideRunnable?.let { mainHandler.removeCallbacks(it) }
                hideRunnable = null
                
                val activity = findActivity(context) ?: return
                if (activity.isFinishing || activity.isDestroyed) return
                
                val decorView = activity.window.decorView as? android.view.ViewGroup
                
                val container = fullscreenContainer
                if (container != null) {
                    try {
                        decorView?.removeView(container)
                    } catch (e: Exception) {
                        android.util.Log.e("SystemWebChromeClient", "Error removing custom view container", e)
                    }
                    fullscreenContainer = null
                }
                
                customView = null
                lockButton = null
                speedupBadge = null
                isOrientationLocked = false
                
                // Signal JS that fullscreen ended (re-enable JS badge)
                try {
                    (session.view as? android.webkit.WebView)?.evaluateJavascript("window.__yue_in_fullscreen__ = false;", null)
                } catch (_: Exception) {}

                try {
                    session.view.visibility = View.VISIBLE
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebChromeClient", "Error restoring session view visibility", e)
                }
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                // Lock to portrait when exiting fullscreen (no auto-rotate)
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                val activity = findActivity(context)
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
                if (session.elementPickerCallback != null) {
                    android.util.Log.d("SystemWebChromeClient", "Blocked popup window creation because element picker is active")
                    return false
                }
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = settings.isAdBlockEnabled || settings.enabledAddons.contains("ublock")
                val currentHost = try {
                    val openerUrl = view?.url
                    if (openerUrl.isNullOrBlank() || openerUrl == "about:blank") {
                        // view?.url bisa null / "about:blank" saat popup dibuka
                        // dari halaman yang belum selesai loading. Fallback ke
                        // session.url biar openerHost tidak empty — kalau empty
                        // nanti popup detection gagal dan tab ga auto-close.
                        android.net.Uri.parse(session.url).host ?: ""
                    } else {
                        android.net.Uri.parse(openerUrl).host ?: ""
                    }
                } catch (e: Exception) {
                    try {
                        android.net.Uri.parse(session.url).host ?: ""
                    } catch (e2: Exception) { ""
                    }
                }
                
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
                    if (AdBlockManager.isUrlRedirectingToBlocked(context, destinationUrl, settings) || AdBlockManager.isSearchEngineWithJudolQuery(context, destinationUrl)) {
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
                
                val isAnchorLink = hitTestResult != null &&
                    (hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                     hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                tempWebView.setTag(987654321, !isAnchorLink)

                session.newTabWithWebViewCallback?.invoke(tempWebView, isPrivate, currentHost)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

private class FullscreenContainer(context: android.content.Context) : android.widget.FrameLayout(context) {
    var lockButton: android.view.View? = null
    var onTouchScreen: (() -> Unit)? = null
    var onSpeedupStart: (() -> Unit)? = null
    var onSpeedupEnd: (() -> Unit)? = null

    private var startX = 0f
    private var startY = 0f
    private var isHolding = false
    private var holdDetectorRunnable: Runnable? = null
    private val density = context.resources.displayMetrics.density

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)

        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isHolding = false

                // Check if touch is on lock button
                val btn = lockButton
                var touchOnButton = false
                if (btn != null && btn.visibility == android.view.View.VISIBLE) {
                    val rect = android.graphics.Rect()
                    btn.getGlobalVisibleRect(rect)
                    if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        touchOnButton = true
                    }
                }

                if (!touchOnButton) {
                    // Start hold detector after 500ms
                    val runnable = Runnable {
                        isHolding = true
                        onSpeedupStart?.invoke()
                    }
                    holdDetectorRunnable = runnable
                    postDelayed(runnable, 500)
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - startX
                val dy = ev.y - startY
                if (Math.hypot(dx.toDouble(), dy.toDouble()) > 20 * density) {
                    // Cancel hold detector if moved too far
                    holdDetectorRunnable?.let {
                        removeCallbacks(it)
                        holdDetectorRunnable = null
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                holdDetectorRunnable?.let {
                    removeCallbacks(it)
                    holdDetectorRunnable = null
                }
                if (isHolding) {
                    onSpeedupEnd?.invoke()
                    isHolding = false
                } else {
                    // It was a tap!
                    // Check if it was on lock button
                    val btn = lockButton
                    var touchOnButton = false
                    if (btn != null && btn.visibility == android.view.View.VISIBLE) {
                        val rect = android.graphics.Rect()
                        btn.getGlobalVisibleRect(rect)
                        if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                            touchOnButton = true
                        }
                    }
                    if (!touchOnButton) {
                        onTouchScreen?.invoke()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

