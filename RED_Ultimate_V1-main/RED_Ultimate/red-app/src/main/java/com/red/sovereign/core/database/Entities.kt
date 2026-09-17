package com.red.sovereign.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.TypeConverter

/**
 * حدود البحث النصي الاحتياطي (LIKE) — تُطبَّق كـ `LIMIT` داخل SQL نفسه
 * بدل `take(n)` في الذاكرة بعد جلب كل الصفوف المطابقة.
 */
const val MESSAGE_SEARCH_DEFAULT_LIMIT = 50
const val MESSAGE_SEARCH_MAX_LIMIT = 100
const val CONTACT_SEARCH_DEFAULT_LIMIT = 50

/**
 * تهريب LIKE موحّد (المكان الوحيد المسموح به).
 * `%` و`_` محرفا بدل، و`\` محرف التهريب — تُهرَّب `\` أولًا وإلا
 * ضوعف تهريب ما بعدها. كل DAO يستخدم `ESCAPE '\'` معه.
 */
fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** نمط `%…%` جاهز للربط بعد التقليم والتهريب. فارغ/فارغ-بعد-التقليم ⇒ `"%%"`. */
fun likeContainsPattern(raw: String): String = "%${raw.trim().escapeLike()}%"

/** حالات الرسالة — تُخزَّن كنص عبر [RedTypeConverters] بدل السلاسل الحرة. */
enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED;

    companion object {
        fun fromDb(value: String?): MessageStatus =
            values().firstOrNull { it.name == value } ?: SENT
    }
}

/** أنواع عناصر السجل المحلي — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class HistoryMessageType {
    RICH_TEXT, TEXT, IMAGE, VIDEO, FILE, AUDIO, VOICE, STICKER, GROUP_MESSAGE, SYSTEM;

    companion object {
        fun fromDb(value: String?): HistoryMessageType =
            values().firstOrNull { it.name == value } ?: RICH_TEXT
    }
}

/** حالات صندوق الصادر — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class OutboxStatus {
    PENDING, SENDING, SENT, FAILED, DEAD_LETTER;

    companion object {
        fun fromDb(value: String?): OutboxStatus =
            values().firstOrNull { it.name == value } ?: PENDING
    }
}

/** حالات سجل المكالمات — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class CallLogStatus {
    COMPLETED, MISSED, REJECTED, ACTIVE, ENDED, FAILED;

    companion object {
        fun fromDb(value: String?): CallLogStatus =
            values().firstOrNull { it.name == value } ?: COMPLETED
    }
}

/** أنواع سجل المكالمات — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class CallLogType {
    VOICE, VIDEO, GROUP, LIVE, SPACE;

    companion object {
        fun fromDb(value: String?): CallLogType =
            values().firstOrNull { it.name == value } ?: VOICE
    }
}

/** اتجاه سجل المكالمات — يُخزَّن كنص عبر [RedTypeConverters]. */
enum class CallDirection {
    INCOMING, OUTGOING;

    companion object {
        fun fromDb(value: String?): CallDirection =
            values().firstOrNull { it.name == value } ?: INCOMING
    }
}

/** حالة البث المباشر — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class LiveStreamStatus {
    SCHEDULED, LIVE, ENDED, CANCELLED, ARCHIVED;

    companion object {
        fun fromDb(value: String?): LiveStreamStatus =
            values().firstOrNull { it.name == value } ?: SCHEDULED
    }
}

/** نوع الوسائط — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class MediaFileType {
    IMAGE, VIDEO, AUDIO, FILE, VOICE, STICKER, DOCUMENT;

    companion object {
        fun fromDb(value: String?): MediaFileType =
            values().firstOrNull { it.name == value } ?: FILE
    }
}

/** حالة الوسائط — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class MediaFileStatus {
    PENDING, UPLOADING, UPLOADED, FAILED, DELETED;

    companion object {
        fun fromDb(value: String?): MediaFileStatus =
            values().firstOrNull { it.name == value } ?: PENDING
    }
}

/** نوع الجهاز — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class DeviceType {
    ANDROID, IOS, DESKTOP, WEB, UNKNOWN;

    companion object {
        fun fromDb(value: String?): DeviceType =
            values().firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** حالة المستخدم — تُخزَّن كنص عبر [RedTypeConverters]. */
