package com.red.sovereign.core.network

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.red.sovereign.R
import com.red.sovereign.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*

/**
 * 🔔 RED Sovereign Notification Router - RED-only
 * محرك التوجيه السيادي — يربط WebSocket بالإشعارات المحلية
 * بدون PSTN/DINSTAR - مكالمات RED فقط عبر WebRTC
 */
class SovereignNotificationRouter : Service() {

    companion object {
        const val CHANNEL_MESSAGES = "red_messages"
        const val CHANNEL_CALLS = "red_calls"
        const val CHANNEL_LIVE = "red_live"
        const val CHANNEL_SPACES = "red_spaces"
        const val FOREGROUND_ID = 1001
        const val ACTION_CONNECT = "com.red.action.CONNECT"
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "red_system")
            .setContentTitle("يونس سيادي RED-only")
            .setContentText("موجه الإشعارات نشط - مكالمات RED + بث + مساحات")
            .setSmallIcon(R.drawable.younes_icon_master_vector)
            .setOngoing(true)
            .build()
        startForeground(FOREGROUND_ID, notification)
        return START_STICKY
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, getString(com.red.sovereign.R.string.channel_messages_name), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CALLS, getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel("red_calls_incoming", getString(com.red.sovereign.R.string.channel_calls_incoming_name), NotificationManager.IMPORTANCE_MAX).apply {
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_LIVE, "بث مباشر يونس", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SPACES, "مساحات يونس", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel("red_system", getString(com.red.sovereign.R.string.channel_system_name), NotificationManager.IMPORTANCE_MIN))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
