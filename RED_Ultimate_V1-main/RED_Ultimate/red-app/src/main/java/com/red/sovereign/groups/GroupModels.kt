package com.red.sovereign.groups

import kotlinx.serialization.Serializable

@Serializable data class GroupMember(val id: String, val groupId: String, val userId: String, val redId: String, val username: String, val role: String, val joinedAt: String)
@Serializable data class GroupSettings(val onlyAdminsCanSend: Boolean = false, val onlyAdminsCanEditInfo: Boolean = true, val requireJoinApproval: Boolean = true)
@Serializable data class Group(val id: String, val name: String, val description: String? = null, val ownerRedId: String, val avatarUrl: String? = null, val privacy: String = "PRIVATE", val settings: GroupSettings = GroupSettings(), val createdAt: String, val members: List<GroupMember> = emptyList())
@Serializable data class CreateGroupRequest(val name: String, val description: String? = null, val privacy: String = "PRIVATE")
@Serializable data class AddGroupMemberRequest(val redId: String, val role: String = "MEMBER")
@Serializable data class UpdateGroupRoleRequest(val role: String)
@Serializable data class TransferGroupOwnershipRequest(val targetUserId: String)
@Serializable data class UpdateGroupAvatarRequest(val mediaKey: String)
@Serializable data class UpdateGroupSettingsRequest(val onlyAdminsCanSend: Boolean, val onlyAdminsCanEditInfo: Boolean, val requireJoinApproval: Boolean)
@Serializable data class CreateGroupInviteRequest(val expiresHours: Long = 24, val maxUses: Int = 1, val requireApproval: Boolean = true)
@Serializable data class GroupInviteResponse(val id: String, val token: String, val expiresAt: String, val maxUses: Int, val requireApproval: Boolean)
@Serializable data class JoinGroupRequest(val token: String)
@Serializable data class ResolveJoinRequest(val approve: Boolean)
@Serializable data class GroupJoinRequestResponse(val id: String, val groupId: String, val redId: String, val username: String, val status: String, val createdAt: String)
