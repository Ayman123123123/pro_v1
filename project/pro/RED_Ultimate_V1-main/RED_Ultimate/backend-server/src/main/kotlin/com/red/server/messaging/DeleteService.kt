package com.red.server.messaging

import com.red.server.database.MessageDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DeleteService(private val mongo: MongoTemplate) {
    fun deleteForEveryone(messageId: String, senderId: String): MessageDocument? {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId).and("deletedAt").`is`(null))
        val message = mongo.findOne(query, MessageDocument::class.java) ?: return null
        mongo.updateFirst(query, Update().set("deletedAt", Instant.now()).set("payload", byteArrayOf()), MessageDocument::class.java)
        return message.copy(payload = byteArrayOf(), deletedAt = Instant.now())
    }
}
