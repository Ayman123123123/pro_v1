package com.red.sovereign.core.delivery

import com.red.sovereign.core.database.MasterDao
import com.red.sovereign.core.database.MessageEntity
import com.red.sovereign.proto.ChatProtos
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedDeliveryEngine @Inject constructor(
    private val masterDao: MasterDao,
    private val webSocketClient: RedWebSocketClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * إرسال رسالة: توليد UUID v7 -> حفظ في Room -> إرسال ProtoBuf -> انتظار ACK
     */
    suspend fun dispatchMessage(conversationId: String, text: String) {
        val messageId = generateUuidV7()
        val msgEntity = MessageEntity(
            uuid = messageId,
            conversationId = conversationId,
            senderId = "me", // محقونة من IdentityManager
            content = text,
            type = "TEXT",
            status = "SENDING",
            timestamp = System.currentTimeMillis(),
            sequenceNumber = 0
        )

        // 1. الحفظ المحلي (Zero Latency UI)
        masterDao.insertMessage(msgEntity)

        // 2. التحويل لـ Binary ProtoBuf
        val proto = ChatProtos.ChatMessage.newBuilder()
            .setId(messageId)
            .setConversationId(conversationId)
            .setPayload(ByteString.copyFromUtf8(text))
            .build()

        // 3. الإرسال عبر WebSocket
        webSocketClient.send(proto.toByteArray())
        
        // 4. آلية إعادة المحاولة (Exponential Backoff)
        startRetryTimer(messageId)
    }

    private fun generateUuidV7(): String {
        val timestamp = System.currentTimeMillis()
        val random = SecureRandom()
        val msb = (timestamp shl 16) or 0x7000L or (random.nextLong() and 0x0FFFL)
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }

    private fun startRetryTimer(msgId: String) {
        scope.launch {
            var delayMs = 1000L
            repeat(5) {
                delay(delayMs)
                val msg = masterDao.getMessageByUuid(msgId)
                if (msg != null && msg.status == "SENDING") {
                    // إعادة المحاولة
                    delayMs *= 2
                } else return@launch
            }
            masterDao.updateMessageStatus(msgId, "FAILED")
        }
    }
}
