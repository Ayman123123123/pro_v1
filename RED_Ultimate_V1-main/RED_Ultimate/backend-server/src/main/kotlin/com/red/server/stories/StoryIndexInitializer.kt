package com.red.server.stories

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * Story indexes — TTL safety net + query indexes.
 *
 * المرجع الوحيد لفهارس القصص (Single Source of Truth): هذا الـ Initializer هو
 * ما يُنشئ الفهارس فعليًا عند الإقلاع ويُصلح المتعارض منها (إسقاط + إعادة).
 * تعليقات @CompoundIndex/@Indexed على النماذج عقد توثيقي فقط.
 *
 * - `expiresAt` TTL with expire(0): MongoDB auto-deletes the document the
 *   moment expiresAt passes, even if [StoryService.cleanupExpired] is delayed
 *   or the scheduler is disabled. The scheduler remains primary because it
 *   also deletes the MinIO media object (TTL alone would orphan it;
 *   OrphanCleanupScheduler is the final backstop).
 * - `ownerId` + `createdAt` compound for feed queries; `deletedAt` sparse
 *   filter is handled in query predicates.
 * - `story_views` / `story_reactions`: مركّب فريد (storyId,userId) لمنع
 *   التكرار؛ الأسماء الكانونية storyId_1_viewerId_1 و storyId_1_userId_1.
 */
@Component
class StoryIndexInitializer(private val mongo: MongoTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun ensureIndex(clazz: Class<*>, index: Index, name: String) {
        runCatching { mongo.indexOps(clazz).createIndex(index) }.onFailure { e ->
            val msg = e.message ?: ""
            if (msg.contains("IndexKeySpecsConflict") || msg.contains("already exists")) {
                runCatching {
                    mongo.indexOps(clazz).dropIndex(name)
                    mongo.indexOps(clazz).createIndex(index)
                    log.info("Recreated conflicting story index {} on {}", name, clazz.simpleName)
                }.onFailure { e2 -> log.warn("Failed to recreate story index {} on {}: {}", name, clazz.simpleName, e2.message) }
            } else {
                log.warn("Failed to create story index {} on {}: {}", name, clazz.simpleName, msg)
            }
        }
    }

    private fun ensureUnique(clazz: Class<*>, fields: List<Pair<String, Sort.Direction>>, uniqueName: String) {
        val ops = mongo.indexOps(clazz)
        val existing = runCatching { ops.indexInfo }.getOrNull() ?: emptyList()
        val conflict = existing.firstOrNull { it.name == uniqueName && !it.isUnique }
        if (conflict != null) {
            runCatching { ops.dropIndex(uniqueName) }.onSuccess {
                log.info("Dropped legacy non-unique index {} on {} to upgrade to unique", uniqueName, clazz.simpleName)
            }
        }
        // نظّف الفهرس المفرد القديم storyId_1 بعد الترقية للمركّب الفريد.
        if (uniqueName == "storyId_1_viewerId_1" || uniqueName == "storyId_1_userId_1") {
            runCatching { ops.dropIndex("storyId_1") }
        }
        ensureIndex(clazz, Index().apply { fields.forEach { (f, d) -> on(f, d) }; unique(); named(uniqueName) }, uniqueName)
    }

    @PostConstruct
    fun init() {
        ensureIndex(
            StoryDocument::class.java,
            Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("expiresAt_1"),
            "expiresAt_1"
        )
        ensureIndex(
            StoryDocument::class.java,
            Index().on("ownerId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("ownerId_1_createdAt_-1"),
            "ownerId_1_createdAt_-1"
        )
        ensureUnique(
            StoryView::class.java,
            listOf("storyId" to Sort.Direction.ASC, "viewerId" to Sort.Direction.ASC),
            "storyId_1_viewerId_1"
        )
        ensureUnique(
            StoryReaction::class.java,
            listOf("storyId" to Sort.Direction.ASC, "userId" to Sort.Direction.ASC),
            "storyId_1_userId_1"
        )
    }
}
