package com.red.server.auth

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID

data class PublicEcPreKey(val keyId: Int, val publicKey: String)
data class PublicKyberPreKey(val keyId: Int, val publicKey: String, val signature: String)
data class PreKeyUploadRequest(
    val ecPreKeys: List<PublicEcPreKey> = emptyList(),
    val kyberPreKeys: List<PublicKyberPreKey> = emptyList()
)
data class PreKeyStockResponse(val ecAvailable: Int, val kyberAvailable: Int, val minimumRecommended: Int = 20)
data class ConsumedPreKeyPair(
    val ecKeyId: Int,
    val ecPublicKey: ByteArray,
    val kyberKeyId: Int,
    val kyberPublicKey: ByteArray,
    val kyberSignature: ByteArray
)

@Service
class OneTimePreKeyService(
    private val jdbc: JdbcTemplate,
    private val devices: UserDeviceRepository
) {
    @Transactional
    fun upload(userId: UUID, deviceId: UUID, request: PreKeyUploadRequest): PreKeyStockResponse {
        val device = devices.findByIdAndUserId(deviceId, userId)
            ?.takeIf { it.status == DeviceStatus.APPROVED }
            ?: throw NoSuchElementException("Approved device not found")
        require(request.ecPreKeys.size <= MAX_UPLOAD && request.kyberPreKeys.size <= MAX_UPLOAD) { "Pre-key batch too large" }
        require(request.ecPreKeys.map { it.keyId }.toSet().size == request.ecPreKeys.size) { "Duplicate EC pre-key ID" }
        require(request.kyberPreKeys.map { it.keyId }.toSet().size == request.kyberPreKeys.size) { "Duplicate Kyber pre-key ID" }

        request.ecPreKeys.forEach { key ->
            require(key.keyId >= 0) { "Invalid EC pre-key ID" }
            val publicKey = decode(key.publicKey, "EC pre-key", 16, 4096)
            jdbc.update(
                "INSERT INTO one_time_ec_prekeys(device_id,key_id,public_key) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                device.id, key.keyId, publicKey
            )
        }
        request.kyberPreKeys.forEach { key ->
            require(key.keyId >= 0) { "Invalid Kyber pre-key ID" }
            val publicKey = decode(key.publicKey, "Kyber pre-key", 32, 16_384)
            val signature = decode(key.signature, "Kyber signature", 32, 512)
            jdbc.update(
                "INSERT INTO one_time_kyber_prekeys(device_id,key_id,public_key,signature) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                device.id, key.keyId, publicKey, signature
            )
        }
        return stock(device.id)
    }

    fun stock(userId: UUID, deviceId: UUID): PreKeyStockResponse {
        val device = devices.findByIdAndUserId(deviceId, userId)
            ?.takeIf { it.status == DeviceStatus.APPROVED }
            ?: throw NoSuchElementException("Approved device not found")
        return stock(device.id)
    }

    /**
     * PostgreSQL row locks and SKIP LOCKED guarantee that concurrent session requests never receive
     * the same EC/Kyber pair. Both updates occur in one statement and therefore one transaction.
     */
    @Transactional
    fun consume(deviceId: UUID): ConsumedPreKeyPair? = jdbc.query(
        CONSUME_SQL,
        { rs: ResultSet, _: Int ->
            ConsumedPreKeyPair(
                ecKeyId = rs.getInt("ec_key_id"),
                ecPublicKey = rs.getBytes("ec_public_key"),
                kyberKeyId = rs.getInt("kyber_key_id"),
                kyberPublicKey = rs.getBytes("kyber_public_key"),
                kyberSignature = rs.getBytes("kyber_signature")
            )
        },
        deviceId, deviceId, deviceId, deviceId
    ).firstOrNull()

    private fun stock(deviceId: UUID): PreKeyStockResponse {
        val ec = jdbc.queryForObject(
            "SELECT COUNT(*) FROM one_time_ec_prekeys WHERE device_id=? AND consumed_at IS NULL",
            Int::class.java, deviceId
        ) ?: 0
        val kyber = jdbc.queryForObject(
            "SELECT COUNT(*) FROM one_time_kyber_prekeys WHERE device_id=? AND consumed_at IS NULL",
            Int::class.java, deviceId
        ) ?: 0
        return PreKeyStockResponse(ec, kyber)
    }

    private fun decode(value: String, label: String, min: Int, max: Int): ByteArray {
        val decoded = runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException("Invalid $label encoding") }
        require(decoded.size in min..max) { "Invalid $label size" }
        return decoded
    }

    private companion object {
        const val MAX_UPLOAD = 100
        val CONSUME_SQL = """
            WITH picked_ec AS (
                SELECT key_id FROM one_time_ec_prekeys
                WHERE device_id=? AND consumed_at IS NULL
                ORDER BY created_at, key_id FOR UPDATE SKIP LOCKED LIMIT 1
            ), picked_kyber AS (
                SELECT key_id FROM one_time_kyber_prekeys
                WHERE device_id=? AND consumed_at IS NULL
                ORDER BY created_at, key_id FOR UPDATE SKIP LOCKED LIMIT 1
            ), updated_ec AS (
                UPDATE one_time_ec_prekeys e SET consumed_at=CURRENT_TIMESTAMP
                FROM picked_ec p
                WHERE e.device_id=? AND e.key_id=p.key_id AND EXISTS (SELECT 1 FROM picked_kyber)
                RETURNING e.key_id, e.public_key
            ), updated_kyber AS (
                UPDATE one_time_kyber_prekeys k SET consumed_at=CURRENT_TIMESTAMP
                FROM picked_kyber p
                WHERE k.device_id=? AND k.key_id=p.key_id AND EXISTS (SELECT 1 FROM picked_ec)
                RETURNING k.key_id, k.public_key, k.signature
            )
            SELECT e.key_id AS ec_key_id, e.public_key AS ec_public_key,
                   k.key_id AS kyber_key_id, k.public_key AS kyber_public_key,
                   k.signature AS kyber_signature
            FROM updated_ec e CROSS JOIN updated_kyber k
        """.trimIndent()
    }
}
