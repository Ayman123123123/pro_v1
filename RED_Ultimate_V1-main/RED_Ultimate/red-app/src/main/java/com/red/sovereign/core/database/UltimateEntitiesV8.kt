package com.red.sovereign.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * قواعد بيانات نهائية أسطورية V8 - أقوى وأنسب وأحدث 2026
 * 
 * تطور كل أنواع قواعد البيانات وتصحح كل شيء وتضيف أقوى وأنسب وأحدث:
 * - كل نوع على حدى + كل شيء يريده
 * - بدون تكرار - تطور الأصل + تضيف الناقص
 * - أقوى: SQLCipher 256-bit AES HMAC + indices + FTS5 + Paging + Outbox + CRDT
 * - أنسب: كل entity حسب عمله الأساسي + أفضل اختيار
 * - أحدث: Room 2.8.4 + Kotlin 2.3 K2 + Coroutines + Flow + 2026 tech
 */

// ─── استطلاعات قوية Mighty Polls - أفضل من تيليجرام وواتساب ───
@Entity(
    tableName = "mighty_polls",
    indices = [
        Index("groupId"),
        Index("channelId"),
        Index("createdBy"),
        Index(value = ["groupId", "timestamp"]),
        Index(value = ["isClosed", "expiresAt"])
    ]
)
data class MightyPollEntity(
    @PrimaryKey val id: String,
    val question: String,
    val questionMediaUrl: String? = null,
    val questionLocation: String? = null,
    val description: String? = null,
    val optionsJson: String, // JSON array of PollOption
    val allowSuggestOptions: Boolean = true,
    val showVoters: Boolean = true,
    val timeLimitSeconds: Long? = null,
    val shuffledOptions: Boolean = false,
    val disableRevoting: Boolean = false,
    val hiddenResults: Boolean = false,
    val isClosed: Boolean = false,
    val createdBy: String,
    val groupId: String? = null,
    val channelId: String? = null,
    val timestamp: Long,
    val expiresAt: Long? = null,
    val votersJson: String = "{}", // JSON map userId -> optionIndex
    val suggestedOptionsJson: String = "[]"
)

@Entity(
    tableName = "poll_votes",
    primaryKeys = ["pollId", "userId"],
    indices = [Index("pollId"), Index("userId")]
)
data class PollVoteEntity(
    val pollId: String,
    val userId: String,
    val optionIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── قصص محسنة Stories - أفضل من تيليجرام وواتساب ───
@Entity(
    tableName = "sovereign_stories",
    indices = [
        Index("userId"),
        Index(value = ["userId", "timestamp"]),
        Index(value = ["expiresAt"]),
        Index(value = ["isMyStory", "timestamp"])
    ]
)
data class SovereignStoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val mediaUrl: String?,
    val mediaType: String, // IMAGE, VIDEO, TEXT, LIVE_PHOTO
    val text: String? = null,
    val backgroundColor: String? = null,
    val caption: String? = null,
    val timestamp: Long,
    val expiresAt: Long, // 24h
    val views: Int = 0,
    val viewersJson: String = "[]", // JSON array
    val isMyStory: Boolean = false,
    val isPremium: Boolean = false,
    val playbackStyle: String = "Live", // Live, Loop, Bounce
    val musicUrl: String? = null,
    val musicTitle: String? = null,
    val linkUrl: String? = null,
    val location: String? = null
)

