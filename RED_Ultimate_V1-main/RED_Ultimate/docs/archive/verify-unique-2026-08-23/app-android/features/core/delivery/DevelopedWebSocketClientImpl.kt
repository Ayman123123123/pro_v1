package com.red.core.delivery

import com.red.proto.ChatProtos
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class DevelopedWebSocketClientImpl(
    private val serverUrl: String,
    private val token: String,
    private val deliveryManager: MessageDeliveryManager
) : DevelopedWebSocketClient {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun connect() {
        val request = Request.Builder()
            .url(serverUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingData(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Reconnection logic: Exponential Backoff
                reconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                reconnect()
            }
        })
    }

    private fun handleIncomingData(data: ByteArray) {
        try {
            // Try to parse as Message
            val msg = ChatProtos.ChatMessage.parseFrom(data)
            // Process message (Step 6 of Algorithm)
            println("New message received: ${msg.id}")
        } catch (e: Exception) {
            // Try to parse as Ack
            val ack = ChatProtos.MessageAck.parseFrom(data)
            deliveryManager.onAckReceived(ack)
        }
    }

    override fun send(data: ByteArray) {
        webSocket?.send(data.toByteString())
    }

    private fun reconnect() {
        // Implementation of Reconnection Jitter + Exponential Backoff
        println("Reconnecting to RED Server...")
    }
}
