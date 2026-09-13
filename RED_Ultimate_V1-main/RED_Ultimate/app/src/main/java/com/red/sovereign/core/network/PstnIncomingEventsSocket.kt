package com.red.sovereign.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * سوكيت تنبيهات المكالمات الواردة (Task 8).
 *
 * القناة الأساسية: `/ws/pstn` (نفس قناة red-app PstnEventSocket).
 * تُقبل أيضاً `/api/pstn/ws/events` كاسم مستعار — الخادم يخدم نفس
 * الأحداث على القناة الموحدة، وهذا العميل يجرّب المسارين بالترتيب.
 *
 * الأحداث:
 * - INCOMING_CALL مع callId, fromNumber, toPort, gatewayId
 * - CALL_ENDED / CALL_ANSWERED
 *
 * الرد/الرفض عبر REST (POST /api/pstn/calls/{callId}/answer|hangup)
 * في CallRepository — هذا السوكيت للإشارات فقط.
 */
data class IncomingPstnEvent(
    val type: String,
    val callId: String = "",
    val stage: String = "",
    val fromNumber: String = "",
    val toPort: Int = -1,
    val gatewayId: String? = null,
    val gatewayHost: String = "",
    val cause: String? = null,
    val raw: String = ""
)

data class PstnCallProgressEvent(
    val callId: String,
    val stage: String,
    val number: String = "",
    val port: Int = -1,
    val cause: String? = null
)

@Singleton
class PstnIncomingEventsSocket @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<IncomingPstnEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<IncomingPstnEvent> = _events.asSharedFlow()
    private val _callProgress = MutableSharedFlow<PstnCallProgressEvent>(extraBufferCapacity = 64)
    val callProgress: SharedFlow<PstnCallProgressEvent> = _callProgress.asSharedFlow()

    private var socket: WebSocket? = null
    private var baseUrl: String = ""
    private var token: String? = null
    private var running = false
    private var retryCount = 0

    /** paths بالترتيب: الأساسي ثم المستعار */
    private fun candidateUrls(): List<String> {
        val base = baseUrl.trimEnd('/')
        return listOf("$base/ws/pstn", "$base/api/pstn/ws/events")
    }

    fun start(baseUrl: String, token: String?) {
        this.baseUrl = baseUrl
        this.token = token
        if (running) return
        running = true
        retryCount = 0
        connect(0)
    }

    fun stop() {
        running = false
        runCatching { socket?.close(1000, "stop") }
        socket = null
    }

    private fun connect(pathIndex: Int) {
        if (!running) return
        val urls = candidateUrls()
        val url = urls[pathIndex.coerceIn(urls.indices)]
        val request = Request.Builder().url(url).apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        socket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryCount = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { event ->
                    if (event.type == "PSTN_CALL_EVENT") {
                        _callProgress.tryEmit(
                            PstnCallProgressEvent(
                                callId = event.callId,
                                stage = event.stage,
                                number = event.fromNumber,
                                port = event.toPort,
                                cause = event.cause
                            )
                        )
                    } else {
                        _events.tryEmit(event)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect(pathIndex)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // جرّب المسار البديل أولاً، ثم backoff
                if (pathIndex + 1 < candidateUrls().size) {
                    connect(pathIndex + 1)
                } else {
                    scheduleReconnect(0)
                }
            }
        })
    }

    private fun scheduleReconnect(pathIndex: Int) {
        if (!running) return
        scope.launch {
            retryCount++
            delay(minOf(30_000L, 1_000L * retryCount))
            if (running) connect(pathIndex)
        }
    }

    internal fun parse(text: String): IncomingPstnEvent? {
        return runCatching {
            val o = JSONObject(text)
            val type = o.optString("type", o.optString("event", ""))
            if (type.isBlank()) return null
            // تجاهل أحداث SMS — تهمنا أحداث PSTN فقط
            if (type.startsWith("SMS")) return null
            val data = if (o.has("data")) o.optJSONObject("data") else o
            IncomingPstnEvent(
                type = type,
                callId = data?.optString("callId", "") ?: "",
                stage = data?.optString("event", data.optString("stage", "")) ?: "",
                fromNumber = data?.optString("fromNumber",
                    data.optString("callerNumber",
                        data.optString("from", ""))) ?: "",
                toPort = data?.optInt("toPort", data.optInt("portIndex", -1)) ?: -1,
                gatewayId = data?.optString("gatewayId")?.takeIf { it.isNotBlank() },
                gatewayHost = data?.optString("gatewayHost", "") ?: "",
                cause = data?.optString("cause")?.takeIf { it.isNotBlank() },
                raw = text
            )
        }.getOrNull()
    }
}
