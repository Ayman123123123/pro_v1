package com.red.server.notification

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * 🔔 YOUNES Notification Service
 * إدارة الإشعارات باستخدام Redis Lists + Hashes
 *
 * اسم bean صريح: يوجد NotificationService آخر (واجهة بريد/SMS) في حزمة services،
 * وبلا اسم صريح يتصادم الاسم الافتراضي ويرفض Spring الإقلاع بـ ConflictingBeanDefinition.
 */
@Service("inAppNotificationService")
class NotificationService(
    private val redis: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val NOTIF_LIST_PREFIX = "notifications:"
        private const val NOTIF_DATA_PREFIX = "notif:data:"
        private const val UNREAD_PREFIX = "notifications:unread:"
        private const val PREFS_PREFIX = "notifications:prefs:"
        private const val MAX_NOTIFICATIONS = 500L
        // TTL التسليم: مفاتيح الإشعارات لا تبقى للأبد للمستخدمين الخاملين
        private const val NOTIF_TTL_DAYS = 30L
    }

    /**
     * إنشاء إشعار جديد — يُستدعى من كل الأنظمة (رسائل، مكالمات، مجموعات، ...)
     */
    fun createNotification(
        userId: String,
        type: String,
        title: String,
        body: String,
        senderId: String? = null,
        senderName: String? = null,
        threadId: String? = null
    ): NotificationDto {
        // ── تحقق من تفضيلات الإشعارات ──
        val prefs = getPreferences(userId)
        val typeEnabled = when (type.lowercase()) {
            "message", "messages" -> prefs.messages
            "call", "calls" -> prefs.calls
            "group", "groups" -> prefs.groups
            "story", "stories" -> prefs.stories
            "live" -> prefs.live
            "system" -> prefs.system
            "security" -> prefs.security
            else -> true
        }
        if (!typeEnabled) {
            log.debug("Notification suppressed for {} type={}: user preference disabled", userId, type)
            return NotificationDto("", type, title, body, senderId, senderName, threadId, false, Instant.now())
        }

        // ── تحقق من ساعات الصمت ──
        if (prefs.quietHoursEnabled && prefs.quietHoursStart != null && prefs.quietHoursEnd != null) {
            try {
                val now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Aden"))
                val start = java.time.LocalTime.parse(prefs.quietHoursStart)
                val end = java.time.LocalTime.parse(prefs.quietHoursEnd)
                val inQuietHours = if (start.isBefore(end) || start == end) {
                    now.isAfter(start) && now.isBefore(end)
                } else {
                    now.isAfter(start) || now.isBefore(end)
                }
                if (inQuietHours) {
                    log.debug("Notification suppressed for {} during quiet hours ({}-{})", userId, prefs.quietHoursStart, prefs.quietHoursEnd)
                    return NotificationDto("", type, title, body, senderId, senderName, threadId, false, Instant.now())
                }
            } catch (_: Exception) { }
        }
        val id = UUID.randomUUID().toString()
        val now = Instant.now()

        // تخزين بيانات الإشعار
        val dataKey = NOTIF_DATA_PREFIX + id
        redis.opsForHash<String, String>().apply {
            put(dataKey, "id", id)
            put(dataKey, "userId", userId)
            put(dataKey, "type", type)
            put(dataKey, "title", title)
            put(dataKey, "body", body)
            put(dataKey, "senderId", senderId ?: "")
            put(dataKey, "senderName", senderName ?: "")
            put(dataKey, "threadId", threadId ?: "")
            put(dataKey, "isRead", "false")
            put(dataKey, "createdAt", now.toString())
        }

        // إضافة للقائمة (الأحدث أولاً)
        val listKey = NOTIF_LIST_PREFIX + userId
        redis.opsForList().leftPush(listKey, id)
        // TTL: حتى لا تتسرب مفاتيح الخاملين للأبد
        runCatching {
            val ttl = java.util.concurrent.TimeUnit.DAYS.toSeconds(NOTIF_TTL_DAYS)
            redis.expire(dataKey, ttl, java.util.concurrent.TimeUnit.SECONDS)
            redis.expire(listKey, ttl, java.util.concurrent.TimeUnit.SECONDS)
            redis.expire(UNREAD_PREFIX + userId, ttl, java.util.concurrent.TimeUnit.SECONDS)
        }

        // تقليم القائمة
        val size = redis.opsForList().size(listKey) ?: 0
        if (size > MAX_NOTIFICATIONS) {
            // حذف الأقدم
            val removed = redis.opsForList().range(listKey, MAX_NOTIFICATIONS, -1) ?: emptyList()
            removed.forEach { oldId ->
                redis.delete(NOTIF_DATA_PREFIX + oldId)
            }
            redis.opsForList().trim(listKey, 0, MAX_NOTIFICATIONS - 1)
        }

        // تحديث عداد غير المقروء
        redis.opsForValue().increment(UNREAD_PREFIX + userId)

        log.debug("Notification created for {}: {} - {}", userId, type, title)

        return NotificationDto(id, type, title, body, senderId, senderName, threadId, false, now)
    }

    /**
     * جلب الإشعارات مع ترقيم الصفحات.
     * التسليم: الفلترة حسب النوع قبل التقطيع — الفلترة بعد range كانت تُرجع صفحات
     * ناقصة/فارغة عند تصفية نوع نادر. نمسح نافذة موسعة (حتى 10×) لملء الصفحة.
     */
    fun getNotifications(userId: String, page: Int, size: Int, type: String?): List<NotificationDto> {
        val safeSize = size.coerceIn(1, 100)
        val safePage = page.coerceAtLeast(0)
        val listKey = NOTIF_LIST_PREFIX + userId
        if (type == null) {
            val start = (safePage * safeSize).toLong()
            val end = start + safeSize - 1
            val ids = redis.opsForList().range(listKey, start, end) ?: return emptyList()
            return ids.mapNotNull { id -> readNotification(id) }
        }
        // مسار مفلتر: اجمع حتى تملأ الصفحة أو تنفد القائمة (سقف 10× لتجنب مسح لا نهائي)
        val need = safeSize
        val skip = safePage * safeSize
        val found = mutableListOf<NotificationDto>()
        var scanned = 0L
        var skipped = 0
        val batch = 100L
        while (found.size < need) {
            val ids = redis.opsForList().range(listKey, scanned, scanned + batch - 1) ?: break
            if (ids.isEmpty()) break
            scanned += ids.size
            for (id in ids) {
                val notif = readNotification(id) ?: continue
                if (notif.type != type) continue
                if (skipped < skip) { skipped++; continue }
                found.add(notif)
                if (found.size >= need) break
            }
            if (ids.size < batch) break
            if (scanned > 1000 + skip) break
        }
        return found
    }

    private fun readNotification(id: String): NotificationDto? {
        val data = redis.opsForHash<String, String>().entries(NOTIF_DATA_PREFIX + id)
        if (data.isEmpty()) return null
        return NotificationDto(
            id = data["id"] ?: id,
            type = data["type"] ?: "UNKNOWN",
            title = data["title"] ?: "",
            body = data["body"] ?: "",
            senderId = data["senderId"]?.takeIf { it.isNotBlank() },
            senderName = data["senderName"]?.takeIf { it.isNotBlank() },
            threadId = data["threadId"]?.takeIf { it.isNotBlank() },
            isRead = data["isRead"] == "true",
            createdAt = parseInstant(data["createdAt"])
        )
    }

    fun getUnreadCount(userId: String): Long {
        return redis.opsForValue().get(UNREAD_PREFIX + userId)?.toLongOrNull() ?: 0
    }

    fun markAsRead(userId: String, notificationId: String) {
        val data = redis.opsForHash<String, String>().entries(NOTIF_DATA_PREFIX + notificationId)
        if (data.isNotEmpty() && data["userId"] == userId && data["isRead"] != "true") {
            redis.opsForHash<String, String>().put(NOTIF_DATA_PREFIX + notificationId, "isRead", "true")
            val left = redis.opsForValue().decrement(UNREAD_PREFIX + userId) ?: 0
            // التسليم: لا عداد سالب بعد قراءات مكررة/متسابقة
            if (left < 0) redis.opsForValue().set(UNREAD_PREFIX + userId, "0")
        }
    }

    fun markAllAsRead(userId: String) {
        val listKey = NOTIF_LIST_PREFIX + userId
        val ids = redis.opsForList().range(listKey, 0, -1) ?: return

        ids.forEach { id ->
            redis.opsForHash<String, String>().put(NOTIF_DATA_PREFIX + id, "isRead", "true")
        }

        redis.opsForValue().set(UNREAD_PREFIX + userId, "0")
    }

    fun delete(userId: String, notificationId: String) {
        val data = redis.opsForHash<String, String>().entries(NOTIF_DATA_PREFIX + notificationId)
        if (data.isNotEmpty() && data["userId"] != userId) {
            // لا يجوز للمستخدم حذف إشعار لا يملكه.
            return
        }
        redis.opsForList().remove(NOTIF_LIST_PREFIX + userId, 1, notificationId)
        redis.delete(NOTIF_DATA_PREFIX + notificationId)
    }

    // ─── التفضيلات ───

    fun getPreferences(userId: String): NotificationPreferences {
        val key = PREFS_PREFIX + userId
        val data = redis.opsForHash<String, String>().entries(key)

        return NotificationPreferences(
            messages = data["messages"]?.toBoolean() ?: true,
            calls = data["calls"]?.toBoolean() ?: true,
            groups = data["groups"]?.toBoolean() ?: true,
            stories = data["stories"]?.toBoolean() ?: true,
            live = data["live"]?.toBoolean() ?: true,
            system = data["system"]?.toBoolean() ?: true,
            security = data["security"]?.toBoolean() ?: true,
            quietHoursEnabled = data["quietHoursEnabled"]?.toBoolean() ?: false,
            quietHoursStart = data["quietHoursStart"],
            quietHoursEnd = data["quietHoursEnd"]
        )
    }

    fun updatePreferences(userId: String, prefs: NotificationPreferences): NotificationPreferences {
        val key = PREFS_PREFIX + userId
        val ops = redis.opsForHash<String, String>()

        ops.put(key, "messages", prefs.messages.toString())
        ops.put(key, "calls", prefs.calls.toString())
        ops.put(key, "groups", prefs.groups.toString())
        ops.put(key, "stories", prefs.stories.toString())
        ops.put(key, "live", prefs.live.toString())
        ops.put(key, "system", prefs.system.toString())
        ops.put(key, "security", prefs.security.toString())
        ops.put(key, "quietHoursEnabled", prefs.quietHoursEnabled.toString())
        prefs.quietHoursStart?.let { ops.put(key, "quietHoursStart", it) }
        prefs.quietHoursEnd?.let { ops.put(key, "quietHoursEnd", it) }

        return prefs
    }

    private fun parseInstant(value: String?): Instant {
        return try { value?.let { Instant.parse(it) } ?: Instant.now() } catch (_: Exception) { Instant.now() }
    }
}
