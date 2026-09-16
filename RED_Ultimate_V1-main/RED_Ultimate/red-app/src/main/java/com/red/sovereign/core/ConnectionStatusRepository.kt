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

    fun publish(state: ServerUiState, retryInSec: Long = 0) {
        _status.value = Status(state, retryInSec, System.currentTimeMillis())
    }

    val isOnline: Boolean get() = _status.value.state == ServerUiState.ONLINE
}
