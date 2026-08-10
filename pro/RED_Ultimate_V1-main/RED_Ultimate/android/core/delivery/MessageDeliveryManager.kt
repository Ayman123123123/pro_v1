package com.red.core.delivery

import com.red.core.database.MasterDao
import com.red.core.database.MessageEntity
import com.red.proto.ChatProtos
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import java.util.UUID

class MessageDeliveryManager(
    private val masterDao: MasterDao,
    private val webSocketClient: DevelopedWebSocketClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun sendMessage(conversationId: String, content: String, type: String) {
        val uuid = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val message = MessageEntity(
            uuid = uuid,
            conversationId = conversationId,
            senderId = "me",
            type = type,
            content = content,
            status = "SENDING",
            timestamp = System.currentTimeMillis(),
            sequenceNumber = 0
        )

        masterDao.insertMessage(message)

        val protoMsg = ChatProtos.ChatMessage.newBuilder()
            .setId(uuid)
            .setConversationId(conversationId)
            .setPayload(ByteString.copyFromUtf8(content))
            .setTimestamp(message.timestamp)
            .build()

        webSocketClient.send(protoMsg.toByteArray())
    }

    fun onAckReceived(messageId: String, status: String, sequenceNumber: Long) {
        scope.launch {
            // Update local database with server-assigned sequence and new status
            masterDao.updateMessageStatus(messageId, status)
            // If we have sequence number, we use it for guaranteed ordering
        }
    }
}

interface DevelopedWebSocketClient {
    fun send(data: ByteArray)
}
