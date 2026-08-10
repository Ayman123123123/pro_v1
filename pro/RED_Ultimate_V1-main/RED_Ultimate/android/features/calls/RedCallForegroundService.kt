package com.red.features.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.red.app.MainActivity

/**
 * RED Sovereign Call Foreground Service
 * Ensures VoIP and Dinstar PSTN calls remain active and handle incoming rings even in background.
 */
class RedCallForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "red_call_channel"
        const val NOTIFICATION_ID = 9999
        const val ACTION_START_CALL = "com.red.action.START_CALL"
        const val ACTION_STOP_CALL = "com.red.action.STOP_CALL"
        const val EXTRA_CALL_TYPE = "extra_call_type" // VOIP or DINSTAR
        const val EXTRA_CALLER_NAME = "extra_caller_name"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: "VOIP"
        val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "مكالمة سيادية واردة"

        if (action == ACTION_STOP_CALL) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createCallNotification(callerName, callType)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "المكالمات السيادية (RED Calls)"
            val descriptionText = "إشعارات المكالمات النشطة وعبر خط DINSTAR"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) // Handled by custom ringtone engine if needed
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createCallNotification(callerName: String, callType: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isDinstar = callType == "DINSTAR"
        val subtitle = if (isDinstar) "📞✨ مكالمة عبر خطي اليمني (DINSTAR)" else "📞 مكالمة عبر الإنترنت (VoIP)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(callerName)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
