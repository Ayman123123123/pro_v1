package com.red.sovereign.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground Service that keeps the Android process alive during a PSTN call.
 *
 * Without this, Android will kill the process when the app goes to background
 * during an active PSTN call (WebRTC audio bridge to DINSTAR GSM).
 */
class PstnCallForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "red_pstn_call"
        private const val NOTIFICATION_ID = 7501
        const val ACTION_START = "com.red.sovereign.pstn.START"
        const val ACTION_STOP = "com.red.sovereign.pstn.STOP"

        fun start(context: Context, number: String, isIncoming: Boolean = false) {
            val intent = Intent(context, PstnCallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra("number", number)
                putExtra("isIncoming", isIncoming)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PstnCallForegroundService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra("number") ?: "Unknown"
                val isIncoming = intent.getBooleanExtra("isIncoming", false)
                val label = if (isIncoming) "Incoming PSTN" else "Outgoing PSTN"
                val notif = buildNotification("$label: $number")
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
                acquireWakeLock()
            }
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "red:pstn-call")
            wakeLock?.acquire(30 * 60 * 1000L) // 30 min max safety
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "PSTN Calls", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.red.sovereign.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("RED Sovereign")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }
}
