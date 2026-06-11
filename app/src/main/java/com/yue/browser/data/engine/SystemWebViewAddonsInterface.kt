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
                        android.widget.Toast.makeText(context, "$name berhasil dipasang!", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    GlobalScope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Add-on ini tidak didukung di Yue Browser", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
