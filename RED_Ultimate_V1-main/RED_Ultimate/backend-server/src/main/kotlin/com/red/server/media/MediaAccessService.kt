package com.red.server.media

import com.red.server.groups.GroupDocument
import com.red.server.groups.GroupMember
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
