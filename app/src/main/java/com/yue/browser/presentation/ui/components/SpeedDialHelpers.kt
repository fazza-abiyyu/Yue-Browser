package com.yue.browser.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.model.SpeedDialConfig

/** Maksimal shortcut di new tab (feedback user 2026-11-07: maks 12, min 0). */
internal const val MAX_SPEED_DIALS = 12

/** Hasil kombinasi shortcut new tab: dial milik user + (opsional) isi slot kosong dari history. */
internal data class CombinedSpeedDials(
    val dials: List<SpeedDialConfig>,
    /** URL yang berasal dari auto-fill history (bukan shortcut milik user). */
    val autoUrls: Set<String>
)

@Composable
internal fun rememberCombinedSpeedDials(
    settings: BrowserSettings,
    historyList: List<HistoryItem>
): CombinedSpeedDials {
    return remember(settings.speedDials, settings.speedDialFillFrequent, settings.speedDialLimit, historyList) {
        val limit = settings.speedDialLimit.coerceIn(0, MAX_SPEED_DIALS)
        val colors = com.yue.browser.presentation.theme.CategoryPaletteHex
        val result = mutableListOf<SpeedDialConfig>()
        val addedHosts = mutableSetOf<String>()
        val autoUrls = mutableSetOf<String>()

        fun hostOf(url: String): String {
            val host = try { android.net.Uri.parse(url).host } catch (e: Exception) { null } ?: url
            return host.removePrefix("www.").removePrefix("m.").substringBefore("/").lowercase(java.util.Locale.US)
        }

        // 1. Shortcut milik user dulu (urutan tersimpan) — 0 s/d limit
        settings.speedDials.forEach { dial ->
            if (result.size >= limit) return@forEach
            val cleanHost = hostOf(dial.url)
            if (cleanHost.isNotEmpty() && !addedHosts.contains(cleanHost)) {
                result.add(dial)
                addedHosts.add(cleanHost)
            }
        }

        // 2. Opsi: isi slot kosong dengan situs yang sering dipakai (history)
        if (settings.speedDialFillFrequent) {
            val topVisited = historyList
                .filter { it.url.startsWith("http") }
                .sortedWith(compareByDescending<HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })

            for (item in topVisited) {
                if (result.size >= limit) break

                val uri = try { android.net.Uri.parse(item.url) } catch (e: java.lang.Exception) { null }
                val cleanHost = if (uri?.host != null) hostOf(item.url) else ""
                if (cleanHost.isEmpty() || addedHosts.contains(cleanHost)) continue

                val letter = (item.title.trim().firstOrNull() ?: cleanHost.firstOrNull() ?: 'W')
                    .toString().uppercase(java.util.Locale.US)

                val cleanName = if (item.title.isNotBlank() && !item.title.startsWith("http") && item.title.length < 30) {
                    item.title
                } else {
                    (uri?.host ?: item.url).removePrefix("www.").removePrefix("m.").substringBefore(".")
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
                autoUrls.add(baseUrl)
            }
        }

        CombinedSpeedDials(result.take(limit), autoUrls)
    }
}
