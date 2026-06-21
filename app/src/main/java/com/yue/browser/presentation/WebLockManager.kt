package com.yue.browser.presentation

import com.yue.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WebLockManager(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val unlockedDomainsByTab = mutableMapOf<String, MutableMap<String, Long>>()
    private var lastInteractionTimeMillis = System.currentTimeMillis()
    private var idleTimerJob: Job? = null

    init {
        startIdleTimer()
    }

    fun notifyUserInteraction() {
        lastInteractionTimeMillis = System.currentTimeMillis()
    }

    fun isDomainLockedForTab(tabId: String, domain: String): Boolean {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        val settings = settingsRepository.settingsFlow.value
        val isLocked = settings.lockedDomains.any { cleanDomain == it || cleanDomain.endsWith(".$it") || it.endsWith(".$cleanDomain") }
        if (!isLocked) return false
        val timeoutMinutes = settings.webLockAutoLockTimeout.toIntOrNull() ?: 0
        if (timeoutMinutes == 0) return true
        val unlocked = unlockedDomainsByTab[tabId] ?: return true
        
        var checkDomain = cleanDomain
        while (checkDomain.isNotEmpty()) {
            val unlockTime = unlocked[checkDomain]
            if (unlockTime != null) {
                val timeoutMs = timeoutMinutes * 60 * 1000L
                if (System.currentTimeMillis() - unlockTime > timeoutMs) {
                    unlocked.remove(checkDomain)
                    return true
                }
                return false
            }
            val dotIndex = checkDomain.indexOf('.')
            if (dotIndex == -1) break
            checkDomain = checkDomain.substring(dotIndex + 1)
        }
        return true
    }

    fun unlockDomainForTab(tabId: String, domain: String) {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        unlockedDomainsByTab.getOrPut(tabId) { mutableMapOf() }[cleanDomain] = System.currentTimeMillis()
    }

    fun lockAllTabs() {
        unlockedDomainsByTab.clear()
    }

    fun reLockDomainForTab(tabId: String, domain: String) {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        unlockedDomainsByTab[tabId]?.remove(cleanDomain)
    }

    fun onTabClosed(tabId: String) {
        unlockedDomainsByTab.remove(tabId)
    }

    fun removeUnlockedDomain(domain: String) {
        val cleaned = domain.removePrefix("www.").lowercase()
        unlockedDomainsByTab.values.forEach { it.remove(cleaned) }
    }

    private fun startIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = scope.launch {
            while (true) {
                delay(5000)
                val settings = settingsRepository.settingsFlow.value
                val timeoutMinutes = settings.webLockAutoLockTimeout.toIntOrNull() ?: 0
                if (timeoutMinutes <= 0) continue
                val timeoutMs = timeoutMinutes * 60 * 1000L
                if (System.currentTimeMillis() - lastInteractionTimeMillis > timeoutMs) {
                    lockAllTabs()
                }
            }
        }
    }
}
