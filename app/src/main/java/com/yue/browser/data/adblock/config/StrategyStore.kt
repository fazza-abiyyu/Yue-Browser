package com.yue.browser.data.adblock.config

import android.content.Context
import org.json.JSONObject
import java.io.File

class StrategyStore(private val context: Context) {
    private val storeDir = File(context.filesDir, "adblock/strategy")

    private val activePolicyFile: File get() = File(storeDir, "active_policy.json")
    private val fallbackPolicyFile: File get() = File(storeDir, "fallback_policy.json")
    private val versionFile: File get() = File(storeDir, "version.txt")
    private val ruleHashFile: File get() = File(storeDir, "rule_hashes.json")

    init {
        storeDir.mkdirs()
    }

    fun saveActivePolicy(policy: AdBlockPolicy) {
        try {
            activePolicyFile.writeText(policyToJson(policy).toString(2))
            versionFile.writeText(policy.policyVersion.toString())
        } catch (e: Exception) {
        }
    }

    fun saveFallbackPolicy(policy: AdBlockPolicy) {
        try {
            fallbackPolicyFile.writeText(policyToJson(policy).toString(2))
        } catch (e: Exception) {
        }
    }

    fun loadActivePolicy(): AdBlockPolicy? {
        return try {
            if (activePolicyFile.exists()) {
                val text = activePolicyFile.readText()
                jsonToPolicy(JSONObject(text))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun loadFallbackPolicy(): AdBlockPolicy? {
        return try {
            if (fallbackPolicyFile.exists()) {
                val text = fallbackPolicyFile.readText()
                jsonToPolicy(JSONObject(text))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getActiveVersion(): Int {
        return try {
            if (versionFile.exists()) {
                versionFile.readText().trim().toIntOrNull() ?: 0
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun saveRuleHashes(hashes: Map<String, String>) {
        try {
            val json = JSONObject()
            hashes.forEach { (key, value) -> json.put(key, value) }
            ruleHashFile.writeText(json.toString(2))
        } catch (e: Exception) {
        }
    }

    fun loadRuleHashes(): Map<String, String> {
        return try {
            if (ruleHashFile.exists()) {
                val json = JSONObject(ruleHashFile.readText())
                val map = mutableMapOf<String, String>()
                json.keys().forEach { key -> map[key] = json.getString(key) }
                map
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun isRuleUpToDate(ruleName: String, remoteHash: String): Boolean {
        val localHashes = loadRuleHashes()
        return localHashes[ruleName] == remoteHash && remoteHash.isNotEmpty()
    }

    private fun policyToJson(policy: AdBlockPolicy): JSONObject {
        return JSONObject().apply {
            put("policyVersion", policy.policyVersion)
            put("engineVersion", policy.engineVersion)
            put("profile", policy.profile)
            put("network", JSONObject().apply {
                put("mode", policy.network.mode.name)
                put("thirdParty", JSONObject().apply {
                    put("blockTrackers", policy.network.thirdParty.blockTrackers)
                    put("blockAds", policy.network.thirdParty.blockAds)
                    put("blockMalvertising", policy.network.thirdParty.blockMalvertising)
                })
                put("firstParty", JSONObject().apply {
                    put("enabled", policy.network.firstParty.enabled)
                })
                put("unknown", JSONObject().apply {
                    put("action", policy.network.unknown.action.name)
                })
            })
            put("navigation", JSONObject().apply {
                put("popup", JSONObject().apply {
                    put("policy", policy.navigation.popup.policy.name)
                })
                put("redirect", JSONObject().apply {
                    put("maxChain", policy.navigation.redirect.maxChain)
                    put("thirdParty", policy.navigation.redirect.thirdParty.name)
                    put("sameSite", policy.navigation.redirect.sameSite.name)
                    put("userGestureRequired", policy.navigation.redirect.userGestureRequired)
                    put("allowOAuth", policy.navigation.redirect.allowOAuth)
                })
            })
            put("cosmetic", JSONObject().apply {
                put("enabled", policy.cosmetic.enabled)
                put("strategy", policy.cosmetic.strategy)
                put("generic", JSONObject().apply {
                    put("maxRules", policy.cosmetic.generic.maxRules)
                })
                put("iframe", JSONObject().apply {
                    put("enabled", policy.cosmetic.iframe.enabled)
                })
            })
            put("scriptlet", JSONObject().apply {
                put("enabled", policy.scriptlet.enabled)
            })
            put("youtube", JSONObject().apply {
                put("enabled", policy.youtube.enabled)
                put("strategy", policy.youtube.strategy)
                put("skipHosts", JSONObject.wrap(policy.youtube.skipHosts))
            })
            put("antiAdblock", JSONObject().apply {
                put("enabled", policy.antiAdblock.enabled)
            })
            put("riskScoring", JSONObject().apply {
                put("blockThreshold", policy.riskScoring.blockThreshold)
                put("warnThreshold", policy.riskScoring.warnThreshold)
                put("signalScores", JSONObject.wrap(policy.riskScoring.signalScores))
            })
        }
    }

    private fun jsonToPolicy(json: JSONObject): AdBlockPolicy {
        val network = json.getJSONObject("network")
        val navigation = json.getJSONObject("navigation")
        val cosmetic = json.getJSONObject("cosmetic")
        val youtube = json.getJSONObject("youtube")
        val riskScoring = json.getJSONObject("riskScoring")
        val signalScores = riskScoring.optJSONObject("signalScores")
            ?.let { obj ->
                obj.keys().asSequence().associateWith { key -> obj.getInt(key) }
            } ?: emptyMap()

        return AdBlockPolicy(
            policyVersion = json.getInt("policyVersion"),
            engineVersion = json.getInt("engineVersion"),
            profile = json.getString("profile"),
            network = NetworkPolicy(
                mode = BlockMode.valueOf(network.getString("mode")),
                thirdParty = ThirdPartyPolicy(
                    blockTrackers = network.getJSONObject("thirdParty").getBoolean("blockTrackers"),
                    blockAds = network.getJSONObject("thirdParty").getBoolean("blockAds"),
                    blockMalvertising = network.getJSONObject("thirdParty").getBoolean("blockMalvertising"),
                ),
                firstParty = FirstPartyPolicy(
                    enabled = network.getJSONObject("firstParty").getBoolean("enabled"),
                ),
                unknown = UnknownPolicy(
                    action = Action.valueOf(network.getJSONObject("unknown").getString("action")),
                ),
            ),
            navigation = NavigationPolicy(
                popup = PopupNavigationPolicy(
                    policy = PopupPolicy.valueOf(navigation.getJSONObject("popup").getString("policy")),
                ),
                redirect = RedirectPolicy(
                    maxChain = navigation.getJSONObject("redirect").getInt("maxChain"),
                    thirdParty = Action.valueOf(navigation.getJSONObject("redirect").getString("thirdParty")),
                    sameSite = Action.valueOf(navigation.getJSONObject("redirect").getString("sameSite")),
                    userGestureRequired = navigation.getJSONObject("redirect").getBoolean("userGestureRequired"),
                    allowOAuth = navigation.getJSONObject("redirect").getBoolean("allowOAuth"),
                ),
            ),
            cosmetic = CosmeticPolicy(
                enabled = cosmetic.getBoolean("enabled"),
                strategy = cosmetic.getString("strategy"),
                generic = GenericCosmeticConfig(
                    maxRules = cosmetic.getJSONObject("generic").getInt("maxRules"),
                ),
                iframe = IframeConfig(
                    enabled = cosmetic.getJSONObject("iframe").getBoolean("enabled"),
                ),
            ),
            cookieBanner = CookieBannerPolicy(
                enabled = json.optJSONObject("cookieBanner")?.optBoolean("enabled", true) ?: true,
                maxRules = json.optJSONObject("cookieBanner")?.optInt("maxRules", 200) ?: 200,
            ),
            webSocket = WebSocketPolicy(
                blockTracking = json.optJSONObject("webSocket")?.optBoolean("blockTracking", true) ?: true,
                blockAnalytics = json.optJSONObject("webSocket")?.optBoolean("blockAnalytics", true) ?: true,
            ),
            scriptlet = ScriptletPolicy(
                enabled = json.optJSONObject("scriptlet")?.optBoolean("enabled", true) ?: true,
            ),
            youtube = YoutubePolicy(
                enabled = youtube.optBoolean("enabled", true),
                strategy = youtube.optString("strategy", "adaptive"),
                skipHosts = youtube.optJSONArray("skipHosts")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: listOf("music.youtube.com"),
            ),
            antiAdblock = AntiAdblockPolicy(
                enabled = json.optJSONObject("antiAdblock")?.optBoolean("enabled", true) ?: true,
            ),
            riskScoring = RiskScoringPolicy(
                blockThreshold = riskScoring.optInt("blockThreshold", 80),
                warnThreshold = riskScoring.optInt("warnThreshold", 50),
                signalScores = signalScores,
            ),
        )
    }
}