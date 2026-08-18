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
    private var pendingNonce: String? = null
    private var pendingRealm: String? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
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
            events.onError("SIP_CONNECTION_CLOSED: $reason")
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            events.onError("SIP_WEBSOCKET_ERROR: ${t.message}")
        }
    }

    fun register(sipServer: String, username: String, password: String) {
        this.sipServer = sipServer
        this.username = username
        this.password = password
        callId = UUID.randomUUID().toString()
        fromTag = Random.nextBytes(8).joinToString("") { "%02x".format(it) }

        val request = Request.Builder()
            .url(sipServer)
            // حاسم: Asterisk يرفض الترقية (400) بدون Sec-WebSocket-Protocol: sip
            .addHeader("Sec-WebSocket-Protocol", "sip")
            .build()
        ws = client.newWebSocket(request, listener)
    }

    fun invite(targetNumber: String) {
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
                        sendInvite(targetNumber)
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
        ws?.send(iceMsg)
    }

    fun bye() {
        val msg = buildBye()
        ws?.send(msg)
        events.onBye("LOCAL_HANGUP")
    }

    fun dispose() {
        ws?.close(1000, "dispose")
        ws = null
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
        ws?.send(msg)
    }

    private fun sendInvite(targetNumber: String) {
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
        ws?.send(msg)
    }

    /** إعادة إرسال INVITE مع Authorization بعد تحدّي 401 — بدون هذا يموت الاتصال. */
    private fun sendInviteWithAuth(targetNumber: String, nonce: String, realm: String) {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = targetNumber.removePrefix("+")
        val sdp = localSdp ?: return
        val uri = "sip:$number@$host"
        val response = computeDigestAuth("INVITE", uri, realm, username, password, nonce)

        val msg = buildString {
            append("INVITE $uri SIP/2.0\r\n")
            append("Via: SIP/2.0/WSS $host;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:${username}@${host}>;tag=$fromTag\r\n")
            append("To: <sip:$number@$host>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq INVITE\r\n")
            append("Contact: <sip:${username}@${host};transport=ws>\r\n")
            append("Authorization: Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\", algorithm=MD5\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: ${sdp.toByteArray().size}\r\n")
            append("\r\n")
            append(sdp)
        }
        ws?.send(msg)
    }

    private fun buildBye(): String {
        branch = generateBranch()
        cseq++
        val host = buildSipHost()
        val number = inviteTarget?.removePrefix("+") ?: ""
        val toUri = toTag?.let { ";tag=$it" } ?: ""
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
                val sdpMatch = Regex("\\r\\n\\r\\n(.*)", RegexOption.DOT_MATCHES_ALL).find(message)
                val remoteSdp = sdpMatch?.groupValues?.get(1)
                val fromHeader = message.lines().find { it.startsWith("From:", ignoreCase = true) }
                val fromNumber = fromHeader?.let {
                    Regex("<sip:([^@>]+)@").find(it)?.groupValues?.get(1)?.removePrefix("+") ?: "UNKNOWN"
                } ?: "UNKNOWN"
                if (remoteSdp != null) {
                    events.onIncomingInvite(remoteSdp, fromNumber)
                }
                // Send 180 Ringing
                sendResponse(180, "Ringing", message)
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
        val response = computeDigestAuth("REGISTER", sipUri(username), realm, username, password, nonce)
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
            append("Authorization: Digest username=\"$username\", realm=\"$realm\", nonce=\"$nonce\", uri=\"${sipUri(username)}\", response=\"$response\", algorithm=MD5\r\n")
            append("Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        ws?.send(msg)
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
        ws?.send(msg)
    }

    private fun sendResponse(statusCode: Int, reason: String, request: String) {
        val host = buildSipHost()
        val via = extractHeader(request, "Via")
        val from = extractHeader(request, "From")
        val to = extractHeader(request, "To")
        val msg = buildString {
            append("SIP/2.0 $statusCode $reason\r\n")
            if (via != null) append("$via\r\n")
            if (from != null) append("$from\r\n")
            if (to != null) append("$to\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseq INVITE\r\n")
            append("User-Agent: RED-Sovereign/1.0\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
        ws?.send(msg)
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
