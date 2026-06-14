package com.yue.browser.domain.model

data class TabGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val colorIndex: Int = 0
)
