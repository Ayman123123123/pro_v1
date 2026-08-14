package com.red.server.calls

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CallRingExpiryJob(private val history: CallHistoryService) {
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    fun expireUnanswered() {
        history.expireStaleRinging()
    }
}