enum class UserStatus {
    ONLINE, OFFLINE, AWAY, BUSY, INVISIBLE;

    companion object {
        fun fromDb(value: String?): UserStatus =
            values().firstOrNull { it.name == value } ?: OFFLINE
    }
}

/**
 * محوّلات الأنواع — تسجيلها على مستوى DAO عبر
 * `@TypeConverters(RedTypeConverters::class)` (لا يتطلب لمس RedDatabase).
 * كل قراءة تتحمّل القيم القديمة/الغريبة عبر fallback آمن بدل الكسر.
 */
class RedTypeConverters {
    @TypeConverter fun messageStatusToDb(v: MessageStatus?): String? = v?.name
    @TypeConverter fun messageStatusFromDb(v: String?): MessageStatus = MessageStatus.fromDb(v)

    @TypeConverter fun historyTypeToDb(v: HistoryMessageType?): String? = v?.name
    @TypeConverter fun historyTypeFromDb(v: String?): HistoryMessageType = HistoryMessageType.fromDb(v)

    @TypeConverter fun outboxStatusToDb(v: OutboxStatus?): String? = v?.name
    @TypeConverter fun outboxStatusFromDb(v: String?): OutboxStatus = OutboxStatus.fromDb(v)

    @TypeConverter fun callLogStatusToDb(v: CallLogStatus?): String? = v?.name
    @TypeConverter fun callLogStatusFromDb(v: String?): CallLogStatus = CallLogStatus.fromDb(v)

    @TypeConverter fun callLogTypeToDb(v: CallLogType?): String? = v?.name
    @TypeConverter fun callLogTypeFromDb(v: String?): CallLogType = CallLogType.fromDb(v)

    @TypeConverter fun callDirectionToDb(v: CallDirection?): String? = v?.name
    @TypeConverter fun callDirectionFromDb(v: String?): CallDirection = CallDirection.fromDb(v)

    @TypeConverter fun liveStreamStatusToDb(v: LiveStreamStatus?): String? = v?.name
    @TypeConverter fun liveStreamStatusFromDb(v: String?): LiveStreamStatus = LiveStreamStatus.fromDb(v)

    @TypeConverter fun mediaFileTypeToDb(v: MediaFileType?): String? = v?.name
    @TypeConverter fun mediaFileTypeFromDb(v: String?): MediaFileType = MediaFileType.fromDb(v)

    @TypeConverter fun mediaFileStatusToDb(v: MediaFileStatus?): String? = v?.name
    @TypeConverter fun mediaFileStatusFromDb(v: String?): MediaFileStatus = MediaFileStatus.fromDb(v)

    @TypeConverter fun deviceTypeToDb(v: DeviceType?): String? = v?.name
    @TypeConverter fun deviceTypeFromDb(v: String?): DeviceType = DeviceType.fromDb(v)

    @TypeConverter fun userStatusToDb(v: UserStatus?): String? = v?.name
    @TypeConverter fun userStatusFromDb(v: String?): UserStatus = UserStatus.fromDb(v)
}

@Entity(tableName = "messages", indices = [Index("conversationId"), Index("status"), Index(value = ["conversationId", "createdAt"])])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val payload: ByteArray,
    val type: String,
    val senderDeviceId: Int,
    val receiverDeviceId: Int,
    val ciphertextType: Int,
    val sequence: Long,
    val status: String,
    val createdAt: Long,
    val outgoing: Boolean,
    // ✅ إضافة دعم الرد على الرسائل
    val replyToMessageId: String? = null,
    val replyToMessageText: String? = null,
    val replyToSenderId: String? = null,
    // ✅ إضافة دعم الحذف للجميع
    val deletedForAll: Boolean = false,
    val deletedBySenderId: String? = null
) {
    // ByteArray يكسر مساواة data class الافتراضية (مرجعية لا محتوى) —
    // contentEquals/contentHashCode إلزاميان وإلا فشلت المقارنات/copies في الكاش.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return id == other.id &&
            conversationId == other.conversationId &&
            senderId == other.senderId &&
            receiverId == other.receiverId &&
            payload.contentEquals(other.payload) &&
            type == other.type &&
            senderDeviceId == other.senderDeviceId &&
            receiverDeviceId == other.receiverDeviceId &&
            ciphertextType == other.ciphertextType &&
            sequence == other.sequence &&
            status == other.status &&
            createdAt == other.createdAt &&
            outgoing == other.outgoing &&
            replyToMessageId == other.replyToMessageId &&
            replyToMessageText == other.replyToMessageText &&
            replyToSenderId == other.replyToSenderId &&
            deletedForAll == other.deletedForAll &&
            deletedBySenderId == other.deletedBySenderId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + receiverId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + senderDeviceId
        result = 31 * result + receiverDeviceId
        result = 31 * result + ciphertextType
        result = 31 * result + sequence.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + outgoing.hashCode()
        result = 31 * result + (replyToMessageId?.hashCode() ?: 0)
        result = 31 * result + (replyToMessageText?.hashCode() ?: 0)
        result = 31 * result + (replyToSenderId?.hashCode() ?: 0)
        result = 31 * result + deletedForAll.hashCode()
        result = 31 * result + (deletedBySenderId?.hashCode() ?: 0)
        return result
    }
}

