package com.red.server.notification

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
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
        val id = "$cleanRed:${cleanToken.hashCode()}"
        val existing = mongo.findById(id, DevicePushTokenDocument::class.java)
        val doc = DevicePushTokenDocument(id, cleanRed, cleanToken, platform.ifBlank { "ANDROID" }.uppercase(), Instant.now())
        return if (existing == null) mongo.insert(doc) else mongo.save(doc)
    }

    fun tokensFor(redId: String): List<String> {
        if (redId.isBlank()) return emptyList()
        val query = Query(Criteria.where("redId").`is`(redId.trim()))
        return mongo.find(query, DevicePushTokenDocument::class.java).map { it.token }.distinct()
    }
}
