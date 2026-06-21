package com.yue.browser.data.engine

import android.webkit.JavascriptInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

    class SystemWebViewAddonsInterface(
    private val context: android.content.Context,
    private val session: SystemWebViewSession,
    private val settingsRepository: com.yue.browser.domain.repository.SettingsRepository
) {
        @JavascriptInterface
        fun installAddon(addonUrl: String) {
            val actualAddonId = when {
                addonUrl.contains("ublock") || addonUrl.contains("cjpalhdlnbpafiamejdnhcphjbkeiagm") -> "ublock"
                addonUrl.contains("darkreader") || addonUrl.contains("eimadpcaloflhjddepbbgoikcjaggafg") -> "darkreader"
                addonUrl.contains("translator") || addonUrl.contains("mchibihcapipjolgdaiegimacnlaaldg") -> "translator"
                else -> null
            }
            
            (session.view as android.webkit.WebView).post {
                if (actualAddonId != null) {
                    GlobalScope.launch(Dispatchers.Main) {
                        settingsRepository.setAddonEnabled(actualAddonId, true)
                        val name = when (actualAddonId) {
                            "ublock" -> "uBlock Origin Lite"
                            "darkreader" -> "Dark Reader"
                            "translator" -> "Page Translator"
                            else -> "Add-on"
                        }
                        android.widget.Toast.makeText(context, context.getString(com.yue.browser.R.string.addon_installed_success, name), android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    GlobalScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(com.yue.browser.R.string.addon_not_supported), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        @JavascriptInterface
        fun translateText(text: String, sourceLanguage: String, targetLanguage: String, callbackId: String) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
                    val url = java.net.URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLanguage&tl=$targetLanguage&dt=t&q=$encodedText")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    
                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val result = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = org.json.JSONArray(result)
                        val firstArray = jsonArray.optJSONArray(0)
                        val sb = java.lang.StringBuilder()
                        if (firstArray != null) {
                            for (i in 0 until firstArray.length()) {
                                val item = firstArray.optJSONArray(i)
                                if (item != null) {
                                    sb.append(item.optString(0, ""))
                                }
                            }
                        }
                        val translatedText = sb.toString()
                        val escapedText = org.json.JSONObject.quote(translatedText)
                        
                        GlobalScope.launch(Dispatchers.Main) {
                            session.evaluateJavascript("window.onTranslationCompleted($escapedText, '$callbackId')", null)
                        }
                    } else {
                        GlobalScope.launch(Dispatchers.Main) {
                            session.evaluateJavascript("window.onTranslationFailed('$callbackId')", null)
                        }
                    }
                } catch (e: Exception) {
                    GlobalScope.launch(Dispatchers.Main) {
                        session.evaluateJavascript("window.onTranslationFailed('$callbackId')", null)
                    }
                }
            }
        }
    }
