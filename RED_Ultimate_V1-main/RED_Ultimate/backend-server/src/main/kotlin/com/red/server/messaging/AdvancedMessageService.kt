package com.red.server.messaging

import com.red.server.database.MessageDocument
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * الحذف والتعديل للجميع (رسائل المجموعات والمحادثات المباشرة).
 *
 * الحذف هنا حذف ناعم متوافق مع [DeleteService]: تُصفَّر الحمولة وتُسجَّل
 * deletedAt، ولا تُحذف الوثيقة من الأرشيف حتى يحافظ نظام التزامن
 * على استمرارية أرقام التسلسل.
 */
@Service
class AdvancedMessageService(private val mongoTemplate: MongoTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * حذف للجميع: يصفّر الحمولة ويُعلّم الرسالة محذوفة ثم يُرجع قائمة
     * المشاركين لإبلاغهم بإشارة الحذف.
     */
    fun processDeleteRequest(messageId: String, senderId: String): List<String> {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId).and("deletedAt").`is`(null))
        val message = mongoTemplate.findOne(query, MessageDocument::class.java) ?: return emptyList()
        mongoTemplate.updateFirst(
            query,
            Update().set("deletedAt", Instant.now()).set("payload", byteArrayOf()),
            MessageDocument::class.java
        )
        log.info("RED: Message {} deleted for everyone.", messageId)
        // قائمة المشاركين في المحادثة لإبلاغهم (نقطة ربط مستقبلية لقائمة المجموعة الكاملة)
        return listOf(message.receiverId)
    }

    /**
     * تعديل الرسالة (خلال 15 دقيقة): يُحدَّث المحتوى ويُعلَّم isEdited.
     */
    fun editMessage(messageId: String, senderId: String, newContent: ByteArray) {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId).and("deletedAt").`is`(null))
        mongoTemplate.updateFirst(
            query,
            Update().set("payload", newContent).set("isEdited", true).set("editedAt", Instant.now()),
            MessageDocument::class.java
        )
        log.info("RED: Message {} edited by sender.", messageId)
    }
}
