package com.red.sovereign.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.red.sovereign.R

/**
 * مدير إشعارات المكالمات — Call Notification Manager
 *
 * يدير عرض الإشعارات للمكالمات الواردة والصادرة والمكتملة.
 * يدعم:
 * - قناة إشعارات مخصصة للمكالمات
 * - أزرار تحكم (قبول/رفض/كتم)
 * - إشعارات مكالمات PSTN
 * - إشعارات المكالمات الجماعية
 * - إشعارات المؤتمر
 * - إشعارات البث المباشر
 */
object CallNotificationManager {

    const val CHANNEL_ID = "red_call_channel"
    const val CHANNEL_NAME = "إشعارات المكالمات"
    const val CHANNEL_DESCRIPTION = "إشعارات المكالمات الواردة والصادرة"

    private var notificationId = 1000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * إنشاء إشعار لمكالمة واردة
     */
    fun buildIncomingCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        callType: String,
        callId: String,
        myUserId: String,
        hostId: String? = null
    ): NotificationCompat.Builder {
        val acceptIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_ACCEPT,
            callType,
            notificationId++,
            callId,
            myUserId,
            hostId ?: "",
            isVideo
        )
        val rejectIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_REJECT,
            callType,
            notificationId++,
            callId,
            myUserId
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle("مكالمة واردة من $peer")
            .setContentText(if (isVideo) "مكالمة فيديو" else "مكالمة صوتية")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, com.red.sovereign.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_call,
                "رفض",
                rejectIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "قبول",
                acceptIntent
            )
    }

    /**
     * إنشاء إشعار لمكالمة صادرة
     */
    fun buildOutgoingCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        callType: String,
        callId: String,
        myUserId: String
    ): NotificationCompat.Builder {
        val endIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_END,
            callType,
            notificationId++,
            callId,
            myUserId
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle("جارٍ الاتصال بـ $peer")
            .setContentText(if (isVideo) "مكالمة فيديو" else "مكالمة صوتية")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(
                android.R.drawable.ic_menu_call,
                "إنهاء",
                endIntent
            )
    }

    /**
     * إنشاء إشعار لمكالمة نشطة
     */
    fun buildActiveCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        isMuted: Boolean,
        isSpeaker: Boolean,
        durationSeconds: Long,
        callType: String,
        callId: String,
        myUserId: String
    ): NotificationCompat.Builder {
        val endIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_END,
            callType,
            notificationId++,
            callId,
            myUserId
        )
        val micIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_TOGGLE_MIC,
            callType,
            notificationId++,
            callId,
            myUserId
        )
        val speakerIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_TOGGLE_SPEAKER,
            callType,
            notificationId++,
            callId,
            myUserId
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle("مكالمة نشطة مع $peer")
            .setContentText(formatDuration(durationSeconds))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(
                if (isMuted) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_call,
                if (isMuted) "تفعيل الميكروفون" else "كتم",
                micIntent
            )
            .addAction(
                if (isSpeaker) android.R.drawable.ic_menu_mylocation else android.R.drawable.ic_menu_compass,
                if (isSpeaker) "إيقاف المكبر" else "تفعيل المكبر",
                speakerIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "إنهاء",
                endIntent
            )
    }

    /**
     * تنسيق المدة للعرض
     */
    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * إزالة إشعار المكالمات
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }
}
