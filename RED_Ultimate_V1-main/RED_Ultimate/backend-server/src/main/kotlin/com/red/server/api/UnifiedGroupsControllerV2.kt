package com.red.server.api

import com.red.server.groups.AddGroupMemberRequest
import com.red.server.groups.CreateGroupRequest
import com.red.server.groups.GroupResponse
import com.red.server.groups.GroupService
import com.red.server.groups.UpdateGroupInfoRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * compat V2 للمجموعات — نحيف ودائم.
 *
 * كان in-memory ويَثق بترويسة `X-RED-ID` (انتحال هوية) ويقبل addMember بلا دور.
 * الآن: الهوية من `Authentication` فقط (JWT principal = user UUID)، والتخزين
 * والـACL عبر [GroupService] الموحد (Mongo: GroupDocument/GroupMember —
 * OWNER/ADMIN/MODERATOR/MEMBER). لا توجد نسخة تخزين ثانية هنا.
 */
@RestController
@RequestMapping("/api/groups/v2")
class UnifiedGroupsControllerV2(
    private val groups: GroupService
) {

    data class CreateGroupRequestV2(
        val name: String,
        val description: String? = null,
        val privacy: String = "PRIVATE",
        val memberIds: List<String> = emptyList()
    )

    private fun callerId(auth: Authentication): UUID = UUID.fromString(auth.name)

    @PostMapping
    fun createGroup(
        auth: Authentication,
        @RequestBody request: CreateGroupRequestV2
    ): ResponseEntity<GroupResponse> {
        val cleanName = request.name.trim()
        require(cleanName.length in 2..100) { "اسم المجموعة يجب أن يكون 2-100" }
        val created = groups.create(
            callerId(auth),
            CreateGroupRequest(
                name = cleanName,
                description = request.description?.trim()?.takeIf { it.isNotEmpty() },
                privacy = request.privacy
            )
        )
        // أعضاء أوليون عبر المسار النظامي (يفحص الحظر + يمنع تصعيد الدور).
        request.memberIds.distinct().filter { it.isNotBlank() }.take(50).forEach { redId ->
            runCatching {
                groups.add(callerId(auth), created.id, AddGroupMemberRequest(redId.trim().uppercase()))
            }
        }
        return ResponseEntity.ok(groups.details(callerId(auth), created.id))
    }

    @GetMapping
    fun listGroups(auth: Authentication): ResponseEntity<List<GroupResponse>> =
        ResponseEntity.ok(groups.list(callerId(auth)))

    @GetMapping("/{groupId}")
    fun getGroup(
        auth: Authentication,
        @PathVariable groupId: String
    ): ResponseEntity<GroupResponse> =
        ResponseEntity.ok(groups.details(callerId(auth), groupId))

    @PatchMapping("/{groupId}")
    fun updateGroup(
        auth: Authentication,
        @PathVariable groupId: String,
        @RequestBody updates: Map<String, Any>
    ): ResponseEntity<GroupResponse> {
        val updated = groups.updateInfo(
            callerId(auth),
            groupId,
            UpdateGroupInfoRequest(
                name = updates["name"] as? String,
                description = updates["description"] as? String,
                privacy = updates["privacy"] as? String
            )
        )
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{groupId}")
    fun deleteGroup(
        auth: Authentication,
        @PathVariable groupId: String
    ): ResponseEntity<Map<String, Any>> {
        // GroupService.delete يشترط OWNER.
        groups.delete(callerId(auth), groupId)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "تم حذف المجموعة"))
    }

    @PostMapping("/{groupId}/members")
    fun addMember(
        auth: Authentication,
        @PathVariable groupId: String,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<GroupResponse> {
        // الـACL داخل GroupService.add: يحترم onlyAdminsCanAddMembers ويمنع
        // منح ADMIN/MODERATOR إلا للمالك. الدور هنا MEMBER دائماً.
        val newMemberId = request["redId"] ?: request["userId"]
        require(!newMemberId.isNullOrBlank()) { "redId مطلوب" }
        return ResponseEntity.ok(
            groups.add(callerId(auth), groupId, AddGroupMemberRequest(newMemberId.trim().uppercase()))
        )
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    fun removeMember(
        auth: Authentication,
        @PathVariable groupId: String,
        @PathVariable memberId: UUID
    ): ResponseEntity<GroupResponse> {
        // GroupService.remove: OWNER يزيل أياً كان، ADMIN يزيل MEMBER فقط،
        // والمالك لا يُزال. memberId هو user UUID (عقد /api/groups الموحد).
        return ResponseEntity.ok(groups.remove(callerId(auth), groupId, memberId))
    }

    @GetMapping("/stats")
    fun getStats(auth: Authentication): ResponseEntity<Map<String, Any>> {
        callerId(auth) // مصادقة فقط — الإحصاء من المخزن الدائم الموحد.
        return ResponseEntity.ok(
            mapOf(
                "totalGroups" to groups.count(),
                "source" to "persistent:groups",
                "roles" to listOf("OWNER", "ADMIN", "MODERATOR", "MEMBER"),
                "privacy" to listOf("PUBLIC", "PRIVATE", "SECRET")
            )
        )
    }
}
