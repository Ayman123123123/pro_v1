package com.red.sovereign.calls

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * مُخطر احتياطي لمكالمة PSTN عندما لا يوجد منسق جلسة نشط.
 * يفتح التطبيق على شاشة الرنين؛ القبول الكامل يتطلب جلسة صالحة.
 */
object PstnRingFallbackNotifier {
    fun show(context: Context, callId: String, caller: String) {
        val launch = Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_PSTN)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_PEER, caller)
            putExtra(IncomingCallActivity.EXTRA_MODE, "PSTN")
        }
        val pi = PendingIntent.getActivity(
            context, callId.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(context, "red_calls_incoming")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("مكالمة واردة")
            .setContentText(caller)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(callId.hashCode(), notif)
    }
}
