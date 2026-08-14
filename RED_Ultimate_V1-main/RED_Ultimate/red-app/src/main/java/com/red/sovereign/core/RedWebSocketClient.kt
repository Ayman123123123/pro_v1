package com.red.sovereign.core

import android.content.Context
import com.google.protobuf.ByteString
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.crypto.EncryptedEnvelope
import com.red.sovereign.proto.RedProtos
import com.red.sovereign.security.CertificatePinner
import com.red.sovereign.security.SecureOkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

/** Thread-safe owner of the one messaging WebSocket used by the foreground service. */
class RedWebSocketClient(
    context: Context,
    private val tokens: TokenStore,
    private val onEnvelope: (RedProtos.RedRED) -> Unit,
    private val onState: (ConnectionState) -> Unit = {}
) {
    private val client = SecureOkHttpClient.buildWebSocketClient(context)
    private val socketLock = Any()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closedByClient = false

    fun connect() {
        val token = tokens.accessToken ?: return onState(ConnectionState.UNAUTHORIZED)
        synchronized(socketLock) {
            if (socket != null) return
            closedByClient = false
            val wsBase = ServerEndpoint.url()
                .replaceFirst("http://", "ws://")
                .replaceFirst("https://", "wss://")
            val request = Request.Builder()
                .url(wsBase.trimEnd('/') + "/ws/master")
                .header("Authorization", "Bearer $token")
                .build()
            onState(ConnectionState.CONNECTING)
            socket = client.newWebSocket(request, listener())
        }
    }

    private fun listener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isCurrent(webSocket)) onState(ConnectionState.CONNECTED)
            else webSocket.close(1000, "superseded")
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            if (!isCurrent(webSocket)) return
            runCatching { RedProtos.RedRED.parseFrom(bytes.toByteArray()) }.onSuccess(onEnvelope)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            val shouldNotify = clearIfCurrent(webSocket) && !closedByClient
            if (shouldNotify) onState(ConnectionState.DISCONNECTED)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val shouldNotify = clearIfCurrent(webSocket) && !closedByClient
            if (!shouldNotify) return
            if (CertificatePinner.isEnabled && t.message?.contains("Certificate pinning failure") == true) {
                onState(ConnectionState.DISCONNECTED)
            } else {
                onState(if (response?.code == 401) ConnectionState.UNAUTHORIZED else ConnectionState.DISCONNECTED)
            }
        }
    }

    private fun isCurrent(candidate: WebSocket): Boolean = synchronized(socketLock) { socket === candidate }

    private fun clearIfCurrent(candidate: WebSocket): Boolean = synchronized(socketLock) {
        if (socket !== candidate) false else {
            socket = null
            true
        }
    }

    /** Returns null instead of crashing the process when the network drops mid-send. */
    fun sendEncrypted(
        receiverRedId: String,
        conversationId: String,
        messageType: String,
        senderDeviceId: Int,
        encrypted: EncryptedEnvelope
    ): String? {
        val sender = tokens.redId ?: return null
        val id = UuidV7.next()
        val chat = RedProtos.ChatMessage.newBuilder()
            .setId(id)
            .setConversationId(conversationId)
            .setSenderId(sender)
            .setReceiverId(receiverRedId)
            .setPayload(ByteString.copyFrom(encrypted.bytes))
            .setTimestamp(System.currentTimeMillis())
            .setType(messageType)
            .setSenderDeviceId(senderDeviceId)
            .setReceiverDeviceId(encrypted.receiverDeviceId)
            .setCiphertextType(encrypted.ciphertextType)
            .build()
        val envelope = RedProtos.RedRED.newBuilder().setMessage(chat).build()
        return id.takeIf { socket?.send(envelope.toByteArray().toByteString()) == true }
    }

    fun acknowledge(messageId: String, sequence: Long, status: String): Boolean {
        require(status == "DELIVERED" || status == "READ")
        val envelope = RedProtos.RedRED.newBuilder().setAck(
            RedProtos.MessageAck.newBuilder()
                .setMessageId(messageId)
                .setSequenceNumber(sequence)
                .setStatus(status)
        ).build()
        return socket?.send(envelope.toByteArray().toByteString()) == true
    }

    fun acknowledgeRemoteWipe(commandId: String): Boolean {
        val envelope = RedProtos.RedRED.newBuilder().setRemoteWipeAck(
            RedProtos.RemoteWipeAck.newBuilder().setCommandId(commandId)
        ).build()
        return socket?.send(envelope.toByteArray().toByteString()) == true
    }

    fun typing(conversationId: String, targetRedId: String, active: Boolean): Boolean {
        val sender = tokens.redId ?: return false
        val envelope = RedProtos.RedRED.newBuilder().setTyping(
            RedProtos.TypingRED.newBuilder()
                .setConversationId(conversationId)
                .setUserId(sender)
                .setTargetUserId(targetRedId)
                .setIsTyping(active)
        ).build()
        return socket?.send(envelope.toByteArray().toByteString()) == true
    }

    fun reconnect() {
        closedByClient = false
        val previous = synchronized(socketLock) {
            val current = socket
            socket = null
            current
        }
        previous?.cancel()
        connect()
    }

    fun disconnect() {
        closedByClient = true
        val active = synchronized(socketLock) {
            val current = socket
            socket = null
            current
        }
        active?.close(1000, "client logout")
    }
}

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, UNAUTHORIZED }
