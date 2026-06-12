package com.yue.browser.data.engine

import android.webkit.WebView
import com.yue.browser.domain.model.UserScript
import java.util.UUID

object UserScriptEngine {

    val gmPolyfill: String by lazy {
        """
(function() {
    if (window._yue_gm_polyfill) return;
    window._yue_gm_polyfill = true;
    var LS = function() {
        try { return localStorage; } catch(e) { return {getItem:function(){},setItem:function(){},removeItem:function(){},key:function(){},get length(){return 0}}; }
    }();
    var P = function(k) { return 'yue_gm_' + k; };

    if (typeof GM_addStyle === 'undefined') {
        GM_addStyle = function GM_addStyle(css) {
            var s = document.createElement('style');
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
            return s;
        };
    }
    if (typeof GM_xmlhttpRequest === 'undefined') {
        GM_xmlhttpRequest = function GM_xmlhttpRequest(d) {
            var x = new XMLHttpRequest();
            x.open(d.method || 'GET', d.url, true);
            if (d.headers) { for (var k in d.headers) x.setRequestHeader(k, d.headers[k]); }
            x.responseType = d.responseType || '';
            x.onload = function() {
                if (d.onload) d.onload({ responseText: x.responseText, response: x.response, status: x.status, readyState: 4, responseHeaders: x.getAllResponseHeaders() });
            };
            x.onerror = function() { if (d.onerror) d.onerror({ finalUrl: d.url, readyState: 0 }); };
            x.onprogress = function(e) { if (d.onprogress) d.onprogress({ loaded: e.loaded, total: e.total, lengthComputable: e.lengthComputable }); };
            x.send(d.data || null);
            return { abort: function() { x.abort(); } };
        };
    }
    if (typeof GM_openInTab === 'undefined') {
        GM_openInTab = function GM_openInTab(url) { window.open(url, '_blank'); };
    }
    if (typeof GM_setValue === 'undefined') {
        GM_setValue = function GM_setValue(k, v) { try { LS.setItem(P(k), JSON.stringify(v)); } catch(e) {} };
    }
    if (typeof GM_getValue === 'undefined') {
        GM_getValue = function GM_getValue(k, d) { try { var v = LS.getItem(P(k)); return v !== null ? JSON.parse(v) : d; } catch(e) { return d; } };
    }
    if (typeof GM_deleteValue === 'undefined') {
        GM_deleteValue = function GM_deleteValue(k) { try { LS.removeItem(P(k)); } catch(e) {} };
    }
    if (typeof GM_listValues === 'undefined') {
        GM_listValues = function GM_listValues() {
            var r = [];
            for (var i = 0; i < LS.length; i++) {
                var k = LS.key(i);
                if (k && k.indexOf('yue_gm_') === 0) r.push(k.substring(7));
            }
            return r;
        };
    }
    if (typeof GM_registerMenuCommand === 'undefined') {
        GM_registerMenuCommand = function GM_registerMenuCommand() {};
    }
    if (typeof GM_notification === 'undefined') {
        GM_notification = function GM_notification(d) {
            if (typeof d === 'string') d = { text: d };
            try { Notification.requestPermission().then(function(p) {
                if (p === 'granted') new Notification(d.title || 'UserScript', { body: d.text || '', icon: d.image || '' });
            }); } catch(e) {}
        };
    }
    if (typeof GM_log === 'undefined') {
        GM_log = function GM_log() { console.log.apply(console, arguments); };
    }
    if (typeof GM_setClipboard === 'undefined') {
        GM_setClipboard = function GM_setClipboard(text) {
            try {
                var ta = document.createElement('textarea');
                ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
                document.body.appendChild(ta); ta.select();
                document.execCommand('copy'); document.body.removeChild(ta);
            } catch(e) {}
        };
    }
    if (typeof unsafeWindow === 'undefined') { unsafeWindow = window; }
    if (typeof GM_info === 'undefined') {
        GM_info = { script: { version: '1.0' }, scriptHandler: 'Yue Browser' };
    }
    if (typeof GM === 'undefined') {
        GM = {
            addStyle: GM_addStyle,
            xmlhttpRequest: GM_xmlhttpRequest,
            openInTab: GM_openInTab,
            setValue: GM_setValue,
            getValue: GM_getValue,
            deleteValue: GM_deleteValue,
            listValues: GM_listValues,
            registerMenuCommand: GM_registerMenuCommand,
            notification: GM_notification,
            log: GM_log,
            setClipboard: GM_setClipboard,
            info: GM_info
        };
    }
})();
        """.trimIndent()
    }

