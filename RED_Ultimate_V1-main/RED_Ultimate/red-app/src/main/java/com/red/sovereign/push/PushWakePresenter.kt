package com.red.sovereign.push

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.red.sovereign.R
import com.red.sovereign.calls.CallNotificationActionReceiver
import com.red.sovereign.calls.CallNotificationManager
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.GroupCallService
import com.red.sovereign.calls.IncomingCallActivity
import com.red.sovereign.calls.LiveStreamService
import java.util.concurrent.ConcurrentHashMap

/**
 * Presents an incoming call that arrived as a dead-process push wake.
 *
 * A background push cannot reliably start an activity on Android 10+, so we use
 * the sanctioned VoIP recipe: a high-importance notification whose full-screen
 * intent targets [IncomingCallActivity] (which is showWhenLocked + turnScreenOn).
 * When the screen is already on and unlocked we additionally start the activity
 * directly for a snappier answer; otherwise the OS opens the full-screen intent.
 *
 * De-duplication is keyed by the call identifier and shares the notification id
 * with CallRingRegistry (callId.hashCode()), so the socket OFFER that lands a
 * moment later replaces - never duplicates - this ring.
 */
object PushWakePresenter {
    private const val TAG = "PushWakePresenter"
    // يغطي كامل الرنين (45s) + تأخر OFFER عبر socket — الأقصر كان يكرر الرنين وهمياً.
    private const val DEDUP_TTL_MS = 60_000L
    private const val RING_TIMEOUT_MS = 45_000L

    private val recent = ConcurrentHashMap<String, Long>()

