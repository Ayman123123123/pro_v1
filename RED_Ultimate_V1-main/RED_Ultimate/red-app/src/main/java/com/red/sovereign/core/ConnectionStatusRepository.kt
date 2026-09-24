package com.red.sovereign.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مصدر الحقيقة الوحيد لحالة السيرفر أعلى التطبيق.
 * يُغذَّى فقط من RedConnectionService.onState — لا polling إضافي.
 * يوقف التكرار المزعج: الواجهة تعرض نقطة حمراء ثابتة بدل Toasts متكررة.
 */
object ConnectionStatusRepository {
    enum class ServerUiState { ONLINE, CONNECTING, OFFLINE }

    data class Status(
        val state: ServerUiState = ServerUiState.CONNECTING,
        val retryInSec: Long = 0,
        val lastChangeAt: Long = System.currentTimeMillis()
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * نشر مزيل للارتداد: نفس الحالة + نفس العد التنازلي لا تُعاد —
     * الواجهة ترسم نقطة/شريطاً ثابتاً أعلى التطبيق بدل Toasts متكررة.
     * الخنق: OFFLINE متكرر بنفس retryInSec يُحدَّث at-most مرة/ثانية.
     */
    @Volatile private var lastEmitAt = 0L
    fun publish(state: ServerUiState, retryInSec: Long = 0) {
        val cur = _status.value
        val now = System.currentTimeMillis()
        if (cur.state == state && cur.retryInSec == retryInSec) return
        if (cur.state == state && state == ServerUiState.OFFLINE && now - lastEmitAt < 1_000L) return
        lastEmitAt = now
        _status.value = Status(state, retryInSec.coerceAtLeast(0L), now)
    }

    val isOnline: Boolean get() = _status.value.state == ServerUiState.ONLINE
}
