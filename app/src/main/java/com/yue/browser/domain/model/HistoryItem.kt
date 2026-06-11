package com.yue.browser.domain.model

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long,
    val visitCount: Int = 1
)
