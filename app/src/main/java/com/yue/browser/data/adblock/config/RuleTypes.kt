package com.yue.browser.data.adblock.config

data class CosmeticRule(
    val selector: String,
    val domain: String?,
    val isGeneric: Boolean,
    val isCookieBanner: Boolean = false,
)

data class ScriptletRule(
    val script: String,
    val domain: String?,
)

data class DocumentContext(
    val url: String,
    val host: String,
)