package com.yue.browser.data.engine

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale

fun SystemWebViewSession.setupPrivateProfile() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        try {
            val profileStore = ProfileStore.getInstance()
            profileStore.getOrCreateProfile("incognito_profile")
            WebViewCompat.setProfile(webViewInstance, "incognito_profile")
        } catch (e: Exception) {
            Log.e("SystemWebViewSession", "Failed to set private profile on WebView", e)
        }
    }
}

fun SystemWebViewSession.observeSettingsChanges() {
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    settingsJob = GlobalScope.launch(Dispatchers.Main) {
        settingsRepository.settingsFlow.collect { settings ->
            if (isDestroyed) return@collect
            val speedupText = context.getString(com.yue.browser.R.string.video_speedup_indicator)
            val formattedRate = String.format(Locale.US, "%.2f", settings.videoSpeedupRate).trimEnd('0').trimEnd('.')
            try {
                webViewInstance.evaluateJavascript(
                    "window.__yue_speedup_enabled__ = ${settings.isVideoSpeedupEnabled}; window.__yue_speedup_rate__ = $formattedRate; window.__yue_speedup_text__ = '$speedupText';",
                    null
                )
            } catch (_: Exception) {}
        }
    }
}

fun SystemWebViewSession.setupJavaScriptInterfaces() {
    webViewInstance.addJavascriptInterface(object {
        @android.webkit.JavascriptInterface
        fun getSpeedupRate(): Float {
            return settingsRepository.settingsFlow.value.videoSpeedupRate
        }
        @android.webkit.JavascriptInterface
        fun isSpeedupEnabled(): Boolean {
            return settingsRepository.settingsFlow.value.isVideoSpeedupEnabled
        }
        @android.webkit.JavascriptInterface
        fun getSpeedupText(): String {
            return context.getString(com.yue.browser.R.string.video_speedup_indicator)
        }
        @android.webkit.JavascriptInterface
        fun isBackgroundPlayEnabled(): Boolean {
            val current = settingsRepository.settingsFlow.value
            return if (isPrivate) current.isBackgroundPlayEnabledPrivate else current.isBackgroundPlayEnabledNormal
        }
    }, "YueSettings")

    webViewInstance.addJavascriptInterface(
        SystemWebViewAddonsInterface(context, this, settingsRepository),
        "YueAddons"
    )
    webViewInstance.addJavascriptInterface(
        SystemWebViewMediaSessionInterface(context, this),
        "YueMediaSession"
    )

    webViewInstance.addJavascriptInterface(object {
        @android.webkit.JavascriptInterface
        fun onFormDetected(json: String) {
            passwordDetectedFieldsJson = json
            webViewInstance.post {
                onPasswordFieldsDetectedCallback?.invoke(json)
            }
        }
        @android.webkit.JavascriptInterface
        fun onFormSubmitted(json: String) {
            webViewInstance.post {
                onPasswordFormSubmittedCallback?.invoke(json)
            }
        }
        @android.webkit.JavascriptInterface
        fun onAutofillDismissed() {
            webViewInstance.post {
                onAutofillDismissedCallback?.invoke()
            }
        }
    }, "YuePasswordDetect")

    webViewInstance.addJavascriptInterface(object {
        @android.webkit.JavascriptInterface
        fun onStateChanged() {
            webViewInstance.post {
                try {
                    val currentUrl = webViewInstance.url ?: ""
                    updateNavigationState(webViewInstance)
                    val isHistoryNav = canGoBack || canGoForward
                    if (currentUrl.isNotEmpty() && (currentUrl != "about:blank" || isDeliberateNewTab || isHistoryNav)) {
                        val normalizedUrl = if (currentUrl == "about:blank") "yue://newtab" else currentUrl
                        url = normalizedUrl
                        title = webViewInstance.title ?: ""

                        stateCallback?.invoke(
                            normalizedUrl,
                            title,
                            progress,
                            combinedCanGoBack,
                            combinedCanGoForward
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SystemWebViewSession", "Error in YueState.onStateChanged", e)
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onSpaDepthChanged(depth: Int) {
            webViewInstance.post {
                try {
                    spaDepth = depth
                    val currentUrl = webViewInstance.url ?: ""
                    if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                        stateCallback?.invoke(
                            url,
                            title,
                            progress,
                            combinedCanGoBack,
                            combinedCanGoForward
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SystemWebViewSession", "Error in YueState.onSpaDepthChanged", e)
                }
            }
        }
    }, "YueState")
}

fun SystemWebViewSession.setupWebClients() {
    webViewInstance.webChromeClient = SystemWebChromeClient(context, this, settingsRepository, isPrivate)
    webViewInstance.webViewClient = SystemWebViewClient(context, this, settingsRepository, isPrivate)
}

fun SystemWebViewSession.configureWebViewSettings(currentSettings: BrowserSettings, initialUA: String) {
    AdBlockManager.ensureAdBlockerInitialized(context)

    val activity = context as? Activity
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    val isDarkActive = currentSettings.isDarkModeSimulated || currentSettings.enabledAddons.contains("darkreader")

    val bgColor = if (isDarkActive) Color.parseColor("#000000") else Color.parseColor("#FFFFFF")
    webViewInstance.setBackgroundColor(bgColor)

    webViewInstance.settings.apply {
        javaScriptEnabled = currentSettings.isJavaScriptEnabled
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportMultipleWindows(true)
        setJavaScriptCanOpenWindowsAutomatically(false)
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        allowFileAccess = true
        allowContentAccess = true
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = initialUA
        setGeolocationEnabled(false)
        textZoom = 100
        setSupportZoom(currentSettings.isZoomEnabled)
        builtInZoomControls = currentSettings.isZoomEnabled
        displayZoomControls = false
    }

    if (isPrivate) {
        webViewInstance.settings.databaseEnabled = true
        webViewInstance.settings.domStorageEnabled = true
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webViewInstance, true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        webViewInstance.settings.isAlgorithmicDarkeningAllowed = isDarkActive
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        webViewInstance.settings.forceDark = if (isDarkActive) {
            WebSettings.FORCE_DARK_ON
        } else {
            WebSettings.FORCE_DARK_OFF
        }
    }
}

fun SystemWebViewSession.setupDownloadListener() {
    webViewInstance.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        val actualAddonId = when {
            url.contains("ublock") || url.contains("cjpalhdlnbpafiamejdnhcphjbkeiagm") -> "ublock"
            url.contains("darkreader") || url.contains("eimadpcaloflhjddepbbgoikcjaggafg") -> "darkreader"
            url.contains("translator") || url.contains("mchibihcapipjolgdaiegimacnlaaldg") -> "translator"
            else -> null
        }
        if (actualAddonId != null) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Main) {
                settingsRepository.setAddonEnabled(actualAddonId, true)
                val name = when (actualAddonId) {
                    "ublock" -> "uBlock Origin Lite"
                    "darkreader" -> "Dark Reader"
                    "translator" -> "Page Translator"
                    else -> "Add-on"
                }
                Toast.makeText(
                    context,
                    context.getString(com.yue.browser.R.string.addon_installed_success, name),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            try {
                val fileName = guessFileNameSafe(url, contentDisposition, mimetype)
                val cookies = try {
                    CookieManager.getInstance().getCookie(url)
                } catch (_: Exception) { null }
                val webViewUA = try {
                    webViewInstance.settings.userAgentString
                } catch (_: Exception) { null }
                com.yue.browser.data.repository.DownloadRepositoryImpl.instance.let { repo ->
                    repo.initialize(context)
                    repo.setGlobalWebViewUserAgent(webViewUA ?: userAgent)
                    val defConnections = settingsRepository.settingsFlow.value.defaultConnectionCount
                    repo.startDownload(url, fileName, context, defConnections, cookies, webViewUA ?: userAgent)
                }
                Toast.makeText(
                    context,
                    context.getString(com.yue.browser.R.string.download_started, fileName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(com.yue.browser.R.string.download_failed_with_reason, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

fun SystemWebViewSession.setupDocumentStartScripts(currentSettings: BrowserSettings) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        val allowedRules = setOf("*")
        try {
            WebViewCompat.addDocumentStartJavaScript(
                webViewInstance,
                WebViewScripts.visibilityOverrideScript,
                allowedRules
            )
            WebViewCompat.addDocumentStartJavaScript(
                webViewInstance,
                WebViewScripts.mediaSessionScript,
                allowedRules
            )
            WebViewCompat.addDocumentStartJavaScript(
                webViewInstance,
                WebViewScripts.eventListenerHookScript,
                allowedRules
            )
            val speedupText = context.getString(com.yue.browser.R.string.video_speedup_indicator)
            val formattedRate = String.format(Locale.US, "%.2f", currentSettings.videoSpeedupRate).trimEnd('0').trimEnd('.')
            val settingsScript = "window.__yue_speedup_enabled__ = ${currentSettings.isVideoSpeedupEnabled}; window.__yue_speedup_rate__ = $formattedRate; window.__yue_speedup_text__ = '$speedupText';"
            WebViewCompat.addDocumentStartJavaScript(
                webViewInstance,
                settingsScript,
                allowedRules
            )
        } catch (e: Exception) {
            Log.e("SystemWebViewSession", "Failed to add start scripts", e)
        }
    }
}

fun SystemWebViewSession.updateUserAgent(currentUrl: String) {
    val expectedUA = getExpectedUserAgent(currentUrl)
    if (webViewInstance.settings.userAgentString != expectedUA) {
        webViewInstance.settings.userAgentString = expectedUA
    }
    webViewInstance.settings.useWideViewPort = true
    webViewInstance.settings.loadWithOverviewMode = true
}

fun SystemWebViewSession.getExpectedUserAgent(currentUrl: String): String {
    return UserAgentManager.getExpectedUserAgent(currentUrl, isDesktopMode, settingsRepository.settingsFlow.value)
}

fun SystemWebViewSession.buildMainFrameHeaders(targetUrl: String? = null, reload: Boolean = false): Map<String, String> {
    val headers = HashMap<String, String>()
    try {
        val currentSettings = settingsRepository.settingsFlow.value
        val urlForUA = if (!targetUrl.isNullOrBlank() && targetUrl != "about:blank") targetUrl else this.url
        val currentUA = UserAgentManager.getExpectedUserAgent(
            urlForUA,
            isDesktopMode,
            currentSettings
        )
        headers["User-Agent"] = currentUA
        if (reload) {
            headers.putAll(UserAgentManager.getReloadHeaders(isDesktopMode))
        } else {
            headers.putAll(UserAgentManager.getDefaultHeaders(isDesktopMode))
        }
    } catch (e: Exception) {
        Log.e("SystemWebViewSession", "buildMainFrameHeaders failed", e)
    }
    return headers
}

fun SystemWebViewSession.guessFileNameSafe(url: String, contentDisposition: String?, mimeType: String?): String {
    var fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    if (mimeType == "application/vnd.android.package-archive" && !fileName.endsWith(".apk", ignoreCase = true)) {
        val lastDot = fileName.lastIndexOf('.')
        fileName = if (lastDot != -1) {
            fileName.substring(0, lastDot) + ".apk"
        } else {
            "$fileName.apk"
        }
    }
    if (fileName.endsWith(".bin", ignoreCase = true)) {
        try {
            val uri = Uri.parse(url)
            val lastPathSegment = uri.lastPathSegment ?: ""
            val extIdx = lastPathSegment.lastIndexOf('.')
            if (extIdx != -1 && extIdx < lastPathSegment.length - 1) {
                val realExt = lastPathSegment.substring(extIdx).toLowerCase(Locale.US)
                val commonExtensions = setOf(".apk", ".pdf", ".zip", ".png", ".jpg", ".jpeg", ".mp4", ".mp3", ".txt", ".html", ".epub")
                if (commonExtensions.contains(realExt)) {
                    fileName = fileName.substring(0, fileName.length - 4) + realExt
                }
            }
        } catch (_: Exception) {}
    }
    return fileName
}


