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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * YOUNES health endpoints.
 *
 * `/health/live` is an instant process probe used by the Android app and
 * Docker liveness. `/health` is readiness: PostgreSQL is the gate for HTTP 200
 * because auth and admin APIs cannot run without it. Mongo/Redis/MinIO are
 * reported in parallel with a short timeout so a hanging MinIO call cannot
 * stall the phone on «جاري الاتصال بالسيرفر».
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
    @Value("\${red.dinstar.scheme:https}") private val dinstarScheme: String,
    @Value("\${red.dinstar.enabled:false}") private val dinstarEnabled: Boolean,
    @Value("\${spring.data.mongodb.uri:}") private val mongoUri: String,
    @Value("\${spring.data.redis.host:}") private val redisHost: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(HealthController::class.java)
        private val probePool = Executors.newFixedThreadPool(4) { task ->
            Thread(task, "younes-health").apply { isDaemon = true }
        }
        private const val PROBE_TIMEOUT_MS = 800L
    }

    @GetMapping("/health/live")
    fun live(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf(
            "brand" to "YOUNES",
            "displayName" to "يونس",
            "status" to "UP",
            "version" to "1.0.0-YOUNES",
            "probe" to "live",
            "timestamp" to Instant.now().toString()
        )
    )

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val startMs = System.currentTimeMillis()

        val postgresFuture = asyncProbe { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 }
        val mongoFuture = asyncProbe {
            val reply = mongoTemplate.executeCommand(Document("ping", 1))
            (reply["ok"] as? Number)?.toDouble() == 1.0
        }
        val redisFuture = asyncProbe {
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
        val minioFuture = asyncProbe {
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build())
        }

        val postgresOk = awaitProbe(postgresFuture, "postgresql")
        val mongoOk = awaitProbe(mongoFuture, "mongodb")
        val redisOk = awaitProbe(redisFuture, "redis")
        val minioOk = awaitProbe(minioFuture, "minio")

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
        val apiReady = postgresOk
        val statusLabel = when {
            allOk -> "UP"
            apiReady -> "DEGRADED"
            else -> "DOWN"
        }
        val totalMs = System.currentTimeMillis() - startMs

        if (!allOk) {
            val down = buildList {
                if (!mongoOk) add("mongodb")
                if (!redisOk) add("redis")
                if (!postgresOk) add("postgresql")
                if (!minioOk) add("minio")
            }
            log.warn("Readiness {} — unavailable services: {} ({}ms)", statusLabel, down.joinToString(), totalMs)
        }

        val payload: Map<String, Any> = mapOf(
            "brand" to "YOUNES",
            "displayName" to "يونس",
            "status" to statusLabel,
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
                "enabled" to dinstarEnabled,
                "host" to dinstarIp,
                "port" to dinstarPort,
                "scheme" to dinstarScheme
            ),
            "bindings" to mapOf(
                "runtime" to InfrastructureBinding.detectRuntime().name,
                "mongodbHost" to (InfrastructureBinding.mongoHost(mongoUri) ?: "unset"),
                "postgresHost" to (InfrastructureBinding.postgresHost(dbUrl) ?: "unset"),
                "redisHost" to redisHost.ifBlank { "unset" }
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

        val status = if (apiReady) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(status).body(payload)
    }

    private fun asyncProbe(block: () -> Boolean): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({ runCatching(block).getOrDefault(false) }, probePool)

    private fun awaitProbe(future: CompletableFuture<Boolean>, name: String): Boolean = try {
        future.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (timeout: TimeoutException) {
        log.warn("Readiness probe timed out: {} ({}ms)", name, PROBE_TIMEOUT_MS)
        future.cancel(true)
        false
    } catch (error: Exception) {
        log.debug("Readiness probe failed: {}", name, error)
        false
    }
}