    fun parseMetadata(rawCode: String, installUrl: String = ""): UserScript? {
        val metadata = extractMetadataBlock(rawCode) ?: return null
        val id = metadata["id"]
            ?: metadata["namespace"]
            ?: metadata["name"]?.lowercase()?.replace(Regex("[^a-z0-9]"), "_")
            ?: UUID.randomUUID().toString()
        val name = metadata["name"] ?: return null

        return UserScript(
            id = id,
            name = name,
            description = metadata["description"] ?: "",
            version = metadata["version"] ?: "1.0",
            author = metadata["author"] ?: metadata["creator"] ?: "",
            namespace = metadata["namespace"] ?: "",
            matchPatterns = metadata["match"]?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: metadata["include"]?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("*://*/*"),
            grantPermissions = metadata["grant"]?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            requireUrls = metadata["require"]?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            code = rawCode,
            isEnabled = true,
            installUrl = installUrl
        )
    }

    fun injectScripts(view: WebView?, scripts: List<UserScript>, context: android.content.Context) {
        if (scripts.isEmpty()) return

        // Inject GM polyfill once (runs only once per page thanks to _yue_gm_polyfill flag)
        view?.evaluateJavascript(gmPolyfill, null)

        for (script in scripts) {
            val scriptId = script.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
            view?.evaluateJavascript("""
                (function() {
                    if (document.getElementById('yue-userscript-$scriptId')) return;
                    var s = document.createElement('script');
                    s.id = 'yue-userscript-$scriptId';
                    s.setAttribute('data-yue-userscript', '1');
                    try {
                        s.appendChild(document.createTextNode(${jsStringLiteral(script.code)}));
                    } catch(e) {
                        s.textContent = ${jsStringLiteral(script.code)};
                    }
                    document.head.appendChild(s);
                })();
            """.trimIndent(), null)
        }
    }

    private fun extractMetadataBlock(code: String): Map<String, String>? {
        val lines = code.lines()
        val startIdx = lines.indexOfFirst { it.trim().removePrefix("//").trim() == "==UserScript==" }
        val endIdx = lines.indexOfFirst { it.trim().removePrefix("//").trim() == "==/UserScript==" }
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return null

        val metadata = mutableMapOf<String, String>()
        val multilineKeys = mutableMapOf<String, StringBuilder>()

        for (i in (startIdx + 1) until endIdx) {
            val line = lines[i].trim()
            if (!line.startsWith("// @")) continue
            val content = line.removePrefix("// @")
            val spaceIdx = content.indexOf(' ')
            if (spaceIdx == -1) {
                metadata[content.trim()] = ""
                continue
            }
            val key = content.substring(0, spaceIdx).trim()
            val value = content.substring(spaceIdx + 1).trim()

            if (value.endsWith("\\") || (!value.contains(" ") && metadata.containsKey(key))) {
                val builder = multilineKeys.getOrPut(key) { StringBuilder(metadata[key] ?: "") }
                if (builder.isNotEmpty()) builder.append("\n")
                builder.append(value.removeSuffix("\\"))
                metadata[key] = builder.toString()
            } else {
                val builder = multilineKeys[key]
                if (builder != null) {
                    builder.append("\n").append(value)
                    metadata[key] = builder.toString()
                } else {
                    val existing = metadata[key]
                    if (existing != null) {
                        metadata[key] = "$existing\n$value"
                    } else {
                        metadata[key] = value
                    }
                }
            }
        }
        return metadata
    }

    private fun jsStringLiteral(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        return "'$escaped'"
    }

    fun downloadScript(
        url: String,
        context: android.content.Context,
        onResult: (script: UserScript?, error: String?) -> Unit
    ) {
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
                conn.setRequestProperty("User-Agent", ua)
                conn.setRequestProperty("Accept", "text/x-user-script,application/javascript,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
                conn.setRequestProperty("DNT", "1")
                val code = conn.responseCode
                android.util.Log.d("UserScriptEngine", "download: $url -> HTTP $code")
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("UserScriptEngine", "body (${body.length} chars): ${body.take(200)}")
                    val script = parseMetadata(body, url)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onResult(script, if (script == null) "Metadata blok tidak ditemukan" else null)
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onResult(null, "HTTP $code")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(null, e.message ?: e.javaClass.simpleName)
                }
            }
        }.start()
    }
}
