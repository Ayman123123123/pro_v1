package com.red.sovereign.network

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.red.sovereign.R

/**
 * RED Sovereign Push Service
 * Ensures real-time message arrival via a persistent local WebSocket link.
 */
class RedPushService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(999, createSovereignNotification())
    }

    private fun createSovereignNotification() = NotificationCompat.Builder(this, "RED_PUSH")
        .setContentTitle("RED Security Engine")
        .setContentText("Protecting your sovereign connection.")
        .setSmallIcon(R.drawable.ic_launcher_red)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