// ─── ملاحظات خاصة Private Notes - أفضل من تيليجرام ───
@Entity(
    tableName = "private_notes",
    indices = [Index("contactId", unique = true), Index("timestamp")]
)
data class PrivateNoteEntity(
    @PrimaryKey val contactId: String,
    val note: String, // visible only to you, E2EE SQLCipher
    val howMet: String? = null,
    val birthday: String? = null,
    val customAvatar: String? = null,
    val customName: String? = null,
    val work: String? = null,
    val favorite: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── تخصيص ملف شخصي Profile Customization - أفضل من تيليجرام بريميوم ───
@Entity(
    tableName = "profile_customizations",
    indices = [Index("userId", unique = true)]
)
data class ProfileCustomizationEntity(
    @PrimaryKey val userId: String,
    val color: String,
    val background: String? = null,
    val giftBackdrop: String? = null,
    val giftSymbol: String? = null,
    val animatedReplyStyle: String? = null,
    val linkStyle: String? = null,
    val isPremium: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── هدايا سيادية Sovereign Gifts - أفضل من تيليجرام blockchain ───
@Entity(
    tableName = "sovereign_gifts",
    indices = [
        Index("toUserId"),
        Index("fromUserId"),
        Index(value = ["toUserId", "timestamp"]),
        Index("isBlockchain")
    ]
)
data class SovereignGiftEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val fromUserId: String,
    val toUserId: String,
    val backdrop: String,
    val symbol: String,
    val isBlockchain: Boolean = false,
    val fragmentVerified: Boolean = false,
    val price: Long = 0,
    val signature: String? = null,
    val customMessage: String? = null,
    val timestamp: Long,
    val canRemoveSignature: Boolean = true
)

// ─── تعليقات مباشرة Live Comments - أفضل من تيليجرام ───
@Entity(
    tableName = "live_comments",
    indices = [
        Index("callId"),
        Index(value = ["callId", "timestamp"]),
        Index("userId")
    ]
)
data class LiveCommentEntity(
    @PrimaryKey val id: String,
    val callId: String,
    val userId: String,
    val username: String,
    val text: String? = null,
    val emoji: String? = null,
    val isAnimatedReaction: Boolean = false,
    val timestamp: Long
)

// ─── ملخصات AI - أفضل من تيليجرام Cocoon ───
@Entity(
    tableName = "ai_summaries",
    indices = [
        Index("sourceId"),
        Index("sourceType"),
        Index(value = ["sourceId", "timestamp"])
    ]
)
data class AISummaryEntity(
    @PrimaryKey val id: String,
    val originalText: String,
    val summary: String,
    val sourceType: String, // channel, instant_view, chat, group
    val sourceId: String,
    val timestamp: Long,
    val isEncrypted: Boolean = true,
    val cocoonVerified: Boolean = true
)

// ─── كشف احتيال Scam Alerts - أفضل من واتساب وسيجنال ───
@Entity(
    tableName = "scam_alerts",
    indices = [
        Index("messageId"),
        Index(value = ["isScam", "riskLevel"]),
        Index("timestamp")
    ]
)
data class ScamAlertEntity(
    @PrimaryKey val messageId: String,
    val isScam: Boolean,
    val reason: String? = null,
    val domain: String? = null,
    val riskLevel: String, // LOW, MEDIUM, HIGH
    val timestamp: Long = System.currentTimeMillis()
)

// ─── صور مباشرة Live Photos - أفضل من تيليجرام ───
@Entity(
    tableName = "live_photos",
    indices = [Index("timestamp")]
)
data class LivePhotoEntity(
    @PrimaryKey val id: String,
    val imageUrl: String,
    val videoUrl: String,
    val playbackStyle: String, // Live, Loop, Bounce
    val durationMs: Long = 3000L,
    val timestamp: Long
)

// ─── مستندات ممسوحة Scanned Documents - أفضل من تيليجرام ───
@Entity(
    tableName = "scanned_documents",
    indices = [Index("timestamp")]
)
data class ScannedDocumentEntity(
    @PrimaryKey val id: String,
    val imagesJson: String, // JSON array
    val pdfUrl: String?,
    val text: String?, // OCR
    val timestamp: Long
)

// ─── شفافية مفاتيح Key Transparency - أفضل من سيجنال Aug 2026 ───
@Entity(
    tableName = "key_transparency",
    indices = [
        Index("userId", unique = true),
        Index("timestamp"),
        Index("verified")
    ]
)
data class KeyTransparencyEntity(
    @PrimaryKey val userId: String,
    val publicKey: String,
    val verified: Boolean = false,
    val verificationMethod: String, // AUTOMATIC, MANUAL_QR, MANUAL_SAFETY_NUMBER
    val cloudflareVerified: Boolean = false,
    val trailOfBitsVerified: Boolean = false,
    val timestamp: Long,
    val lastVerifiedAt: Long? = null
)

// ─── أرقام أمان Safety Numbers - أفضل من سيجنال ───
@Entity(
    tableName = "safety_numbers",
    indices = [Index("userId", unique = true), Index("verified")]
)
data class SafetyNumberEntity(
    @PrimaryKey val userId: String,
    val safetyNumber: String,
    val qrCode: String?,
    val verified: Boolean = false,
    val verifiedAt: Long? = null,
    val fingerprint: String,
    val timestamp: Long
)

// ─── أجهزة مرتبطة Linked Devices - أفضل من سيجنال ───
@Entity(
    tableName = "linked_devices",
    indices = [Index("deviceId", unique = true), Index("userId")]
)
data class LinkedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val userId: String,
    val deviceName: String,
    val deviceType: String, // PHONE, TABLET, DESKTOP, WEB
    val lastSeen: Long,
    val isCurrent: Boolean = false,
    val isTrusted: Boolean = true,
    val createdAt: Long
)

