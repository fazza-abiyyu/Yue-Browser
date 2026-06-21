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
            android.util.Log.d("YueTranslate", "translateText called: textLength=${text.length}, sl=$sourceLanguage, tl=$targetLanguage, callbackId=$callbackId")
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLanguage&tl=$targetLanguage&dt=t")
                    android.util.Log.d("YueTranslate", "Sending POST request to: https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLanguage&tl=$targetLanguage")
                    
                    val postData = "q=" + java.net.URLEncoder.encode(text, "UTF-8")
                    val postDataBytes = postData.toByteArray(charset("UTF-8"))
                    
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.setRequestProperty("Content-Length", postDataBytes.size.toString())
                    
                    connection.outputStream.use { out ->
                        out.write(postDataBytes)
                    }
                    
                    val responseCode = connection.responseCode
                    android.util.Log.d("YueTranslate", "Response code: $responseCode")
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
                        android.util.Log.d("YueTranslate", "Translation succeeded. Output length: ${translatedText.length}")
                        val escapedText = org.json.JSONObject.quote(translatedText)
                        
                        GlobalScope.launch(Dispatchers.Main) {
                            session.evaluateJavascript("window.onTranslationCompleted($escapedText, '$callbackId')", null)
                        }
                    } else {
                        android.util.Log.e("YueTranslate", "Error response code: $responseCode")
                        GlobalScope.launch(Dispatchers.Main) {
                            session.evaluateJavascript("window.onTranslationFailed('$callbackId')", null)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YueTranslate", "Exception in translateText", e)
                    GlobalScope.launch(Dispatchers.Main) {
                        session.evaluateJavascript("window.onTranslationFailed('$callbackId')", null)
                    }
                }
            }
        }
    }
