package com.red.server.groups

import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/groups")
class GroupController(private val groups: GroupService) {
    /** P0-F: توافق + ترقيم — بدون cursor&limit يعيد السلوك القديم، ومعهما keyset. */
    @GetMapping fun list(
        auth: Authentication,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?
    ) = if (cursor == null && limit == null) groups.list(UUID.fromString(auth.name))
        else groups.listPaged(UUID.fromString(auth.name), cursor, limit)
    /** P0-F: اكتشاف المجموعات العامة فقط. */
    @GetMapping("/discover") fun discover(
        @RequestParam(required = false, defaultValue = "") q: String,
        @RequestParam(required = false) limit: Int?
    ) = groups.discover(q, limit ?: 20)
    @PostMapping fun create(@Valid @RequestBody request: CreateGroupRequest, auth: Authentication) = groups.create(UUID.fromString(auth.name), request)
    @GetMapping("/{id}") fun details(@PathVariable id: String, auth: Authentication) = groups.details(UUID.fromString(auth.name), id)
    @PostMapping("/{id}/members") fun add(@PathVariable id: String, @RequestBody request: AddGroupMemberRequest, auth: Authentication) = groups.add(UUID.fromString(auth.name), id, request)
    @PatchMapping("/{id}/members/{userId}") fun role(@PathVariable id: String, @PathVariable userId: UUID, @RequestBody request: UpdateGroupRoleRequest, auth: Authentication) = groups.role(UUID.fromString(auth.name), id, userId, request)
    @DeleteMapping("/{id}/members/{userId}") fun remove(@PathVariable id: String, @PathVariable userId: UUID, auth: Authentication) = groups.remove(UUID.fromString(auth.name), id, userId)
    @PatchMapping("/{id}/avatar") fun avatar(@PathVariable id: String, @RequestBody request: UpdateGroupAvatarRequest, auth: Authentication) = groups.updateAvatar(UUID.fromString(auth.name), id, request)
    @PatchMapping("/{id}/settings") fun settings(@PathVariable id: String, @RequestBody request: UpdateGroupSettingsRequest, auth: Authentication) = groups.updateSettings(UUID.fromString(auth.name), id, request)
    @PostMapping("/{id}/invites") fun invite(@PathVariable id: String, @RequestBody request: CreateGroupInviteRequest, auth: Authentication) = groups.createInvite(UUID.fromString(auth.name), id, request)
    @GetMapping("/{id}/invites") fun listInvites(@PathVariable id: String, auth: Authentication) = groups.listInvites(UUID.fromString(auth.name), id)
    @DeleteMapping("/{id}/invites/{inviteId}") fun revokeInvite(@PathVariable id: String, @PathVariable inviteId: String, auth: Authentication) = groups.revokeInvite(UUID.fromString(auth.name), id, inviteId)
    @PatchMapping("/{id}") fun updateInfo(@PathVariable id: String, @RequestBody request: UpdateGroupInfoRequest, auth: Authentication) = groups.updateInfo(UUID.fromString(auth.name), id, request)
    @PatchMapping("/{id}/disappearing") fun disappearing(@PathVariable id: String, @RequestBody request: UpdateDisappearingRequest, auth: Authentication) = groups.updateDisappearing(UUID.fromString(auth.name), id, request)
    @PostMapping("/join-requests") fun requestJoin(@RequestBody request: JoinGroupRequest, auth: Authentication) = groups.requestJoin(UUID.fromString(auth.name), request)
    // LEGENDARY: معاينة قبل الانضمام الأعمى (واتساب يعرض الاسم/الصورة/العدد)
    @GetMapping("/invites/preview") fun previewInvite(@RequestParam token: String, auth: Authentication): Map<String, Any?> {
        UUID.fromString(auth.name) // مصادقة فقط — لا حاجة عضوية
        return groups.previewInvite(token)
    }
    @GetMapping("/{id}/join-requests") fun joinRequests(@PathVariable id: String, auth: Authentication) = groups.joinRequests(UUID.fromString(auth.name), id)
    @PostMapping("/{id}/join-requests/{requestId}") fun resolveJoin(@PathVariable id: String, @PathVariable requestId: String, @RequestBody request: ResolveJoinRequest, auth: Authentication) = groups.resolveJoinRequest(UUID.fromString(auth.name), id, requestId, request.approve)
    @PostMapping("/{id}/transfer-ownership") fun transfer(@PathVariable id: String, @RequestBody request: TransferGroupOwnershipRequest, auth: Authentication) = groups.transferOwnership(UUID.fromString(auth.name), id, request.targetUserId)
    @DeleteMapping("/{id}/membership") fun leave(@PathVariable id: String, auth: Authentication) = groups.leave(UUID.fromString(auth.name), id)
    @DeleteMapping("/{id}") fun delete(@PathVariable id: String, auth: Authentication) = groups.delete(UUID.fromString(auth.name), id)
    // ── P0-F: حظر المجموعات ──
    @PostMapping("/{id}/ban") fun ban(@PathVariable id: String, @RequestBody request: BanGroupMemberRequest, auth: Authentication) = groups.ban(UUID.fromString(auth.name), id, request)
    @DeleteMapping("/{id}/bans/{userId}") fun unban(@PathVariable id: String, @PathVariable userId: UUID, auth: Authentication) = groups.unban(UUID.fromString(auth.name), id, userId)
    @GetMapping("/{id}/bans") fun listBans(@PathVariable id: String, auth: Authentication) = groups.listBans(UUID.fromString(auth.name), id)
    // ── LEGENDARY: تفعيل الدوال الميتة (كانت 404 رغم وجود أزرار UI) ──
    @PatchMapping("/{id}/slow-mode") fun slowMode(@PathVariable id: String, @RequestBody body: Map<String, Int>, auth: Authentication) =
        groups.updateSlowMode(UUID.fromString(auth.name), id, (body["slowModeSeconds"] ?: 0).coerceIn(0, 3600))
    @GetMapping("/{id}/slow-mode") fun getSlow(@PathVariable id: String, auth: Authentication): Map<String, Any?> {
        groups.details(UUID.fromString(auth.name), id) // فحص عضوية أولاً
        return mapOf("groupId" to id, "slowModeSeconds" to groups.slowModeSeconds(id), "archived" to groups.isGroupEnded(id))
    }
    @PostMapping("/{id}/messages/{messageUuid}/delete-for-all") fun adminDelete(@PathVariable id: String, @PathVariable messageUuid: String, auth: Authentication) =
        groups.deleteForAllByAdmin(UUID.fromString(auth.name), id, messageUuid)
    @PostMapping("/{id}/end") fun end(@PathVariable id: String, auth: Authentication) =
        groups.endGroup(UUID.fromString(auth.name), id)
    // LEGENDARY: ربط المجتمع (مدير فقط)
    @PostMapping("/{id}/community") fun setCommunity(
        @PathVariable id: String, @RequestBody request: SetGroupCommunityRequest, auth: Authentication
    ) = groups.setCommunity(UUID.fromString(auth.name), id, request.communityId)
    // LEGENDARY: من قرأ — علامة رتيبة + قائمة القراء
    @PostMapping("/{id}/read") fun markRead(
        @PathVariable id: String, @RequestBody request: MarkGroupReadRequest, auth: Authentication
    ) = groups.markRead(UUID.fromString(auth.name), id, request.sequence)
    @GetMapping("/{id}/reads") fun readers(
        @PathVariable id: String, @RequestParam sequence: Long, auth: Authentication
    ) = groups.readers(UUID.fromString(auth.name), id, sequence)
}
