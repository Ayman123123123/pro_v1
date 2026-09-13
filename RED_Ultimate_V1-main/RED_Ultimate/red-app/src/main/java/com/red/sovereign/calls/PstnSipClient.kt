package com.red.sovereign.calls

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesRose
import com.red.sovereign.ui.theme.YounesVoid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 🌉 YOUNES PSTN SIP Client — المكالمة الحقيقية عبر DINSTAR.
 *
 * **الجزء الذي كان مفقوداً كلياً**: الخادم يوفر POST /api/pstn/bridge ببيانات
 * SIP/ICE جاهزة ([PstnBridgeApi])، وهنا يسجل التطبيق SIP عبر WebSocket مع
 * Asterisk (transport-ws :8089، نظير red-webrtc-client) ويرسل INVITE مع
 * ترويسة X-Red-Gw (توجيه دقيق لمنفذ الشريحة)، ثم يجسر صوت WebRTC (Opus) مع
 * ساق GSM — فتُسمع الرنين الحقيقي للناقل ويستمر الحديث بلا انقطاع.
 *
 * SIP مصغّر مكتوب يدوياً: REGISTER + INVITE + ACK + BYE + CANCEL مع
 * Digest Auth (RFC 2617، دعم qop=auth). لا مكتبات خارجية.
 */
// ══════════════════════════════ الحالة والـ Runtime ══════════════════════════════

sealed interface PstnCallState {
    data object Idle : PstnCallState
    data class Preparing(val number: String) : PstnCallState
    data class Ringing(val number: String) : PstnCallState
    data class Active(val number: String, val startedAt: Long) : PstnCallState
    data class Failed(val number: String, val message: String) : PstnCallState
    data class Ended(val number: String, val durationMs: Long) : PstnCallState
}

object PstnCallRuntime {
    var state: PstnCallState by mutableStateOf(PstnCallState.Idle)
    var muted: Boolean by mutableStateOf(false)
    var speaker: Boolean by mutableStateOf(true)
}

/** مدير مكالمة PSTN — يجمع REST bridge + عميل SIP + تحرير الحجز. */
object PstnCallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: PstnSipClient? = null

    fun start(context: Context, suppliedNumber: String) {
        if (PstnCallRuntime.state != PstnCallState.Idle &&
            PstnCallRuntime.state !is PstnCallState.Failed &&
            PstnCallRuntime.state !is PstnCallState.Ended
        ) return // مكالمة جارية — لا ازدواج
        val number = suppliedNumber.filter { it.isDigit() || it == '+' }
        PstnCallRuntime.state = PstnCallState.Preparing(number)
        val appContext = context.applicationContext
        scope.launch {
            when (val result = PstnBridgeApi(TokenStore(appContext)).bridge(number)) {
                is ApiResult.Success -> {
                    val sip = PstnSipClient(appContext, result.value)
                    client = sip
                    sip.call()
                }
                is ApiResult.Error -> {
                    PstnCallRuntime.state =
                        PstnCallState.Failed(number, PstnErrorLocalizer.arabic(result.message))
                }
            }
        }
    }

    fun hangup(context: Context) {
        val active = client
        val callId = active?.bridge?.callId
        active?.hangup()
        // تحرير حجز المنفذ في الخادم حتى لو مات عميل SIP
        val appContext = context.applicationContext
        scope.launch {
            runCatching { callId?.let { PstnBridgeApi(TokenStore(appContext)).hangup(it) } }
        }
    }

    fun mute(enabled: Boolean) {
        PstnCallRuntime.muted = enabled
        client?.setMuted(enabled)
    }

    fun setSpeaker(on: Boolean) {
        PstnCallRuntime.speaker = on
        client?.setSpeaker(on)
    }
}

// ══════════════════════════════ عميل SIP ══════════════════════════════

class PstnSipClient(private val appContext: Context, val bridge: PstnBridgeInfo) {

