package com.red.server.messaging

import com.red.server.database.SovereignMongoDocuments
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DeleteService(private val mongo: MongoTemplate) {
    fun deleteForEveryone(messageId: String, senderId: String): SovereignMongoDocuments.MessageDocument? {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId).and("deletedForEveryoneAt").`is`(null))
        val message = mongo.findOne(query, SovereignMongoDocuments.MessageDocument::class.java) ?: return null
        mongo.updateFirst(query, Update().set("deletedForEveryoneAt", Instant.now()).set("payload", byteArrayOf()), SovereignMongoDocuments.MessageDocument::class.java)
        return message.copy(deletedForEveryoneAt = Instant.now())
    }
}