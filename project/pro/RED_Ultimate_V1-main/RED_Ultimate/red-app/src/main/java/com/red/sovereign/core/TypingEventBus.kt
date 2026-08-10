package com.red.sovereign.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TypingEvent(val conversationId: String, val userId: String, val isTyping: Boolean)

object TypingEventBus {
    private val _events = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()
    
    fun publish(event: TypingEvent) {
        _events.tryEmit(event)
    }
}
