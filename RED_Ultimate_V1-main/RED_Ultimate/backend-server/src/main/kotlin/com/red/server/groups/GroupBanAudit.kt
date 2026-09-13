package com.red.server.groups

import com.red.server.social.UuidV7
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * P0-F: حظر المجموعات + سجل التدقيق.
 * مجموعات Mongo: group_bans + group_audit_log عبر MongoTemplate (Document بسيط).
 */
@Document("group_bans")
@CompoundIndex(name = "group_ban_unique", def = "{'groupId': 1, 'userId': 1}", unique = true)
data class GroupBan(
    @Id val id: String,
    @Indexed val groupId: String,
    @Indexed val userId: String,
    val redId: String,
    val reason: String? = null,
    val bannedBy: String,
    val bannedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

@Document("group_audit_log")
data class GroupAudit(
    @Id val id: String = UuidV7.next(),
    @Indexed val groupId: String,
    val action: String,
    val actor: String,
    val target: String? = null,
    val at: Instant = Instant.now(),
    val details: String? = null
)

data class BanGroupMemberRequest(
    val userId: UUID,
    val reason: String? = null,
    val expiresAt: Instant? = null
)

data class GroupBanResponse(
    val groupId: String,
    val userId: String,
    val redId: String,
    val reason: String?,
    val bannedBy: String,
    val bannedAt: Instant,
    val expiresAt: Instant?
)

fun GroupBan.toResponse() = GroupBanResponse(groupId, userId, redId, reason, bannedBy, bannedAt, expiresAt)
