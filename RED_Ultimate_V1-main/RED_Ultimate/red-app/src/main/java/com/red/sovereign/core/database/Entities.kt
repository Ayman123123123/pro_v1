package com.red.sovereign.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

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
)

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
)

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
    val type: String, // VOICE, VIDEO, GROUP, LIVE, SPACE, CONFERENCE, AUDIO_SPACE
    val direction: String, // INCOMING, OUTGOING
    val route: String = "RED", // RED فقط - تم إلغاء PSTN/DINSTAR
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
