package com.red.server.stories

import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * Story indexes — TTL safety net + query indexes.
 *
 * - `expiresAt` TTL with expire(0): MongoDB auto-deletes the document the
 *   moment expiresAt passes, even if [StoryService.cleanupExpired] is delayed
 *   or the scheduler is disabled. The scheduler remains primary because it
 *   also deletes the MinIO media object (TTL alone would orphan it;
 *   OrphanCleanupScheduler is the final backstop).
 * - `ownerId` + `createdAt` compound for feed queries; `deletedAt` sparse
 *   filter is handled in query predicates.
 */
@Component
class StoryIndexInitializer(private val mongo: MongoTemplate) {
    @PostConstruct
    fun init() {
        runCatching {
            mongo.indexOps(StoryDocument::class.java)
                .createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(0))
        }
        runCatching {
            mongo.indexOps(StoryDocument::class.java)
                .createIndex(Index().on("ownerId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC))
        }
        runCatching {
            mongo.indexOps(StoryView::class.java)
                .createIndex(Index().on("storyId", Sort.Direction.ASC))
        }
        runCatching {
            mongo.indexOps(StoryReaction::class.java)
                .createIndex(Index().on("storyId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC))
        }
    }
}
