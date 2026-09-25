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
        val lastChangeAt: Long = System.currentTimeMillis(),
        /** مؤشر الحالة الحقيقية: هل القاطع مفتوح (cooldown) أم انقطاع عابر. */
        val isBreakerOpen: Boolean = false,
        /** آخر نجاح اتصال — تُحسب منه مدة الاستقرار في الواجهة. */
        val lastOnlineAtMs: Long = 0L
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * نشر مزيل للارتداد: نفس الحالة + نفس العد التنازلي لا تُعاد —
     * الواجهة ترسم نقطة/شريطاً ثابتاً أعلى التطبيق بدل Toasts متكررة.
     * الخنق: OFFLINE متكرر بنفس retryInSec يُحدَّث at-most مرة/ثانية.
     */
    @Volatile private var lastEmitAt = 0L
    @Volatile private var lastOnlineAtMs = 0L
    fun publish(state: ServerUiState, retryInSec: Long = 0, isBreakerOpen: Boolean = false) {
        val cur = _status.value
        val now = System.currentTimeMillis()
        if (state == ServerUiState.ONLINE) lastOnlineAtMs = now
        if (cur.state == state && cur.retryInSec == retryInSec && cur.isBreakerOpen == isBreakerOpen) return
        if (cur.state == state && state == ServerUiState.OFFLINE && now - lastEmitAt < 1_000L) return
        lastEmitAt = now
        _status.value = Status(state, retryInSec.coerceAtLeast(0L), now, isBreakerOpen, lastOnlineAtMs)
    }

    /** مسارات مختصرة — المصدر الوحيد لها RedConnectionService.onState. */
    fun publishOnline() = publish(ServerUiState.ONLINE, 0, false)
    fun publishConnecting() = publish(ServerUiState.CONNECTING, 0, false)
    fun publishOffline(retryInSec: Long = 0, isBreakerOpen: Boolean = false) =
        publish(ServerUiState.OFFLINE, retryInSec, isBreakerOpen)

    /** مسبار half-open/مهلة القاطع — تُستدعى من الخدمة عند فتح القاطع فقط. */
    fun publishBreakerOpen(remainingSec: Long) =
        publish(ServerUiState.OFFLINE, remainingSec.coerceAtLeast(1L), true)

    val isOnline: Boolean get() = _status.value.state == ServerUiState.ONLINE
    val isOffline: Boolean get() = _status.value.state == ServerUiState.OFFLINE
    val isBreakerOpenSnapshot: Boolean get() = _status.value.isBreakerOpen
}
