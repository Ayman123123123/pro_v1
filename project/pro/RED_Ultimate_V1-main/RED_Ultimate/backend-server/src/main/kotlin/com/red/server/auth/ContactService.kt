package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.jdbc.core.JdbcTemplate
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
data class ReportRequest(val redId: String, val category: String, val details: String? = null)
data class ReportResponse(val id: UUID, val status: String)

@Service
class ContactService(private val jdbc: JdbcTemplate, private val users: UserAccountRepository) {
    fun contacts(ownerId: UUID): List<PublicRedProfile> = jdbc.query(
        """SELECT u.red_id,u.username,u.full_name FROM red_contacts c JOIN users u ON u.id=c.contact_id
           WHERE c.owner_id=? AND u.status='APPROVED' ORDER BY lower(u.full_name),lower(u.username)""",
        ::profileRow,
        ownerId
    )

    fun incoming(recipientId: UUID): List<ContactRequestResponse> = jdbc.query(
        """SELECT r.id,r.created_at,u.red_id,u.username,u.full_name FROM contact_requests r
           JOIN users u ON u.id=r.requester_id WHERE r.recipient_id=? AND r.status='PENDING'
           ORDER BY r.created_at""",
        { rs, _ -> ContactRequestResponse(rs.getObject("id", UUID::class.java), profileRow(rs, 0), rs.getTimestamp("created_at").toInstant()) },
        recipientId
    )

    @Transactional
    fun request(requesterId: UUID, redId: String): ContactRequestResponse {
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
               ON CONFLICT (requester_id,recipient_id) DO UPDATE SET status='PENDING',created_at=CURRENT_TIMESTAMP,resolved_at=NULL""",
            id, requesterId, target.id
        )
        val actualId = jdbc.queryForObject(
            "SELECT id FROM contact_requests WHERE requester_id=? AND recipient_id=?",
            UUID::class.java, requesterId, target.id
        ) ?: id
        return ContactRequestResponse(actualId, PublicRedProfile(target.redId, target.username, target.displayName), Instant.now())
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
            jdbc.update("INSERT INTO red_contacts(owner_id,contact_id) VALUES (?,?) ON CONFLICT DO NOTHING", row.first, row.second)
            jdbc.update("INSERT INTO red_contacts(owner_id,contact_id) VALUES (?,?) ON CONFLICT DO NOTHING", row.second, row.first)
        }
    }

    @Transactional
    fun remove(ownerId: UUID, redId: String) {
        val target = users.findByRedId(redId.uppercase()) ?: throw NoSuchElementException("RED identity not found")
        jdbc.update("DELETE FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)", ownerId, target.id, target.id, ownerId)
    }

    @Transactional
    fun block(ownerId: UUID, redId: String) {
        val target = approved(redId)
        require(target.id != ownerId) { "Cannot block yourself" }
        jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?) ON CONFLICT DO NOTHING", ownerId, target.id)
        jdbc.update("DELETE FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)", ownerId, target.id, target.id, ownerId)
        jdbc.update("UPDATE contact_requests SET status='REJECTED',resolved_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND ((requester_id=? AND recipient_id=?) OR (requester_id=? AND recipient_id=?))", ownerId, target.id, target.id, ownerId)
    }

    fun unblock(ownerId: UUID, redId: String) {
        val target = users.findByRedId(redId.uppercase()) ?: throw NoSuchElementException("RED identity not found")
        jdbc.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?", ownerId, target.id)
    }

    fun blocked(ownerId: UUID): List<PublicRedProfile> = jdbc.query(
        "SELECT u.red_id,u.username,u.full_name FROM user_blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=? ORDER BY b.created_at DESC",
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
        rs.getString("red_id"), rs.getString("username"), rs.getString("full_name")
    )

    companion object {
        private val REPORT_CATEGORIES = setOf("SPAM", "HARASSMENT", "IMPERSONATION", "SCAM", "VIOLENCE", "OTHER")
    }
}
