package com.red.server.calls

import com.red.server.websocket.CallWebSocketHandler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CallRingExpiryJob(
    private val history: CallHistoryService,
    private val callWebSocketHandler: CallWebSocketHandler
) {
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    fun expireUnanswered() {
        history.expireStaleRinging()
        callWebSocketHandler.cleanupStaleGroups()
    }
}
