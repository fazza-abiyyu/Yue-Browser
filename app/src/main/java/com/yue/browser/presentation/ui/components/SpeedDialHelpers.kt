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
        val topVisited = historyList
            .filter { it.url.startsWith("http") }
            .sortedWith(compareByDescending<HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })
            .take(6)

        val topDials = topVisited.map { item ->
            val uri = try { android.net.Uri.parse(item.url) } catch (e: java.lang.Exception) { null }
            val host = uri?.host ?: item.url
            val cleanHost = host.removePrefix("www.").removePrefix("m.").substringBefore("/")

            val letter = (item.title.trim().firstOrNull() ?: cleanHost.firstOrNull() ?: 'W')
                .toString().uppercase(java.util.Locale.US)

            val cleanName = if (item.title.isNotBlank() && !item.title.startsWith("http") && item.title.length < 30) {
                item.title
            } else {
                cleanHost.substringBefore(".")
            }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }

            val colors = listOf("4285F4", "34A853", "FBBC05", "EA4335", "9C27B0", "3F51B5", "00BCD4", "E91E63")
            val colorIndex = Math.abs(cleanHost.hashCode()) % colors.size
            val colorHex = colors[colorIndex]

            SpeedDialConfig(
                name = cleanName,
                url = item.url,
                iconLetter = letter,
                iconBgColorHex = colorHex
            )
        }

        val result = mutableListOf<SpeedDialConfig>()
        val addedHosts = mutableSetOf<String>()

        topDials.forEach { dial ->
            val host = try { android.net.Uri.parse(dial.url).host } catch (e: java.lang.Exception) { null } ?: dial.url
            val cleanHost = host.removePrefix("www.").removePrefix("m.")
            if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                result.add(dial)
                addedHosts.add(cleanHost)
            }
        }

        settings.speedDials.forEach { dial ->
            val host = try { android.net.Uri.parse(dial.url).host } catch (e: java.lang.Exception) { null } ?: dial.url
            val cleanHost = host.removePrefix("www.").removePrefix("m.")
            if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                result.add(dial)
                addedHosts.add(cleanHost)
            }
        }
        result.take(6)
    }
}
