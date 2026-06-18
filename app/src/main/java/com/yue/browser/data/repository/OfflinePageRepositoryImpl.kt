package com.yue.browser.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.yue.browser.domain.model.OfflinePageItem
import com.yue.browser.domain.repository.OfflinePageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class OfflinePageRepositoryImpl : OfflinePageRepository {
    companion object {
        val instance = OfflinePageRepositoryImpl()
    }

    private var sharedPreferences: SharedPreferences? = null
    private val _offlinePages = MutableStateFlow<List<OfflinePageItem>>(emptyList())
    override val offlinePagesFlow: StateFlow<List<OfflinePageItem>> = _offlinePages.asStateFlow()

    fun initialize(context: Context) {
        if (sharedPreferences != null) return
        sharedPreferences = context.getSharedPreferences("yue_browser_offline_pages", Context.MODE_PRIVATE)
        loadOfflinePages()
    }

    private fun loadOfflinePages() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("offline_items", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(json)
            val list = mutableListOf<OfflinePageItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    OfflinePageItem(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        filePath = obj.getString("filePath"),
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                )
            }
            _offlinePages.value = list.sortedByDescending { it.savedAt }
        } catch (e: Exception) {
            _offlinePages.value = emptyList()
        }
    }

    private fun saveOfflinePages() {
        val prefs = sharedPreferences ?: return
        val jsonArray = JSONArray()
        _offlinePages.value.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("url", item.url)
            obj.put("title", item.title)
            obj.put("filePath", item.filePath)
            obj.put("savedAt", item.savedAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString("offline_items", jsonArray.toString()).apply()
    }

    override fun addOfflinePage(url: String, title: String, filePath: String): OfflinePageItem {
        val trimmedUrl = url.trim()
        val trimmedTitle = if (title.isBlank()) trimmedUrl else title.trim()
        val id = UUID.randomUUID().toString()
        val savedAt = System.currentTimeMillis()

        val item = OfflinePageItem(
            id = id,
            url = trimmedUrl,
            title = trimmedTitle,
            filePath = filePath,
            savedAt = savedAt
        )

        val existingWithSameUrl = _offlinePages.value.find { it.url == trimmedUrl }
        if (existingWithSameUrl != null) {
            try {
                File(existingWithSameUrl.filePath).delete()
            } catch (e: Exception) {
                // ignore
            }
        }

        val currentList = _offlinePages.value.filter { it.url != trimmedUrl }.toMutableList()
        currentList.add(item)
        _offlinePages.value = currentList.sortedByDescending { it.savedAt }
        saveOfflinePages()
        return item
    }

    override fun removeOfflinePage(id: String) {
        val item = _offlinePages.value.find { it.id == id }
        if (item != null) {
            try {
                File(item.filePath).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        _offlinePages.value = _offlinePages.value.filter { it.id != id }
        saveOfflinePages()
    }

    override fun isSavedOffline(url: String): Boolean {
        val trimmedUrl = url.trim()
        return _offlinePages.value.any { it.url == trimmedUrl }
    }
}
