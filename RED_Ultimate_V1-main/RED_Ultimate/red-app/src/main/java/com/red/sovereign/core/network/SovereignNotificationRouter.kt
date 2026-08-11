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
 * 🔔 YOUNES Sovereign Notification Router
 * محرك التوجيه السيادي — يربط WebSocket بالإشعارات المحلية
 */
class SovereignNotificationRouter : Service() {

    companion object {
        const val CHANNEL_MESSAGES = "red_messages"
        const val CHANNEL_CALLS = "red_calls"
        const val CHANNEL_DINSTAR = "red_dinstar"
        const val FOREGROUND_ID = 1001
        const val ACTION_CONNECT = "com.red.action.CONNECT"
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "red_system")
            .setContentTitle("يونس سيادي")
            .setContentText("موجه الإشعارات نشط")
            .setSmallIcon(R.drawable.younes_icon_master_vector)
            .setOngoing(true)
            .build()
        startForeground(FOREGROUND_ID, notification)
        return START_STICKY
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, "الرسائل", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CALLS, "المكالمات", NotificationManager.IMPORTANCE_MAX))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DINSTAR, "Dinstar Gateway", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("red_system", "نظام يونس", NotificationManager.IMPORTANCE_MIN))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