    companion object {
        private const val TAG = "RED.PstnSip"
        private const val RING_TIMEOUT_MS = 60_000L
        private const val SIP_USER = "red-webrtc-client"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val number = bridge.targetNumber.filter { it.isDigit() }
    private val sipHost: String = resolveSipHost()
    private val wsUrl: String = resolveWsUrl()

    private var ws: WebSocket? = null
    private var registered = false
    private var answered = false
    private var finished = false
    private var inviteSent = false
    private var cseq = 0
    private val callId = UUID.randomUUID().toString()
    private val registerCallId = UUID.randomUUID().toString()
    private val localTag = randomTag()
    private var remoteTag: String? = null
    private var branch = newBranch()
    private var wsRetried = false

    // ── WebRTC ──
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null

    // ══════════ دورة الحياة ══════════

    fun call() {
        PstnCallRuntime.state = PstnCallState.Preparing(bridge.targetNumber)
        connectWs()
    }

    fun hangup() {
        if (finished) return
        if (answered) {
            sendBye()
        } else if (inviteSent) {
            sendCancel()
        }
        finish(null)
    }

    fun setMuted(enabled: Boolean) {
        audioTrack?.setEnabled(!enabled)
    }

    fun setSpeaker(on: Boolean) {
        runCatching {
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = on
            }
        }
    }

    private fun finish(message: String?) {
        if (finished) return
        finished = true
        val prev = PstnCallRuntime.state
        PstnCallRuntime.state = when {
            message != null -> PstnCallState.Failed(bridge.targetNumber, message)
            prev is PstnCallState.Active ->
                PstnCallState.Ended(bridge.targetNumber, System.currentTimeMillis() - prev.startedAt)
            else -> PstnCallState.Ended(bridge.targetNumber, 0L)
        }
        runCatching { pc?.close() }
        audioSource?.dispose()
        runCatching { audioManager?.mode = AudioManager.MODE_NORMAL }
        scope.launch {
            delay(300)
            runCatching { factory?.dispose() }
            runCatching { ws?.close(1000, "bye") }
            scope.cancel()
        }
    }

    // ══════════ WebSocket ══════════

