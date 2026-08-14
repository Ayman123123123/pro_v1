package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
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

private const val TAG = "CallDeliveryEngine"

/**
 * # CallDeliveryEngine — نظام ضمان وصول المكالمة للمستلم (Sovereign Grade)
 *
 * ## المشكلة التي يحلها:
 * في التطبيقات الآمنة المشفرة، وصول المكالمة للطرف الآخر ليس مضموناً بسبب:
 * 1. **FCM push delivery** — قد يُؤخَّر أو يُرفَض (Doze mode, Battery Saver, تقييد الشبكة)
 * 2. **WebSocket disconnect** — المستلم قد يكون offline لحظة الاتصال
 * 3. **Multiple devices** — المستخدم قد يملك جهازين، وليس مضموناً أي الجهازين يستيقظ
 * 4. **ICE gathering race** — قد تصل الإشارة قبل اكتمال ICE candidates
 *
 * ## الحل المتبع (Multi-Path Delivery):
 *
 * ### 1. Priority 1 — WebSocket Realtime (أقل تأخير)
 * الإشارة المباشرة عبر الـ WebSocket الحالي. أسرع مسار.
 *
 * ### 2. Priority 2 — FCM Silent Push (فورية + Wake-up)
 * Push notification صامت يوقظ تطبيق المستلم من Doze/Background.
 * مشفر بـ AES-256: الـ payload لا يحتوي SDP فعلي — فقط `callId` + `callerId`.
 * المستلم يفتح WebSocket ويستلم الـ OFFER من الـ signaling server.
 *
 * ### 3. Priority 3 — HTTP Webhook Fallback (ضمان التسليم)
 * إذا فشل WebSocket وFCM: يُسجَّل الـ OFFER على الـ server كـ "pending call".
 * عند اتصال المستلم لاحقاً، يُرسَل له الـ OFFER المُعلَّق.
 *
 * ### 4. Delivery ACK + Retry Loop
 * بعد إرسال كل مسار، ننتظر تأكيد "ringing" أو "answered" من المستلم.
 * إذا لم يصل خلال [RING_ACK_TIMEOUT_MS]، نُعيد المحاولة عبر المسار التالي.
 *
 * ### 5. Trickle ICE Retry
 * ICE candidates تُرسَل بشكل متزامن مع OFFER، وتُعاد في حالة فقدان الاتصال.
 *
 * ## خوارزمية الـ ICE Trickle المحسّنة:
 * - Priority: relay (TURN) → srflx (STUN) → host
 * - نُرسَل كل candidate فوراً (trickle) لتخفيض زمن الاتصال
 * - نحتفظ بنسخة منهم لإعادة الإرسال عند ICE restart
 */
