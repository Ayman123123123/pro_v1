package com.red.sovereign.crypto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DecryptedMessage(
    val id: String,
    val conversationId: String,
    val senderRedId: String,
    val plaintext: ByteArray,
    val timestamp: Long,
    val sequence: Long,
    val type: String = "TEXT",
    val outgoing: Boolean = false,
    val status: String = "SENT"
)

object DecryptedMessageBus {
    private val mutable = MutableSharedFlow<DecryptedMessage>(extraBufferCapacity = 128)
    val messages = mutable.asSharedFlow()
    fun publish(message: DecryptedMessage) { mutable.tryEmit(message) }
}

/**
 * Phase-1 (2026-09-14): تحديث رسالة معروضة مسبقاً (استبدال لا إضافة) —
 * يُستخدم عند نجاح فك تشفير عنصر نائب: الواجهة تستبدل العنصر بنفس المعرف.
 */
object DecryptedMessageUpdateBus {
    private val mutable = MutableSharedFlow<DecryptedMessage>(extraBufferCapacity = 64)
    val updates = mutable.asSharedFlow()
    fun publish(message: DecryptedMessage) { mutable.tryEmit(message) }
}
