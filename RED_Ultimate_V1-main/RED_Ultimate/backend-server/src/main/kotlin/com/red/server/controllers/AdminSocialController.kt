package com.red.server.controllers

import com.red.server.groups.GroupDocument
import com.red.server.groups.GroupMember
import com.red.server.social.PostDocument
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 🛠️ إدارة المجموعات والمنشورات — لوحة الأدمن
 *
 * كل المسارات هنا تحت بادئة `/api/admin/social` فتشملها قاعدة
 * `SecurityConfig` العامة لمسارات الأدمن التي تتطلب دور ADMIN.
 *
 * البيانات في MongoDB (مجموعات `groups`/`group_members` ومنشورات
 * `posts`)، لذلك يُستعمل MongoTemplate مباشرة بدل خدمات المستخدمين
 * التي تفرض صلاحيات العضوية — الأدمن يدير بلا عضوية.
 */
@RestController
@RequestMapping("/api/admin/social")
class AdminSocialController(
    private val mongo: MongoTemplate
) {

    // ━━━━━━━━━━━━━━━━ المجموعات ━━━━━━━━━━━━━━━━

    /** إحصائيات عامة: عدد المجموعات والأعضاء والمتوسط ومجموعات اليوم */
    @GetMapping("/groups/overview")
    fun groupsOverview(): ResponseEntity<Map<String, Any>> {
        val totalGroups = mongo.count(Query(), GroupDocument::class.java)
        val totalMembers = mongo.count(Query(), GroupMember::class.java)
        val todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val createdToday = mongo.count(
            Query(Criteria.where("createdAt").gte(todayStart)),
            GroupDocument::class.java
        )
        return ResponseEntity.ok(mapOf(
            "totalGroups" to totalGroups,
            "totalMembers" to totalMembers,
            "avgMembersPerGroup" to if (totalGroups > 0) totalMembers.toDouble() / totalGroups else 0.0,
            "createdToday" to createdToday
        ))
    }

    /** قائمة المجموعات مع بحث بالاسم وعدد الأعضاء، مرتّبة بالأحدث */
    @GetMapping("/groups")
    fun listGroups(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val query = Query().with(Sort.by(Sort.Direction.DESC, "createdAt"))
        if (!q.isNullOrBlank()) {
            query.addCriteria(Criteria.where("name").regex(".*${Regex.escape(q.trim())}.*", "i"))
        }
        val total = mongo.count(query, GroupDocument::class.java)
        query.skip(page.toLong() * size).limit(size)
        val groups = mongo.find(query, GroupDocument::class.java).map { g ->
            val memberCount = mongo.count(
                Query(Criteria.where("groupId").`is`(g.id)),
                GroupMember::class.java
            )
            mapOf(
                "id" to g.id,
                "name" to g.name,
                "description" to g.description,
                "ownerRedId" to g.ownerRedId,
                "avatarUrl" to g.avatarMediaKey?.let { "/api/media/$it" },
                "memberCount" to memberCount,
                "createdAt" to g.createdAt,
                "updatedAt" to g.updatedAt
            )
        }
        return ResponseEntity.ok(mapOf(
            "content" to groups,
            "page" to page, "size" to size,
            "totalElements" to total,
            "totalPages" to if (size > 0) (total + size - 1) / size else 0
        ))
    }

    /** تفاصيل مجموعة مع قائمة أعضائها */
    @GetMapping("/groups/{groupId}")
    fun groupDetails(@PathVariable groupId: String): ResponseEntity<Any> {
        val group = mongo.findById(groupId, GroupDocument::class.java)
            ?: return ResponseEntity.notFound().build()
        val members = mongo.find(
            Query(Criteria.where("groupId").`is`(groupId)),
            GroupMember::class.java
        )
        return ResponseEntity.ok(mapOf(
            "id" to group.id,
            "name" to group.name,
            "description" to group.description,
            "ownerRedId" to group.ownerRedId,
            "avatarUrl" to group.avatarMediaKey?.let { "/api/media/$it" },
            "createdAt" to group.createdAt,
            "updatedAt" to group.updatedAt,
            "members" to members.map { m ->
                mapOf(
                    "userId" to m.userId,
                    "redId" to m.redId,
                    "username" to m.username,
                    "role" to m.role.name,
                    "joinedAt" to m.joinedAt
                )
            }
        ))
    }

    /** حذف مجموعة وكل عضوياتها */
    @DeleteMapping("/groups/{groupId}")
    fun deleteGroup(@PathVariable groupId: String): ResponseEntity<Map<String, Any>> {
        val group = mongo.findById(groupId, GroupDocument::class.java)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "GROUP_NOT_FOUND"))
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupMember::class.java)
        mongo.remove(group)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /** إزالة عضو من مجموعة */
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    fun removeMember(
        @PathVariable groupId: String,
        @PathVariable userId: String
    ): ResponseEntity<Map<String, Any>> {
        val result = mongo.remove(
            Query(Criteria.where("groupId").`is`(groupId).and("userId").`is`(userId)),
            GroupMember::class.java
        )
        return if (result.deletedCount > 0) ResponseEntity.ok(mapOf("success" to true))
        else ResponseEntity.status(404).body(mapOf("success" to false, "error" to "MEMBER_NOT_FOUND"))
    }

    // ━━━━━━━━━━━━━━━━ المنشورات ━━━━━━━━━━━━━━━━

    /** إحصائيات المنشورات: الإجمالي واليوم والمحذوف والاستطلاعات */
    @GetMapping("/posts/overview")
    fun postsOverview(): ResponseEntity<Map<String, Any>> {
        val total = mongo.count(Query(), PostDocument::class.java)
        val todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val createdToday = mongo.count(
            Query(Criteria.where("createdAt").gte(todayStart)),
            PostDocument::class.java
        )
        val deleted = mongo.count(
            Query(Criteria.where("deletedAt").ne(null)),
            PostDocument::class.java
        )
        val polls = mongo.count(
            Query(Criteria.where("kind").`is`("POLL")),
            PostDocument::class.java
        )
        return ResponseEntity.ok(mapOf(
            "totalPosts" to total,
            "createdToday" to createdToday,
            "deletedPosts" to deleted,
            "polls" to polls
        ))
    }

    /** قائمة المنشورات مع بحث بالنص أو الكاتب، مرتّبة بالأحدث */
    @GetMapping("/posts")
    fun listPosts(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) includeDeleted: Boolean = false,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Map<String, Any>> {
        val query = Query().with(Sort.by(Sort.Direction.DESC, "createdAt"))
        if (!includeDeleted) query.addCriteria(Criteria.where("deletedAt").`is`(null))
        if (!q.isNullOrBlank()) {
            val pattern = ".*${Regex.escape(q.trim())}.*"
            query.addCriteria(Criteria().orOperator(
                Criteria.where("text").regex(pattern, "i"),
                Criteria.where("authorUsername").regex(pattern, "i"),
                Criteria.where("authorDisplayName").regex(pattern, "i")
            ))
        }
        val total = mongo.count(query, PostDocument::class.java)
        query.skip(page.toLong() * size).limit(size)
        val posts = mongo.find(query, PostDocument::class.java).map { p ->
            mapOf(
                "id" to p.id,
                "authorRedId" to p.authorRedId,
                "authorUsername" to p.authorUsername,
                "authorDisplayName" to p.authorDisplayName,
                "text" to p.text.take(280),
                "visibility" to p.visibility.name,
                "kind" to p.kind.name,
                "mediaCount" to p.media.size,
                "hashtags" to p.hashtags,
                "reactionCounts" to p.reactionCounts,
                "replyCount" to p.replyCount,
                "repostCount" to p.repostCount,
                "createdAt" to p.createdAt,
                "deleted" to (p.deletedAt != null)
            )
        }
        return ResponseEntity.ok(mapOf(
            "content" to posts,
            "page" to page, "size" to size,
            "totalElements" to total,
            "totalPages" to if (size > 0) (total + size - 1) / size else 0
        ))
    }

    /** حذف ناعم لمنشور (يُبقي السجل مع deletedAt) */
    @DeleteMapping("/posts/{postId}")
    fun deletePost(@PathVariable postId: String): ResponseEntity<Map<String, Any>> {
        val post = mongo.findById(postId, PostDocument::class.java)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "POST_NOT_FOUND"))
        mongo.save(post.copy(deletedAt = Instant.now()))
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /** استعادة منشور محذوف */
    @PostMapping("/posts/{postId}/restore")
    fun restorePost(@PathVariable postId: String): ResponseEntity<Map<String, Any>> {
        val post = mongo.findById(postId, PostDocument::class.java)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "POST_NOT_FOUND"))
        mongo.save(post.copy(deletedAt = null))
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
