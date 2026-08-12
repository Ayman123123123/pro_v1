package com.red.server.controllers

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * YOUNES readiness endpoint.
 *
 * This is deliberately stricter than a liveness probe: an HTTP 200 means every
 * mandatory data service is usable. Docker/Nginx therefore cannot report a
 * healthy platform while the response body quietly says DOWN.
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
    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val startMs = System.currentTimeMillis()

        val postgresResult = runCatching {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1
        }
        val postgresOk = postgresResult.getOrDefault(false)

        // MongoDatabase.name is a local accessor and does not contact MongoDB.
        // A real ping is required for this endpoint to be a readiness signal.
        val mongoResult = runCatching {
            val reply = mongoTemplate.executeCommand(Document("ping", 1))
            (reply["ok"] as? Number)?.toDouble() == 1.0
        }
        val mongoOk = mongoResult.getOrDefault(false)

        val redisResult = runCatching {
            val connection = requireNotNull(redisTemplate.connectionFactory?.connection) {
                "Redis connection factory is unavailable"
            }
            try {
                connection.ping()
                true
            } finally {
                connection.close()
            }
        }
        val redisOk = redisResult.getOrDefault(false)

        val minioResult = runCatching {
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build())
        }
        val minioOk = minioResult.getOrDefault(false)

        val flywayResult = runCatching {
            val latest = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String::class.java
            )
            val applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Int::class.java
            ) ?: 0
            latest to applied
        }

        val allOk = mongoOk && redisOk && postgresOk && minioOk
        val totalMs = System.currentTimeMillis() - startMs

        if (!allOk) {
            val down = buildList {
                if (!mongoOk) add("mongodb")
                if (!redisOk) add("redis")
                if (!postgresOk) add("postgresql")
                if (!minioOk) add("minio")
            }
            log.warn("Readiness DOWN — unavailable services: {} ({}ms)", down.joinToString(), totalMs)
            postgresResult.exceptionOrNull()?.let { log.debug("PostgreSQL readiness failure", it) }
            mongoResult.exceptionOrNull()?.let { log.debug("MongoDB readiness failure", it) }
            redisResult.exceptionOrNull()?.let { log.debug("Redis readiness failure", it) }
            minioResult.exceptionOrNull()?.let { log.debug("MinIO readiness failure", it) }
        }

        // Public readiness output contains stable error codes, not exception text
        // that could disclose hosts, credentials, drivers, or internal topology.
        val payload: Map<String, Any> = mapOf(
            "brand" to "YOUNES",
            "displayName" to "يونس",
            "status" to if (allOk) "UP" else "DOWN",
            "version" to "1.0.0-YOUNES",
            "timestamp" to Instant.now().toString(),
            "responseTimeMs" to totalMs,
            "services" to mapOf(
                "postgresql" to mapOf(
                    "status" to if (postgresOk) "UP" else "DOWN",
                    "database" to dbUrl.substringAfterLast('/').substringBefore('?'),
                    "error" to if (postgresOk) null else "POSTGRESQL_UNAVAILABLE"
                ),
                "mongodb" to mapOf(
                    "status" to if (mongoOk) "UP" else "DOWN",
                    "database" to if (mongoOk) mongoTemplate.db.name else "unreachable",
                    "error" to if (mongoOk) null else "MONGODB_UNAVAILABLE"
                ),
                "redis" to mapOf(
                    "status" to if (redisOk) "UP" else "DOWN",
                    "error" to if (redisOk) null else "REDIS_UNAVAILABLE"
                ),
                "minio" to mapOf(
                    "status" to if (minioOk) "UP" else "DOWN",
                    "bucket" to minioBucket,
                    "error" to if (minioOk) null else "MINIO_OR_BUCKET_UNAVAILABLE"
                )
            ),
            "dinstar" to mapOf(
                "host" to dinstarIp,
                "port" to dinstarPort,
                "scheme" to dinstarScheme
            ),
            "flyway" to mapOf(
                "latestVersion" to flywayResult.getOrNull()?.first,
                "appliedCount" to (flywayResult.getOrNull()?.second ?: 0),
                "error" to if (flywayResult.isSuccess) null else "FLYWAY_HISTORY_UNAVAILABLE"
            ),
            "system" to mapOf(
                "javaVersion" to System.getProperty("java.version"),
                "availableProcessors" to Runtime.getRuntime().availableProcessors(),
                "maxMemoryMb" to Runtime.getRuntime().maxMemory() / 1024 / 1024
            )
        )

        val status = if (allOk) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(status).body(payload)
    }
}
