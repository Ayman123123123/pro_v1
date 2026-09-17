package com.red.server.notification

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant

@Document("device_push_tokens")
data class DevicePushTokenDocument(
    @Id val id: String,
    @Indexed val redId: String,
    val token: String,
    val platform: String = "ANDROID",
    val updatedAt: Instant = Instant.now()
)

@Service
class DevicePushTokenService(private val mongo: MongoTemplate) {
    /**
     * معرّف الوثيقة = redId + SHA-256(token).
     * hashCode()‎ القديم 32-bit (تصادمات + سالب) — استُبدل بـ SHA-256 hex.
     * remove() يحذف المعرفين الجديد والقديم لتنظيف البقايا المهاجرة.
     */
    fun register(redId: String, token: String, platform: String = "ANDROID"): DevicePushTokenDocument {
        val cleanRed = redId.trim()
        val cleanToken = token.trim()
        require(cleanRed.isNotBlank()) { "redId is required" }
        require(cleanToken.length in 16..4096) { "push token is invalid" }
        val id = "$cleanRed:${sha256Hex(cleanToken)}"
        val existing = mongo.findById(id, DevicePushTokenDocument::class.java)
        val doc = DevicePushTokenDocument(id, cleanRed, cleanToken, platform.ifBlank { "ANDROID" }.uppercase(), Instant.now())
        return if (existing == null) mongo.insert(doc) else mongo.save(doc)
    }

    fun remove(redId: String, token: String) {
        if (redId.isBlank() || token.isBlank()) return
        val red = redId.trim()
        val tok = token.trim()
        mongo.remove(Query(Criteria.where("id").`is`("$red:${sha256Hex(tok)}")), DevicePushTokenDocument::class.java)
        // توافق رجعي: نظّف بقايا معرّف hashCode()‎ القديم إن وُجدت.
        mongo.remove(Query(Criteria.where("id").`is`("$red:${tok.hashCode()}")), DevicePushTokenDocument::class.java)
    }

    companion object {
        fun sha256Hex(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            return buildString(digest.size * 2) { digest.forEach { append("%02x".format(it)) } }
        }
    }

    fun tokensFor(redId: String): List<String> {
        if (redId.isBlank()) return emptyList()
        val query = Query(Criteria.where("redId").`is`(redId.trim()))
        return mongo.find(query, DevicePushTokenDocument::class.java).map { it.token }.distinct()
    }
}
