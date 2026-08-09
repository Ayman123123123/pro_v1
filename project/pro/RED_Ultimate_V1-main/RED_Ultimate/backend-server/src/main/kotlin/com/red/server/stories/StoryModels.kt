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
    val createdAt: Instant = Instant.now(),
    @Indexed val expiresAt: Instant,
    var deletedAt: Instant? = null
)

@Document("story_views")
data class StoryView(@Id val id: String, val storyId: String, val viewerId: String, val viewedAt: Instant = Instant.now())

data class CreateStoryRequest(val mediaKey: String, val caption: String? = null)
data class StoryResponse(
    val id: String, val ownerRedId: String, val ownerUsername: String, val ownerDisplayName: String,
    val mediaUrl: String, val mediaType: String, val caption: String?, val createdAt: Instant,
    val expiresAt: Instant, val viewCount: Long
)
