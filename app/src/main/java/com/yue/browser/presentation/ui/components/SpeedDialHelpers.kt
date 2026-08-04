package com.yue.browser.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.model.SpeedDialConfig

@Composable
internal fun rememberCombinedSpeedDials(
    settings: BrowserSettings,
    historyList: List<HistoryItem>
): List<SpeedDialConfig> {
    return remember(settings.speedDials, historyList) {
        val result = mutableListOf<SpeedDialConfig>()
        val addedHosts = mutableSetOf<String>()
        val colors = listOf("4285F4", "34A853", "FBBC05", "EA4335", "9C27B0", "3F51B5", "00BCD4", "E91E63")

        // 1. Add top history items first (most visited → frequently used)
        val topVisited = historyList
            .filter { it.url.startsWith("http") }
            .sortedWith(compareByDescending<HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })

        for (item in topVisited) {
            if (result.size >= 8) break

            val uri = try { android.net.Uri.parse(item.url) } catch (e: java.lang.Exception) { null }
            val host = uri?.host ?: item.url
            val cleanHost = host.removePrefix("www.").removePrefix("m.").substringBefore("/").lowercase(java.util.Locale.US)

            if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                val letter = (item.title.trim().firstOrNull() ?: cleanHost.firstOrNull() ?: 'W')
                    .toString().uppercase(java.util.Locale.US)

                val cleanName = if (item.title.isNotBlank() && !item.title.startsWith("http") && item.title.length < 30) {
                    item.title
                } else {
                    host.removePrefix("www.").removePrefix("m.").substringBefore(".")
                }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }

                val colorIndex = Math.abs(cleanHost.hashCode()) % colors.size
                val baseUrl = if (uri != null && uri.host != null) "${uri.scheme}://${uri.host}/" else item.url
                result.add(
                    SpeedDialConfig(
                        name = cleanName,
                        url = baseUrl,
                        iconLetter = letter,
                        iconBgColorHex = colors[colorIndex]
                    )
                )

                addedHosts.add(cleanHost)
            }
        }

        // 2. Fill remaining slots with user's speed dials (defaults or custom)
        if (result.size < 8) {
            settings.speedDials.forEach { dial ->
                if (result.size >= 8) return@forEach
                val host = try { android.net.Uri.parse(dial.url).host } catch (e: java.lang.Exception) { null } ?: dial.url
                val cleanHost = host.removePrefix("www.").removePrefix("m.").lowercase(java.util.Locale.US)
                if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                    result.add(dial)
                    addedHosts.add(cleanHost)
                }
            }
        }

        result.take(8)
    }
}
