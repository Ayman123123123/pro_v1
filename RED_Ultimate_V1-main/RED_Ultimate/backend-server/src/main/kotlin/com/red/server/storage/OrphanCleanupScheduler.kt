package com.red.server.storage

import com.red.server.database.ChannelDocument
import com.red.server.groups.GroupDocument
import com.red.server.media.MediaGrantService
import com.red.server.media.MediaService
import com.red.server.social.PostDocument
import com.red.server.stories.StoryDocument
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 🧹 تنظيف يومي للملفات اليتيمة — يحذف كائنات MinIO بدون مرجع حي.
 * يعمل كل يوم 03:00 Asia/Aden.
 *
 * ضمانات السلامة (fail-closed):
 * 1. dryRun=true هو الافتراضي — لا حذف حقيقي إلا بتفعيل صريح
 *    (red.media.cleanup.dry-run=false).
 * 2. الجردة fail-closed: فشل أي مصدر مراجع يُلغي دورة الحذف كاملةً.
 * 3. فترة سماح (red.media.cleanup.grace-days، افتراضي 7): الكائنات الجديدة
 *    (رفوعات جارية، شظايا ‎.partN، وسائط لم تُربط بعد) محمية دائمًا.
 * 4. العمر المجهول (فشل stat) = حماية، لا حذف.
 * 5. مهلة إجمالية (red.media.cleanup.timeout-seconds، افتراضي 300): انتهاؤها
 *    يُوقف الدورة فورًا بلا حذف لاحق، والجردة المتجاوِزة تُعدّ ناقصة.
 *
 * مصادر المراجع (أسماء الحقول/الجداول الصحيحة):
 * 1. posts (Mongo): media[].objectKey + poll.options[].imageUrl — غير المحذوفة
 * 2. stories (Mongo): mediaKey — كل المستندات غير المحذوفة (find مُرقَّم، لا firstBatch)
 * 3. groups (Mongo): avatarMediaKey (وليس avatarKey)
 * 4. channels (Mongo): avatarMediaKey
 * 5. messages/group_messages/channel_messages (Mongo): attachments[].mediaKey
 *    (distinct بخادم Mongo على المسار المنقّط — بلا تحميل للمستندات كاملةً)
 * 6. users.avatar_url (Postgres/JDBC): صور البروفايل
 * 7. media_grants.object_key (Postgres/JDBC — المنح السارية فقط): ليست في MongoDB
 * 8. مشتقات thumbs/<key>: محمية تبعًا لأصلها
 * ملاحظة: المجتمعات بلا مفاتيح وسائط (أفاتار لوني إجرائي avatarColor فقط) —
 * لا تُجرد. تسجيلات المكالمات/البث عناوين خارجية (vodUrl/hlsUrl) لا مفاتيح MinIO.
 */
