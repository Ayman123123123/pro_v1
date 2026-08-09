package com.red.sovereign.social

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,
    val authorRedId: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val text: String,
    val visibility: String,
    val kind: String = "POST",
    val parentId: String? = null,
    val quotePostId: String? = null,
    val poll: Poll? = null,
    val createdAt: String,
    val reactionCounts: Map<String, Long> = emptyMap(),
    val replyCount: Long = 0,
    val repostCount: Long = 0
)
@Serializable data class Poll(val options: List<PollOption>, val expiresAt: String? = null)
@Serializable data class PollOption(val id: String, val text: String, val votes: Long = 0)
@Serializable data class FeedResponse(val posts: List<Post>, val nextCursor: String? = null)
@Serializable data class CreatePostRequest(val text: String, val visibility: String = "LOCAL_YEMEN", val parentId: String? = null, val quotePostId: String? = null, val pollOptions: List<String> = emptyList(), val pollDurationHours: Int? = null)
@Serializable data class ReactionRequest(val type: String, val active: Boolean)
@Serializable data class PollVoteRequest(val optionId: String)
