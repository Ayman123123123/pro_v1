package com.red.sovereign.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير انتظار المكالمة — Call Waiting Manager
 *
 * يتعامل مع حالة "مكالمة في الانتظار" عندما يكون المستخدم في مكالمة نشطة
 * ويستقبل مكالمة أخرى. يدعم:
 * - عرض المكالمة الواردة كبطاقة عائمة
 * - السماح بقبول/رفض المكالمة الواردة
 * - وضع المكالمة الحالية على الانتظار
 * - التبديل بين المكالمات
 */
object CallWaitingManager {

    private val _waitingCall = MutableStateFlow<WaitingCallInfo?>(null)
    val waitingCall: StateFlow<WaitingCallInfo?> = _waitingCall.asStateFlow()

    private val _isHoldingActive = MutableStateFlow(false)
    val isHoldingActive: StateFlow<Boolean> = _isHoldingActive.asStateFlow()

    data class WaitingCallInfo(
        val callId: String,
        val peer: String,
        val isVideo: Boolean,
        val callType: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ActiveCallInfo(
        val callId: String,
        val peer: String,
        val isVideo: Boolean,
        val callType: String,
        val startedAt: Long
    )

    fun setWaitingCall(info: WaitingCallInfo?) {
        _waitingCall.value = info
    }

    fun setActiveCall(info: ActiveCallInfo?) {
        // In a real implementation, we'd store the active call info
        // For now, we just manage the waiting call state
    }

    fun holdActiveCall() {
        _isHoldingActive.value = true
    }

    fun resumeActiveCall() {
        _isHoldingActive.value = false
    }

    fun acceptWaitingCall() {
        // End current active call, accept waiting call
        _waitingCall.value = null
        _isHoldingActive.value = false
    }

    fun rejectWaitingCall() {
        _waitingCall.value = null
        _isHoldingActive.value = false
    }

    fun clearAll() {
        _waitingCall.value = null
        _isHoldingActive.value = false
    }

    fun hasWaitingCall(): Boolean = _waitingCall.value != null
    fun isHolding(): Boolean = _isHoldingActive.value
}
