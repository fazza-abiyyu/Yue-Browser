package com.yue.browser.data.adblock.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service: Ad blocking statistics and analytics.
 * Tracks blocked items per session, per-domain counts, and performance metrics.
 */
class AdBlockStats {
    private val tag = "AdBlockStats"

    private val totalBlocked = AtomicInteger(0)
    private val totalAllows = AtomicInteger(0)
    private val totalWarnings = AtomicInteger(0)
    
    private val blockedByDomain = ConcurrentHashMap<String, AtomicInteger>()
    private val blockedByType = ConcurrentHashMap<String, AtomicInteger>()
    private val blockingLatencyMs = ConcurrentHashMap<String, MutableList<Long>>()
    
    private var sessionStartTime = System.currentTimeMillis()
    private var lastSyncTime = 0L

    // ========================================================================
    // Recording
    // ========================================================================

    fun recordBlocked(host: String, signalType: String?, latencyMs: Long) {
        totalBlocked.incrementAndGet()
        
        blockedByDomain.getOrPut(host) { AtomicInteger(0) }.incrementAndGet()
        if (signalType != null) {
            blockedByType.getOrPut(signalType) { AtomicInteger(0) }.incrementAndGet()
        }
        
        if (signalType != null) {
            val latencies = blockingLatencyMs.getOrPut(signalType) { mutableListOf() }
            synchronized(latencies) {
                latencies.add(latencyMs)
                if (latencies.size > 100) latencies.removeAt(0)
            }
        }
    }

    fun recordAllowed() {
        totalAllows.incrementAndGet()
    }

    fun recordWarning() {
        totalWarnings.incrementAndGet()
    }

    // ========================================================================
    // Queries
    // ========================================================================

    fun getTotalBlocked(): Int = totalBlocked.get()
    fun getTotalAllowed(): Int = totalAllows.get()
    fun getTotalWarnings(): Int = totalWarnings.get()
    
    fun getSessionDurationMs(): Long = System.currentTimeMillis() - sessionStartTime
    
    fun getBlockedByDomain(): Map<String, Int> {
        return blockedByDomain.mapValues { it.value.get() }
    }
    
    fun getTopBlockedDomains(limit: Int = 10): List<Pair<String, Int>> {
        return blockedByDomain.map { it.key to it.value.get() }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    fun getBlockedByType(): Map<String, Int> {
        return blockedByType.mapValues { it.value.get() }
    }
    
    fun getAverageLatencyMs(signalType: String): Long {
        val latencies = blockingLatencyMs[signalType] ?: return 0
        synchronized(latencies) {
            if (latencies.isEmpty()) return 0
            return latencies.average().toLong()
        }
    }
    
    fun getBlockRatePercent(): Float {
        val total = totalBlocked.get() + totalAllows.get()
        if (total == 0) return 0f
        return (totalBlocked.get().toFloat() / total.toFloat()) * 100f
    }

    // ========================================================================
    // Export / Reset
    // ========================================================================

    fun getStatsSnapshot(): StatsSnapshot {
        return StatsSnapshot(
            totalBlocked = totalBlocked.get(),
            totalAllowed = totalAllows.get(),
            totalWarnings = totalWarnings.get(),
            sessionDurationMs = getSessionDurationMs(),
            blockRatePercent = getBlockRatePercent(),
            topBlockedDomains = getTopBlockedDomains(5),
            blockedByType = getBlockedByType(),
            avgLatencyMs = blockingLatencyMs.mapValues { (_, latencies) ->
                synchronized(latencies) {
                    if (latencies.isEmpty()) 0 else latencies.average().toLong()
                }
            },
        )
    }

    fun reset() {
        totalBlocked.set(0)
        totalAllows.set(0)
        totalWarnings.set(0)
        blockedByDomain.clear()
        blockedByType.clear()
        blockingLatencyMs.clear()
        sessionStartTime = System.currentTimeMillis()
        Log.d(tag, "Stats reset")
    }

    data class StatsSnapshot(
        val totalBlocked: Int,
        val totalAllowed: Int,
        val totalWarnings: Int,
        val sessionDurationMs: Long,
        val blockRatePercent: Float,
        val topBlockedDomains: List<Pair<String, Int>>,
        val blockedByType: Map<String, Int>,
        val avgLatencyMs: Map<String, Long>,
    )
}