// TODO(P1-A): عمود topicId مقترح لجدول local_history لم يُضف عمدًا هنا —
// إضافة عمود Room تتطلب Migration (6→7) + تعديل RedDatabase.kt وهو خارج
// نطاق P1-A («ملفاتك فقط»). البديل الحالي: hashtags الموجودة داخل حمولة
// RichMessage (topicId أولًا، ثم hashtags، ثم #topic من النص) تُستخدم كـ topics
// وتُستخرج محليًا عبر LocalHistoryEntity.extractTopics() أدناه دون كسر البناء.
@Entity(
    tableName = "local_history",
    indices = [
        Index(value = ["conversationId", "createdAt"]),
        Index(value = ["conversationId", "messageType", "createdAt"])
    ]
)
data class LocalHistoryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val encryptedPlaintext: ByteArray,
    val messageType: String,
    val createdAt: Long,
    val outgoing: Boolean,
    val status: String = "SENT",
    val replyToMessageId: String? = null,
    val replyToMessageText: String? = null,
    val replyToSenderId: String? = null,
    val deletedForAll: Boolean = false,
    val deletedBySenderId: String? = null
) {
    // ByteArray في data class: مساواة مرجعية افتراضيًا — contentEquals إلزامي.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalHistoryEntity) return false
        return id == other.id &&
            conversationId == other.conversationId &&
            senderId == other.senderId &&
            encryptedPlaintext.contentEquals(other.encryptedPlaintext) &&
            messageType == other.messageType &&
            createdAt == other.createdAt &&
            outgoing == other.outgoing &&
            status == other.status &&
            replyToMessageId == other.replyToMessageId &&
            replyToMessageText == other.replyToMessageText &&
            replyToSenderId == other.replyToSenderId &&
            deletedForAll == other.deletedForAll &&
            deletedBySenderId == other.deletedBySenderId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + encryptedPlaintext.contentHashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + outgoing.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (replyToMessageId?.hashCode() ?: 0)
        result = 31 * result + (replyToMessageText?.hashCode() ?: 0)
        result = 31 * result + (replyToSenderId?.hashCode() ?: 0)
        result = 31 * result + deletedForAll.hashCode()
        result = 31 * result + (deletedBySenderId?.hashCode() ?: 0)
        return result
    }
}

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["archived", "pinned", "lastMessageTimestamp"]),
        Index("lastMessageTimestamp")
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val mutedUntil: Long = 0,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val redId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isFriend: Boolean = true,
    val isBlocked: Boolean = false,
    val lastSeen: Long = 0
)

@Entity(
    tableName = "groups",
    indices = [Index("createdAt"), Index("archived"), Index("updatedAt")]
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val ownerRedId: String = "",
    val myRole: String = "MEMBER",
    val privacy: String = "PRIVATE",
    val settingsJson: String = "{}",
    val communityId: String? = null,
    val memberCount: Int = 0,
    val slowModeSeconds: Int = 0,
    val disappearingSeconds: Int = 0,
    val archived: Boolean = false,
    val updatedAt: Long = 0,
    val createdAt: Long = 0
)

