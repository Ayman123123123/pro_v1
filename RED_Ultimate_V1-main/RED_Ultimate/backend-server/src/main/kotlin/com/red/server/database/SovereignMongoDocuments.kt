package com.red.server.database

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * 🗄️ YOUNES Sovereign MongoDB Documents
 * المستندات — البيانات غير العلائقية التي تحتاج مرونة عالية
 * 
 * التصميم:
 * - messages: رسائل المحادثات (مشفرة، تسلسلية)
 * - call_history: سجل المكالمات (سريع التغير)
 * - stories: القصص (تنتهي صلاحيتها)
 * - posts: المنشورات (مرنة البنية)
 * - live_streams: البث المباشر
 * - group_chat: رسائل المجموعات
 * - notification_archive: أرشيف الإشعارات
 */

// ════════════════════════════════════════════════════
// 💬 الرسائل — المشفرة طرفيًا
// ════════════════════════════════════════════════════

@Document("messages")
@CompoundIndex(name = "conv_seq", def = "{'conversationId': 1, 'sequenceNumber': -1}")
@CompoundIndex(name = "pinned_conv", def = "{'conversationId': 1, 'isPinned': 1, 'pinnedAt': -1}")
@CompoundIndex(name = "sender_created", def = "{'senderId': 1, 'createdAt': -1}")
data class MessageDocument(
    @Id val id: String? = null,
    @Indexed(unique = true) val uuid: String,
    @Indexed val conversationId: String,
    @Indexed val senderId: String,
    val senderDeviceId: Int,
    @Indexed val receiverId: String,
    val receiverDeviceId: Int,
    var payload: ByteArray,
    val messageType: String = "TEXT", // TEXT, IMAGE, VIDEO, VOICE, FILE, LOCATION, CONTACT, POLL
    val ciphertextType: Int,
    val sequenceNumber: Long = 0,
    @Indexed var status: String = "SENT", // SENT, DELIVERED, READ, FAILED
    // الوسائط المشفرة
    val attachments: List<MessageAttachment> = emptyList(),
    // 🎙️ البيانات الوصفية للرسائل الصوتية (اختيارية — تُملأ عند messageType=VOICE)
    val voiceMetadata: VoiceMessageMetadata? = null,
    // الرد والتحويل
    val replyToMessageUuid: String? = null,
    val forwardedFromConversationId: String? = null,
    var forwardCount: Int = 0,
    // التفاعل
    val reactions: List<MessageReaction> = emptyList(),
    // التثبيت — V26: يدعم تثبيت 5 رسائل لكل محادثة
    @Indexed var isPinned: Boolean = false,
    var pinnedAt: Instant? = null,
    var pinnedBy: String? = null,
    // الحذف
    var deletedForSenderAt: Instant? = null,
    var deletedForEveryoneAt: Instant? = null,
    // التعديل — V26: سجل التعديلات
    var editVersion: Int = 1,
    var originalCreatedAt: Instant? = null,
    // الاختفاء المرن — V26: 0=دائم، 30..604800 ثانية، -1=بعد القراءة
    var disappearAfterSeconds: Int? = null,
    var disappearAt: Instant? = null,
    var viewOnce: Boolean = false,
    // التوقيتات
    val createdAt: Instant = Instant.now(),
    var deliveredAt: Instant? = null,
    var readAt: Instant? = null,
    var editedAt: Instant? = null
)

data class MessageAttachment(
    val mediaKey: String,
    val mimeType: String,
    val encryptedKey: ByteArray, // مفتاح تشفير الملف
    val digest: ByteArray,       // SHA-256 للتحقق
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null, // للصوت/الفيديو
    val caption: String? = null,
    val fileName: String? = null
)

/**
 * 🎙️ بيانات وصفية للرسائل الصوتية (Voice Messages)
 * مخزّنة في MongoDB كحقل منفصل للبحث والفهرسة الفعّالة
 * الـ waveform يُخزّن كـ base64 لتوفير المساحة
 */
data class VoiceMessageMetadata(
    val durationMs: Long,
    val waveform: String,        // base64-encoded List<Int> (96 samples max)
    val sampleRate: Int = 44100, // Hz
    val bitrate: Int = 96000,    // bps
    val codec: String = "AAC",   // AAC, Opus
    val mimeType: String = "audio/mp4" // audio/mp4, audio/ogg
) {
    init {
        require(durationMs in 0..600_000) { "Voice message max 10 minutes" }
        require(sampleRate in 8000..48000) { "Sample rate must be 8-48 kHz" }
        require(bitrate in 8000..320_000) { "Bitrate must be 8-320 kbps" }
    }
}

