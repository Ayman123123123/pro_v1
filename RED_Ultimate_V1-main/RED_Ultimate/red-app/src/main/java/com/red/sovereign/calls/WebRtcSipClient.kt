package com.red.sovereign.calls

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.security.MessageDigest
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Minimal SIP-over-WebSocket client for Asterisk WSS.
 *
 * Handles: REGISTER → INVITE → ACK → BYE with MD5 digest auth.
 * The SDP offer/answer exchange happens through the PeerConnection,
 * and this client relays the SIP signaling over WebSocket.
 *
 * This is a purpose-built client for the RED Sovereign PSTN bridge.
 * It does NOT implement the full SIP protocol — only the subset needed
 * for outbound PSTN calls via Asterisk.
 */
class WebRtcSipClient(
    private val context: Context,
    private val peerConnection: PeerConnection,
    private val events: Events
) {
    interface Events {
        fun onRegistered()
        fun onInviteSent()
        fun onAnswered()
        fun onBye(cause: String?)
        fun onIncomingInvite(sdp: String, fromNumber: String)
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    private var callId = UUID.randomUUID().toString()
    private var fromTag = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
    private var toTag: String? = null
    private var branch: String = generateBranch()
    private var cseq = 1
    private var sipServer = ""
    private var username = ""
    private var password = ""
    private var localSdp: String? = null
    private var remoteSdp: String? = null
    private var inviteTarget: String? = null
    private var inviteHeaders: Map<String, String> = emptyMap()
    private var pendingNonce: String? = null
    private var pendingRealm: String? = null
    /** تحدّي RFC 3261 الحالي — عند توفره تُبنى الترويسة بـ qop=auth (يتطلبه Asterisk). */
    private var authChallenge: SipDigestAuth.Challenge? = null
    private var nonceCount = 0
    private var lastIncomingRequest: String? = null
    var lastIncomingSdp: String? = null
        private set
    private var uasToTag: String? = null
    private var disposed = false

    // ── Multi-candidate WSS failover ─────────────────────────────────────
    // التطبيق قد يصل إلى Asterisk عبر عدة عناوين (LAN مباشر / نطاق عام عبر
    // nginx). تُجرَّب العناوين تسلسلياً مع مهلة اتصال لكل عنوان، ويُكمل
    // التسجيل (REGISTER) عند نجاح أي منها.
    private var pendingCandidates: List<String>? = null
    private var candidateIndex = 0
    private var connectTimer: Timer? = null
    private val attemptGuard = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        private const val SIP_CONNECT_TIMEOUT_MS = 12_000L
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (attemptGuard.getAndSet(false)) connectTimer?.cancel()
            sendRegister()
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleSipMessage(text)
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleSipMessage(bytes.utf8())
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            if (disposed || webSocket !== ws) return
            // الخادم أغلق الاتصال قبل اكتمال التسجيل — جرب العنوان التالي.
            if (attemptGuard.getAndSet(false)) {
                connectTimer?.cancel()
                val candidates = pendingCandidates
                if (candidates != null && candidateIndex + 1 < candidates.size) {
                    tryConnect(candidates, candidateIndex + 1)
                } else {
                    events.onError("SIP_CONNECTION_CLOSED: $reason")
                }
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (disposed || webSocket !== ws) return
            if (attemptGuard.getAndSet(false)) {
                connectTimer?.cancel()
                val candidates = pendingCandidates
                if (candidates != null && candidateIndex + 1 < candidates.size) {
                    tryConnect(candidates, candidateIndex + 1)
                } else {
                    events.onError("SIP_WEBSOCKET_ERROR: ${t.message}")
                }
            }
        }
    }

    fun register(sipServer: String, username: String, password: String) {
        register(listOf(sipServer), username, password)
    }

    /**
     * سجل لدى أول عنوان WSS ينجح الاتصال به (تسلسلياً مع مهلة).
     * عند فشل جميع العناوين يُبعث onError("SIP_REGISTER_FAILED_ALL").
     */
    fun register(candidates: List<String>, username: String, password: String) {
        this.username = username
        this.password = password
        pendingCandidates = candidates
        tryConnect(candidates, 0)
    }

    private fun tryConnect(candidates: List<String>, index: Int) {
        if (disposed) return
        if (index >= candidates.size) {
            events.onError("SIP_REGISTER_FAILED_ALL")
            return
        }
        sipServer = candidates[index]
        candidateIndex = index
        callId = UUID.randomUUID().toString()
        fromTag = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
        branch = generateBranch()

        attemptGuard.set(true)
        connectTimer?.cancel()
        connectTimer = Timer("sip-connect-timeout", false).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (attemptGuard.getAndSet(false)) {
                        runCatching { ws?.close(1000, "timeout") }
                        ws = null
                        tryConnect(candidates, candidateIndex + 1)
                    }
                }
            }, SIP_CONNECT_TIMEOUT_MS)
        }

        val request = Request.Builder()
            .url(sipServer)
            // حاسم: Asterisk يرفض الترقية (400) بدون Sec-WebSocket-Protocol: sip
            .addHeader("Sec-WebSocket-Protocol", "sip")
            .build()
        ws = client.newWebSocket(request, listener)
    }

    fun invite(targetNumber: String, customHeaders: Map<String, String> = emptyMap()) {
        // Generate SDP offer via PeerConnection
        val constraints = org.webrtc.MediaConstraints().apply {
            mandatory.add(org.webrtc.MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(org.webrtc.MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                localSdp = sdp.description
                peerConnection.setLocalDescription(object : org.webrtc.SdpObserver {
                    override fun onSetSuccess() {
                        sendInvite(targetNumber, customHeaders)
                    }
                    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                    override fun onSetFailure(error: String) {
                        events.onError("SET_LOCAL_SDP_FAILED: $error")
                    }
                    override fun onCreateFailure(error: String) = Unit
                }, sdp)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) {
                events.onError("CREATE_OFFER_FAILED: $error")
            }
            override fun onSetFailure(error: String) = Unit
        }, constraints)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        // Send ICE candidate via SIP INFO or re-INVITE
        val iceMsg = buildIceMessage(candidate)
        sendOrReport(iceMsg, "INFO-ICE")
    }

    fun bye() {
        val msg = buildBye()
        sendOrReport(msg, "BYE")
        events.onBye("LOCAL_HANGUP")
    }

    fun dispose() {
        disposed = true
        connectTimer?.cancel()
        connectTimer = null
        ws?.close(1000, "dispose")
        ws = null
    }

    // ─── UAS helpers for inbound INVITE ─────────────────────────────────

    /**
     * Store last inbound INVITE for later 200 OK generation.
     * Exposed so PstnWebRtcManager can persist invite before SDP creation.
     */
    fun storeLastInvite(request: String) {
        lastIncomingRequest = request
        val sdpMatch = Regex("\\r\\n\\r\\n(.*)", RegexOption.DOT_MATCHES_ALL).find(request)
        lastIncomingSdp = sdpMatch?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        if (lastIncomingSdp == null) {
            val idx = request.indexOf("\r\n\r\n")
            if (idx >= 0) lastIncomingSdp = request.substring(idx + 4).trim().ifEmpty { null }
        }
    }

    /**
     * UAS 200 OK with SDP answer — RFC 3261 §13.3.1.4.
     * Must be used for inbound INVITE, not the UAC sendAck().
     * Extracts Via/From/To/Call-ID/CSeq from stored lastIncomingRequest,
     * generates a To tag if missing, and sends SIP/2.0 200 OK with SDP.
     * Returns true if the WebSocket send was attempted.
     */
    fun send200OkWithSdp(localSdp: String): Boolean {
        val request = lastIncomingRequest ?: return false
        val via = extractHeader(request, "Via") ?: return false
        val from = extractHeader(request, "From") ?: return false
        val toRaw = extractHeader(request, "To") ?: return false
        val callIdHeader = extractHeader(request, "Call-ID") ?: return false
        val cseqHeader = extractHeader(request, "CSeq") ?: return false
        val host = buildSipHost()
        val tag = uasToTag ?: Random.nextBytes(8).joinToString("") { "%02x".format(it) }.also { uasToTag = it }
        val toHeader = if (toRaw.contains("tag=", ignoreCase = true)) toRaw else "$toRaw;tag=$tag"
        val sdpBytes = localSdp.toByteArray()
        val msg = buildString {
            append("SIP/2.0 200 OK\r\n")
            append("$via\r\n")
            append("$from\r\n")
            append("$toHeader\r\n")
            append("$callIdHeader\r\n")
            append("$cseqHeader\r\n")
            append("Contact: <sip:$username@$host;transport=ws>\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: ${sdpBytes.size}\r\n")
            append("\r\n")
            append(localSdp)
        }
        val sent = sendOrReport(msg, "200-OK+SDP")
        // Update internal callId to match dialog so BYE uses correct ID
        runCatching {
            val extracted = callIdHeader.substringAfter(":", "").trim()
            if (extracted.isNotBlank()) callId = extracted
        }
        lastIncomingSdp = localSdp
        return sent
    }


    /**
     * كل إرسال يمرّ من هنا.
     *
     * قبل هذا التوحيد كانت `ws?.send(msg)` تُستدعى قيمتُها مُهمَلة في ثمانية
     * مواضع. `WebSocket.send()` تُرجع `false` عند إغلاق المقبس أو امتلاء
     * الطابور (سقف OkHttp 16MB)، وتُرجع `null` عبر `?.` إذا لم تُفتح المقبس
     * أصلًا — أي أن `REGISTER` لم يغادر الجهاز قطّ، ولا `onFailure` تُستدعى،
     * ولا شيء يصل الواجهة. هذا حرفيًا عرَض «SIP_SEND_FAILED / رسالة فارغة».
     * الآن: لا إرسال بصمت — إمّا نجاح أو `onError` باسم المعاملة وحالتها.
     */
    private fun sendOrReport(payload: String, label: String): Boolean {
        val socket = ws
        if (socket == null) {
            events.onError("SIP_SEND_FAILED: $label — المقبس غير مفتوح (sipServer=${if (sipServer.isEmpty()) "<unset>" else sipServer}, disposed=$disposed)")
            android.util.Log.e("SipClient", "send($label) refused: no open WebSocket")
            return false
        }
        return if (socket.send(payload)) {
            true
        } else {
            events.onError("SIP_SEND_FAILED: $label — رفض OkHttp الإرسال (المقبس أُغلق أو طابوره ممتلئ)")
            android.util.Log.e("SipClient", "send($label) returned false (closed/queue overflow)")
            false
        }
    }

    // ─── SIP Message Building ─────────────────────────────────────────

    private fun generateBranch(): String =
        "z9hG4bK${Random.nextBytes(16).joinToString("") { "%02x".format(it) }}"

    private fun generateBranchShort(): String =
        "z9hG4bK${Random.nextBytes(8).joinToString("") { "%02x".format(it) }}"

    private fun sipUri(user: String): String =
        "sip:$user@${sipServer.removePrefix("wss://").removePrefix("ws://").split(":").first().split("/").first()}"

    private fun buildSipHost(): String =
        sipServer.removePrefix("wss://").removePrefix("ws://").split(":").first().split("/").first()

    private fun buildFromUri(): String =
        "sip:${username}@${buildSipHost()};tag=$fromTag"

    private fun buildToUri(target: String): String {
        val number = target.removePrefix("+")
        return "sip:$number@${buildSipHost()}"
    }

    private fun sendRegister() {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val msg = buildString {
            append("REGISTER sip:$host SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <${sipUri(username)}>;tag=$fromTag\r\n")
            append("To: <${sipUri(username)}>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq REGISTER\r\n")
            append("Expires: 3600\r\n")
            append("Contact: <sip:${username}@${host};transport=ws>\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        sendOrReport(msg, "REGISTER")
    }

    private fun sendInvite(targetNumber: String, customHeaders: Map<String, String>) {
        inviteTarget = targetNumber
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = targetNumber.removePrefix("+")
        val sdp = localSdp ?: return

        val msg = buildString {
            append("INVITE sip:$number@$host SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:$number@$host>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq INVITE\r\n")
            append("Contact: <sip:${username}@${host};transport=ws>\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: ${sdp.toByteArray().size}\r\n")
            append("\r\n")
            append(sdp)
        }
        sendOrReport(msg, "INVITE")
    }

    /** إعادة إرسال INVITE مع Authorization بعد تحدّي 401 — بدون هذا يموت الاتصال. */
    private fun sendInviteWithAuth(targetNumber: String, nonce: String, realm: String) {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = targetNumber.removePrefix("+")
        val sdp = localSdp ?: return
        val uri = "sip:$number@$host"

        val msg = buildString {
            append("INVITE $uri SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:$number@$host>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq INVITE\r\n")
            append("Contact: <sip:${username}@${host};transport=ws>\r\n")
            append("Authorization: ${digestAuthorization("INVITE", uri)}\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: ${sdp.toByteArray().size}\r\n")
            append("\r\n")
            append(sdp)
        }
        sendOrReport(msg, "INVITE+auth")
    }

    private fun buildBye(): String {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = inviteTarget?.removePrefix("+") ?: ""
        val toUri = toTag?.let { ";tag=$it" } ?: ""
        // For UAS dialog, the remote tag is fromTag-like; if uasToTag exists, BYE To should carry remote's tag
        return buildString {
            append("BYE sip:${buildSipHost()} SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:${number}@$host>$toUri\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq BYE\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
    }

    private fun buildIceMessage(candidate: IceCandidate): String {
        val host = buildSipHost()
        branch = generateBranchShort()
        cseq++
        val info = "a=candidate:${candidate.sdpMLineIndex} ${candidate.sdpMid ?: "0"} ${candidate.sdp}"
        return buildString {
            append("INFO sip:$host SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:${username}@${host}>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq INFO\r\n")
            append("Content-Type: application/x-ice-info\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: ${info.toByteArray().size}\r\n")
            append("\r\n")
            append(info)
        }
    }

    // ─── SIP Message Handling ──────────────────────────────────────────

    private fun handleSipMessage(message: String) {
        val statusCode = parseStatusCode(message)
        val method = parseMethod(message)

        when {
            // 401 Unauthorized → resend with digest auth (REGISTER أو INVITE حسب السياق)
            statusCode == 401 -> {
                // حاول تحليل تحدٍّ Digest كامل (realm/nonce/qop/opaque/algorithm).
                val headerValue = extractHeader(message, "WWW-Authenticate")?.substringAfter(':', "")?.trim()
                val challenge = SipDigestAuth.parseChallenge(headerValue)
                if (challenge != null) {
                    authChallenge = challenge
                    nonceCount = 0
                    pendingNonce = challenge.nonce
                    pendingRealm = challenge.realm
                    val challengedMethod = parseCSeqMethod(message)
                    if (challengedMethod == "INVITE" && inviteTarget != null) {
                        sendInviteWithAuth(inviteTarget!!, challenge.nonce, challenge.realm)
                    } else {
                        sendRegisterWithAuth(challenge.nonce, challenge.realm)
                    }
                } else {
                    // fallback: رأس غير قياسي — استخدم MD5 الخام كما كان سابقاً
                    val nonce = parseHeader(message, "WWW-Authenticate", "nonce")
                    val realm = parseHeader(message, "WWW-Authenticate", "realm")
                    if (nonce != null && realm != null) {
                        pendingNonce = nonce
                        pendingRealm = realm
                        val challengedMethod = parseCSeqMethod(message)
                        if (challengedMethod == "INVITE" && inviteTarget != null) {
                            sendInviteWithAuth(inviteTarget!!, nonce, realm)
                        } else {
                            sendRegisterWithAuth(nonce, realm)
                        }
                    } else {
                        events.onError("SIP_AUTH_MISSING_CHALLENGE")
                    }
                }
            }

            // 200 OK to REGISTER
            statusCode == 200 && method.isEmpty() -> {
                val cseqHeader = parseCSeqMethod(message)
                if (cseqHeader == "REGISTER") {
                    events.onRegistered()
                } else if (cseqHeader == "INVITE") {
                    handle200OkToInvite(message)
                }
            }

            // 100 Trying, 180 Ringing → progress
            statusCode == 180 -> {
                events.onInviteSent()
            }

            // Incoming INVITE → incoming PSTN call via WebRTC
            method == "INVITE" -> {
                lastIncomingRequest = message
                val sdpMatch = Regex("\\r\\n\\r\\n(.*)", RegexOption.DOT_MATCHES_ALL).find(message)
                lastIncomingSdp = sdpMatch?.groupValues?.get(1)?.trim()?.ifEmpty { null }
                val fromHeader = message.lines().find { it.startsWith("From:", ignoreCase = true) }
                val fromNumber = fromHeader?.let {
                    Regex("<sip:([^@>]+)@").find(it)?.groupValues?.get(1)?.removePrefix("+") ?: "UNKNOWN"
                } ?: "UNKNOWN"
                if (lastIncomingSdp != null) {
                    events.onIncomingInvite(lastIncomingSdp!!, fromNumber)
                }
                // Send 180 Ringing
                sendResponse(180, "Ringing", message)
            }

            // ACK from UAC after our 200 OK (UAS flow) — just acknowledge
            method == "ACK" -> {
                // Asterisk ACK to our 200 OK; call is now confirmed bidirectional.
                // Nothing to send; audio already flows via WebRTC. Log for debugging.
                android.util.Log.i("SipClient", "ACK received for dialog $callId")
            }

            // BYE from remote
            method == "BYE" -> {
                sendResponse(200, "OK", message)
                events.onBye("REMOTE_HANGUP")
            }

            // SIP error
            statusCode in 400..699 -> {
                events.onError("SIP_ERROR_$statusCode")
            }
        }
    }

    private fun handle200OkToInvite(message: String) {
        toTag = parseToTag(message)
        val sdpMatch = Regex("\\r\\n\\r\\n(.*)", RegexOption.DOT_MATCHES_ALL).find(message)
        remoteSdp = sdpMatch?.groupValues?.get(1)

        // Set remote description on PeerConnection
        if (remoteSdp != null) {
            peerConnection.setRemoteDescription(object : org.webrtc.SdpObserver {
                override fun onSetSuccess() {
                    peerConnection.createAnswer(object : org.webrtc.SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            peerConnection.setLocalDescription(object : org.webrtc.SdpObserver {
                                override fun onSetSuccess() {
                                    sendAck()
                                    events.onAnswered()
                                }
                                override fun onSetFailure(error: String) {
                                    events.onError("SET_ANSWER_FAILED: $error")
                                }
                                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                                override fun onCreateFailure(error: String) = Unit
                            }, sdp)
                        }
                        override fun onSetSuccess() = Unit
                        override fun onCreateFailure(error: String) {
                            events.onError("CREATE_ANSWER_FAILED: $error")
                        }
                        override fun onSetFailure(error: String) = Unit
                    }, org.webrtc.MediaConstraints())
                }
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onCreateFailure(error: String) {
                    events.onError("SET_REMOTE_SDP_FAILED: $error")
                }
                override fun onSetFailure(error: String) = Unit
            }, SessionDescription(SessionDescription.Type.ANSWER, remoteSdp!!))
        }
    }

    private fun sendRegisterWithAuth(nonce: String, realm: String) {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val registerUri = sipUri(username)
        val msg = buildString {
            append("REGISTER sip:$host SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <${sipUri(username)}>;tag=$fromTag\r\n")
            append("To: <${sipUri(username)}>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq REGISTER\r\n")
            append("Expires: 3600\r\n")
            append("Contact: <sip:${username}@${host};transport=ws>\r\n")
            append("Authorization: ${digestAuthorization("REGISTER", registerUri)}\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        sendOrReport(msg, "REGISTER+auth")
    }

    internal fun sendAck() {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = inviteTarget?.removePrefix("+") ?: ""
        val toTagStr = toTag?.let { ";tag=$it" } ?: ""
        val msg = buildString {
            append("ACK sip:$number@$host SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:$number@$host>$toTagStr\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq ACK\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        sendOrReport(msg, "ACK")
    }

    /**
     * Reject an incoming INVITE with 486 Busy Here.
     * Uses the last stored incoming INVITE message (set in the INVITE handler).
     */
    fun rejectIncoming() {
        val request = lastIncomingRequest
        if (request != null) sendResponse(486, "Busy Here", request)
        lastIncomingRequest = null
        lastIncomingSdp = null
        uasToTag = null
    }

    private fun sendResponse(statusCode: Int, reason: String, request: String) {
        val host = buildSipHost()
        val via = extractHeader(request, "Via")
        val from = extractHeader(request, "From")
        val toRaw = extractHeader(request, "To")
        val callIdHeader = extractHeader(request, "Call-ID")
        val cseqHeader = extractHeader(request, "CSeq")
        // Generates To tag for provisional/final responses if missing (RFC 3261 8.2.6.2)
        val toHeader = if (toRaw != null && !toRaw.contains("tag=", ignoreCase = true) && statusCode != 408 && statusCode != 481) {
            val tag = uasToTag ?: Random.nextBytes(8).joinToString("") { "%02x".format(it) }.also { uasToTag = it }
            "$toRaw;tag=$tag"
        } else toRaw
        val msg = buildString {
            append("SIP/2.0 $statusCode $reason\r\n")
            if (via != null) append("$via\r\n")
            if (from != null) append("$from\r\n")
            if (toHeader != null) append("$toHeader\r\n")
            if (callIdHeader != null) append("$callIdHeader\r\n") else append("Call-ID: $callId\r\n")
            if (cseqHeader != null) append("$cseqHeader\r\n") else append("CSeq: $cseq INVITE\r\n")
            // Contact is required for dialog-forming responses (180/200)
            if (statusCode in 180..200) append("Contact: <sip:$username@$host;transport=ws>\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        sendOrReport(msg, "response")
    }

    // ─── SIP Parsing Utilities ──────────────────────────────────────────

    private fun parseStatusCode(message: String): Int {
        val firstLine = message.lines().firstOrNull() ?: return 0
        val match = Regex("SIP/2.0\\s+(\\d{3})").find(firstLine) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun parseMethod(message: String): String {
        val firstLine = message.lines().firstOrNull() ?: return ""
        val match = Regex("^(\\w+)\\s+sip:").find(firstLine.trim()) ?: return ""
        return match.groupValues[1]
    }

    private fun parseCSeqMethod(message: String): String {
        val line = message.lines().find { it.startsWith("CSeq:", ignoreCase = true) } ?: return ""
        return line.split("\\s+".toRegex()).lastOrNull() ?: ""
    }

    private fun parseHeader(message: String, headerName: String, param: String): String? {
        val line = message.lines().find {
            it.startsWith("$headerName:", ignoreCase = true) || it.startsWith("$headerName ", ignoreCase = true)
        } ?: return null
        val match = Regex("$param=\"([^\"]+)\"").find(line) ?: Regex("$param=([^,\\s]+)").find(line)
        return match?.groupValues?.get(1)
    }

    private fun parseToTag(message: String): String? {
        val to = message.lines().find { it.startsWith("To:", ignoreCase = true) } ?: return null
        val match = Regex("tag=([^;\\s]+)").find(to)
        return match?.groupValues?.get(1)
    }

    private fun extractHeader(message: String, headerName: String): String? {
        return message.lines().find {
            it.startsWith("$headerName:", ignoreCase = true) || it.startsWith("$headerName ", ignoreCase = true)
        }
    }

    /**
     * يبني ترويسة Authorization وفق RFC 3261 عبر SipDigestAuth (يدعم
     * qop=auth وSHA-256 الذي يتحداه Asterisk الحديث)، مع fallback إلى
     * MD5 الخام إذا لم يُفهم التحدي إطلاقاً.
     */
    private fun digestAuthorization(method: String, uri: String): String {
        val challenge = authChallenge
        if (challenge != null) {
            nonceCount += 1
            val cnonce = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
            val built = SipDigestAuth.buildAuthorization(method, uri, username, password, challenge, nonceCount, cnonce)
            if (built != null) return built.value
            // خوارزمية غير مدعومة → صفّر التحدي واستخدم المسار القديم بدل الفشل الصامت.
            authChallenge = null
        }
        // تحذير: لا يجوز إلصاق تجاوب MD5 مجرّدًا. المسار القديم كان ينتج
        // `Authorization: 6f1a…` فيردّ الطرف بـ 400 Bad Request — وكان ذلك يبدو
        // «تسجيلًا صامتًا يفشل». نبني ترويسة Digest كاملة ولو من الحقول الخام.
        val response = computeDigestAuth(method, uri, pendingRealm ?: "", username, password, pendingNonce ?: "")
        val fields = buildList {
            add("username=\"$username\"")
            if ((pendingRealm ?: "").isNotBlank()) add("realm=\"${pendingRealm}\"")
            add("nonce=\"${pendingNonce.orEmpty()}\"")
            add("uri=\"$uri\"")
            add("response=\"$response\"")
            add("algorithm=MD5")
        }
        return "Digest ${fields.joinToString(", ")}"
    }

    /**
     * Compute SIP Digest (MD5) authentication response.
     * HA1 = MD5(username:realm:password)
     * HA2 = MD5(method:uri)
     * response = MD5(HA1:nonce:HA2)
     */
    private fun computeDigestAuth(method: String, uri: String, realm: String, user: String, pass: String, nonce: String): String {
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        return md5("$ha1:$nonce:$ha2")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

