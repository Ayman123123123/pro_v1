package com.red.sovereign.data.dinstar

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * عميل WebSocket للاتصال المباشر مع خادم DINSTAR
 * يستقبل تحديثات الحالة والأحداث في الوقت الفعلي
 */
class DinstarWebSocketClient(
    private val wsUrl: String,
    private val listener: DinstarWebSocketListener
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    interface DinstarWebSocketListener {
        fun onConnected()
        fun onDisconnected(code: Int, reason: String)
        fun onError(error: Throwable)
        fun onPortStatusUpdate(gatewayId: String, port: Int, status: Map<String, Any>)
        fun onDeviceStatusUpdate(gatewayId: String, status: Map<String, Any>)
        fun onUssdResponse(gatewayId: String, port: Int, response: Map<String, Any>)
        fun onPortControl(gatewayId: String, port: Int, control: Map<String, Any>)
        fun onNewCdr(gatewayId: String, cdr: Map<String, Any>)
        fun onIncomingSms(gatewayId: String, port: Int, sms: Map<String, Any>)
        fun onAlert(gatewayId: String, alert: Map<String, Any>)
    }

    fun connect() {
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onDisconnected(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            val gatewayId = json.getString("gatewayId")
            val data = json.optJSONObject("data")?.toMap() ?: emptyMap()
            val port = json.optInt("port", -1)

            when (type) {
                "PORT_STATUS" -> {
                    if (port >= 0) {
                        listener.onPortStatusUpdate(gatewayId, port, data)
                    }
                }
                "DEVICE_STATUS" -> {
                    listener.onDeviceStatusUpdate(gatewayId, data)
                }
                "USSD_RESPONSE" -> {
                    if (port >= 0) {
                        listener.onUssdResponse(gatewayId, port, data)
                    }
                }
                "PORT_CONTROL" -> {
                    if (port >= 0) {
                        listener.onPortControl(gatewayId, port, data)
                    }
                }
                "NEW_CDR" -> {
                    listener.onNewCdr(gatewayId, data)
                }
                "INCOMING_SMS" -> {
                    if (port >= 0) {
                        listener.onIncomingSms(gatewayId, port, data)
                    }
                }
                "ALERT" -> {
                    listener.onAlert(gatewayId, data)
                }
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            when (val value = get(key)) {
                is JSONObject -> map[key] = value.toMap()
                is JSONArray -> map[key] = value.toList()
                JSONObject.NULL -> {} // skip null values
                else -> map[key] = value
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until length()) {
            when (val value = get(i)) {
                is JSONObject -> list.add(value.toMap())
                is JSONArray -> list.add(value.toList())
                JSONObject.NULL -> {} // skip null values
                else -> list.add(value)
            }
        }
        return list
    }
}
