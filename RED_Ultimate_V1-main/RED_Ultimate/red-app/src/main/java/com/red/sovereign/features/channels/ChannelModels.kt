package com.red.sovereign.features.channels

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val ownerId: String = "",
    val ownerUsername: String? = null,
    val isPublic: Boolean = true,
    val subscriberCount: Int = 0,
    val isBroadcast: Boolean = true,
    val boostsCount: Int = 0,
    val coverImageUrl: String? = null,
    val avatarUrl: String? = null,
    val verificationBadge: VerificationBadge? = null,
    val category: ChannelCategory = ChannelCategory.GENERAL,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isMuted: Boolean = false,
    val notificationLevel: NotificationLevel = NotificationLevel.ALL,
    val myRole: String? = null,
    val isJoined: Boolean = false,
    val viewCount: Long = 0,
    val growthStats: GrowthStats? = null,
    val scheduledMessageCount: Int = 0,
    val mediaCount: MediaCount = MediaCount(),
    val lastMessageAt: String? = null,
) {
    val level: Int get() = levelForBoosts(boostsCount)
    val perks: ChannelLevelPerks get() = perksForLevel(level)
}

@Serializable
enum class ChannelCategory(val displayName: String, val icon: String) {
    NEWS("أخبار", "📰"),
    TECH("تقنية", "💻"),
    CRYPTO("عملات", "₿"),
    GAMING("ألعاب", "🎮"),
    LOCAL("محلي", "📍"),
    VERIFIED("موثق", "✅"),
    GENERAL("عام", "📢"),
    ENTERTAINMENT("ترفيه", "🎭"),
    EDUCATION("تعليم", "📚"),
    SPORTS("رياضة", "⚽"),
    FINANCE("مال", "💰"),
    HEALTH("صحة", "🏥"),
}

@Serializable
enum class VerificationBadge(val label: String, val color: String) {
    NONE("بدون", "#7A8590"),
    VERIFIED("موثق", "#2196F3"),
    OFFICIAL("رسمي", "#4CAF50"),
    PARTNER("شريك", "#9C27B0"),
}

@Serializable
enum class NotificationLevel(val label: String) {
    ALL("الكل"),
    MENTIONS("المنشنات فقط"),
    NONE("صامت"),
}

@Serializable
data class GrowthStats(
    val dailyGrowth: List<GrowthPoint> = emptyList(),
    val weeklyGrowth: List<GrowthPoint> = emptyList(),
    val monthlyGrowth: List<GrowthPoint> = emptyList(),
    val velocity: Double = 0.0,
)

@Serializable
data class GrowthPoint(
    val date: String,
    val subscribers: Int,
    val views: Long,
)

@Serializable
data class MediaCount(
    val photos: Int = 0,
    val videos: Int = 0,
    val files: Int = 0,
    val links: Int = 0,
    val voice: Int = 0,
)

@Serializable
data class ChannelMessage(
    val id: String,
    val channelId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String? = null,
    val authorRole: String = "SUBSCRIBER",
    val text: String,
    val media: List<MessageMedia> = emptyList(),
    val replyToId: String? = null,
    val threadId: String? = null,
    val reactions: Map<String, Reaction> = emptyMap(),
    val forwardCount: Int = 0,
    val viewCount: Long = 0,
    val isPinned: Boolean = false,
    val isScheduled: Boolean = false,
    val scheduledAt: String? = null,
    val sentAt: String,
    val editedAt: String? = null,
    val poll: PollData? = null,
    val isTranslated: Boolean = false,
    val originalText: String? = null,
)

@Serializable
data class MessageMedia(
    val type: MediaType,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val waveformData: List<Float>? = null,
)

@Serializable
enum class MediaType {
    IMAGE, VIDEO, FILE, LINK, VOICE, POLL
}

@Serializable
data class Reaction(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean = false,
    val users: List<String> = emptyList(),
)

@Serializable
data class PollData(
    val id: String,
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int = 0,
    val userVoted: Boolean = false,
    val userVote: String? = null,
    val endsAt: String? = null,
    val isAnonymous: Boolean = false,
    val allowMultiple: Boolean = false,
)

