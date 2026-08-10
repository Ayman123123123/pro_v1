package com.red.server.messaging

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ConversationSequence
import com.red.server.database.MessageDocument
import com.red.server.groups.GroupMember
import com.red.sovereign.proto.RedProtos
import jakarta.annotation.PostConstruct
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class MessageService(
    private val mongo: MongoTemplate,
    private val redis: RedisTemplate<String, String>,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate
) {
    @PostConstruct
    fun indexes() {
        mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("uuid", Sort.Direction.ASC).unique())
        mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("receiverId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC))
        mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("conversationId", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC))
    }

    fun processIncoming(message: RedProtos.ChatMessage): MessageDocument {
        validate(message)
        enforceNotBlocked(message.senderId, message.receiverId)
        if (message.type in GROUP_TYPES) enforceGroupMembership(message)
        mongo.findOne(Query(Criteria.where("uuid").`is`(message.id)), MessageDocument::class.java)?.let { existing ->
            require(existing.senderId == message.senderId && existing.receiverId == message.receiverId && existing.conversationId == message.conversationId &&
                existing.senderDeviceId == message.senderDeviceId && existing.receiverDeviceId == message.receiverDeviceId) {
                "Message UUID collision"
            }
            return existing
        }

        val stored = MessageDocument(
            uuid = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            payload = message.payload.toByteArray(),
            messageType = message.type.ifBlank { "TEXT" },
            senderDeviceId = message.senderDeviceId,
            receiverDeviceId = message.receiverDeviceId,
            ciphertextType = message.ciphertextType,
            sequenceNumber = nextSequence(message.conversationId),
            status = "SENT"
        )
        val saved = try { mongo.save(stored) } catch (_: DuplicateKeyException) {
            mongo.findOne(Query(Criteria.where("uuid").`is`(message.id)), MessageDocument::class.java)
                ?: throw IllegalStateException("Message deduplication failed")
        }
        redis.opsForZSet().add("red:presence:index", message.senderId, System.currentTimeMillis().toDouble())
        redis.convertAndSend("red:messages:${message.receiverId}", saved.uuid)
        return saved
    }

    fun pendingFor(receiverId: String, receiverDeviceId: Int, limit: Int = 500): List<MessageDocument> = mongo.find(
        Query(Criteria.where("receiverId").`is`(receiverId).and("receiverDeviceId").`is`(receiverDeviceId).and("status").`is`("SENT").and("deletedAt").`is`(null))
            .with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(limit.coerceIn(1, 500)),
        MessageDocument::class.java
    )

    fun getMissedMessages(userId: String, conversationId: String, fromSequence: Long, toSequence: Long, limit: Int = 500): List<MessageDocument> {
        val criteria = Criteria.where("conversationId").`is`(conversationId)
            .andOperator(Criteria().orOperator(Criteria.where("senderId").`is`(userId), Criteria.where("receiverId").`is`(userId)))
            .and("sequenceNumber").gte(fromSequence.coerceAtLeast(0))
            .and("deletedAt").`is`(null)
        if (toSequence > 0) criteria.and("sequenceNumber").lte(toSequence)
        return mongo.find(Query(criteria).with(Sort.by(Sort.Direction.ASC, "sequenceNumber")).limit(limit.coerceIn(1, 500)), MessageDocument::class.java)
    }

    /** Only the intended receiver may advance SENT -> DELIVERED -> READ. */
    fun acknowledge(receiverId: String, receiverDeviceId: Int, messageId: String, requestedStatus: String): MessageDocument {
        val status = requestedStatus.uppercase()
        require(status == "DELIVERED" || status == "READ") { "Unsupported ACK status" }
        val message = mongo.findOne(Query(Criteria.where("uuid").`is`(messageId)), MessageDocument::class.java)
            ?: throw NoSuchElementException("Message not found")
        require(message.receiverId == receiverId && message.receiverDeviceId == receiverDeviceId) { "Only the target device can acknowledge this message" }
        if (rank(status) > rank(message.status)) {
            message.status = status
            if (status == "DELIVERED" && message.deliveredAt == null) message.deliveredAt = Instant.now()
            if (status == "READ") { if (message.deliveredAt == null) message.deliveredAt = Instant.now(); message.readAt = Instant.now() }
            mongo.save(message)
        }
        return message
    }

    fun findAuthorized(messageId: String, userId: String): MessageDocument? = mongo.findOne(
        Query(Criteria.where("uuid").`is`(messageId).orOperator(Criteria.where("senderId").`is`(userId), Criteria.where("receiverId").`is`(userId))),
        MessageDocument::class.java
    )

    private fun enforceNotBlocked(senderRedId: String, receiverRedId: String) {
        val sender = users.findByRedId(senderRedId) ?: throw NoSuchElementException("Sender identity not found")
        val receiver = users.findByRedId(receiverRedId) ?: throw NoSuchElementException("Receiver identity not found")
        val blocked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
            Int::class.java,
            sender.id, receiver.id, receiver.id, sender.id
        ) ?: 0
        require(blocked == 0) { "Messaging is not allowed between these identities" }
    }

    private fun enforceGroupMembership(message: RedProtos.ChatMessage) {
        val sender = users.findByRedId(message.senderId) ?: throw NoSuchElementException("Sender identity not found")
        val receiver = users.findByRedId(message.receiverId) ?: throw NoSuchElementException("Receiver identity not found")
        require(mongo.exists(Query(Criteria.where("id").`is`("${message.conversationId}:${sender.id}")), GroupMember::class.java)) { "Sender is not a group member" }
        require(mongo.exists(Query(Criteria.where("id").`is`("${message.conversationId}:${receiver.id}")), GroupMember::class.java)) { "Receiver is not a group member" }
    }

    private fun nextSequence(conversationId: String): Long {
        val sequence = mongo.findAndModify(
            Query(Criteria.where("id").`is`(conversationId)), Update().inc("sequence", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true), ConversationSequence::class.java
        ) ?: error("Unable to allocate conversation sequence")
        return sequence.sequence
    }

    private fun validate(message: RedProtos.ChatMessage) {
        val id = runCatching { UUID.fromString(message.id) }.getOrElse { throw IllegalArgumentException("Message ID must be UUID v7") }
        require(id.version() == 7) { "Message ID must be UUID v7" }
        require(message.senderId.matches(RED_ID)) { "Invalid sender YOUNES ID" }
        require(message.receiverId.matches(RED_ID) && message.receiverId != message.senderId) { "Invalid receiver YOUNES ID" }
        require(message.conversationId.length in 8..128) { "Invalid conversation ID" }
        require(message.senderDeviceId in 1..127 && message.receiverDeviceId in 1..127) { "Invalid protocol device ID" }
        val allowedCiphertext = if (message.type == "GROUP_MESSAGE") message.ciphertextType == 4 else message.ciphertextType == 2 || message.ciphertextType == 3
        require(allowedCiphertext) { "Unsupported libsignal ciphertext type for ${message.type}" }
        // 🔐 E2EE Hardening: Group messages must reference existing group and members only
        if (message.type in GROUP_TYPES) {
            require(message.conversationId.length in 8..128) { "Group conversation ID must be valid group" }
        }
        require(message.payload.size() in 1..1_048_576) { "Encrypted envelope must contain 1 byte to 1 MiB" }
        require(message.type.ifBlank { "TEXT" } in TYPES) { "Unsupported message type" }
    }

    private fun rank(status: String) = when (status) { "SENT" -> 1; "DELIVERED" -> 2; "READ" -> 3; else -> 0 }

    companion object {
        private val RED_ID = Regex("^(RED|YNS)-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$")
        private val TYPES = setOf("TEXT", "RICH_TEXT", "IMAGE", "VIDEO", "AUDIO", "VOICE", "FILE", "SYSTEM", "GROUP_KEY_DISTRIBUTION", "GROUP_MESSAGE")
        private val GROUP_TYPES = setOf("GROUP_KEY_DISTRIBUTION", "GROUP_MESSAGE")
    }
}
