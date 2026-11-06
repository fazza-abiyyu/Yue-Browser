package com.yue.browser.data.adblock.remote

import android.content.Context
import android.util.Log
import com.yue.browser.data.adblock.engine.HostBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Lazy remote filter loader — download domain blocklists dari GitHub raw URL,
 * parse format beragam (hosts, easylist, plain), cache ke disk, merge ke HostBlocker.
 *
 * DI-DESIGN UNTUK TIDAK MEMBLOCK STARTUP:
 * - Dipanggil setelah asset rules selesai di-load (background coroutine)
 * - Hanya download kalau cache lokal > 24 jam
 * - Kegagalan download tidak fatal (silent fallback ke asset rules)
 */
class RemoteFilterLoader(
    private val context: Context,
    private val hostBlocker: HostBlocker,
) {
    companion object {
        private const val TAG = "RemoteFilterLoader"
        private const val PREFS_NAME = "yue_adblock_remote"
        private const val KEY_LAST_DOWNLOAD = "last_download_ms"
        private const val DOWNLOAD_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 jam

        // Hanya source TERBAIK + TER-RINGAN.
        private val REMOTE_SOURCES = listOf(
            RemoteSource(
                name = "blocklistproject-ads",
                url = "https://raw.githubusercontent.com/blocklistproject/Lists/master/ads.txt",
            ),
        )

        private val SKIP_PATTERNS = listOf(
            "#", "!", "[AdBlock", "[Mirror", "[Original",
            "title:", "last modified:", "expires:", "homepage:", "license:",
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val cacheDir by lazy {
        File(context.cacheDir, "adblock_remote").apply { mkdirs() }
    }

    fun loadIfNeeded() {
        val lastDownload = prefs.getLong(KEY_LAST_DOWNLOAD, 0L)
        val now = System.currentTimeMillis()
        val ageHours = (now - lastDownload) / 3_600_000L

        if (lastDownload > 0 && now - lastDownload < DOWNLOAD_INTERVAL_MS) {
            Log.d(TAG, "Remote filters cache fresh (age=${ageHours}h), loading from disk only")
            scope.launch { loadFromCache() }
            return
        }

        Log.d(TAG, "Remote filters stale (age=${ageHours}h), triggering lazy download")
        scope.launch {
            try {
                val totalAdded = downloadAndCache()
                if (totalAdded > 0) {
                    prefs.edit().putLong(KEY_LAST_DOWNLOAD, System.currentTimeMillis()).apply()
                    Log.d(TAG, "Remote filters downloaded OK: +$totalAdded hosts")
                } else {
                    Log.w(TAG, "Remote download returned 0 hosts, keeping old cache")
                    loadFromCache()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Remote download failed (offline?), falling back to cache: ${e.message}")
                loadFromCache()
            }
        }
    }

    private suspend fun downloadAndCache(): Int = withContext(Dispatchers.IO) {
        var totalAdded = 0
        val newlyLoadedHosts = mutableSetOf<String>()

        for (source in REMOTE_SOURCES) {
            try {
                val content = downloadRaw(source.url)
                val hosts = parseHosts(content)
                if (hosts.isNotEmpty()) {
                    File(cacheDir, "${source.name}.txt").writeText(content)
                    newlyLoadedHosts.addAll(hosts)
                    totalAdded += hosts.size
                    Log.d(TAG, "  ✓ ${source.name}: +${hosts.size} hosts")
                } else {
                    Log.w(TAG, "  ✗ ${source.name}: 0 hosts parsed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "  ✗ ${source.name} failed: ${e.message}")
            }
        }

        if (newlyLoadedHosts.isNotEmpty()) {
            hostBlocker.addBlockedHosts(newlyLoadedHosts)
        }
        totalAdded
    }

    private suspend fun loadFromCache() = withContext(Dispatchers.IO) {
        var totalAdded = 0
        for (source in REMOTE_SOURCES) {
            val cachedFile = File(cacheDir, "${source.name}.txt")
            if (!cachedFile.exists()) continue
            try {
                val hosts = parseHosts(cachedFile.readText())
                if (hosts.isNotEmpty()) {
                    hostBlocker.addBlockedHosts(hosts)
                    totalAdded += hosts.size
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cache ${source.name}: ${e.message}")
            }
        }
        if (totalAdded > 0) Log.d(TAG, "Loaded $totalAdded hosts from disk cache")
    }

    private fun downloadRaw(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "YueBrowser-AdBlock/1.0")
            instanceFollowRedirects = true
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHosts(content: String): Set<String> {
        val result = mutableSetOf<String>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (SKIP_PATTERNS.any { line.startsWith(it) }) continue

            if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                val host = line.substringAfter(' ').trim()
                if (isValidHost(host)) result.add(host.lowercase(Locale.US))
                continue
            }

            if (line.startsWith("||") && !line.contains("##") && !line.contains("$")) {
                val host = line.removePrefix("||").substringBefore('^').trim()
                if (!host.contains("/") && !host.contains(":") && isValidHost(host))
                    result.add(host.lowercase(Locale.US))
                continue
            }

            if (line.contains('.') && !line.contains(" ") && !line.contains("\t")
                && !line.contains("/") && !line.contains("=")) {
                if (isValidHost(line)) result.add(line.lowercase(Locale.US))
            }
        }
        return result
    }

    private fun isValidHost(host: String): Boolean {
        val h = host.trim()
        if (h.isEmpty() || h.length > 253) return false
        if (h.startsWith(".") || h.endsWith(".")) return false
        if (h.startsWith("-") || h.endsWith("-")) return false
        if (!h.contains(".")) return false
        if (h.contains("/") || h.contains("=") || h.contains("$") || h.contains("#")) return false
        val labels = h.split(".")
        if (labels.size < 2) return false
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return false
            if (!label.all { it.isLetterOrDigit() || it == '-' }) return false
        }
        val tld = labels.last()
        if (tld.all { it.isDigit() }) return false
        return true
    }

    data class RemoteSource(val name: String, val url: String)
}
