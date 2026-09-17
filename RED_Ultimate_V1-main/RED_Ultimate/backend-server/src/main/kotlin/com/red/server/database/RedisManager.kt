package com.red.server.database

import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 🗄️ YOUNES Sovereign Redis Manager
 * الكاش والمؤقتات — البيانات السريعة الزوالة
 *
 * P9 — عقد مفاتيح Redis (كل مفتاح له TTL صريح ما لم تُذكر دورة حياة بديلة):
 * ┌─────────────────────────────────┬────────────────────────────┬─────────┐
 * │ النمط                           │ الوصف                      │ TTL     │
 * ├─────────────────────────────────┼────────────────────────────┼─────────┤
 * │ red:presence:index              │ ZSET حضور حي redId←ms      │ بلا TTL (تطهير Score 5m)│
 * │ red:online                      │ Set المتصلين (حياة=سوكت)   │ بلا (سوكت)│
 * │ red:typing:{conv}:{user}        │ "يكتب الآن"                │ 5s      │
 * │ red:ratelimit:{scope}:{key}     │ عداد Rate Limit (Lua ذري)  │ نافذة   │
 * │ red:session:{tokenHash}         │ كاش جلسة Refresh (احتياطي) │ =انتهاء │
 * │ red:notify:unread:{userId}      │ عداد غير المقروءة          │ 30d منزلق│
 * │ red:notify:queue:{userId}       │ إشعارات مؤقتة (≤100)       │ 30d     │
 * │ red:notify:data:{notifId}       │ هاش إشعار داخل التطبيق     │ 30d     │
 * │ red:notify:prefs:{userId}       │ تفضيلات الإشعارات (هاش)    │ 30d منزلق│
 * │ red:call:signaling:{callId}     │ إشارات WebRTC مؤقتة        │ 30m     │
 * │ red:call:pending:{redId}        │ عروض مكالمات للسحب (hash)  │ 120s    │
 * │ red:media:grant:{key}:{grantee} │ صلاحية وسائط مؤقتة         │ 1h      │
 * │ red:search:recent:{userId}      │ آخر 20 بحثًا               │ 30d     │
 * │ red:metrics:realtime            │ مقاييس حية (hash)          │ 48h منزلق│
 * └─────────────────────────────────┴────────────────────────────┴─────────┘
 * حضور ZSET/Set يُدار مباشرة عبر StringRedisTemplate (نمط معتمد في 8 ملفات).
 * القنوات: red:messages:{redId} للإيصال الفوري، red:typing لإشارات الكتابة.
 * ملاحظة P9: حُذفت المكررات الميتة (red:seq، red:presence:{u}، red:status،
 * red:otp، red:device:cert) — التسلسل في Mongo conversation_sequences حصرًا.
 * (ملاحظة: لا تكتب شارحة-نجمة هنا أبدًا — Kotlin يعشّش التعليقات الكتلية فيبتلع الملف.)
 */
@Component
class RedisManager(private val redis: StringRedisTemplate) {

