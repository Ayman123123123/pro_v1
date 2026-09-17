package com.red.server.messaging

// MessageDocument is a top-level class declared in database/SovereignMongoDocuments.kt
// (the legacy duplicate database/MessageDocument.kt was removed). Import the class itself.
import com.red.server.database.MessageDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class DeleteService(private val mongo: MongoTemplate) {
    fun deleteForEveryone(userRedId: String, messageId: String): Boolean {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(userRedId).and("deletedForEveryoneAt").`is`(null))
        val message = mongo.findOne(query, MessageDocument::class.java) ?: return false
        mongo.updateFirst(query, Update().set("deletedForEveryoneAt", Instant.now()).set("payload", byteArrayOf()), MessageDocument::class.java)
        return true
    }

    fun deleteForMe(userRedId: String, messageId: String): Boolean {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("receiverId").`is`(userRedId).and("deletedForMeAt").`is`(null))
        val message = mongo.findOne(query, MessageDocument::class.java) ?: return false
        mongo.updateFirst(query, Update().set("deletedForMeAt", Instant.now()), MessageDocument::class.java)
        return true
    }
}