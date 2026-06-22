package com.yue.browser.data.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

fun SystemWebViewSession.startElementPickerHelper(
    onElementsPicked: (cssSelectors: List<String>) -> Unit,
    onCancel: () -> Unit,
    isDark: Boolean
) {
    elementPickerCallback = onElementsPicked
    elementPickerCancelCallback = onCancel
    webViewInstance.post {
        val labelHapus = context.getString(com.yue.browser.R.string.picker_hapus)
        val labelSelected = context.getString(com.yue.browser.R.string.picker_selected_count)
        val labelHint = context.getString(com.yue.browser.R.string.picker_hint)
        webViewInstance.evaluateJavascript(
            WebViewScriptsVideo.elementPickerScript(isDark, labelHapus, labelSelected, labelHint),
            null
        )
    }
}

fun SystemWebViewSession.stopElementPickerHelper() {
    elementPickerCallback = null
    elementPickerCancelCallback = null
    webViewInstance.post {
        webViewInstance.evaluateJavascript(
            """
            (function() {
                ['__yue_picker_overlay__','__yue_picker_toolbar__','__yue_picker_style__',
                 '__yue_picker_overlay_style__','__yue_picker_dead_style__'].forEach(function(id) {
                    try { var e = document.getElementById(id); if (e) e.remove(); } catch(e) {}
                });
                window.__yuePicker__ = null;
                window.__yuePickerActive__ = false;
            })();
            """.trimIndent(),
            null
        )
    }
}

private fun SystemWebViewSession.goBackIfBlankPage() {
    val currentUrl = webViewInstance.url ?: url
    if (currentUrl == "about:blank" || currentUrl == "yue://newtab" || currentUrl.isBlank()) {
        webViewInstance.post {
            if (canGoBack) {
                webViewInstance.goBack()
            } else if (!openerHost.isNullOrEmpty()) {
                requestCloseCallback?.invoke()
            }
        }
    }
}

fun SystemWebViewSession.handleElementPickerSubmit(selectorsJson: String) {
    val cb = elementPickerCallback ?: return
    try {
        val arr = JSONArray(selectorsJson)
        val selectors = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            selectors.add(arr.getString(i))
        }
        webViewInstance.post {
            cb(selectors)
            val combined = selectors.joinToString(", ")
            val escaped = JSONObject.quote(combined)
            val hideScript = "(function() { try { var style = document.getElementById('__yue_blocked_css__'); if (!style) { style = document.createElement('style'); style.id = '__yue_blocked_css__'; document.head.appendChild(style); } style.textContent += $escaped + ' { display: none !important; visibility: hidden !important; }\\n'; } catch(e) {} })();"
            webViewInstance.evaluateJavascript(hideScript, null)
            stopElementPickerHelper()
            goBackIfBlankPage()
        }
    } catch (e: Exception) {
        Log.e("ElementPicker", "Error parsing selector JSON", e)
    }
}

fun SystemWebViewSession.handleElementPickerCancel() {
    val cancel = elementPickerCancelCallback
    webViewInstance.post {
        cancel?.invoke()
    }
    stopElementPickerHelper()
    goBackIfBlankPage()
}
