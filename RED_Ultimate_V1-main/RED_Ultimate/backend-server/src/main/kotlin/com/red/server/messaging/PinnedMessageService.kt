package com.red.server.messaging

import com.red.server.database.MessageDocument
import com.red.server.database.GroupMessageDocument
import com.red.server.database.ChannelMemberDocument
import com.red.server.database.ChannelMessageDocument
import com.red.server.groups.GroupMember
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
    private val jdbc: JdbcTemplate
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
        // ترتبط الرسالة بالنطاق المطلوب، ويملك صاحب الطلب صلاحية التثبيت ضمنه.
        verifyMessageScopeAndPermission(actorId, messageUuid, conversationId, groupId, channelId)
        existingPin(messageUuid, conversationId, groupId, channelId)?.let { return it }
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
        setPinnedInMongo(messageUuid, conversationId, groupId, channelId, actorId.toString(), true)

        return PinResponse(id.toString(), messageUuid, conversationId, groupId, channelId, actorId.toString(), Instant.now(), expiresAt)
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

    fun listForGroup(groupId: String): List<PinResponse> {
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

    private fun existingPin(messageUuid: String, conversationId: String?, groupId: String?, channelId: String?): PinResponse? {
        val sql = when {
            conversationId != null -> "SELECT * FROM pinned_messages WHERE message_uuid=? AND conversation_id=? LIMIT 1"
            groupId != null -> "SELECT * FROM pinned_messages WHERE message_uuid=? AND group_id=? LIMIT 1"
            else -> "SELECT * FROM pinned_messages WHERE message_uuid=? AND channel_id=? LIMIT 1"
        }
        val scope = groupId?.let(UUID::fromString) ?: channelId?.let(UUID::fromString) ?: conversationId
        return jdbc.query(sql, { rs, _ ->
            PinResponse(
                rs.getObject("id", UUID::class.java).toString(), rs.getString("message_uuid"),
                rs.getString("conversation_id"), rs.getObject("group_id", UUID::class.java)?.toString(),
                rs.getObject("channel_id", UUID::class.java)?.toString(), rs.getObject("pinned_by", UUID::class.java).toString(),
                rs.getTimestamp("pinned_at").toInstant(), rs.getTimestamp("expires_at")?.toInstant()
            )
        }, messageUuid, scope).firstOrNull()
    }

    private fun validateScope(conversationId: String?, groupId: String?, channelId: String?) {
        val filled = listOfNotNull(conversationId, groupId, channelId).size
        require(filled == 1) { "يجب تحديد نطاق واحد فقط: conversation أو group أو channel" }
    }

    private fun verifyMessageScopeAndPermission(actorId: UUID, messageUuid: String, conversationId: String?, groupId: String?, channelId: String?) {
        val actor = actorId.toString()
        when {
            conversationId != null -> {
                val message = mongo.findOne(
                    Query(Criteria.where("uuid").`is`(messageUuid).and("conversationId").`is`(conversationId)),
                    MessageDocument::class.java
                ) ?: throw NoSuchElementException("الرسالة غير موجودة في هذه المحادثة")
                require(message.senderId == actor || message.receiverId == actor) { "لا تملك صلاحية تثبيت رسالة هذه المحادثة" }
            }
            groupId != null -> {
                mongo.findOne(
                    Query(Criteria.where("uuid").`is`(messageUuid).and("groupId").`is`(groupId)),
                    GroupMessageDocument::class.java
                ) ?: throw NoSuchElementException("الرسالة غير موجودة في هذه المجموعة")
                val membership = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$actor")), GroupMember::class.java)
                    ?: throw IllegalArgumentException("أنت لست عضوًا في هذه المجموعة")
                require(membership.role.name in setOf("OWNER", "ADMIN")) { "فقط مالك المجموعة أو مشرفها يستطيع تثبيت الرسائل" }
            }
            else -> {
                mongo.findOne(
                    Query(Criteria.where("uuid").`is`(messageUuid).and("channelId").`is`(channelId)),
                    ChannelMessageDocument::class.java
                ) ?: throw NoSuchElementException("الرسالة غير موجودة في هذه القناة")
                val membership = mongo.findOne(
                    Query(Criteria.where("channelId").`is`(channelId).and("userId").`is`(actor)),
                    ChannelMemberDocument::class.java
                ) ?: throw IllegalArgumentException("أنت لست عضوًا في هذه القناة")
                require(membership.role in setOf("OWNER", "ADMIN")) { "فقط مالك القناة أو مشرفها يستطيع تثبيت الرسائل" }
            }
        }
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
