package com.red.sovereign.stories

import kotlinx.serialization.Serializable

@Serializable data class CreateStoryRequest(val mediaKey: String, val caption: String? = null)
@Serializable data class Story(
    val id: String, val ownerRedId: String, val ownerUsername: String, val ownerDisplayName: String,
    val mediaUrl: String, val mediaType: String, val caption: String? = null,
    val createdAt: String, val expiresAt: String, val viewCount: Long = 0
)
