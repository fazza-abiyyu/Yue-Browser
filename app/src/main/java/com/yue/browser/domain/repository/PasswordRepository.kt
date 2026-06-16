package com.yue.browser.domain.repository

import com.yue.browser.domain.model.PasswordEntry
import kotlinx.coroutines.flow.StateFlow

interface PasswordRepository {
    val passwordsFlow: StateFlow<List<PasswordEntry>>
    fun addPassword(entry: PasswordEntry)
    fun updatePassword(entry: PasswordEntry)
    fun deletePassword(id: String)
    fun getPasswordForUrl(url: String): PasswordEntry?
}
