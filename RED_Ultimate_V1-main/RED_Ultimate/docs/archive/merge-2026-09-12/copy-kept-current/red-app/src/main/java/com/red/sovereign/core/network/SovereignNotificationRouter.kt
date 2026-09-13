package com.red.sovereign.core.network

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.red.sovereign.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 🔔 YOUNES Sovereign Notification Service with Real Routing
 * خدمة الإشعارات السيادية — WebSocket حقيقي + توجيه الرسائل + إشعارات أصلية
 */
class SovereignNotificationRouter : Service() {

    companion object {
        private const val TAG = "RED.NotificationRouter"
        const val CHANNEL_MESSAGES = "red_messages"
        const val CHANNEL_CALLS = "red_calls"
        const val CHANNEL_GROUPS = "red_groups"
        const val CHANNEL_SYSTEM = "red_system"
        const val CHANNEL_DINSTAR = "red_dinstar"
        const val CHANNEL_LIVE = "red_live"
        const val FOREGROUND_ID = 1001

        // Intent Actions
        const val ACTION_CONNECT = "com.red.action.CONNECT"
        const val ACTION_DISCONNECT = "com.red.action.DISCONNECT"
        const val ACTION_MARK_READ = "com.red.action.MARK_READ"

        // State
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        private val _pendingNotifications = MutableStateFlow<List<NotificationData>>(emptyList())
        val pendingNotifications: StateFlow<List<NotificationData>> = _pendingNotifications
    }