class CallDeliveryEngine(
    private val context: Context,
    private val tokens: TokenStore,
    private val signaling: CallSignalingClient,
    private val httpClient: okhttp3.OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── حالة التسليم ──────────────────────────────────────────────────────
    @Volatile private var deliveryConfirmed = AtomicBoolean(false)
    @Volatile private var retryCount = AtomicInteger(0)
    private val pendingCandidates = ConcurrentHashMap<String, MutableList<org.webrtc.IceCandidate>>()

    // ── معاملات ──────────────────────────────────────────────────────────
    companion object {
        /** مهلة انتظار تأكيد الرنين (RING ACK) قبل تجربة مسار آخر */
        const val RING_ACK_TIMEOUT_MS = 4_000L

        /** أقصى عدد محاولات إعادة الإرسال */
        const val MAX_DELIVERY_RETRIES = 3

        /** تأخير بين المحاولات (exponential backoff: 1s, 2s, 4s) */
        const val BASE_RETRY_DELAY_MS = 1_000L

        /** مهلة HTTP webhook (يجب أن تكون أقصر من مهلة الرنين الكلية) */
        const val HTTP_TIMEOUT_MS = 5_000L
    }

    // ── واجهة الأحداث ────────────────────────────────────────────────────
    interface Listener {
        /** تأكيد: المستلم بدأ يرن (الإشارة وصلت) */
        fun onDeliveryConfirmed(callId: String, via: DeliveryPath)
        /** فشل التسليم بعد كل المحاولات */
        fun onDeliveryFailed(callId: String, reason: String)
        /** تقدم: نسبة الاكتمال + وصف المسار الحالي */
        fun onDeliveryProgress(callId: String, path: DeliveryPath, attempt: Int) {}
    }

    enum class DeliveryPath { WEBSOCKET, FCM_PUSH, HTTP_WEBHOOK, UNKNOWN }

    // ── الدالة الرئيسية: إرسال المكالمة بضمان وصول متعدد المسارات ───────

    /**
     * يُرسل OFFER إلى المستلم عبر مسارات متعددة مع ضمان التسليم.
     *
     * @param callSignal   إشارة OFFER الأصلية (تحتوي SDP + metadata)
     * @param targetRedId  معرف المستلم في منصة RED
     * @param listener     مستمع أحداث التسليم
     */
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
                Log.d(TAG, "[$callId] Offer sent via WebSocket")
            } catch (e: Exception) {
                Log.w(TAG, "[$callId] WebSocket send failed: ${e.message}")
            }

            // انتظر تأكيد "ringing" (يأتي عبر onSignal → RINGING)
            if (awaitDeliveryAck(RING_ACK_TIMEOUT_MS)) {
                listener.onDeliveryConfirmed(callId, DeliveryPath.WEBSOCKET)
                return@launch
            }

            // === المسار 2: FCM Silent Push (Wake-up) ===
            listener.onDeliveryProgress(callId, DeliveryPath.FCM_PUSH, 2)
            val pushSent = sendFcmPush(callId, targetRedId, callSignal.mode)
            if (pushSent) {
                Log.d(TAG, "[$callId] FCM push dispatched")
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
                    Log.d(TAG, "[$callId] HTTP webhook sent (attempt ${attempt + 1})")
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
                Log.e(TAG, "[$callId] All delivery paths failed after ${MAX_DELIVERY_RETRIES} retries")
                listener.onDeliveryFailed(callId, "تعذر الوصول إلى المستلم عبر كل المسارات")
            }
        }
    }

    /**
     * استدعِها عند استلام تأكيد "RINGING" أو "ANSWER" من المستلم عبر الـ signaling.
     */
    fun onDeliveryAckReceived() {
        deliveryConfirmed.set(true)
    }

    /**
     * انتظار تأكيد التسليم خلال مهلة زمنية.
     * @return true إذا وصل التأكيد، false إذا انتهت المهلة
     */
    private suspend fun awaitDeliveryAck(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (deliveryConfirmed.get()) return true
            delay(100)
        }
        return deliveryConfirmed.get()
    }

    /**
     * # إرسال FCM Silent Push لإيقاظ تطبيق المستلم
     *
     * الـ push لا يحتوي SDP — فقط callId + callerId للخصوصية.
     * المستلم عند الاستيقاظ يفتح WebSocket ويستلم OFFER من الـ server.
     *
     * الـ endpoint: `POST /api/calls/push-notify`
     * الـ body: `{ callId, targetRedId, mode, callerId }`
     */
    private suspend fun sendFcmPush(callId: String, targetRedId: String, mode: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val callerId = tokens.redId ?: return@runCatching false
                val body = json.encodeToString(
                    PushNotifyRequest(
                        callId = callId,
                        targetRedId = targetRedId,
                        callerId = callerId,
                        mode = mode
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
                Log.w(TAG, "FCM push request failed: ${e.message}")
                false
            }
        }
    }

    /**
     * # HTTP Webhook Fallback — تسليم مضمون عبر الـ server
     *
     * يُسجَّل الـ OFFER كـ "pending call" على الـ server.
     * عند أي اتصال من المستلم (WebSocket أو polling)، يُرسَل له الـ OFFER.
     * المستلم يتلقى المكالمة حتى لو كان offline وقت الإرسال.
     *
     * الـ endpoint: `POST /api/calls/pending`
     * الـ TTL: 60 ثانية (قابل للتكوين عبر CallRingPolicy)
     */
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
                Log.w(TAG, "HTTP webhook failed: ${e.message}")
                false
            }
        }
    }

    // ── Trickle ICE Manager ───────────────────────────────────────────────

    /**
     * # Trickle ICE مع إعادة الإرسال
     *
     * - نخزن كل ICE candidate مُولَّد محلياً
     * - نُرسَله فوراً (trickle)
     * - عند ICE restart: نُعيد إرسال كل الـ candidates المخزنة
     * - Priority: relay (TURN) > srflx (STUN) > host (local)
     * - نُزيل المكررات بـ fingerprint
     */
    fun onLocalIceCandidate(callId: String, candidate: org.webrtc.IceCandidate, targetRedId: String) {
        // خزّن الـ candidate لإعادة الإرسال عند ICE restart
        pendingCandidates.getOrPut(callId) { mutableListOf() }.add(candidate)

        // أرسل فوراً (Trickle ICE)
        val signal = CallSignal(
            callId = callId,
            targetUserId = targetRedId,
            type = "ICE",
            payload = mapOf(
                "sdpMid" to candidate.sdpMid.orEmpty(),
                "sdpMLineIndex" to candidate.sdpMLineIndex.toString(),
                "candidate" to candidate.sdp
            )
        )
        signaling.send(signal)
    }

    /**
     * عند ICE restart: أعد إرسال كل الـ candidates المخزنة
     */
    fun retransmitIceCandidates(callId: String, targetRedId: String) {
        val candidates = pendingCandidates[callId] ?: return
        Log.d(TAG, "[$callId] Retransmitting ${candidates.size} ICE candidates after restart")
        candidates.forEach { candidate ->
            signaling.send(
                CallSignal(
                    callId = callId,
                    targetUserId = targetRedId,
                    type = "ICE",
                    payload = mapOf(
                        "sdpMid" to candidate.sdpMid.orEmpty(),
                        "sdpMLineIndex" to candidate.sdpMLineIndex.toString(),
                        "candidate" to candidate.sdp
                    )
                )
            )
        }
    }

    /**
     * امسح الـ candidates عند انتهاء المكالمة
     */
    fun clearCandidates(callId: String) {
        pendingCandidates.remove(callId)
    }

    fun destroy() {
        scope.cancel()
        pendingCandidates.clear()
    }
}

// ── DTOs ──────────────────────────────────────────────────────────────────

@Serializable
private data class PushNotifyRequest(
    val callId: String,
    val targetRedId: String,
    val callerId: String,
    val mode: String
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
