package com.red.sovereign.stories

import kotlinx.serialization.Serializable

@Serializable data class CreateStoryRequest(
    val mediaKey: String, 
    val caption: String? = null, 
    val visibleTo: String = "EVERYONE", 
    val audience: List<String> = emptyList(),
    val mediaType: String = "image/jpeg",
    val backgroundColor: String? = null, // For TEXT: #D32F2F, #1565C0
    val durationMs: Long? = null // For VOICE/VIDEO
)
@Serializable data class Story(
    val id: String, val ownerRedId: String, val ownerUsername: String, val ownerDisplayName: String,
    val mediaUrl: String, val mediaType: String, val caption: String? = null,
    val createdAt: String, val expiresAt: String, val viewCount: Long = 0,
    val visibleTo: String = "EVERYONE",
    val audience: List<String> = emptyList(),
    val reactions: Map<String, Long> = emptyMap(),
    val viewerIds: List<String> = emptyList(),
    val isViewed: Boolean = false,
    val backgroundColor: String? = null, // TEXT stories
    val durationMs: Long? = null, // VOICE stories
    val waveform: List<Int> = emptyList() // VOICE waveform
)
@Serializable data class StoryView(val storyId: String, val viewerRedId: String, val reaction: String? = null)
@Serializable data class StoryReactionRequest(val emoji: String)
@Serializable data class StoryAudienceRequest(val visibleTo: String, val audience: List<String>)

// Helper to determine story type
fun Story.isText(): Boolean = mediaType == "TEXT" || mediaType.startsWith("text/")
fun Story.isVoice(): Boolean = mediaType.startsWith("audio/") || mediaType == "VOICE"
fun Story.isVideo(): Boolean = mediaType.startsWith("video/")
fun Story.isImage(): Boolean = mediaType.startsWith("image/")
