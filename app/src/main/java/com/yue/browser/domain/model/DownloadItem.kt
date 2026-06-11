package com.yue.browser.domain.model

import kotlinx.coroutines.flow.StateFlow

data class DownloadItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val filePath: String,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val chunks: List<DownloadChunk> = emptyList(),
    val connectionCount: Int = 4 // Jumlah koneksi paralel (seperti IDM)
)

data class DownloadChunk(
    val id: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedByte: Long = 0,
    val status: ChunkStatus = ChunkStatus.PENDING
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

enum class ChunkStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}