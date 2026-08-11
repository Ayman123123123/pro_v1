package com.red.server.social

// الهوية تُقرأ من Authentication — مرشح JWT يضع principal = معرّف المستخدم (UUID نصّي).
// (وسما AuthenticatedUser/Self لم يكونا موجودين في المستودع إطلاقاً.)
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Communities Controller — مجتمعات وقنوات عامة
 *  - المجتمعات مفتوحة للقراءة للجميع، الكتابة للأعضاء فقط
 *  - كل عضو ينضم عبر /join (تلقائي، لا موافقة)
 *  - المُنشئ = ADMIN، لديه صلاحية الحذف وتعديل الإعدادات
 *  - عكس المجموعات (Groups) المشفّرة E2EE، المجتمعات عامة
 * ════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/communities")
class CommunitiesController(
    private val mongo: MongoTemplate,
    private val users: com.red.server.auth.repository.UserAccountRepository
) {
    /** List all public communities (paginated, with optional search) */
    @GetMapping
    fun list(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "30") size: Int,
        authentication: Authentication?
    ): List<CommunityResponse> {
        val userId = optionalUserId(authentication)
        val query = Query().with(Sort.by(Sort.Direction.DESC, "createdAt"))
        if (!search.isNullOrBlank()) {
            query.addCriteria(
                Criteria().orOperator(
                    Criteria.where("name").regex(search.trim(), "i"),
                    Criteria.where("description").regex(search.trim(), "i"),
                    Criteria.where("tags").regex(search.trim(), "i")
                )
            )
        }
        query.skip((page.coerceAtLeast(0) * size.coerceIn(1, 100)).toLong())
            .limit(size.coerceIn(1, 100))

        val communities = mongo.find(query, CommunityDocument::class.java)
        if (communities.isEmpty()) return emptyList()
        if (userId == null) return communities.map { it.toResponse(null, 0L) }

        val ids = communities.map { it.id }
        val memberships = mongo.find(
            Query(Criteria.where("communityId").`in`(ids).and("userId").`is`(userId)),
            CommunityMember::class.java
        )
        val memberCountByCommunity = mongo.aggregate(
            org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                org.springframework.data.mongodb.core.aggregation.Aggregation.match(
                    Criteria.where("communityId").`in`(ids)
                ),
                org.springframework.data.mongodb.core.aggregation.Aggregation.group("communityId").count().`as`("count")
            ),
            CommunityMember::class.java,
            CommunityMemberCount::class.java
        ).mappedResults.associate { it._id to it.count }

        return communities.map { community ->
            val my = memberships.firstOrNull { it.communityId == community.id }
            community.toResponse(
                myRole = my?.role,
                memberCount = memberCountByCommunity[community.id] ?: 0L
            )
        }
    }

    /** Get one community by id */
    @GetMapping("/{id}")
    fun get(@PathVariable id: String, authentication: Authentication?): ResponseEntity<CommunityResponse> {
        val userId = optionalUserId(authentication)
        val community = mongo.findById(id, CommunityDocument::class.java)
            ?: return ResponseEntity.notFound().build()
        val memberCount = mongo.count(
            Query(Criteria.where("communityId").`is`(id)),
            CommunityMember::class.java
        )
        val myRole = userId?.let {
            mongo.findOne(
                Query(Criteria.where("communityId").`is`(id).and("userId").`is`(it)),
                CommunityMember::class.java
            )?.role
        }
        return ResponseEntity.ok(community.toResponse(myRole, memberCount))
    }

    /** Create a new community — any authenticated user can create */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateCommunityRequest
    ): CommunityResponse {
        val userId = authentication.name
        require(!mongo.exists(
            Query(Criteria.where("name").`is`(request.name.trim())),
            CommunityDocument::class.java
        )) { "اسم المجتمع مستخدم بالفعل" }

        val user = users.findById(UUID.fromString(userId))
            .orElseThrow { NoSuchElementException("User not found") }

        val community = CommunityDocument(
            id = UuidV7.next(),
            name = request.name.trim(),
            description = request.description?.trim()?.takeIf { it.isNotEmpty() },
            category = request.category?.trim()?.takeIf { it.isNotEmpty() } ?: "GENERAL",
            tags = request.tags?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList(),
            isPublic = request.isPublic,
            createdBy = user.redId,
            createdByUsername = user.username,
            avatarColor = request.avatarColor?.trim()?.takeIf { it.isNotEmpty() } ?: pickRandomColor(),
            rules = request.rules?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val saved = mongo.save(community)

        // المُنشئ يصبح ADMIN تلقائياً
        mongo.save(CommunityMember(
            id = "${saved.id}:$userId",
            communityId = saved.id,
            userId = userId,
            userRedId = user.redId,
            username = user.username,
            role = CommunityRole.ADMIN,
            joinedAt = Instant.now()
        ))

        return saved.toResponse(CommunityRole.ADMIN, 1L)
    }

    /** Join a public community (or request to join for private ones) */
    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    fun join(
        authentication: Authentication,
        @PathVariable id: String
    ): ResponseEntity<CommunityResponse> {
        val userId = authentication.name
        val community = mongo.findById(id, CommunityDocument::class.java)
            ?: return ResponseEntity.notFound().build()

        if (!community.isPublic) {
            return ResponseEntity.status(403).build()
        }

        val existing = mongo.findOne(
            Query(Criteria.where("communityId").`is`(id).and("userId").`is`(userId)),
            CommunityMember::class.java
        )
        if (existing != null) {
            return ResponseEntity.ok(community.toResponse(existing.role, currentMemberCount(id)))
        }

        val user = users.findById(UUID.fromString(userId))
            .orElseThrow { NoSuchElementException("User not found") }

        mongo.save(CommunityMember(
            id = "$id:$userId",
            communityId = id,
            userId = userId,
            userRedId = user.redId,
            username = user.username,
            role = CommunityRole.MEMBER,
            joinedAt = Instant.now()
        ))
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(id)),
            org.springframework.data.mongodb.core.query.Update().inc("memberCount", 1L).set("updatedAt", Instant.now()),
            CommunityDocument::class.java
        )

        val memberCount = currentMemberCount(id)
        return ResponseEntity.ok(community.toResponse(CommunityRole.MEMBER, memberCount))
    }

    /** Leave a community (admins cannot leave if they're the only admin) */
    @PostMapping("/{id}/leave")
    @PreAuthorize("isAuthenticated()")
    fun leave(
        authentication: Authentication,
        @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        val userId = authentication.name
        val membership = mongo.findOne(
            Query(Criteria.where("communityId").`is`(id).and("userId").`is`(userId)),
            CommunityMember::class.java
        ) ?: return ResponseEntity.notFound().build()

        if (membership.role == CommunityRole.ADMIN) {
            val adminCount = mongo.count(
                Query(Criteria.where("communityId").`is`(id).and("role").`is`(CommunityRole.ADMIN.name)),
                CommunityMember::class.java
            )
            if (adminCount <= 1) {
                return ResponseEntity.status(409).body(mapOf(
                    "error" to "أنت المشرف الوحيد — حوّل الملكية لمستخدم آخر قبل المغادرة"
                ))
            }
        }

        mongo.remove(
            Query(Criteria.where("id").`is`(membership.id)),
            CommunityMember::class.java
        )
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(id)),
            org.springframework.data.mongodb.core.query.Update().inc("memberCount", -1L),
            CommunityDocument::class.java
        )
        return ResponseEntity.ok(mapOf("status" to "left"))
    }

    /** Update community settings (ADMIN only) */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun update(
        authentication: Authentication,
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateCommunityRequest
    ): ResponseEntity<CommunityResponse> {
        val userId = authentication.name
        val community = mongo.findById(id, CommunityDocument::class.java)
            ?: return ResponseEntity.notFound().build()
        val my = mongo.findOne(
            Query(Criteria.where("communityId").`is`(id).and("userId").`is`(userId)),
            CommunityMember::class.java
        )
        if (my?.role != CommunityRole.ADMIN) {
            return ResponseEntity.status(403).build()
        }

        val update = org.springframework.data.mongodb.core.query.Update()
            .set("updatedAt", Instant.now())
        request.name?.trim()?.takeIf { it.length in 2..100 }?.let { update.set("name", it) }
        request.description?.trim()?.takeIf { it.isNotEmpty() }?.let { update.set("description", it) }
        request.category?.trim()?.takeIf { it.isNotEmpty() }?.let { update.set("category", it) }
        request.avatarColor?.trim()?.takeIf { it.isNotEmpty() }?.let { update.set("avatarColor", it) }
        request.rules?.trim()?.takeIf { it.isNotEmpty() }?.let { update.set("rules", it) }
        request.isPublic?.let { update.set("isPublic", it) }
        request.tags?.let {
            update.set("tags", it.map(String::trim).map(String::lowercase).filter(String::isNotEmpty))
        }

        mongo.updateFirst(Query(Criteria.where("id").`is`(id)), update, CommunityDocument::class.java)
        val updated = mongo.findById(id, CommunityDocument::class.java)!!
        val memberCount = currentMemberCount(id)
        return ResponseEntity.ok(updated.toResponse(my.role, memberCount))
    }

    /** Delete community (ADMIN only, soft delete) */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun delete(authentication: Authentication, @PathVariable id: String): ResponseEntity<Map<String, String>> {
        val userId = authentication.name
        val my = mongo.findOne(
            Query(Criteria.where("communityId").`is`(id).and("userId").`is`(userId)),
            CommunityMember::class.java
        )
        if (my?.role != CommunityRole.ADMIN) {
            return ResponseEntity.status(403).build()
        }
        // Soft delete: mark as archived
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(id)),
            org.springframework.data.mongodb.core.query.Update()
                .set("archived", true)
                .set("archivedAt", Instant.now()),
            CommunityDocument::class.java
        )
        return ResponseEntity.ok(mapOf("status" to "archived"))
    }

    /** المعرف اختياري في نقاط القراءة العامة: يُقبل فقط إن كان UUID صالحاً (مستخدم JWT حقيقي، لا "anonymousUser"). */
    private fun optionalUserId(authentication: Authentication?): String? =
        authentication?.name?.let { name -> runCatching { UUID.fromString(name).toString() }.getOrNull() }

    private fun currentMemberCount(communityId: String): Long =
        mongo.count(Query(Criteria.where("communityId").`is`(communityId)), CommunityMember::class.java)

    private fun pickRandomColor(): String {
        val colors = listOf("#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8", "#FFD93D", "#6BCB77", "#C780FA")
        return colors.random()
    }
}