@Entity(
    tableName = "call_logs",
    indices = [
        Index("timestamp"),
        Index(value = ["peerId", "timestamp"])
    ]
)
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerLabel: String = "",
    val type: String, // VOICE, VIDEO, GROUP, LIVE, SPACE
    val direction: String, // INCOMING, OUTGOING
    val route: String = "RED", // RED
    val status: String, // COMPLETED, MISSED, REJECTED, ACTIVE, ENDED, FAILED
    val timestamp: Long,
    val durationMs: Long = 0,
    val answeredAt: Long? = null,
    val endedAt: Long? = null
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mediaUrl: String,
    val mediaType: String,
    val caption: String? = null,
    val timestamp: Long,
    val expiresAt: Long,
    val isMyStory: Boolean = false
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val timestamp: Long
)

/**
 * تفاعل إيموجي على رسالة — يُخزّن محلياً للعرض السريع بعد فك التشفير.
 * المفتاح الأساسي مركّب من (الرسالة + المُرسِل) لأن لكل مستخدم تفاعلاً واحداً لكل رسالة
 * (toggle: إعادة إرسال نفس الإيموجي = إزالة؛ إرسال إيموجي مختلف = استبدال).
 * E2EE: الإيموجي نفسه لا يصل للخادم (يُرسل ضمن حمولة RICH_TEXT المشفّرة).
 */
@Entity(
    tableName = "message_reactions",
    primaryKeys = ["messageId", "senderId"],
    indices = [Index("conversationId"), Index("messageId")]
)
data class MessageReactionEntity(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * P1-A: استخراج topics رسالة من local_history دون تغيير السكيما.
 * الأولوية: RichMessage.topicId ← hashtags ← regex #topic من النص/الخام.
 * تُستخدم للفلترة المحلية في ChatThreadScreen (chips). لا تمس Room.
 */
private val LocalHistoryTopicRegex = Regex("#[\\w\u0600-\u06FF\\-]{2,30}")

fun LocalHistoryEntity.extractTopics(): List<String> {
    val rich = runCatching { com.red.sovereign.core.RichMessage.decode(encryptedPlaintext) }.getOrNull()
    if (rich != null) {
        val out = LinkedHashSet<String>()
        rich.topicId?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }?.let { out += it }
        rich.hashtags.forEach { h -> h.trim().removePrefix("#").takeIf { it.isNotBlank() }?.let { out += it } }
        LocalHistoryTopicRegex.findAll(rich.text).forEach { out += it.value.removePrefix("#") }
        if (out.isNotEmpty()) return out.toList()
    }
    // خام UTF-8 (مسار متفائل/قديم): استخراج مباشر من النص
    val raw = runCatching { encryptedPlaintext.toString(Charsets.UTF_8) }.getOrDefault("")
    return LocalHistoryTopicRegex.findAll(raw).map { it.value.removePrefix("#") }.distinct().toList()
}

fun List<LocalHistoryEntity>.distinctTopics(): List<String> =
    flatMap { runCatching { it.extractTopics() }.getOrDefault(emptyList()) }.distinct().sorted()

/**
 * رسالة مُعلَّمة (Starred/Bookmarked) — تُخزّن محلياً في Room للرجوع السريع.
 * لا ت arrived مع Restroom لأن التعليق محلي فقط (E2EE: لا معرف للرسالة على الخادم).
 */
@Entity(
    tableName = "starred_messages",
    indices = [Index("conversationId"), Index("starredAt")])
data class StarredMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String,
    val messageText: String,
    val messageType: String,
    val starredAt: Long = System.currentTimeMillis()
)

/**
 * LEGENDARY P1: رفع الوسائط المتين — outbox خاص بالصور/الصوت/الفيديو.
 * يحل: قتل العملية أثناء الرفع = فقدان + رفع على Main + حذف الملف عند الفشل.
 * idempotencyKey = messageId يمنع التكرار عند retry.
 */
@Entity(
    tableName = "media_uploads",
    indices = [Index(value = ["status", "nextAttemptAt"]), Index(value = ["messageId"], unique = true)]
)
data class MediaUploadEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val targetRedId: String? = null,
    val localPath: String,
    val mimeType: String,
    val size: Long = 0,
    val thumbPath: String? = null,
    val blurHash: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val objectKey: String? = null,
    val url: String? = null,
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val idempotencyKey: String = messageId
)

/**
 * قناة (Channel) — للبث والمجتمعات.
 * يدعم: القنوات النصية، الصوتية، المرئية، والإعلانات.
 */
