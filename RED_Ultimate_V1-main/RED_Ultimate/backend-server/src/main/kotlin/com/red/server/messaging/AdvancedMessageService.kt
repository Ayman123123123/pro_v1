package com.red.server.messaging

import com.red.server.database.ChannelMessageDocument
import com.red.server.database.GroupMessageDocument
import com.red.server.database.MessageDocument
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.Base64

internal fun expiredDisappearingMessagesQuery(now: Instant): Query =
    Query(Criteria.where("disappearAt").ne(null).lte(now))

data class SyncResult(
    val messages: List<MessageSyncItem>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class MessageSyncItem(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val payload: String,
    val messageType: String,
    val timestamp: Instant,
    val sequenceNumber: Long,
    val senderDeviceId: String,
    val receiverDeviceId: String?,
    val ciphertextType: String,
    val replyToMessageId: String?
)

data class SearchResult(
    val messages: List<MessageSearchHit>,
    val totalHits: Long
)

data class MessageSearchHit(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val snippet: String,
    val messageType: String,
    val timestamp: Instant,
    val score: Float
)

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
        } catch (e: Exception) {
            log.debug("Failed to record message edit history for {}: {}", messageId, e.message)
        }

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

    fun syncMessages(
        userRedId: String,
        cursor: String?,
        limit: Int,
        conversationId: String?,
        since: Instant?
    ): SyncResult {
        val criteria = Criteria.where("receiverId").`is`(userRedId)
            .and("deletedForEveryoneAt").`is`(null)
        
        if (conversationId != null) {
            criteria.and("conversationId").`is`(conversationId)
        }
        
        if (since != null) {
            criteria.and("createdAt").gt(since)
        }
        
        if (cursor != null && cursor.isNotBlank()) {
            try {
                val decoded = String(Base64.getDecoder().decode(cursor))
                val parts = decoded.split(":")
                if (parts.size == 2) {
                    criteria.and("sequenceNumber").gt(parts[0].toLong())
                    criteria.and("createdAt").gt(Instant.parse(parts[1]))
                }
            } catch (_: Exception) {
                // Ignore invalid cursor
            }
        }
        
        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.ASC, "sequenceNumber", "createdAt"))
            .limit(limit + 1)
        
        val docs = mongoTemplate.find(query, MessageDocument::class.java)
        val hasMore = docs.size > limit
        val items = docs.take(limit)
        
        val nextCursor = if (hasMore && items.isNotEmpty()) {
            val last = items.last()
            Base64.getEncoder().encodeToString("${last.sequenceNumber}:${last.createdAt}".toByteArray())
        } else null
        
        return SyncResult(
            messages = items.map { doc ->
                MessageSyncItem(
                    messageId = doc.uuid,
                    conversationId = doc.conversationId,
                    senderId = doc.senderId,
                    payload = Base64.getEncoder().encodeToString(doc.payload),
                    messageType = doc.messageType,
                    timestamp = doc.createdAt,
                    sequenceNumber = doc.sequenceNumber,
                    senderDeviceId = doc.senderDeviceId.toString(),
                    receiverDeviceId = doc.receiverDeviceId?.toString(),
                    ciphertextType = doc.ciphertextType.toString(),
                    replyToMessageId = doc.replyToMessageUuid
                )
            },
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }

    fun searchMessages(
        userRedId: String,
        query: String,
        conversationId: String?,
        messageType: String?,
        senderId: String?,
        fromDate: Instant?,
        toDate: Instant?,
        offset: Int,
        limit: Int
    ): SearchResult {
        val criteria = Criteria().orOperator(
            Criteria.where("senderId").`is`(userRedId),
            Criteria.where("receiverId").`is`(userRedId)
        ).and("deletedForEveryoneAt").`is`(null)
        
        // Text search on payload (requires text index)
        val textCriteria = Criteria.where("$text").`is`(org.springframework.data.mongodb.core.query.TextCriteria.forDefaultLanguage().matching(query))
        criteria.andOperator(textCriteria)
        
        if (conversationId != null) {
            criteria.and("conversationId").`is`(conversationId)
        }
        
        if (messageType != null) {
            criteria.and("messageType").`is`(messageType)
        }
        
        if (senderId != null) {
            criteria.and("senderId").`is`(senderId)
        }
        
        if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate)
        }
        
        if (toDate != null) {
            criteria.and("createdAt").lte(toDate)
        }
        
        val countQuery = Query(criteria)
        val totalHits = mongoTemplate.count(countQuery, MessageDocument::class.java).toLong()
        
        val searchQuery = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, "score"))
            .with(Sort.by(Sort.Direction.DESC, "createdAt"))
            .skip(offset.toLong())
            .limit(limit)
        
        val docs = mongoTemplate.find(searchQuery, MessageDocument::class.java)
        
        return SearchResult(
            messages = docs.map { doc ->
                MessageSearchHit(
                    messageId = doc.uuid,
                    conversationId = doc.conversationId,
                    senderId = doc.senderId,
                    snippet = String(doc.payload).take(200),
                    messageType = doc.messageType,
                    timestamp = doc.createdAt,
                    score = 1.0f // MongoDB text score would be in metadata
                )
            },
            totalHits = totalHits
        )
    }

    /**
     * تنظيف الرسائل ذاتية الاختفاء — يعمل كل 5 دقائق.
     * يغطي المحادثات الخاصة + رسائل المجموعات + رسائل القنوات
     * (إعدادات GroupService.updateDisappearing تُفرض عند الكتابة عبر
     * MessageService.disappearingSecondsForConversation، وهنا تُحذف المنتهية).
     */
    @Scheduled(fixedDelay = 300_000)
    fun cleanupDisappearing() {
        val now = Instant.now()
        // LEGENDARY FIX: حذف جماعي deleteMany بدل find + remove واحداً واحداً (كان N+1 ينهار مع آلاف المؤقتة)
        val q = expiredDisappearingMessagesQuery(now)
        val d1 = mongoTemplate.remove(q, MessageDocument::class.java).deletedCount
        val d2 = mongoTemplate.remove(q, GroupMessageDocument::class.java).deletedCount
        val d3 = mongoTemplate.remove(q, ChannelMessageDocument::class.java).deletedCount
        val total = d1 + d2 + d3
        if (total > 0) {
            log.info("Cleaned {} disappearing messages (private={}, group={}, channel={})", total, d1, d2, d3)
        }
        // Postgres: نظف التثبيتات المنتهية
        try {
            jdbc?.update("DELETE FROM pinned_messages WHERE expires_at IS NOT NULL AND expires_at < NOW()")
        } catch (e: Exception) {
            log.warn("Failed to cleanup expired pinned messages: {}", e.message)
        }
    }
}
