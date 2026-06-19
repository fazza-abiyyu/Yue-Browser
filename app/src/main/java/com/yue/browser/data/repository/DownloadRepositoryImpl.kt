package com.yue.browser.data.repository

import com.yue.browser.data.notification.DownloadNotifier
import com.yue.browser.domain.model.ChunkStatus
import com.yue.browser.domain.model.DownloadChunk
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.DownloadStatus
import com.yue.browser.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DownloadRepositoryImpl : DownloadRepository {
    companion object {
        val instance = DownloadRepositoryImpl()
        private const val CHUNK_SIZE = 1024 * 1024 * 2 // 2MB per chunk
        private const val SAVE_INTERVAL_MS = 2000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 300L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1500L

        // Fallback User-Agent seperti Chrome desktop (jika WebView UA tidak tersedia)
        private const val FALLBACK_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    // In-memory storage: cookies + userAgent per downloadId (tidak disimpan ke disk)
    private val cookiesByDownload = ConcurrentHashMap<String, String>()
    private val userAgentByDownload = ConcurrentHashMap<String, String>()
    // Global user agent dari WebView (digunakan jika tidak ada per-download)
    @Volatile private var globalWebViewUserAgent: String? = null

    private var appContext: android.content.Context? = null
    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloadsFlow: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, MutableList<Job>>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val stateMutex = Mutex()

    private val lastSaveTime = AtomicLong(0)
    private val lastNotificationTime = ConcurrentHashMap<String, Long>()
    private var saveJob: Job? = null

    // Menerapkan headers yang membuat request terlihat berasal dari browser sungguhan.
    // Sekarang JUGA mengirim cookies dari sesi WebView (penyebab utama 403).
    private fun applyBrowserLikeHeaders(conn: HttpURLConnection, url: String, withRange: Boolean, downloadId: String? = null) {
        // 1. User-Agent: prioritaskan UA per-download, lalu global WebView UA, lalu fallback
        val ua = downloadId?.let { userAgentByDownload[it] } ?: globalWebViewUserAgent ?: FALLBACK_USER_AGENT
        conn.setRequestProperty("User-Agent", ua)

        // 2. Headers standar browser
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("Connection", "keep-alive")
        try {
            val urlObj = URL(url)
            val referer = "${urlObj.protocol}://${urlObj.host}/"
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Origin", referer.removeSuffix("/"))
        } catch (_: Exception) { }
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Pragma", "no-cache")
        conn.setRequestProperty("Sec-Fetch-Site", "same-site")
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate")
        conn.setRequestProperty("Sec-Fetch-User", "?1")
        conn.setRequestProperty("Sec-Fetch-Dest", "document")
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1")
        conn.setRequestProperty("DNT", "1")

        // 3. Cookies: ambil dari in-memory map, atau dari CookieManager WebView
        val cookieHeader = downloadId?.let { cookiesByDownload[it] } ?: run {
            // Fallback: ambil cookies langsung dari CookieManager WebView untuk URL ini
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.getCookie(url)
            } catch (_: Exception) { null }
        }
        if (!cookieHeader.isNullOrEmpty()) {
            conn.setRequestProperty("Cookie", cookieHeader)
        }

        conn.instanceFollowRedirects = true
        conn.useCaches = false

        if (!withRange) {
            conn.setRequestProperty("Range", null as String?)
        }
    }

    // Set global WebView user agent (dipanggil dari SystemWebViewSession)
    fun setGlobalWebViewUserAgent(ua: String) {
        globalWebViewUserAgent = ua
    }

    fun initialize(context: android.content.Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (sharedPreferences != null) return
        sharedPreferences = appCtx.getSharedPreferences("yue_browser_downloads", android.content.Context.MODE_PRIVATE)
        DownloadNotifier.initialize(appCtx)
        loadDownloads()
    }

    private fun loadDownloads() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("download_items", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<DownloadItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val chunksJson = obj.optJSONArray("chunks") ?: org.json.JSONArray()
                val chunks = mutableListOf<DownloadChunk>()
                for (j in 0 until chunksJson.length()) {
                    val chunkObj = chunksJson.getJSONObject(j)
                    chunks.add(
                        DownloadChunk(
                            id = chunkObj.getInt("id"),
                            startByte = chunkObj.getLong("startByte"),
                            endByte = chunkObj.getLong("endByte"),
                            downloadedByte = chunkObj.getLong("downloadedByte"),
                            status = ChunkStatus.valueOf(chunkObj.getString("status"))
                        )
                    )
                }
                list.add(
                    DownloadItem(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        fileName = obj.getString("fileName"),
                        filePath = obj.getString("filePath"),
                        totalSize = obj.getLong("totalSize"),
                        downloadedSize = obj.getLong("downloadedSize"),
                        status = DownloadStatus.valueOf(obj.getString("status")),
                        progress = obj.getInt("progress"),
                        lastModified = obj.getLong("lastModified"),
                        chunks = chunks,
                        connectionCount = obj.optInt("connectionCount", 4)
                    )
                )
            }
            _downloads.value = list
        } catch (e: Exception) {
            _downloads.value = emptyList()
        }
    }

    private suspend fun saveDownloads(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveTime.get() < SAVE_INTERVAL_MS) {
            if (saveJob == null || saveJob?.isActive == false) {
                saveJob = coroutineScope.launch {
                    delay(SAVE_INTERVAL_MS)
                    saveDownloadsInternal()
                }
            }
            return
        }
        saveDownloadsInternal()
    }

    private suspend fun saveDownloadsInternal() {
        val prefs = sharedPreferences ?: return
        lastSaveTime.set(System.currentTimeMillis())
        val jsonArray = org.json.JSONArray()
        stateMutex.withLock {
            _downloads.value.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("id", item.id)
                obj.put("url", item.url)
                obj.put("fileName", item.fileName)
                obj.put("filePath", item.filePath)
                obj.put("totalSize", item.totalSize)
                obj.put("downloadedSize", item.downloadedSize)
                obj.put("status", item.status.name)
                obj.put("progress", item.progress)
                obj.put("lastModified", item.lastModified)
                obj.put("connectionCount", item.connectionCount)

                val chunksArray = org.json.JSONArray()
                item.chunks.forEach { chunk ->
                    val chunkObj = org.json.JSONObject()
                    chunkObj.put("id", chunk.id)
                    chunkObj.put("startByte", chunk.startByte)
                    chunkObj.put("endByte", chunk.endByte)
                    chunkObj.put("downloadedByte", chunk.downloadedByte)
                    chunkObj.put("status", chunk.status.name)
                    chunksArray.put(chunkObj)
                }
                obj.put("chunks", chunksArray)
                jsonArray.put(obj)
            }
        }
        prefs.edit().putString("download_items", jsonArray.toString()).apply()
    }

    override fun startDownload(url: String, fileName: String, context: android.content.Context, connectionCount: Int, cookies: String?, webViewUserAgent: String?) {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val filePath = "${downloadDir.absolutePath}/$fileName"

        // Generate downloadId dulu agar bisa dipakai untuk simpan cookies/UA
        val tempId = java.util.UUID.randomUUID().toString()

        // Simpan cookies & userAgent in-memory (tidak persist ke disk)
        if (!cookies.isNullOrEmpty()) {
            cookiesByDownload[tempId] = cookies
        }
        if (!webViewUserAgent.isNullOrEmpty()) {
            userAgentByDownload[tempId] = webViewUserAgent
            // Set juga sebagai global fallback
            if (globalWebViewUserAgent == null) {
                globalWebViewUserAgent = webViewUserAgent
            }
        }

        val existingDownload = _downloads.value.find { it.url == url }
        if (existingDownload != null) {
            if (existingDownload.status == DownloadStatus.DOWNLOADING) return
            if (existingDownload.status == DownloadStatus.PAUSED) {
                resumeDownload(existingDownload.id, context)
                return
            }
        }

        // === DETEKSI 403 AWAL: coba probe URL dulu. Jika 403, fallback ke Android DownloadManager ===
        val probeResult = probeUrlWithCookies(url, tempId)
        if (probeResult == 403 || probeResult == 401) {
            // Server memblokir direct connection — pakai Android DownloadManager
            // yang otomatis pakai cookies WebView dan bekerja dengan baik
            startAndroidDownloadManager(url, fileName, context, cookies, webViewUserAgent)
            return
        }

        val sizeInfo = getFileSizeAndRangeSupport(url, tempId)
        val fileSize = sizeInfo.first
        val supportsRange = sizeInfo.second

        if (fileSize <= 0 || !supportsRange) {
            startSingleConnectionDownload(url, fileName, filePath, context, tempId)
            return
        }

        val chunks = createChunks(fileSize, connectionCount)

        val downloadItem = DownloadItem(
            id = tempId,
            url = url,
            fileName = fileName,
            filePath = filePath,
            totalSize = fileSize,
            status = DownloadStatus.PENDING,
            chunks = chunks,
            connectionCount = connectionCount
        )

        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value + downloadItem
            }
            saveDownloads(force = true)
            performMultiPartDownload(downloadItem.id, url, filePath, context)
        }
    }

    // Probe URL dengan cookies, return response code
    private fun probeUrlWithCookies(url: String, downloadId: String): Int {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            applyBrowserLikeHeaders(connection, url, withRange = false, downloadId = downloadId)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            connection.responseCode
        } catch (e: Exception) {
            -1
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    // FALLBACK: pakai Android DownloadManager sistem (paling kompatibel dengan server yang butuh auth/cookie)
    private fun startAndroidDownloadManager(url: String, fileName: String, context: android.content.Context, cookies: String?, webViewUserAgent: String?) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            request.setTitle(fileName)
            request.setDescription(fileName)
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)

            // Set headers — sama seperti browser, plus cookies
            request.addRequestHeader("Accept", "*/*")
            request.addRequestHeader("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            val ua = webViewUserAgent ?: globalWebViewUserAgent ?: FALLBACK_USER_AGENT
            request.addRequestHeader("User-Agent", ua)
            try {
                val urlObj = URL(url)
                val referer = "${urlObj.protocol}://${urlObj.host}/"
                request.addRequestHeader("Referer", referer)
                request.addRequestHeader("Origin", referer.removeSuffix("/"))
            } catch (_: Exception) { }

            // Cookies: gunakan yang disediakan, atau ambil dari CookieManager
            val finalCookies = cookies ?: try {
                android.webkit.CookieManager.getInstance().getCookie(url)
            } catch (_: Exception) { null }
            if (!finalCookies.isNullOrEmpty()) {
                request.addRequestHeader("Cookie", finalCookies)
            }

            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            android.widget.Toast.makeText(context, "Mendownload via sistem: $fileName", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileSizeAndRangeSupport(url: String, downloadId: String? = null): Pair<Long, Boolean> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            applyBrowserLikeHeaders(connection, url, withRange = false, downloadId = downloadId)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            val responseCode = connection.responseCode
            var contentLength = connection.contentLengthLong

            // Jika HEAD ditolak (403/404/405), coba pakai GET
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 405 || responseCode >= 400) {
                connection.disconnect()
                val fallbackConn = URL(url).openConnection() as HttpURLConnection
                connection = fallbackConn
                fallbackConn.requestMethod = "GET"
                applyBrowserLikeHeaders(fallbackConn, url, withRange = false, downloadId = downloadId)
                fallbackConn.connectTimeout = 10000
                fallbackConn.readTimeout = 10000
                fallbackConn.connect()

                val fallbackCode = fallbackConn.responseCode
                contentLength = fallbackConn.contentLengthLong
                if (fallbackCode != HttpURLConnection.HTTP_OK && fallbackCode != HttpURLConnection.HTTP_PARTIAL) {
                    return Pair(-1, false)
                }
            }

            val acceptRanges = connection.getHeaderField("Accept-Ranges")
            val supportsRangeByHeader = acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)

            var supportsRange = supportsRangeByHeader
            if (!supportsRange && contentLength > 0) {
                connection.disconnect()
                try {
                    val testConnection = URL(url).openConnection() as HttpURLConnection
                    testConnection.requestMethod = "GET"
                    applyBrowserLikeHeaders(testConnection, url, withRange = true, downloadId = downloadId)
                    testConnection.setRequestProperty("Range", "bytes=0-0")
                    testConnection.connectTimeout = 5000
                    testConnection.readTimeout = 5000
                    testConnection.connect()

                    val testCode = testConnection.responseCode
                    val contentRange = testConnection.getHeaderField("Content-Range")
                    supportsRange = (testCode == HttpURLConnection.HTTP_PARTIAL) || (contentRange != null && contentRange.contains("bytes", ignoreCase = true))
                    testConnection.disconnect()
                } catch (_: Exception) {
                    supportsRange = false
                }
            }

            Pair(contentLength, supportsRange)
        } catch (e: Exception) {
            Pair(-1, false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun createChunks(totalSize: Long, connectionCount: Int): List<DownloadChunk> {
        val chunks = mutableListOf<DownloadChunk>()
        val chunkCount = Math.min(connectionCount, Math.ceil(totalSize.toDouble() / CHUNK_SIZE).toInt()).coerceAtLeast(1)
        val actualChunkSize = totalSize / chunkCount

        for (i in 0 until chunkCount) {
            val startByte = i * actualChunkSize
            val endByte = if (i == chunkCount - 1) totalSize - 1 else startByte + actualChunkSize - 1
            chunks.add(
                DownloadChunk(
                    id = i,
                    startByte = startByte,
                    endByte = endByte
                )
            )
        }
        return chunks
    }

    private fun startSingleConnectionDownload(url: String, fileName: String, filePath: String, context: android.content.Context, downloadId: String) {
        val downloadItem = DownloadItem(
            id = downloadId,
            url = url,
            fileName = fileName,
            filePath = filePath,
            status = DownloadStatus.PENDING,
            connectionCount = 1
        )

        coroutineScope.launch {
            stateMutex.withLock {
                if (!_downloads.value.any { it.id == downloadId }) {
                    _downloads.value = _downloads.value + downloadItem
                }
            }
            saveDownloads(force = true)

            var connection: HttpURLConnection? = null
            var outputStream: RandomAccessFile? = null
            var inputStream: java.io.InputStream? = null

            try {
                val file = File(filePath)
                val downloadedBytes = if (file.exists()) file.length() else 0L

                updateDownloadStatus(downloadItem.id, DownloadStatus.DOWNLOADING)

                val urlObj = URL(url)
                connection = urlObj.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val wantResume = downloadedBytes > 0
                applyBrowserLikeHeaders(connection, url, withRange = wantResume, downloadId = downloadItem.id)
                if (wantResume) {
                    connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                }

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 401) {
                    // Masih 403 juga — fallback ke Android DownloadManager
                    updateDownloadStatus(downloadItem.id, DownloadStatus.FAILED)
                    startAndroidDownloadManager(url, fileName, context, cookiesByDownload[downloadItem.id], userAgentByDownload[downloadItem.id])
                    return@launch
                }
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    val totalSize = if (downloadedBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        connection.contentLengthLong + downloadedBytes
                    } else {
                        connection.contentLengthLong
                    }

                    updateDownloadTotalSize(downloadItem.id, totalSize)
                    updateDownloadProgress(downloadItem.id, downloadedBytes, totalSize)

                    outputStream = RandomAccessFile(file, "rw")
                    outputStream.seek(downloadedBytes)

                    inputStream = connection.inputStream
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = downloadedBytes
                    var lastProgressUpdate = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isDownloadPaused(downloadItem.id)) {
                            updateDownloadStatus(downloadItem.id, DownloadStatus.PAUSED)
                            return@launch
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL_MS) {
                            updateDownloadProgress(downloadItem.id, totalRead, totalSize)
                            lastProgressUpdate = now
                        }
                    }

                    updateDownloadStatus(downloadItem.id, DownloadStatus.COMPLETED)
                    updateDownloadProgress(downloadItem.id, totalSize, totalSize)
                    saveDownloads(force = true)

                    val finalDownload = stateMutex.withLock { _downloads.value.find { it.id == downloadItem.id } }
                    if (finalDownload != null) {
                        appContext?.let { DownloadNotifier.showCompleted(it, finalDownload) }
                    }
                    android.widget.Toast.makeText(context, "Download selesai: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    updateDownloadStatus(downloadItem.id, DownloadStatus.FAILED)
                    saveDownloads(force = true)

                    val failedDownload = stateMutex.withLock { _downloads.value.find { it.id == downloadItem.id } }
                    if (failedDownload != null) {
                        appContext?.let { DownloadNotifier.showFailed(it, failedDownload, "Kesalahan server: $responseCode") }
                    }
                }
            } catch (e: Exception) {
                updateDownloadStatus(downloadItem.id, DownloadStatus.FAILED)
                saveDownloads(force = true)

                val failedDownload = stateMutex.withLock { _downloads.value.find { it.id == downloadItem.id } }
                if (failedDownload != null) {
                    appContext?.let { DownloadNotifier.showFailed(it, failedDownload, e.message ?: "Gagal mengunduh") }
                }
            } finally {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
                downloadJobs.remove(downloadItem.id)
            }
        }
    }

    override fun rebuildChunksAndResume(id: String, newConnectionCount: Int, context: android.content.Context) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)

        coroutineScope.launch {
            val download = stateMutex.withLock { _downloads.value.find { it.id == id } } ?: return@launch
            if (download.totalSize <= 0) return@launch

            // Cek apakah file sudah ada sebagian data
            val file = File(download.filePath)
            val existingBytes = if (file.exists()) file.length() else 0L

            // Buat chunks baru dengan connection count baru
            val newChunks = createChunks(download.totalSize, newConnectionCount)

            // Update state dengan chunks baru
            stateMutex.withLock {
                _downloads.value = _downloads.value.map {
                    if (it.id == id) {
                        it.copy(
                            chunks = newChunks,
                            downloadedSize = 0L,
                            progress = 0,
                            status = DownloadStatus.PENDING,
                            connectionCount = newConnectionCount,
                            lastModified = System.currentTimeMillis()
                        )
                    } else {
                        it
                    }
                }
            }
            saveDownloads(force = true)

            // Hapus file existing agar mulai fresh
            if (file.exists() && existingBytes < download.totalSize) {
                file.delete()
            }

            performMultiPartDownload(id, download.url, download.filePath, context)
        }
    }

    private suspend fun performMultiPartDownload(id: String, url: String, filePath: String, context: android.content.Context) {
        updateDownloadStatus(id, DownloadStatus.DOWNLOADING)
        saveDownloads()

        val jobs = mutableListOf<Job>()
        val currentDownload = stateMutex.withLock {
            _downloads.value.find { it.id == id }
        } ?: return

        currentDownload.chunks
            .filter { it.status == ChunkStatus.PENDING || it.status == ChunkStatus.FAILED }
            .forEach { chunk ->
                val job = coroutineScope.launch {
                    downloadChunk(id, chunk, url, filePath, context)
                }
                jobs.add(job)
            }

        downloadJobs[id] = jobs
    }

    private suspend fun downloadChunk(downloadId: String, chunk: DownloadChunk, url: String, filePath: String, context: android.content.Context) {
        var connection: HttpURLConnection? = null
        var outputStream: RandomAccessFile? = null
        var inputStream: java.io.InputStream? = null

        try {
            updateChunkStatus(downloadId, chunk.id, ChunkStatus.DOWNLOADING)

            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            applyBrowserLikeHeaders(connection, url, withRange = true, downloadId = downloadId)
            connection.setRequestProperty("Range", "bytes=${chunk.startByte + chunk.downloadedByte}-${chunk.endByte}")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_PARTIAL || responseCode == HttpURLConnection.HTTP_OK) {
                val file = File(filePath)
                outputStream = RandomAccessFile(file, "rw")
                outputStream.seek(chunk.startByte + chunk.downloadedByte)

                inputStream = connection.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = chunk.downloadedByte
                var lastProgressUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isDownloadPaused(downloadId)) {
                        updateChunkStatus(downloadId, chunk.id, ChunkStatus.PENDING)
                        return
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL_MS) {
                        updateChunkProgress(downloadId, chunk.id, totalRead)
                        lastProgressUpdate = now
                    }
                }

                updateChunkStatus(downloadId, chunk.id, ChunkStatus.COMPLETED)
                checkDownloadComplete(downloadId, context)
            } else {
                updateChunkStatus(downloadId, chunk.id, ChunkStatus.FAILED)
                checkDownloadFailed(downloadId, "Gagal mengunduh bagian file")
            }
        } catch (e: Exception) {
            updateChunkStatus(downloadId, chunk.id, ChunkStatus.FAILED)
            checkDownloadFailed(downloadId, e.message ?: "Gagal mengunduh")
        } finally {
            inputStream?.close()
            outputStream?.close()
            connection?.disconnect()
        }
    }

    private suspend fun checkDownloadFailed(id: String, errorMsg: String) {
        val download = stateMutex.withLock { _downloads.value.find { it.id == id } } ?: return
        val hasActiveChunks = download.chunks.any { it.status == ChunkStatus.DOWNLOADING || it.status == ChunkStatus.PENDING || it.status == ChunkStatus.COMPLETED }
        if (!hasActiveChunks && download.chunks.isNotEmpty()) {
            // Semua chunk gagal, atau hanya gagal dan tidak ada yang sedang/pending
            val allFailedOrEmpty = download.chunks.all { it.status == ChunkStatus.FAILED }
            if (allFailedOrEmpty) {
                updateDownloadStatus(id, DownloadStatus.FAILED)
                saveDownloads(force = true)
                appContext?.let {
                    DownloadNotifier.showFailed(it, download, errorMsg)
                }
            }
        }
    }

    private suspend fun checkDownloadComplete(id: String, context: android.content.Context) {
        val download = stateMutex.withLock {
            _downloads.value.find { it.id == id }
        } ?: return

        if (download.chunks.all { it.status == ChunkStatus.COMPLETED }) {
            updateDownloadStatus(id, DownloadStatus.COMPLETED)
            updateDownloadProgress(id, download.totalSize, download.totalSize)
            saveDownloads(force = true)

            // Post completed notification
            val finalDownload = stateMutex.withLock { _downloads.value.find { it.id == id } }
            if (finalDownload != null) {
                appContext?.let { DownloadNotifier.showCompleted(it, finalDownload) }
            }
            android.widget.Toast.makeText(context, "Download selesai: ${download.fileName}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDownloadPaused(id: String): Boolean {
        return _downloads.value.find { it.id == id }?.status == DownloadStatus.PAUSED
    }

    override fun pauseDownload(id: String) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)
        coroutineScope.launch {
            updateDownloadStatus(id, DownloadStatus.PAUSED)

            stateMutex.withLock {
                _downloads.value = _downloads.value.map {
                    if (it.id == id) {
                        it.copy(chunks = it.chunks.map { chunk ->
                            if (chunk.status == ChunkStatus.DOWNLOADING) {
                                chunk.copy(status = ChunkStatus.PENDING)
                            } else {
                                chunk
                            }
                        }, lastModified = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }
            saveDownloads(force = true)

            // Post paused notification
            stateMutex.withLock { _downloads.value.find { it.id == id } }?.let { download ->
                appContext?.let { DownloadNotifier.showPaused(it, download) }
            }
        }
    }

    override fun resumeDownload(id: String, context: android.content.Context) {
        val download = _downloads.value.find { it.id == id } ?: return
        if (download.status == DownloadStatus.COMPLETED) return

        coroutineScope.launch {
            updateDownloadStatus(id, DownloadStatus.PENDING)
            saveDownloads()

            if (download.chunks.isEmpty()) {
                val job = coroutineScope.launch {
                    startSingleConnectionDownload(download.url, download.fileName, download.filePath, context, id)
                }
                downloadJobs[id] = mutableListOf(job)
            } else {
                performMultiPartDownload(id, download.url, download.filePath, context)
            }
        }
    }

    override fun cancelDownload(id: String) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)
        val download = _downloads.value.find { it.id == id } ?: return

        val file = File(download.filePath)
        if (file.exists()) {
            file.delete()
        }

        // Cancel notification
        appContext?.let { DownloadNotifier.cancel(it, id) }

        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value.filter { it.id != id }
            }
            saveDownloads(force = true)
        }
    }

    override fun removeDownload(id: String) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)
        val download = _downloads.value.find { it.id == id } ?: return

        val file = File(download.filePath)
        if (file.exists()) {
            file.delete()
        }

        // Cancel notification
        appContext?.let { DownloadNotifier.cancel(it, id) }

        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value.filter { it.id != id }
            }
            saveDownloads(force = true)
        }
    }

    override fun replaceUrlAndResume(id: String, newUrl: String, context: android.content.Context) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)

        val oldDownload = _downloads.value.find { it.id == id } ?: return
        val sizeInfo = getFileSizeAndRangeSupport(newUrl, id)
        val fileSize = sizeInfo.first
        val supportsRange = sizeInfo.second

        val newChunks = if (fileSize > 0 && supportsRange) {
            createChunks(fileSize, oldDownload.connectionCount)
        } else {
            emptyList()
        }

        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value.map {
                    if (it.id == id) {
                        it.copy(
                            url = newUrl,
                            totalSize = fileSize,
                            downloadedSize = 0,
                            progress = 0,
                            status = DownloadStatus.PENDING,
                            chunks = newChunks,
                            lastModified = System.currentTimeMillis()
                        )
                    } else {
                        it
                    }
                }
            }
            saveDownloads(force = true)

            val download = stateMutex.withLock { _downloads.value.find { it.id == id } } ?: return@launch
            if (download.chunks.isEmpty()) {
                startSingleConnectionDownload(newUrl, download.fileName, download.filePath, context, id)
            } else {
                performMultiPartDownload(id, newUrl, download.filePath, context)
            }
        }
    }

    override fun rewriteFile(id: String, context: android.content.Context) {
        downloadJobs[id]?.forEach { it.cancel() }
        downloadJobs.remove(id)

        val download = _downloads.value.find { it.id == id } ?: return

        val file = File(download.filePath)
        if (file.exists()) {
            file.delete()
        }

        val sizeInfo = getFileSizeAndRangeSupport(download.url, id)
        val fileSize = sizeInfo.first
        val supportsRange = sizeInfo.second

        val newChunks = if (fileSize > 0 && supportsRange) {
            createChunks(fileSize, download.connectionCount)
        } else {
            emptyList()
        }

        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value.map {
                    if (it.id == id) {
                        it.copy(
                            totalSize = fileSize,
                            downloadedSize = 0,
                            progress = 0,
                            status = DownloadStatus.PENDING,
                            chunks = newChunks,
                            lastModified = System.currentTimeMillis()
                        )
                    } else {
                        it
                    }
                }
            }
            saveDownloads(force = true)

            if (newChunks.isEmpty()) {
                startSingleConnectionDownload(download.url, download.fileName, download.filePath, context, id)
            } else {
                performMultiPartDownload(id, download.url, download.filePath, context)
            }
        }
    }

    override fun setConnectionCount(id: String, count: Int) {
        coroutineScope.launch {
            stateMutex.withLock {
                _downloads.value = _downloads.value.map {
                    if (it.id == id) {
                        it.copy(connectionCount = count.coerceIn(1, 16), lastModified = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }
            saveDownloads(force = true)
        }
    }

    private suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        stateMutex.withLock {
            _downloads.value = _downloads.value.map {
                if (it.id == id) it.copy(status = status, lastModified = System.currentTimeMillis()) else it
            }
        }
        saveDownloads()
    }

    private suspend fun updateDownloadTotalSize(id: String, totalSize: Long) {
        stateMutex.withLock {
            _downloads.value = _downloads.value.map {
                if (it.id == id) it.copy(totalSize = totalSize, lastModified = System.currentTimeMillis()) else it
            }
        }
        saveDownloads()
    }

    private suspend fun updateDownloadProgress(id: String, downloaded: Long, total: Long) {
        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        stateMutex.withLock {
            _downloads.value = _downloads.value.map {
                if (it.id == id) {
                    it.copy(downloadedSize = downloaded, progress = progress, lastModified = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        saveDownloads()
        postProgressNotificationIfNeeded(id)
    }

    private suspend fun updateChunkStatus(id: String, chunkId: Int, status: ChunkStatus) {
        stateMutex.withLock {
            _downloads.value = _downloads.value.map { download ->
                if (download.id == id) {
                    download.copy(chunks = download.chunks.map { chunk ->
                        if (chunk.id == chunkId) chunk.copy(status = status) else chunk
                    }, lastModified = System.currentTimeMillis())
                } else {
                    download
                }
            }
        }
        saveDownloads()
        if (status == ChunkStatus.FAILED) {
            // Check if all chunks failed, but don't show failed notification per-chunk
            // We'll show failed only if the entire download is failed
        }
    }

    private suspend fun updateChunkProgress(id: String, chunkId: Int, downloadedByte: Long) {
        stateMutex.withLock {
            _downloads.value = _downloads.value.map { download ->
                if (download.id == id) {
                    val updatedChunks = download.chunks.map { chunk ->
                        if (chunk.id == chunkId) chunk.copy(downloadedByte = downloadedByte) else chunk
                    }
                    val totalDownloaded = updatedChunks.sumOf { it.downloadedByte }
                    val progress = if (download.totalSize > 0) ((totalDownloaded * 100) / download.totalSize).toInt() else 0
                    download.copy(
                        chunks = updatedChunks,
                        downloadedSize = totalDownloaded,
                        progress = progress,
                        lastModified = System.currentTimeMillis()
                    )
                } else {
                    download
                }
            }
        }
        saveDownloads()
        postProgressNotificationIfNeeded(id)
    }

    private suspend fun postProgressNotificationIfNeeded(id: String) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        val last = lastNotificationTime.putIfAbsent(id, 0L) ?: 0L
        if (now - last < NOTIFICATION_UPDATE_INTERVAL_MS) return

        // Only the first coroutine within the interval posts the notification
        if (!lastNotificationTime.replace(id, last, now)) {
            // Lost CAS race, skip
            return
        }

        val download = stateMutex.withLock { _downloads.value.find { it.id == id } } ?: return
        if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED) {
            DownloadNotifier.updateProgress(ctx, download)
        }
    }
}
