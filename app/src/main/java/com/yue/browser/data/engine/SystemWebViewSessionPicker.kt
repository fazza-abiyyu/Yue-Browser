package com.yue.browser.data.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

fun SystemWebViewSession.startElementPickerHelper(
    onElementsPicked: (cssSelectors: List<String>) -> Unit,
    onCancel: () -> Unit,
    isDark: Boolean,
    initialMode: String = "HOLD"
) {
    elementPickerCallback = onElementsPicked
    elementPickerCancelCallback = onCancel
    webViewInstance.post {
        val labelHapus = context.getString(com.yue.browser.R.string.picker_hapus)
        val labelSelected = context.getString(com.yue.browser.R.string.picker_selected_count)
        val labelHint = context.getString(com.yue.browser.R.string.picker_hint)
        webViewInstance.evaluateJavascript(
            WebViewScriptsVideo.elementPickerScript(isDark, labelHapus, labelSelected, labelHint)
        ) {
            // WAJIB di-chain: apply mode awal SETELAH script terpasang.
            // Kalau dipanggil terpisah sebelum inject selesai, window.__yuePickerSetMode
            // belum ada → JS tertinggal di default BULK sementara UI menampilkan HOLD.
            webViewInstance.evaluateJavascript(
                "if(window.__yuePickerSetMode)window.__yuePickerSetMode('$initialMode')", null
            )
        }
    }
}

fun SystemWebViewSession.stopElementPickerHelper(notifyClosed: Boolean = false) {
    elementPickerCallback = null
    elementPickerCancelCallback = null
    // Ambil callback lalu null-kan dulu supaya tidak rekursi kalau callback
    // memicu stopElementPicker() lagi dari sisi native.
    val closed = pickerClosedCallback
    if (notifyClosed) pickerClosedCallback = null
    webViewInstance.post {
        webViewInstance.evaluateJavascript(
            """
            (function() {
                // PENTING: Panggil stop() dulu untuk remove document-level event handlers
                // (click/touchstart/touchmove/touchend/mousemove yang didaftarkan via addEventListener).
                // Tanpa ini, handler tetap nempel di document meskipun overlay DOM sudah dihapus —
                // hasilnya: native toolbar hilang tapi tapping page masih trigger picker selection.
                try {
                    if (window.__yuePicker__ && typeof window.__yuePicker__.stop === 'function') {
                        window.__yuePicker__.stop();
                    }
                } catch(e) {}
                // Fallback cleanup — hapus sisa DOM elements kalau stop() sudah jalan atau gagal
                ['__yue_picker_overlay__','__yue_picker_toolbar__','__yue_picker_style__',
                 '__yue_picker_overlay_style__','__yue_picker_dead_style__'].forEach(function(id) {
                    try { var e = document.getElementById(id); if (e) e.remove(); } catch(e) {}
                });
                window.__yuePicker__ = null;
                window.__yuePickerActive__ = false;
                window.__yuePickerSubmit = null;
                window.__yuePickerCancel = null;
                window.__yuePickerClear = null;
            })();
            """.trimIndent(),
            null
        )
    }
    // Page refresh / navigasi menghancurkan JS picker — beri tahu UI native
    // supaya toolbar ikut tertutup, bukan tertinggal dengan count lama.
    if (notifyClosed) closed?.invoke()
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

        // Persist CSS selectors per-domain — biar apply otomatis di page load berikutnya
        if (selectors.isNotEmpty()) {
            val pageHost = try {
                android.net.Uri.parse(webViewInstance.url ?: url).host?.lowercase(java.util.Locale.US)
            } catch (_: Exception) { null }
            if (!pageHost.isNullOrBlank()) {
                val repo = settingsRepository
                selectors.forEach { sel ->
                    repo.addBlockedCssSelector(pageHost, sel)
                }
                // Simpan permanen, tapi tandai privat kalau dipilih dari tab incognito —
                // UI akan menyamarkan domain & selectornya sampai dibuka dengan biometrik/PIN.
                if (isPrivate) repo.markPrivateBlockedCssDomain(pageHost)
                android.util.Log.d("ElementPicker", "Saved ${selectors.size} CSS selectors for domain=$pageHost")
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
