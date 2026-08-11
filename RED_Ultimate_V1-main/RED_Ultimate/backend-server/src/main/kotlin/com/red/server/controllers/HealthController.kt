package com.red.server.controllers

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory
import io.minio.MinioClient
import java.time.Instant

/**
 * 🏥 YOUNES Sovereign Health Controller
 * فحص شامل مع تفاصيل كل خدمة + إصدار + وقت
 */
@RestController
class HealthController(
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val jdbcTemplate: JdbcTemplate,
    private val minioClient: MinioClient,
    @Value("\${red.minio.bucket:red-media}") private val minioBucket: String,
    @Value("\${spring.datasource.url:}") private val dbUrl: String,
    @Value("\${red.dinstar.ip:}") private val dinstarIp: String,
    @Value("\${red.dinstar.port:443}") private val dinstarPort: Int,
    @Value("\${red.dinstar.scheme:https}") private val dinstarScheme: String
) {
    companion object { private val log = LoggerFactory.getLogger(HealthController::class.java) }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val startMs = System.currentTimeMillis()

        // ─── فحص PostgreSQL ───
        val postgresResult = runCatching {
            val result = jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            result == 1
        }
        val postgresOk = postgresResult.getOrDefault(false)
        val postgresLatency = if (postgresOk) runCatching { jdbcTemplate.queryForObject("SELECT 1", Int::class.java); true }.getOrDefault(false) else false

        // ─── فحص MongoDB ───
        val mongoResult = runCatching {
            mongoTemplate.db.name
            true
        }
        val mongoOk = mongoResult.getOrDefault(false)

        // ─── فحص Redis ───
        val redisResult = runCatching {
            redisTemplate.connectionFactory?.connection?.ping()
            true
        }
        val redisOk = redisResult.getOrDefault(false)

        // ─── فحص MinIO (تخزين الوسائط S3) ───
        val minioResult = runCatching {
            minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(minioBucket).build()
            )
        }
        val minioOk = minioResult.getOrDefault(false)

        // ─── فحص Flyway (آخر migration) ───
        val flywayResult = runCatching {
            jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                String::class.java
            )
        }

        val allOk = mongoOk && redisOk && postgresOk && minioOk
        val totalMs = System.currentTimeMillis() - startMs

        if (!allOk) {
            val down = mutableListOf<String>()
            if (!mongoOk) down += "mongodb"
            if (!redisOk) down += "redis"
            if (!postgresOk) down += "postgresql"
            if (!minioOk) down += "minio"
            log.warn("Health check DOWN — services unavailable: {} ({}ms)", down.joinToString(), totalMs)
        }

        return mapOf(
            "brand" to "YOUNES",
            "displayName" to "يونس",
            "status" to if (allOk) "UP" else "DOWN",
            "version" to "1.0.0-YOUNES",
            "timestamp" to Instant.now().toString(),
            "responseTimeMs" to totalMs,
            "services" to mapOf(
                "postgresql" to mapOf(
                    "status" to if (postgresOk) "UP" else "DOWN",
                    "database" to dbUrl.substringAfterLast("/").substringBefore("?"),
                    "error" to if (!postgresOk) (postgresResult.exceptionOrNull()?.message?.take(100) ?: "unknown") else null
                ),
                "mongodb" to mapOf(
                    "status" to if (mongoOk) "UP" else "DOWN",
                    "database" to if (mongoOk) mongoTemplate.db.name else "unreachable",
                    "error" to if (!mongoOk) (mongoResult.exceptionOrNull()?.message?.take(100) ?: "unknown") else null
                ),
                "redis" to mapOf(
                    "status" to if (redisOk) "UP" else "DOWN",
                    "error" to if (!redisOk) (redisResult.exceptionOrNull()?.message?.take(100) ?: "unknown") else null
                ),
                "minio" to mapOf(
                    "status" to if (minioOk) "UP" else "DOWN",
                    "bucket" to minioBucket,
                    // bucketExists تُرجع false بلا استثناء عند غياب الـ bucket —
                    // لذلك نميّز الرسالة بدل "unknown" المضللة
                    "error" to if (!minioOk) (minioResult.exceptionOrNull()?.message?.take(100)
                        ?: "bucket '$minioBucket' does not exist") else null
                )
            ),
            "dinstar" to mapOf(
                "host" to dinstarIp,
                "port" to dinstarPort,
                "scheme" to dinstarScheme
            ),
            "flyway" to mapOf(
                "latestVersion" to flywayResult.getOrNull()
            ),
            "system" to mapOf(
                "javaVersion" to System.getProperty("java.version"),
                "osName" to System.getProperty("os.name"),
                "availableProcessors" to Runtime.getRuntime().availableProcessors(),
                "maxMemoryMb" to Runtime.getRuntime().maxMemory() / 1024 / 1024,
                "freeMemoryMb" to Runtime.getRuntime().freeMemory() / 1024 / 1024,
                "usedMemoryMb" to (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024
            )
        )
    }
}
