package com.yue.browser.data.repository

import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryRepositoryImpl : HistoryRepository {
    companion object {
        val instance = HistoryRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    override val historyFlow: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (sharedPreferences != null) return
        sharedPreferences = context.getSharedPreferences("yue_browser_history", android.content.Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("history_items", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<HistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    HistoryItem(
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        timestamp = obj.getLong("timestamp"),
                        visitCount = obj.optInt("visitCount", 1)
                    )
                )
            }
            // Sort by newest first
            _history.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    private fun saveHistory() {
        val prefs = sharedPreferences ?: return
        val jsonArray = org.json.JSONArray()
        _history.value.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("url", item.url)
            obj.put("title", item.title)
            obj.put("timestamp", item.timestamp)
            obj.put("visitCount", item.visitCount)
            jsonArray.put(obj)
        }
        prefs.edit().putString("history_items", jsonArray.toString()).apply()
    }

    override fun addHistory(url: String, title: String) {
        val trimmedUrl = url.trim()
        val trimmedTitle = if (title.isBlank()) trimmedUrl else title.trim()
        if (trimmedUrl.isBlank() || trimmedUrl == "yue://newtab" || trimmedUrl == "about:blank") return

        // Increment visit count if it already exists in history
        val existingItem = _history.value.find { it.url == trimmedUrl }
        val newVisitCount = (existingItem?.visitCount ?: 0) + 1

        val currentList = _history.value.filter { it.url != trimmedUrl }.toMutableList()
        currentList.add(HistoryItem(trimmedUrl, trimmedTitle, System.currentTimeMillis(), newVisitCount))
        
        // Limit to 500 items to avoid bloating
        _history.value = currentList.sortedByDescending { it.timestamp }.take(500)
        saveHistory()
    }

    override fun removeHistory(url: String) {
        _history.value = _history.value.filter { it.url != url }
        saveHistory()
    }

    override fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }
}
