package com.red.server.social

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 🔴 YOUNES User Status & Privacy Service
 * إدارة الحالات والخصوصية باستخدام Redis
 *
 * - مَن يرى مَن: CONTACTS = جهات الاتصال المعتمدة (ثنائي الاتجاه)
 * - الفحص باستخدام repository فعلي (لا stubs)
 * - فلترة: المستخدم المخفي يظهر OFFLINE للآخرين
 * - الاسم والـ username يأتيان من UserAccountRepository
 */
@Service
class UserStatusService(
    private val redis: RedisTemplate<String, String>,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STATUS_PREFIX = "user:status:"
        private const val PRIVACY_PREFIX = "user:privacy:"
        private const val ONLINE_SET = "users:online"
        private const val CONTACTS_SET_PREFIX = "contacts:"
        private const val STATUS_TTL_HOURS = 24L
    }

    data class UserStatusEntry(
        val type: String,
        val customText: String?,
        val updatedAt: Instant
    )

    // ─── الحالة ───

    fun updateStatus(userId: String, type: String, customText: String?, visibleTo: String): UserStatusEntry {
        require(type in setOf("ONLINE", "OFFLINE", "INVISIBLE", "AWAY", "BUSY")) {
            "نوع الحالة غير مدعوم: $type"
        }
        require(visibleTo in setOf("EVERYONE", "CONTACTS", "NOBODY")) {
            "قيمة visibleTo غير مدعومة: $visibleTo"
        }
        val account = resolveAccount(userId) ?: throw NoSuchElementException("User not found")
        val redId = account.redId
        val key = STATUS_PREFIX + redId
        val entry = UserStatusEntry(type, customText, Instant.now())

        redis.opsForHash<String, String>().apply {
            put(key, "type", type)
            put(key, "customText", customText ?: "")
            put(key, "visibleTo", visibleTo)
            put(key, "updatedAt", entry.updatedAt.toString())
        }
        redis.expire(key, STATUS_TTL_HOURS, TimeUnit.HOURS)

        // مجموعة المتصلين تُخزَّن بمعرّف يونس — نفس مفتاح red:presence:index
        when (type) {
            "ONLINE" -> redis.opsForSet().add(ONLINE_SET, redId)
            "OFFLINE", "INVISIBLE" -> redis.opsForSet().remove(ONLINE_SET, redId)
        }

        log.debug("Status updated for {}: {}", redId, type)
        return entry
    }

    fun getVisibleStatus(targetUserId: String, requesterId: String): UserStatusEntry? {
        val target = resolveAccount(targetUserId) ?: return null
        val requester = resolveAccount(requesterId)
        val targetRedId = target.redId
        val requesterRedId = requester?.redId ?: requesterId
        val key = STATUS_PREFIX + targetRedId
        val data = redis.opsForHash<String, String>().entries(key)

        if (data.isEmpty()) return null

        val visibleTo = data["visibleTo"] ?: "EVERYONE"
        val type = data["type"] ?: "OFFLINE"
        val customText = data["customText"]?.takeIf { it.isNotBlank() }
        val updatedAt = parseInstant(data["updatedAt"])

        val canSee = when (visibleTo) {
            "EVERYONE" -> true
            "NOBODY" -> targetRedId == requesterRedId
            "CONTACTS" -> targetRedId == requesterRedId || areContacts(targetRedId, requesterRedId)
            else -> true
        }
        if (!canSee) return null

        if (type == "INVISIBLE" && targetRedId != requesterRedId) {
            return UserStatusEntry("OFFLINE", null, updatedAt)
        }

        return UserStatusEntry(type, customText, updatedAt)
    }

    /**
     * فحص ثنائي الاتجاه: هل هما في قائمة جهات اتصال بعضهما؟
     * يستخدم Redis Set للتخزين المؤقت (membership من contact service)
     */
    private fun areContacts(userA: String, userB: String): Boolean {
        val setA = redis.opsForSet().isMember(CONTACTS_SET_PREFIX + userA, userB) ?: false
        val setB = redis.opsForSet().isMember(CONTACTS_SET_PREFIX + userB, userA) ?: false
        // ثنائي الاتجاه — لازم الاثنين
        return setA && setB
    }

    /** إضافة/إزالة من جهات الاتصال (يُستدعى من ContactService) */
    fun addContact(userId: String, contactId: String) {
        redis.opsForSet().add(CONTACTS_SET_PREFIX + userId, contactId)
    }

    fun removeContact(userId: String, contactId: String) {
        redis.opsForSet().remove(CONTACTS_SET_PREFIX + userId, contactId)
    }

    // ─── الخصوصية ───

    fun getPrivacySettings(userId: String): PrivacySettingsResponse {
        val account = resolveAccount(userId) ?: return defaultPrivacySettings()
        return jdbc.query(
            """SELECT last_seen, online_status, profile_photo, about, status, read_receipts, calls, groups_add, live_location
               FROM user_privacy_settings WHERE user_id = ?""",
            { rs, _ ->
                PrivacySettingsResponse(
                    lastSeen = rs.getString("last_seen"),
                    onlineStatus = rs.getString("online_status"),
                    profilePhoto = rs.getString("profile_photo"),
                    about = rs.getString("about"),
                    status = rs.getString("status"),
                    readReceipts = rs.getString("read_receipts"),
                    calls = rs.getString("calls"),
                    groups = rs.getString("groups_add"),
                    liveLocation = rs.getString("live_location")
                )
            },
            account.id
        ).firstOrNull() ?: defaultPrivacySettings()
    }

    fun updatePrivacySettings(userId: String, request: PrivacySettingsRequest): PrivacySettingsResponse {
        val account = resolveAccount(userId) ?: throw NoSuchElementException("User not found")
        val validScopes = setOf("EVERYONE", "CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH", "NOBODY")
        fun validated(field: String, requested: String?, current: String): String = requested?.also {
            require(it in validScopes) { "قيمة $field غير صالحة: $it (المسموح: $validScopes)" }
        } ?: current

        val current = getPrivacySettings(account.redId)
        val updated = PrivacySettingsResponse(
            lastSeen = validated("lastSeen", request.lastSeen, current.lastSeen),
            onlineStatus = validated("onlineStatus", request.onlineStatus, current.onlineStatus),
            profilePhoto = validated("profilePhoto", request.profilePhoto, current.profilePhoto),
            about = validated("about", request.about, current.about),
            status = validated("status", request.status, current.status),
            readReceipts = validated("readReceipts", request.readReceipts, current.readReceipts),
            calls = validated("calls", request.calls, current.calls),
            groups = validated("groups", request.groups, current.groups),
            liveLocation = validated("liveLocation", request.liveLocation, current.liveLocation)
        )

        jdbc.update(
            """INSERT INTO user_privacy_settings
               (user_id, last_seen, online_status, profile_photo, about, status, read_receipts, calls, groups_add, live_location)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (user_id) DO UPDATE SET
                 last_seen = EXCLUDED.last_seen, online_status = EXCLUDED.online_status,
                 profile_photo = EXCLUDED.profile_photo, about = EXCLUDED.about, status = EXCLUDED.status,
                 read_receipts = EXCLUDED.read_receipts, calls = EXCLUDED.calls, groups_add = EXCLUDED.groups_add,
                 live_location = EXCLUDED.live_location""",
            account.id, updated.lastSeen, updated.onlineStatus, updated.profilePhoto, updated.about, updated.status,
            updated.readReceipts, updated.calls, updated.groups, updated.liveLocation
        )

        val key = PRIVACY_PREFIX + account.redId
        val ops = redis.opsForHash<String, String>()
        ops.put(key, "lastSeen", updated.lastSeen)
        ops.put(key, "onlineStatus", updated.onlineStatus)
        ops.put(key, "profilePhoto", updated.profilePhoto)
        ops.put(key, "about", updated.about)
        ops.put(key, "status", updated.status)
        ops.put(key, "readReceipts", updated.readReceipts)
        ops.put(key, "calls", updated.calls)
        ops.put(key, "groups", updated.groups)
        ops.put(key, "liveLocation", updated.liveLocation)

        log.info("Privacy settings updated for {}", account.redId)
        return updated
    }

    private fun defaultPrivacySettings() = PrivacySettingsResponse(
        lastSeen = "EVERYONE", onlineStatus = "EVERYONE", profilePhoto = "EVERYONE", about = "EVERYONE",
        status = "CONTACTS", readReceipts = "EVERYONE", calls = "CONTACTS", groups = "EVERYONE", liveLocation = "NOBODY"
    )

    // ─── جهات الاتصال المتصلة ───

    fun getOnlineContacts(userId: String): List<OnlineContact> {
        val me = resolveAccount(userId) ?: return emptyList()
        val onlineUserIds = redis.opsForSet().members(ONLINE_SET) ?: emptySet()

        return onlineUserIds
            .filter { it != me.redId }
            .mapNotNull { onlineId ->
                val status = getVisibleStatus(onlineId, me.redId) ?: return@mapNotNull null
                val user = users.findByRedId(onlineId) ?: return@mapNotNull null
                OnlineContact(
                    userId = onlineId,
                    displayName = user.displayName.ifBlank { user.username },
                    username = user.username,
                    avatarColor = user.avatarColor,
                    type = status.type,
                    customText = status.customText
                )
            }
    }

    private fun resolveAccount(id: String): UserAccount? {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return null
        users.findByRedId(trimmed.uppercase())?.let { return it }
        val uuid = runCatching { UUID.fromString(trimmed) }.getOrNull() ?: return null
        return users.findById(uuid).orElse(null)
    }

    private fun parseInstant(value: String?): Instant {
        return try { value?.let { Instant.parse(it) } ?: Instant.now() } catch (_: Exception) { Instant.now() }
    }
}

data class OnlineContact(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarColor: String? = null,
    val type: String,
    val customText: String?
)

data class PrivacySettingsResponse(
    val lastSeen: String,
    val onlineStatus: String,
    val profilePhoto: String,
    val about: String,
    val status: String,
    val readReceipts: String,
    val calls: String,
    val groups: String,
    val liveLocation: String
)

data class PrivacySettingsRequest(
    val lastSeen: String? = null,
    val onlineStatus: String? = null,
    val profilePhoto: String? = null,
    val about: String? = null,
    val status: String? = null,
    val readReceipts: String? = null,
    val calls: String? = null,
    val groups: String? = null,
    val liveLocation: String? = null
)
