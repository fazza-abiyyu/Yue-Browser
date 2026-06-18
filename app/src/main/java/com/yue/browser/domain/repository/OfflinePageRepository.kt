package com.yue.browser.domain.repository

import com.yue.browser.domain.model.OfflinePageItem
import kotlinx.coroutines.flow.StateFlow

interface OfflinePageRepository {
    val offlinePagesFlow: StateFlow<List<OfflinePageItem>>
    fun addOfflinePage(url: String, title: String, filePath: String): OfflinePageItem
    fun removeOfflinePage(id: String)
    fun isSavedOffline(url: String): Boolean
}