@org.springframework.data.mongodb.core.mapping.Document(collection = "communities")
data class CommunityDocument(
    val id: String,
    val name: String,
    val description: String?,
    val category: String = "GENERAL",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val createdBy: String,
    val createdByUsername: String,
    val avatarColor: String = "#45B7D1",
    val rules: String? = null,
    val archived: Boolean = false,
    val archivedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(myRole: CommunityRole?, memberCount: Long) = CommunityResponse(
        id = id,
        name = name,
        description = description,
        category = category,
        tags = tags,
        isPublic = isPublic,
        createdBy = createdBy,
        createdByUsername = createdByUsername,
        avatarColor = avatarColor,
        rules = rules,
        memberCount = memberCount,
        myRole = myRole?.name,
        isJoined = myRole != null,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}

@org.springframework.data.mongodb.core.mapping.Document(collection = "community_members")
data class CommunityMember(
    val id: String,
    val communityId: String,
    val userId: String,
    val userRedId: String,
    val username: String,
    val role: CommunityRole,
    val joinedAt: Instant
)

enum class CommunityRole {
    ADMIN, MODERATOR, MEMBER
}

data class CommunityResponse(
    val id: String,
    val name: String,
    val description: String?,
    val category: String,
    val tags: List<String>,
    val isPublic: Boolean,
    val createdBy: String,
    val createdByUsername: String,
    val avatarColor: String,
    val rules: String?,
    val memberCount: Long,
    val myRole: String?,
    val isJoined: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class CreateCommunityRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 100)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:Size(max = 50)
    val category: String? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean = true,
    @field:Size(max = 2000)
    val rules: String? = null,
    @field:Size(max = 20)
    val avatarColor: String? = null
)

data class UpdateCommunityRequest(
    @field:Size(min = 2, max = 100)
    val name: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
    @field:Size(max = 50)
    val category: String? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean? = null,
    @field:Size(max = 2000)
    val rules: String? = null,
    @field:Size(max = 20)
    val avatarColor: String? = null
)

/** Aggregation result class for member counts */
data class CommunityMemberCount(
    @org.springframework.data.annotation.Id
    val _id: String,
    val count: Long
)
