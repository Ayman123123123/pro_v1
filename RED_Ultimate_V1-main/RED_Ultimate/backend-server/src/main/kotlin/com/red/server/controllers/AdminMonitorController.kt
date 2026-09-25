package com.red.server.controllers

import com.red.server.services.MasterStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Compat monitor surface for the admin dashboard.
 *
 * Canonical live metrics live in [MasterStatsService.getLiveMetrics] (also
 * served at `/api/master/v1/stats/realtime`). This controller keeps the legacy
 * `/api/admin/monitor/stats` path and key shape but maps it from the same
 * durable source instead of raw Mongo/Redis reads, so both surfaces can never
 * disagree. Previously this endpoint counted the raw `messages` collection
 * (wrong name — the store uses `message_documents`) and reported `0` when
 * Redis/Mongo were unreachable.
 */
@RestController
@RequestMapping("/api/admin")
class AdminMonitorController(private val stats: MasterStatsService) {
    @GetMapping("/monitor/stats")
    fun stats(): Map<String, Any> {
        val live = stats.getLiveMetrics()
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return mapOf(
            "active_users" to (live["active_users"] ?: 0),
            // Durable 24h delivery counts from the canonical source (additive detail keys below).
            "total_messages" to (live["messages_24h"] ?: 0L),
            "messages_24h" to (live["messages_24h"] ?: 0L),
            "delivered_messages_24h" to (live["delivered_messages_24h"] ?: 0L),
            "read_messages_24h" to (live["read_messages_24h"] ?: 0L),
            "delivery_rate_percent" to (live["delivery_rate_percent"] ?: 0.0),
            "pending_approvals" to (live["pending_approvals"] ?: 0),
            "db_health" to (live["db_health"] ?: "DOWN"),
            "jvm_memory_percent" to (live["jvm_memory_percent"] ?: 0.0),
            "uptime_ms" to runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().uptime }.getOrDefault(0L),
            "cpu_cores" to runtime.availableProcessors(),
            "jvm_memory_mb" to (runtime.totalMemory() / 1024 / 1024),
            "timestamp" to System.currentTimeMillis()
        )
    }
}
