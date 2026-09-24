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
        val backgroundColor = request.backgroundColor?.trim()?.takeIf(String::isNotEmpty)
        require(backgroundColor == null || HEX_COLOR.matches(backgroundColor)) { "Story background color is invalid" }
        val durationMs = request.durationMs
        require(durationMs == null || durationMs in 1..MAX_STORY_DURATION_MS) { "Story duration is invalid" }

        val requestedType = request.mediaType?.trim()?.uppercase()
        val (mediaKey, mediaType) = if (requestedType == "TEXT") {
            require(!caption.isNullOrBlank()) { "Text stories require a caption" }
            "" to "TEXT"
        } else {
            require(request.mediaKey.startsWith("users/$userId/")) { "Story media must belong to the account" }
            require(media.exists(request.mediaKey)) { "Media object not found" }
            val metadata = media.metadata(request.mediaKey)
            require(
                metadata.mimeType.startsWith("image/") ||
                    metadata.mimeType.startsWith("video/") ||
                    metadata.mimeType.startsWith("audio/")
            ) { "Stories support images, videos, and audio only" }
            request.mediaKey to metadata.mimeType
        }

        val story = mongo.save(
            StoryDocument(
                id = UuidV7.next(),
                ownerId = user.id.toString(),
                ownerRedId = user.redId,
                ownerUsername = user.username,
                ownerDisplayName = user.displayName,
                mediaKey = mediaKey,
                mediaType = mediaType,
                caption = caption,
                visibility = request.visibility,
                // Android's audience picker sends RED IDs; authorization uses
                // account UUIDs. Resolve at publication rather than silently
                // publishing a story nobody selected can actually open.
                allowedUserIds = resolveAudience(userId, request),
                backgroundColor = backgroundColor,
                durationMs = durationMs,
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            ),
        )
        return response(story, 0)
    }

    // LEGENDARY FIX: pagination + تجميع العدادات دفعة واحدة (كان find-all + N+1 count + فلترة ذاكرة)
    fun active(viewerId: UUID, limit: Int = 50, cursor: String? = null): List<StoryResponse> {
        val now = Instant.now()
        val lim = limit.coerceIn(1, 50)
        val base = Criteria.where("expiresAt").gt(now).and("deletedAt").`is`(null)
        if (cursor != null) runCatching {
            val c = mongo.findOne(Query(Criteria.where("id").`is`(cursor)), StoryDocument::class.java)
            if (c != null) base.and("createdAt").lt(c.createdAt)
        }
        val page = mongo.find(Query(base).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(lim * 3),
            StoryDocument::class.java)
        val visible = page.filter { story -> canAccess(viewerId, story) }.take(lim)
        if (visible.isEmpty()) return emptyList()
        // عدادات دفعة واحدة بدل N+1
        val ids = visible.map { it.id }
        val counts: Map<String, Long> = runCatching {
            val agg = mongo.aggregate(
                org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("storyId").`in`(ids)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("storyId").count().`as`("c")
                ), "story_views", Map::class.java
            ).mappedResults
            agg.associate { (it["_id"] as? String ?: "") to ((it["c"] as? Number)?.toLong() ?: 0L) }
        }.getOrDefault(emptyMap())
        return visible.map { story -> response(story, counts[story.id] ?: 0L) }
    }

    fun active(viewerId: UUID): List<StoryResponse> = active(viewerId, 50, null)

    /** LEGENDARY: رد مشفر على حالة — فحص وصول + إرجاع هدف التشفير (النص نفسه يُشفر E2EE عميلاً ولا يمر خاماً) */
    fun replyTarget(viewerId: UUID, storyId: String): Map<String, String> {
        val story = activeStory(storyId)
        require(canAccess(viewerId, story)) { "Story is not visible to this account" }
        require(story.ownerId != viewerId.toString()) { "Cannot reply to own story" }
        return mapOf("ownerId" to story.ownerId, "ownerRedId" to story.ownerRedId, "storyId" to story.id)
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

    /** قائمة المشاهِدين — للمالك فقط (كان العداد وحده فيُعرض زر عين بلا بيانات). */
    fun viewers(ownerId: UUID, storyId: String): List<StoryViewerResponse> {
        val story = activeStory(storyId)
        require(story.ownerId == ownerId.toString()) { "Only the owner can list viewers" }
        return mongo.find(Query(Criteria.where("storyId").`is`(storyId)), StoryView::class.java)
            .sortedByDescending { it.viewedAt }
            .take(200)
            .mapNotNull { view ->
                val viewerUuid = runCatching { UUID.fromString(view.viewerId) }.getOrNull() ?: return@mapNotNull null
                val account = runCatching { users.findById(viewerUuid).orElse(null) }.getOrNull() ?: return@mapNotNull null
                StoryViewerResponse(account.redId, account.username, account.displayName, view.viewedAt)
            }
    }

    fun delete(ownerId: UUID, storyId: String) {
        val story = activeStory(storyId)
        require(story.ownerId == ownerId.toString()) { "Only the owner can delete this story" }
        // A user-owned upload may also be referenced by a post, another story,
        // a group avatar or an explicit message grant. Removing this story must
        // revoke its story-based visibility, not destroy the shared object.
        mongo.updateFirst(Query(Criteria.where("id").`is`(storyId)), Update().set("deletedAt", Instant.now()), StoryDocument::class.java)
    }

    fun activeCount(): Long = mongo.count(Query(Criteria.where("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)), StoryDocument::class.java)

    @Scheduled(fixedDelay = 300_000)
    fun cleanupExpired() {
        val expired = mongo.find(Query(Criteria.where("expiresAt").lte(Instant.now())), StoryDocument::class.java)
        // Remove expired references and their metadata, never the upload itself:
        // other live documents/grants may point to the same MinIO key. Automatic
        // physical garbage collection remains disabled until the cross-store
        // inventory is complete and race-safe.
        if (expired.isNotEmpty()) {
            val ids = expired.map(StoryDocument::id)
            mongo.remove(Query(Criteria.where("storyId").`in`(ids)), StoryView::class.java)
            mongo.remove(Query(Criteria.where("id").`in`(ids)), StoryDocument::class.java)
        }
    }

    fun purgeAll(): Long {
        // Purging story references is not proof that their uploads are unused
        // elsewhere. Keep object deletion behind a separately audited GC path.
        mongo.remove(Query(), StoryView::class.java)
        return mongo.remove(Query(), StoryDocument::class.java).deletedCount
    }

    private fun activeStory(id: String): StoryDocument = mongo.findOne(Query(Criteria.where("id").`is`(id)
        .and("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)), StoryDocument::class.java)
        ?: throw NoSuchElementException("Story not found")

    private fun resolveAudience(ownerId: UUID, request: CreateStoryRequest): Set<String> {
        if (request.visibility != StoryVisibility.SELECTED) return emptySet()
        require(request.allowedUserIds.size in 1..100) { "Select 1-100 story viewers" }
        return request.allowedUserIds.map { raw ->
            val identifier = raw.trim()
            require(identifier.isNotBlank()) { "Invalid story viewer" }
            val uuid = runCatching { UUID.fromString(identifier) }.getOrNull()
            val account = if (uuid != null) users.findById(uuid).orElse(null)
                          else users.findByRedId(identifier.uppercase())
            requireNotNull(account) { "Unknown story viewer" }
            require(account.id != ownerId) { "The owner cannot be a selected viewer" }
            account.id.toString()
        }.toSet()
    }

    /** Direct story URLs use the same block-first rule in MediaAccessService. */
    private fun canAccess(viewerId: UUID, story: StoryDocument): Boolean {
        if (story.ownerId == viewerId.toString()) return true
        val owner = runCatching { UUID.fromString(story.ownerId) }.getOrNull() ?: return false
        // A block in either direction overrides EVERYONE, SELECTED and old
        // audience grants. Check it *before* returning from any visibility branch.
        val blocked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))", Boolean::class.java, owner, viewerId, viewerId, owner) == true
        if (blocked) return false
        return when (story.visibility) {
            StoryVisibility.EVERYONE -> true
            StoryVisibility.SELECTED -> viewerId.toString() in story.allowedUserIds
            else -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM red_contacts a JOIN red_contacts b ON a.owner_id=b.contact_id AND a.contact_id=b.owner_id WHERE a.owner_id=? AND a.contact_id=?)", Boolean::class.java, owner, viewerId) == true
        }
    }

    private fun response(story: StoryDocument, views: Long) = StoryResponse(
        story.id,
        story.ownerRedId,
        story.ownerUsername,
        story.ownerDisplayName,
        story.mediaKey.takeIf(String::isNotBlank)?.let { "/api/media/$it" }.orEmpty(),
        story.mediaType,
        story.caption,
        story.createdAt,
        story.expiresAt,
        views,
        story.backgroundColor,
        story.durationMs,
    )

    private companion object {
        val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
        const val MAX_STORY_DURATION_MS = 24 * 60 * 60 * 1000L
    }
}