@Entity(
    tableName = "channels",
    indices = [
        Index("communityId"),
        Index("ownerId"),
        Index("type"),
        Index("createdAt"),
        Index(value = ["communityId", "type"]),
        Index(value = ["isArchived", "updatedAt"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val type: String = "TEXT", // TEXT, VOICE, VIDEO, ANNOUNCEMENT, STAGE
    val communityId: String? = null,
    val ownerId: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val settingsJson: String = "{}", // JSON: slowMode, permissions, encryption, etc.
    val memberCount: Int = 0,
    val messageCount: Long = 0,
    val isPrivate: Boolean = true,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0,
    val slowModeSeconds: Int = 0,
    val maxMembers: Int = 0, // 0 = unlimited
    val defaultNotifications: String = "ALL", // ALL, MENTIONS, NONE
    val encryptionEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0
)

/**
 * مجتمع (Community) — مجموعة قنوات مترابطة.
 * يدعم: التسلسل الهرمي، الأدوار، الإعدادات المشتركة.
 */
@Entity(
    tableName = "communities",
    indices = [
        Index("ownerId"),
        Index("createdAt"),
        Index("isPublic"),
        Index(value = ["isArchived", "updatedAt"])
    ]
)
data class CommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val ownerId: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val settingsJson: String = "{}", // JSON: roles, permissions, features, branding
    val memberCount: Int = 0,
    val channelCount: Int = 0,
    val isPublic: Boolean = false,
    val isArchived: Boolean = false,
    val archivedAt: Long = 0,
    val verificationLevel: Int = 0, // 0=none, 1=low, 2=medium, 3=high, 4=highest
    val explicitContentFilter: Int = 1, // 0=disabled, 1=no_role, 2=all
    val defaultChannelNotifications: String = "MENTIONS",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0
)

/**
 * عضو مجتمع/قناة — يربط المستخدمين بالمجتمعات والقنوات.
 */
@Entity(
    tableName = "community_members",
    primaryKeys = ["communityId", "userId"],
    indices = [
        Index("userId"),
        Index("role"),
        Index("joinedAt"),
        Index(value = ["communityId", "role"])
    ]
)
data class CommunityMemberEntity(
    val communityId: String,
    val userId: String,
    val role: String = "MEMBER", // OWNER, ADMIN, MODERATOR, MEMBER
    val joinedAt: Long = System.currentTimeMillis(),
    val invitedBy: String? = null,
    val nickname: String? = null,
    val isMuted: Boolean = false,
    val mutedUntil: Long = 0,
    val permissionsJson: String = "{}", // صلاحيات مخصصة لهذا العضو
    val lastReadMessageId: String? = null,
    val notificationLevel: String = "ALL" // ALL, MENTIONS, NONE
)

/**
 * عضو قناة — يربط المستخدمين بالقنوات.
 */
@Entity(
    tableName = "channel_members",
    primaryKeys = ["channelId", "userId"],
    indices = [
        Index("userId"),
        Index("role"),
        Index("joinedAt"),
        Index(value = ["channelId", "role"])
    ]
)
data class ChannelMemberEntity(
    val channelId: String,
    val userId: String,
    val role: String = "MEMBER", // OWNER, ADMIN, MODERATOR, MEMBER, SPEAKER
    val joinedAt: Long = System.currentTimeMillis(),
    val invitedBy: String? = null,
    val isMuted: Boolean = false,
    val mutedUntil: Long = 0,
    val permissionsJson: String = "{}",
    val lastReadMessageId: String? = null,
    val notificationLevel: String = "ALL"
)

/**
 * بث مباشر (Live Stream) — للبث المرئي/الصوتي المباشر.
 */
@Entity(
    tableName = "live_streams",
    indices = [
        Index("channelId"),
        Index("hostId"),
        Index("communityId"),
        Index("status"),
        Index("scheduledAt"),
        Index("startedAt"),
        Index("endedAt"),
        Index(value = ["channelId", "status"]),
        Index(value = ["hostId", "status"]),
        Index(value = ["communityId", "status"])
    ]
)
data class LiveStreamEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val hostId: String,
    val communityId: String? = null,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val status: String = "SCHEDULED", // SCHEDULED, LIVE, ENDED, CANCELLED, ARCHIVED
    val streamType: String = "VIDEO", // VIDEO, AUDIO, SCREEN_SHARE
    val scheduledAt: Long = 0,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val durationSeconds: Long = 0,
    val peakViewers: Int = 0,
    val totalUniqueViewers: Long = 0,
    val totalWatchTimeSeconds: Long = 0,
    val hlsManifestUrl: String? = null,
    val rtmpIngestUrl: String? = null,
    val streamKey: String? = null,
    val recordingEnabled: Boolean = true,
    val recordingStatus: String = "PENDING", // PENDING, PROCESSING, READY, FAILED
    val recordingUrl: String? = null,
    val chatEnabled: Boolean = true,
    val reactionsEnabled: Boolean = true,
    val giftsEnabled: Boolean = false,
    val settingsJson: String = "{}", // JSON: quality, latency, moderation, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0
)

/**
 * مشاهد بث مباشر — تتبع وقت الانضمام/المغادرة لكل مشاهد.
 */
@Entity(
    tableName = "live_stream_viewers",
    primaryKeys = ["streamId", "userId"],
    indices = [
        Index("userId"),
        Index("joinedAt"),
        Index("leftAt"),
        Index(value = ["streamId", "joinedAt"]),
        Index(value = ["streamId", "leftAt"])
    ]
)
data class LiveStreamViewerEntity(
    val streamId: String,
    val userId: String,
    val joinedAt: Long = System.currentTimeMillis(),
    val leftAt: Long = 0,
    val watchDurationSeconds: Long = 0,
    val quality: String = "AUTO", // AUTO, 1080p, 720p, 480p, 360p, AUDIO_ONLY
    val platform: String = "ANDROID", // ANDROID, IOS, DESKTOP, WEB
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * تسجيل بث مباشر — Metadata للتسجيلات المخزنة في MinIO/MongoDB GridFS.
 */
@Entity(
    tableName = "live_stream_recordings",
    indices = [
        Index("streamId"),
        Index("hostId"),
        Index("status"),
        Index("createdAt"),
        Index(value = ["streamId", "status"])
    ]
)
data class LiveStreamRecordingEntity(
    @PrimaryKey val id: String,
    val streamId: String,
    val hostId: String,
    val title: String,
    val durationSeconds: Long = 0,
    val fileSizeBytes: Long = 0,
    val storagePath: String? = null, // MinIO path or MongoDB GridFS ID
    val hlsManifestUrl: String? = null,
    val thumbnailUrl: String? = null,
    val status: String = "PENDING", // PENDING, PROCESSING, READY, FAILED
    val processingStartedAt: Long = 0,
    val processingCompletedAt: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * دردشة البث المباشر — رسائل عالية التردد.
 */
@Entity(
    tableName = "live_stream_chat",
    indices = [
        Index("streamId"),
        Index("userId"),
        Index("sentAt"),
        Index(value = ["streamId", "sentAt"]),
        Index(value = ["streamId", "messageType"])
    ]
)
data class LiveStreamChatEntity(
    @PrimaryKey val id: String,
    val streamId: String,
    val userId: String,
    val content: String,
    val messageType: String = "CHAT", // CHAT, REACTION, DONATION, SYSTEM, POLL_VOTE
    val metadataJson: String = "{}", // بيانات إضافية: emoji, amount, poll_id, etc.
    val sentAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0,
    val deletedBy: String? = null
)

/**
 * تفاعلات البث المباشر (إيموجي، إعجابات).
 */
@Entity(
    tableName = "live_stream_reactions",
    primaryKeys = ["streamId", "userId", "emoji"],
    indices = [
        Index("streamId"),
        Index("userId"),
        Index("createdAt"),
        Index(value = ["streamId", "createdAt"])
    ]
)
data class LiveStreamReactionEntity(
    val streamId: String,
    val userId: String,
    val emoji: String,
    val count: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * هدايا/تبرعات البث المباشر.
 */
@Entity(
    tableName = "live_stream_gifts",
    indices = [
        Index("streamId"),
        Index("senderId"),
        Index("createdAt"),
        Index(value = ["streamId", "createdAt"]),
        Index(value = ["streamId", "amount"])
    ]
)
data class LiveStreamGiftEntity(
    @PrimaryKey val id: String,
    val streamId: String,
    val senderId: String,
    val giftType: String, // HEART, STAR, DIAMOND, CUSTOM
    val giftName: String,
    val amount: Double, // قيمة الهدية بالعملة المحلية
    val currency: String = "USD",
    val message: String? = null,
    val animationUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ملف وسائط — Metadata للملفات المخزنة في MinIO/MongoDB GridFS.
 */
@Entity(
    tableName = "media_files",
    indices = [
        Index("ownerId"),
        Index("conversationId"),
        Index("messageId"),
        Index("type"),
        Index("status"),
        Index("createdAt"),
        Index(value = ["conversationId", "type"]),
        Index(value = ["ownerId", "createdAt"])
    ]
)
data class MediaFileEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val conversationId: String? = null,
    val messageId: String? = null,
    val type: String, // IMAGE, VIDEO, AUDIO, FILE, VOICE, STICKER, DOCUMENT
    val mimeType: String,
    val fileName: String,
    val fileSize: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val blurHash: String? = null,
    val thumbnailPath: String? = null,
    val storagePath: String, // MinIO path or MongoDB GridFS ID
    val cdnUrl: String? = null,
    val status: String = "PENDING", // PENDING, UPLOADING, UPLOADED, FAILED, DELETED
    val encryptionKey: String? = null, // مفتاح تشفير الملف (مشتق من مفتاح المحادثة)
    val checksum: String? = null, // SHA256 للتحقق من السلامة
    val metadataJson: String = "{}", // بيانات إضافية: EXIF, chapters, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0
)

/**
 * جهاز مستخدم — لإدارة الجلسات والإشعارات.
 */
@Entity(
    tableName = "devices",
    indices = [
        Index("userId"),
        Index("deviceToken"), // FCM/APNs token
        Index("type"),
        Index("lastActiveAt"),
        Index(value = ["userId", "type"])
    ]
)
data class DeviceEntity(
    @PrimaryKey val id: String, // device fingerprint / installation ID
    val userId: String,
    val name: String, // "iPhone 15 Pro", "Samsung Galaxy S24", "Chrome on Windows"
    val type: String = "UNKNOWN", // ANDROID, IOS, DESKTOP, WEB, UNKNOWN
    val platform: String = "", // android, ios, windows, macos, linux, web
    val platformVersion: String = "",
    val appVersion: String = "",
    val deviceToken: String? = null, // FCM/APNs push token
    val voipToken: String? = null, // VoIP push token (iOS)
    val publicKey: String? = null, // مفتاح تشفير الجهاز
    val lastActiveAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = 0,
    val isActive: Boolean = true,
    val isTrusted: Boolean = false,
    val pushEnabled: Boolean = true,
    val voipEnabled: Boolean = false,
    val settingsJson: String = "{}", // إعدادات خاصة بالجهاز
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * ملف تعريف مستخدم (User Profile) — مخزن محلياً للعرض السريع.
 */
@Entity(
    tableName = "user_profiles",
    indices = [
        Index("username"),
        Index("displayName"),
        Index("status"),
        Index("lastSeen"),
        Index("updatedAt")
    ]
)
data class UserProfileEntity(
    @PrimaryKey val id: String, // Red ID
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val status: String = "OFFLINE", // ONLINE, OFFLINE, AWAY, BUSY, INVISIBLE
    val customStatus: String? = null,
    val customStatusEmoji: String? = null,
    val customStatusExpiresAt: Long = 0,
    val isVerified: Boolean = false,
    val isBot: Boolean = false,
    val isSystem: Boolean = false,
    val lastSeen: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val privacySettingsJson: String = "{}", // JSON: who can add, see profile, etc.
    val notificationSettingsJson: String = "{}", // JSON: notifications preferences
    val theme: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val language: String = "ar"
)

/**
 * إعدادات التطبيق — مفتاح/قيمة للإعدادات المحلية.
 */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * مسودة بث مباشر — للحفظ المؤقت قبل البدء.
 */
@Entity(tableName = "live_stream_drafts")
data class LiveStreamDraftEntity(
    @PrimaryKey val channelId: String,
    val hostId: String,
    val title: String = "",
    val description: String = "",
    val thumbnailPath: String? = null,
    val scheduledAt: Long = 0,
    val streamType: String = "VIDEO",
    val settingsJson: String = "{}",
    val updatedAt: Long = System.currentTimeMillis()
)
