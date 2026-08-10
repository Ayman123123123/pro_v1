package com.red.server.database

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 🗄️ YOUNES Sovereign Redis Manager
 * الكاش والمؤقتات — البيانات السريعة الزوالة
 *
 * أنماط المفاتيح:
 * ┌─────────────────────────────────┬──────────────────────────────────────┐
 * │ النمط                           │ الوصف                                 │
 * ├─────────────────────────────────┼──────────────────────────────────────┤
 * │ red:seq:{conversationId}        │ التسلسل الرقمي للمحادثة              │
 * │ red:presence:{userId}           │ حالة الاتصال (ONLINE/OFFLINE/BUSY)   │
 * │ red:status:{userId}             │ الحالة التفصيلية (JSON)              │
 * │ red:typing:{conversationId}     │ "يكتب الآن" (set مع TTL)             │
 * │ red:online                      │ Set: المستخدمون المتصلون              │
 * │ red:ratelimit:{scope}:{key}     │ عداد Rate Limit                       │
 * │ red:session:{tokenHash}         │ جلسة Refresh Token                    │
 * │ red:otp:{userId}                │ رمز التحقق OTP                       │
 * │ red:device:cert:{deviceId}      │ شهادة الجهاز المؤقتة                  │
 * │ red:notify:unread:{userId}      │ عداد الإشعارات غير المقروءة          │
 * │ red:notify:queue:{userId}       │ قائمة إشعارات مؤقتة                   │
 * │ red:call:signaling:{callId}     │ إشارات WebRTC مؤقتة                  │
 * │ red:dinstar:status:{gatewayId}  │ حالة بوابة Dinstar                    │
 * │ red:dinstar:ports:{gatewayId}   │ منافذ Dinstar الحالية (hash)          │
 * │ red:dinstar:loadbalancer        │ Round-robin counter                   │
 * │ red:media:grant:{objectKey}     │ صلاحية وسائط مؤقتة                    │
 * │ red:search:recent:{userId}      │ عمليات البحث الأخيرة (list)           │
 * │ red:backup:progress:{userId}    │ تقدم النسخ الاحتياطي                  │
 * │ red:metrics:realtime            │ مقاييس حية (hash)                     │
 * └─────────────────────────────────┴──────────────────────────────────────┘
 */
@Component
class RedisManager(private val redis: StringRedisTemplate) {

    // ══════════════════════════════════════════
    // 📊 التسلسل الرقمي (ACID-like counter)
    // ══════════════════════════════════════════

    fun incrementSequence(conversationId: String): Long {
        return redis.opsForValue().increment("red:seq:$conversationId") ?: 1L
    }

    fun incrementGroupSequence(groupId: String): Long {
        return redis.opsForValue().increment("red:seq:group:$groupId") ?: 1L
    }

    // ══════════════════════════════════════════
    // 🟢 حالة الاتصال (Presence)
    // ══════════════════════════════════════════

    fun setPresence(userId: String, status: String = "ONLINE") {
        redis.opsForValue().set("red:presence:$userId", status, 5, TimeUnit.MINUTES)
        if (status == "ONLINE") {
            redis.opsForSet().add("red:online", userId)
        } else {
            redis.opsForSet().remove("red:online", userId)
        }
    }

    fun getPresence(userId: String): String? {
        return redis.opsForValue().get("red:presence:$userId")
    }

    fun removePresence(userId: String) {
        redis.delete("red:presence:$userId")
        redis.opsForSet().remove("red:online", userId)
    }

    fun getOnlineUsers(): Set<String> {
        return redis.opsForSet().members("red:online") ?: emptySet()
    }

    fun isUserOnline(userId: String): Boolean {
        return redis.opsForSet().isMember("red:online", userId) == true
    }

    // ══════════════════════════════════════════
    // 🔴 الحالة التفصيلية (Status)
    // ══════════════════════════════════════════

    fun setUserStatus(userId: String, type: String, customText: String?, visibleTo: String) {
        val key = "red:status:$userId"
        redis.opsForHash<String, String>().apply {
            put(key, "type", type)
            put(key, "customText", customText ?: "")
            put(key, "visibleTo", visibleTo)
            put(key, "updatedAt", System.currentTimeMillis().toString())
        }
        redis.expire(key, 24, TimeUnit.HOURS)
    }

