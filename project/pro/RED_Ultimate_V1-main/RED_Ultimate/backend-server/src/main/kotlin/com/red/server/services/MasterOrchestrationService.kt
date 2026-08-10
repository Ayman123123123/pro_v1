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
class MasterOrchestrationService(
    private val mongo: MongoTemplate,
    private val postgres: JdbcTemplate,
    private val redis: StringRedisTemplate
) {
    fun getFullSystemMetrics(): Map<String, Any> {
        val postgresUp = runCatching { postgres.queryForObject("SELECT 1", Int::class.java) == 1 }.getOrDefault(false)
        val redisUp = runCatching { redis.execute { it.ping() } == "PONG" }.getOrDefault(false)
        val mongoUp = runCatching { mongo.db.runCommand(org.bson.Document("ping", 1)).get("ok").toString().toDouble() == 1.0 }.getOrDefault(false)
        val today = Instant.now().minus(24, ChronoUnit.HOURS)
        return mapOf(
            "auth" to mapOf(
                "total_users" to (postgres.queryForObject("SELECT count(*) FROM users", Int::class.java) ?: 0),
                "pending" to (postgres.queryForObject("SELECT count(*) FROM users WHERE status = 'PENDING'", Int::class.java) ?: 0)
            ),
            "messaging" to mapOf(
                "messages_24h" to mongo.count(Query(Criteria.where("createdAt").gte(today)), "messages"),
                "active_websockets" to (redis.opsForZSet().zCard("red:presence:index") ?: 0)
            ),
            "infrastructure" to mapOf("postgres" to postgresUp, "mongo" to mongoUp, "redis" to redisUp)
        )
    }
}
