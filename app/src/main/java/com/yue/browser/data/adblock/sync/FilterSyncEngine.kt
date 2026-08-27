package com.yue.browser.data.adblock.sync

import android.content.Context
import android.util.Log
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.RuleFileInfo
import com.yue.browser.data.adblock.config.RuleMetadata
import com.yue.browser.data.adblock.config.StrategyMetadata
import com.yue.browser.data.adblock.config.StrategyStore
import com.yue.browser.data.adblock.remote.StrategyApi
import java.io.File

class FilterSyncEngine(
    private val context: Context,
    private val api: StrategyApi,
    private val store: StrategyStore,
) {
    private val rulesDir = File(context.filesDir, "adblock/rules")

    companion object {
        private const val TAG = "FilterSyncEngine"
        private const val CURRENT_ENGINE_VERSION = 1
    }

    suspend fun sync(profile: String = "balanced"): SyncResult {
        Log.d(TAG, "Starting sync for profile=$profile")

        val metadata = try {
            api.fetchMetadata()
        } catch (e: Exception) {
            Log.w(TAG, "Sync skipped (BE unreachable): ${e.message}")
            return SyncResult.Skipped("BE unreachable: ${e.message}")
        }

        if (metadata.minEngineVersion > CURRENT_ENGINE_VERSION) {
            Log.w(TAG, "Policy requires engine v${metadata.minEngineVersion}, current v$CURRENT_ENGINE_VERSION")
            return SyncResult.Skipped("Engine version mismatch")
        }

        val newPolicy = try {
            api.fetchPolicy(profile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch policy: ${e.message}")
            return SyncResult.Failure("Failed to fetch policy: ${e.message}")
        }

        if (newPolicy.engineVersion > CURRENT_ENGINE_VERSION) {
            Log.w(TAG, "Policy requires engine v${newPolicy.engineVersion}, current v$CURRENT_ENGINE_VERSION")
            return SyncResult.Skipped("Engine version mismatch in policy")
        }

        val rulesToDownload = resolveRulesToDownload(metadata.rules)
        rulesDir.mkdirs()

        val downloadedHashes = mutableMapOf<String, String>()
        downloadedHashes.putAll(store.loadRuleHashes())

        var allRulesDownloaded = true
        for ((ruleName, ruleInfo) in rulesToDownload) {
            if (ruleInfo != null) {
                downloadedHashes[ruleName] = ruleInfo.hash
            }
            val destFile = File(rulesDir, ruleName)
            val success = api.downloadRule(ruleName, destFile)
            if (!success) {
                Log.w(TAG, "Failed to download rule: $ruleName")
                allRulesDownloaded = false
            }
        }

        store.saveRuleHashes(downloadedHashes)

        val currentActive = store.loadActivePolicy()
        if (currentActive != null) {
            store.saveFallbackPolicy(currentActive)
        }

        store.saveActivePolicy(newPolicy)

        Log.d(TAG, "Sync completed (policy v${newPolicy.policyVersion}, rules=$allRulesDownloaded)")
        return SyncResult.Success(newPolicy, allRulesDownloaded)
    }

    private fun resolveRulesToDownload(rules: RuleMetadata): Map<String, RuleFileInfo?> {
        val result = mutableMapOf<String, RuleFileInfo?>()
        val allRuleNames = listOf(
            "ad_domains.txt" to rules.adDomains,
            "abpindo.txt" to rules.abpindo,
            "easylist.txt" to rules.easylist,
            "cosmetic_selectors.txt" to rules.cosmeticSelectors,
            "dangerous_elements.txt" to rules.dangerousElements,
            "gambling_moderate.txt" to rules.gamblingModerate,
            "gambling_strict.txt" to rules.gamblingStrict,
            "exceptions.txt" to null,
            "whitelist.txt" to null,
        )

        for ((ruleName, ruleInfo) in allRuleNames) {
            if (ruleInfo != null && store.isRuleUpToDate(ruleName, ruleInfo.hash)) {
                Log.d(TAG, "Skipping $ruleName (up to date)")
                continue
            }
            result[ruleName] = ruleInfo
        }

        return result
    }

    fun rollback(): AdBlockPolicy? {
        val fallback = store.loadFallbackPolicy()
        if (fallback != null) {
            store.saveActivePolicy(fallback)
            Log.d(TAG, "Rolled back to policy v${fallback.policyVersion}")
        }
        return fallback
    }

    sealed class SyncResult {
        data class Success(val policy: AdBlockPolicy, val allRulesDownloaded: Boolean) : SyncResult()
        data class Failure(val message: String) : SyncResult()
        data class Skipped(val reason: String) : SyncResult()
    }
}