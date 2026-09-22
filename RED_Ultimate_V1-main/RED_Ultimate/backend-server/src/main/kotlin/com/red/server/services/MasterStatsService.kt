package com.red.server.services

import com.red.server.calls.ActiveCallRegistry
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
    private val redis: StringRedisTemplate,
    private val activeCalls: ActiveCallRegistry
) {
    fun getLiveMetrics(): Map<String, Any> {
        val cutoff = System.currentTimeMillis() - 5 * 60_000
        redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff.toDouble())
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val memoryPercent = if (runtime.maxMemory() == 0L) 0.0 else used * 100.0 / runtime.maxMemory()
        val dbHealthy = runCatching { postgres.queryForObject("SELECT 1", Int::class.java) == 1 }.getOrDefault(false)
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val messages24h = mongo.count(Query(Criteria.where("createdAt").gte(since)), "messages")
        val delivered24h = mongo.count(Query(Criteria.where("createdAt").gte(since).and("status").`in`("DELIVERED", "READ")), "messages")
        val read24h = mongo.count(Query(Criteria.where("createdAt").gte(since).and("status").`is`("READ")), "messages")
        val pending24h = mongo.count(Query(Criteria.where("createdAt").gte(since).and("status").`is`("SENT")), "messages")
        val activeConversations = mongo.findDistinct(Query(Criteria.where("createdAt").gte(since)), "conversationId", "messages", String::class.java).size
        return mapOf(
            "active_users" to (redis.opsForZSet().zCard("red:presence:index") ?: 0),
            "messages_24h" to messages24h,
            "delivered_messages_24h" to delivered24h,
            "read_messages_24h" to read24h,
            "pending_messages_24h" to pending24h,
            "active_conversations" to activeConversations,
            "delivery_rate_percent" to if (messages24h == 0L) 0.0 else String.format("%.2f", delivered24h * 100.0 / messages24h).toDouble(),
            "jvm_memory_percent" to String.format("%.2f", memoryPercent).toDouble(),
            "db_health" to if (dbHealthy) "UP" else "DOWN",
            "pending_approvals" to (postgres.queryForObject("SELECT count(*) FROM users WHERE status = 'PENDING'", Int::class.java) ?: 0),
            "timestamp" to System.currentTimeMillis()
        )
    }

    /** Active calls are supplied from the real-time ActiveCallRegistry via the Redis ZSet. */
    fun getVoipMetrics(): Map<String, Any> {
        // تفاصيل كاملة من السجل الحي إن توفرت، وإلا معرفات المكالمات من Redis فقط
        val live = activeCalls.snapshot()
        val calls = if (live.isNotEmpty()) live
        else (redis.opsForZSet().range("red:calls:active", 0, -1) ?: emptyList()).map { callId ->
            mapOf("id" to callId, "type" to "VOIP", "room" to callId)
        }
        return mapOf(
            "active_calls" to calls.size,
            "calls" to calls,
            "source" to "realtime",
            "timestamp" to System.currentTimeMillis()
        )
    }
}
