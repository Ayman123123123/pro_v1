package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("group_invites")
data class GroupInviteDocument(
    @Id val id: String,
    @Indexed val groupId: String,
    val creatorId: String,
    @Indexed(unique = true) val tokenHash: String,
    val requireApproval: Boolean,
    val maxUses: Int,
    var uses: Int = 0,
    @Indexed val expiresAt: Instant,
    var revokedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

@Document("group_join_requests")
data class GroupJoinRequestDocument(
    @Id val id: String,
    @Indexed val groupId: String,
    @Indexed val userId: String,
    val redId: String,
    val username: String,
    var status: String = "PENDING",
    val createdAt: Instant = Instant.now(),
    var resolvedAt: Instant? = null,
    var resolvedBy: String? = null
)

data class CreateGroupInviteRequest(val expiresHours: Long = 24, val maxUses: Int = 1, val requireApproval: Boolean = true)
data class GroupInviteResponse(val id: String, val token: String, val expiresAt: Instant, val maxUses: Int, val requireApproval: Boolean)
data class JoinGroupRequest(val token: String)
data class ResolveJoinRequest(val approve: Boolean)
data class GroupJoinRequestResponse(val id: String, val groupId: String, val redId: String, val username: String, val status: String, val createdAt: Instant)
