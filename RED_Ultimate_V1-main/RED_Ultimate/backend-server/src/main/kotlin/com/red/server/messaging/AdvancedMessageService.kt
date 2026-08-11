package com.red.server.messaging

import com.red.server.database.MessageDocument
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AdvancedMessageService(
    private val mongoTemplate: MongoTemplate,
    private val jdbc: JdbcTemplate? = null
) {
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
     * تعديل الرسالة مع حفظ السجل — V26
     * يحفظ الحمولة السابقة في message_edit_history (Postgres) و message_edit_history (Mongo)
     */
    fun editMessage(messageId: String, senderId: String, newContent: ByteArray) {
        val query = Query(Criteria.where("uuid").`is`(messageId).and("senderId").`is`(senderId))
        val existing = mongoTemplate.findOne(query, MessageDocument::class.java)
            ?: throw NoSuchElementException("Message not found or not owned by sender")

        // حد زمني 24 ساعة للتعديل (من V26 system_settings)
        val ageSeconds = Instant.now().epochSecond - existing.createdAt.epochSecond
        require(ageSeconds <= 86400) { "انتهت مهلة التعديل (24 ساعة)" }
        require(newContent.size in 1..1_048_576) { "حجم الحمولة غير صالح" }

        // حفظ السجل في Postgres — نحول RED ID إلى UUID عبر users
        try {
            val editorUuid = try {
                if (senderId.matches(Regex("^[0-9a-fA-F-]{36}$"))) UUID.fromString(senderId)
                else jdbc?.queryForObject("SELECT id FROM users WHERE red_id=?", UUID::class.java, senderId) ?: UUID.randomUUID()
            } catch (_: Exception) { UUID.randomUUID() }
            jdbc?.update(
                """INSERT INTO message_edit_history(id, message_uuid, conversation_id, editor_id, previous_payload, edited_at, edit_version)
                   VALUES (?,?,?,?,?,NOW(),?)""",
                UUID.randomUUID(), messageId, existing.conversationId, editorUuid,
                existing.payload, existing.editVersion
            )
        } catch (_: Exception) { /* Postgres قد لا يكون متاح في اختبار وحدة */ }

        // حفظ السجل في Mongo
        try {
            mongoTemplate.save(
                com.red.server.database.MessageEditHistoryDocument(
                    id = UUID.randomUUID().toString(),
                    messageUuid = messageId,
                    conversationId = existing.conversationId,
                    editorId = senderId,
                    previousPayload = existing.payload,
                    editVersion = existing.editVersion
                )
            )
        } catch (_: Exception) {}

        val result = mongoTemplate.updateFirst(
            query,
            Update()
                .set("payload", newContent)
                .set("editedAt", Instant.now())
                .set("editVersion", existing.editVersion + 1)
                .set("isEdited", true),
            "messages"
        )
        if (result.modifiedCount > 0) log.info("Message {} edited by sender {} (v{} -> v{})", messageId, senderId, existing.editVersion, existing.editVersion + 1)
    }

    /**
     * تنظيف الرسائل ذاتية الاختفاء — يعمل كل 5 دقائق
     */
    @Scheduled(fixedDelay = 300_000)
    fun cleanupDisappearing() {
        val now = Instant.now()
        // Mongo: احذف الرسائل التي انتهى وقتها
        val expired = mongoTemplate.find(
            Query(Criteria.where("disappearAt").lte(now)),
            MessageDocument::class.java
        )
        if (expired.isNotEmpty()) {
            expired.forEach { msg ->
                mongoTemplate.remove(Query(Criteria.where("uuid").`is`(msg.uuid)), MessageDocument::class.java)
            }
            log.info("Cleaned {} disappearing messages", expired.size)
        }
        // Postgres: نظف التثبيتات المنتهية
        try {
            jdbc?.update("DELETE FROM pinned_messages WHERE expires_at IS NOT NULL AND expires_at < NOW()")
        } catch (_: Exception) {}
    }
}