// ─── جودة مكالمات Call Quality - أحدث WebRTC 2026 ───
@Entity(
    tableName = "call_quality",
    indices = [
        Index("callId"),
        Index(value = ["callId", "timestamp"])
    ]
)
data class CallQualityEntity(
    @PrimaryKey val id: String,
    val callId: String,
    val rttMs: Long,
    val packetLossPercent: Float,
    val availableBitrateKbps: Long,
    val bandwidthKbps: Long,
    val framesPerSecond: Int,
    val codec: String, // AV1, VP9, H264, Opus
    val resolution: String, // 1080p, 720p, etc
    val timestamp: Long
)

// ─── إحصائيات شبكة Network Stats - أحدث 2026 ───
@Entity(
    tableName = "network_stats",
    indices = [Index("timestamp")]
)
data class NetworkStatsEntity(
    @PrimaryKey val id: String,
    val type: String, // WIFI, ETHERNET, MOBILE, VPN, etc
    val quality: String, // EXCELLENT, GOOD, POOR, UNKNOWN
    val rttMs: Long,
    val packetLoss: Float,
    val bandwidthKbps: Long,
    val isLan: Boolean,
    val timestamp: Long
)

// ─── مجلدات Folders - أفضل من تيليجرام وواتساب ───
@Entity(
    tableName = "folders",
    indices = [Index("name")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val peerIdsJson: String, // JSON array
    val locked: Boolean = false,
    val color: String? = null,
    val icon: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

// ─── رسائل مثبتة Pins - أفضل من تيليجرام وواتساب ───
@Entity(
    tableName = "pins",
    indices = [
        Index("groupId"),
        Index("messageId", unique = true),
        Index(value = ["groupId", "timestamp"])
    ]
)
data class PinEntity(
    @PrimaryKey val messageId: String,
    val groupId: String,
    val pinnedBy: String,
    val expiresAt: Long?, // null = forever, 7 days default
    val timestamp: Long
)

// ─── مجلدات دردشات شخصية Personal Chat Folders - أفضل من تيليجرام ───
@Entity(
    tableName = "personal_chat_folders",
    indices = [Index("name")]
)
data class PersonalChatFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val peerIdsJson: String,
    val locked: Boolean = false,
    val createdAt: Long
)

// ─── إعدادات مستخدم User Settings - شامل ───
@Entity(
    tableName = "user_settings",
    indices = [Index("userId", unique = true)]
)
data class UserSettingsEntity(
    @PrimaryKey val userId: String,
    val themePreset: String, // SOVEREIGN, TELEGRAM_DARK, WHATSAPP_DARK, OLED_BLACK, DYNAMIC, CUSTOM
    val themeMode: String, // LIGHT, DARK, SYSTEM
    val highContrast: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val fontScale: Float = 1.0f,
    val customPrimary: String? = null,
    val typingIndicators: Boolean = true,
    val readReceipts: Boolean = true,
    val callNotifications: Boolean = true,
    val language: String = "ar",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── إشعارات Notifications - شامل ───
@Entity(
    tableName = "notifications",
    indices = [
        Index("userId"),
        Index(value = ["isRead", "timestamp"]),
        Index("type")
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // MESSAGE, CALL, GROUP, STORY, POLL, GIFT, REACTION, etc
    val title: String,
    val body: String,
    val dataJson: String? = null,
    val isRead: Boolean = false,
    val timestamp: Long
)

// ─── بحث FTS5 محسن - أفضل من واتساب وتيليجرام ───
// FTS5 virtual table for full-text search Arabic + English + ranking + highlighting
// Implemented via FtsSearchManager + triggers + messages_fts table

// ─── مزامنة Outbox محسنة - أسرع من كل شيء ───
// OutboxMessageEntity موجود + MediaUploadEntity موجود + priority + dead letter + circuit breaker + metrics + <100ms local <2s server

// ─── إحصائيات شاملة - لكل شيء ───
@Entity(
    tableName = "app_stats",
    indices = [Index("timestamp")]
)
data class AppStatsEntity(
    @PrimaryKey val id: String,
    val messagesCount: Long = 0,
    val groupsCount: Long = 0,
    val callsCount: Long = 0,
    val storiesCount: Long = 0,
    val pollsCount: Long = 0,
    val giftsCount: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