data class MessageReaction(
    val userId: String,
    val emoji: String,
    val addedAt: Instant = Instant.now()
)

@Document("conversation_sequences")
data class ConversationSequence(
    @Id val conversationId: String,
    var sequence: Long = 0
)

// ════════════════════════════════════════════════════
// 📞 أنماط المكالمات المشتركة
// ════════════════════════════════════════════════════

data class CallParticipant(
    val userId: String,
    val role: String = "PARTICIPANT", // HOST, SPEAKER, LISTENER, PARTICIPANT
    val joinedAt: Instant = Instant.now(),
    val leftAt: Instant? = null
)

enum class CallType { VOIP_AUDIO, VOIP_VIDEO, CONFERENCE, LIVE_BROADCAST, PSTN_DINSTAR, AUDIO_SPACE }
enum class CallRoute { RED, DINSTAR }
enum class CallStatus { RINGING, CONNECTING, ACTIVE, ON_HOLD, ENDED, MISSED, FAILED }

// ════════════════════════════════════════════════════
// 📡 البث المباشر والغرف الصوتية
// ════════════════════════════════════════════════════

@Document("audio_spaces")
data class AudioSpaceDocument(
    @Id val id: String,
    @Indexed val hostId: String,
    val hostRedId: String,
    val title: String,
    @Indexed var status: String = "ACTIVE", // ACTIVE, ENDED
    val speakers: List<SpaceSpeaker> = emptyList(),
    var listenerCount: Int = 0,
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null
)

data class SpaceSpeaker(
    val userId: String,
    val username: String,
    val role: String = "SPEAKER", // HOST, CO_HOST, SPEAKER
    val joinedAt: Instant = Instant.now()
)

// ════════════════════════════════════════════════════
// 👥 رسائل المجموعات — مشفرة أيضًا
// ════════════════════════════════════════════════════

@Document("group_messages")
@CompoundIndex(name = "group_seq", def = "{'groupId': 1, 'sequenceNumber': -1}")
@CompoundIndex(name = "group_pinned", def = "{'groupId': 1, 'isPinned': 1, 'pinnedAt': -1}")
@CompoundIndex(name = "group_sender_created", def = "{'groupId': 1, 'senderId': 1, 'createdAt': -1}")
data class GroupMessageDocument(
    @Id val id: String? = null,
    @Indexed(unique = true) val uuid: String,
    @Indexed val groupId: String,
    @Indexed val senderId: String,
    val senderDeviceId: Int,
    var payload: ByteArray,
    val messageType: String = "TEXT",
    val ciphertextType: Int,
    val sequenceNumber: Long = 0,
    @Indexed var status: String = "SENT",
    val attachments: List<MessageAttachment> = emptyList(),
    val replyToMessageUuid: String? = null,
    var forwardCount: Int = 0,
    val reactions: List<MessageReaction> = emptyList(),
    @Indexed var isPinned: Boolean = false,
    var pinnedAt: Instant? = null,
    var pinnedBy: String? = null,
    var deletedForSenderAt: Instant? = null,
    var deletedForEveryoneAt: Instant? = null,
    var editVersion: Int = 1,
    var originalCreatedAt: Instant? = null,
    var disappearAfterSeconds: Int? = null,
    var disappearAt: Instant? = null,
    var viewOnce: Boolean = false,
    val createdAt: Instant = Instant.now(),
    var deliveredAt: Instant? = null,
    var readAt: Instant? = null,
    var editedAt: Instant? = null
)

// ════════════════════════════════════════════════════
// 🔔 أرشيف الإشعارات — للبحث والتتبع
// ════════════════════════════════════════════════════

@Document("notification_archive")
@CompoundIndex(name = "user_time", def = "{'userId': 1, 'createdAt': -1}")
data class NotificationArchiveDocument(
    @Id val id: String,
    @Indexed val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val senderId: String? = null,
    val senderName: String? = null,
    val threadId: String? = null,
    val priority: String = "NORMAL",
    val actionLabel: String? = null,
    val actionData: String? = null, // JSON
    val isRead: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val readAt: Instant? = null
)

