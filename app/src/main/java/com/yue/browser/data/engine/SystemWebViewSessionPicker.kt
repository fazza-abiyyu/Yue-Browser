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
    android.util.Log.d("SystemWebViewSessionPicker", "goBackIfBlankPage: currentUrl=$currentUrl openerHost=$openerHost canGoBack=$canGoBack")
    if (currentUrl == "about:blank" || currentUrl == "yue://newtab" || currentUrl.isBlank()) {
        webViewInstance.post {
            if (canGoBack) {
                webViewInstance.goBack()
            } else if (!openerHost.isNullOrEmpty()) {
                android.util.Log.d("SystemWebViewSessionPicker", "Closing tab via goBackIfBlankPage: url=$url")
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
        val netUrls = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val entry = arr.getString(i)
            if (entry.startsWith("@@net|")) {
                netUrls.add(entry.removePrefix("@@net|"))
            } else {
                selectors.add(entry)
            }
        }
        // Network-level block: for every third-party resource backing the picked
        // elements, add its host to the custom ad-block filter list so requests to
        // it are cut in shouldInterceptRequest — not just hidden via CSS.
        if (netUrls.isNotEmpty()) {
            val pageHost = try {
                android.net.Uri.parse(webViewInstance.url ?: url).host?.lowercase(java.util.Locale.US)
            } catch (_: Exception) { null }
            val repo = settingsRepository
            netUrls.forEach { raw ->
                val host = try {
                    android.net.Uri.parse(raw).host?.lowercase(java.util.Locale.US)
                } catch (_: Exception) { null }
                if (!host.isNullOrBlank() && host != pageHost && !pageHost.isNullOrBlank() && !host.endsWith(".$pageHost")) {
                    repo.addCustomAdBlockFilter(host)
                    android.util.Log.d("ElementPicker", "Network-block host from picked element: $host (from $raw)")
                }
            }
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
