package com.red.server.stories

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("stories")
data class StoryDocument(
    @Id val id: String,
    @Indexed val ownerId: String,
    val ownerRedId: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val mediaKey: String,
    val mediaType: String,
    val caption: String?,
    /** جمهور القصة؛ الافتراضي الآمن هو جهات الاتصال المتبادلة فقط. */
    val visibility: StoryVisibility = StoryVisibility.CONTACTS,
    val allowedUserIds: Set<String> = emptySet(),
    val backgroundColor: String? = null,
    val durationMs: Long? = null,
    val waveform: List<Int> = emptyList(),
    val createdAt: Instant = Instant.now(),
    @Indexed val expiresAt: Instant,
    var deletedAt: Instant? = null
)

@Document("story_views")
data class StoryView(@Id val id: String, val storyId: String, val viewerId: String, val viewedAt: Instant = Instant.now())

@Document("story_reactions")
data class StoryReaction(@Id val id: String, val storyId: String, val userId: String, val emoji: String, val createdAt: Instant = Instant.now())

enum class StoryVisibility { CONTACTS, EVERYONE, SELECTED }
data class StoryReactionRequest(val emoji: String)
data class CreateStoryRequest(
    val mediaKey: String,
    val caption: String? = null,
    val visibility: StoryVisibility = StoryVisibility.CONTACTS,
    val allowedUserIds: Set<String> = emptySet(),
    val mediaType: String? = null,
    val backgroundColor: String? = null,
    val durationMs: Long? = null,
    val waveform: List<Int> = emptyList()
)
data class StoryResponse(
    val id: String, val ownerRedId: String, val ownerUsername: String, val ownerDisplayName: String,
    val mediaUrl: String, val mediaType: String, val caption: String?, val createdAt: Instant,
    val expiresAt: Instant, val viewCount: Long,
    val visibility: StoryVisibility, val allowedUserIds: Set<String>,
    val backgroundColor: String? = null, val durationMs: Long? = null, val waveform: List<Int> = emptyList()
)
