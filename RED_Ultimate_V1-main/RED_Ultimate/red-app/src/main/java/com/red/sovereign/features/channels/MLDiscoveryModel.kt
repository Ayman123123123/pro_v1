package com.red.sovereign.features.channels

import kotlinx.serialization.Serializable

/**
 * ML Discovery Models for on-device TensorFlow Lite recommendations
 * Runs entirely on-device for privacy — no user data leaves the device
 */
@Serializable
data class ChannelRecommendation(
    val channel: Channel,
    val score: Float,
    val reason: RecommendationReason,
    val confidence: Float,
    val features: RecommendationFeatures
)

@Serializable
enum class RecommendationReason(val displayName: String) {
    SIMILAR_INTERESTS("اهتمامات مشابهة"),
    TRENDING_IN_AREA("رائج في منطقتك"),
    FRIENDS_SUBSCRIBED("أصدقاؤك مشتركين"),
    HIGH_ENGAGEMENT("تفاعل عالي"),
    NEW_CONTENT("محتوى جديد"),
    CATEGORY_MATCH("فئة مفضلة"),
    CROSS_PROMOTION("ترويج متبادل"),
    REENGAGEMENT("إعادة تفاعل");
}

@Serializable
data class RecommendationFeatures(
    val userCategories: List<String> = emptyList(),
    val userLanguages: List<String> = emptyList(),
    val recentInteractions: List<String> = emptyList(),
    val timeOfDay: Int = 0,
    val dayOfWeek: Int = 0,
    val isNearby: Boolean = false,
    val friendOverlap: Int = 0,
    val engagementVelocity: Float = 0f
)

@Serializable
data class UserEmbedding(
    val vector: FloatArray,
    val lastUpdated: Long,
    val version: Int = 1
)

@Serializable
data class ChannelEmbedding(
    val channelId: String,
    val vector: FloatArray,
    val category: ChannelCategory,
    val tags: List<String>,
    val language: String,
    val updatedAt: Long
)

@Serializable
data class DiscoveryFeed(
    val forYou: List<ChannelRecommendation> = emptyList(),
    val trending: List<ChannelRecommendation> = emptyList(),
    val nearby: List<ChannelRecommendation> = emptyList(),
    val categories: Map<ChannelCategory, List<ChannelRecommendation>> = emptyMap(),
    val lastRefreshed: Long = System.currentTimeMillis()
)

@Serializable
data class SearchSuggestion(
    val text: String,
    val type: SuggestionType,
    val channelId: String? = null,
    val category: ChannelCategory? = null
)

enum class SuggestionType {
    CHANNEL, CATEGORY, TAG, RECENT, TRENDING
}

data class SearchResult(
    val channels: List<Channel> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val correctedQuery: String? = null
)

data class ChannelPeekData(
    val channel: Channel,
    val recentMessages: List<ChannelMessage> = emptyList(),
    val mediaPreview: List<MessageMedia> = emptyList(),
    val memberCount: Int,
    val isLive: Boolean = false,
    val currentViewers: Int = 0
)

data class NotificationSettings(
    val channelId: String,
    val level: NotificationLevel = NotificationLevel.ALL,
    val customKeywords: List<String> = emptyList(),
    val muteUntil: Long? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showPreview: Boolean = true,
    val priorityOnly: Boolean = false
) {
    fun shouldNotify(message: ChannelMessage): Boolean {
        return when (level) {
            NotificationLevel.NONE -> false
            NotificationLevel.MENTIONS -> message.text.contains("@${UserSession.currentUsername}", true) ||
                    customKeywords.any { message.text.contains(it, true) }
            NotificationLevel.ALL -> true
        }
    }
}

object UserSession {
    var currentUserId: String = ""
    var currentUsername: String = ""
    var preferences: UserPreferences = UserPreferences()
}

@Serializable
data class UserPreferences(
    val preferredCategories: List<ChannelCategory> = emptyList(),
    val preferredLanguages: List<String> = listOf("ar", "en"),
    val contentFilter: ContentFilter = ContentFilter.NONE,
    var discoveryEnabled: Boolean = true,
    var nearbyEnabled: Boolean = false,
    var analyticsOptIn: Boolean = false
)

enum class ContentFilter {
    NONE, MATURE, STRICT
}
