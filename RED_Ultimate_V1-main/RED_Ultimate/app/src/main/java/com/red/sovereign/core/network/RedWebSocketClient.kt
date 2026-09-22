package com.red.sovereign.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RED WebSocket Client — connects to the RED Sovereign backend for real-time messaging.
 */
@Singleton
class RedWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private var listener: MessageListener? = null

    interface MessageListener {
        fun onMessageReceived(data: ByteArray)
        fun onConnected()
        fun onDisconnected(code: Int, reason: String)
        fun onError(t: Throwable)
    }

    fun connect(url: String, token: String?, messageListener: MessageListener) {
        listener = messageListener
        val request = Request.Builder()
            .url(url)
            .apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                listener?.onMessageReceived(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener?.onDisconnected(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener?.onError(t)
            }
        })
    }

    fun send(data: ByteArray): Boolean {
        return webSocket?.send(okio.ByteString.of(*data)) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
