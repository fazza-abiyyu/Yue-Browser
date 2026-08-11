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
    val blockedCssSelectors: Map<String, List<String>> = emptyMap(), // domain -> list of CSS selectors
    val isUserScriptEnabled: Boolean = true,
    val isZoomEnabled: Boolean = true,
    val isBackgroundPlayEnabledNormal: Boolean = false,
    val isBackgroundPlayEnabledPrivate: Boolean = false,
    // Web Lock
    val lockedDomains: Set<String> = emptySet(),
    val webLockPinHash: String = "", // SHA-256 hash of PIN, empty = no PIN set
    val webLockAutoLockTimeout: String = "0", // in minutes, "0" = seketika (instant), options: 0, 1, 5, 15, 30
    val webLockMaxAttempts: Int = 5, // max failed PIN attempts before lockout
    val webLockLockDurationMinutes: Int = 5, // lockout duration in minutes
    val webLockAttemptsEnabled: Boolean = true, // enable/disable failed attempt lockout
    // Playback Settings
    val isVideoSpeedupEnabled: Boolean = true,
    val videoSpeedupRate: Float = 2.0f,
    val isAutoPipEnabled: Boolean = false,
    val isVideoOrientationLocked: Boolean = false,
    val adblockWhitelistedDomains: Set<String> = emptySet(),
    val darkmodeWhitelistedDomains: Set<String> = emptySet(),
    val isDownloadMultiThread: Boolean = true,
    val downloadDirectory: String = "",
    val isDeletePhysicalFile: Boolean = true,
    val defaultConnectionCount: Int = 4,
    val appLanguage: String = "system",
    val appThemeMode: String = "dark",
    // First run
    val firstRunCompleted: Boolean = false,
    // Security & Privacy
    val isHttpsOnlyModeEnabled: Boolean = false,
    val isDoNotTrackEnabled: Boolean = false,
    val isBlockThirdPartyCookiesEnabled: Boolean = true,
    val isFingerprintProtectionEnabled: Boolean = false,
    val isReferrerControlEnabled: Boolean = false,
    val isSafeBrowsingEnabled: Boolean = true,
    val sitePermissions: Map<String, Map<String, Boolean>> = emptyMap()
)
