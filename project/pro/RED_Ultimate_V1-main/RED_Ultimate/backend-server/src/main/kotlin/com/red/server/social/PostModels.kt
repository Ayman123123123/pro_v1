package com.red.server.social

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("posts")
data class PostDocument(
    @Id val id: String,
    @Indexed val authorId: String,
    @Indexed val authorRedId: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val text: String,
    @Indexed val visibility: PostVisibility,
    val kind: PostKind = PostKind.POST,
    @Indexed val parentId: String? = null,
    val quotePostId: String? = null,
    val poll: Poll? = null,
    val media: List<PostMedia> = emptyList(),
    @Indexed val createdAt: Instant = Instant.now(),
    val editedAt: Instant? = null,
    @Indexed val deletedAt: Instant? = null,
    val reactionCounts: Map<String, Long> = emptyMap(),
    val replyCount: Long = 0,
    val repostCount: Long = 0
)

enum class PostVisibility { PUBLIC, LOCAL_YEMEN }
enum class FeedScope { ALL, FOLLOWING, YEMEN }
enum class PostKind { POST, POLL }
data class PostMedia(val objectKey: String, val mimeType: String, val width: Int? = null, val height: Int? = null)
data class Poll(val options: List<PollOption>, val expiresAt: Instant?)
data class PollOption(val id: String, val text: String, val votes: Long = 0)

@Document("post_reactions")
data class PostReaction(@Id val id: String, val postId: String, val userId: String, val type: String, val createdAt: Instant = Instant.now())

@Document("poll_votes")
data class PollVote(@Id val id: String, val postId: String, val userId: String, val optionId: String, val createdAt: Instant = Instant.now())

@Document("follows")
data class FollowDocument(
    @Id val id: String,
    @Indexed val followerId: String,
    @Indexed val followedId: String,
    val createdAt: Instant = Instant.now()
)

data class CreatePostRequest(
    val text: String,
    val visibility: PostVisibility = PostVisibility.LOCAL_YEMEN,
    val parentId: String? = null,
    val quotePostId: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollDurationHours: Int? = null,
    val media: List<PostMedia> = emptyList()
)
data class ReactionRequest(val type: String, val active: Boolean)
data class PollVoteRequest(val optionId: String)
data class FeedResponse(val posts: List<PostDocument>, val nextCursor: String?)
