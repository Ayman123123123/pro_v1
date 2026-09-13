package com.red.sovereign.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير انتظار المكالمة — Call Waiting Manager
 *
 * يتعامل مع حالة "مكالمة في الانتظار" ومكالمات متعددة:
 * - عرض المكالمة الواردة كبطاقة عائمة / تراكب
 * - قبول/رفض المكالمة الواردة
 * - وضع المكالمة الحالية على الانتظار (Hold) واستئنافها (Resume)
 * - التبديل بين المكالمات (Swap Calls)
 * - دمج المكالمات (Conference Merge)
 * - إدارة قائمة المكالمات النشطة والمنتظرة
 */
object CallWaitingManager {

    private val _waitingCall = MutableStateFlow<WaitingCallInfo?>(null)
    val waitingCall: StateFlow<WaitingCallInfo?> = _waitingCall.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallInfo?>(null)
    val activeCall: StateFlow<ActiveCallInfo?> = _activeCall.asStateFlow()

    private val _isHoldingActive = MutableStateFlow(false)
    val isHoldingActive: StateFlow<Boolean> = _isHoldingActive.asStateFlow()

    private val _isSwapped = MutableStateFlow(false)
    val isSwapped: StateFlow<Boolean> = _isSwapped.asStateFlow()

    private val _heldCallsList = MutableStateFlow<List<ActiveCallInfo>>(emptyList())
    val heldCallsList: StateFlow<List<ActiveCallInfo>> = _heldCallsList.asStateFlow()

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
        val startedAt: Long,
        val isHeld: Boolean = false
    )

    fun setWaitingCall(info: WaitingCallInfo?) {
        _waitingCall.value = info
    }

    fun setActiveCall(info: ActiveCallInfo?) {
        _activeCall.value = info
    }

    fun holdActiveCall() {
        _isHoldingActive.value = true
        _activeCall.value = _activeCall.value?.copy(isHeld = true)
        _activeCall.value?.let { current ->
            if (!_heldCallsList.value.any { it.callId == current.callId }) {
                _heldCallsList.value = _heldCallsList.value + current
            }
        }
    }

    fun resumeActiveCall() {
        _isHoldingActive.value = false
        _activeCall.value = _activeCall.value?.copy(isHeld = false)
        _activeCall.value?.let { current ->
            _heldCallsList.value = _heldCallsList.value.filter { it.callId != current.callId }
        }
    }

    fun swapCalls() {
        val waiting = _waitingCall.value
        val active = _activeCall.value
        if (waiting != null && active != null) {
            val newActive = ActiveCallInfo(
                callId = waiting.callId,
                peer = waiting.peer,
                isVideo = waiting.isVideo,
                callType = waiting.callType,
                startedAt = System.currentTimeMillis(),
                isHeld = false
            )
            val newWaiting = WaitingCallInfo(
                callId = active.callId,
                peer = active.peer,
                isVideo = active.isVideo,
                callType = active.callType,
                timestamp = active.startedAt
            )
            _activeCall.value = newActive
            _waitingCall.value = newWaiting
            _isSwapped.value = !_isSwapped.value
        }
    }

    fun acceptWaitingCall() {
        val waiting = _waitingCall.value
        if (waiting != null) {
            _activeCall.value?.let { _ ->
                holdActiveCall()
            }
            _activeCall.value = ActiveCallInfo(
                callId = waiting.callId,
                peer = waiting.peer,
                isVideo = waiting.isVideo,
                callType = waiting.callType,
                startedAt = System.currentTimeMillis(),
                isHeld = false
            )
            _waitingCall.value = null
            _isHoldingActive.value = false
        }
    }

    fun rejectWaitingCall() {
        _waitingCall.value = null
    }

    fun mergeCalls() {
        val waiting = _waitingCall.value
        val active = _activeCall.value
        if (waiting != null && active != null) {
            _waitingCall.value = null
            _isHoldingActive.value = false
        }
    }

    fun clearAll() {
        _waitingCall.value = null
        _activeCall.value = null
        _isHoldingActive.value = false
        _isSwapped.value = false
        _heldCallsList.value = emptyList()
    }

    fun hasWaitingCall(): Boolean = _waitingCall.value != null
    fun isHolding(): Boolean = _isHoldingActive.value
}