// ════════════════════════════════════════════════════
// 📌 الرسائل المثبتة — مرآة سريعة لـ pinned_messages (Postgres) في MongoDB
// ════════════════════════════════════════════════════

@Document("pinned_messages")
@CompoundIndex(name = "conv_pinned", def = "{'conversationId': 1, 'pinnedAt': -1}")
data class PinnedMessageDocument(
    @Id val id: String,
    @Indexed val conversationId: String? = null,
    @Indexed val groupId: String? = null,
    @Indexed val channelId: String? = null,
    @Indexed(unique = true) val messageUuid: String,
    val messageType: String = "PRIVATE",
    @Indexed val pinnedBy: String,
    val pinnedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val displayOrder: Int = 0
)

// ════════════════════════════════════════════════════
// 📝 سجل تعديلات الرسائل — للتدقيق والتراجع
// ════════════════════════════════════════════════════

@Document("message_edit_history")
@CompoundIndex(name = "message_version", def = "{'messageUuid': 1, 'editedAt': -1}")
data class MessageEditHistoryDocument(
    @Id val id: String,
    @Indexed val messageUuid: String,
    val conversationId: String? = null,
    val groupId: String? = null,
    @Indexed val editorId: String,
    val previousPayload: ByteArray? = null,
    val editVersion: Int = 1,
    val editReason: String? = null,
    val editedAt: Instant = Instant.now()
)

// ════════════════════════════════════════════════════
// 📢 القنوات — بث أحادي لجمهور كبير (مثل تيليجرام)
// ════════════════════════════════════════════════════

@Document("channels")
data class ChannelDocument(
    @Id val id: String,
    @Indexed val name: String,
    @Indexed(unique = true) val username: String? = null,
    val description: String? = null,
    val avatarMediaKey: String? = null,
    @Indexed val ownerId: String,
    val isPublic: Boolean = true,
    val isVerified: Boolean = false,
    var subscriberCount: Int = 0,
    var messageCount: Int = 0,
    val allowComments: Boolean = false,
    val allowReactions: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

@Document("channel_members")
@CompoundIndex(name = "channel_user", def = "{'channelId': 1, 'userId': 1}")
data class ChannelMemberDocument(
    @Id val id: String,
    @Indexed val channelId: String,
    @Indexed val userId: String,
    val role: String = "SUBSCRIBER", // OWNER, ADMIN, MODERATOR, SUBSCRIBER
    val isMuted: Boolean = false,
    val isBanned: Boolean = false,
    val joinedAt: Instant = Instant.now(),
    var lastReadMessageId: String? = null
)

@Document("channel_messages")
@CompoundIndex(name = "channel_seq", def = "{'channelId': 1, 'sequenceNumber': -1}")
data class ChannelMessageDocument(
    @Id val id: String? = null,
    @Indexed(unique = true) val uuid: String,
    @Indexed val channelId: String,
    @Indexed val senderId: String,
    val senderDeviceId: Int,
    var payload: ByteArray,
    val messageType: String = "TEXT",
    val ciphertextType: Int,
    val sequenceNumber: Long = 0,
    var status: String = "SENT",
    val attachments: List<MessageAttachment> = emptyList(),
    var forwardCount: Int = 0,
    val reactions: List<MessageReaction> = emptyList(),
    var isPinned: Boolean = false,
    var pinnedAt: Instant? = null,
    var viewCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    var editedAt: Instant? = null,
    var deletedAt: Instant? = null
)

// ════════════════════════════════════════════════════
// 📝 ملاحظة لنفسي — إعدادات محادثة الذات
// ════════════════════════════════════════════════════

@Document("note_to_self")
data class NoteToSelfDocument(
    @Id val userId: String,
    val conversationId: String,
    val isPinned: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

// ════════════════════════════════════════════════════
// ⏰ إعدادات الاختفاء المرن
// ════════════════════════════════════════════════════

@Document("disappearing_settings")
data class DisappearingSettingsDocument(
    @Id val id: String,
    val conversationId: String? = null,
    val groupId: String? = null,
    val channelId: String? = null,
    val userId: String? = null,
    val disappearAfterSeconds: Int = 0, // 0=دائم، 30..604800، -1=بعد القراءة
    val mode: String = "AFTER_SEND",
    val enabled: Boolean = true,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null
)
