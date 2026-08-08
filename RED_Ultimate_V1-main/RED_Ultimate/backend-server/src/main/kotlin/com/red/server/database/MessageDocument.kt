package com.red.server.database

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("messages")
data class MessageDocument(
    @Id val id: String? = null,
    @Indexed(unique = true) val uuid: String,
    @Indexed val conversationId: String,
    @Indexed val senderId: String,
    @Indexed val receiverId: String,
    var payload: ByteArray,
    val messageType: String = "TEXT",
    val senderDeviceId: Int,
    val receiverDeviceId: Int,
    val ciphertextType: Int,
    val sequenceNumber: Long = 0,
    @Indexed var status: String = "SENT",
    val createdAt: Instant = Instant.now(),
    var deliveredAt: Instant? = null,
    var readAt: Instant? = null,
    var deletedAt: Instant? = null,
    var isEdited: Boolean = false,
    var editedAt: Instant? = null
)

@Document("conversation_sequences")
data class ConversationSequence(@Id val conversationId: String, var sequence: Long = 0)
