package com.yue.browser.domain.repository

import com.yue.browser.domain.model.BookmarkItem
import kotlinx.coroutines.flow.StateFlow

interface BookmarkRepository {
    val bookmarksFlow: StateFlow<List<BookmarkItem>>
    fun addBookmark(url: String, title: String)
    fun removeBookmark(url: String)
    fun isBookmarked(url: String): Boolean
}
