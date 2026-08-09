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
class StoryService(private val mongo: MongoTemplate, private val users: UserAccountRepository, private val media: MediaService) {
    fun create(userId: UUID, request: CreateStoryRequest): StoryResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(request.mediaKey.startsWith("users/$userId/")) { "Story media must belong to the account" }
        require(media.exists(request.mediaKey)) { "Media object not found" }
        val metadata = media.metadata(request.mediaKey)
        require(metadata.mimeType.startsWith("image/") || metadata.mimeType.startsWith("video/")) { "Stories support images and videos only" }
        val caption = request.caption?.trim()?.takeIf(String::isNotEmpty)
        require(caption == null || caption.length <= 500) { "Story caption is too long" }
        val story = mongo.save(StoryDocument(UuidV7.next(), user.id.toString(), user.redId, user.username, user.displayName,
            request.mediaKey, metadata.mimeType, caption, expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)))
        return response(story, 0)
    }

    fun active(): List<StoryResponse> {
        val now = Instant.now()
        return mongo.find(Query(Criteria.where("expiresAt").gt(now).and("deletedAt").`is`(null))
            .with(Sort.by(Sort.Direction.DESC, "createdAt")), StoryDocument::class.java).map { story ->
            response(story, mongo.count(Query(Criteria.where("storyId").`is`(story.id)), StoryView::class.java))
        }
    }

    fun viewed(viewerId: UUID, storyId: String): StoryResponse {
        val story = activeStory(storyId)
        mongo.save(StoryView("$storyId:$viewerId", storyId, viewerId.toString()))
        return response(story, mongo.count(Query(Criteria.where("storyId").`is`(storyId)), StoryView::class.java))
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

    private fun response(story: StoryDocument, views: Long) = StoryResponse(story.id, story.ownerRedId, story.ownerUsername,
        story.ownerDisplayName, "/api/media/${story.mediaKey}", story.mediaType, story.caption, story.createdAt, story.expiresAt, views)
}
