package com.yue.browser.data.repository

import com.yue.browser.domain.model.UserScript
import com.yue.browser.domain.repository.UserScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class UserScriptRepositoryImpl : UserScriptRepository {
    companion object {
        val instance = UserScriptRepositoryImpl()
    }

    private var sharedPreferences: android.content.SharedPreferences? = null
    private val _scripts = MutableStateFlow<List<UserScript>>(emptyList())
    override val scriptsFlow: StateFlow<List<UserScript>> = _scripts.asStateFlow()

    fun initialize(context: android.content.Context) {
        if (sharedPreferences != null) return
        sharedPreferences = context.getSharedPreferences("yue_browser_userscripts", android.content.Context.MODE_PRIVATE)
        loadScripts()
    }

    private fun loadScripts() {
        val prefs = sharedPreferences ?: return
        val json = prefs.getString("userscript_items", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(json)
            val list = mutableListOf<UserScript>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserScript(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        version = obj.optString("version", "1.0"),
                        author = obj.optString("author", ""),
                        namespace = obj.optString("namespace", ""),
                        matchPatterns = jsonArrayToList(obj.optJSONArray("matchPatterns")),
                        grantPermissions = jsonArrayToList(obj.optJSONArray("grantPermissions")),
                        requireUrls = jsonArrayToList(obj.optJSONArray("requireUrls")),
                        code = obj.getString("code"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        installUrl = obj.optString("installUrl", ""),
                        installedAt = obj.optLong("installedAt", System.currentTimeMillis())
                    )
                )
            }
            _scripts.value = list
        } catch (e: Exception) {
            _scripts.value = emptyList()
        }
    }

    private fun saveScripts() {
        val prefs = sharedPreferences ?: return
        val jsonArray = JSONArray()
        _scripts.value.forEach { script ->
            val obj = JSONObject()
            obj.put("id", script.id)
            obj.put("name", script.name)
            obj.put("description", script.description)
            obj.put("version", script.version)
            obj.put("author", script.author)
            obj.put("namespace", script.namespace)
            obj.put("matchPatterns", JSONArray(script.matchPatterns))
            obj.put("grantPermissions", JSONArray(script.grantPermissions))
            obj.put("requireUrls", JSONArray(script.requireUrls))
            obj.put("code", script.code)
            obj.put("isEnabled", script.isEnabled)
            obj.put("installUrl", script.installUrl)
            obj.put("installedAt", script.installedAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString("userscript_items", jsonArray.toString()).apply()
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    override fun installScript(script: UserScript) {
        val currentList = _scripts.value.filter { it.id != script.id }.toMutableList()
        currentList.add(script)
        _scripts.value = currentList
        saveScripts()
    }

    override fun uninstallScript(id: String) {
        _scripts.value = _scripts.value.filter { it.id != id }
        saveScripts()
    }

    fun clearAllScripts() {
        _scripts.value = emptyList()
        saveScripts()
    }

    override fun toggleScript(id: String, enabled: Boolean) {
        _scripts.value = _scripts.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveScripts()
    }

    override fun getMatchingScripts(url: String): List<UserScript> {
        val host = try {
            android.net.Uri.parse(url).host ?: ""
        } catch (e: Exception) { "" }
        if (host.isBlank()) return emptyList()
        return _scripts.value.filter { script ->
            script.isEnabled && script.matchPatterns.any { pattern ->
                matchPattern(pattern, url, host)
            }
        }
    }

    private fun matchPattern(pattern: String, url: String, host: String): Boolean {
        if (pattern == "<all_urls>" || pattern == "*://*/*" || pattern == "http*://*/*") return true
        if (pattern == "http://*/*" && url.startsWith("http://")) return true
        if (pattern == "https://*/*" && url.startsWith("https://")) return true

        return try {
            val regex = patternToRegex(pattern)
            regex.containsMatchIn(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        var regex = pattern
            .replace(".", "\\.")
            .replace("?", "\\?")
            .replace("*", ".*")
        return Regex("^$regex$")
    }
}
