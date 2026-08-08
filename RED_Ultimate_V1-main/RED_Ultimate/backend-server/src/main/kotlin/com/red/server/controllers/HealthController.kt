package com.red.server.controllers

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory

@RestController
class HealthController(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val jdbcTemplate: JdbcTemplate
) {
    companion object { private val log = LoggerFactory.getLogger(HealthController::class.java) }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val mongoResult = runCatching { mongoTemplate.db.name; true }
        val redisResult = runCatching { redisTemplate.connectionFactory?.connection?.ping(); true }
        val postgresResult = runCatching { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 }

        val mongoOk = mongoResult.getOrDefault(false)
        val redisOk = redisResult.getOrDefault(false)
        val postgresOk = postgresResult.getOrDefault(false)
        val allOk = mongoOk && redisOk && postgresOk

        if (!allOk) {
            val down = mutableListOf<String>()
            if (!mongoOk) down += "mongodb"
            if (!redisOk) down += "redis"
            if (!postgresOk) down += "postgresql"
            log.warn("Health check DOWN — services unavailable: {}", down.joinToString())
        }

        return mapOf(
            "brand" to "YOUNES",
            "displayName" to "يونس",
            "status" to if (allOk) "UP" else "DOWN",
            "services" to mapOf(
                "mongodb" to if (mongoOk) "UP" else "DOWN",
                "redis" to if (redisOk) "UP" else "DOWN",
                "postgresql" to if (postgresOk) "UP" else "DOWN"
            ),
            "version" to "1.0.0-YOUNES",
            "timestamp" to System.currentTimeMillis()
        )
    }
}
