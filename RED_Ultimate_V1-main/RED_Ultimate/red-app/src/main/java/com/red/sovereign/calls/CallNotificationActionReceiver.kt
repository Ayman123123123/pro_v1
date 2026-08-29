package com.red.sovereign.calls

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.red.sovereign.R

/**
 * مستقبل مركزي لجميع أزرار إشعارات المكالمات (1-to-1, Group, Conference, Zoom, PSTN, Live).
 *
 * قبل إنشائه كانت Notification Actions تستخدم `PendingIntent.getService` مباشرة
 * نحو الخدمات، وهذا يخالف سياسات FGS في Android 14 ويستهلك بطارية بدون داعٍ.
 * هنا نوحّد الاستقبال، نرسل ردّ الفعل للخدمة المناسبة، ونضيف RemoteInput للردّ السريع.
 *
 * الاستخدام من الخدمات:
 *   CallNotificationActionReceiver.receiverIntent(ctx, CallNotificationActionReceiver.ACTION_ACCEPT, CALL_TYPE_1TO1, notifId)
 *   CallNotificationActionReceiver.receiverIntent(ctx, CallNotificationActionReceiver.ACTION_END, CALL_TYPE_GROUP, notifId, groupCallId = id)
 */
class CallNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: CALL_TYPE_1TO1
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val myUserId = intent.getStringExtra(EXTRA_MY_USER_ID) ?: ""
        val hostId = intent.getStringExtra(EXTRA_HOST_ID) ?: ""
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        if (notifId > 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (action == ACTION_REJECT || action == ACTION_DECLINE || action == ACTION_END) {
                nm.cancel(notifId)
            }
        }

        when (action) {
            ACTION_ACCEPT -> dispatchAccept(context, callType, callId, myUserId, hostId, isVideo)
            ACTION_ACCEPT_VIDEO -> dispatchAccept(context, callType, callId, myUserId, hostId, true)
            ACTION_ACCEPT_AUDIO -> dispatchAccept(context, callType, callId, myUserId, hostId, false)
            ACTION_REJECT, ACTION_DECLINE -> dispatchDecline(context, callType, callId)
            ACTION_END -> dispatchEnd(context, callType)
            ACTION_TOGGLE_MIC -> YounesCallService.action(context, YounesCallService.ACTION_MIC)
            ACTION_TOGGLE_SPEAKER -> YounesCallService.action(context, YounesCallService.ACTION_SPEAKER)
            ACTION_TOGGLE_VIDEO -> YounesCallService.action(context, YounesCallService.ACTION_CAMERA)
            ACTION_HOLD -> YounesCallService.action(context, YounesCallService.ACTION_HOLD)
            ACTION_RESUME -> YounesCallService.action(context, YounesCallService.ACTION_RESUME)
            ACTION_SWITCH_CAMERA -> YounesCallService.action(context, YounesCallService.ACTION_SWITCH_CAMERA)
            ACTION_BLUETOOTH -> YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH)
            ACTION_SILENCE -> YounesCallService.silenceRinger(context)
            ACTION_HOLD_ACTIVE -> YounesCallService.holdActiveCall(context)
            ACTION_RESUME_RINGER -> YounesCallService.resumeRinger(context)
            ACTION_QUICK_REPLY -> handleQuickReply(context, intent, callType, callId)
        }
    }

    private fun dispatchAccept(context: Context, callType: String, callId: String, myUserId: String, hostId: String, isVideo: Boolean) {
        when (callType) {
            CALL_TYPE_GROUP -> if (callId.isNotEmpty() && myUserId.isNotEmpty()) {
                GroupCallService.accept(context, callId, myUserId, isVideo)
            }
            CALL_TYPE_ZOOM -> if (callId.isNotEmpty() && myUserId.isNotEmpty()) {
                ZoomGroupCallService.accept(context, callId, myUserId, isVideo, hostId)
            }
            CALL_TYPE_CONFERENCE -> ConferenceService.accept(context, callId)
            else -> YounesCallService.accept(context, cameraOn = isVideo, micOn = true)
        }
    }

    private fun dispatchDecline(context: Context, callType: String, callId: String) {
        when (callType) {
            CALL_TYPE_GROUP -> if (callId.isNotEmpty()) GroupCallService.decline(context, callId)
            CALL_TYPE_ZOOM -> if (callId.isNotEmpty()) ZoomGroupCallService.decline(context, callId)
            else -> YounesCallService.action(context, YounesCallService.ACTION_REJECT)
        }
    }

    private fun dispatchEnd(context: Context, callType: String) {
        when (callType) {
            CALL_TYPE_GROUP -> GroupCallService.end(context)
            CALL_TYPE_CONFERENCE -> ConferenceService.leave(context)
            CALL_TYPE_ZOOM -> ZoomGroupCallService.end(context)
            else -> YounesCallService.action(context, YounesCallService.ACTION_END)
        }
    }

    private fun handleQuickReply(context: Context, intent: Intent, callType: String, callId: String) {
        val input = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = input.getString(KEY_QUICK_REPLY).orEmpty().trim()
        if (text.isEmpty()) return
        if (callType == CALL_TYPE_1TO1) {
            val i = Intent(context, YounesCallService::class.java).apply {
                action = "com.red.sovereign.call.QUICK_REPLY"
                putExtra("call_id", callId)
                putExtra("message", text)
            }
            try { context.startService(i) } catch (_: Exception) { }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.red.sovereign.callnotif.ACCEPT"
        const val ACTION_ACCEPT_VIDEO = "com.red.sovereign.callnotif.ACCEPT_VIDEO"
        const val ACTION_ACCEPT_AUDIO = "com.red.sovereign.callnotif.ACCEPT_AUDIO"
        const val ACTION_REJECT = "com.red.sovereign.callnotif.REJECT"
        const val ACTION_DECLINE = "com.red.sovereign.callnotif.DECLINE"
        const val ACTION_END = "com.red.sovereign.callnotif.END"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.callnotif.MIC"
        const val ACTION_TOGGLE_SPEAKER = "com.red.sovereign.callnotif.SPEAKER"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.callnotif.VIDEO"
        const val ACTION_HOLD = "com.red.sovereign.callnotif.HOLD"
        const val ACTION_RESUME = "com.red.sovereign.callnotif.RESUME"
        const val ACTION_SWITCH_CAMERA = "com.red.sovereign.callnotif.SWITCH_CAMERA"
        const val ACTION_BLUETOOTH = "com.red.sovereign.callnotif.BLUETOOTH"
        const val ACTION_SILENCE = "com.red.sovereign.callnotif.SILENCE"
        const val ACTION_HOLD_ACTIVE = "com.red.sovereign.callnotif.HOLD_ACTIVE"
        const val ACTION_RESUME_RINGER = "com.red.sovereign.callnotif.RESUME_RINGER"
        const val ACTION_QUICK_REPLY = "com.red.sovereign.callnotif.QUICK_REPLY"

        const val KEY_QUICK_REPLY = "key_quick_reply"

        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_MY_USER_ID = "extra_my_user_id"
        const val EXTRA_HOST_ID = "extra_host_id"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        const val CALL_TYPE_1TO1 = "1to1"
        const val CALL_TYPE_GROUP = "group"
        const val CALL_TYPE_CONFERENCE = "conference"
        const val CALL_TYPE_ZOOM = "zoom"
        const val CALL_TYPE_PSTN = "pstn"
        const val CALL_TYPE_LIVESTREAM = "livestream"

        private fun buildIntent(
            context: Context,
            action: String,
            callType: String,
            notifId: Int,
            callId: String,
            myUserId: String,
            hostId: String,
            isVideo: Boolean
        ): Intent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_CALL_TYPE, callType)
            if (callId.isNotEmpty()) putExtra(EXTRA_CALL_ID, callId)
            if (myUserId.isNotEmpty()) putExtra(EXTRA_MY_USER_ID, myUserId)
            if (hostId.isNotEmpty()) putExtra(EXTRA_HOST_ID, hostId)
            putExtra(EXTRA_IS_VIDEO, isVideo)
        }

        fun receiverIntent(
            context: Context,
            action: String,
            callType: String,
            notifId: Int,
            callId: String = "",
            myUserId: String = "",
            hostId: String = "",
            isVideo: Boolean = false
        ): PendingIntent {
            val intent = buildIntent(context, action, callType, notifId, callId, myUserId, hostId, isVideo)
            val requestCode = (action.hashCode() xor notifId xor callType.hashCode())
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, flags)
        }

        fun quickReplyRemoteInput(label: String): RemoteInput = RemoteInput.Builder(KEY_QUICK_REPLY)
            .setLabel(label)
            .setAllowFreeFormInput(true)
            .setChoices(arrayOf("سأعاود الاتصال لاحقاً", "لا أستطيع الآن", "أين أنت؟"))
            .build()

        fun quickReplyPendingIntent(
            context: Context,
            callType: String,
            notifId: Int,
            callId: String = "",
            myUserId: String = "",
            hostId: String = "",
            isVideo: Boolean = false
        ): PendingIntent {
            val intent = buildIntent(context, ACTION_QUICK_REPLY, callType, notifId, callId, myUserId, hostId, isVideo)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, (ACTION_QUICK_REPLY.hashCode() xor notifId), intent, flags)
        }

        fun acceptWithReplyAction(
            context: Context,
            callType: String,
            notifId: Int,
            callId: String = "",
            myUserId: String = "",
            hostId: String = "",
            isVideo: Boolean = false,
            acceptLabel: String
        ): NotificationCompat.Action {
            val replyPi = quickReplyPendingIntent(context, callType, notifId, callId, myUserId, hostId, isVideo)
            val replyInput = quickReplyRemoteInput(context.getString(R.string.call_quick_reply_hint))
            return NotificationCompat.Action.Builder(
                android.R.drawable.sym_action_chat,
                acceptLabel,
                replyPi
            ).addRemoteInput(replyInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        }
    }
}