@Component
@EnableScheduling
class OrphanCleanupScheduler(
    private val storage: StorageMonitorService,
    private val media: MediaService,
    private val mongo: MongoTemplate,
    private val jdbc: JdbcTemplate,
    private val grants: MediaGrantService,
    /** معاينة فقط افتراضيًا — اقلبها false صراحةً لتفعيل الحذف بعد المراجعة. */
    @Value("\${red.media.cleanup.dry-run:true}") private val dryRunDefault: Boolean,
    @Value("\${red.media.cleanup.grace-days:7}") private val graceDays: Long,
    @Value("\${red.media.cleanup.timeout-seconds:300}") private val timeoutSeconds: Long = 300
) {
    private val log = LoggerFactory.getLogger(OrphanCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Aden")
    fun dailyOrphanScan() {
        try {
            val deadline = Instant.now().plusSeconds(timeoutSeconds.coerceAtLeast(30))
            val stats = storage.getLocalUsageStats()
            log.info("Orphan scan — media_files: {} bytes, db_records: {}", stats["media_files"], stats["database_records"])
            val inventory = collectReferencedMediaKeys(deadline)
            log.info("Collected {} referenced media keys (complete={})", inventory.keys.size, inventory.complete)
            if (!inventory.complete) {
                log.error(
                    "Reference inventory INCOMPLETE (failed sources: {}) — skipping deletion entirely (fail-closed)",
                    inventory.failures
                )
                return
            }
            if (Instant.now().isAfter(deadline)) {
                log.error("Orphan scan TIMEOUT before deletion — skipping deletion entirely (fail-closed)")
                return
            }
            val grace = Duration.ofDays(graceDays.coerceAtLeast(0))
            val timeout = Duration.between(Instant.now(), deadline).let { if (it.isNegative) Duration.ZERO else it }
            val candidates = media.deleteOrphans(inventory.keys, dryRun = dryRunDefault, gracePeriod = grace, timeout = timeout)
            if (dryRunDefault) {
                if (candidates.isNotEmpty()) {
                    log.warn("DRY-RUN preview: {} orphan candidates NOT deleted. First 10: {}", candidates.size, candidates.take(10))
                } else {
                    log.info("No orphan media keys found ✓")
                }
            } else if (candidates.isNotEmpty()) {
                log.warn("Deleted {} orphan media keys. First 10: {}", candidates.size, candidates.take(10))
            } else {
                log.info("No orphan media keys found ✓")
            }
        } catch (e: Exception) {
            log.warn("Orphan scan failed: {}", e.message, e)
        }
    }

    /**
     * يجمع كل الـ object keys المُشار إليها حيًّا. فشل أي مصدر يُسجَّل في
     * [ReferenceInventory.failures] ويجعل الجردة ناقصة — والمجدول عندها
     * لا يحذف شيئًا إطلاقًا. تجاوز [deadline] يُسجَّل كفشل مهلة (fail-closed).
     */
    fun collectReferencedMediaKeys(): ReferenceInventory =
        collectReferencedMediaKeys(Instant.now().plusSeconds(timeoutSeconds.coerceAtLeast(30)))

    fun collectReferencedMediaKeys(deadline: Instant): ReferenceInventory {
        fun expired(): Boolean = Instant.now().isAfter(deadline)
        val keys = mutableSetOf<String>()
        val failures = mutableListOf<String>()

        // 1) المنشورات — media[].objectKey + صور خيارات الاستطلاعات
        try {
            mongo.find(
                Query(Criteria.where("deletedAt").`is`(null)),
                PostDocument::class.java
            ).forEach { post ->
                post.media.forEach { m -> normalizeMediaKey(m.objectKey)?.let { keys.add(it) } }
                post.poll?.options?.forEach { opt -> normalizeMediaKey(opt.imageUrl)?.let { keys.add(it) } }
            }
        } catch (e: Exception) {
            failures.add("posts:${e.message}")
            log.warn("Failed to scan posts: {}", e.message)
        }

        // 2) القصص — كل المستندات غير المحذوفة (find مُرقَّم من السائق، لا firstBatch)
        try {
            mongo.find(
                Query(Criteria.where("deletedAt").`is`(null)),
                StoryDocument::class.java
            ).forEach { story ->
                normalizeMediaKey(story.mediaKey)?.let { keys.add(it) }
            }
        } catch (e: Exception) {
            failures.add("stories:${e.message}")
            log.warn("Failed to scan stories: {}", e.message)
        }

        // 3) صور المجموعات — الحقل الصحيح avatarMediaKey
        try {
            mongo.find(Query(), GroupDocument::class.java).forEach { group ->
                normalizeMediaKey(group.avatarMediaKey)?.let { keys.add(it) }
            }
        } catch (e: Exception) {
            failures.add("groups:${e.message}")
            log.warn("Failed to scan groups: {}", e.message)
        }

        // 4) صور القنوات — avatarMediaKey
        try {
            mongo.find(Query(), ChannelDocument::class.java).forEach { channel ->
                normalizeMediaKey(channel.avatarMediaKey)?.let { keys.add(it) }
            }
        } catch (e: Exception) {
            failures.add("channels:${e.message}")
            log.warn("Failed to scan channels: {}", e.message)
        }

        // 5) مرفقات المحادثات (خاصة/مجموعات/قنوات) — الحقل الصحيح attachments.mediaKey
        mapOf(
            "messages" to "messages",
            "group_messages" to "group_messages",
            "channel_messages" to "channel_messages"
        ).forEach { (source, collection) ->
            if (expired()) {
                failures.add("$source:timeout")
                log.warn("Orphan inventory timeout before {}", collection)
                return@forEach
            }
            try {
                mongo.getCollection(collection)
                    .distinct("attachments.mediaKey", String::class.java)
                    .forEach { raw -> normalizeMediaKey(raw)?.let { keys.add(it) } }
            } catch (e: Exception) {
                failures.add("$source:${e.message}")
                log.warn("Failed to scan {}: {}", collection, e.message)
            }
        }

        if (expired()) {
            failures.add("timeout:inventory-deadline-exceeded")
            log.warn("Orphan inventory timeout — marking incomplete (fail-closed)")
            return ReferenceInventory(keys = keys, complete = false, failures = failures)
        }

        // 6) صور البروفايل — users.avatar_url في Postgres
        try {
            jdbc.queryForList(
                "SELECT DISTINCT avatar_url FROM users WHERE avatar_url IS NOT NULL AND avatar_url <> ''",
                String::class.java
            ).forEach { raw -> normalizeMediaKey(raw)?.let { keys.add(it) } }
        } catch (e: Exception) {
            failures.add("users.avatar_url:${e.message}")
            log.warn("Failed to scan users.avatar_url: {}", e.message)
        }

        // 7) المنح السارية — media_grants في Postgres (ليست MongoDB!)
        try {
            grants.listActiveGrantedKeys().forEach { raw -> normalizeMediaKey(raw)?.let { keys.add(it) } }
        } catch (e: Exception) {
            failures.add("media_grants:${e.message}")
            log.warn("Failed to scan media_grants: {}", e.message)
        }

        // 8) المصغّرات المشتقة thumbs/<key> تتبع أصلها المرجعي
        val derived = keys.filter { !it.startsWith("thumbs/") }.map { "thumbs/$it" }
        keys.addAll(derived)

        return ReferenceInventory(keys = keys, complete = failures.isEmpty(), failures = failures)
    }

    /**
     * تطبيع مفتاح الوسائط: يقبل المفتاح الخام أو مسار ‎/api/media/‎ الكامل
     * أو URL كاملًا، ويعيد المفتاح الخام (users/…‎). فارغ/فارغ-المعنى → null.
     */
    internal fun normalizeMediaKey(raw: String?): String? {
        var key = raw?.trim().orEmpty()
        if (key.isBlank()) return null
        val apiMarker = "/api/media/"
        val markerAt = key.indexOf(apiMarker)
        if (markerAt >= 0) key = key.substring(markerAt + apiMarker.length)
        key = key.substringBefore('?').trim()
        return key.takeIf { it.isNotBlank() }
    }
}

/** نتيجة جردة المراجع: complete=false تعني إلغاء أي حذف (fail-closed). */
data class ReferenceInventory(
    val keys: Set<String>,
    val complete: Boolean,
    val failures: List<String> = emptyList()
)
