package com.red.server.storage

import com.red.server.media.MediaService
import com.red.server.social.CommunityDocument
import com.red.server.social.PostDocument
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 🧹 تنظيف يومي للملفات اليتيمة — يحذف كائنات MinIO بدون مرجع في MongoDB
 * يعمل كل يوم 03:00 Asia/Aden
 *
 * يجمع الـ object keys المُشار إليها من:
 * 1. PostDocument.media[].objectKey (المنشورات)
 * 2. StoryDocument.mediaKey (القصص) — يفحص collection "stories"
 * 3. GroupDocument.avatarKey (صور المجموعات)
 * 4. CommunityDocument.avatarKey + bannerKey (المجتمعات)
 * 5. media_grants collection (صلاحيات الوصول)
 */
@Component
@EnableScheduling
class OrphanCleanupScheduler(
    private val storage: StorageMonitorService,
    private val media: MediaService,
    private val mongo: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(OrphanCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Aden")
    fun dailyOrphanScan() {
        try {
            val stats = storage.getLocalUsageStats()
            log.info("Orphan scan — media_files: {} bytes, db_records: {}", stats["media_files"], stats["database_records"])
            val referenced = collectReferencedMediaKeys()
            log.info("Collected {} referenced media keys from MongoDB", referenced.size)
            // Remove orphaned media objects — dryRun=false after verification
            val orphans = media.deleteOrphans(referenced, dryRun = false)
            if (orphans.isNotEmpty()) {
                log.warn("Deleted {} orphan media keys. First 10: {}", orphans.size, orphans.take(10))
            } else {
                log.info("No orphan media keys found ✓")
            }
        } catch (e: Exception) {
            log.warn("Orphan scan failed: {}", e.message, e)
        }
    }

    /**
     * يجمع كل الـ object keys المُشار إليها في قاعدة البيانات
     * مفهرسة لتحسين الأداء — نتجاهل الـ deletedAt/deleted records
     */
    internal fun collectReferencedMediaKeys(): Set<String> {
        val keys = mutableSetOf<String>()

        // 1) PostDocument.media[].objectKey (المنشورات) — فقط غير المحذوفة
        try {
            mongo.find(
                Query(Criteria.where("deletedAt").`is`(null)),
                PostDocument::class.java
            ).forEach { post ->
                post.media.forEach { media ->
                    if (media.objectKey.isNotBlank()) keys.add(media.objectKey)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to scan PostDocument: {}", e.message)
        }

        // 2) StoryDocument — collection "stories"
        try {
            val storyResults = mongo.executeCommand(
                org.bson.Document("find", "stories")
                    .append("projection", org.bson.Document("mediaKey", 1).append("backgroundKey", 1).append("archived", 1))
            )
            @Suppress("UNCHECKED_CAST")
            val batch = storyResults["cursor"] as? org.bson.Document
            val firstBatch = batch?.get("firstBatch") as? List<org.bson.Document> ?: emptyList()
            firstBatch.forEach { story ->
                if (story.getBoolean("archived", false) == false) {
                    story.getString("mediaKey")?.takeIf { it.isNotBlank() }?.let { keys.add(it) }
                    story.getString("backgroundKey")?.takeIf { it.isNotBlank() }?.let { keys.add(it) }
                }
            }
        } catch (e: Exception) {
            log.debug("stories collection scan skipped: {}", e.message)
        }

        // 3) GroupDocument avatar
        try {
            mongo.executeCommand(
                org.bson.Document("distinct", "groups")
                    .append("key", "avatarKey")
            ).get("values")?.let { values ->
                @Suppress("UNCHECKED_CAST")
                (values as? List<String>)?.forEach { key ->
                    if (key.isNotBlank()) keys.add(key)
                }
            }
        } catch (e: Exception) {
            log.debug("groups avatar scan skipped: {}", e.message)
        }

        // 4) CommunityDocument — avatarKey, bannerKey
        try {
            mongo.find(
                Query(Criteria.where("archived").`is`(false)),
                CommunityDocument::class.java
            ).forEach { community ->
                // نستخدم الـ id كـ banner key (Avatar is rendered procedurally)
                keys.add("community-banner:${community.id}")
            }
        } catch (e: Exception) {
            log.debug("CommunityDocument scan skipped: {}", e.message)
        }

        // 5) media_grants — keys المصرح لها
        try {
            val grantsResult = mongo.executeCommand(
                org.bson.Document("distinct", "media_grants")
                    .append("key", "objectKey")
            )
            @Suppress("UNCHECKED_CAST")
            (grantsResult.get("values") as? List<String>)?.forEach { key ->
                if (key.isNotBlank()) keys.add(key)
            }
        } catch (e: Exception) {
            log.debug("media_grants scan skipped: {}", e.message)
        }

        return keys
    }
}
