package com.red.server.media

import com.red.server.groups.GroupDocument
import com.red.server.groups.GroupMember
import com.red.server.database.ChannelDocument
import com.red.server.database.ChannelMessageDocument
import com.red.server.database.GroupMessageDocument
import com.red.server.database.MessageDocument
import com.red.server.social.PostDocument
import com.red.server.stories.StoryDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/** Object-level authorization for authenticated media downloads. */
@Service
class MediaAccessService(private val mongo: MongoTemplate, private val jdbc: JdbcTemplate) {
    /**
     * An explicit uploader deletion must not silently break a published story,
     * post, channel/group avatar or message attachment. Deny on Mongo errors rather than treating
     * an unavailable reference store as proof that the object is unused.
     * This is a safety guard, not an atomic cross-store reference count: a
     * publisher racing this check still needs a separately designed lifecycle.
     */
    fun requireNoPublishedReferences(key: String) {
        val inUse = mongo.exists(Query(Criteria.where("mediaKey").`is`(key)
            .and("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)), StoryDocument::class.java) ||
            mongo.exists(Query(Criteria.where("avatarMediaKey").`is`(key)), GroupDocument::class.java) ||
            mongo.exists(Query(Criteria.where("avatarMediaKey").`is`(key)), ChannelDocument::class.java) ||
            mongo.exists(Query(Criteria.where("media.objectKey").`is`(key)
                .and("deletedAt").`is`(null)), PostDocument::class.java) ||
            mongo.exists(Query(Criteria.where("poll.options.imageUrl").`is`("/api/media/$key")
                .and("deletedAt").`is`(null)), PostDocument::class.java) ||
            mongo.exists(Query(Criteria.where("attachments.mediaKey").`is`(key)
                .and("deletedForEveryoneAt").`is`(null)), MessageDocument::class.java) ||
            mongo.exists(Query(Criteria.where("attachments.mediaKey").`is`(key)
                .and("deletedForEveryoneAt").`is`(null)), GroupMessageDocument::class.java) ||
            mongo.exists(Query(Criteria.where("attachments.mediaKey").`is`(key)
                .and("deletedAt").`is`(null)), ChannelMessageDocument::class.java)
        if (inUse) throw ResponseStatusException(HttpStatus.CONFLICT, "Media object is still referenced by published content")
        // Profile avatars live in PostgreSQL, not Mongo. Do not let the owner
        // remove their upload while the directory still points to it.
        val profileAvatar = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users WHERE avatar_url=? OR avatar_media_key=?)",
            Boolean::class.java, key, key
        ) == true
        if (profileAvatar) throw ResponseStatusException(HttpStatus.CONFLICT, "Media object is still used as a profile avatar")
    }

    fun requireDownloadAllowed(accountId: UUID, key: String) {
        val ownerId = key.substringAfter("users/", "").substringBefore('/')
        if (ownerId == accountId.toString()) return
        // A stale explicit grant must not override a later block. Most object
        // keys carry the uploader UUID; story authorization below checks its
        // document owner as well when that differs from the object uploader.
        val uploader = runCatching { UUID.fromString(ownerId) }.getOrNull()
        if (uploader != null && isBlocked(uploader, accountId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Media object is not accessible to this account")
        }
        val story = mongo.findOne(
            Query(Criteria.where("mediaKey").`is`(key)
                .and("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)),
            StoryDocument::class.java
        )
        val storyOwner = story?.ownerId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (story != null && story.ownerId != accountId.toString() &&
            (storyOwner == null || isBlocked(storyOwner, accountId))) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Media object is not accessible to this account")
        }
        val explicitlyGranted = jdbc.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM media_grants WHERE object_key=? AND grantee_id=?
               AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP))""",
            Boolean::class.java,
            key,
            accountId
        ) == true
        if (explicitlyGranted) return
        val avatarGroup = mongo.findOne(Query(Criteria.where("avatarMediaKey").`is`(key)), GroupDocument::class.java)
        if (avatarGroup != null && mongo.exists(Query(Criteria.where("id").`is`("${avatarGroup.id}:$accountId")), GroupMember::class.java)) return

        if (story != null && canAccessStory(accountId, story)) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Media object is not accessible to this account")
    }

    private fun isBlocked(owner: UUID, viewer: UUID): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))",
        Boolean::class.java, owner, viewer, viewer, owner
    ) == true

    private fun canAccessStory(viewerId: UUID, story: StoryDocument): Boolean {
        if (story.ownerId == viewerId.toString()) return true
        val owner = runCatching { UUID.fromString(story.ownerId) }.getOrNull() ?: return false
        if (isBlocked(owner, viewerId)) return false
        return when (story.visibility) {
            com.red.server.stories.StoryVisibility.EVERYONE -> true
            com.red.server.stories.StoryVisibility.SELECTED -> viewerId.toString() in story.allowedUserIds
            com.red.server.stories.StoryVisibility.CONTACTS -> jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM red_contacts a JOIN red_contacts b ON a.owner_id=b.contact_id AND a.contact_id=b.owner_id WHERE a.owner_id=? AND a.contact_id=?)",
                Boolean::class.java, owner, viewerId
            ) == true
        }
    }
}
