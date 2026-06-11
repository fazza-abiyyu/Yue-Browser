package com.yue.browser.domain.repository

import com.yue.browser.domain.model.DownloadItem
import kotlinx.coroutines.flow.StateFlow

interface DownloadRepository {
    val downloadsFlow: StateFlow<List<DownloadItem>>
    fun startDownload(url: String, fileName: String, context: android.content.Context, connectionCount: Int = 4, cookies: String? = null, webViewUserAgent: String? = null)
    fun pauseDownload(id: String)
    fun resumeDownload(id: String, context: android.content.Context)
    fun cancelDownload(id: String)
    fun removeDownload(id: String)
    fun replaceUrlAndResume(id: String, newUrl: String, context: android.content.Context)
    fun rewriteFile(id: String, context: android.content.Context)
    fun setConnectionCount(id: String, count: Int)
    fun rebuildChunksAndResume(id: String, newConnectionCount: Int, context: android.content.Context)
}