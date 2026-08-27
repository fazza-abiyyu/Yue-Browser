package com.yue.browser.data.adblock.config

data class StrategyMetadata(
    val latestPolicyVersion: Int,
    val minEngineVersion: Int,
    val lastUpdated: String,
    val rules: RuleMetadata,
    val availableProfiles: List<String>,
)

data class RuleMetadata(
    val adDomains: RuleFileInfo,
    val abpindo: RuleFileInfo,
    val easylist: RuleFileInfo,
    val cosmeticSelectors: RuleFileInfo,
    val dangerousElements: RuleFileInfo,
    val gamblingModerate: RuleFileInfo,
    val gamblingStrict: RuleFileInfo,
)

data class RuleFileInfo(
    val version: Int,
    val hash: String,
)