    data class NotificationData(
        val type: String,
        val title: String,
        val body: String,
        val senderId: String? = null,
        val senderName: String? = null,
        val threadId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connected = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var retryCount = 0
    private val maxRetries = 10
    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createAllChannels()
        Log.i(TAG, "Sovereign notification router created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MARK_READ -> {
                val notifId = intent.getIntExtra("notification_id", -1)
                if (notifId > 0) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notifId)
                }
                return START_STICKY
            }
        }

        startForeground(FOREGROUND_ID, createForegroundNotification("يونس — اتصال سيادي نشط"))

        if (!connected.get()) {
            connectWebSocket()
        }

        return START_STICKY
    }

    // ─── WebSocket Connection ───

    private fun connectWebSocket() {
        val host = System.getProperty("red.backend.host") ?: "192.168.1.50"
        val port = System.getProperty("red.backend.port") ?: "8080"
        val token = System.getProperty("red.auth.token") ?: ""
        val wsUrl = "ws://$host:$port/ws/master?token=$token"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connected.set(true)
                _isConnected.value = true
                retryCount = 0
                updateForeground("يونس — متصل")
                Log.i(TAG, "WebSocket connected to sovereign backend")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "Message received: ${text.take(200)}")
                routeMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Binary message: ${bytes.size} bytes")
                // Parse binary payload
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                connected.set(false)
                _isConnected.value = false
                updateForeground("يونس — غير متصل")
                Log.w(TAG, "WebSocket closing: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                _isConnected.value = false
                updateForeground("يونس — خطأ في الاتصال")
                Log.e(TAG, "WebSocket failure", t)
                scheduleReconnect()
            }
        })
    }

    // ─── Message Routing ───

    private fun routeMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            val payload = json.optJSONObject("payload") ?: json

            when (type) {
                // ─── الرسائل ───
                "new_message", "message" -> {
                    val sender = payload.optString("senderName", "مجهول")
                    val content = payload.optString("content", "")
                    val chatId = payload.optString("chatId", "")
                    val isGroup = payload.optBoolean("isGroup", false)

                    showNotification(
                        channel = CHANNEL_MESSAGES,
                        title = if (isGroup) "${payload.optString("groupName", "مجموعة")}: $sender" else sender,
                        body = content,
                        category = Notification.CATEGORY_MESSAGE,
                        priority = NotificationCompat.PRIORITY_HIGH,
                        senderPerson = Person.Builder().setName(sender).build()
                    )
                }

                // ─── المكالمات ───
                "incoming_call" -> {
                    val caller = payload.optString("callerName", "مجهول")
                    val callType = payload.optString("callType", "audio")
                    val callId = payload.optString("callId", "")

                    showNotification(
                        channel = CHANNEL_CALLS,
                        title = "مكالمة واردة من $caller",
                        body = if (callType == "video") "مكالمة فيديو" else "مكالمة صوتية",
                        category = Notification.CATEGORY_CALL,
                        priority = NotificationCompat.PRIORITY_MAX,
                        isCall = true
                    )
                }

                "missed_call" -> {
                    val caller = payload.optString("callerName", "مجهول")
                    showNotification(
                        channel = CHANNEL_CALLS,
                        title = "مكالمة فائتة",
                        body = "من $caller",
                        category = Notification.CATEGORY_CALL,
                        priority = NotificationCompat.PRIORITY_HIGH
                    )
                }

                // ─── القصص ───
                "story_view" -> {
                    val viewer = payload.optString("viewerName", "")
                    showNotification(
                        channel = CHANNEL_MESSAGES,
                        title = viewer,
                        body = "شاهد قصتك",
                        priority = NotificationCompat.PRIORITY_LOW
                    )
                }

                // ─── المجموعات ───
                "group_invite" -> {
                    val groupName = payload.optString("groupName", "")
                    val inviter = payload.optString("inviterName", "")
                    showNotification(
                        channel = CHANNEL_GROUPS,
                        title = "دعوة لمجموعة",
                        body = "$inviter دعاك لـ $groupName",
                        priority = NotificationCompat.PRIORITY_DEFAULT
                    )
                }

                "role_change" -> {
                    val groupName = payload.optString("groupName", "")
                    val newRole = payload.optString("newRole", "")
                    showNotification(
                        channel = CHANNEL_GROUPS,
                        title = "تغيير دورك في $groupName",
                        body = "أصبحت: $newRole",
                        priority = NotificationCompat.PRIORITY_DEFAULT
                    )
                }

                // ─── البث المباشر ───
                "live_started" -> {
                    val hostName = payload.optString("hostName", "")
                    showNotification(
                        channel = CHANNEL_LIVE,
                        title = "بث مباشر بدأ",
                        body = "$hostName بدأ بثاً مباشراً",
                        priority = NotificationCompat.PRIORITY_DEFAULT
                    )
                }

                "space_started" -> {
                    val hostName = payload.optString("hostName", "")
                    showNotification(
                        channel = CHANNEL_LIVE,
                        title = "غرفة صوتية جديدة",
                        body = "$hostName بدأ غرفة صوتية",
                        priority = NotificationCompat.PRIORITY_DEFAULT
                    )
                }

                // ─── Dinstar ───
                "dinstar_status" -> {
                    val status = payload.optString("status", "")
                    showNotification(
                        channel = CHANNEL_DINSTAR,
                        title = "تحديث Dinstar",
                        body = status,
                        priority = NotificationCompat.PRIORITY_LOW
                    )
                }

                "dinstar_alert" -> {
                    val alert = payload.optString("alert", "")
                    showNotification(
                        channel = CHANNEL_DINSTAR,
                        title = "⚠️ تنبيه Dinstar",
                        body = alert,
                        priority = NotificationCompat.PRIORITY_HIGH
                    )
                }

                // ─── الأمان ───
                "security_alert" -> {
                    val alert = payload.optString("alert", "")
                    showNotification(
                        channel = CHANNEL_SYSTEM,
                        title = "🔒 تنبيه أمني",
                        body = alert,
                        category = Notification.CATEGORY_STATUS,
                        priority = NotificationCompat.PRIORITY_HIGH
                    )
                }

                "new_device" -> {
                    val deviceName = payload.optString("deviceName", "جهاز")
                    showNotification(
                        channel = CHANNEL_SYSTEM,
                        title = "جهاز جديد مسجل",
                        body = "$deviceName — تأكد من أن هذا أنت",
                        category = Notification.CATEGORY_STATUS,
                        priority = NotificationCompat.PRIORITY_HIGH
                    )
                }

                else -> {
                    Log.d(TAG, "Unhandled message type: $type")
                }
            }

            // إضافة للقائمة
            _pendingNotifications.value = listOf(
                NotificationData(type, "", text.take(100))
            ) + _pendingNotifications.value.take(99)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to route message", e)
        }
    }

    // ─── Show Notification ───

    private var notificationIdCounter = 2000
    private fun showNotification(
        channel: String,
        title: String,
        body: String,
        category: String? = null,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        senderPerson: Person? = null,
        isCall: Boolean = false
    ) {
        val id = notificationIdCounter++
        val builder = NotificationCompat.Builder(this, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_red)
            .setPriority(priority)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        category?.let { builder.setCategory(it) }
        senderPerson?.let {
            val style = NotificationCompat.MessagingStyle(it)
            style.addMessage(body, System.currentTimeMillis(), it)
            builder.setStyle(style)
        }

        if (isCall) {
            builder.setOngoing(true)
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val callPendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(this, id, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else null
            if (callPendingIntent != null) {
                builder.setFullScreenIntent(callPendingIntent, true)
            }
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, builder.build())
    }

    // ─── Reconnect ───

    private fun scheduleReconnect() {
        if (retryCount >= maxRetries) {
            Log.w(TAG, "Max retries reached ($maxRetries)")
            updateForeground("يونس — غير متصل (فشل)")
            return
        }

        val delay = minOf(30L, (2L shl retryCount)) // Exponential backoff: 1, 2, 4, 8, 16, 30
        retryCount++

        serviceScope.launch {
            kotlinx.coroutines.delay(delay * 1000)
            if (!connected.get()) {
                Log.i(TAG, "Reconnecting (attempt $retryCount)...")
                updateForeground("يونس — إعادة اتصال ($retryCount)")
                connectWebSocket()
            }
        }
    }

    private fun disconnect() {
        webSocket?.close(1000, "Service stopping")
        webSocket = null
        connected.set(false)
        _isConnected.value = false
        serviceScope.cancel()
    }

    // ─── Foreground Notification ───

    private fun createForegroundNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SYSTEM)
            .setContentTitle("يونس سيادي")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_red)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForeground(text: String) {
        val notification = createForegroundNotification(text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_ID, notification)
    }

    // ─── Notification Channels ───

    private fun createAllChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(CHANNEL_MESSAGES, "الرسائل", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعارات الرسائل الجديدة"
                enableLights(true)
                lightColor = 0xFF1E88E5.toInt()
            },
            NotificationChannel(CHANNEL_CALLS, "المكالمات", NotificationManager.IMPORTANCE_MAX).apply {
                description = "إشعارات المكالمات الواردة والفائتة"
                enableVibration(true)
            },
            NotificationChannel(CHANNEL_GROUPS, "المجموعات", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعارات المجموعات والدعوات"
            },
            NotificationChannel(CHANNEL_LIVE, "البث المباشر", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعارات البث المباشر والغرف الصوتية"
            },
            NotificationChannel(CHANNEL_DINSTAR, "Dinstar Gateway", NotificationManager.IMPORTANCE_LOW).apply {
                description = "حالة وتنبيهات بوابة Dinstar GSM"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_SYSTEM, "النظام والأمان", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعارات النظام والتنبيهات الأمنية"
                lockScreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
        )

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(channels)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        Log.i(TAG, "Notification router destroyed")
        super.onDestroy()
    }
}
