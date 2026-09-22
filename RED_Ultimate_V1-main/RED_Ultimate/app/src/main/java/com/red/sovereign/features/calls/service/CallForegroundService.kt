package com.red.sovereign.features.calls.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.telecom.CallAudioState
import androidx.core.app.NotificationCompat
import com.red.sovereign.features.calls.CallOrchestrator
import com.red.sovereign.features.calls.VoipState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * خدمة Android أمامية للمكالمات.
 *
 * - تُبقي المكالمة نشطة عند إغلاق التطبيق (WakeLock)
 * - تعرض إشعار CallStyle دائم مع أزرار (كتم / إنهاء)
 * - تُنشئ القناة الصوتية بالأولوية العالية
 */
@AndroidEntryPoint
class CallForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "red_call_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_END_CALL = "com.red.sovereign.ACTION_END_CALL"
        const val ACTION_TOGGLE_MUTE = "com.red.sovereign.ACTION_TOGGLE_MUTE"

        fun startCall(context: Context, callerName: String, callId: String, isVideo: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra("callerName", callerName)
                putExtra("callId", callId)
                putExtra("isVideo", isVideo)
                action = "START"
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply { action = "STOP" }
            context.startService(intent)
        }
    }

    @Inject lateinit var callOrchestrator: CallOrchestrator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var callerName = ""
    private var callId = ""
    private var isMuted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                callerName = intent.getStringExtra("callerName") ?: "مجهول"
                callId = intent.getStringExtra("callId") ?: ""
                val isVideo = intent.getBooleanExtra("isVideo", false)
                startForeground(NOTIFICATION_ID, buildCallNotification(callerName, isVideo))
            }
            "STOP" -> stopSelf()
            ACTION_END_CALL -> {
                callOrchestrator.endActiveCall()
                stopSelf()
            }
            ACTION_TOGGLE_MUTE -> {
                isMuted = !isMuted
                // TODO: Route to CallViewModel.toggleMute via shared state
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────

    private fun buildCallNotification(name: String, isVideo: Boolean): Notification {
        val endIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CallForegroundService::class.java).apply { action = ACTION_END_CALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CallForegroundService::class.java).apply { action = ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (isVideo) "📹 مكالمة فيديو" else "📞 مكالمة صوتية")
            .setContentText(name)
            .setSubText("RED Sovereign")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                if (isMuted) "إلغاء الكتم" else "كتم",
                muteIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إنهاء",
                endIntent
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildCallNotification(callerName, false))
    }

    // ── Channel ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "مكالمات RED",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات المكالمات النشطة"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    // ── WakeLock ──────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RED:CallWakeLock"
        ).apply { acquire(60 * 60 * 1000L) /* max 1 hour */ }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
}
