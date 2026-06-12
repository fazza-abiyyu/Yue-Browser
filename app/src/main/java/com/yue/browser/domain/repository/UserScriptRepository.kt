package com.yue.browser.domain.repository

import com.yue.browser.domain.model.UserScript
import kotlinx.coroutines.flow.StateFlow

interface UserScriptRepository {
    val scriptsFlow: StateFlow<List<UserScript>>
    fun installScript(script: UserScript)
    fun uninstallScript(id: String)
    fun toggleScript(id: String, enabled: Boolean)
    fun getMatchingScripts(url: String): List<UserScript>
}
