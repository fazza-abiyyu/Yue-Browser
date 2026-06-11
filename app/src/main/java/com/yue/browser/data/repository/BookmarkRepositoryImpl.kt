package com.yue.browser.data.repository

import com.yue.browser.domain.model.BookmarkItem
import com.yue.browser.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BookmarkRepositoryImpl : BookmarkRepository {
    companion object {
        val instance = BookmarkRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    override val bookmarksFlow: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (sharedPreferences != null) return
        sharedPreferences = context.getSharedPreferences("yue_browser_bookmarks", android.content.Context.MODE_PRIVATE)
        loadBookmarks()
    }

    private fun loadBookmarks() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("bookmark_items", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<BookmarkItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BookmarkItem(
                        url = obj.getString("url"),
                        title = obj.getString("title")
                    )
                )
            }
            _bookmarks.value = list
        } catch (e: Exception) {
            _bookmarks.value = emptyList()
        }
    }

    private fun saveBookmarks() {
        val prefs = sharedPreferences ?: return
        val jsonArray = org.json.JSONArray()
        _bookmarks.value.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("url", item.url)
            obj.put("title", item.title)
            jsonArray.put(obj)
        }
        prefs.edit().putString("bookmark_items", jsonArray.toString()).apply()
    }

    override fun addBookmark(url: String, title: String) {
        val trimmedUrl = url.trim()
        val trimmedTitle = if (title.isBlank()) trimmedUrl else title.trim()
        if (trimmedUrl.isBlank() || trimmedUrl == "yue://newtab" || trimmedUrl == "about:blank") return

        // Check if already exists, update title if so
        val currentList = _bookmarks.value.filter { it.url != trimmedUrl }.toMutableList()
        currentList.add(BookmarkItem(trimmedUrl, trimmedTitle))
        _bookmarks.value = currentList
        saveBookmarks()
    }

    override fun removeBookmark(url: String) {
        _bookmarks.value = _bookmarks.value.filter { it.url != url }
        saveBookmarks()
    }

    override fun isBookmarked(url: String): Boolean {
        return _bookmarks.value.any { it.url == url }
    }
}
