package com.red.sovereign.features.sms

import android.util.Log
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthApi
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * عميل WebSocket دائم لأحداث PSTN/SMS الحية عبر `/ws/pstn`.
 *
 * يبث الخادم رسائل مغلفة `{type, data}` — تُفكّ عبر PstnEventEnvelopeCodec:
 * - SMS_RECEIVED  {id, number, content, time, port}
 * - SMS_STATUS    {id, number, status}
 * - PSTN_CALL_EVENT {callId, event, number, cause, port, redId}
 * - PSTN_INCOMING {number, caller, port, channel, callId}
 *
 * المتانة (كانت غائبة فتفقد المكالمات الواردة بصمت):
 * - إعادة اتصال تلقائية بتراجع أُسّي (1s→30s) بعد أي فشل/إغلاق.
 * - ping كل 25ث يمنع قطع الاتصال الخامل ويكشف الموت الشبح مبكراً.
 * - إعادة المحاولة عند غياب الـtoken (سباق تحديث الجلسة) بدل التجاهل الصامت.
 */
class PstnEventSocket(
    private val tokens: TokenStore,
    private val onEnvelope: (PstnWsEnvelope) -> Unit = {},
    private val onState: (Boolean) -> Unit = {}
) {
    private val client: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(tokens.context)
        .newBuilder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    @Volatile private var active = false
    @Volatile private var manuallyClosed = false
    private val connecting = AtomicBoolean(false)
    private var backoffMs = INITIAL_BACKOFF_MS
    @Volatile private var lastFailureWasAuth = false

    fun connect() {
        manuallyClosed = false
        active = true
        openSocket()
    }

    private fun openSocket() {
        if (!active || manuallyClosed) return
        if (!connecting.compareAndSet(false, true)) return
        // سباق تحديث الجلسة: token غير جاهز الآن → أعد المحاولة قريباً بدل التجاهل.
        val token = tokens.accessToken
        if (token.isNullOrBlank()) {
            connecting.set(false)
            scheduleReconnect()
            return
        }
        val wsBase = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        val request = Request.Builder().url(wsBase.trimEnd('/') + "/ws/pstn")
            .header("Authorization", "Bearer $token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connecting.set(false)
                backoffMs = INITIAL_BACKOFF_MS
                lastFailureWasAuth = false
                onState(true)
                // حارس: أغلق الاتصال إن لم يصل منه شيء طويلاً (pingInterval يتكفل بذلك فعلياً).
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { PstnEventEnvelopeCodec.decode(text) }.onSuccess(onEnvelope)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connecting.set(false)
                onState(false)
                scheduleReconnect()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connecting.set(false)
                onState(false)
                if (response?.code == 401) {
                    Log.w(TAG, "PSTN WS 401 — will try token refresh before reconnect")
                    lastFailureWasAuth = true
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!active || manuallyClosed) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (lastFailureWasAuth) {
                lastFailureWasAuth = false
                val refreshed = refreshTokenIfNeeded()
                if (refreshed) backoffMs = INITIAL_BACKOFF_MS
            }
            openSocket()
        }
    }

    private suspend fun tryRefresh(): Boolean {
        val refresh = tokens.refreshToken ?: return false
        return runCatching {
            when (val result = AuthApi(tokens.context).refresh(refresh)) {
                is ApiResult.Success -> {
                    tokens.updateTokens(result.value)
                    Log.i(TAG, "PSTN WS token refreshed")
                    true
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "PSTN WS refresh failed: ${result.message}")
                    false
                }
            }
        }.getOrDefault(false)
    }

    private suspend fun refreshTokenIfNeeded(): Boolean {
        return tryRefresh()
    }

    fun disconnect() {
        manuallyClosed = true
        active = false
        reconnectJob?.cancel()
        reconnectJob = null
        runCatching { socket?.close(1000, "app") }
        socket = null
        onState(false)
    }

    fun isActive() = active

    /**
     * إرسال رسالة تحكم للخادم على نفس القناة (PSTN_ACCEPT / PSTN_REJECT).
     * يُرجع false إذا لم تكن الجلسة مفتوحة — يستدعيها المستدعئ لإعادة المحاولة.
     */
    fun sendControl(type: String, data: Map<String, String?>): Boolean {
        val ws = socket ?: return false
        if (!active) return false
        val payload = org.json.JSONObject().apply {
            put("type", type)
            put("data", org.json.JSONObject().apply {
                data.forEach { (k, v) -> if (v == null) put(k, org.json.JSONObject.NULL) else put(k, v) }
            })
        }.toString()
        return runCatching { ws.send(payload) }.getOrDefault(false)
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    private companion object {
        const val TAG = "PstnEventSocket"
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
