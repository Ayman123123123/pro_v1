package com.red.sovereign.groups

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.red.sovereign.calls.GroupCallService
import com.red.sovereign.calls.ZoomGroupCallService

/**
 * حالة المكالمة الجماعية النشطة داخل المجموعة
 */
data class ActiveGroupCallState(
    val groupId: String,
    val callId: String,
    val isVideo: Boolean,
    val isHost: Boolean,
    val participantCount: Int = 1,
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = false
)

/**
 * منسّق المكالمات الجماعية (GroupCallCoordinator)
 * الجسر السيادي بين شاشات ونماذج المجموعات ومحرك المكالمات الجماعية (SFU / Mesh WebRTC).
 */
object GroupCallCoordinator {

    private val _activeCallState = MutableStateFlow<ActiveGroupCallState?>(null)
    val activeCallState: StateFlow<ActiveGroupCallState?> = _activeCallState.asStateFlow()

    /**
     * بدء مكالمة جماعية صوتية أو مرئية لمجموعة معينة
     */
    fun startGroupCall(context: Context, groupId: String, isVideo: Boolean, useZoomMode: Boolean = false) {
        val callId = "grp_call_${groupId}_${System.currentTimeMillis()}"
        _activeCallState.value = ActiveGroupCallState(
            groupId = groupId,
            callId = callId,
            isVideo = isVideo,
            isHost = true,
            isVideoEnabled = isVideo
        )

        val serviceClass = if (useZoomMode) ZoomGroupCallService::class.java else GroupCallService::class.java
        val intent = Intent(context, serviceClass).apply {
            action = "ACTION_START_GROUP_CALL"
            putExtra("EXTRA_GROUP_ID", groupId)
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_IS_VIDEO", isVideo)
        }
        context.startForegroundService(intent)
    }

    /**
     * الانضمام إلى مكالمة جماعية نشطة
     */
    fun joinGroupCall(context: Context, groupId: String, callId: String, isVideo: Boolean) {
        _activeCallState.value = ActiveGroupCallState(
            groupId = groupId,
            callId = callId,
            isVideo = isVideo,
            isHost = false,
            isVideoEnabled = isVideo
        )

        val intent = Intent(context, GroupCallService::class.java).apply {
            action = "ACTION_JOIN_GROUP_CALL"
            putExtra("EXTRA_GROUP_ID", groupId)
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_IS_VIDEO", isVideo)
        }
        context.startForegroundService(intent)
    }

    /**
     * إنهاء أو مغادرة المكالمة الجماعية
     */
    fun leaveGroupCall(context: Context) {
        val current = _activeCallState.value ?: return
        val intent = Intent(context, GroupCallService::class.java).apply {
            action = "ACTION_LEAVE_GROUP_CALL"
            putExtra("EXTRA_GROUP_ID", current.groupId)
            putExtra("EXTRA_CALL_ID", current.callId)
        }
        context.startService(intent)
        _activeCallState.value = null
    }

    /**
     * كتم أو تفعيل الميكروفون
     */
    fun toggleMute() {
        _activeCallState.value = _activeCallState.value?.let {
            it.copy(isMuted = !it.isMuted)
        }
    }

    /**
     * تفعيل أو إيقاف الكاميرا
     */
    fun toggleVideo() {
        _activeCallState.value = _activeCallState.value?.let {
            it.copy(isVideoEnabled = !it.isVideoEnabled)
        }
    }

    /**
     * تحديث عدد المشاركين في المكالمة
     */
    fun updateParticipants(count: Int) {
        _activeCallState.value = _activeCallState.value?.copy(participantCount = count)
    }
}