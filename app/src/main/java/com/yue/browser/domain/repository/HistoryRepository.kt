package com.yue.browser.domain.repository

import com.yue.browser.domain.model.HistoryItem
import kotlinx.coroutines.flow.StateFlow

interface HistoryRepository {
    val historyFlow: StateFlow<List<HistoryItem>>
    fun addHistory(url: String, title: String)
    fun removeHistory(url: String)
    fun clearHistory()
}
