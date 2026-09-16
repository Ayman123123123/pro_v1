package com.red.sovereign.features.channels

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Channels API - Android client with competitive features
 * Talks to /api/channels on the backend
 * Real data only — no fixtures, no mock data
 */

@Serializable
data class CreateChannelBody(
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val isPublic: Boolean = true,
    val isBroadcast: Boolean = true,
    val category: String = "GENERAL",
    val tags: List<String> = emptyList(),
    val coverImageUrl: String? = null,
)

@Serializable
data class UpdateChannelBody(
    val name: String? = null,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val category: String? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean? = null,
    val isBroadcast: Boolean? = null,
    val slowModeSec: Int? = null,
    val settings: ChannelSettings? = null,
)

@Serializable
data class ChannelSettings(
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
    val autoDeleteTimerDays: Int = 0,
)

@Serializable
private data class ChannelListEnvelope(
    val channels: List<Channel> = emptyList(),
    val count: Int = 0,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
private data class ChannelCreateEnvelope(
    val success: Boolean = false,
    val channel: Channel? = null,
)

@Serializable
private data class SearchEnvelope(
    val channels: List<Channel> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val trendingCategories: List<String> = emptyList(),
)

@Serializable
private data class TrendingEnvelope(
    val channels: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(),
)

@Serializable
private data class NearbyEnvelope(
    val channels: List<NearbyChannel> = emptyList(),
)

@Serializable
private data class RecommendationsEnvelope(
    val channels: List<ChannelRecommendation> = emptyList(),
)

@Serializable
private data class AnalyticsEnvelope(
    val analytics: ChannelAnalytics,
)

@Serializable
private data class ModerationEnvelope(
    val items: List<ModerationQueueItem> = emptyList(),
    val totalCount: Int = 0,
)

@Serializable
private data class BannedUsersEnvelope(
    val users: List<BannedUser> = emptyList(),
)

@Serializable
private data class InviteEnvelope(
    val invite: InviteLink,
)

@Serializable
private data class InvitesEnvelope(
    val invites: List<InviteLink> = emptyList(),
)

@Serializable
private data class MediaEnvelope(
    val photos: List<MessageMedia> = emptyList(),
    val videos: List<MessageMedia> = emptyList(),
    val files: List<MessageMedia> = emptyList(),
    val links: List<MessageMedia> = emptyList(),
    val voice: List<MessageMedia> = emptyList(),
    val totalCount: Int = 0,
)

class ChannelsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun list(
        search: String? = null,
        category: String? = null,
        tags: List<String> = emptyList(),
        sort: String = "subscribers",
        cursor: String? = null,
        limit: Int = 30,
    ): ApiResult<ChannelListResult> {
        val qs = buildString {
            append("?limit=").append(limit.coerceIn(1, 100))
            append("&sort=").append(sort)
            if (!search.isNullOrBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search.trim(), "UTF-8"))
            }
            if (!category.isNullOrBlank()) {
                append("&category=").append(category)
            }
            if (tags.isNotEmpty()) {
                append("&tags=").append(tags.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") })
            }
            if (!cursor.isNullOrBlank()) {
                append("&cursor=").append(cursor)
            }
        }
        val raw = when (val r = client.request("GET", "/api/channels$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<ChannelListEnvelope>(raw)
            ApiResult.Success(200, ChannelListResult(
                channels = env.channels,
                hasMore = env.hasMore,
                nextCursor = env.nextCursor,
                totalCount = env.count,
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getRecommendations(limit: Int = 20): ApiResult<List<ChannelRecommendation>> {
        val raw = when (val r = client.request("GET", "/api/channels/recommendations?limit=$limit")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<RecommendationsEnvelope>(raw).channels)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getTrending(limit: Int = 20): ApiResult<List<Channel>> {
        val raw = when (val r = client.request("GET", "/api/channels/trending?limit=$limit")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<TrendingEnvelope>(raw).channels)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getNearby(lat: Double, lng: Double, radiusKm: Int = 50, limit: Int = 20): ApiResult<List<NearbyChannel>> {
        val raw = when (val r = client.request("GET", "/api/channels/nearby?lat=$lat&lng=$lng&radius=$radiusKm&limit=$limit")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<NearbyEnvelope>(raw).channels)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun search(
        query: String,
        category: String? = null,
        cursor: String? = null,
        limit: Int = 30,
    ): ApiResult<ChannelSearchResult> {
        val qs = buildString {
            append("?query=").append(java.net.URLEncoder.encode(query.trim(), "UTF-8"))
            append("&limit=").append(limit.coerceIn(1, 100))
            if (!category.isNullOrBlank()) {
                append("&category=").append(category)
            }
            if (!cursor.isNullOrBlank()) {
                append("&cursor=").append(cursor)
            }
        }
        val raw = when (val r = client.request("GET", "/api/channels/search$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<SearchEnvelope>(raw)
            ApiResult.Success(200, ChannelSearchResult(
                channels = env.channels,
                totalCount = env.totalCount,
                hasMore = env.hasMore,
                suggestions = env.suggestions,
                trendingCategories = env.trendingCategories.map { ChannelCategory.valueOf(it) },
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun details(id: String): ApiResult<Channel> {
        val raw = when (val r = client.request("GET", "/api/channels/$id")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Channel>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun create(body: CreateChannelBody): ApiResult<Channel> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<ChannelCreateEnvelope>(raw)
            val channel = env.channel
            when {
                channel != null -> ApiResult.Success(200, channel)
                else -> ApiResult.Error(400, "CHANNEL_CREATE_REJECTED")
            }
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun update(id: String, body: UpdateChannelBody): ApiResult<Channel> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("PATCH", "/api/channels/$id", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Channel>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun delete(id: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$id")

    suspend fun join(id: String): ApiResult<String> =
        client.request("POST", "/api/channels/$id/join")

    suspend fun leave(id: String): ApiResult<String> =
        client.request("POST", "/api/channels/$id/leave")

    suspend fun mute(id: String, level: NotificationLevel): ApiResult<String> {
        val body = json.encodeToString(mapOf("notificationLevel" to level.name)).toRequestBody()
        return client.requestBody("POST", "/api/channels/$id/mute", body)
    }

    suspend fun boost(id: String, count: Int = 1): ApiResult<Channel> {
        val body = json.encodeToString(mapOf("count" to count)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$id/boost", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Channel>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getAnalytics(id: String): ApiResult<ChannelAnalytics> {
        val raw = when (val r = client.request("GET", "/api/channels/$id/analytics")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<AnalyticsEnvelope>(raw).analytics)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getModerationQueue(id: String, status: String? = null, page: Int = 0, size: Int = 20): ApiResult<ModerationQueueResult> {
        val qs = buildString {
            append("?page=$page&size=$size")
            if (!status.isNullOrBlank()) append("&status=$status")
        }
        val raw = when (val r = client.request("GET", "/api/channels/$id/moderation$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<ModerationEnvelope>(raw)
            ApiResult.Success(200, ModerationQueueResult(
                items = env.items,
                totalCount = env.totalCount,
                hasMore = env.items.size >= size,
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun moderateAction(id: String, itemId: String, action: String, reason: String? = null): ApiResult<String> {
        val body = json.encodeToString(mapOf("action" to action, "reason" to reason)).toRequestBody()
        return client.requestBody("POST", "/api/channels/$id/moderation/$itemId", body)
    }

    suspend fun getBannedUsers(id: String): ApiResult<List<BannedUser>> {
        val raw = when (val r = client.request("GET", "/api/channels/$id/banned")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<BannedUsersEnvelope>(raw).users)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun banUser(id: String, userId: String, reason: String, expiresAt: String? = null): ApiResult<BannedUser> {
        val body = json.encodeToString(mapOf("userId" to userId, "reason" to reason, "expiresAt" to expiresAt)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$id/ban", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<BannedUser>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun unbanUser(id: String, userId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$id/ban/$userId")

    suspend fun createInvite(id: String, maxUses: Int = 0, expiresAt: String? = null): ApiResult<InviteLink> {
        val body = json.encodeToString(mapOf("maxUses" to maxUses, "expiresAt" to expiresAt)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$id/invites", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<InviteEnvelope>(raw).invite)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getInvites(id: String): ApiResult<List<InviteLink>> {
        val raw = when (val r = client.request("GET", "/api/channels/$id/invites")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<InvitesEnvelope>(raw).invites)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun revokeInvite(id: String, code: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$id/invites/$code")

    suspend fun getMedia(id: String, type: MediaType? = null, cursor: String? = null, limit: Int = 50): ApiResult<MediaResult> {
        val qs = buildString {
            append("?limit=$limit")
            if (!type.isNullOrBlank()) append("&type=${type.name}")
            if (!cursor.isNullOrBlank()) append("&cursor=$cursor")
        }
        val raw = when (val r = client.request("GET", "/api/channels/$id/media$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<MediaEnvelope>(raw)
            ApiResult.Success(200, MediaResult(
                photos = env.photos,
                videos = env.videos,
                files = env.files,
                links = env.links,
                voice = env.voice,
                totalCount = env.totalCount,
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getMessages(
        channelId: String,
        beforeId: String? = null,
        afterId: String? = null,
        limit: Int = 50,
        threadId: String? = null,
    ): ApiResult<MessagesResult> {
        val qs = buildString {
            append("?limit=$limit")
            if (!beforeId.isNullOrBlank()) append("&before=$beforeId")
            if (!afterId.isNullOrBlank()) append("&after=$afterId")
            if (!threadId.isNullOrBlank()) append("&threadId=$threadId")
        }
        val raw = when (val r = client.request("GET", "/api/channels/$channelId/messages$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val list = json.decodeFromString<List<ChannelMessage>>(raw)
            ApiResult.Success(200, MessagesResult(
                messages = list,
                hasMore = list.size >= limit,
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun sendMessage(channelId: String, body: SendMessageBody): ApiResult<ChannelMessage> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/messages", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelMessage>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun react(channelId: String, messageId: String, emoji: String): ApiResult<Reaction> {
        val body = json.encodeToString(mapOf("emoji" to emoji)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/messages/$messageId/react", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Reaction>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun removeReaction(channelId: String, messageId: String, emoji: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/messages/$messageId/react/$emoji")

    suspend fun forward(channelId: String, messageId: String, targetChannelId: String): ApiResult<String> {
        val body = json.encodeToString(mapOf("targetChannelId" to targetChannelId)).toRequestBody()
        return client.requestBody("POST", "/api/channels/$channelId/messages/$messageId/forward", body)
    }

    suspend fun translate(channelId: String, messageId: String, targetLang: String): ApiResult<String> {
        val body = json.encodeToString(mapOf("targetLang" to targetLang)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/messages/$messageId/translate", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, raw)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun pinMessage(channelId: String, messageId: String): ApiResult<String> =
        client.request("POST", "/api/channels/$channelId/messages/$messageId/pin")

    suspend fun unpinMessage(channelId: String, messageId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/messages/$messageId/pin")

    suspend fun deleteMessage(channelId: String, messageId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/messages/$messageId")

    suspend fun scheduleMessage(channelId: String, body: ScheduleMessageBody): ApiResult<ChannelMessage> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/scheduled", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelMessage>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getScheduledMessages(channelId: String): ApiResult<List<ChannelMessage>> {
        val raw = when (val r = client.request("GET", "/api/channels/$channelId/scheduled")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<List<ChannelMessage>>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun cancelScheduledMessage(channelId: String, messageId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/scheduled/$messageId")

    suspend fun createPoll(channelId: String, body: CreatePollBody): ApiResult<ChannelMessage> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/polls", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelMessage>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun votePoll(channelId: String, messageId: String, optionIds: List<String>): ApiResult<PollData> {
        val body = json.encodeToString(mapOf("optionIds" to optionIds)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/messages/$messageId/vote", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<PollData>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun getMembers(channelId: String, role: String? = null, query: String? = null, page: Int = 0, size: Int = 50): ApiResult<MembersResult> {
        val qs = buildString {
            append("?page=$page&size=$size")
            if (!role.isNullOrBlank()) append("&role=$role")
            if (!query.isNullOrBlank()) append("&query=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}")
        }
        val raw = when (val r = client.request("GET", "/api/channels/$channelId/members$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val list = json.decodeFromString<List<ChannelMember>>(raw)
            ApiResult.Success(200, MembersResult(
                members = list,
                hasMore = list.size >= size,
            ))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun updateMemberRole(channelId: String, userId: String, role: String): ApiResult<ChannelMember> {
        val body = json.encodeToString(mapOf("role" to role)).toRequestBody()
        val raw = when (val r = client.requestBody("PATCH", "/api/channels/$channelId/members/$userId", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelMember>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun kickMember(channelId: String, userId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/members/$userId")

    suspend fun getRoles(channelId: String): ApiResult<List<ChannelRole>> {
        val raw = when (val r = client.request("GET", "/api/channels/$channelId/roles")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<List<ChannelRole>>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun createRole(channelId: String, body: CreateRoleBody): ApiResult<ChannelRole> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/roles", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelRole>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun updateRole(channelId: String, roleId: String, body: UpdateRoleBody): ApiResult<ChannelRole> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("PATCH", "/api/channels/$channelId/roles/$roleId", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelRole>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun deleteRole(channelId: String, roleId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/roles/$roleId")

    suspend fun getEvents(channelId: String): ApiResult<List<CommunityEvent>> {
        val raw = when (val r = client.request("GET", "/api/channels/$channelId/events")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<List<CommunityEvent>>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun createEvent(channelId: String, body: CreateEventBody): ApiResult<CommunityEvent> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/events", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<CommunityEvent>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun updateEvent(channelId: String, eventId: String, body: UpdateEventBody): ApiResult<CommunityEvent> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("PATCH", "/api/channels/$channelId/events/$eventId", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<CommunityEvent>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun deleteEvent(channelId: String, eventId: String): ApiResult<String> =
        client.request("DELETE", "/api/channels/$channelId/events/$eventId")

    suspend fun rsvpEvent(channelId: String, eventId: String, rsvp: EventRsvp): ApiResult<CommunityEvent> {
        val body = json.encodeToString(mapOf("rsvp" to rsvp.name)).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels/$channelId/events/$eventId/rsvp", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<CommunityEvent>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }
}

data class ChannelListResult(
    val channels: List<Channel>,
    val hasMore: Boolean,
    val nextCursor: String?,
    val totalCount: Int,
)

data class MessagesResult(
    val messages: List<ChannelMessage>,
    val hasMore: Boolean,
)

data class MediaResult(
    val photos: List<MessageMedia>,
    val videos: List<MessageMedia>,
    val files: List<MessageMedia>,
    val links: List<MessageMedia>,
    val voice: List<MessageMedia>,
    val totalCount: Int,
)

data class ModerationQueueResult(
    val items: List<ModerationQueueItem>,
    val totalCount: Int,
    val hasMore: Boolean,
)

data class MembersResult(
    val members: List<ChannelMember>,
    val hasMore: Boolean,
)

@Serializable
data class SendMessageBody(
    val text: String,
    val media: List<MessageMedia> = emptyList(),
    val replyToId: String? = null,
    val threadId: String? = null,
    val silent: Boolean = false,
    val scheduleAt: String? = null,
)

@Serializable
data class ScheduleMessageBody(
    val text: String,
    val media: List<MessageMedia> = emptyList(),
    val scheduledAt: String,
    val threadId: String? = null,
)

@Serializable
data class CreatePollBody(
    val question: String,
    val options: List<String>,
    val isAnonymous: Boolean = false,
    val allowMultiple: Boolean = false,
    val endsAt: String? = null,
)

@Serializable
data class CreateRoleBody(
    val name: String,
    val color: String,
    val permissions: List<String>,
    val hierarchy: Int,
)

@Serializable
data class UpdateRoleBody(
    val name: String? = null,
    val color: String? = null,
    val permissions: List<String>? = null,
    val hierarchy: Int? = null,
)

@Serializable
data class ChannelMember(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: String = "SUBSCRIBER",
    val joinedAt: String,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
)

@Serializable
data class CommunityEvent(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startAt: String,
    val endAt: String? = null,
    val isOnline: Boolean = false,
    val meetingUrl: String? = null,
    val maxAttendees: Int = 0,
    val createdBy: String,
    val createdAt: String,
    val rsvpCount: Map<String, Int> = emptyMap(),
    val userRsvp: EventRsvp? = null,
)

@Serializable
data class CreateEventBody(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startAt: String,
    val endAt: String? = null,
    val isOnline: Boolean = false,
    val meetingUrl: String? = null,
    val maxAttendees: Int = 0,
)

@Serializable
data class UpdateEventBody(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val isOnline: Boolean? = null,
    val meetingUrl: String? = null,
    val maxAttendees: Int? = null,
)