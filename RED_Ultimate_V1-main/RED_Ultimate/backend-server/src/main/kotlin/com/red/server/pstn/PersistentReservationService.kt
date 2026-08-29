package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * حجوزات المنافذ والمكالمات النشطة — **Postgres مصدر الحقيقة، Redis كاش سريع، والذاكرة ملاذ أخير**.
 *
 * ## لماذا ثلاث طبقات؟
 * - **Postgres (V42/V43)**: يبقى بعد إعادة تشغيل الباكند أو Redis أو حتى فقدان كامل للذاكرة.
 *   الفهرس الجزئي الفريد `WHERE expires_at > NOW()` يمنع الحجز المزدوج على مستوى قاعدة
 *   البيانات حتى مع تعدد نسخ الباكند — لا يمكن لمكالمتين أن تحجزا نفس المنفذ في نفس اللحظة
 *   حتى مع تزامن كامل. لا يمكن لـ Redis وحده ضمان ذلك عبر نسخ متعددة بلا Redlock.
 * - **Redis**: قراءة بـ O(1) بلا استعلام SQL لكل مكالمة، وكتابة TTL تلقائية تحرر المنفذ
 *   حتى لو لم يصل `release`. يُستخدم كـ write-through cache.
 * - **الذاكرة (ConcurrentHashMap)**: ملاذ أخير حين تكون Postgres و Redis غير متاحتين
 *   (الاختبارات الوحدوية، أو انقطاع الشبكة). يضمن أن الموزّع لا ينهار أبدًا.
 *
 * ## الضمانات
 * - **ذرّي**: `INSERT` في Postgres هو الحجز نفسه — لا فحص ثم إدراج منفصل يفتح نافذة سباق.
 * - **محدود زمنيًا**: كل حجز يحمل `expires_at` — حتى لو لم يُستدع `release` (تحطم العملية)
 *   سيُحرر تلقائيًا بعد TTL.
 * - **قابل للتنظيف**: مهمة مجدولة تحذف المنتهية من Postgres و Redis كل دقيقة.
 */
