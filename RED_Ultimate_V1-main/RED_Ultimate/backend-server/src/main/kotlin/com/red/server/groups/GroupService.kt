package com.red.server.groups

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class GroupService(private val mongo: MongoTemplate, private val users: UserAccountRepository, private val media: MediaService) {
    private val random = SecureRandom()
    fun create(ownerId: UUID, request: CreateGroupRequest): GroupResponse {
        val owner = users.findById(ownerId).orElseThrow { NoSuchElementException("User not found") }
        val name = request.name.trim(); require(name.length in 2..100) { "Group name must be 2-100 characters" }
        val description = request.description?.trim()?.takeIf(String::isNotEmpty); require(description == null || description.length <= 500)
        val group = mongo.save(GroupDocument(UuidV7.next(), name, description, owner.redId))
        mongo.save(GroupMember("${group.id}:${owner.id}", group.id, owner.id.toString(), owner.redId, owner.username, GroupRole.OWNER))
        return response(group)
    }

    fun list(userId: UUID): List<GroupResponse> {
        val ids = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), GroupMember::class.java).map(GroupMember::groupId)
        if (ids.isEmpty()) return emptyList()
        return mongo.find(Query(Criteria.where("id").`in`(ids)).with(Sort.by(Sort.Direction.DESC, "updatedAt")), GroupDocument::class.java).map(::response)
    }

    fun details(userId: UUID, groupId: String): GroupResponse {
        membership(groupId, userId)
        return response(group(groupId))
    }

    fun add(actorId: UUID, groupId: String, request: AddGroupMemberRequest): GroupResponse {
        requireManager(groupId, actorId)
        require(request.role != GroupRole.OWNER) { "Ownership transfer requires a dedicated operation" }
        val target = users.findByRedId(request.redId) ?: throw NoSuchElementException("RED identity not found")
        val id = "$groupId:${target.id}"
        require(!mongo.exists(Query(Criteria.where("id").`is`(id)), GroupMember::class.java)) { "User is already a member" }
        mongo.save(GroupMember(id, groupId, target.id.toString(), target.redId, target.username, request.role))
        touch(groupId)
        return response(group(groupId))
    }

    fun role(actorId: UUID, groupId: String, targetUserId: UUID, request: UpdateGroupRoleRequest): GroupResponse {
        val actor = membership(groupId, actorId); require(actor.role == GroupRole.OWNER) { "Only owner can change roles" }
        require(request.role != GroupRole.OWNER) { "Ownership transfer is not supported yet" }
        val target = membership(groupId, targetUserId); require(target.role != GroupRole.OWNER)
        target.role = request.role; mongo.save(target); touch(groupId)
        return response(group(groupId))
    }

    fun remove(actorId: UUID, groupId: String, targetUserId: UUID): GroupResponse {
        val actor = membership(groupId, actorId); val target = membership(groupId, targetUserId)
        require(target.role != GroupRole.OWNER) { "Owner cannot be removed" }
        require(actor.role == GroupRole.OWNER || (actor.role == GroupRole.ADMIN && target.role == GroupRole.MEMBER)) { "Insufficient group permission" }
        mongo.remove(Query(Criteria.where("id").`is`(target.id)), GroupMember::class.java); touch(groupId)
        return response(group(groupId))
    }

    fun transferOwnership(ownerId: UUID, groupId: String, targetUserId: UUID): GroupResponse {
        val currentOwner = membership(groupId, ownerId)
        require(currentOwner.role == GroupRole.OWNER) { "Only owner can transfer ownership" }
        val target = membership(groupId, targetUserId)
        require(target.id != currentOwner.id) { "Target must be another member" }
        currentOwner.role = GroupRole.ADMIN
        target.role = GroupRole.OWNER
        mongo.save(currentOwner); mongo.save(target)
        val group = group(groupId)
        val targetAccount = users.findById(targetUserId).orElseThrow { NoSuchElementException("Target account not found") }
        val updated = group.copy(ownerRedId = targetAccount.redId, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    fun delete(ownerId: UUID, groupId: String) {
        require(membership(groupId, ownerId).role == GroupRole.OWNER) { "Only owner can delete group" }
        group(groupId).avatarMediaKey?.let { runCatching { media.delete(it) } }
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupMember::class.java)
        mongo.remove(Query(Criteria.where("id").`is`(groupId)), GroupDocument::class.java)
    }

    fun leave(userId: UUID, groupId: String) {
        val member = membership(groupId, userId); require(member.role != GroupRole.OWNER) { "Owner must transfer or delete the group" }
        mongo.remove(Query(Criteria.where("id").`is`(member.id)), GroupMember::class.java); touch(groupId)
    }

    fun updateAvatar(actorId: UUID, groupId: String, request: UpdateGroupAvatarRequest): GroupResponse {
        requireManager(groupId, actorId)
        require(request.mediaKey.startsWith("users/$actorId/")) { "Group avatar must belong to the manager" }
        require(media.exists(request.mediaKey)) { "Avatar media not found" }
        require(media.metadata(request.mediaKey).mimeType.startsWith("image/")) { "Group avatar must be an image" }
        val current = group(groupId)
        current.avatarMediaKey?.let { if (it != request.mediaKey) runCatching { media.delete(it) } }
        val updated = current.copy(avatarMediaKey = request.mediaKey, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    fun createInvite(actorId: UUID, groupId: String, request: CreateGroupInviteRequest): GroupInviteResponse {
        requireManager(groupId, actorId)
        require(request.expiresHours in 1..168) { "Invite expiry must be 1-168 hours" }
        require(request.maxUses in 1..100) { "Invite max uses must be 1-100" }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val invite = mongo.save(GroupInviteDocument(
            id = UuidV7.next(), groupId = groupId, creatorId = actorId.toString(), tokenHash = hashToken(token),
            requireApproval = request.requireApproval, maxUses = request.maxUses,
            expiresAt = Instant.now().plus(request.expiresHours, ChronoUnit.HOURS)
        ))
        return GroupInviteResponse(invite.id, token, invite.expiresAt, invite.maxUses, invite.requireApproval)
    }

    fun requestJoin(userId: UUID, request: JoinGroupRequest): GroupJoinRequestResponse {
        val invite = mongo.findOne(Query(Criteria.where("tokenHash").`is`(hashToken(request.token))), GroupInviteDocument::class.java)
            ?: throw NoSuchElementException("Invite not found")
        require(invite.revokedAt == null && invite.expiresAt.isAfter(Instant.now()) && invite.uses < invite.maxUses) { "Invite is expired or exhausted" }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val memberId = "${invite.groupId}:$userId"
        require(!mongo.exists(Query(Criteria.where("id").`is`(memberId)), GroupMember::class.java)) { "User is already a member" }
        invite.uses += 1; mongo.save(invite)
        if (!invite.requireApproval) {
            mongo.save(GroupMember(memberId, invite.groupId, user.id.toString(), user.redId, user.username, GroupRole.MEMBER)); touch(invite.groupId)
            return GroupJoinRequestResponse("joined:$memberId", invite.groupId, user.redId, user.username, "APPROVED", Instant.now())
        }
        val id = "${invite.groupId}:$userId"
        val pending = mongo.findById(id, GroupJoinRequestDocument::class.java)?.takeIf { it.status == "PENDING" }
            ?: mongo.save(GroupJoinRequestDocument(id, invite.groupId, user.id.toString(), user.redId, user.username))
        return pending.response()
    }

    fun joinRequests(actorId: UUID, groupId: String): List<GroupJoinRequestResponse> {
        requireManager(groupId, actorId)
        return mongo.find(Query(Criteria.where("groupId").`is`(groupId).and("status").`is`("PENDING")).with(Sort.by("createdAt")), GroupJoinRequestDocument::class.java).map { it.response() }
    }

    fun resolveJoinRequest(actorId: UUID, groupId: String, requestId: String, approve: Boolean): GroupResponse {
        requireManager(groupId, actorId)
        val pending = mongo.findById(requestId, GroupJoinRequestDocument::class.java)
            ?: throw NoSuchElementException("Join request not found")
        require(pending.groupId == groupId && pending.status == "PENDING") { "Join request is not pending" }
        pending.status = if (approve) "APPROVED" else "REJECTED"; pending.resolvedAt = Instant.now(); pending.resolvedBy = actorId.toString(); mongo.save(pending)
        if (approve && !mongo.exists(Query(Criteria.where("id").`is`("$groupId:${pending.userId}")), GroupMember::class.java)) {
            mongo.save(GroupMember("$groupId:${pending.userId}", groupId, pending.userId, pending.redId, pending.username, GroupRole.MEMBER)); touch(groupId)
        }
        return response(group(groupId))
    }

    fun revokeInvite(actorId: UUID, groupId: String, inviteId: String) {
        requireManager(groupId, actorId)
        val invite = mongo.findById(inviteId, GroupInviteDocument::class.java) ?: throw NoSuchElementException("Invite not found")
        require(invite.groupId == groupId); invite.revokedAt = Instant.now(); mongo.save(invite)
    }

    fun count(): Long = mongo.count(Query(), GroupDocument::class.java)

    private fun hashToken(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun GroupJoinRequestDocument.response() = GroupJoinRequestResponse(id, groupId, redId, username, status, createdAt)

    private fun group(id: String) = mongo.findById(id, GroupDocument::class.java) ?: throw NoSuchElementException("Group not found")
    private fun membership(groupId: String, userId: UUID) = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java)
        ?: throw NoSuchElementException("Group membership not found")
    private fun requireManager(groupId: String, userId: UUID) = membership(groupId, userId).also { require(it.role == GroupRole.OWNER || it.role == GroupRole.ADMIN) }
    private fun touch(groupId: String) { group(groupId).also { it.updatedAt = Instant.now(); mongo.save(it) } }
    private fun response(group: GroupDocument) = GroupResponse(group.id, group.name, group.description, group.ownerRedId, group.avatarMediaKey?.let { "/api/media/$it" }, group.createdAt,
        mongo.find(Query(Criteria.where("groupId").`is`(group.id)).with(Sort.by(Sort.Direction.ASC, "joinedAt")), GroupMember::class.java))
}
