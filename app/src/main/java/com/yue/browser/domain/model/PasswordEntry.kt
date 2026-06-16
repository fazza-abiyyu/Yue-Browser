package com.yue.browser.domain.model

data class PasswordEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
