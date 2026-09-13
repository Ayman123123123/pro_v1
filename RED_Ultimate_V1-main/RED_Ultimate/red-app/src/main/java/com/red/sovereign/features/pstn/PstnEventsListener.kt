package com.red.sovereign.sms

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ✨ مستمع أحداث PSTN/SMS الحية — `/ws/pstn`.
 *
 * الخادم (PstnEventWebSocketHandler) يبث:
 * - SMS_RECEIVED {id, number, content, time, port}
 * - SMS_STATUS   {id, number, status}
 * - PSTN_CALL_EVENT / PSTN_INCOMING
 *
 * يُشغَّل مع شاشة الرسائل: أول SMS_RECEIVED يحدّث المحادثات فوراً
 * (بلا انتظار الاستقصاء 30 ث) ويطلق إشعاراً نظامياً إن كانت الشاشة مقفلة.
 */
class PstnEventsListener(
    context: Context,
    private val onSmsReceived: (number: String, text: String) -> Unit,
    private val onSmsStatus: (number: String, status: String) -> Unit = { _, _ -> }
) {
    companion object {
        private const val TAG = "RED.PstnEvents"
        private const val PATH = "/ws/pstn"
    }

    private val appContext = context.applicationContext
    private val tokens = TokenStore(appContext)
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private var stopped = false

    fun start() {
        if (connected.get() || stopped) return
        val token = tokens.accessToken ?: return
        val url = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .trimEnd('/') + PATH
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                Log.i(TAG, "PSTN events WS connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                if (!stopped) Log.d(TAG, "PSTN events WS dropped: ${t.message}")
            }
        })
    }

    private fun handle(text: String) {
        runCatching {
            val json = org.json.JSONObject(text)
            val type = json.optString("type")
            val data = json.optJSONObject("data") ?: return
            when (type) {
                "SMS_RECEIVED" -> onSmsReceived(
                    data.optString("number"),
                    data.optString("content").ifBlank { data.optString("text") }
                )
                "SMS_STATUS" -> onSmsStatus(
                    data.optString("number"),
                    data.optString("status")
                )
                // PSTN_CALL_EVENT/PSTN_INCOMING تُدار في طبقة المكالمات — هنا للرسائل فقط
            }
        }.onFailure { Log.d(TAG, "bad payload: ${it.message}") }
    }

    fun stop() {
        stopped = true
        runCatching { ws?.close(1000, "leave") }
        connected.set(false)
    }
}
