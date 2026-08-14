package com.red.sovereign.features.calls.signaling

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// ─── Signal Types ──────────────────────────────────────────────────────────

enum class SignalType {
    OFFER, ANSWER, ICE, HOLD, RESUME, END, REJECT,
    CONFERENCE_INVITE, LIVE_INVITE, RENEGOTIATE,
    // Incoming from server
    RINGING_PUSH_SENT, ACK, CANCELLED, UNKNOWN
}

data class CallSignal(
    val callId: String?,
    val sourceUserId: String,
    val targetUserId: String,
    val type: SignalType,
    val mode: String,            // VOICE, VIDEO, GROUP, LIVE, SPACE
    val payload: Map<String, Any?> = emptyMap()
)

data class OutgoingSignal(
    val callId: String? = null,
    val targetUserId: String,
    val type: String,
    val mode: String = "VOICE",
    val payload: Map<String, Any?> = emptyMap()
)

// ─── CallSignalingClient ──────────────────────────────────────────────────

/**
 * RED WebRTC Signaling Client.
 *
 * يفتح اتصال WebSocket واحد دائم مع `/ws/call` ويتولى:
 * - إرسال: OFFER, ANSWER, ICE, HOLD, RESUME, END, REJECT
 * - استقبال: جميع الإشارات الواردة وبثّها عبر [incoming]
 * - إعادة الاتصال التلقائية مع backoff أسي (1s → 2s → 4s → max 30s)
 */
@Singleton
class CallSignalingClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallSignaling"
        private const val PREFS = "red_sovereign_identity"
        private const val PATH = "/ws/call"
        private val RECONNECT_DELAYS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
    }

    // Incoming signal stream — collect in ViewModels
    private val _incoming = MutableSharedFlow<CallSignal>(extraBufferCapacity = 32)
    val incoming: SharedFlow<CallSignal> = _incoming

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connected = AtomicBoolean(false)

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val authToken: String get() = prefs.getString("AUTH_TOKEN", "") ?: ""
    private val serverHost: String get() {
        val host = prefs.getString("SERVER_HOST", "wss://red.sovereign.local") ?: "wss://red.sovereign.local"
        return host.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout — persistent WS
            .build()
    }

    private var ws: WebSocket? = null
    private var reconnectAttempt = 0

    // ── Connection Management ─────────────────────────────────────────

    fun connect() {
        if (connected.get()) return
        doConnect()
    }

    fun disconnect() {
        reconnectAttempt = 0
        ws?.close(1000, "User disconnect")
        ws = null
        connected.set(false)
    }

    private fun doConnect() {
        val token = authToken
        if (token.isBlank()) return   // Not logged in yet

        val url = "$serverHost$PATH"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                reconnectAttempt = 0
                android.util.Log.i(TAG, "Signaling WS connected to $url")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAndEmit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                parseAndEmit(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                android.util.Log.w(TAG, "Signaling WS closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                android.util.Log.e(TAG, "Signaling WS error: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        val delay = RECONNECT_DELAYS.getOrElse(reconnectAttempt) { RECONNECT_DELAYS.last() }
        reconnectAttempt++
        scope.launch {
            delay(delay)
            android.util.Log.i(TAG, "Reconnecting signaling WS (attempt $reconnectAttempt)...")
            doConnect()
        }
    }

    // ── Sending Signals ───────────────────────────────────────────────

    fun sendOffer(targetUserId: String, callId: String? = null, mode: String = "VOICE", sdp: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "OFFER", mode, mapOf("sdp" to sdp)))

    fun sendAnswer(targetUserId: String, callId: String, mode: String = "VOICE", sdp: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "ANSWER", mode, mapOf("sdp" to sdp)))

    fun sendIceCandidate(targetUserId: String, callId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int): Boolean =
        send(OutgoingSignal(callId, targetUserId, "ICE", "VOICE", mapOf(
            "candidate" to candidate,
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex
        )))

    fun sendReject(targetUserId: String, callId: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "REJECT"))

    fun sendEnd(targetUserId: String, callId: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "END"))

    fun sendHold(targetUserId: String, callId: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "HOLD"))

    fun sendResume(targetUserId: String, callId: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "RESUME"))

    fun sendRenegotiate(targetUserId: String, callId: String, sdp: String): Boolean =
        send(OutgoingSignal(callId, targetUserId, "RENEGOTIATE", payload = mapOf("sdp" to sdp)))

    private fun send(signal: OutgoingSignal): Boolean {
        val socket = ws ?: return false
        val json = gson.toJson(signal)
        return socket.send(json)
    }

    // ── Parsing ───────────────────────────────────────────────────────

    private fun parseAndEmit(raw: String) {
        try {
            val json = JSONObject(raw)
            val typeStr = json.optString("type", "UNKNOWN").uppercase()
            val type = try { SignalType.valueOf(typeStr) } catch (_: Exception) { SignalType.UNKNOWN }

            val payloadMap: Map<String, Any?> = try {
                val payloadJson = json.optJSONObject("payload")
                if (payloadJson != null) {
                    val type2 = object : TypeToken<Map<String, Any?>>() {}.type
                    gson.fromJson(payloadJson.toString(), type2)
                } else emptyMap()
            } catch (_: Exception) { emptyMap() }

            val signal = CallSignal(
                callId = json.optString("callId").ifBlank { null },
                sourceUserId = json.optString("sourceUserId", ""),
                targetUserId = json.optString("targetUserId", ""),
                type = type,
                mode = json.optString("mode", "VOICE"),
                payload = payloadMap
            )
            scope.launch { _incoming.emit(signal) }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse signal: ${e.message}")
        }
    }

    fun isConnected(): Boolean = connected.get()
}
