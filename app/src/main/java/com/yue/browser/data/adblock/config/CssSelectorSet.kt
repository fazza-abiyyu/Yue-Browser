package com.yue.browser.data.adblock.config

data class CssSelectorSet(
    val generic: List<String>,
    val domainSpecific: Map<String, List<String>>,
    val wildcard: Map<String, List<String>>,
)