@Serializable
data class PollOption(
    val id: String,
    val text: String,
    val votes: Int = 0,
    val userVoted: Boolean = false,
)

@Serializable
data class ChannelAnalytics(
    val channelId: String,
    val totalViews: Long,
    val totalSubscribers: Int,
    val avgViewsPerPost: Double,
    val engagementRate: Double,
    val topPosts: List<ChannelMessage> = emptyList(),
    val audienceDemographics: AudienceDemographics = AudienceDemographics(),
    val growthChart: GrowthStats = GrowthStats(),
    val bestPostingTimes: List<PostingTime> = emptyList(),
)

@Serializable
data class AudienceDemographics(
    val countries: Map<String, Int> = emptyMap(),
    val languages: Map<String, Int> = emptyMap(),
    val devices: Map<String, Int> = emptyMap(),
    val ageGroups: Map<String, Int> = emptyMap(),
)

@Serializable
data class PostingTime(
    val hour: Int,
    val dayOfWeek: Int,
    val avgEngagement: Double,
)

@Serializable
data class ModerationQueueItem(
    val id: String,
    val type: ModerationType,
    val messageId: String,
    val messagePreview: String,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val createdAt: String,
    val status: ModerationStatus = ModerationStatus.PENDING,
)

@Serializable
enum class ModerationType {
    SPAM, HARASSMENT, ILLEGAL, COPYRIGHT, MISINFORMATION, OTHER
}

@Serializable
enum class ModerationStatus {
    PENDING, APPROVED, REJECTED, ESCALATED
}

@Serializable
data class BannedUser(
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val bannedAt: String,
    val bannedBy: String,
    val reason: String,
    val expiresAt: String? = null,
    val isPermanent: Boolean = false,
)

@Serializable
data class ChannelRole(
    val name: String,
    val color: String,
    val permissions: Set<ChannelPermission>,
    val isDefault: Boolean = false,
    val hierarchy: Int = 0,
)

@Serializable
enum class ChannelPermission {
    SEND_MESSAGES,
    SEND_MEDIA,
    PIN_MESSAGES,
    MANAGE_MESSAGES,
    MANAGE_CHANNEL,
    MANAGE_ROLES,
    BAN_USERS,
    VIEW_ANALYTICS,
    MANAGE_INVITES,
    START_VOICE_CHAT,
    MANAGE_EVENTS,
}

@Serializable
data class InviteLink(
    val code: String,
    val url: String,
    val qrCodeUrl: String,
    val createdBy: String,
    val createdAt: String,
    val expiresAt: String? = null,
    val maxUses: Int = 0,
    val currentUses: Int = 0,
    val isRevoked: Boolean = false,
)

@Serializable
data class ChannelSearchResult(
    val channels: List<Channel> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val trendingCategories: List<ChannelCategory> = emptyList(),
)

@Serializable
data class NearbyChannel(
    val channel: Channel,
    val distanceKm: Double,
    val isOptedIn: Boolean = true,
)

// NOTE: on-device ML discovery (ChannelRecommendation/RecommendationReason/
// MLDiscoveryEngine) was removed 2026-09-21 as unwired dead code — no active
// screen or repository referenced it. Reactions/threads are served by
// MessageContent.MessageReactions + RedDao.getThreadReplies instead.

@Serializable
data class ChannelSettings(
    val channelId: String,
    val slowModeSec: Int = 0,
    val antiSpamEnabled: Boolean = true,
    val linkPreviewEnabled: Boolean = true,
    val reactionsEnabled: Boolean = true,
    val threadsEnabled: Boolean = true,
    val translationEnabled: Boolean = true,
    val voiceMessagesEnabled: Boolean = true,
    val mediaDownloadEnabled: Boolean = true,
    val forwardEnabled: Boolean = true,
    val copyEnabled: Boolean = true,
    val screenshotProtection: Boolean = false,
    autoDeleteTimerDays: Int = 0,
)