    fun getUserStatus(userId: String): Map<String, String> {
        return redis.opsForHash<String, String>().entries("red:status:$userId")
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
        val keys = redis.keys("red:typing:$conversationId:*") ?: emptySet()
        return keys.map { it.substringAfterLast(":") }.toSet()
    }

    // ══════════════════════════════════════════
    // 🚦 Rate Limiting
    // ══════════════════════════════════════════

    fun checkRateLimit(key: String, maxRequests: Int, windowSeconds: Long): Boolean {
        val current = redis.opsForValue().increment("red:ratelimit:$key") ?: 1L
        if (current == 1L) {
            redis.expire("red:ratelimit:$key", windowSeconds, TimeUnit.SECONDS)
        }
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

    fun storeOtp(userId: String, code: String, expiresInSeconds: Long = 300) {
        redis.opsForValue().set("red:otp:$userId", code, expiresInSeconds, TimeUnit.SECONDS)
    }

    fun verifyOtp(userId: String, code: String): Boolean {
        val stored = redis.opsForValue().get("red:otp:$userId")
        if (stored == code) {
            redis.delete("red:otp:$userId")
            return true
        }
        return false
    }

    // ══════════════════════════════════════════
    // 🔔 الإشعارات المؤقتة
    // ══════════════════════════════════════════

    fun incrementUnreadNotifications(userId: String): Long {
        return redis.opsForValue().increment("red:notify:unread:$userId") ?: 1L
    }

    fun getUnreadNotificationCount(userId: String): Long {
        return redis.opsForValue().get("red:notify:unread:$userId")?.toLongOrNull() ?: 0L
    }

    fun resetUnreadNotifications(userId: String) {
        redis.opsForValue().set("red:notify:unread:$userId", "0")
    }

    fun pushNotification(userId: String, notificationJson: String) {
        redis.opsForList().leftPush("red:notify:queue:$userId", notificationJson)
        // Trim to last 100
        redis.opsForList().trim("red:notify:queue:$userId", 0, 99)
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
    // 📡 حالة0 Dinstar حالة البوابة
    // ══════════════════════════════════════════

    fun cacheDinstarStatus(gatewayId: String, statusJson: String) {
        redis.opsForValue().set("red:dinstar:status:$gatewayId", statusJson, 2, TimeUnit.MINUTES)
    }

    fun getDinstarStatus(gatewayId: String): String? {
        return redis.opsForValue().get("red:dinstar:status:$gatewayId")
    }

    fun cacheDinstarPorts(gatewayId: String, portIndex: Int, portJson: String) {
        redis.opsForHash<String, String>().put("red:dinstar:ports:$gatewayId", portIndex.toString(), portJson)
    }

    fun getDinstarPorts(gatewayId: String): Map<String, String> {
        return redis.opsForHash<String, String>().entries("red:dinstar:ports:$gatewayId")
    }

    fun incrementLoadBalancerCounter(): Long {
        return redis.opsForValue().increment("red:dinstar:loadbalancer") ?: 1L
    }

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
        redis.opsForList().leftPush("red:search:recent:$userId", query)
        redis.opsForList().trim("red:search:recent:$userId", 0, 19) // آخر 20
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
        return redis.opsForHash<String, String>().increment("red:metrics:realtime", metric, delta) ?: delta
    }

    fun getMetrics(): Map<String, String> {
        return redis.opsForHash<String, String>().entries("red:metrics:realtime")
    }

    fun setMetric(metric: String, value: String) {
        redis.opsForHash<String, String>().put("red:metrics:realtime", metric, value)
    }

    // ══════════════════════════════════════════
    // 🧹 تنظيف
    // ══════════════════════════════════════════

    fun cleanUserData(userId: String) {
        val patterns = listOf(
            "red:presence:$userId",
            "red:status:$userId",
            "red:notify:unread:$userId",
            "red:notify:queue:$userId",
            "red:search:recent:$userId",
            "red:otp:$userId"
        )
        redis.delete(patterns)
        redis.opsForSet().remove("red:online", userId)
    }
}
