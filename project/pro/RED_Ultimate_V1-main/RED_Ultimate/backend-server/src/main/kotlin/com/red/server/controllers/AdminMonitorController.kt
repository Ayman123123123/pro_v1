package com.red.server.controllers

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminMonitorController(
    private val mongo: MongoTemplate,
    private val redis: RedisTemplate<String, String>
) {
    @GetMapping("/monitor/stats")
    fun stats(): Map<String, Any> {
        val cutoff = System.currentTimeMillis() - 5 * 60_000
        redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff.toDouble())
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return mapOf(
            "active_users" to (redis.opsForZSet().zCard("red:presence:index") ?: 0),
            "total_messages" to mongo.getCollection("messages").countDocuments(),
            "jvm_memory_percent" to if (runtime.maxMemory() == 0L) 0 else (used * 100 / runtime.maxMemory()),
            "uptime_ms" to runCatching { java.lang.management.ManagementFactory.getRuntimeMXBean().uptime }.getOrDefault(0L),
            "cpu_cores" to runtime.availableProcessors(),
            "jvm_memory_mb" to (runtime.totalMemory() / 1024 / 1024),
            "timestamp" to System.currentTimeMillis()
        )
    }
}
