package com.red.server.stories

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class StoryService(
    private val mongo: MongoTemplate,
    private val users: UserAccountRepository,
    private val media: MediaService,
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate
) {
    fun create(userId: UUID, request: CreateStoryRequest): StoryResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val caption = request.caption?.trim()?.takeIf(String::isNotEmpty)
        require(caption == null || caption.length <= 500) { "Story caption is too long" }
        require(request.allowedUserIds.size <= 100) { "A selected audience may contain at most 100 accounts" }
        require(request.visibility != StoryVisibility.SELECTED || request.allowedUserIds.isNotEmpty()) { "A selected audience must contain at least one account" }
        request.allowedUserIds.forEach { UUID.fromString(it) }

        val requestedType = request.mediaType?.uppercase()
        val isText = requestedType == "TEXT"
        val mediaType = if (isText) {
            require(!caption.isNullOrBlank()) { "Text stories require content" }
            "TEXT"
        } else {
            require(request.mediaKey.startsWith("users/$userId/")) { "Story media must belong to the account" }
            require(media.exists(request.mediaKey)) { "Media object not found" }
            val mimeType = media.metadata(request.mediaKey).mimeType
            require(mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                "Stories support images, videos, and voice media only"
            }
            if (requestedType == "VOICE") "VOICE" else mimeType
        }
        require(request.backgroundColor == null || isText && request.backgroundColor.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            "Text-story background must be a hexadecimal colour"
        }
        require(request.durationMs == null || request.durationMs in 1..(5 * 60 * 1000L)) { "Story duration must be between 1 ms and 5 minutes" }
        require(request.waveform.size <= 512 && request.waveform.all { it in 0..100 }) { "Story waveform is invalid" }
        val audience = if (request.visibility == StoryVisibility.SELECTED) request.allowedUserIds else emptySet()
        val story = mongo.save(StoryDocument(
            id = UuidV7.next(), ownerId = user.id.toString(), ownerRedId = user.redId, ownerUsername = user.username,
            ownerDisplayName = user.displayName, mediaKey = request.mediaKey, mediaType = mediaType, caption = caption,
            visibility = request.visibility, allowedUserIds = audience, backgroundColor = request.backgroundColor,
            durationMs = request.durationMs, waveform = request.waveform, expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        ))
        return response(story, 0)
    }

    fun active(viewerId: UUID): List<StoryResponse> {
        val now = Instant.now()
        return mongo.find(Query(Criteria.where("expiresAt").gt(now).and("deletedAt").`is`(null))
            .with(Sort.by(Sort.Direction.DESC, "createdAt")), StoryDocument::class.java).filter { story -> canAccess(viewerId, story) }.map { story ->
            response(story, mongo.count(Query(Criteria.where("storyId").`is`(story.id)), StoryView::class.java))
        }
    }

    fun viewed(viewerId: UUID, storyId: String): StoryResponse {
        val story = activeStory(storyId)
        require(canAccess(viewerId, story)) { "Story is not visible to this account" }
        mongo.save(StoryView("$storyId:$viewerId", storyId, viewerId.toString()))
        return response(story, mongo.count(Query(Criteria.where("storyId").`is`(storyId)), StoryView::class.java))
    }

    fun react(userId: UUID, storyId: String, request: StoryReactionRequest) {
        val story = activeStory(storyId)
        require(canAccess(userId, story)) { "Story is not visible to this account" }
        val emoji = request.emoji.trim()
        require(emoji in setOf("❤️", "🔥", "😢", "👏", "😍", "🎉", "👍")) { "Unsupported story reaction" }
        mongo.save(StoryReaction("$storyId:$userId", storyId, userId.toString(), emoji))
    }

    fun delete(ownerId: UUID, storyId: String) {
        val story = activeStory(storyId)
        require(story.ownerId == ownerId.toString()) { "Only the owner can delete this story" }
        mongo.updateFirst(Query(Criteria.where("id").`is`(storyId)), Update().set("deletedAt", Instant.now()), StoryDocument::class.java)
        runCatching { media.delete(story.mediaKey) }
    }

    fun activeCount(): Long = mongo.count(Query(Criteria.where("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)), StoryDocument::class.java)

    @Scheduled(fixedDelay = 300_000)
    fun cleanupExpired() {
        val expired = mongo.find(Query(Criteria.where("expiresAt").lte(Instant.now())), StoryDocument::class.java)
        expired.forEach { story -> runCatching { media.delete(story.mediaKey) } }
        if (expired.isNotEmpty()) {
            val ids = expired.map(StoryDocument::id)
            mongo.remove(Query(Criteria.where("storyId").`in`(ids)), StoryView::class.java)
            mongo.remove(Query(Criteria.where("id").`in`(ids)), StoryDocument::class.java)
        }
    }

    fun purgeAll(): Long {
        val stories = mongo.findAll(StoryDocument::class.java)
        stories.forEach { runCatching { media.delete(it.mediaKey) } }
        mongo.remove(Query(), StoryView::class.java)
        return mongo.remove(Query(), StoryDocument::class.java).deletedCount
    }

    private fun activeStory(id: String): StoryDocument = mongo.findOne(Query(Criteria.where("id").`is`(id)
        .and("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)), StoryDocument::class.java)
        ?: throw NoSuchElementException("Story not found")

    /** Same authorization rule is duplicated in MediaAccessService for direct media URLs. */
    private fun canAccess(viewerId: UUID, story: StoryDocument): Boolean {
        if (story.ownerId == viewerId.toString() || story.visibility == StoryVisibility.EVERYONE) return true
        if (story.visibility == StoryVisibility.SELECTED) return viewerId.toString() in story.allowedUserIds
        val owner = UUID.fromString(story.ownerId)
        val blocked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))", Boolean::class.java, owner, viewerId, viewerId, owner) == true
        if (blocked) return false
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM red_contacts a JOIN red_contacts b ON a.owner_id=b.contact_id AND a.contact_id=b.owner_id WHERE a.owner_id=? AND a.contact_id=?)", Boolean::class.java, owner, viewerId) == true
    }

    private fun response(story: StoryDocument, views: Long) = StoryResponse(
        id = story.id, ownerRedId = story.ownerRedId, ownerUsername = story.ownerUsername,
        ownerDisplayName = story.ownerDisplayName,
        mediaUrl = if (story.mediaType == "TEXT") "" else "/api/media/${story.mediaKey}",
        mediaType = story.mediaType, caption = story.caption, createdAt = story.createdAt, expiresAt = story.expiresAt,
        viewCount = views, visibility = story.visibility, allowedUserIds = story.allowedUserIds,
        backgroundColor = story.backgroundColor, durationMs = story.durationMs, waveform = story.waveform
    )
}
