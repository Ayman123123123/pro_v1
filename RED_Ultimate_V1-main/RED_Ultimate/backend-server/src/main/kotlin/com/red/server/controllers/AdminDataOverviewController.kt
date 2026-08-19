package com.red.server.controllers

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Read-only operational inventory for the administrator dashboard.
 *
 * This deliberately reports aggregate counts and health metadata only. It never
 * returns E2EE envelopes, message text, recovery codes, passwords, identity
 * private keys, raw phone/SIM identifiers, or unmasked media references.
 */
@RestController
@RequestMapping("/api/admin/operations")
class AdminDataOverviewController(
    private val postgres: JdbcTemplate,
    private val mongo: MongoTemplate,
    private val redis: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(AdminDataOverviewController::class.java)

    @GetMapping("/overview")
    fun overview(): Map<String, Any?> {
        val observedAt = Instant.now()
        val postgresql = SourceProbe("postgresql", observedAt)
        val mongodb = SourceProbe("mongodb", observedAt)
        val redisSource = SourceProbe("redis", observedAt)
        val cutoff = System.currentTimeMillis() - PRESENCE_WINDOW_MS

        redisSource.read {
            redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff.toDouble())
            Unit
        }
        val online = redisSource.read { redis.opsForZSet().zCard("red:presence:index") ?: 0L }
        val activeCalls = redisSource.read { redis.opsForSet().size("red:calls:active") ?: 0L }

        return mapOf(
            "generatedAt" to observedAt.toString(),
            "users" to mapOf(
                "total" to sqlCount(postgresql, "users"),
                "approved" to sqlCount(postgresql, "users", "status='APPROVED'"),
                "pending" to sqlCount(postgresql, "users", "status='PENDING'"),
                "banned" to sqlCount(postgresql, "users", "status='BANNED'"),
                "administrators" to sqlCount(postgresql, "users", "role='ADMIN'"),
                "online" to online
            ),
            "devices" to mapOf(
                "total" to sqlCount(postgresql, "user_devices"),
                "approved" to sqlCount(postgresql, "user_devices", "status='APPROVED'"),
                "pending" to sqlCount(postgresql, "user_devices", "status='PENDING'"),
                "revoked" to sqlCount(postgresql, "user_devices", "status='REVOKED'"),
                "activeRefreshSessions" to sqlCount(postgresql, "refresh_sessions", "revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
            ),
            "moderation" to mapOf(
                "openReports" to sqlCount(postgresql, "user_reports", "status IN ('OPEN','PENDING','REVIEWING')"),
                "securityAlerts24h" to sqlCount(postgresql, "admin_audit_log", "severity IN ('WARNING','CRITICAL') AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'"),
                "auditEvents24h" to sqlCount(postgresql, "admin_audit_log", "created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'")
            ),
            "content" to mapOf(
                "groups" to mongoCount(mongodb, "group_documents", "groups"),
                "messages" to mongoCount(mongodb, "message_documents", "messages"),
                "stories" to mongoCount(mongodb, "story_documents", "stories"),
                "posts" to mongoCount(mongodb, "post_documents", "posts"),
                "channels" to sqlCount(postgresql, "channels"),
                "polls" to sqlCount(postgresql, "polls"),
                "events" to sqlCount(postgresql, "events"),
                "stickerPacks" to sqlCount(postgresql, "sticker_packs")
            ),
            "communications" to mapOf(
                "callHistory" to sqlCount(postgresql, "call_history"),
                "activeCalls" to activeCalls,
                "dinstarCdr" to sqlCount(postgresql, "dinstar_cdr"),
                "gateways" to sqlCount(postgresql, "telecom_gateways"),
                "gatewayPorts" to sqlCount(postgresql, "gateway_port_snapshots")
            ),
            "storage" to mapOf(
                "mediaGrants" to sqlCount(postgresql, "media_grants"),
                "backups" to sqlCount(postgresql, "backup_history"),
                "notifications" to sqlCount(postgresql, "user_notifications")
            ),
            "dataSources" to mapOf(
                "postgresql" to postgresql.snapshot(),
                "mongodb" to mongodb.snapshot(),
                "redis" to redisSource.snapshot()
            )
        )
    }

    private fun sqlCount(source: SourceProbe, table: String, predicate: String? = null): Long? =
        source.read {
            postgres.queryForObject(
                "SELECT COUNT(*) FROM $table${predicate?.let { " WHERE $it" } ?: ""}",
                Long::class.java
            ) ?: 0L
        }

    private fun mongoCount(source: SourceProbe, vararg collections: String): Long? {
        collections.forEach { collection ->
            val count = source.read { mongo.getCollection(collection).countDocuments() } ?: return null
            if (count > 0L) return count
        }
        return 0L
    }

    private inner class SourceProbe(
        private val source: String,
        private val observedAt: Instant,
        private var failure: Throwable? = null
    ) {
        fun <T> read(operation: () -> T): T? {
            if (failure != null) return null
            return try {
                operation()
            } catch (error: Exception) {
                failure = error
                log.warn("Operational overview source '{}' failed: {}", source, error.message, error)
                null
            }
        }

        fun snapshot(): Map<String, Any?> = mapOf(
            "available" to (failure == null),
            "error" to failure?.let { "UNAVAILABLE" },
            "observedAt" to observedAt.toString()
        )
    }

    private companion object { const val PRESENCE_WINDOW_MS = 5 * 60_000L }
}