    /**
     * Rings + opens the full-screen incoming UI.
     * @return true when this wake was presented (false when de-duplicated).
     */
    fun present(
        context: Context,
        callType: String,
        callId: String,
        from: String,
        mode: String,
        myUserId: String
    ): Boolean {
        if (callId.isBlank()) return false
        if (isDuplicate(callId)) {
            Log.i(TAG, "wake de-duplicated callId=$callId")
            return false
        }
        // بلا وهم: لا رنين وهمي عند تعطيل إشعارات المكالمات أو نشاط DND.
        // الإيقاظ يبقى sync فقط عبر RedPushService (socket/mailbox) دون full-screen.
        runCatching {
            val s = com.red.sovereign.settings.SettingsRuntime.current
            if (!s.callNotifications) {
                Log.i(TAG, "call wake suppressed: callNotifications off callId=$callId")
                return false
            }
            if (com.red.sovereign.settings.DndPolicy.shouldSuppress(s)) {
                Log.i(TAG, "call wake suppressed: DND active callId=$callId")
                return false
            }
        }
        // Android 13+: بلا POST_NOTIFICATIONS لا يمكن عرض الرنين — لا ندّعي العرض.
        // نعيد false قبل الحفظ في dedup حتى تُعاد المحاولة عند وصول OFFER عبر socket.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = runCatching {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!granted) {
                Log.i(TAG, "call wake suppressed: POST_NOTIFICATIONS denied callId=$callId")
                return false
            }
        }
        remember(callId)

        CallNotificationManager.createNotificationChannel(context)
        val peer = from.ifBlank { "RED" }
        val isVideo = mode.equals("VIDEO", ignoreCase = true)
        val target = incomingIntent(context, callType, callId, peer, mode, myUserId)

        postFullScreen(context, callType, callId, peer, isVideo, myUserId, target)

        if (isInteractiveAndUnlocked(context)) {
            runCatching { context.startActivity(target) }
                .onFailure { Log.w(TAG, "direct activity start blocked: ${it.message}") }
        }
        return true
    }

    /** Cancels the ring notification and clears the de-dup marker for [callId]. */
    fun cancel(context: Context, callId: String) {
        if (callId.isBlank()) return
        recent.remove(callId)
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(callId.hashCode())
        }
    }

    /** Drops all de-dup markers (logout / unregister). */
    fun clearDedup() = recent.clear()

    private fun isDuplicate(callId: String): Boolean {
        val now = System.currentTimeMillis()
        // ConcurrentHashMap iterator لا يدعم remove() — كان يرمي
        // UnsupportedOperationException بعد انتهاء TTL ويسقط الإيقاظ.
        // removeIf ذرّي وآمن على CHM.
        runCatching { recent.entries.removeIf { now - it.value > DEDUP_TTL_MS } }
        val last = recent[callId] ?: return false
        return now - last <= DEDUP_TTL_MS
    }

    private fun remember(callId: String) {
        recent[callId] = System.currentTimeMillis()
    }

    private fun isInteractiveAndUnlocked(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        pm.isInteractive && !km.isKeyguardLocked
    }.getOrDefault(false)

    private fun incomingIntent(
        context: Context,
        callType: String,
        callId: String,
        peer: String,
        mode: String,
        myUserId: String
    ): Intent {
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val isVideo = mode.equals("VIDEO", ignoreCase = true)
        when (callType) {
            IncomingCallActivity.CALL_TYPE_GROUP -> {
                intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_GROUP)
                intent.putExtra(GroupCallService.EXTRA_GROUP_CALL_ID, callId)
                intent.putExtra(GroupCallService.EXTRA_MY_USER_ID, myUserId)
                intent.putExtra(GroupCallService.EXTRA_HOST_NAME, peer)
                intent.putExtra(GroupCallService.EXTRA_IS_VIDEO, isVideo)
            }
            IncomingCallActivity.CALL_TYPE_CONFERENCE -> {
                intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_CONFERENCE)
                intent.putExtra(ConferenceService.EXTRA_ROOM_ID, callId)
                intent.putExtra(ConferenceService.EXTRA_USER_ID, myUserId)
                intent.putExtra(ConferenceService.EXTRA_INVITER, peer)
                intent.putExtra(ConferenceService.EXTRA_VIDEO, isVideo)
            }
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> {
                intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_LIVESTREAM)
                intent.putExtra(LiveStreamService.EXTRA_STREAM_ID, callId)
                intent.putExtra(LiveStreamService.EXTRA_USER_ID, myUserId)
                intent.putExtra(LiveStreamService.EXTRA_BROADCASTER_NAME, peer)
            }
            else -> {
                intent.putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_1TO1)
                intent.putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                intent.putExtra(IncomingCallActivity.EXTRA_PEER, peer)
                intent.putExtra(IncomingCallActivity.EXTRA_MODE, mode)
                intent.putExtra(IncomingCallActivity.EXTRA_INVITER, peer)
            }
        }
        return intent
    }

    private fun postFullScreen(
        context: Context,
        callType: String,
        callId: String,
        peer: String,
        isVideo: Boolean,
        myUserId: String,
        target: Intent
    ) {
        val notifId = callId.hashCode()
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPi = PendingIntent.getActivity(context, notifId, target, piFlags)
        val acceptPi = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_ACCEPT,
            callType,
            notifId,
            callId,
            myUserId,
            "",
            isVideo
        )
        val rejectPi = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_REJECT,
            callType,
            notifId,
            callId,
            myUserId
        )
        val title = when (callType) {
            IncomingCallActivity.CALL_TYPE_GROUP -> "دعوة مكالمة جماعية من $peer"
            IncomingCallActivity.CALL_TYPE_CONFERENCE -> "دعوة مؤتمر من $peer"
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> "بث مباشر من $peer"
            else -> "مكالمة واردة من $peer"
        }
        val builder = NotificationCompat.Builder(context, CallNotificationManager.CHANNEL_ID_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(if (isVideo) "مكالمة فيديو" else "مكالمة صوتية")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setColor(0xFF00C98C.toInt())
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "رفض", rejectPi)
            .addAction(android.R.drawable.ic_menu_call, "رد", acceptPi)

        // Android 13+: بلا POST_NOTIFICATIONS يسقط notify باستثناء — لا رنين وهمي،
        // الإيقاظ يبقى عبر socket/poll في RedPushService.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = runCatching {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!granted) {
                Log.i(TAG, "ring skipped: POST_NOTIFICATIONS not granted callId=$callId")
                return
            }
        }
        runCatching { NotificationManagerCompat.from(context).notify(notifId, builder.build()) }
            .onFailure { Log.w(TAG, "post ring notification failed: ${it.message}") }
    }
}
