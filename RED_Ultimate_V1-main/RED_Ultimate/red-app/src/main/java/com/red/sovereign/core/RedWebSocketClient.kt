package com.red.sovereign.core

import android.content.Context
import com.google.protobuf.ByteString
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.crypto.EncryptedEnvelope
import com.red.sovereign.proto.RedProtos
import com.red.sovereign.security.CertificatePinner
import com.red.sovereign.security.SecureOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class RedWebSocketClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val onEnvelope: (RedProtos.RedRED) -> Unit,
    private val onState: (ConnectionState) -> Unit = {}
) {
    private val client = SecureOkHttpClient.buildWebSocketClient(context)
    private var socket: WebSocket? = null

    fun connect() {
        val token = tokens.accessToken ?: return onState(ConnectionState.UNAUTHORIZED)
        val wsBase = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        val request = Request.Builder().url(wsBase.trimEnd('/') + "/ws/master")
            .header("Authorization", "Bearer $token").build()
        onState(ConnectionState.CONNECTING)
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onState(ConnectionState.CONNECTED)
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                runCatching { RedProtos.RedRED.parseFrom(bytes.toByteArray()) }.onSuccess(onEnvelope)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onState(ConnectionState.DISCONNECTED)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Certificate pinning failure
                if (CertificatePinner.isEnabled && t.message?.contains("Certificate pinning failure") == true) {
                    onState(ConnectionState.DISCONNECTED)
                } else {
                    onState(if (response?.code == 401) ConnectionState.UNAUTHORIZED else ConnectionState.DISCONNECTED)
                }
            }
        })
    }

    /**
     * @return معرف الرسالة عند نجاح الإرسال، أو null عند انقطاع الـ socket.
     * كان يرمي IllegalStateException عبر check() فينهار التطبيق إذا انقطع
     * الاتصال بين الحشو والإرسال؛ الآن يفشل بصمت ويُسجَّل لدى المتصل.
     */
    fun sendEncrypted(
        receiverRedId: String,
        conversationId: String,
        messageType: String,
        senderDeviceId: Int,
        encrypted: EncryptedEnvelope
    ): String? {
        val sender = requireNotNull(tokens.redId) { "RED ID is unavailable" }
        val id = UuidV7.next()
        val chat = RedProtos.ChatMessage.newBuilder()
            .setId(id).setConversationId(conversationId).setSenderId(sender).setReceiverId(receiverRedId)
            .setPayload(ByteString.copyFrom(encrypted.bytes)).setTimestamp(System.currentTimeMillis()).setType(messageType)
            .setSenderDeviceId(senderDeviceId).setReceiverDeviceId(encrypted.receiverDeviceId)
            .setCiphertextType(encrypted.ciphertextType).build()
        val envelope = RedProtos.RedRED.newBuilder().setMessage(chat).build()
        val sent = socket?.send(envelope.toByteArray().toByteString()) == true
        if (!sent) android.util.Log.w("RedWebSocketClient", "sendEncrypted failed: socket not connected")
        return if (sent) id else null
    }

    fun acknowledge(messageId: String, sequence: Long, status: String): Boolean {
        require(status == "DELIVERED" || status == "READ")
        val envelope = RedProtos.RedRED.newBuilder().setAck(
            RedProtos.MessageAck.newBuilder().setMessageId(messageId).setSequenceNumber(sequence).setStatus(status)
        ).build()
        return socket?.send(envelope.toByteArray().toByteString()) == true
    }

    fun typing(conversationId: String, targetRedId: String?, active: Boolean) {
        val sender = requireNotNull(tokens.redId)
        val builder = RedProtos.TypingRED.newBuilder().setConversationId(conversationId).setUserId(sender)
            .setIsTyping(active)
        // للمجموعات (conversationId > 32) target قد يكون فارغاً — البث للكل. للفرد مطلوب.
        if (!targetRedId.isNullOrBlank() && targetRedId != sender) {
            builder.setTargetUserId(targetRedId)
        } else if (conversationId.length <= 32) {
            // محادثة فردية بدون target تعني خطأ — نتجاهل
            if (active && targetRedId.isNullOrBlank()) return
        }
        val envelope = RedProtos.RedRED.newBuilder().setTyping(builder.build()).build()
        socket?.send(envelope.toByteArray().toByteString())
    }

    /** اختصار للمجموعات — يبث لكل الأعضاء دون target */
    fun typingGroup(conversationId: String, active: Boolean) = typing(conversationId, null, active)

    fun deleteMessage(messageId: String, conversationId: String, forEveryone: Boolean = true): Boolean {
        val envelope = RedProtos.RedRED.newBuilder().setDelete(
            RedProtos.DeleteRED.newBuilder().setMessageId(messageId).setConversationId(conversationId).setForEveryone(forEveryone)
        ).build()
        return socket?.send(envelope.toByteArray().toByteString()) == true
    }

    fun disconnect() { socket?.close(1000, "client logout"); socket = null }
}

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, UNAUTHORIZED }
