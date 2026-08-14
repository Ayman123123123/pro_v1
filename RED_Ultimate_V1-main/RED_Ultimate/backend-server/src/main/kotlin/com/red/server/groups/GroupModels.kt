package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
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
    val privacy: GroupPrivacy = GroupPrivacy.PRIVATE,
    val settings: GroupSettings = GroupSettings(),
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

@Document("group_members")
@CompoundIndex(name = "group_member_unique", def = "{'groupId': 1, 'userId': 1}", unique = true)
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
enum class GroupPrivacy { PRIVATE, PUBLIC }

/** Server-enforced controls; clients must not be trusted to enforce group policy. */
data class GroupSettings(
    val onlyAdminsCanSend: Boolean = false,
    val onlyAdminsCanEditInfo: Boolean = true,
    val requireJoinApproval: Boolean = true
)
data class CreateGroupRequest(val name: String, val description: String? = null, val privacy: String = "PRIVATE")
data class UpdateGroupSettingsRequest(
    val onlyAdminsCanSend: Boolean,
    val onlyAdminsCanEditInfo: Boolean,
    val requireJoinApproval: Boolean
)
data class AddGroupMemberRequest(val redId: String, val role: GroupRole = GroupRole.MEMBER)
data class UpdateGroupRoleRequest(val role: GroupRole)
data class TransferGroupOwnershipRequest(val targetUserId: java.util.UUID)
data class UpdateGroupAvatarRequest(val mediaKey: String)
data class GroupResponse(val id: String, val name: String, val description: String?, val ownerRedId: String, val avatarUrl: String?, val privacy: GroupPrivacy, val settings: GroupSettings, val createdAt: Instant, val members: List<GroupMember>)
