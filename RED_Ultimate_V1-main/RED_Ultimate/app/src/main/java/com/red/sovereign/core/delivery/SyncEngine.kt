package com.red.sovereign.core.delivery

import com.red.sovereign.core.database.RedDao
import com.red.sovereign.proto.RedProtos
import com.red.sovereign.core.network.RedWebSocketClient
import javax.inject.Inject

class SyncEngine @Inject constructor(
    private val redDao: RedDao,
    private val webSocketClient: RedWebSocketClient
) {
    /**
     * فحص الفجوات: إذا وجدنا رسالة برقم 10 ونحن نملك 7، نطلب 8 و 9 فوراً.
     */
    suspend fun repairSequenceGaps(conversationId: String, currentMaxSeq: Long) {
        val localMax = redDao.getLastSequenceNumber(conversationId)
        if (currentMaxSeq > localMax + 1) {
            val request = RedProtos.RedRED.newBuilder().setSyncReq(
                RedProtos.SyncRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setFromSequence(localMax + 1)
                    .setToSequence(currentMaxSeq)
            ).build()
            webSocketClient.send(request.toByteArray())
        }
    }
}
