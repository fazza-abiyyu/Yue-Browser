package com.yue.browser.domain.model

data class UserScript(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0",
    val author: String = "",
    val namespace: String = "",
    val matchPatterns: List<String> = emptyList(),
    val grantPermissions: List<String> = emptyList(),
    val requireUrls: List<String> = emptyList(),
    val code: String,
    val isEnabled: Boolean = true,
    val installUrl: String = "",
    val installedAt: Long = System.currentTimeMillis()
)
