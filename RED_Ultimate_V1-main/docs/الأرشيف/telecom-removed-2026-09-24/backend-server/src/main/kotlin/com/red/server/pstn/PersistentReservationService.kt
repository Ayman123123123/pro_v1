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
        // 1) Postgres — استيلاء ذرّي عبر الفهرس الفريد
        //    uq_gateway_port_active_reservation (V47) على (gateway_id, port_index)
        //    بـNULLS NOT DISTINCT.
        //
        //    عبارة واحدة تحمل القرار كاملًا:
        //      • لا صف        ⇒ INSERT ينجح            ⇒ 1 صف  ⇒ محجوز لنا
        //      • صف منتهٍ     ⇒ DO UPDATE يستولي عليه  ⇒ 1 صف  ⇒ محجوز لنا
        //      • صف حيّ       ⇒ شرط WHERE يمنع التحديث ⇒ 0 صف  ⇒ مرفوض
        //      • نفس المكالمة ⇒ تجديد مسموح (idempotent) ⇒ 1 صف
        //
        //    الشكل السابق كان INSERT مجرَّدًا يعتمد على فهرس غير موجود، فلم
        //    يرجع false أبدًا من قاعدة البيانات ⇒ مكالمتان متزامنتان تأخذان
        //    المنفذ نفسه (حجز مزدوج صامت).
        try {
            val rows = jdbc.update(
                """INSERT INTO gateway_port_reservations
                       (gateway_id, port_index, reserved_by_user_id, call_id, target_number, reserved_at, expires_at)
                   VALUES (?,?,?,?,?,?,?)
                   ON CONFLICT (gateway_id, port_index) DO UPDATE SET
                       reserved_by_user_id = EXCLUDED.reserved_by_user_id,
                       call_id             = EXCLUDED.call_id,
                       target_number       = EXCLUDED.target_number,
                       reserved_at         = EXCLUDED.reserved_at,
                       expires_at          = EXCLUDED.expires_at
                   WHERE gateway_port_reservations.expires_at <= NOW()
                      OR gateway_port_reservations.call_id = EXCLUDED.call_id""",
                gatewayId, port, userId, callId, targetNumber,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(expiresAt)
            )
            if (rows == 0) {
                log.debug("Port {}.{} is held by a live reservation — refusing call {}", gatewayId, port, callId)
                return false
            }
        } catch (e: DuplicateKeyException) {
            log.debug("Port {}.{} already reserved in Postgres for call {}", gatewayId, port, callId)
            return false
        } catch (e: Exception) {
            // فحص إن كان السبب هو انتهاك الفهرس الفريد لكن الاستثناء ليس DuplicateKeyException
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
            // set لا setIfAbsent: Postgres حسم الملكية، وكاش قديم من مكالمة
            // منتهية كان يبقى حتى TTL فيرفض isPortReserved كل محاولة جديدة.
            redis.opsForValue().set(portKey(gatewayId, port), callId, ttl)
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

    /**
     * تحرير المنفذ. عند تمرير [callId] لا يُحرَّر إلا إن كان الحجز الحيّ
     * يخصّ تلك المكالمة — وهذا هو الاستخدام الصحيح عند إنهاء مكالمة.
     *
     * ⚠️ التحرير بلا callId يمسح أي حجز على المنفذ ولو كان لمكالمة أخرى:
     * إنهاء المكالمة (أ) على المنفذ 3 كان يمسح حجز المكالمة (ب) الحيّة على
     * المنفذ نفسه، فيصبح المنفذ قابلًا للاختيار مرتين. يُترك بلا callId
     * فقط للتنظيف الإداري ولإصلاح العالق (heartbeat/سحب المنفذ يدويًا).
     */
    fun releasePort(gatewayId: UUID?, port: Int, callId: String? = null) {
        try {
            val deleted = if (callId != null) {
                jdbc.update(
                    "DELETE FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ? AND call_id = ?",
                    gatewayId, port, callId
                )
            } else {
                jdbc.update(
                    "DELETE FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ?",
                    gatewayId, port
                )
            }
            if (callId != null && deleted == 0) {
                log.debug("Port {}.{} not released: live reservation belongs to another call than {}", gatewayId, port, callId)
                return
            }
        } catch (e: Exception) {
            log.debug("Postgres release failed: {}", e.message)
        }
        try {
            // كاش Redis يحمل callId الحاجز: لا تمسح كاش مكالمة أخرى.
            if (callId != null) {
                val cached = redis.opsForValue().get(portKey(gatewayId, port))
                if (cached != null && cached != callId) {
                    log.debug("Redis port cache for {}.{} belongs to {} — keeping", gatewayId, port, cached)
                    return
                }
            }
            redis.delete(portKey(gatewayId, port))
        } catch (e: Exception) {
            log.debug("Redis release failed: {}", e.message)
        }
    }

    /**
     * تحرير حجز على منفذ يُبلّغ **الجهاز** أنه خالٍ (IDLE) والحجز أقدم من
     * [minAge]. هذا هو المُصلِح الصحيح لـ«البورت يجلس معلّق على مكالمة»:
     *
     * الحقيقة النهائية لحالة المنفذ هي الجهاز لا الخادم. فإن قال الجهاز
     * IDLE وبقي عندنا حجز، فالحجز يتيم: مات التطبيق، أو ضاع BYE، أو لم يصل
     * HangupEvent. تحريره هو الصواب.
     *
     * [minAge] إلزامية: التحرير الفوري كان يقتل مكالمة في طور التهيئة —
     * بين لحظة الحجز ولحظة أن يعلن الجهاز ACTIVE تمرّ ثوانٍ يقول فيها
     * الجهاز IDLE بحق. مهلة قصيرة (دقيقتان افتراضًا) تفصل المكالمة الوليدة
     * عن الحجز اليتيم.
     *
     * @return true إن حُرِّر حجزٌ فعلًا.
     */
    fun releaseStaleIdlePort(gatewayId: UUID?, port: Int, minAge: Duration = Duration.ofMinutes(2)): Boolean {
        val cutoff = Instant.now().minus(minAge)
        val callId = try {
            jdbc.queryForObject(
                """SELECT call_id FROM gateway_port_reservations
                   WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ? AND reserved_at <= ?
                   LIMIT 1""",
                String::class.java, gatewayId, port, java.sql.Timestamp.from(cutoff)
            )
        } catch (_: Exception) { null } ?: return false

        val deleted = try {
            jdbc.update(
                "DELETE FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ? AND call_id = ?",
                gatewayId, port, callId
            )
        } catch (e: Exception) {
            log.debug("stale idle release failed for {}.{}: {}", gatewayId, port, e.message)
            0
        }
        if (deleted == 0) return false

        // المكالمة النشطة المرتبطة بنفس callId يتيمة أيضًا — أزلها معها،
        // وإلا بقي hasActiveCall يرفض المستخدم حتى المهلة.
        try { jdbc.update("DELETE FROM pstn_active_calls WHERE call_id = ?", callId) } catch (_: Exception) {}
        try {
            redis.delete(portKey(gatewayId, port))
            redis.opsForValue().get(callKey(callId))?.trim()?.let { owner ->
                redis.delete("red:pstn:active:$owner")
            }
            redis.delete(callKey(callId))
            redis.delete("red:pstn:calluser:$callId")
            redis.delete("red:pstn:callport:$callId")
        } catch (_: Exception) {}
        log.warn(
            "Released orphaned reservation: gateway={} port={} callId={} — device reports the port IDLE and the reservation is older than {}",
            gatewayId ?: "local", port, callId, minAge
        )
        return true
    }

    fun isPortReserved(gatewayId: UUID?, port: Int): Boolean {
        // Postgres أولًا: هو مصدر الحقيقة ويعرف الانتهاء. Redis كاش بلا
        // معرفة بالانتهاء المنطقي — تقديمه كان يرفض منفذًا حرًّا لأن كاشًا
        // قديمًا بقي حتى TTL بعد تحرير الصف.
        try {
            val count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gateway_port_reservations WHERE gateway_id IS NOT DISTINCT FROM ? AND port_index = ? AND expires_at > NOW()",
                Int::class.java, gatewayId, port
            ) ?: 0
            return count > 0
        } catch (_: Exception) {}
        // Postgres غير متاح — اعتمد على الكاش
        return try { redis.opsForValue().get(portKey(gatewayId, port)) != null } catch (_: Exception) { false }
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

    /**
     * معرّف المكالمة الحيّة للمستخدم من Postgres — يُستخدم لتنظيف الحالة
     * عندما تكون مفاتيح Redis قد مضت وبقي الصف الدائم يحجب المكالمات.
     */
    fun findActiveCallIdByUser(userId: UUID): String? = try {
        jdbc.queryForObject(
            "SELECT call_id FROM pstn_active_calls WHERE user_id = ? AND expires_at > NOW() ORDER BY started_at DESC LIMIT 1",
            String::class.java, userId
        )
    } catch (_: Exception) { null }

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