    // عدّاد ذرّي لـ Rate Limit: INCR ثم PEXPIRE عند أول زيادة فقط — لا منافسة بين عمليتين
    private val rateLimitScript: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent()
        )
        resultType = Long::class.javaObjectType
    }

    // SCAN تدريجي بدل KEYS — لا يجمّد Redis على مجموعات كبيرة
    private fun scanKeys(pattern: String, count: Int = 200): Set<String> {
        return try {
            redis.execute { connection ->
                val cursor = connection.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(count.toLong())
                        .build()
                )
                val keys = mutableSetOf<String>()
                while (cursor.hasNext()) {
                    keys.add(String(cursor.next(), Charsets.UTF_8))
                }
                keys
            } ?: emptySet()
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(RedisManager::class.java)
                .warn("Redis SCAN failed for pattern '$pattern': ${e.message}")
            emptySet()
        }
    }

    // ═══════════════════════5═══════════════════
    // ✍️ "يكتب الآن"
    // ══════════════════════════════════════════

    fun setTyping(userId: String, conversationId: String) {
        redis.convertAndSend("red:typing", "$conversationId:$userId")
        // Also store as a short-lived key
        redis.opsForValue().set("red:typing:$conversationId:$userId", "1", 5, TimeUnit.SECONDS)
    }

    fun isTyping(conversationId: String, userId: String): Boolean {
        return redis.hasKey("red:typing:$conversationId:$userId")
    }

    fun getTypingUsers(conversationId: String): Set<String> {
        val keys = scanKeys("red:typing:$conversationId:*")
        return keys.map { it.substringAfterLast(":") }.toSet()
    }

    // ══════════════════════════════════════════
    // 🚦 Rate Limiting
    // ══════════════════════════════════════════

    fun checkRateLimit(key: String, maxRequests: Int, windowSeconds: Long): Boolean {
        // INCR + PEXPIRE ذرّية في سكربت واحد — لا نافذة يمكن تجاوزها بطلبين متزامنين
        // ⚠️ StringRedisTemplate يتطلّب ARGV كـ String — Long يُسبب ClassCastException
        val current = redis.execute(
            rateLimitScript,
            listOf("red:ratelimit:$key"),
            (windowSeconds * 1000).toString()
        ) ?: 1L
        return current <= maxRequests
    }

    fun getRateLimitRemaining(key: String, maxRequests: Int): Int {
        val current = redis.opsForValue().get("red:ratelimit:$key")?.toLongOrNull() ?: 0L
        return maxOf(0, maxRequests - current.toInt())
    }

    // ══════════════════════════════════════════
    // 🔐 الجلسات و OTP
    // ══════════════════════════════════════════

    fun storeRefreshSession(tokenHash: String, userId: String, deviceId: String, expiresInSeconds: Long) {
        redis.opsForValue().set("red:session:$tokenHash", "$userId:$deviceId", expiresInSeconds, TimeUnit.SECONDS)
    }

    fun getRefreshSession(tokenHash: String): String? {
        return redis.opsForValue().get("red:session:$tokenHash")
    }

    fun revokeRefreshSession(tokenHash: String) {
        redis.delete("red:session:$tokenHash")
    }

    // ══════════════════════════════════════════
    // 🔔 الإشعارات المؤقتة
    // ══════════════════════════════════════════

    fun incrementUnreadNotifications(userId: String): Long {
        val key = "red:notify:unread:$userId"
        val v = redis.opsForValue().increment(key) ?: 1L
        redis.expire(key, 30, TimeUnit.DAYS) // P9: TTL منزلق — لا عدادات خالدة
        return v
    }

    fun getUnreadNotificationCount(userId: String): Long {
        return redis.opsForValue().get("red:notify:unread:$userId")?.toLongOrNull() ?: 0L
    }

    fun resetUnreadNotifications(userId: String) {
        redis.opsForValue().set("red:notify:unread:$userId", "0", 30, TimeUnit.DAYS) // P9: TTL
    }

    fun pushNotification(userId: String, notificationJson: String) {
        val key = "red:notify:queue:$userId"
        redis.opsForList().leftPush(key, notificationJson)
        // Trim to last 100
        redis.opsForList().trim(key, 0, 99)
        redis.expire(key, 30, TimeUnit.DAYS) // P9: TTL
    }

    fun getRecentNotifications(userId: String, count: Long = 20): List<String> {
        return redis.opsForList().range("red:notify:queue:$userId", 0, count - 1) ?: emptyList()
    }

    // ══════════════════════════════════════════
    // 📞 إشارات المكالمات
    // ══════════════════════════════════════════

    fun cacheCallSignal(callId: String, signalJson: String) {
        redis.opsForValue().set("red:call:signaling:$callId", signalJson, 30, TimeUnit.MINUTES)
    }

    fun getCallSignal(callId: String): String? {
        return redis.opsForValue().get("red:call:signaling:$callId")
    }

    fun removeCallSignal(callId: String) {
        redis.delete("red:call:signaling:$callId")
    }

    // ══════════════════════════════════════════
    // 📞 عروض المكالمات المعلّقة (صندوق بريد الإيقاظ)
    // ══════════════════════════════════════════

    // تخزين ذرّي: سقف لكل مستقبل + TTL أصلي في عملية واحدة.
    // ملاحظة sliding-EXPIRE: الـ EXPIRE على مستوى مفتاح الهاش كله (شبكة أمان GC فقط)،
    // والانتهاء الحقيقي لكل عرض هو createdAtMs+ttlSeconds داخل القيمة ويفحصه القارئ
    // عند السحب (410 GONE بعد الانتهاء) — فلا يعتمد أي عرض على TTL المفتاح وحده.
    // لذلك لا يُقصَّر TTL أبداً بعرض قصير لاحق: نُبقي max(الحالي، الجديد).
    private val storePendingOfferScript: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local key = KEYS[1]
            local field = ARGV[1]
            local value = ARGV[2]
            local ttl = tonumber(ARGV[3])
            local cap = tonumber(ARGV[4])
            if redis.call('HEXISTS', key, field) == 0 then
                if redis.call('HLEN', key) >= cap then
                    return 0
                end
            end
            redis.call('HSET', key, field, value)
            local cur = redis.call('TTL', key)
            if cur < 0 or ttl > cur then
                redis.call('EXPIRE', key, ttl)
            end
            return 1
            """.trimIndent()
        )
        resultType = Long::class.javaObjectType
    }

    // سحب + إزالة ذرّية: لا يسحب جهازان العرض نفسه
    private val takePendingOfferScript: DefaultRedisScript<String> = DefaultRedisScript<String>().apply {
        setScriptText(
            """
            local key = KEYS[1]
            local field = ARGV[1]
            if field == '' then
                local ks = redis.call('HKEYS', key)
                if #ks == 0 then
                    return nil
                end
                field = ks[1]
            end
            local value = redis.call('HGET', key, field)
            if value then
                redis.call('HDEL', key, field)
            end
            return value
            """.trimIndent()
        )
        resultType = String::class.java
    }

    /**
     * يخزّن عرض مكالمة للسحب لاحقاً عند اتصال المستقبل (Path 2 of Multi-Path Delivery).
     *
     * hash لكل مستقبل: callId → JSON العرض، بـ TTL أصلي = ttlSeconds. فالمخزن يدوم عبر
     * إعادة تشغيل الخادم ويُشارَك بين النسخ خلف موازن — بخلاف خريطة الذاكرة السابقة التي
     * كانت تُفقد كل عرض معلّق عند أي نشر جديد (فتضيع رنّة من تطبيق مقتول بعد وصولها).
     * يعيد false عند بلوغ سقف المستقبل — لا إسقاط صامت لعروض قائمة.
     *
     * عقد الانتهاء: القيمة تحمل expiresAt الضمني (createdAtMs + ttlSeconds*1000) وهو
     * المرجع الوحيد لانتهاء العرض — القارئ يفحصه عند السحب ويرفض المنتهي. أما EXPIRE
     * على المفتاح فشبكة أمان GC للحقل كله ولا يُقصَّر بعرض قصير (يُحفَظ max TTL).
     */
    fun storePendingCallOffer(targetRedId: String, callId: String, offerJson: String, ttlSeconds: Long): Boolean {
        val stored = redis.execute(
            storePendingOfferScript,
            listOf(pendingOfferKey(targetRedId)),
            callId, offerJson, ttlSeconds.toString(), PENDING_OFFER_PER_USER_CAP.toString()
        ) ?: 0L
        return stored == 1L
    }

    /** يسحب عرضاً ويزيله ذرّياً (بـ callId، أو أي عرض للمستقبل عند null). */
    fun takePendingCallOffer(targetRedId: String, callId: String?): String? =
        redis.execute(takePendingOfferScript, listOf(pendingOfferKey(targetRedId)), callId.orEmpty())

    fun pendingCallOfferCount(targetRedId: String): Long =
        redis.opsForHash<String, String>().size(pendingOfferKey(targetRedId)) ?: 0L

    private fun pendingOfferKey(targetRedId: String) = "red:call:pending:$targetRedId"

    // ══════════════════════════════════════════
    // 🖼️ صلاحيات الوسائط المؤقتة
    // ══════════════════════════════════════════

    fun grantMediaAccess(objectKey: String, granteeId: String, expiresInSeconds: Long = 3600) {
        redis.opsForValue().set("red:media:grant:$objectKey:$granteeId", "1", expiresInSeconds, TimeUnit.SECONDS)
    }

    fun hasMediaAccess(objectKey: String, granteeId: String): Boolean {
        return redis.hasKey("red:media:grant:$objectKey:$granteeId")
    }

    fun revokeMediaAccess(objectKey: String, granteeId: String) {
        redis.delete("red:media:grant:$objectKey:$granteeId")
    }

    // ══════════════════════════════════════════
    // 🔍 عمليات البحث الأخيرة
    // ══════════════════════════════════════════

    fun addRecentSearch(userId: String, query: String) {
        val key = "red:search:recent:$userId"
        redis.opsForList().leftPush(key, query)
        redis.opsForList().trim(key, 0, 19) // آخر 20
        redis.expire(key, 30, TimeUnit.DAYS) // P9: TTL
    }

    fun getRecentSearches(userId: String): List<String> {
        return redis.opsForList().range("red:search:recent:$userId", 0, 19) ?: emptyList()
    }

    fun clearRecentSearches(userId: String) {
        redis.delete("red:search:recent:$userId")
    }

    // ══════════════════════════════════════════
    // 📊 مقاييس حية
    // ══════════════════════════════════════════

    fun incrementMetric(metric: String, delta: Long = 1): Long {
        val v = redis.opsForHash<String, String>().increment("red:metrics:realtime", metric, delta) ?: delta
        redis.expire("red:metrics:realtime", 48, TimeUnit.HOURS) // P9: TTL منزلق
        return v
    }

    fun getMetrics(): Map<String, String> {
        return redis.opsForHash<String, String>().entries("red:metrics:realtime")
    }

    fun setMetric(metric: String, value: String) {
        redis.opsForHash<String, String>().put("red:metrics:realtime", metric, value)
        redis.expire("red:metrics:realtime", 48, TimeUnit.HOURS) // P9: TTL منزلق
    }

    // ══════════════════════════════════════════
    // 🧹 تنظيف
    // ══════════════════════════════════════════

    fun deleteKey(key: String) {
        redis.delete(key)
    }

    fun cleanUserData(userId: String) {
        // P9: مفاتيح حية فقط — الحضور ZSET يُزال بـ ZREM لا DEL.
        // تُجمَع معرّفات الإشعارات *قبل* حذف القوائم لحذف هاشات البيانات التابعة
        // (red:notify:data:/legacy notif:data:) — وإلا تيتّمت بلا TTL مرجعي.
        val notifIds = mutableSetOf<String>()
        runCatching {
            redis.opsForList().range("red:notify:queue:$userId", 0, -1)?.let { notifIds += it }
            redis.opsForList().range("notifications:$userId", 0, -1)?.let { notifIds += it } // legacy
        }
        // مفاتيح المتجهات مبعثرة لكل جهاز — SCAN تدريجي بدل KEYS.
        val vectorKeys = runCatching { scanKeys("red:vector:$userId:*") }.getOrDefault(emptySet())
        redis.delete(
            listOf(
                "red:notify:unread:$userId",
                "red:notify:queue:$userId",
                "red:notify:prefs:$userId",
                "notifications:$userId", // legacy
                "notifications:unread:$userId", // legacy
                "notifications:prefs:$userId", // legacy
                "red:search:recent:$userId",
                "user:status:$userId",
                "user:privacy:$userId",
                "contacts:$userId",
                "red:vector-index:$userId"
            )
        )
        if (notifIds.isNotEmpty()) {
            val dataKeys = notifIds.flatMap { listOf("red:notify:data:$it", "notif:data:$it") } // legacy
            runCatching { redis.delete(dataKeys) }
        }
        if (vectorKeys.isNotEmpty()) runCatching { redis.delete(vectorKeys) }
        // عضوية عكسية: إزالة best-effort من قوائم جهات اتصال الآخرين.
        runCatching {
            scanKeys("contacts:*").forEach { redis.opsForSet().remove(it, userId) }
        }
        redis.opsForSet().remove("red:online", userId)
        redis.opsForZSet().remove("red:presence:index", userId)
    }

    companion object {
        /** سقف عروض المكالمات المعلّقة لكل مستقبل — يمنع مستخدماً واحداً من نفخ الذاكرة. */
        private const val PENDING_OFFER_PER_USER_CAP = 50
    }
}
