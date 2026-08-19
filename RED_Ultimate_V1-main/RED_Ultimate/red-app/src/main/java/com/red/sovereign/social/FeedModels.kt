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
    val media: List<PostMedia> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val linkCard: LinkCard? = null,
    val createdAt: String,
    val editedAt: String? = null,
    val editHistory: List<EditEntry> = emptyList(),
    val reactionCounts: Map<String, Long> = emptyMap(),
    val replyCount: Long = 0,
    val repostCount: Long = 0,
    val isHidden: Boolean = false,
    val isMuted: Boolean = false
)
/**
 * وسائط منشور. الحقل `objectKey` — لا `url` — لأنه اسم الحقل في
 * `PostMedia` بالخادم، وهو ما يُخزَّن ويُعاد كما هو. الرابط القابل
 * للتحميل يُبنى منه بـ`/api/media/$objectKey` عبر MediaApi (وهي
 * تتطلّب المصادقة، فالرابط ليس عامًّا).
 *
 * كان الاسم `url` فكان الحقل لا يُملأ أبدًا عند فكّ ترميز الردّ.
 */
@Serializable data class PostMedia(
    val objectKey: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val voiceWaveform: List<Int> = emptyList()
)
@Serializable data class LinkCard(val url: String, val title: String? = null, val description: String? = null, val imageUrl: String? = null)
@Serializable data class EditEntry(val text: String, val editedAt: String)
@Serializable data class Poll(val options: List<PollOption>, val expiresAt: String? = null)
@Serializable data class PollOption(val id: String, val text: String, val votes: Long = 0)
@Serializable data class FeedResponse(val posts: List<Post>, val nextCursor: String? = null)
@Serializable data class CreatePostRequest(
    val text: String, 
    val visibility: String = "LOCAL_YEMEN", 
    val parentId: String? = null, 
    val quotePostId: String? = null, 
    val pollOptions: List<String> = emptyList(), 
    val pollDurationHours: Int? = null,
    /**
     * الخادم يقرأ `media: List<PostMedia>` لا `mediaKeys: List<String>`.
     * كان الاسم الخاطئ يعني رفض الطلب بـ400 فور إرفاق أي وسيط
     * (FAIL_ON_UNKNOWN_PROPERTIES مفعَّل افتراضيًّا في Jackson).
     */
    val media: List<PostMedia> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList()
)
@Serializable data class ReactionRequest(val type: String, val active: Boolean)
@Serializable data class PollVoteRequest(val optionId: String)
@Serializable data class EditPostRequest(val text: String)
@Serializable data class HidePostRequest(val reason: String? = null)
