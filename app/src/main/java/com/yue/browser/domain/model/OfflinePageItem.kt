package com.yue.browser.domain.model

data class OfflinePageItem(
    val id: String,
    val url: String,
    val title: String,
    val filePath: String,
    val savedAt: Long
)
