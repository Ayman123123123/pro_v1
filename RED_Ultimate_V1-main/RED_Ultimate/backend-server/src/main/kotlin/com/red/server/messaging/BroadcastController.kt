package com.red.server.messaging

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.social.AudienceGuard
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 📢 قوائم البث — V50 (مثل واتساب Broadcast Lists)
 *
 * الخادم يحفظ العضوية فقط؛ التوزيع الفعلي E2EE fan-out من جهاز المالك
 * (الخادم لا يملك مفاتيح التشفير فلا يستطيع إنشاء N نسخة مشفرة).
 * POST /{id}/send يعيد قائمة المستلمين المعتمدين ليفانّع العميل عليها.
 *
 * CRUD:
 * - GET    /api/broadcasts — قوائم المالك
 * - POST   /api/broadcasts {name} — إنشاء
 * - GET    /api/broadcasts/{id} — تفاصيل + عدد الأعضاء
 * - DELETE /api/broadcasts/{id} — حذف
 * - GET    /api/broadcasts/{id}/members — الأعضاء
 * - POST   /api/broadcasts/{id}/members {redId|userId} — إضافة
 * - DELETE /api/broadcasts/{id}/members/{userId} — إزالة
 * - POST   /api/broadcasts/{id}/send — مستلمو التوزيع (للمالك)
 */
@RestController
@RequestMapping("/api/broadcasts")
class BroadcastController(
    private val jdbc: JdbcTemplate,
    private val users: UserAccountRepository
) {

    data class CreateListRequest(val name: String = "")
    data class AddMemberRequest(val redId: String? = null, val userId: UUID? = null)

    @GetMapping
    fun list(auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        val lists = jdbc.query(
            """SELECT l.id, l.name, l.created_at, COUNT(m.user_id) AS member_count
               FROM broadcast_lists l LEFT JOIN broadcast_members m ON m.list_id = l.id
               WHERE l.owner_id=? GROUP BY l.id ORDER BY l.created_at DESC""",
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id", UUID::class.java).toString(),
                    "name" to rs.getString("name"),
                    "createdAt" to rs.getTimestamp("created_at").toInstant().toString(),
                    "memberCount" to rs.getInt("member_count")
                )
            },
            owner
        )
        return ResponseEntity.ok(mapOf("lists" to lists, "count" to lists.size))
    }

    @PostMapping
    fun create(@RequestBody req: CreateListRequest, auth: Authentication): ResponseEntity<Any> {
        val name = req.name.trim()
        require(name.length in 1..100) { "INVALID_LIST_NAME" }
        val owner = UUID.fromString(auth.name)
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO broadcast_lists(id, owner_id, name) VALUES (?,?,?)", id, owner, name)
        return ResponseEntity.ok(mapOf("success" to true, "id" to id.toString(), "name" to name))
    }

    @GetMapping("/{id}")
    fun details(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        val row = jdbc.query(
            """SELECT l.id, l.name, l.created_at, COUNT(m.user_id) AS member_count
               FROM broadcast_lists l LEFT JOIN broadcast_members m ON m.list_id = l.id
               WHERE l.id=? AND l.owner_id=? GROUP BY l.id""",
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id", UUID::class.java).toString(),
                    "name" to rs.getString("name"),
                    "createdAt" to rs.getTimestamp("created_at").toInstant().toString(),
                    "memberCount" to rs.getInt("member_count")
                )
            },
            id, owner
        ).firstOrNull() ?: throw NoSuchElementException("LIST_NOT_FOUND")
        return ResponseEntity.ok(row)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        val removed = jdbc.update("DELETE FROM broadcast_lists WHERE id=? AND owner_id=?", id, owner) > 0
        if (!removed) throw NoSuchElementException("LIST_NOT_FOUND")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/{id}/members")
    fun members(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        requireOwned(id, owner)
        val members = jdbc.query(
            """SELECT m.user_id, u.red_id, u.username, u.display_name
               FROM broadcast_members m JOIN users u ON u.id = m.user_id
               WHERE m.list_id=? ORDER BY m.added_at ASC""",
            { rs, _ ->
                mapOf(
                    "userId" to rs.getObject("user_id", UUID::class.java).toString(),
                    "redId" to rs.getString("red_id"),
                    "username" to rs.getString("username"),
                    "displayName" to rs.getString("display_name")
                )
            },
            id
        )
        return ResponseEntity.ok(mapOf("members" to members, "count" to members.size))
    }

    @PostMapping("/{id}/members")
    fun addMember(@PathVariable id: UUID, @RequestBody req: AddMemberRequest, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        requireOwned(id, owner)
        val target = when {
            req.redId != null -> users.findByRedId(req.redId.trim().uppercase())
                ?: throw NoSuchElementException("USER_NOT_FOUND")
            req.userId != null -> users.findById(req.userId)
                .orElseThrow { NoSuchElementException("USER_NOT_FOUND") }
            else -> throw IllegalArgumentException("INVALID_MEMBER_REF")
        }
        require(target.status == AccountStatus.APPROVED) { "USER_NOT_APPROVED" }
        // حارس الجمهور: المحظور ثنائيًا لا يُضاف لقوائم البث
        require(!AudienceGuard.isBlockedEitherDirection(jdbc, owner, target.id)) { "CONTACT_BLOCKED" }
        jdbc.update(
            "INSERT INTO broadcast_members(list_id, user_id) VALUES (?,?) ON CONFLICT DO NOTHING",
            id, target.id
        )
        return ResponseEntity.ok(mapOf("success" to true, "userId" to target.id.toString(), "redId" to target.redId))
    }

    @DeleteMapping("/{id}/members/{userId}")
    fun removeMember(@PathVariable id: UUID, @PathVariable userId: UUID, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        requireOwned(id, owner)
        val removed = jdbc.update("DELETE FROM broadcast_members WHERE list_id=? AND user_id=?", id, userId) > 0
        if (!removed) throw NoSuchElementException("MEMBER_NOT_FOUND")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    /**
     * مستلمو التوزيع للـ E2EE fan-out من جهاز المالك.
     * يعيد فقط الحسابات المعتمدة (المعلّقة/المحظورة تُستبعد بصمت).
     * حارس الجمهور: المحظورون ثنائيًا مع المالك يُستبعدون بصمت.
     */
    @PostMapping("/{id}/send")
    fun sendRecipients(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val owner = UUID.fromString(auth.name)
        requireOwned(id, owner)
        val memberIds = jdbc.query(
            "SELECT user_id FROM broadcast_members WHERE list_id=?",
            { rs, _ -> rs.getObject("user_id", UUID::class.java) },
            id
        )
        if (memberIds.isEmpty()) return ResponseEntity.ok(mapOf("recipients" to emptyList<Any>(), "count" to 0))
        val blocked = AudienceGuard.blockedAuthorIds(jdbc, owner).toSet()
        val recipients = users.findAllById(memberIds)
            .filter { it.status == AccountStatus.APPROVED && it.id != owner && it.id.toString() !in blocked }
            .map { mapOf("userId" to it.id.toString(), "redId" to it.redId, "username" to it.username, "displayName" to it.displayName) }
        return ResponseEntity.ok(mapOf("recipients" to recipients, "count" to recipients.size))
    }

    private fun requireOwned(id: UUID, owner: UUID) {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM broadcast_lists WHERE id=? AND owner_id=?",
            Int::class.java, id, owner
        ) ?: 0
        if (count == 0) throw NoSuchElementException("LIST_NOT_FOUND")
    }
}