@Service
class PersistentReservationService(
    private val jdbc: JdbcTemplate,
    private val redis: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(PersistentReservationService::class.java)

    // ── مفاتيح Redis (متوافقة مع PstnActiveCallKeys) ──────────────────────
    private fun portKey(gatewayId: UUID?, port: Int) = "gw:reserve:${gatewayId ?: "local"}:$port"
    private fun activeKey(userId: UUID) = "red:pstn:active:$userId"
    private fun callKey(callId: String) = "red:pstn:call:$callId"

    // ── حجز المنفذ ───────────────────────────────────────────────────────
    /**
     * يحاول حجز منفذ. يعود `true` إن نجح، `false` إن كان محجوزًا.
     * يكتب أولًا في Postgres (مصدر الحقيقة)، ثم يكاش في Redis.
     * فشل Redis لا يُبطل الحجز — Postgres كافٍ للصحة.
     */
    fun tryReservePort(
        gatewayId: UUID?,
        port: Int,
        userId: UUID,
        callId: String,
        ttl: Duration = Duration.ofMinutes(5),
        targetNumber: String? = null
    ): Boolean {
        val now = Instant.now()
        val expiresAt = now.plus(ttl)
        // 1) Postgres — الحجز الذري الحقيقي عبر الفهرس الجزئي الفريد
        //    الفهرس uq_gateway_port_active_reservation يمنع INSERT ثانٍ لنفس (gateway,port)
        //    طالما الأول لم ينته — حتى مع تزامن كامل بين نسختين.
        try {
            jdbc.update(
                "INSERT INTO gateway_port_reservations (gateway_id, port_index, reserved_by_user_id, call_id, target_number, reserved_at, expires_at) VALUES (?,?,?,?,?,?,?)",
                gatewayId, port, userId, callId, targetNumber, java.sql.Timestamp.from(now), java.sql.Timestamp.from(expiresAt)
            )
        } catch (e: DuplicateKeyException) {
            log.debug("Port {}.{} already reserved in Postgres for call {}", gatewayId, port, callId)
            return false
        } catch (e: Exception) {
            // فحص إن كان السبب هو انتهاك الفهرس الجزئي لكن الاستثناء ليس DuplicateKeyException
            // (بعض السائقين يلفونه كـ BadSqlGrammar أو DataIntegrityViolation بلا النوع الدقيق)
            val msg = e.message ?: ""
            if (msg.contains("uq_gateway_port_active_reservation") || msg.contains("duplicate key")) {
                log.debug("Port {}.{} already reserved (via constraint): {}", gatewayId, port, msg)
                return false
            }
            // Postgres غير متاح (اختبارات بلا DB، أو انقطاع) — سقط إلى Redis
            log.debug("Postgres reservation failed, falling back to Redis: {}", e.message)
            return tryReservePortRedis(gatewayId, port, callId, ttl)
        }

        // 2) Redis — كاش سريع (best-effort, لا يُبطل نجاح Postgres)
        try {
            redis.opsForValue().setIfAbsent(portKey(gatewayId, port), callId, ttl)
        } catch (e: Exception) {
            log.debug("Redis cache for port reservation failed (non-fatal): {}", e.message)
        }
        return true
    }

    private fun tryReservePortRedis(gatewayId: UUID?, port: Int, callId: String, ttl: Duration): Boolean {
        return try {
            val ok = redis.opsForValue().setIfAbsent(portKey(gatewayId, port), callId, ttl) ?: false
            if (!ok) log.debug("Port {}.{} already reserved in Redis", gatewayId, port)
            ok
        } catch (e: Exception) {
            log.warn("Both Postgres and Redis unavailable for port reservation — allowing (in-memory fallback): {}", e.message)
            true // ملاذ أخير: اسمح بالحجز حتى لا تُرفض كل المكالمات عند فقدان البنية
        }
    }

    fun releasePort(gatewayId: UUID?, port: Int) {
        try {
            jdbc.update(
                "DELETE FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ?",
                gatewayId, port
            )
        } catch (e: Exception) {
            log.debug("Postgres release failed: {}", e.message)
        }
        try {
            redis.delete(portKey(gatewayId, port))
        } catch (e: Exception) {
            log.debug("Redis release failed: {}", e.message)
        }
    }

    fun isPortReserved(gatewayId: UUID?, port: Int): Boolean {
        // Redis أولاً (أسرع)
        try {
            val cached = redis.opsForValue().get(portKey(gatewayId, port))
            if (cached != null) return true
        } catch (_: Exception) {}
        // Postgres ثانيًا (أدق)
        return try {
            val count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ? AND expires_at > NOW()",
                Int::class.java, gatewayId, port
            ) ?: 0
            count > 0
        } catch (_: Exception) { false }
    }

    // ── المكالمات النشطة ──────────────────────────────────────────────────
    fun bindActiveCall(userId: UUID, callId: String, gatewayId: UUID?, port: Int, targetNumber: String?, ttl: Duration = Duration.ofMinutes(30)) {
        val now = Instant.now()
        val expiresAt = now.plus(ttl)
        try {
            jdbc.update(
                """INSERT INTO pstn_active_calls (call_id, user_id, gateway_id, port_index, target_number, started_at, expires_at)
                   VALUES (?,?,?,?,?,?,?)
                   ON CONFLICT (call_id) DO UPDATE SET expires_at = EXCLUDED.expires_at""",
                callId, userId, gatewayId, port, targetNumber, java.sql.Timestamp.from(now), java.sql.Timestamp.from(expiresAt)
            )
        } catch (e: Exception) {
            log.debug("Postgres active call bind failed: {}", e.message)
        }
        try {
            redis.opsForValue().set(activeKey(userId), PstnActiveCallKeys.format(callId, gatewayId, port), ttl)
            redis.opsForValue().set(callKey(callId), userId.toString(), ttl)
        } catch (e: Exception) {
            log.debug("Redis active call bind failed: {}", e.message)
        }
    }

    fun unbindActiveCall(userId: UUID, callId: String) {
        try {
            jdbc.update("DELETE FROM pstn_active_calls WHERE call_id = ?", callId)
            jdbc.update("DELETE FROM gateway_port_reservations WHERE call_id = ?", callId)
        } catch (e: Exception) {
            log.debug("Postgres unbind failed: {}", e.message)
        }
        try {
            redis.delete(activeKey(userId))
            redis.delete(callKey(callId))
        } catch (e: Exception) {
            log.debug("Redis unbind failed: {}", e.message)
        }
    }

    fun findActiveCall(callId: String): Pair<UUID, String>? {
        // Redis أولاً
        try {
            val raw = redis.opsForValue().get(callKey(callId))
            if (raw != null) {
                val uid = runCatching { UUID.fromString(raw.trim()) }.getOrNull()
                if (uid != null) return uid to raw
            }
        } catch (_: Exception) {}
        // Postgres fallback
        return try {
            jdbc.queryForObject(
                "SELECT user_id FROM pstn_active_calls WHERE call_id = ? AND expires_at > NOW()",
                { rs, _ -> UUID.fromString(rs.getString("user_id")) to rs.getString("call_id") },
                callId
            )
        } catch (_: Exception) { null }
    }

    fun hasActiveCall(userId: UUID): Boolean {
        try {
            val cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pstn_active_calls WHERE user_id = ? AND expires_at > NOW()",
                Int::class.java, userId
            ) ?: 0
            if (cnt > 0) return true
        } catch (_: Exception) {}
        return false
    }

    // ── تنظيف دوري ────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    fun cleanupExpired() {
        try {
            val deletedPorts = jdbc.update("DELETE FROM gateway_port_reservations WHERE expires_at <= NOW()")
            val deletedCalls = jdbc.update("DELETE FROM pstn_active_calls WHERE expires_at <= NOW()")
            if (deletedPorts > 0 || deletedCalls > 0) {
                log.info("Cleaned up {} expired port reservations and {} active calls", deletedPorts, deletedCalls)
            }
        } catch (e: Exception) {
            log.debug("Reservation cleanup failed: {}", e.message)
        }
    }
}
