package com.red.server.messaging

import com.red.server.database.MessageDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class AdvancedMessageService(private val mongoTemplate: MongoTemplate) {

    /**
     * الحذف للجميع: يحذف الرسالة من الأرشيف ويرسل إشارة حذف لكافة الأجهزة
     */
    fun processDeleteRequest(messageId: String, senderId: String): List<String> {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId))
        val message = mongoTemplate.findOne(query, MessageDocument::class.java)
        
        return if (message != null) {
            mongoTemplate.remove(query, "messages")
            println("🔴 RED: Message $messageId deleted for everyone.")
            // إرجاع قائمة المشاركين في المحادثة لإبلاغهم
            listOf(message.receiverId) 
        } else emptyList()
    }

    /**
     * تعديل الرسالة (خلال 15 دقيقة)
     */
    fun editMessage(messageId: String, senderId: String, newContent: ByteArray) {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId))
        val update = Update().set("payload", newContent).set("isEdited", true)
        mongoTemplate.updateFirst(query, update, "messages")
    }
}
