package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("groups")
data class GroupDocument(
    @Id val id: String,
    val name: String,
    val description: String?,
    val ownerRedId: String,
    val avatarMediaKey: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

@Document("group_members")
data class GroupMember(
    @Id val id: String,
    @Indexed val groupId: String,
    @Indexed val userId: String,
    val redId: String,
    val username: String,
    var role: GroupRole,
    val joinedAt: Instant = Instant.now()
)

enum class GroupRole { OWNER, ADMIN, MEMBER }
data class CreateGroupRequest(val name: String, val description: String? = null)
data class AddGroupMemberRequest(val redId: String, val role: GroupRole = GroupRole.MEMBER)
data class UpdateGroupRoleRequest(val role: GroupRole)
data class TransferGroupOwnershipRequest(val targetUserId: java.util.UUID)
data class UpdateGroupAvatarRequest(val mediaKey: String)
data class GroupResponse(val id: String, val name: String, val description: String?, val ownerRedId: String, val avatarUrl: String?, val createdAt: Instant, val members: List<GroupMember>)
