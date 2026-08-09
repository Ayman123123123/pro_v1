package com.red.sovereign.crypto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MessageAck(val messageId: String, val status: String)

object MessageAckBus {
    private val mutable = MutableSharedFlow<MessageAck>(extraBufferCapacity = 64)
    val acks = mutable.asSharedFlow()
    fun publish(ack: MessageAck) { mutable.tryEmit(ack) }
}
