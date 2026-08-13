package com.red.server.controllers

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
    @GetMapping("/overview")
    fun overview(): Map<String, Any> = runCatching {
        val cutoff = System.currentTimeMillis() - PRESENCE_WINDOW_MS
        runCatching { redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff.toDouble()) }

        mapOf(
            "generatedAt" to Instant.now().toString(),
            "users" to mapOf(
                "total" to sqlCount("users"),
                "approved" to sqlCount("users", "status='APPROVED'"),
                "pending" to sqlCount("users", "status='PENDING'"),
                "banned" to sqlCount("users", "status='BANNED'"),
                "administrators" to sqlCount("users", "role='ADMIN'"),
                "online" to (redis.opsForZSet().zCard("red:presence:index") ?: 0L)
            ),
            "devices" to mapOf(
                "total" to sqlCount("user_devices"),
                "approved" to sqlCount("user_devices", "status='APPROVED'"),
                "pending" to sqlCount("user_devices", "status='PENDING'"),
                "revoked" to sqlCount("user_devices", "status='REVOKED'"),
                "activeRefreshSessions" to sqlCount("refresh_sessions", "revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
            ),
            "moderation" to mapOf(
                "openReports" to sqlCount("user_reports", "status IN ('OPEN','PENDING','REVIEWING')"),
                "securityAlerts24h" to sqlCount("admin_audit_log", "severity IN ('WARNING','CRITICAL') AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'"),
                "auditEvents24h" to sqlCount("admin_audit_log", "created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'")
            ),
            "content" to mapOf(
                "groups" to mongoCount("group_documents", "groups"),
                "messages" to mongoCount("message_documents", "messages"),
                "stories" to mongoCount("story_documents", "stories"),
                "posts" to mongoCount("post_documents", "posts"),
                "channels" to sqlCount("channels"),
                "polls" to sqlCount("polls"),
                "events" to sqlCount("events"),
                "stickerPacks" to sqlCount("sticker_packs")
            ),
            "communications" to mapOf(
                "callHistory" to sqlCount("call_history"),
                "activeCalls" to (redis.opsForSet().size("red:calls:active") ?: 0L),
                "dinstarCdr" to sqlCount("dinstar_cdr"),
                "gateways" to sqlCount("telecom_gateways"),
                "gatewayPorts" to sqlCount("gateway_port_snapshots")
            ),
            "storage" to mapOf(
                "mediaGrants" to sqlCount("media_grants"),
                "backups" to sqlCount("backup_history"),
                "notifications" to sqlCount("user_notifications")
            )
        )
    }.getOrElse {
        mapOf(
            "generatedAt" to Instant.now().toString(),
            "users" to emptyMap<String, Any>(),
            "devices" to emptyMap<String, Any>(),
            "moderation" to emptyMap<String, Any>(),
            "content" to emptyMap<String, Any>(),
            "communications" to emptyMap<String, Any>(),
            "storage" to emptyMap<String, Any>(),
            "partial" to true
        )
    }

    private fun sqlCount(table: String, predicate: String? = null): Long = runCatching {
        // Table names and predicates are compile-time constants in this class.
        postgres.queryForObject("SELECT COUNT(*) FROM $table${predicate?.let { " WHERE $it" } ?: ""}", Long::class.java) ?: 0L
    }.getOrDefault(0L)

    private fun mongoCount(vararg collections: String): Long = collections
        .asSequence()
        .map { collection -> runCatching { mongo.getCollection(collection).countDocuments() }.getOrDefault(0L) }
        .firstOrNull { it > 0L } ?: 0L

    private companion object { const val PRESENCE_WINDOW_MS = 5 * 60_000L }
}