    private fun connectWs() {
        Log.i(TAG, "Connecting SIP WS → $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "SIP WS open — sending REGISTER")
                sendRegister()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { runCatching { handleMessage(text) } }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "SIP WS failure: ${t.message}")
                // wss على منفذ غير TLS → جرّب ws مرة واحدة
                if (!wsRetried && wsUrl.startsWith("wss")) {
                    wsRetried = true
                    val alt = wsUrl.replaceFirst("wss", "ws")
                    if (alt != wsUrl) {
                        Log.i(TAG, "Retrying with plain WS")
                        connectWsFallback(alt)
                        return
                    }
                }
                if (!finished) {
                    finish("انقطع الاتصال بمحرك المكالمات (${t.message ?: "NETWORK"})")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!finished && !answered) finish("أُغلق اتصال محرك المكالمات")
            }
        })
    }

    private fun connectWsFallback(url: String) {
        val request = Request.Builder().url(url).build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = sendRegister()
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { runCatching { handleMessage(text) } }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!finished) finish("تعذر الوصول لمحرك المكالمات (SIP)")
            }
        })
    }

    // ══════════ معالجة الرسائل الواردة ══════════

    private suspend fun handleMessage(text: String) {
        val (head, body) = splitSdp(text)
        val lines = head.split("\r\n", "\n").filter { it.isNotBlank() }
        val first = lines.firstOrNull() ?: return

        if (first.startsWith("SIP/2.0 ")) {
            val code = first.removePrefix("SIP/2.0 ").take(3).toIntOrNull() ?: return
            val reason = first.removePrefix("SIP/2.0 ").drop(3).trim()
            val headers = parseHeaders(lines)
            val cseqMethod = headers["cseq"]?.substringAfter(' ', "").orEmpty().uppercase()
            handleResponse(code, reason, headers, cseqMethod, body)
        } else if (first.startsWith("BYE")) {
            val headers = parseHeaders(lines)
            sendResponse(text, 200, "OK", headers)
            finish(null)
        } else if (first.startsWith("CANCEL")) {
            val headers = parseHeaders(lines)
            sendResponse(text, 200, "OK", headers)
        } else if (first.startsWith("OPTIONS")) {
            val headers = parseHeaders(lines)
            sendResponse(text, 200, "OK", headers)
        } else if (first.startsWith("ACK")) {
            // ACK للـ re-INVITE الوارد — لا شيء مطلوب
        } else if (first.startsWith("INVITE")) {
            // re-INVITE من Asterisk (نادراً — hold إلخ): نرد 200 بنفس SDP الحالي
            val headers = parseHeaders(lines)
            val sdp = pc?.localDescription?.description.orEmpty()
            sendResponse(
                text, 200, "OK", headers,
                contentType = "application/sdp", body = sdp
            )
        }
    }

    private suspend fun handleResponse(
        code: Int,
        reason: String,
        headers: Map<String, String>,
        cseqMethod: String,
        body: String
    ) {
        when {
            // ── REGISTER ──
            cseqMethod == "REGISTER" -> {
                when (code) {
                    401, 407 -> {
                        val challenge = headers["www-authenticate"] ?: headers["proxy-authenticate"]
                        if (challenge == null) {
                            finish("رفض محرك المكالمات التسجيل ($reason)")
                            return
                        }
                        sendRegister(authChallenge = challenge)
                    }
                    in 200..299 -> {
                        registered = true
                        Log.i(TAG, "SIP registered — sending INVITE to $number")
                        setupWebRtc()
                    }
                    else -> finish("فشل تسجيل SIP ($code $reason)")
                }
            }

            // ── INVITE ──
            cseqMethod == "INVITE" -> {
                remoteTag = headers["to"]?.substringAfter("tag=")?.takeIf { it.isNotBlank() }
                when {
                    code == 100 -> Unit
                    code == 180 || code == 183 -> {
                        if (PstnCallRuntime.state !is PstnCallState.Active) {
                            PstnCallRuntime.state = PstnCallState.Ringing(bridge.targetNumber)
                        }
                        if (code == 183 && body.contains("v=0")) {
                            setRemoteSdp(body)
                        }
                    }
                    code in 200..299 -> {
                        if (body.contains("v=0")) setRemoteSdp(body)
                        sendAck(headers)
                        answered = true
                        PstnCallRuntime.state =
                            PstnCallState.Active(bridge.targetNumber, System.currentTimeMillis())
                        setSpeaker(PstnCallRuntime.speaker)
                    }
                    code == 401 || code == 407 -> {
                        val challenge = headers["www-authenticate"] ?: headers["proxy-authenticate"]
                        if (challenge == null) {
                            finish("رفض محرك المكالمات المكالمة ($reason)")
                        } else {
                            sendInvite(authChallenge = challenge)
                        }
                    }
                    code == 486 || code == 600 -> finish("الرقم مشغول")
                    code == 487 -> finish(null) // أُلغيت (نهينا نحن)
                    code in 400..699 -> finish(failureArabic(code, reason))
                }
            }
        }
    }

    private fun failureArabic(code: Int, reason: String): String = when (code) {
        403 -> "مرفوض — تحقق من تفعيل الخدمة وربط الشريحة"
        404 -> "الرقم غير موجود أو الوجهة مرفوضة"
        488 -> "ترميز الصوت غير مدعوم على البوابة"
        500, 502, 503 -> "بوابة GSM غير جاهزة — حاول بعد لحظات"
        else -> "فشل المكالمة ($code $reason)"
    }

    // ══════════ WebRTC ══════════

    private fun setupWebRtc() {
        WebRtcBootstrap.ensure(appContext)
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val iceServers = mutableListOf<PeerConnection.IceServer>()
        bridge.iceServers.iceServers.forEach { server ->
            server.urls.forEach { url ->
                val b = PeerConnection.IceServer.builder(url)
                server.username?.let { b.setUsername(it) }
                server.credential?.let { b.setPassword(it) }
                iceServers.add(b.createIceServer())
            }
        }
        if (iceServers.isEmpty()) {
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }

        val egl = org.webrtc.EglBase.create()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        pc = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE: $state")
                if (state == PeerConnection.IceConnectionState.FAILED && !finished) {
                    finish("تعذر تأسيس مسار الصوت (ICE)")
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                // ICE مرن: Asterisk يتحقق بالاتصال عبر مرشحات SDP الأولية (actpass)
                if (state == PeerConnection.IceGatheringState.COMPLETE && !inviteSent) {
                    sendInvite()
                }
            }
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) = Unit
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                if (newState == PeerConnection.PeerConnectionState.FAILED && !finished) {
                    finish("تعذر تأسيس مسار الصوت")
                }
            }
        }) ?: run {
            finish("فشل تهيئة الصوت")
            return
        }

        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("pstn-audio", audioSource)?.apply { setEnabled(!PstnCallRuntime.muted) }
        audioTrack?.let { pc?.addTrack(it, listOf("pstn-stream")) }

        // أنشئ SDP العرض ثم أرسل INVITE
        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                pc?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "setLocalDescription OK — sending INVITE")
                        // إرسال INVITE هنا (أو عند اكتمال ICE — أيهما أسبق)
                        if (!inviteSent) sendInvite()
                    }
                    override fun onSetFailure(error: String) {
                        Log.w(TAG, "setLocalDescription failed: $error")
                        finish("فشل تجهيز الصوت: $error")
                    }
                    override fun onCreateSuccess(description: SessionDescription?) {
                        Log.w(TAG, "Unexpected onCreateSuccess on setLocalDescription observer — ignoring")
                    }
                    override fun onCreateFailure(error: String) {
                        Log.w(TAG, "setLocalDescription observer onCreateFailure: $error")
                        finish("فشل تجهيز الصوت: $error")
                    }
                }, description)
            }
            override fun onSetSuccess() {
                Log.d(TAG, "createOffer observer onSetSuccess — no-op")
            }
            override fun onCreateFailure(error: String) = finish("فشل إنشاء العرض الصوتي: $error")
            override fun onSetFailure(error: String) {
                Log.w(TAG, "createOffer observer onSetFailure: $error")
                finish("فشل إنشاء العرض الصوتي: $error")
            }
        }, MediaConstraints())
    }

    private fun setRemoteSdp(sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {
                Log.w(TAG, "setRemoteDescription failed: $error")
            }
        }, desc)
    }

    // ══════════ بناء رسائل SIP ══════════

    private fun sendRegister(authChallenge: String? = null) {
        cseq += 1
        val seq = cseq
        val via = "SIP/2.0/WS $sipHost;branch=$branch"
        val sb = StringBuilder()
        sb.append("REGISTER sip:$sipHost SIP/2.0\r\n")
        sb.append("Via: $via\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <$SIP_USER@$sipHost>;tag=$localTag\r\n")
        sb.append("To: <$SIP_USER@$sipHost>\r\n")
        sb.append("Call-ID: $registerCallId\r\n")
        sb.append("CSeq: $seq REGISTER\r\n")
        sb.append("Contact: <$SIP_USER@$sipHost;transport=ws>\r\n")
        sb.append("Expires: 300\r\n")
        if (authChallenge != null) {
            sb.append(digestHeader("REGISTER", "sip:$sipHost", authChallenge)).append("\r\n")
        }
        sb.append("Content-Length: 0\r\n\r\n")
        ws?.send(sb.toString())
    }

    private fun sendInvite(authChallenge: String? = null) {
        inviteSent = true
        if (authChallenge != null) cseq += 1
        branch = if (authChallenge != null) newBranch() else branch
        val sdp = pc?.localDescription?.description ?: run {
            finish("لا يوجد عرض صوتي — أعد المحاولة")
            return
        }
        val via = "SIP/2.0/WS $sipHost;branch=$branch"
        val sb = StringBuilder()
        sb.append("INVITE sip:$number@$sipHost SIP/2.0\r\n")
        sb.append("Via: $via\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <$SIP_USER@$sipHost>;tag=$localTag\r\n")
        sb.append("To: <sip:$number@$sipHost>\r\n")
        sb.append("Call-ID: $callId\r\n")
        sb.append("CSeq: ${cseq} INVITE\r\n")
        sb.append("Contact: <$SIP_USER@$sipHost;transport=ws>\r\n")
        // توجيه دقيق لمنفذ الشريحة (مثل dinstar-gw-192-168-11-1-port-7) — يقرؤه dialplan
        bridge.gateway?.takeIf { it.isNotBlank() }?.let {
            sb.append("X-Red-Gw: $it\r\n")
        }
        if (authChallenge != null) {
            sb.append(digestHeader("INVITE", "sip:$number@$sipHost", authChallenge)).append("\r\n")
        }
        sb.append("Content-Type: application/sdp\r\n")
        sb.append("Content-Length: ${sdp.toByteArray(Charsets.UTF_8).size}\r\n\r\n")
        sb.append(sdp)
        ws?.send(sb.toString())

        // مهلة الرنين — الهدف لم يرد خلال 60 ثانية
        scope.launch {
            delay(RING_TIMEOUT_MS)
            if (!answered && !finished) {
                sendCancel()
                finish("لم يرد الرقم خلال مهلة الرنين")
            }
        }
    }

    private fun sendAck(responseHeaders: Map<String, String>) {
        val toHeader = responseHeaders["to"] ?: "<sip:$number@$sipHost>"
        val sb = StringBuilder()
        sb.append("ACK sip:$number@$sipHost SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/WS $sipHost;branch=${newBranch()}\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <$SIP_USER@$sipHost>;tag=$localTag\r\n")
        sb.append("To: $toHeader\r\n")
        sb.append("Call-ID: $callId\r\n")
        sb.append("CSeq: ${cseq} ACK\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        ws?.send(sb.toString())
    }

    private fun sendBye() {
        val sb = StringBuilder()
        sb.append("BYE sip:$number@$sipHost SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/WS $sipHost;branch=${newBranch()}\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <$SIP_USER@$sipHost>;tag=$localTag\r\n")
        sb.append("To: <sip:$number@$sipHost>${remoteTag?.let { ";tag=$it" } ?: ""}\r\n")
        sb.append("Call-ID: $callId\r\n")
        sb.append("CSeq: ${cseq + 1} BYE\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        ws?.send(sb.toString())
    }

    private fun sendCancel() {
        val sb = StringBuilder()
        sb.append("CANCEL sip:$number@$sipHost SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/WS $sipHost;branch=$branch\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <$SIP_USER@$sipHost>;tag=$localTag\r\n")
        sb.append("To: <sip:$number@$sipHost>\r\n")
        sb.append("Call-ID: $callId\r\n")
        sb.append("CSeq: ${cseq} CANCEL\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        ws?.send(sb.toString())
    }

    private fun sendResponse(rawRequest: String, code: Int, reason: String, headers: Map<String, String>, contentType: String? = null, body: String = "") {
        val via = rawRequest.lineSequence().firstOrNull { it.startsWith("Via:") } ?: return
        val sb = StringBuilder()
        sb.append("SIP/2.0 $code $reason\r\n")
        sb.append(via).append("\r\n")
        sb.append("From: ${headers["from"] ?: ""}\r\n")
        var toHeader = headers["to"] ?: ""
        if (!toHeader.contains("tag=", ignoreCase = true)) {
            toHeader += ";tag=$localTag"
        }
        sb.append("To: $toHeader\r\n")
        sb.append("Call-ID: ${headers["call-id"] ?: ""}\r\n")
        sb.append("CSeq: ${headers["cseq"] ?: ""}\r\n")
        if (contentType != null) {
            sb.append("Content-Type: $contentType\r\n")
            sb.append("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n\r\n")
            sb.append(body)
        } else {
            sb.append("Content-Length: 0\r\n\r\n")
        }
        ws?.send(sb.toString())
    }

    // ══════════ Digest Auth ══════════

    private fun digestHeader(method: String, uri: String, challenge: String): String {
        val params = parseChallenge(challenge)
        val realm = params["realm"].orEmpty()
        val nonce = params["nonce"].orEmpty()
        val qopList = params["qop"]?.split(",")?.map { it.trim() }
        val qop = qopList?.firstOrNull { it.equals("auth", true) }
        val opaque = params["opaque"]
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val nc = "00000001"

        val ha1 = md5Hex("$SIP_USER:$realm:${bridge.sipPassword}")
        val ha2 = md5Hex("$method:$uri")
        val response = if (qop == null) {
            md5Hex("$ha1:$nonce:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        }

        val sb = StringBuilder("Authorization: Digest ")
        sb.append("username=\"$SIP_USER\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", ")
        if (qop != null) sb.append("qop=$qop, nc=$nc, cnonce=\"$cnonce\", ")
        sb.append("response=\"$response\", algorithm=MD5")
        opaque?.let { sb.append(", opaque=\"$it\"") }
        return sb.toString()
    }

    private fun parseChallenge(challenge: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val regex = Regex("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))")
        regex.findAll(challenge.removePrefix("Digest").removePrefix("digest")).forEach {
            val key = it.groupValues[1].lowercase()
            val value = it.groupValues[2].ifBlank { it.groupValues[3] }
            out[key] = value
        }
        return out
    }

    // ══════════ مساعدات ══════════

    private fun resolveSipHost(): String {
        val backendHost = runCatching { java.net.URI(com.red.sovereign.core.ServerEndpoint.url()).host }.getOrNull()
        return runCatching { java.net.URI(bridge.sipServer).host }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "localhost" && it != "127.0.0.1" }
            ?: backendHost
            ?: "127.0.0.1"
    }

    private fun resolveWsUrl(): String {
        val uri = runCatching { java.net.URI(bridge.sipServer) }.getOrNull()
        val host = resolveSipHost()
        val secure = uri?.scheme?.contains("s", ignoreCase = true) == true
        val port = uri?.port?.takeIf { it > 0 } ?: 8089
        val path = uri?.path?.takeIf { it.isNotBlank() && it != "/" } ?: "/ws"
        return "${if (secure) "wss" else "ws"}://$host:$port$path"
    }

    private fun splitSdp(text: String): Pair<String, String> {
        val idx = text.indexOf("\r\n\r\n").let { if (it >= 0) it else text.indexOf("\n\n") }
        return if (idx >= 0) text.substring(0, idx) to text.substring(idx).trim()
        else text to ""
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                out[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        return out
    }

    private fun newBranch(): String = "z9hG4bK" + UUID.randomUUID().toString().replace("-", "").take(24)
    private fun randomTag(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

// ══════════════════════════════ الواجهة (Overlay) ══════════════════════════════

/**
 * 📞 غلاف مكالمة DINSTAR الحية — يُعرض عبر [UnifiedCallOverlays].
 * يغطي: تجهيز، رنين الناقل الحقيقي، مكالمة نشطة بمؤقّت، كتم، سماعة، إنهاء.
 */
@Composable
fun PstnCallOverlay() {
    val state = PstnCallRuntime.state

    LaunchedEffect(state) {
        if (state is PstnCallState.Failed || state is PstnCallState.Ended) {
            delay(2800)
            PstnCallRuntime.state = PstnCallState.Idle
        }
    }

    when (state) {
        PstnCallState.Idle -> Unit
        is PstnCallState.Failed -> PstnPillOverlay(
            title = "فشلت المكالمة",
            subtitle = state.message,
            number = state.number,
            isError = true
        )
        is PstnCallState.Ended -> PstnPillOverlay(
            title = "انتهت المكالمة",
            subtitle = formatDuration(state.durationMs),
            number = state.number,
            isError = false
        )
        else -> PstnActiveOverlay(state)
    }
}

@Composable
private fun PstnActiveOverlay(state: PstnCallState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (title, _) = when (state) {
        is PstnCallState.Preparing -> "جارٍ الربط عبر DINSTAR" to "تجهيز المنفذ والجسر الصوتي…"
        is PstnCallState.Ringing -> "جارٍ الرنين على الشبكة" to "رنين الناقل الحقيقي — انتظر الرد"
        is PstnCallState.Active -> "مكالمة نشطة عبر DINSTAR" to ""
        else -> "مكالمة DINSTAR" to ""
    }
    // نبض المؤقّت للمكالمة النشطة — قراءة tick في التركيب تضمن إعادة الرسم كل ثانية.
    // المفتاح state (لا Unit): يُلغي LaunchedEffect تلقائياً عند تغيّر الحالة أو مغادرة التركيب،
    // فيتوقف الـ while(true) بدل البقاء حياً في الخلفية بعد انتهاء المكالمة النشطة.
    var tick by androidx.compose.runtime.remember(state) { mutableStateOf(0) }
    val subtitle = if (state is PstnCallState.Active) {
        tick
        formatDuration(System.currentTimeMillis() - state.startedAt)
    } else when (state) {
        is PstnCallState.Preparing -> "تجهيز المنفذ والجسر الصوتي…"
        is PstnCallState.Ringing -> "رنين الناقل الحقيقي — انتظر الرد"
        else -> ""
    }
    if (state is PstnCallState.Active) {
        LaunchedEffect(state) { while (true) { delay(1000); tick++ } }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xF0101015), Color(0xF01A1A22)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.PhoneInTalk, null,
                tint = AqyalGold, modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(state.let { (it as? PstnCallState.Active)?.number ?: (it as? PstnCallState.Ringing)?.number ?: "" },
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(title, color = AqyalGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = .65f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(46.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                FilledIconButton(
                    onClick = { PstnCallManager.mute(!PstnCallRuntime.muted) },
                    modifier = Modifier.size(60.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (PstnCallRuntime.muted) AqyalGold else Color(0xFF232330)
                    )
                ) {
                    Icon(
                        if (PstnCallRuntime.muted) Icons.Default.MicOff else Icons.Default.Mic,
                        "كتم المايكروفون",
                        tint = if (PstnCallRuntime.muted) Color.Black else Color.White
                    )
                }
                FilledIconButton(
                    onClick = { PstnCallManager.setSpeaker(!PstnCallRuntime.speaker) },
                    modifier = Modifier.size(60.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (PstnCallRuntime.speaker) AqyalGold else Color(0xFF232330)
                    )
                ) {
                    Icon(
                        if (PstnCallRuntime.speaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        "السماعة",
                        tint = if (PstnCallRuntime.speaker) Color.Black else Color.White
                    )
                }
                FilledIconButton(
                    onClick = { PstnCallManager.hangup(context) },
                    modifier = Modifier.size(60.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = YounesRose)
                ) {
                    Icon(Icons.Default.CallEnd, "إنهاء المكالمة", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PstnPillOverlay(title: String, subtitle: String, number: String, isError: Boolean) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0171721)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Default.ErrorOutline else Icons.Default.Call, null,
                tint = if (isError) YounesRose else YounesEmerald
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$title · $number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
            }
            IconButton(onClick = { PstnCallRuntime.state = PstnCallState.Idle }) {
                Text("حسناً", color = AqyalGold, fontSize = 13.sp)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
