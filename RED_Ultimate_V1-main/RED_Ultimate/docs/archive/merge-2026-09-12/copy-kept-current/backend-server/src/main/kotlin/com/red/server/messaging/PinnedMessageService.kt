package com.red.server.messaging

import com.red.server.database.GroupMessageDocument
import com.red.server.database.MessageDocument
import com.red.server.groups.GroupService
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * 📌 خدمة تثبيت الرسائل — V26
 * تدعم تثبيت 5 رسائل للخاصة و 10 للمجموعة و 20 للقناة
 * تخزن في PostgreSQL (pinned_messages) ومرآة سريعة في MongoDB (messages.isPinned)
 */
@Service
class PinnedMessageService(
    private val mongo: MongoTemplate,
    private val jdbc: JdbcTemplate,
    private val groups: GroupService
) {
    companion object {
        const val MAX_PINNED_PRIVATE = 5
        const val MAX_PINNED_GROUP = 10
        const val MAX_PINNED_CHANNEL = 20
    }

    data class PinResponse(
        val id: String,
        val messageUuid: String,
        val conversationId: String?,
        val groupId: String?,
        val channelId: String?,
        val pinnedBy: String,
        val pinnedAt: Instant,
        val expiresAt: Instant?
    )

    fun pin(
        actorId: UUID,
        actorRedId: String,
        messageUuid: String,
        conversationId: String? = null,
        groupId: String? = null,
        channelId: String? = null,
        expiresAt: Instant? = null
    ): PinResponse {
        validateScope(conversationId, groupId, channelId)
        val type = when {
            conversationId != null -> "PRIVATE"
            groupId != null -> "GROUP"
            else -> "CHANNEL"
        }
        // 🔐 C3: التثبيت في مجموعة يتطلب عضوية فعلية — لا تثبيت من غير الأعضاء
        if (groupId != null) {
            require(groups.roleFor(actorId, groupId) != null) { "Only group members may pin messages" }
        }
        // تحقق من وجود الرسالة وصلاحية التثبيت
        verifyMessageExists(messageUuid, conversationId, groupId, channelId, actorRedId)
        checkLimit(conversationId, groupId, channelId)

        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO pinned_messages(id, conversation_id, group_id, channel_id, message_uuid, message_type, pinned_by, expires_at)
               VALUES (?,?,?,?,?,?,?,?)
               ON CONFLICT DO NOTHING""",
            id, conversationId, groupId?.let { UUID.fromString(it) }, channelId?.let { UUID.fromString(it) },
            messageUuid, type, actorId, expiresAt
        )
        // مرآة سريعة في MongoDB
        setPinnedInMongo(messageUuid, conversationId, groupId, channelId, actorRedId, true)

        return PinResponse(id.toString(), messageUuid, conversationId, groupId, channelId, actorRedId, Instant.now(), expiresAt)
    }

    fun unpin(actorId: UUID, messageUuid: String): Boolean {
        val deleted = jdbc.update("DELETE FROM pinned_messages WHERE message_uuid=? AND pinned_by=?", messageUuid, actorId) > 0
        if (deleted) {
            // إزالة المرآة من Mongo
            mongo.updateFirst(
                Query(Criteria.where("uuid").`is`(messageUuid)),
                Update().set("isPinned", false).unset("pinnedAt").unset("pinnedBy"),
                MessageDocument::class.java
            )
            mongo.updateFirst(
                Query(Criteria.where("uuid").`is`(messageUuid)),
                Update().set("isPinned", false).unset("pinnedAt").unset("pinnedBy"),
                GroupMessageDocument::class.java
            )
        }
        return deleted
    }

    fun listForConversation(conversationId: String): List<PinResponse> {
        return jdbc.query(
            "SELECT * FROM pinned_messages WHERE conversation_id=? AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY pinned_at DESC",
            { rs, _ ->
                PinResponse(
                    rs.getObject("id", UUID::class.java).toString(),
                    rs.getString("message_uuid"),
                    rs.getString("conversation_id"),
                    rs.getObject("group_id", UUID::class.java)?.toString(),
                    rs.getObject("channel_id", UUID::class.java)?.toString(),
                    rs.getObject("pinned_by", UUID::class.java).toString(),
                    rs.getTimestamp("pinned_at").toInstant(),
                    rs.getTimestamp("expires_at")?.toInstant()
                )
            },
            conversationId
        )
    }

    fun listForGroup(actorId: UUID, groupId: String): List<PinResponse> {
        // 🔐 C3: سرد مثبتات المجموعة للأعضاء فقط
        require(groups.roleFor(actorId, groupId) != null) { "Only group members may view pinned messages" }
        return jdbc.query(
            "SELECT * FROM pinned_messages WHERE group_id=? AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY pinned_at DESC",
            { rs, _ ->
                PinResponse(
                    rs.getObject("id", UUID::class.java).toString(),
                    rs.getString("message_uuid"),
                    rs.getString("conversation_id"),
                    rs.getObject("group_id", UUID::class.java)?.toString(),
                    rs.getObject("channel_id", UUID::class.java)?.toString(),
                    rs.getObject("pinned_by", UUID::class.java).toString(),
                    rs.getTimestamp("pinned_at").toInstant(),
                    rs.getTimestamp("expires_at")?.toInstant()
                )
            },
            UUID.fromString(groupId)
        )
    }

    fun cleanupExpired(): Int {
        return jdbc.update("DELETE FROM pinned_messages WHERE expires_at IS NOT NULL AND expires_at < NOW()")
    }

    private fun validateScope(conversationId: String?, groupId: String?, channelId: String?) {
        val filled = listOfNotNull(conversationId, groupId, channelId).size
        require(filled == 1) { "يجب تحديد نطاق واحد فقط: conversation أو group أو channel" }
    }

    private fun verifyMessageExists(messageUuid: String, conversationId: String?, groupId: String?, channelId: String?, actorRedId: String) {
        // 🔧 C2: رسائل المجموعات تُخزَّن فعلياً في messages — مجموعة group_messages مرآة
        // لم تُفعّل قط، والتحقق منها كان يفشل دائماً فيمنع تثبيت أي رسالة مجموعة.
        val doc = mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), MessageDocument::class.java)
        require(doc != null) { "الرسالة غير موجودة" }
        // 🔐 الرسالة المثبتة يجب أن تنتمي فعلاً للنطاق المطلوب — لا تثبيت رسائل غريبة
        if (groupId != null) require(doc.conversationId == groupId) { "الرسالة لا تنتمي لهذه المجموعة" }
        if (conversationId != null) require(doc.conversationId == conversationId) { "الرسالة لا تنتمي لهذه المحادثة" }
    }

    private fun checkLimit(conversationId: String?, groupId: String?, channelId: String?) {
        val count = when {
            conversationId != null -> jdbc.queryForObject("SELECT COUNT(*) FROM pinned_messages WHERE conversation_id=?", Int::class.java, conversationId) ?: 0
            groupId != null -> jdbc.queryForObject("SELECT COUNT(*) FROM pinned_messages WHERE group_id=?", Int::class.java, UUID.fromString(groupId)) ?: 0
            else -> jdbc.queryForObject("SELECT COUNT(*) FROM pinned_messages WHERE channel_id=?", Int::class.java, UUID.fromString(channelId)) ?: 0
        }
        val max = when {
            conversationId != null -> MAX_PINNED_PRIVATE
            groupId != null -> MAX_PINNED_GROUP
            else -> MAX_PINNED_CHANNEL
        }
        require(count < max) { "تم الوصول للحد الأقصى للتثبيت ($max)" }
    }

    private fun setPinnedInMongo(messageUuid: String, conversationId: String?, groupId: String?, channelId: String?, actorRedId: String, pinned: Boolean) {
        val update = Update().set("isPinned", pinned).set("pinnedAt", Instant.now()).set("pinnedBy", actorRedId)
        mongo.updateFirst(Query(Criteria.where("uuid").`is`(messageUuid)), update, MessageDocument::class.java)
        mongo.updateFirst(Query(Criteria.where("uuid").`is`(messageUuid)), update, GroupMessageDocument::class.java)
    }
}
