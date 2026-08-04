package com.yue.browser.data.repository

import com.yue.browser.domain.model.PasswordEntry
import com.yue.browser.domain.repository.PasswordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasswordRepositoryImpl : PasswordRepository {
    companion object {
        val instance = PasswordRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _passwords = MutableStateFlow<List<PasswordEntry>>(emptyList())
    override val passwordsFlow: StateFlow<List<PasswordEntry>> = _passwords.asStateFlow()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
                try {
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
                } catch (e: Exception) {
                    android.util.Log.e("PasswordRepository", "Error parsing password entry at index $i", e)
                }
            }
            _passwords.value = list
        } catch (e: Exception) {
            _passwords.value = emptyList()
        }
    }

    private fun savePasswords() {
        val prefs = sharedPreferences ?: return
        try {
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
            prefs.edit().putString("password_entries", jsonArray.toString()).commit()
        } catch (e: Exception) {
            android.util.Log.e("PasswordRepository", "Error saving passwords", e)
        }
    }

    override fun addPassword(entry: PasswordEntry) {
        val currentList = _passwords.value.toMutableList()
        currentList.add(entry)
        _passwords.value = currentList
        coroutineScope.launch {
            savePasswords()
        }
    }

    override fun updatePassword(entry: PasswordEntry) {
        val currentList = _passwords.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            currentList[index] = entry
            _passwords.value = currentList
            coroutineScope.launch {
                savePasswords()
            }
        }
    }

    override fun deletePassword(id: String) {
        _passwords.value = _passwords.value.filter { it.id != id }
        coroutineScope.launch {
            savePasswords()
        }
    }

    private fun getParentDomain(host: String): String {
        val clean = host.removePrefix("www.").removePrefix("m.").toLowerCase(java.util.Locale.US).trim()
        val parts = clean.split(".")
        if (parts.size <= 2) return clean
        
        val lastTwo = parts.takeLast(2).joinToString(".")
        val multiPartTlds = setOf(
            "co.id", "com.id", "net.id", "org.id", "web.id", "my.id", "biz.id",
            "co.uk", "org.uk", "me.uk",
            "com.au", "net.au", "org.au",
            "com.br", "net.br", "org.br",
            "com.cn", "net.cn", "org.cn", "gov.cn",
            "co.jp", "org.jp", "ne.jp",
            "co.kr", "ne.kr", "re.kr",
            "com.sg", "net.sg", "org.sg",
            "com.tw", "net.tw", "org.tw"
        )
        
        return if (multiPartTlds.contains(lastTwo)) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    override fun getPasswordForUrl(url: String): PasswordEntry? {
        if (url.isBlank() || url == "yue://newtab") return null
        val host = try {
            android.net.Uri.parse(url).host ?: ""
        } catch (e: Exception) { "" }
        if (host.isBlank()) return null
        val parentHost = getParentDomain(host)
        return _passwords.value.firstOrNull { entry ->
            if (entry.url.isBlank()) return@firstOrNull false
            try {
                val entryHost = android.net.Uri.parse(entry.url).host ?: ""
                val parentEntry = getParentDomain(entryHost)
                parentEntry == parentHost
            } catch (e: Exception) { false }
        }
    }
}

