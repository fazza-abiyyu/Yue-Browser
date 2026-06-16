package com.yue.browser.data.repository

import com.yue.browser.domain.model.PasswordEntry
import com.yue.browser.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PasswordRepositoryImpl : PasswordRepository {
    companion object {
        val instance = PasswordRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _passwords = MutableStateFlow<List<PasswordEntry>>(emptyList())
    override val passwordsFlow: StateFlow<List<PasswordEntry>> = _passwords.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (sharedPreferences != null) return
        sharedPreferences = context.getSharedPreferences("yue_browser_passwords", android.content.Context.MODE_PRIVATE)
        loadPasswords()
    }

    private fun loadPasswords() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("password_entries", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<PasswordEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PasswordEntry(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        url = obj.optString("url", ""),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        note = obj.optString("note", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            _passwords.value = list
        } catch (e: Exception) {
            _passwords.value = emptyList()
        }
    }

    private fun savePasswords() {
        val prefs = sharedPreferences ?: return
        val jsonArray = org.json.JSONArray()
        _passwords.value.forEach { entry ->
            val obj = org.json.JSONObject()
            obj.put("id", entry.id)
            obj.put("name", entry.name)
            obj.put("url", entry.url)
            obj.put("username", entry.username)
            obj.put("password", entry.password)
            obj.put("note", entry.note)
            obj.put("createdAt", entry.createdAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString("password_entries", jsonArray.toString()).apply()
    }

    override fun addPassword(entry: PasswordEntry) {
        val currentList = _passwords.value.toMutableList()
        currentList.add(entry)
        _passwords.value = currentList
        savePasswords()
    }

    override fun updatePassword(entry: PasswordEntry) {
        val currentList = _passwords.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            currentList[index] = entry
            _passwords.value = currentList
            savePasswords()
        }
    }

    override fun deletePassword(id: String) {
        _passwords.value = _passwords.value.filter { it.id != id }
        savePasswords()
    }

    override fun getPasswordForUrl(url: String): PasswordEntry? {
        if (url.isBlank() || url == "yue://newtab") return null
        val host = try {
            android.net.Uri.parse(url).host ?: ""
        } catch (e: Exception) { "" }
        if (host.isBlank()) return null
        val cleanHost = host.removePrefix("www.").removePrefix("m.")
        return _passwords.value.firstOrNull { entry ->
            if (entry.url.isBlank()) return@firstOrNull false
            try {
                val entryHost = android.net.Uri.parse(entry.url).host ?: ""
                val cleanEntry = entryHost.removePrefix("www.").removePrefix("m.")
                cleanEntry == cleanHost || cleanEntry.endsWith(".$cleanHost") || cleanHost.endsWith(".$cleanEntry")
            } catch (e: Exception) { false }
        }
    }
}
