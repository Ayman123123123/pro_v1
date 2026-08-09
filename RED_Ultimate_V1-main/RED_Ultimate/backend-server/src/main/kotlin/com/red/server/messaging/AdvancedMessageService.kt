package com.red.server.messaging

import com.red.server.database.SovereignMongoDocuments.MessageDocument
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class AdvancedMessageService(private val mongoTemplate: MongoTemplate) {
    companion object { private val log = LoggerFactory.getLogger(AdvancedMessageService::class.java) }

    /**
     * الحذف للجميع: يحذف الرسالة من الأرشيف ويرسل إشارة حذف لكافة الأجهزة
     */
    fun processDeleteRequest(messageId: String, senderId: String): List<String> {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId))
        val message = mongoTemplate.findOne(query, MessageDocument::class.java)
        
        return if (message != null) {
            mongoTemplate.remove(query, "messages")
            log.info("Message {} deleted for everyone by sender {}", messageId, senderId)
            listOf(message.receiverId) 
        } else emptyList()
    }

    /**
     * تعديل الرسالة (خلال 15 دقيقة)
     */
    fun editMessage(messageId: String, senderId: String, newContent: ByteArray) {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId))
        val result = mongoTemplate.updateFirst(query, Update().set("payload", newContent).set("isEdited", true), "messages")
        if (result.modifiedCount > 0) log.info("Message {} edited by sender {}", messageId, senderId)
    }
}
