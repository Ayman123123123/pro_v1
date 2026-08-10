package com.red.sovereign.core.delivery

import com.red.sovereign.core.database.MasterDao
import com.red.sovereign.proto.ChatProtos
import com.red.sovereign.core.network.RedWebSocketClient
import kotlinx.coroutines.*
import javax.inject.Inject

class SyncManager @Inject constructor(
    private val masterDao: MasterDao,
    private val webSocketClient: RedWebSocketClient
) {
    private var lastKnownSequence: Long = 0

    /**
     * فحص الفجوات: إذا وصل الرقم 10 وكان لدينا 7، اطلب 8 و 9
     */
    suspend fun checkAndSync(receivedSequence: Long, conversationId: String) {
        if (receivedSequence > lastKnownSequence + 1) {
            val request = ChatProtos.SyncRequest.newBuilder()
                .setConversationId(conversationId)
                .setFromSequence(lastKnownSequence + 1)
                .setToSequence(receivedSequence - 1)
                .build()
            webSocketClient.send(request.toByteArray())
        }
        lastKnownSequence = receivedSequence
    }
}
