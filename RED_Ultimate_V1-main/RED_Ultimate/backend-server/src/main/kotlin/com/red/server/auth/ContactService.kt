package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.social.UserStatusService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class ContactRequestResponse(
    val id: UUID,
    val requester: PublicRedProfile,
    val createdAt: Instant
)
data class OutgoingContactRequestResponse(
    val id: UUID,
    val recipient: PublicRedProfile,
    val createdAt: Instant
)
data class ReportRequest(val redId: String, val category: String, val details: String? = null)
data class ReportResponse(val id: UUID, val status: String)

/** معلومات حضور مفصّلة: online + آخر ظهور (epoch ms أو null إن غير متاح). */
data class PresenceInfo(val online: Boolean, val lastSeen: Long?)

@Service
class ContactService(
    private val jdbc: JdbcTemplate,
    private val users: UserAccountRepository,
    private val redis: RedisTemplate<String, String>,
    private val presence: UserStatusService
) {
    fun contacts(ownerId: UUID): List<PublicRedProfile> = jdbc.query(
        """SELECT u.red_id,u.username,u.full_name,u.avatar_url FROM red_contacts c JOIN users u ON u.id=c.contact_id
           WHERE c.owner_id=? AND u.status='APPROVED' ORDER BY lower(u.full_name),lower(u.username)""",
        ::profileRow,
        ownerId
    )

    fun incoming(recipientId: UUID): List<ContactRequestResponse> = jdbc.query(
        """SELECT r.id,r.created_at,u.red_id,u.username,u.full_name,u.avatar_url FROM contact_requests r
           JOIN users u ON u.id=r.requester_id WHERE r.recipient_id=? AND r.status='PENDING'
           ORDER BY r.created_at""",
        { rs, _ -> ContactRequestResponse(rs.getObject("id", UUID::class.java), profileRow(rs, 0), rs.getTimestamp("created_at").toInstant()) },
        recipientId
    )

    fun outgoing(requesterId: UUID): List<OutgoingContactRequestResponse> = jdbc.query(
        """SELECT r.id,r.created_at,u.red_id,u.username,u.full_name,u.avatar_url FROM contact_requests r
           JOIN users u ON u.id=r.recipient_id WHERE r.requester_id=? AND r.status='PENDING'
           ORDER BY r.created_at""",
        { rs, _ -> OutgoingContactRequestResponse(rs.getObject("id", UUID::class.java), profileRow(rs, 0), rs.getTimestamp("created_at").toInstant()) },
        requesterId
    )

    /** Presence is limited to established contacts and respects online_status privacy. */
    fun presence(ownerId: UUID, requestedIds: List<String>): Map<String, Boolean> {
        require(requestedIds.size <= 100) { "At most 100 contact IDs may be checked at once" }
        val requested = requestedIds.map { it.uppercase() }.toSet()
        if (requested.isEmpty()) return emptyMap()
        val requesterRedId = users.findById(ownerId).orElse(null)?.redId ?: return emptyMap()
        val allowed = contacts(ownerId).asSequence().map(PublicRedProfile::redId).filter(requested::contains).toList()
        val cutoff = (System.currentTimeMillis() - PRESENCE_WINDOW_MS).toDouble()
        return allowed.associateWith { redId ->
            val isOnlineRaw = (redis.opsForZSet().score("red:presence:index", redId) ?: Double.NEGATIVE_INFINITY) >= cutoff
            if (!isOnlineRaw) return@associateWith false
            val privacy = runCatching { presence.getPrivacySettings(redId) }.getOrNull()
            when (privacy?.onlineStatus ?: "EVERYONE") {
                "NOBODY" -> redId == requesterRedId
                "CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH" -> redId == requesterRedId || areContacts(redId, requesterRedId)
                else -> true
            }
        }
    }

    /**
     * Presence مفصّل: يعود بـ { redId -> {online, lastSeen} }.
     * يحترم إعدادات الخصوصية (user_privacy_settings.last_seen / online_status) —
     * NOBODY / CONTACTS تُخفى إن لم يكن الطالب مخوّلاً.
     */
    fun presenceDetailed(ownerId: UUID, requestedIds: List<String>): Map<String, PresenceInfo> {
        require(requestedIds.size <= 100) { "At most 100 contact IDs may be checked at once" }
        val requested = requestedIds.map { it.uppercase() }.toSet()
        if (requested.isEmpty()) return emptyMap()
        val requesterRedId = users.findById(ownerId).orElse(null)?.redId ?: return emptyMap()
        val allowed = contacts(ownerId).asSequence().map(PublicRedProfile::redId).filter(requested::contains).toList()
        val cutoff = (System.currentTimeMillis() - PRESENCE_WINDOW_MS).toDouble()
        val lastSeenMap = allowed.associateWith { redId -> users.findByRedId(redId)?.lastSeen }
        return allowed.associateWith { redId ->
            val isOnlineRaw = (redis.opsForZSet().score("red:presence:index", redId) ?: Double.NEGATIVE_INFINITY) >= cutoff
            // احترام خصوصية online_status و last_seen للهدف
            val privacy = runCatching { presence.getPrivacySettings(redId) }.getOrNull()
            val canSeeOnline = when (privacy?.onlineStatus ?: "EVERYONE") {
                "NOBODY" -> redId == requesterRedId
                "CONTACTS" -> redId == requesterRedId || areContacts(redId, requesterRedId)
                "CONTACTS_EXCEPT", "ONLY_SHARE_WITH" -> redId == requesterRedId || areContacts(redId, requesterRedId) // مبسّط
                else -> true
            }
            val canSeeLastSeen = when (privacy?.lastSeen ?: "EVERYONE") {
                "NOBODY" -> redId == requesterRedId
                "CONTACTS" -> redId == requesterRedId || areContacts(redId, requesterRedId)
                "CONTACTS_EXCEPT", "ONLY_SHARE_WITH" -> redId == requesterRedId || areContacts(redId, requesterRedId)
                else -> true
            }
            val online = isOnlineRaw && canSeeOnline
            val lastSeenEpoch = if (!canSeeLastSeen) null else if (online) System.currentTimeMillis() else lastSeenMap[redId]?.toEpochMilli()
            // إن كان مخفياً تماماً و offline، نعيد null لآخر ظهور
            PresenceInfo(online, lastSeenEpoch)
        }
    }

    private fun areContacts(aRedId: String, bRedId: String): Boolean {
        if (aRedId == bRedId) return true
        val a = users.findByRedId(aRedId.uppercase()) ?: return false
        val b = users.findByRedId(bRedId.uppercase()) ?: return false
        val cnt = jdbc.queryForObject("SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?", Int::class.java, a.id, b.id) ?: 0
        if (cnt > 0) return true
        val cnt2 = jdbc.queryForObject("SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?", Int::class.java, b.id, a.id) ?: 0
        return cnt2 > 0
    }

    @Transactional
    fun request(requesterId: UUID, redId: String): OutgoingContactRequestResponse {
        val target = approved(redId)
        require(target.id != requesterId) { "Cannot add yourself" }
        require(!blockedEitherDirection(requesterId, target.id)) { "Contact is blocked" }
        require(jdbc.queryForObject("SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?", Int::class.java, requesterId, target.id) == 0) { "Already a contact" }
        require(jdbc.queryForObject("SELECT COUNT(*) FROM contact_requests WHERE requester_id=? AND recipient_id=? AND status='PENDING'", Int::class.java, target.id, requesterId) == 0) {
            "This user already sent you a request"
        }
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO contact_requests(id,requester_id,recipient_id,status) VALUES (?,?,?,'PENDING')
               ON CONFLICT (requester_id,recipient_id) WHERE status='PENDING'
               DO UPDATE SET status='PENDING',created_at=CURRENT_TIMESTAMP,resolved_at=NULL""",
            id, requesterId, target.id
        )
        val actualId = jdbc.queryForObject(
            "SELECT id FROM contact_requests WHERE requester_id=? AND recipient_id=?",
            UUID::class.java, requesterId, target.id
        ) ?: id
        return OutgoingContactRequestResponse(actualId, PublicRedProfile(target.redId, target.username, target.displayName), Instant.now())
    }

    @Transactional
    fun resolve(recipientId: UUID, requestId: UUID, accept: Boolean) {
        val row = jdbc.query(
            "SELECT requester_id,recipient_id FROM contact_requests WHERE id=? AND status='PENDING' FOR UPDATE",
            { rs, _ -> rs.getObject("requester_id", UUID::class.java) to rs.getObject("recipient_id", UUID::class.java) }, requestId
        ).singleOrNull() ?: throw NoSuchElementException("Pending contact request not found")
        require(row.second == recipientId) { "Only the recipient can resolve this request" }
        val status = if (accept) "ACCEPTED" else "REJECTED"
        jdbc.update("UPDATE contact_requests SET status=?,resolved_at=CURRENT_TIMESTAMP WHERE id=?", status, requestId)
        if (accept) {
            require(!blockedEitherDirection(row.first, row.second)) { "Contact is blocked" }
            // إن أُرسل طلب متبادل بالتزامن، لا يبقى طلب معلق بعد تحقق الصداقة.
            jdbc.update("UPDATE contact_requests SET status='CANCELED',resolved_at=CURRENT_TIMESTAMP WHERE requester_id=? AND recipient_id=? AND status='PENDING'", row.second, row.first)
            jdbc.update("INSERT INTO red_contacts(owner_id,contact_id) VALUES (?,?) ON CONFLICT DO NOTHING", row.first, row.second)
            jdbc.update("INSERT INTO red_contacts(owner_id,contact_id) VALUES (?,?) ON CONFLICT DO NOTHING", row.second, row.first)
            val requester = users.findById(row.first).orElse(null)
            val recipient = users.findById(row.second).orElse(null)
            if (requester != null && recipient != null) {
                presence.addContact(requester.redId, recipient.redId)
                presence.addContact(recipient.redId, requester.redId)
            }
        }
    }

    @Transactional
    fun cancel(requesterId: UUID, requestId: UUID) {
        val row = jdbc.query(
            "SELECT requester_id,status FROM contact_requests WHERE id=? FOR UPDATE",
            { rs, _ -> rs.getObject("requester_id", UUID::class.java) to rs.getString("status") },
            requestId
        ).singleOrNull() ?: throw NoSuchElementException("Contact request not found")
        require(row.first == requesterId) { "Only the requester can cancel this contact request" }
        require(row.second == "PENDING") { "Only pending contact requests can be cancelled" }
        jdbc.update("UPDATE contact_requests SET status='CANCELED',resolved_at=CURRENT_TIMESTAMP WHERE id=?", requestId)
    }

    @Transactional
    fun remove(ownerId: UUID, redId: String) {
        val target = users.findByRedId(redId.uppercase()) ?: throw NoSuchElementException("RED identity not found")
        jdbc.update("DELETE FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)", ownerId, target.id, target.id, ownerId)
        users.findById(ownerId).orElse(null)?.let { owner ->
            presence.removeContact(owner.redId, target.redId)
            presence.removeContact(target.redId, owner.redId)
        }
    }

    @Transactional
    fun block(ownerId: UUID, redId: String) {
        val target = approved(redId)
        require(target.id != ownerId) { "Cannot block yourself" }
        jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?) ON CONFLICT DO NOTHING", ownerId, target.id)
        jdbc.update("DELETE FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)", ownerId, target.id, target.id, ownerId)
        jdbc.update("UPDATE contact_requests SET status='REJECTED',resolved_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND ((requester_id=? AND recipient_id=?) OR (requester_id=? AND recipient_id=?))", ownerId, target.id, target.id, ownerId)
        jdbc.update("DELETE FROM media_grants WHERE (owner_id=? AND grantee_id=?) OR (owner_id=? AND grantee_id=?)", ownerId, target.id, target.id, ownerId)
        users.findById(ownerId).orElse(null)?.let { owner ->
            presence.removeContact(owner.redId, target.redId)
            presence.removeContact(target.redId, owner.redId)
        }
    }

    fun unblock(ownerId: UUID, redId: String) {
        val target = users.findByRedId(redId.uppercase()) ?: throw NoSuchElementException("RED identity not found")
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?", ownerId, target.id)
    }

    fun blocked(ownerId: UUID): List<PublicRedProfile> = jdbc.query(
        "SELECT u.red_id,u.username,u.full_name,u.avatar_url FROM user_blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=? ORDER BY b.created_at DESC",
        ::profileRow,
        ownerId
    )

    fun report(reporterId: UUID, request: ReportRequest): ReportResponse {
        val target = users.findByRedId(request.redId.uppercase()) ?: throw NoSuchElementException("RED identity not found")
        val category = request.category.trim().uppercase()
        require(category in REPORT_CATEGORIES) { "Unsupported report category" }
        val details = request.details?.trim()?.takeIf { it.isNotEmpty() }
        require(details == null || details.length <= 1000) { "Report details are too long" }
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO user_reports(id,reporter_id,reported_id,category,details) VALUES (?,?,?,?,?)", id, reporterId, target.id, category, details)
        return ReportResponse(id, "OPEN")
    }

    private fun approved(redId: String) = users.findByRedId(redId.trim().uppercase())
        ?.takeIf { it.status == AccountStatus.APPROVED }
        ?: throw NoSuchElementException("Approved RED identity not found")

    private fun blockedEitherDirection(first: UUID, second: UUID): Boolean =
        (jdbc.queryForObject("SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)", Int::class.java, first, second, second, first) ?: 0) > 0

    private fun profileRow(rs: ResultSet, ignored: Int) = PublicRedProfile(
        rs.getString("red_id"), rs.getString("username"), rs.getString("full_name"),
        rs.getString("avatar_url")
    )

    companion object {
        private const val PRESENCE_WINDOW_MS = 5 * 60_000L
        private val REPORT_CATEGORIES = setOf("SPAM", "HARASSMENT", "IMPERSONATION", "SCAM", "VIOLENCE", "OTHER")
    }
}
