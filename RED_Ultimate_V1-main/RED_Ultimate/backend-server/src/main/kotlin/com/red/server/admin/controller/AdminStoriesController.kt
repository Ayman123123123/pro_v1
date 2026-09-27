package com.red.server.admin.controller

import com.red.server.admin.service.AdminService
import com.red.server.media.MediaService
import com.red.server.stories.StoryDocument
import com.red.server.stories.StoryView
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * 🛡️ إشراف الأدمن على الحالات (القصص) — WhatsApp-like statuses.
 *
 * مصدر واحد للحقيقة: مجموعة Mongo `stories` (نفس مجموعة `/api/stories`).
 * كل عمليات التأثير (إخفاء/حذف/تطهير) تُسجَّل إلزاميًا في `admin_audit_log`.
 * لا يُكشف محتوى الوسائط هنا — الإخفاء يعتمّد الوصول دون حذف كائن MinIO،
 * والحذف يزيل الوسائط نهائيًا (كما يفعل `StoryService.delete`).
 *
 * ملاحظة أمنية: المسارات تحت النطاق الإداري المحمي بـ hasRole("ADMIN").
 */
@RestController
@RequestMapping("/api/admin/stories")
class AdminStoriesController(
    private val mongo: MongoTemplate,
    private val media: MediaService,
    private val adminService: AdminService
) {
    private val log = LoggerFactory.getLogger(AdminStoriesController::class.java)

    data class AdminStoryRow(
        val id: String,
        val ownerId: String,
        val ownerRedId: String,
        val ownerUsername: String,
        val ownerDisplayName: String,
        val mediaType: String,
        val caption: String?,
        val visibility: String,
        val viewCount: Long,
        val createdAt: Instant,
        val expiresAt: Instant,
        val hidden: Boolean,
        val deleted: Boolean
    )

    private fun activeCriteria(): Criteria =
        Criteria.where("expiresAt").gt(Instant.now()).and("deletedAt").`is`(null)

    /** GET /api/admin/stories/overview — إحصائيات سريعة لصفحة المحتوى. */
    @GetMapping("/overview")
    fun overview(): ResponseEntity<Map<String, Any>> {
        val now = Instant.now()
        val active = mongo.count(Query(activeCriteria()), StoryDocument::class.java)
        val hidden = mongo.count(
            Query(Criteria.where("expiresAt").gt(now).and("deletedAt").`is`(null).and("hiddenAt").ne(null)),
            StoryDocument::class.java
        )
        val views = mongo.count(Query(Criteria.where("viewedAt").gt(now.minusSeconds(86_400))), StoryView::class.java)
        return ResponseEntity.ok(
            mapOf(
                "activeStories" to active,
                "hiddenStories" to hidden,
                "viewsLast24h" to views
            )
        )
    }

    /**
     * GET /api/admin/stories — قائمة مُصفّحة لكل الحالات النشطة (+ المنسدلة/المخفية اختياريًا).
     * إخفاء المالك: `?owner=` يقيّد على مستخدم (نمط متسق مع باقي واجهات الإدارة).
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false, defaultValue = "false") includeHidden: Boolean,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val safeSize = size.coerceIn(1, 100)
        val now = Instant.now()
        val criteria = Criteria()
        if (!includeDeleted) criteria.and("deletedAt").`is`(null)
        if (!includeHidden) criteria.and("hiddenAt").`is`(null)
        if (!includeDeleted && !includeHidden) criteria.and("expiresAt").gt(now)
        if (!owner.isNullOrBlank()) criteria.orOperator(
            Criteria.where("ownerId").`is`(owner.trim()),
            Criteria.where("ownerRedId").`is`(owner.trim())
        )
        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, "createdAt"))
            .skip(page.toLong() * safeSize.toLong())
            .limit(safeSize)
        val docs = mongo.find(query, StoryDocument::class.java)
        val total = mongo.count(Query(criteria), StoryDocument::class.java)
        val hiddenIds = mutableSetOf<String>()
        if (docs.isNotEmpty()) {
            mongo.find(
                Query(Criteria.where("id").`in`(docs.map(StoryDocument::id)).and("hiddenAt").ne(null)),
                StoryDocument::class.java
            ).forEach { hiddenIds.add(it.id) }
        }
        val rows = docs.map { d ->
            val views = mongo.count(Query(Criteria.where("storyId").`is`(d.id)), StoryView::class.java)
            AdminStoryRow(
                id = d.id,
                ownerId = d.ownerId,
                ownerRedId = d.ownerRedId,
                ownerUsername = d.ownerUsername,
                ownerDisplayName = d.ownerDisplayName,
                mediaType = d.mediaType,
                caption = d.caption,
                visibility = d.visibility.name,
                viewCount = views,
                createdAt = d.createdAt,
                expiresAt = d.expiresAt,
                hidden = d.id in hiddenIds,
                deleted = d.deletedAt != null
            )
        }
        return ResponseEntity.ok(
            mapOf(
                "content" to rows,
                "page" to page,
                "size" to safeSize,
                "totalElements" to total,
                "totalPages" to (total + safeSize - 1) / safeSize
            )
        )
    }

    /** POST /api/admin/stories/{id}/hide — تعمية الوصول بلا حذف نهائي (قابل للتراجع). */
    @PostMapping("/{id}/hide")
    fun hide(@PathVariable id: String, authentication: Authentication): ResponseEntity<Any> {
        val result = mongo.updateFirst(
            Query(activeCriteria().and("id").`is`(id)),
            Update().set("hiddenAt", Instant.now()),
            StoryDocument::class.java
        )
        if (result.modifiedCount == 0L) {
            return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "STORY_NOT_FOUND"))
        }
        audit(authentication, "STORY_HIDDEN", id, "Admin hid a story (soft moderation)")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /** POST /api/admin/stories/{id}/unhide — التراجع عن الإخفاء. */
    @PostMapping("/{id}/unhide")
    fun unhide(@PathVariable id: String, authentication: Authentication): ResponseEntity<Any> {
        val result = mongo.updateFirst(
            Query(Criteria.where("id").`is`(id).and("hiddenAt").ne(null)),
            Update().unset("hiddenAt"),
            StoryDocument::class.java
        )
        if (result.modifiedCount == 0L) {
            return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "STORY_NOT_HIDDEN"))
        }
        audit(authentication, "STORY_UNHIDDEN", id, "Admin un-hid a story")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /** DELETE /api/admin/stories/{id} — حذف نهائي + إزالة كائن الوسائط من MinIO. */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, authentication: Authentication): ResponseEntity<Any> {
        val story = mongo.findOne(
            Query(Criteria.where("id").`is`(id).and("deletedAt").`is`(null)),
            StoryDocument::class.java
        ) ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "STORY_NOT_FOUND"))
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(id)),
            Update().set("deletedAt", Instant.now()),
            StoryDocument::class.java
        )
        if (story.mediaKey.isNotBlank()) runCatching { media.delete(story.mediaKey) }
            .onFailure { log.warn("admin story delete: media cleanup failed for {}: {}", id, it.message) }
        mongo.remove(Query(Criteria.where("storyId").`is`(id)), StoryView::class.java)
        audit(authentication, "STORY_DELETED", id, "Admin deleted a story permanently (owner=${story.ownerId})")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /** POST /api/admin/stories/{id}/purge — تطهير فوري بجميع مشاهداته (بشرية: يتأكد العميل). */
    @PostMapping("/{id}/purge")
    fun purge(@PathVariable id: String, authentication: Authentication): ResponseEntity<Any> = delete(id, authentication)

    private fun audit(authentication: Authentication, action: String, storyId: String, description: String) {
        val adminId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        runCatching {
            adminService.recordAudit(
                adminId = adminId,
                adminUsername = authentication.principal?.toString() ?: authentication.name,
                action = action,
                category = "MODERATION",
                targetType = "STORY",
                targetId = storyId,
                description = description,
                severity = "WARNING"
            )
        }.onFailure { log.error("audit write failed for {} / {}: {}", action, storyId, it.message) }
    }
}
