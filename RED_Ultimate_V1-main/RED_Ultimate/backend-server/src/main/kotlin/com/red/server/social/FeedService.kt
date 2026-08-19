package com.red.server.social

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class FeedService(
    private val mongo: MongoTemplate,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate,
    private val linkCards: LinkCardService? = null
) {
    fun create(userId: UUID, request: CreatePostRequest): PostDocument {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.status == AccountStatus.APPROVED)
        val text = request.text.trim()
        require(text.isNotEmpty() && text.length <= 10_000) { "Post text must contain 1-10000 characters" }
        require(request.media.size <= 10) { "A post may contain at most 10 media objects" }
        request.parentId?.let { require(activePost(it) != null) { "Parent post not found" } }
        request.quotePostId?.let { require(activePost(it) != null) { "Quoted post not found" } }
        val poll = buildPoll(request)
        // 🔗 Auto-fetch link card for first URL in text (with SSRF protection)
        val linkCard = Regex("""https?://[^\s]+|www\.[^\s]+""").find(text)?.value?.let { url ->
            try { linkCards?.fetch(url) } catch (_: Exception) { null }
        }
        val post = PostDocument(
            id = UuidV7.next(), authorId = user.id.toString(), authorRedId = user.redId,
            authorUsername = user.username, authorDisplayName = user.displayName, text = text,
            visibility = request.visibility, kind = if (poll == null) PostKind.POST else PostKind.POLL,
            parentId = request.parentId, quotePostId = request.quotePostId, poll = poll, media = request.media,
            linkCard = linkCard
        )
        mongo.save(post)
        request.parentId?.let { mongo.updateFirst(Query(Criteria.where("id").`is`(it)), Update().inc("replyCount", 1), PostDocument::class.java) }
        return post
    }

    fun feed(userId: UUID, scope: FeedScope, before: Instant?, limit: Int): FeedResponse {
        val criteria = Criteria.where("deletedAt").`is`(null).and("parentId").`is`(null)
        val hidden = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), HiddenPost::class.java).map(HiddenPost::postId)
        val muted = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), MutedAuthor::class.java).map(MutedAuthor::authorId)
        if (hidden.isNotEmpty()) criteria.and("id").nin(hidden)
        if (muted.isNotEmpty()) criteria.and("authorId").nin(muted)
        val friendIds = mutualFriendIds(userId) + userId.toString()
        when (scope) {
            FeedScope.PUBLIC -> criteria.and("visibility").`is`(PostVisibility.PUBLIC)
            FeedScope.ALL -> criteria.andOperator(
                Criteria().orOperator(
                    Criteria.where("visibility").`is`(PostVisibility.PUBLIC),
                    Criteria.where("authorId").`is`(userId.toString()),
                    Criteria.where("visibility").`is`(PostVisibility.FRIENDS).and("authorId").`in`(friendIds)
                )
            )
            FeedScope.FRIENDS, FeedScope.FOLLOWING -> {
                if (friendIds.size == 1) return FeedResponse(emptyList(), null)
                criteria.and("visibility").`is`(PostVisibility.FRIENDS).and("authorId").`in`(friendIds)
            }
            // نطاق توافق للمنشورات المحلية القديمة؛ لا يُستخدم كنطاق خصوصية جديد.
            FeedScope.YEMEN -> criteria.and("visibility").`is`(PostVisibility.LOCAL_YEMEN)
        }
        before?.let { criteria.and("createdAt").lt(it) }
        val posts = mongo.find(Query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit.coerceIn(1, 50)), PostDocument::class.java)
        return FeedResponse(posts, posts.lastOrNull()?.createdAt?.toString())
    }

    private fun mutualFriendIds(userId: UUID): List<String> = jdbc.queryForList(
        """SELECT a.contact_id::text
           FROM red_contacts a
           WHERE a.owner_id = ?
             AND EXISTS (
                 SELECT 1 FROM red_contacts b
                 WHERE b.owner_id = a.contact_id AND b.contact_id = a.owner_id
             )""",
        String::class.java,
        userId
    ).filterNotNull()

    fun thread(postId: String): List<PostDocument> {
        require(activePost(postId) != null) { "Post not found" }
        return mongo.find(Query(Criteria().orOperator(Criteria.where("id").`is`(postId), Criteria.where("parentId").`is`(postId)).and("deletedAt").`is`(null))
            .with(Sort.by(Sort.Direction.ASC, "createdAt")), PostDocument::class.java)
    }

    fun react(userId: UUID, postId: String, request: ReactionRequest): PostDocument {
        require(activePost(postId) != null) { "Post not found" }
        val type = request.type.uppercase()
        require(type in setOf("LIKE", "LOVE", "SUPPORT", "INSIGHTFUL")) { "Unsupported reaction" }
        val id = "$postId:$userId:$type"
        val exists = mongo.exists(Query(Criteria.where("id").`is`(id)), PostReaction::class.java)
        if (request.active && !exists) {
            mongo.save(PostReaction(id, postId, userId.toString(), type))
            mongo.updateFirst(Query(Criteria.where("id").`is`(postId)), Update().inc("reactionCounts.$type", 1), PostDocument::class.java)
        } else if (!request.active && exists) {
            mongo.remove(Query(Criteria.where("id").`is`(id)), PostReaction::class.java)
            mongo.updateFirst(Query(Criteria.where("id").`is`(postId)), Update().inc("reactionCounts.$type", -1), PostDocument::class.java)
        }
        return requireNotNull(activePost(postId))
    }

    fun vote(userId: UUID, postId: String, request: PollVoteRequest): PostDocument {
        val post = requireNotNull(activePost(postId)) { "Post not found" }
        val poll = requireNotNull(post.poll) { "Post has no poll" }
        require(poll.expiresAt == null || poll.expiresAt.isAfter(Instant.now())) { "Poll is closed" }
        require(poll.options.any { it.id == request.optionId }) { "Poll option not found" }
        val voteId = "$postId:$userId"
        require(!mongo.exists(Query(Criteria.where("id").`is`(voteId)), PollVote::class.java)) { "User already voted" }
        mongo.save(PollVote(voteId, postId, userId.toString(), request.optionId))
        mongo.updateFirst(Query(Criteria.where("id").`is`(postId).and("poll.options.id").`is`(request.optionId)),
            Update().inc("poll.options.$.votes", 1), PostDocument::class.java)
        return requireNotNull(activePost(postId))
    }

    fun follow(userId: UUID, targetRedId: String) {
        val target = users.findByRedId(targetRedId) ?: throw NoSuchElementException("RED identity not found")
        require(target.id != userId) { "A user cannot follow their own account" }
        mongo.save(FollowDocument("$userId:${target.id}", userId.toString(), target.id.toString()))
    }

    fun unfollow(userId: UUID, targetRedId: String) {
        val target = users.findByRedId(targetRedId) ?: return
        mongo.remove(Query(Criteria.where("id").`is`("$userId:${target.id}")), FollowDocument::class.java)
    }

    fun following(userId: UUID): List<String> {
        val ids = mongo.find(Query(Criteria.where("followerId").`is`(userId.toString())), FollowDocument::class.java).map(FollowDocument::followedId)
        return users.findAllById(ids.map(UUID::fromString)).map { it.redId }
    }

    fun edit(userId: UUID, postId: String, request: EditPostRequest): PostDocument {
        val post = requireNotNull(activePost(postId)) { "Post not found" }
        require(post.authorId == userId.toString()) { "Only the author can edit this post" }
        val text = request.text.trim()
        require(text.isNotEmpty() && text.length <= 10_000) { "Post text must contain 1-10000 characters" }
        mongo.updateFirst(Query(Criteria.where("id").`is`(postId)), Update().set("text", text).set("editedAt", Instant.now()), PostDocument::class.java)
        return requireNotNull(activePost(postId))
    }

    fun hide(userId: UUID, postId: String) {
        require(activePost(postId) != null) { "Post not found" }
        mongo.save(HiddenPost("$userId:$postId", userId.toString(), postId))
    }

    fun mute(userId: UUID, targetRedId: String) {
        val target = users.findByRedId(targetRedId.trim().uppercase()) ?: throw NoSuchElementException("RED identity not found")
        require(target.id != userId) { "Cannot mute yourself" }
        mongo.save(MutedAuthor("$userId:${target.id}", userId.toString(), target.id.toString()))
    }

    fun report(userId: UUID, postId: String, request: HidePostRequest) {
        require(activePost(postId) != null) { "Post not found" }
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() } ?: "OTHER"
        require(reason.length <= 200) { "Report reason is too long" }
        mongo.save(PostReport("$postId:$userId", postId, userId.toString(), reason.uppercase()))
    }

    fun delete(userId: UUID, postId: String) {
        val post = requireNotNull(activePost(postId)) { "Post not found" }
        require(post.authorId == userId.toString()) { "Only the author can delete this post" }
        mongo.updateFirst(Query(Criteria.where("id").`is`(postId)), Update().set("deletedAt", Instant.now()).set("text", ""), PostDocument::class.java)
    }

    private fun activePost(id: String) = mongo.findOne(Query(Criteria.where("id").`is`(id).and("deletedAt").`is`(null)), PostDocument::class.java)
    private fun buildPoll(request: CreatePostRequest): Poll? {
        if (request.pollOptions.isEmpty()) return null
        val options = request.pollOptions.map(String::trim).filter(String::isNotEmpty)
        require(options.size in 2..6 && options.all { it.length <= 100 }) { "Poll must contain 2-6 valid options" }
        val expiry = request.pollDurationHours?.let { Instant.now().plus(it.coerceIn(1, 168).toLong(), ChronoUnit.HOURS) }
        return Poll(options.map { PollOption(UuidV7.next(), it) }, expiry)
    }
}
