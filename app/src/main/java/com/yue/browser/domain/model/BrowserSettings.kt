package com.yue.browser.domain.model

data class SpeedDialConfig(
    val name: String,
    val url: String,
    val iconLetter: String,
    val iconBgColorHex: String
)

data class BrowserSettings(
    val desktopDomains: Set<String> = emptySet(),
    val isDarkModeSimulated: Boolean = false,
    val isJavaScriptEnabled: Boolean = true,
    val searchEngineUrl: String = "https://www.google.com/search?q=",
    val isAdBlockEnabled: Boolean = true,
    val enabledAddons: Set<String> = emptySet(),
    val customAdBlockFilters: List<String> = emptyList(),
    val speedDials: List<SpeedDialConfig> = listOf(
        SpeedDialConfig("Google", "https://www.google.com", "G", "EA4335"),
        SpeedDialConfig("YouTube", "https://www.youtube.com", "Y", "FF0000"),
        SpeedDialConfig("GitHub", "https://github.com", "H", "24292E"),
        SpeedDialConfig("Reddit", "https://www.reddit.com", "R", "FF4500"),
        SpeedDialConfig("Wikipedia", "https://www.wikipedia.org", "W", "72777D"),
        SpeedDialConfig("Medium", "https://medium.com", "M", "000000")
    ),
    val addonsMetadata: Map<String, Map<String, String>> = emptyMap(),
    val blockedCssSelectors: Map<String, List<String>> = emptyMap() // domain -> list of CSS selectors
)
