package com.red.server.controllers

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val jdbcTemplate: JdbcTemplate
) {
    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val mongoOk = try {
            mongoTemplate.db.name; true
        } catch (e: Exception) { false }
        
        val redisOk = try {
            redisTemplate.connectionFactory?.connection?.ping(); true
        } catch (e: Exception) { false }
        
        val postgresOk = try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1
        } catch (e: Exception) { false }

        val allOk = mongoOk && redisOk && postgresOk
        
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
