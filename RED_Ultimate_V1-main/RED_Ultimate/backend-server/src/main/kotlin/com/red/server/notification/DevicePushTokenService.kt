package com.red.server.notification

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
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
    fun register(redId: String, token: String, platform: String = "ANDROID"): DevicePushTokenDocument {
        val cleanRed = redId.trim()
        val cleanToken = token.trim()
        require(cleanRed.isNotBlank()) { "redId is required" }
        require(cleanToken.length in 16..4096) { "push token is invalid" }
        val id = docId(cleanRed, cleanToken)
        val existing = mongo.findById(id, DevicePushTokenDocument::class.java)
        val doc = DevicePushTokenDocument(id, cleanRed, cleanToken, platform.ifBlank { "ANDROID" }.uppercase(), Instant.now())
        return if (existing == null) mongo.insert(doc) else mongo.save(doc)
    }

    fun remove(redId: String, token: String) {
        if (redId.isBlank() || token.isBlank()) return
        mongo.remove(Query(Criteria.where("id").`is`(docId(redId.trim(), token.trim()))), DevicePushTokenDocument::class.java)
    }

    /** إزالة ازدواج: اشتقاق المعرف في موضع واحد — SHA-256 مستقر بلا تصادم hashCode. */
    private fun docId(redId: String, token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        val hex = digest.take(12).joinToString("") { "%02x".format(it) }
        return "$redId:$hex"
    }

    /** TTL الرموز: حذف الرموز الخاملة >180 يومًا أسبوعيًا حتى لا تتراكم للأبد. */
    @Scheduled(fixedDelay = 7 * 24 * 60 * 60 * 1000L)
    fun cleanupStaleTokens(): Long {
        val cutoff = Instant.now().minusSeconds(180L * 24 * 3600)
        return runCatching {
            mongo.remove(
                Query(Criteria.where("updatedAt").lt(cutoff)),
                DevicePushTokenDocument::class.java
            ).deletedCount
        }.getOrDefault(0L)
    }

    fun tokensFor(redId: String): List<String> {
        if (redId.isBlank()) return emptyList()
        val query = Query(Criteria.where("redId").`is`(redId.trim()))
        return mongo.find(query, DevicePushTokenDocument::class.java).map { it.token }.distinct()
    }
}
