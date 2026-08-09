package com.red.server.media

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MediaGrantService(
    private val media: MediaService,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate
) {
    @Transactional
    fun grant(ownerId: UUID, request: MediaGrantRequest): MediaGrantResponse {
        require(request.objectKey.startsWith("users/$ownerId/")) { "Media object must belong to the sender" }
        require(media.exists(request.objectKey)) { "Media object not found" }
        val grantee = users.findByRedId(request.targetRedId.trim().uppercase())
            ?: throw NoSuchElementException("Target account not found")
        require(grantee.status == AccountStatus.APPROVED) { "Target account is not approved" }
        require(grantee.id != ownerId) { "A media grant to the owner is unnecessary" }
        jdbc.update(
            """INSERT INTO media_grants(object_key,owner_id,grantee_id) VALUES (?,?,?)
               ON CONFLICT (object_key,grantee_id) DO UPDATE SET owner_id=EXCLUDED.owner_id,created_at=CURRENT_TIMESTAMP,expires_at=NULL""",
            request.objectKey, ownerId, grantee.id
        )
        return MediaGrantResponse(request.objectKey, grantee.redId)
    }

    fun revokeAll(ownerId: UUID, objectKey: String) {
        jdbc.update("DELETE FROM media_grants WHERE object_key=? AND owner_id=?", objectKey, ownerId)
    }

    /** Removes every future object grant involving an account after ban, rejection, or account closure. */
    fun revokeAllForAccount(accountId: UUID) {
        jdbc.update("DELETE FROM media_grants WHERE owner_id=? OR grantee_id=?", accountId, accountId)
    }
}

data class MediaGrantRequest(val objectKey: String = "", val targetRedId: String = "")
data class MediaGrantResponse(val objectKey: String, val targetRedId: String)
