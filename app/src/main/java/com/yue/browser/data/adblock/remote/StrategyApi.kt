package com.yue.browser.data.adblock.remote

import android.content.Context
import android.os.Build
import android.util.Log
import com.yue.browser.BuildConfig
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.RuleFileInfo
import com.yue.browser.data.adblock.config.RuleMetadata
import com.yue.browser.data.adblock.config.StrategyMetadata
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class StrategyApi(
    context: Context? = null,
    private val baseUrl: String? = null,
) {
    private val resolvedBaseUrl: String by lazy {
        baseUrl ?: resolveBaseUrl(context)
    }

    private fun resolveBaseUrl(context: Context?): String {
        if (context != null && isEmulator()) {
            Log.d(TAG, "Running on emulator, using emulator URL")
            return BuildConfig.ADBLOCK_BASE_URL
        }
        Log.d(TAG, "Using device IP URL")
        return BuildConfig.ADBLOCK_DEVICE_IP
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        return (fingerprint.contains("generic", ignoreCase = true) ||
                fingerprint.contains("emulator", ignoreCase = true) ||
                model.contains("Emulator", ignoreCase = true) ||
                model.contains("sdk", ignoreCase = true) ||
                manufacturer.contains("Genymotion", ignoreCase = true) ||
                Build.BRAND.contains("generic", ignoreCase = true))
    }

    fun fetchMetadata(): StrategyMetadata {
        val url = URL("$resolvedBaseUrl/adblock/metadata")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response).getJSONObject("data")
                parseMetadata(json)
            } else {
                throw Exception("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchPolicy(profile: String = "balanced"): AdBlockPolicy {
        val url = URL("$resolvedBaseUrl/adblock/policy?profile=$profile")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response).getJSONObject("data")
                parsePolicy(json)
            } else {
                throw Exception("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun downloadRule(ruleName: String, destFile: File): Boolean {
        val url = URL("$resolvedBaseUrl/adblock/rules/$ruleName")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.exists() && destFile.length() > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download rule $ruleName: ${e.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    fun getBaseUrl(): String = resolvedBaseUrl

    private fun parseMetadata(json: JSONObject): StrategyMetadata {
        val rules = json.getJSONObject("rules")
        return StrategyMetadata(
            latestPolicyVersion = json.optInt("latestPolicyVersion", 0),
            minEngineVersion = json.optInt("minEngineVersion", 1),
            lastUpdated = json.optString("lastUpdated", ""),
            rules = RuleMetadata(
                adDomains = parseRuleInfo(rules.optJSONObject("adDomains") ?: JSONObject()),
                abpindo = parseRuleInfo(rules.optJSONObject("abpindo") ?: JSONObject()),
                easylist = parseRuleInfo(rules.optJSONObject("easylist") ?: JSONObject()),
                cosmeticSelectors = parseRuleInfo(rules.optJSONObject("cosmeticSelectors") ?: JSONObject()),
                dangerousElements = parseRuleInfo(rules.optJSONObject("dangerousElements") ?: JSONObject()),
                gamblingModerate = parseRuleInfo(rules.optJSONObject("gamblingModerate") ?: JSONObject()),
                gamblingStrict = parseRuleInfo(rules.optJSONObject("gamblingStrict") ?: JSONObject()),
            ),
            availableProfiles = json.optJSONArray("availableProfiles")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("balanced", "aggressive", "minimal", "anti-judol"),
        )
    }

    private fun parseRuleInfo(json: JSONObject): RuleFileInfo {
        return RuleFileInfo(
            version = json.optInt("version", 0),
            hash = json.optString("hash", ""),
        )
    }

    private fun parsePolicy(json: JSONObject): AdBlockPolicy {
        val network = json.optJSONObject("network") ?: JSONObject()
        val navigation = json.optJSONObject("navigation") ?: JSONObject()
        val cosmetic = json.optJSONObject("cosmetic") ?: JSONObject()
        val youtube = json.optJSONObject("youtube") ?: JSONObject()
        val riskScoring = json.optJSONObject("riskScoring") ?: JSONObject()

        return AdBlockPolicy(
            policyVersion = json.optInt("policyVersion", 1),
            engineVersion = json.optInt("engineVersion", 1),
            profile = json.optString("profile", "balanced"),
            network = com.yue.browser.data.adblock.config.NetworkPolicy(
                mode = runCatching {
                    com.yue.browser.data.adblock.config.BlockMode.valueOf(
                        network.optString("mode", "BALANCED")
                    )
                }.getOrDefault(com.yue.browser.data.adblock.config.BlockMode.BALANCED),
                thirdParty = com.yue.browser.data.adblock.config.ThirdPartyPolicy(
                    blockTrackers = network.optJSONObject("thirdParty")?.optBoolean("blockTrackers", true) ?: true,
                    blockAds = network.optJSONObject("thirdParty")?.optBoolean("blockAds", true) ?: true,
                    blockMalvertising = network.optJSONObject("thirdParty")?.optBoolean("blockMalvertising", true) ?: true,
                ),
                firstParty = com.yue.browser.data.adblock.config.FirstPartyPolicy(
                    enabled = network.optJSONObject("firstParty")?.optBoolean("enabled", false) ?: false,
                ),
                unknown = com.yue.browser.data.adblock.config.UnknownPolicy(
                    action = runCatching {
                        com.yue.browser.data.adblock.config.Action.valueOf(
                            network.optJSONObject("unknown")?.optString("action", "ALLOW") ?: "ALLOW"
                        )
                    }.getOrDefault(com.yue.browser.data.adblock.config.Action.ALLOW),
                ),
            ),
            navigation = com.yue.browser.data.adblock.config.NavigationPolicy(
                popup = com.yue.browser.data.adblock.config.PopupNavigationPolicy(
                    policy = runCatching {
                        com.yue.browser.data.adblock.config.PopupPolicy.valueOf(
                            navigation.optJSONObject("popup")?.optString("policy", "SMART") ?: "SMART"
                        )
                    }.getOrDefault(com.yue.browser.data.adblock.config.PopupPolicy.SMART),
                ),
                redirect = com.yue.browser.data.adblock.config.RedirectPolicy(
                    maxChain = navigation.optJSONObject("redirect")?.optInt("maxChain", 3) ?: 3,
                    thirdParty = runCatching {
                        com.yue.browser.data.adblock.config.Action.valueOf(
                            navigation.optJSONObject("redirect")?.optString("thirdParty", "BLOCK") ?: "BLOCK"
                        )
                    }.getOrDefault(com.yue.browser.data.adblock.config.Action.BLOCK),
                    sameSite = runCatching {
                        com.yue.browser.data.adblock.config.Action.valueOf(
                            navigation.optJSONObject("redirect")?.optString("sameSite", "ALLOW") ?: "ALLOW"
                        )
                    }.getOrDefault(com.yue.browser.data.adblock.config.Action.ALLOW),
                    userGestureRequired = navigation.optJSONObject("redirect")?.optBoolean("userGestureRequired", true) ?: true,
                    allowOAuth = navigation.optJSONObject("redirect")?.optBoolean("allowOAuth", true) ?: true,
                ),
            ),
            cosmetic = com.yue.browser.data.adblock.config.CosmeticPolicy(
                enabled = cosmetic.optBoolean("enabled", true),
                strategy = cosmetic.optString("strategy", "domain_aware"),
                generic = com.yue.browser.data.adblock.config.GenericCosmeticConfig(
                    maxRules = cosmetic.optJSONObject("generic")?.optInt("maxRules", 500) ?: 500,
                ),
                iframe = com.yue.browser.data.adblock.config.IframeConfig(
                    enabled = cosmetic.optJSONObject("iframe")?.optBoolean("enabled", true) ?: true,
                ),
            ),
            cookieBanner = com.yue.browser.data.adblock.config.CookieBannerPolicy(
                enabled = json.optJSONObject("cookieBanner")?.optBoolean("enabled", true) ?: true,
                maxRules = json.optJSONObject("cookieBanner")?.optInt("maxRules", 200) ?: 200,
            ),
            webSocket = com.yue.browser.data.adblock.config.WebSocketPolicy(
                blockTracking = json.optJSONObject("webSocket")?.optBoolean("blockTracking", true) ?: true,
                blockAnalytics = json.optJSONObject("webSocket")?.optBoolean("blockAnalytics", true) ?: true,
            ),
            scriptlet = com.yue.browser.data.adblock.config.ScriptletPolicy(
                enabled = json.optJSONObject("scriptlet")?.optBoolean("enabled", true) ?: true,
            ),
            youtube = com.yue.browser.data.adblock.config.YoutubePolicy(
                enabled = youtube.optBoolean("enabled", true),
                strategy = youtube.optString("strategy", "adaptive"),
                skipHosts = youtube.optJSONArray("skipHosts")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: listOf("music.youtube.com"),
            ),
            antiAdblock = com.yue.browser.data.adblock.config.AntiAdblockPolicy(
                enabled = json.optJSONObject("antiAdblock")?.optBoolean("enabled", true) ?: true,
            ),
            riskScoring = com.yue.browser.data.adblock.config.RiskScoringPolicy(
                blockThreshold = riskScoring.optInt("blockThreshold", 80),
                warnThreshold = riskScoring.optInt("warnThreshold", 50),
                signalScores = riskScoring.optJSONObject("signalScores")?.let { obj ->
                    obj.keys().asSequence().associateWith { key -> obj.getInt(key) }
                } ?: emptyMap(),
            ),
        )
    }

    companion object {
        private const val TAG = "StrategyApi"
    }
}