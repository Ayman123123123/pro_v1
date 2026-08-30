package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.red.sovereign.core.notifications.NotificationHelper

/**
 * مدير تكامل خدمات المكالمات — Call Service Integration Manager
 *
 * يدير تفاعل الخدمات المختلفة (YounesCallService, GroupCallService,
 * ConferenceService, LiveStreamService) مع بعضها البعض ومع النظام.
 */
object CallServiceIntegration {

    /**
     * بدء مكالمة فردية
     */
    fun startCall(context: Context, callId: String, peer: String, isVideo: Boolean) {
        val intent = Intent(context, YounesCallService::class.java).apply {
            action = YounesCallService.ACTION_START
            putExtra(YounesCallService.EXTRA_CALL_ID, callId)
            putExtra(YounesCallService.EXTRA_PEER, peer)
            putExtra(YounesCallService.EXTRA_IS_VIDEO, isVideo)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * إنهاء مكالمة فردية
     */
    fun endCall(context: Context, callId: String) {
        YounesCallService.action(context, YounesCallService.ACTION_END)
    }

    /**
     * بدء مكالمة جماعية
     */
    fun startGroupCall(
        context: Context,
        groupId: String,
        groupName: String,
        isVideo: Boolean,
        members: List<String>
    ) {
        val intent = Intent(context, GroupCallService::class.java).apply {
            action = GroupCallService.ACTION_START
            putExtra(GroupCallService.EXTRA_GROUP_ID, groupId)
            putExtra(GroupCallService.EXTRA_GROUP_NAME, groupName)
            putExtra(GroupCallService.EXTRA_IS_VIDEO, isVideo)
            putStringArrayListExtra(GroupCallService.EXTRA_MEMBERS, ArrayList(members))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * إنهاء مكالمة جماعية
     */
    fun endGroupCall(context: Context, groupId: String) {
        GroupCallService.end(context)
    }

    /**
     * بدء مؤتمر
     */
    fun startConference(context: Context, roomId: String, isVideo: Boolean) {
        val intent = Intent(context, ConferenceService::class.java).apply {
            action = ConferenceService.ACTION_START
            putExtra(ConferenceService.EXTRA_ROOM_ID, roomId)
            putExtra(ConferenceService.EXTRA_IS_VIDEO, isVideo)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * مغادرة مؤتمر
     */
    fun leaveConference(context: Context) {
        ConferenceService.leave(context)
    }

    /**
     * بدء بث مباشر
     */
    fun startLiveStream(context: Context, roomId: String, isVideo: Boolean) {
        val intent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_START
            putExtra(LiveStreamService.EXTRA_ROOM_ID, roomId)
            putExtra(LiveStreamService.EXTRA_IS_VIDEO, isVideo)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * إيقاف البث المباشر
     */
    fun stopLiveStream(context: Context) {
        LiveStreamService.stop(context)
    }

    /**
     * بدء مكالمة PSTN
     */
    fun startPstnCall(context: Context, phoneNumber: String) {
        val intent = Intent(context, PstnCallForegroundService::class.java).apply {
            action = PstnCallForegroundService.ACTION_DIAL
            putExtra(PstnCallForegroundService.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * إنهاء مكالمة PSTN
     */
    fun endPstnCall(context: Context) {
        PstnCallForegroundService.action(context, PstnCallForegroundService.ACTION_HANGUP)
    }

    /**
     * التحقق من وجود مكالمة نشطة
     */
    fun hasActiveCall(context: Context): Boolean {
        return YounesCallService.isCallActive(context) ||
               GroupCallService.isGroupCallActive(context) ||
               ConferenceService.isConferenceActive(context) ||
               PstnCallForegroundService.isPstnCallActive(context)
    }

    /**
     * الحصول على نوع المكالمة النشطة
     */
    fun getActiveCallType(context: Context): String? {
        return when {
            YounesCallService.isCallActive(context) -> "1to1"
            GroupCallService.isGroupCallActive(context) -> "group"
            ConferenceService.isConferenceActive(context) -> "conference"
            PstnCallForegroundService.isPstnCallActive(context) -> "pstn"
            else -> null
        }
    }

    /**
     * إرسال إجراء لجميع الخدمات النشطة
     */
    fun broadcastAction(context: Context, action: String, extraKey: String? = null, extraValue: Any? = null) {
        val intent = Intent("com.red.sovereign.calls.ACTION").apply {
            action = action
            extraKey?.let { putExtra(it, extraValue ?: "") }
        }
        context.sendBroadcast(intent)
    }
}
