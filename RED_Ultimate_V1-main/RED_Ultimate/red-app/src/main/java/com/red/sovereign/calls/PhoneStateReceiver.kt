package com.red.sovereign.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.telephony.TelephonyManager
import com.red.sovereign.MainActivity

/**
 * يستقبل أحداث PSTN النظامية (مكالمة واردة على خط الهاتف).
 * لا يمكن لـ RED VoIP الرد عليها مباشرة، لكن نستفيد من:
 * 1. تذكير المستخدم بوجود خط PSTN إذا كان في مكالمة RED نشطة
 * 2. الهدوء التلقائي للـ ringer عند ورود PSTN
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (CallRuntime.state is CallUiState.Active) {
                    showPstnReminderNotification(context, incomingNumber)
                }
                // Silence current RED ringer to avoid acoustic conflict
                YounesCallService.silenceRinger()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // User picked up PSTN — pause any media, but don't auto-end RED
                if (CallRuntime.state is CallUiState.Active) {
                    YounesCallService.holdActiveCall()
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // PSTN ended — resume RED ringer/UI if a RED call is incoming
                YounesCallService.resumeRinger()
            }
        }
    }

    private fun showPstnReminderNotification(context: Context, number: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        if (manager.getNotificationChannel(PSTN_CHANNEL) == null) {
            val channel = NotificationChannel(
                PSTN_CHANNEL,
                "مكالمة PSTN واردة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تذكير عند ورود مكالمة هاتف عادي أثناء استخدام RED"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val displayNumber = number?.let { maskPhoneNumber(it) } ?: "رقم مخفي"
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PSTN_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_phone_call)
            .setContentTitle("مكالمة هاتفية واردة")
            .setContentText("PSTN: $displayNumber — رُد من تطبيق الهاتف")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        manager.notify(PSTN_NOTIFICATION_ID, notification)
        android.util.Log.i("PhoneStateReceiver", "PSTN notification shown for: $displayNumber")
    }

    /** Mask phone number: +9677XXXXXXX → +967••••XXX */
    private fun maskPhoneNumber(number: String): String {
        if (number.length < 6) return number
        val prefix = number.take(4)
        val suffix = number.takeLast(2)
        return "$prefix••••$suffix"
    }

    companion object {
        private const val PSTN_CHANNEL = "red_pstn_reminder"
        private const val PSTN_NOTIFICATION_ID = 8001
    }
}
