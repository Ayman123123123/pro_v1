package com.red.server.stories

import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * Story indexes — TTL safety net + query indexes.
 *
 * - `expiresAt` TTL with expire(0): MongoDB eventually removes expired story
 *   documents if [StoryService.cleanupExpired] is delayed or disabled. Neither
 *   path deletes the uploader-owned MinIO key, which may still be shared with
 *   another resource. The scheduled orphan scan is currently preview-only.
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
