package com.red.sovereign.network

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.red.sovereign.R
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.util.concurrent.atomic.AtomicBoolean

/**
 * YOUNES Sovereign Notification Service
 * Maintains a persistent WebSocket to the local backend for real-time
 * message/call delivery without Google FCM.
 */
class RedNotificationService : Service() {

    companion object {
        private const val TAG = "RED.NotificationService"
        const val CHANNEL_ID = "red_sovereign_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CONNECT = "com.red.action.CONNECT"
        const val ACTION_DISCONNECT = "com.red.action.DISCONNECT"
    }

    private val isConnected = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder().build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Notification service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnectWebSocket()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = createNotification("يونس — اتصال سيادي نشط")
        startForeground(NOTIFICATION_ID, notification)

        if (!isConnected.get()) {
            connectToRedSocket()
        }

        return START_STICKY
    }

    private fun connectToRedSocket() {
        // TODO: Replace with actual backend WebSocket URL from SharedPreferences/DataStore
        val host = System.getProperty("red.backend.host") ?: "192.168.1.50"
        val port = System.getProperty("red.backend.port") ?: "8080"
        val wsUrl = "ws://$host:$port/ws/master"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                updateNotification("يونس — متصل")
                Log.i(TAG, "WebSocket connected to sovereign backend")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: ${text.take(100)}")
                // TODO: Parse protobuf/JSON and route to appropriate handler
                // - New message → show chat notification
                // - Incoming call → launch call screen
                // - Gateway alert → update Dinstar status
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "WebSocket binary message received (${bytes.size} bytes)")
                // TODO: Parse binary protobuf message
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected.set(false)
                updateNotification("يونس — غير متصل (إعادة اتصال)")
                Log.w(TAG, "WebSocket closing: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                updateNotification("يونس — خطأ في الاتصال")
                Log.e(TAG, "WebSocket failure", t)
                // Auto-reconnect after 5 seconds
                // TODO: Use WorkManager for reliable retry
            }
        })
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "Service stopping")
        webSocket = null
        isConnected.set(false)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("يونس سيادي")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_red)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "اتصال يونس السيادي",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة الإشعارات السيادية للمحادثات والمكالمات"
                setShowBadge(false)
                lockScreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnectWebSocket()
        client.dispatcher.executorService.shutdown()
        Log.i(TAG, "Notification service destroyed")
        super.onDestroy()
    }
}
