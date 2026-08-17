package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * محرك ضمان وصول المكالمة — Multi-Path Delivery Engine
 * 
 * المسارات:
 * 1. WebSocket مباشر (الأسرع) — انتظار RINGING ACK
 * 2. FCM Silent Push (إيقاظ الجهاز) — POST /api/calls/push-notify
 * 3. HTTP Webhook Fallback (ضمان التسليم) — POST /api/calls/pending
 * 
 * مع Trickle ICE retry و adaptive bitrate
 */
class CallDeliveryEngine(
    private val context: Context,
    private val tokens: TokenStore,
    private val signaling: CallSignalingClient,
    private val httpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── حالة التسليم ──
    @Volatile private var deliveryConfirmed = AtomicBoolean(false)
    @Volatile private var retryCount = AtomicInteger(0)
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<org.webrtc.IceCandidate>>()

    // ── معاملات ──
    companion object {
        const val RING_ACK_TIMEOUT_MS = 4_000L
        const val MAX_DELIVERY_RETRIES = 3
        const val BASE_RETRY_DELAY_MS = 1_000L
        const val HTTP_TIMEOUT_MS = 5_000L
    }

    interface Listener {
        fun onDeliveryConfirmed(callId: String, via: DeliveryPath)
        fun onDeliveryFailed(callId: String, reason: String)
        fun onDeliveryProgress(callId: String, path: DeliveryPath, attempt: Int)
    }

    enum class DeliveryPath { WEBSOCKET, FCM_PUSH, HTTP_WEBHOOK, UNKNOWN }

    // ── الدالة الرئيسية: إرسال المكالمة بضمان وصول متعدد المسارات ──
    fun deliverCallOffer(
        callSignal: CallSignal,
        targetRedId: String,
        listener: Listener
    ) {
        deliveryConfirmed.set(false)
        retryCount.set(0)
        val callId = callSignal.callId ?: return

        scope.launch {
            // === المسار 1: WebSocket الحالي (الأسرع) ===
            listener.onDeliveryProgress(callId, DeliveryPath.WEBSOCKET, 1)
            try {
                signaling.send(callSignal)
                Log.d("CallDeliveryEngine", "[$callId] Offer sent via WebSocket")
            } catch (e: Exception) {
                Log.w("CallDeliveryEngine", "[$callId] WebSocket send failed: ${e.message}")
            }

            // انتظر تأكيد "ringing" (يأتي عبر onSignal → RINGING)
            if (awaitDeliveryAck(RING_ACK_TIMEOUT_MS)) {
                listener.onDeliveryConfirmed(callId, DeliveryPath.WEBSOCKET)
                return@launch
            }

            // === المسار 2: FCM Silent Push (Wake-up) ===
            listener.onDeliveryProgress(callId, DeliveryPath.FCM_PUSH, 2)
            val pushSent = sendFcmPush(callId, targetRedId, callSignal.mode, callSignal.payload["sdp"].orEmpty())
            if (pushSent) {
                Log.d("CallDeliveryEngine", "[$callId] FCM push dispatched")
                if (awaitDeliveryAck(RING_ACK_TIMEOUT_MS)) {
                    listener.onDeliveryConfirmed(callId, DeliveryPath.FCM_PUSH)
                    return@launch
                }
            }

            // === المسار 3: HTTP Webhook Fallback ===
            repeat(MAX_DELIVERY_RETRIES) { attempt ->
                if (deliveryConfirmed.get()) return@repeat
                listener.onDeliveryProgress(callId, DeliveryPath.HTTP_WEBHOOK, attempt + 1)
                val webhookSent = sendHttpWebhook(callSignal, targetRedId)
                if (webhookSent) {
                    Log.d("CallDeliveryEngine", "[$callId] HTTP webhook sent (attempt ${attempt + 1})")
                    if (awaitDeliveryAck(RING_ACK_TIMEOUT_MS)) {
                        listener.onDeliveryConfirmed(callId, DeliveryPath.HTTP_WEBHOOK)
                        return@launch
                    }
                }
                // Exponential backoff
                val delayMs = BASE_RETRY_DELAY_MS * (1L shl attempt.coerceAtMost(3))
                delay(delayMs)
            }

            if (!deliveryConfirmed.get()) {
                Log.e("CallDeliveryEngine", "[$callId] All delivery paths failed after ${MAX_DELIVERY_RETRIES} retries")
                listener.onDeliveryFailed(callId, "تعذر الوصول إلى المستلم عبر كل المسارات")
            }
        }
    }

    fun onDeliveryAckReceived() {
        deliveryConfirmed.set(true)
    }

    private suspend fun awaitDeliveryAck(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (deliveryConfirmed.get()) return true
            delay(100)
        }
        return deliveryConfirmed.get()
    }

    // ── FCM Silent Push ──
    private suspend fun sendFcmPush(callId: String, targetRedId: String, mode: String, offerSdp: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val callerId = tokens.redId ?: return@runCatching false
                val body = json.encodeToString(
                    PushNotifyRequest(
                        callId = callId,
                        targetRedId = targetRedId,
                        callerId = callerId,
                        mode = mode,
                        offerSdp = offerSdp
                    )
                )
                val request = Request.Builder()
                    .url("${ServerEndpoint.url()}/api/calls/push-notify")
                    .header("Authorization", "Bearer ${tokens.accessToken.orEmpty()}")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful
                response.close()
                success
            }.getOrElse { e ->
                Log.w("CallDeliveryEngine", "FCM push request failed: ${e.message}")
                false
            }
        }
    }

    // ── HTTP Webhook Fallback ──
    private suspend fun sendHttpWebhook(signal: CallSignal, targetRedId: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(
                    PendingCallRequest(
                        callId = signal.callId.orEmpty(),
                        targetRedId = targetRedId,
                        callerId = tokens.redId.orEmpty(),
                        mode = signal.mode,
                        offerSdp = signal.payload["sdp"].orEmpty(),
                        ttlSeconds = (CallRingPolicy.UNANSWERED_TIMEOUT_MS / 1000).toInt()
                    )
                )
                val request = Request.Builder()
                    .url("${ServerEndpoint.url()}/api/calls/pending")
                    .header("Authorization", "Bearer ${tokens.accessToken.orEmpty()}")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful
                response.close()
                success
            }.getOrElse { e ->
                Log.w("CallDeliveryEngine", "HTTP webhook failed: ${e.message}")
                false
            }
        }
    }

    // ── Trickle ICE Manager ──
    fun onLocalIceCandidate(callId: String, candidate: org.webrtc.IceCandidate, targetRedId: String) {
        pendingCandidates.getOrPut(callId) { mutableListOf() }.add(candidate)
        val signal = CallSignal(
            callId = callId,
            targetUserId = targetRedId,
            type = CallSignal.ICE,
            payload = mapOf(
                "sdpMid" to candidate.sdpMid.orEmpty(),
                "sdpMLineIndex" to candidate.sdpMLineIndex.toString(),
                "candidate" to candidate.sdp
            )
        )
        signaling.send(signal)
    }

    fun retransmitIceCandidates(callId: String, targetRedId: String) {
        val candidates = pendingCandidates[callId] ?: return
        Log.d("CallDeliveryEngine", "[$callId] Retransmitting ${candidates.size} ICE candidates after restart")
        candidates.forEach { candidate ->
            signaling.send(
                CallSignal(
                    callId = callId,
                    targetUserId = targetRedId,
                    type = CallSignal.ICE,
                    payload = mapOf(
                        "sdpMid" to candidate.sdpMid.orEmpty(),
                        "sdpMLineIndex" to candidate.sdpMLineIndex.toString(),
                        "candidate" to candidate.sdp
                    )
                )
            )
        }
    }

    fun clearCandidates(callId: String) {
        pendingCandidates.remove(callId)
    }

    fun destroy() {
        scope.cancel()
        pendingCandidates.clear()
    }

    // ── DTOs ──
    @Serializable
    private data class PushNotifyRequest(
        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int? = null
    )

    @Serializable
    private data class PendingCallRequest(
        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int
    )
}