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
    // التفاعل
    val reactions: List<MessageReaction> = emptyList(),
    // الحذف
    var deletedForSenderAt: Instant? = null,
    var deletedForEveryoneAt: Instant? = null,
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
// 📞 سجل المكالمات — سريع التغير
// ════════════════════════════════════════════════════

@Document("call_history")
@CompoundIndex(name = "participants_time", def = "{'initiatorId': 1, 'startedAt': -1}")
data class CallHistoryDocument(
    @Id val id: String,
    @Indexed val initiatorId: String,
    @Indexed val targetId: String,
    val targetLabel: String,
    val type: CallType,
    val route: CallRoute,
    var status: CallStatus,
    // التفاصيل
    var durationMs: Long = 0,
    val dinstarPort: Int? = null,
    var signalStrength: Int? = null,
    var viewerCount: Int = 0,
    var isRecorded: Boolean = false,
    val recordingMediaKey: String? = null,
    // المشاركون
    val participants: List<CallParticipant> = emptyList(),
    // التوقيتات
    val startedAt: Instant = Instant.now(),
    var answeredAt: Instant? = null,
    var endedAt: Instant? = null
)

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
// 📖 القصص — تنتهي صلاحيتها بعد 24 ساعة
// ════════════════════════════════════════════════════

@Document("stories")
@CompoundIndex(name = "owner_expires", def = "{'ownerId': 1, 'expiresAt': 1}", )
data class StoryDocument(
    @Id val id: String,
    @Indexed val ownerId: String,
    val ownerRedId: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val mediaKey: String,
    @Indexed val mediaType: String, // IMAGE, VIDEO, TEXT, VOICE
    val caption: String?,
    val backgroundColor: String?, // للنص: #D32F2F, #1565C0, etc.
    // الخصوصية
    @Indexed val visibleTo: String = "EVERYONE", // EVERYONE, CONTACTS, NOBODY
    val excludedUsers: List<String> = emptyList(),
    val includedUsers: List<String> = emptyList(),
    // التوقيتات
    val createdAt: Instant = Instant.now(),
    @Indexed(expireAfterSeconds = 86400) val expiresAt: Instant, // TTL index
    var deletedAt: Instant? = null
)

@Document("story_views")
data class StoryView(
    @Id val id: String,
    @Indexed val storyId: String,
    @Indexed val viewerId: String,
    val viewedAt: Instant = Instant.now(),
    val reaction: String? = null // ❤️ 🔥 😢 etc.
)

@Document("story_reactions")
data class StoryReaction(
    @Id val id: String,
    @Indexed val storyId: String,
    @Indexed val userId: String,
    val emoji: String,
    val createdAt: Instant = Instant.now()
)

// ════════════════════════════════════════════════════
// 📝 المنشورات — مرنة البنية
// ════════════════════════════════════════════════════

@Document("posts")
data class PostDocument(
    @Id val id: String,
    @Indexed val authorId: String,
    @Indexed val authorRedId: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val text: String,
    @Indexed val visibility: PostVisibility,
    val kind: PostKind = PostKind.POST,
    @Indexed val parentId: String? = null,  // رد
    val quotePostId: String? = null,        // اقتباس
    val poll: Poll? = null,
    val media: List<PostMedia> = emptyList(),
    // الخصوصية المتقدمة
    val visibleTo: String = "EVERYONE",
    val excludedUsers: List<String> = emptyList(),
    // التفاعل
    val reactionCounts: Map<String, Long> = emptyMap(),
    var replyCount: Long = 0,
    var repostCount: Long = 0,
    // التوقيتات
    @Indexed val createdAt: Instant = Instant.now(),
    val editedAt: Instant? = null,
    @Indexed val deletedAt: Instant? = null
)

enum class PostVisibility { PUBLIC, LOCAL_YEMEN, FOLLOWERS, PRIVATE }
enum class PostKind { POST, POLL }
data class PostMedia(val objectKey: String, val mimeType: String, val width: Int? = null, val height: Int? = null)
data class Poll(val options: List<PollOption>, val expiresAt: Instant?, val multiChoice: Boolean = false)
data class PollOption(val id: String, val text: String, val votes: Long = 0)

@Document("post_reactions")
data class PostReaction(@Id val id: String, @Indexed val postId: String, @Indexed val userId: String, val type: String, val createdAt: Instant = Instant.now())

@Document("poll_votes")
data class PollVote(@Id val id: String, @Indexed val postId: String, @Indexed val userId: String, @Indexed val optionId: String, val createdAt: Instant = Instant.now())

@Document("follows")
data class FollowDocument(@Id val id: String, @Indexed val followerId: String, @Indexed val followedId: String, val createdAt: Instant = Instant.now())

// ════════════════════════════════════════════════════
// 📡 البث المباشر والغرف الصوتية
// ════════════════════════════════════════════════════

@Document("live_streams")
data class LiveStreamDocument(
    @Id val id: String,
    @Indexed val hostId: String,
    val hostRedId: String,
    val hostUsername: String,
    val title: String,
    val category: String? = null,
    @Indexed var status: String = "LIVE", // LIVE, ENDED
    var viewerCount: Int = 0,
    val maxViewers: Int = 0,
    val mediaKey: String? = null, // تسجيل البث
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null
)

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
    val reactions: List<MessageReaction> = emptyList(),
    var deletedForSenderAt: Instant? = null,
    var deletedForEveryoneAt: Instant? = null,
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
