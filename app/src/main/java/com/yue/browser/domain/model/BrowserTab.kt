package com.yue.browser.domain.model

import com.yue.browser.domain.engine.BrowserSession

data class BrowserTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "https://www.google.com",
    val title: String = "New Tab",
    val session: BrowserSession,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
    val isPrivate: Boolean = false,
    val thumbnail: android.graphics.Bitmap? = null,
    val favicon: android.graphics.Bitmap? = null,
    val lastAccessed: Long = System.currentTimeMillis(),
    val groupId: String? = null,
    val isTranslated: Boolean = false,
    val translationSource: String = "auto",
    val translationTarget: String = "id",
    val translatedDomain: String? = null
)

