package com.red.sovereign.crypto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phase-1 (2026-09-14): أخطاء الإرسال الصادرة (تشفير/دليل/شبكة) — تُبث للواجهة
 * لعرض تنبيه فوري بدل الصمت (كانت تضيع في notifyConnection فقط).
 */
data class SendError(
    val conversationId: String,
    val targetId: String?,
    val code: String,
    val arabicMessage: String
)

object MessageSendErrorBus {
    private val mutable = MutableSharedFlow<SendError>(extraBufferCapacity = 32)
    val errors = mutable.asSharedFlow()
    fun publish(error: SendError) { mutable.tryEmit(error) }
}
