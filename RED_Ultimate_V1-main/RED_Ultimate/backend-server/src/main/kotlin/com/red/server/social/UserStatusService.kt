package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 🔴 YOUNES User Status & Privacy Service
 * إدارة الحالات والخصوصية باستخدام Redis
 */
@Service
class UserStatusService(
    private val redis: RedisTemplate<String, String>,
    private val jdbc: JdbcTemplate,
    private val users: UserAccountRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STATUS_PREFIX = "user:status:"
        private const val PRIVACY_PREFIX = "user:privacy:"
        private const val ONLINE_SET = "users:online"
        private const val STATUS_TTL_HOURS = 24L
    }

    data class UserStatusEntry(
        val type: String,
        val customText: String?,
        val updatedAt: Instant
    )

    // ─── الحالة ───

    fun updateStatus(userId: String, type: String, customText: String?, visibleTo: String): UserStatusEntry {
        val key = STATUS_PREFIX + userId
        val entry = UserStatusEntry(type, customText, Instant.now())

        redis.opsForHash<String, String>().apply {
            put(key, "type", type)
            put(key, "customText", customText ?: "")
            put(key, "visibleTo", visibleTo)
            put(key, "updatedAt", entry.updatedAt.toString())
        }
        redis.expire(key, STATUS_TTL_HOURS, TimeUnit.HOURS)

        // تحديث مجموعة المتصلين
        when (type) {
            "ONLINE" -> redis.opsForSet().add(ONLINE_SET, userId)
            "OFFLINE", "INVISIBLE" -> redis.opsForSet().remove(ONLINE_SET, userId)
        }

        log.debug("Status updated for {}: {}", userId, type)
        return entry
    }

    fun getVisibleStatus(targetUserId: String, requesterId: String): UserStatusEntry? {
        val key = STATUS_PREFIX + targetUserId
        val data = redis.opsForHash<String, String>().entries(key)

        if (data.isEmpty()) return null

        val visibleTo = data["visibleTo"] ?: "EVERYONE"
        val type = data["type"] ?: "OFFLINE"
        val customText = data["customText"]?.takeIf { it.isNotBlank() }

        // فحص الخصوصية
        when (visibleTo) {
            "NOBODY" -> return if (targetUserId == requesterId) UserStatusEntry(type, customText, parseInstant(data["updatedAt"])) else null
            "CONTACTS" -> {
                // الحالة مرئية فقط لجهات اتصال المستخدم (red_contacts في PostgreSQL)
                if (targetUserId != requesterId && !areContacts(targetUserId, requesterId)) return null
            }
        }

        // المستخدم المخفي يبدو غير متصل
        if (type == "INVISIBLE" && targetUserId != requesterId) {
            return UserStatusEntry("OFFLINE", null, parseInstant(data["updatedAt"]))
        }

        return UserStatusEntry(type, customText, parseInstant(data["updatedAt"]))
    }

    // ─── الخصوصية ───

    fun getPrivacySettings(userId: String): PrivacySettingsResponse {
        val key = PRIVACY_PREFIX + userId
        val data = redis.opsForHash<String, String>().entries(key)

        return PrivacySettingsResponse(
            lastSeen = data["lastSeen"] ?: "EVERYONE",
            onlineStatus = data["onlineStatus"] ?: "EVERYONE",
            profilePhoto = data["profilePhoto"] ?: "EVERYONE",
            about = data["about"] ?: "EVERYONE",
            status = data["status"] ?: "CONTACTS",
            readReceipts = data["readReceipts"] ?: "EVERYONE",
            calls = data["calls"] ?: "CONTACTS",
            groups = data["groups"] ?: "EVERYONE",
            liveLocation = data["liveLocation"] ?: "NOBODY"
        )
    }

    fun updatePrivacySettings(userId: String, request: PrivacySettingsRequest): PrivacySettingsResponse {
        val key = PRIVACY_PREFIX + userId
        val ops = redis.opsForHash<String, String>()

        request.lastSeen?.let { ops.put(key, "lastSeen", it) }
        request.onlineStatus?.let { ops.put(key, "onlineStatus", it) }
        request.profilePhoto?.let { ops.put(key, "profilePhoto", it) }
        request.about?.let { ops.put(key, "about", it) }
        request.status?.let { ops.put(key, "status", it) }
        request.readReceipts?.let { ops.put(key, "readReceipts", it) }
        request.calls?.let { ops.put(key, "calls", it) }
        request.groups?.let { ops.put(key, "groups", it) }
        request.liveLocation?.let { ops.put(key, "liveLocation", it) }

        log.info("Privacy settings updated for {}", userId)
        return getPrivacySettings(userId)
    }

    // ─── جهات الاتصال المتصلة ───

    fun getOnlineContacts(userId: String): List<OnlineContact> {
        val onlineUserIds = redis.opsForSet().members(ONLINE_SET) ?: emptySet()

        return onlineUserIds
            .filter { it != userId }
            .mapNotNull { onlineId ->
                val status = getVisibleStatus(onlineId, userId) ?: return@mapNotNull null
                val displayName = users.findByRedId(onlineId)?.displayName ?: onlineId
                OnlineContact(onlineId, displayName, status.type, status.customText)
            }
    }

    /** تحقق من أن [requesterId] جهة اتصال لـ [ownerId] (علاقة ثنائية الاتجاه). */
    private fun areContacts(ownerId: String, requesterId: String): Boolean {
        val owner = users.findByRedId(ownerId) ?: return false
        val requester = users.findByRedId(requesterId) ?: return false
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)",
            Int::class.java,
            owner.id, requester.id, requester.id, owner.id
        ) ?: 0
        return count > 0
    }

    private fun parseInstant(value: String?): Instant {
        return try { value?.let { Instant.parse(it) } ?: Instant.now() } catch (_: Exception) { Instant.now() }
    }
}
