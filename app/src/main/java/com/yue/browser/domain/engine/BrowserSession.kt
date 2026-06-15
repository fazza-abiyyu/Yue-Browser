package com.yue.browser.domain.engine

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface BrowserSession {
    val id: String
    val isPrivate: Boolean
    val url: String
    val title: String
    val progress: Int
    val canGoBack: Boolean
    val canGoForward: Boolean
    val view: View // Underlying view for screenshot capture, clearFocus, keyboard token, etc.

    // Callbacks
    var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)?
    var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)?
    var faviconCallback: ((android.graphics.Bitmap) -> Unit)?
    var thumbnailCaptureCallback: ((android.graphics.Bitmap) -> Unit)?
    var newTabWithWebViewCallback: ((android.webkit.WebView, Boolean, String) -> Unit)?
    var requestCloseCallback: (() -> Unit)?

    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun destroy()
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    fun setJavaScriptEnabled(enabled: Boolean)
    fun isJavaScriptEnabled(): Boolean
    fun setForceDarkMode(enabled: Boolean)
    fun setDesktopModeEnabled(enabled: Boolean)
    fun isDesktopModeEnabled(): Boolean
    fun setZoomEnabled(enabled: Boolean)
    fun captureThumbnail(callback: (android.graphics.Bitmap) -> Unit)
    fun startElementPicker(onElementsPicked: (cssSelectors: List<String>) -> Unit, onCancel: () -> Unit = {}, isDark: Boolean = false)
    fun stopElementPicker()

    @Composable
    fun Render(
        modifier: Modifier,
        onScrollChanged: (visible: Boolean) -> Unit,
        onReload: () -> Unit,
        isGone: Boolean
    )
}

