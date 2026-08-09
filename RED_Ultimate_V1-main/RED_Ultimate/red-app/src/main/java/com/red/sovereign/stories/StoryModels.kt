package com.red.sovereign.stories

import kotlinx.serialization.Serializable

@Serializable data class CreateStoryRequest(val mediaKey: String, val caption: String? = null, val visibleTo: String = "EVERYONE", val audience: List<String> = emptyList())
@Serializable data class Story(
    val id: String, val ownerRedId: String, val ownerUsername: String, val ownerDisplayName: String,
    val mediaUrl: String, val mediaType: String, val caption: String? = null,
    val createdAt: String, val expiresAt: String, val viewCount: Long = 0,
    val visibleTo: String = "EVERYONE",
    val audience: List<String> = emptyList(),
    val reactions: Map<String, Long> = emptyMap(), // ❤️, 🔥, 😢
    val viewerIds: List<String> = emptyList(),
    val isViewed: Boolean = false
)
@Serializable data class StoryView(val storyId: String, val viewerRedId: String, val reaction: String? = null)
@Serializable data class StoryReactionRequest(val emoji: String)
@Serializable data class StoryAudienceRequest(val visibleTo: String, val audience: List<String>)
