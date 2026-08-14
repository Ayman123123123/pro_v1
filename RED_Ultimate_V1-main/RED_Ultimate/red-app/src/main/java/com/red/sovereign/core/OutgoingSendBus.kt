package com.red.sovereign.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * نتيجة إرسال محلي بعد التشفير — ليست API على الخادم.
 *
 * الواجهة تُظهر فقاعة مؤقتة بـ [clientId]. الخدمة تنشر نجاحاً أو فشلاً
 * حتى لا يُمسح النص بصمت عندما يفشل `NO_APPROVED_REMOTE_DEVICE`.
 */
data class OutgoingSendEvent(
    val conversationId: String,
    val clientId: String,
    val success: Boolean,
    val serverId: String? = null,
    val error: String? = null,
)

object OutgoingSendBus {
    private val eventsInternal = MutableSharedFlow<OutgoingSendEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = eventsInternal.asSharedFlow()
    fun publish(event: OutgoingSendEvent) {
        eventsInternal.tryEmit(event)
    }
}
