package com.red.server.services

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MasterStatsService(
    private val mongo: MongoTemplate,
    private val postgres: JdbcTemplate,
    private val redis: StringRedisTemplate
) {
    fun getLiveMetrics(): Map<String, Any> {
        val cutoff = System.currentTimeMillis() - 5 * 60_000
        redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff.toDouble())
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val memoryPercent = if (runtime.maxMemory() == 0L) 0.0 else used * 100.0 / runtime.maxMemory()
        val dbHealthy = runCatching { postgres.queryForObject("SELECT 1", Int::class.java) == 1 }.getOrDefault(false)
        val messages24h = mongo.count(Query(Criteria.where("createdAt").gte(Instant.now().minus(24, ChronoUnit.HOURS))), "messages")
        return mapOf(
            "active_users" to (redis.opsForZSet().zCard("red:presence:index") ?: 0),
            "messages_24h" to messages24h,
            "jvm_memory_percent" to String.format("%.2f", memoryPercent).toDouble(),
            "db_health" to if (dbHealthy) "UP" else "DOWN",
            "pending_approvals" to (postgres.queryForObject("SELECT count(*) FROM users WHERE status = 'PENDING'", Int::class.java) ?: 0),
            "timestamp" to System.currentTimeMillis()
        )
    }

    /** Active calls are supplied only when a real media worker registers them. */
    fun getVoipMetrics(): Map<String, Any> = mapOf(
        "active_calls" to (redis.opsForSet().size("red:calls:active") ?: 0),
        "source" to "realtime",
        "timestamp" to System.currentTimeMillis()
    )
}
