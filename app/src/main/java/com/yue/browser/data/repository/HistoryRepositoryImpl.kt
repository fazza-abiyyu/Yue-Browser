package com.yue.browser.data.repository

import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryRepositoryImpl : HistoryRepository {
    companion object {
        val instance = HistoryRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    override val historyFlow: StateFlow<List<HistoryItem>> = _history.asStateFlow()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url.isNotEmpty()) {
                        list.add(
                            HistoryItem(
                                url = url,
                                title = obj.optString("title", url),
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                visitCount = obj.optInt("visitCount", 1)
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HistoryRepository", "Error parsing history item at index $i", e)
                }
            }
            _history.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    private fun saveHistory() {
        val prefs = sharedPreferences ?: return
        try {
            val jsonArray = org.json.JSONArray()
            _history.value.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("url", item.url)
                obj.put("title", item.title)
                obj.put("timestamp", item.timestamp)
                obj.put("visitCount", item.visitCount)
                jsonArray.put(obj)
            }
            prefs.edit().putString("history_items", jsonArray.toString()).commit()
        } catch (e: Exception) {
            android.util.Log.e("HistoryRepository", "Error saving history", e)
        }
    }

    override fun addHistory(url: String, title: String) {
        val trimmedUrl = url.trim()
        val trimmedTitle = if (title.isBlank()) trimmedUrl else title.trim()
        if (trimmedUrl.isBlank() || trimmedUrl == "yue://newtab" || trimmedUrl == "about:blank") return

        val existingItem = _history.value.find { it.url == trimmedUrl }
        val newVisitCount = (existingItem?.visitCount ?: 0) + 1

        val currentList = _history.value.filter { it.url != trimmedUrl }.toMutableList()
        currentList.add(HistoryItem(trimmedUrl, trimmedTitle, System.currentTimeMillis(), newVisitCount))
        
        _history.value = currentList.sortedByDescending { it.timestamp }.take(500)
        coroutineScope.launch {
            saveHistory()
        }
    }

    override fun removeHistory(url: String) {
        _history.value = _history.value.filter { it.url != url }
        coroutineScope.launch {
            saveHistory()
        }
    }

    override fun clearHistory() {
        _history.value = emptyList()
        coroutineScope.launch {
            saveHistory()
        }
    }
}
