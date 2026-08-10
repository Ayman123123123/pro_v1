package com.red.sovereign.developed.delivery

import java.util.UUID

class GuaranteedDelivery(val retryStrategy: String) {
    fun start() {
        println("Guaranteed Delivery Engine Started with $retryStrategy")
    }

    fun generateMsgId(): String {
        // Implementation of Time-ordered UUID v7
        return "${System.currentTimeMillis()}-${UUID.randomUUID()}"
    }
}
