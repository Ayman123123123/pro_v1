package com.red.sovereign.features.dinstar

import android.util.Log
import com.red.sovereign.core.ServerEndpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * جسر WebSocket لأحداث DINSTAR الحية (حالة المنافذ، CDR، الرسائل).
 *
 * يعيد الاتصال تلقائيًا بتأخير تصاعدي. هذا ليس ترفًا: البوابة على شبكة
 * محلية قد تنقطع، وبلا إعادة اتصال يبقى المشغّل أمام لوحة **جامدة تبدو
 * سليمة** — لا خطأ ظاهر، فقط أحداث توقفت. أسوأ من عطل معلن.
 */
class DinstarWebSocketBridge(private val backendUrl: String = ServerEndpoint.url()) {
    companion object {
        private const val TAG = "RED.DinstarWS"
        private const val WS_PATH = "/ws/dinstar"
        private const val PING_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        /** حدّ الأُسّ: 2^6 = 64 ثانية، فوقه يقصّه السقف أعلاه. */
        private const val BACKOFF_SHIFT_CAP = 6
    }

    /** حالة الاتصال المعروضة للمستخدم — لا يكفي بثّ الأحداث وحده. */
    enum class WsConnectionState(val labelAr: String) {
        CONNECTED("متصل"),
        CONNECTING("جارٍ الاتصال"),
        DISCONNECTED("غير متصل"),
        FAILED("فشل الاتصال")
    }

    private val client = OkHttpClient.Builder()
        // WebSocket طويل الأمد: مهلة القراءة الافتراضية تقطعه في صمت
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    /** يميّز الإغلاق المتعمَّد عن الانقطاع، فلا نعيد الاتصال بعد disconnect. */
    private val closedByClient = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _wsEvents = MutableSharedFlow<DinstarWsEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val wsEvents: SharedFlow<DinstarWsEvent> = _wsEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    fun connect(token: String? = null) {
        if (isConnected.get()) return
        closedByClient.set(false)
        _connectionState.value = WsConnectionState.CONNECTING
        val wsUrl = backendUrl.replace("http", "ws").trimEnd('/') + WS_PATH
        val request = Request.Builder().url(wsUrl).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                // نجاح الاتصال يصفّر العدّاد: الانقطاع التالي يبدأ من ثانية
                // واحدة لا من آخر تأخير بلغناه.
                reconnectAttempts.set(0)
                _connectionState.value = WsConnectionState.CONNECTED
                _wsEvents.tryEmit(DinstarWsEvent.Connected)
                Log.i(TAG, "Dinstar WS Connected")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { parseAndEmit(text) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                _connectionState.value = WsConnectionState.DISCONNECTED
                Log.i(TAG, "WS closed: $code $reason")
                scheduleReconnect(token)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                _connectionState.value = WsConnectionState.FAILED
                _wsEvents.tryEmit(DinstarWsEvent.Error(t.message ?: "WS_FAILURE"))
                Log.w(TAG, "WS failure: ${t.message}")
                scheduleReconnect(token)
            }
        })
    }

    fun disconnect() {
        closedByClient.set(true)
        webSocket?.close(1000, "disconnect")
        webSocket = null
        isConnected.set(false)
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    /** إرسال أمر عبر القناة الحية — false حين لا اتصال. */
    fun send(message: String): Boolean = webSocket?.send(message) ?: false

    /**
     * إعادة اتصال بتأخير تصاعدي: 1s, 2s, 4s … بسقف دقيقة.
     *
     * التصاعد مقصود: انقطاع الشبكة المحلية غالبًا لحظي فتكفيه ثانية،
     * أما تعطّل الخادم فيطول — والمحاولة كل ثانية تستنزف البطارية بلا
     * فائدة. السقف يمنع التباعد إلى ما لا نهاية.
     */
    private fun scheduleReconnect(token: String?) {
        if (closedByClient.get()) return
        val attempt = reconnectAttempts.incrementAndGet()
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = WsConnectionState.FAILED
            Log.w(TAG, "بلغنا الحد الأقصى لمحاولات إعادة الاتصال ($MAX_RECONNECT_ATTEMPTS)")
            return
        }
        val delayMs = minOf(1000L shl minOf(attempt - 1, BACKOFF_SHIFT_CAP), MAX_RECONNECT_DELAY_MS)
        Log.i(TAG, "إعادة الاتصال بعد ${delayMs}ms (المحاولة $attempt)")
        scope.launch {
            delay(delayMs)
            if (!closedByClient.get()) connect(token)
        }
    }

    private suspend fun parseAndEmit(text: String) {
        runCatching {
            val json = org.json.JSONObject(text)
            val type = json.optString("type", "")
            /*
             * الخادم يغلّف الحمولة تحت "data" (انظر broadcastEvent و
             * buildPortStatusPayload). القراءة من الجذر كانت تُرجع قيمًا
             * فارغة صامتة: gatewayId فارغ وport = 0 — و0 منفذ صالح، فيبدو
             * كأن المنفذ الأول هو المتغيّر دائمًا.
             * نُبقي التراجع إلى الجذر احتياطًا لأي باثّ لا يغلّف.
             */
            val body = json.optJSONObject("data") ?: json
            when (type) {
                "DINSTAR_PORT_STATUS" -> _wsEvents.emit(
                    DinstarWsEvent.PortStatusChanged(
                        // معرّف البوابة: بدونه لا يُعرف أي جهاز تغيّر منفذه
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        port = body.optInt("port", -1),
                        callState = body.optString("callState"),
                        // optInt تُرجع 0 عند الغياب، و0 قراءةٌ صالحة تعني
                        // ‎-113 dBm. التمييز بين «غياب القيمة» و«أضعف قيمة»
                        // ضروري، لذا null صراحةً.
                        signalDbm = if (json.isNull("signalDbm")) null else json.optInt("signalDbm"),
                        signalUsable = json.optBoolean("signalUsable", false)
                    )
                )
                "DINSTAR_CDR" -> _wsEvents.emit(DinstarWsEvent.CdrReceived(text))
                "DINSTAR_SMS" -> _wsEvents.emit(
                    DinstarWsEvent.IncomingSms(
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        port = body.optInt("port"),
                        number = body.optString("number"),
                        text = body.optString("text")
                    )
                )
                // ─── أحداث كان الخادم يبثّها ولا يستقبلها أحد ───
                "DINSTAR_DEVICE_STATUS" -> _wsEvents.emit(
                    DinstarWsEvent.DeviceStatusChanged(
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        status = DinstarDeviceStatus(
                            cpuUsed = body.str("cpu_used", "cpuUsed"),
                            memoryTotal = body.str("memory_total", "memoryTotal"),
                            memoryUsed = body.str("memory_used", "memoryUsed"),
                            memoryFree = body.str("memory_free", "memoryFree"),
                            flashTotal = body.str("flash_total", "flashTotal"),
                            flashUsed = body.str("flash_used", "flashUsed"),
                            flashFree = body.str("flash_free", "flashFree"),
                            temperature = body.str("temperature"),
                            uptime = body.str("uptime")
                        )
                    )
                )
                "DINSTAR_USSD" -> _wsEvents.emit(
                    DinstarWsEvent.UssdResponse(
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        port = body.optInt("port", -1),
                        // الرد قد يصل نصًّا مباشرًا أو كائنًا تحت "response"
                        response = body.optJSONObject("response")?.toString()
                            ?: body.optString("response").ifBlank { body.optString("text") }
                    )
                )
                "DINSTAR_PORT_CONTROL" -> _wsEvents.emit(
                    DinstarWsEvent.PortControlChanged(
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        port = body.optInt("port", -1),
                        control = body.optJSONObject("control")?.toString() ?: body.optString("control")
                    )
                )
                /*
                 * استثناء من البوابة: فشل مكالمة، شريحة منزوعة، انقطاع منفذ.
                 * أهم حدث تشغيليًّا وكان يُهمل بالكامل — المشغّل لا يعرف أن
                 * شريحةً سقطت إلا حين تفشل المكالمات عليها.
                 */
                "DINSTAR_EXCEPTION" -> _wsEvents.emit(
                    DinstarWsEvent.GatewayException(
                        gatewayId = body.optString("gatewayId").takeIf { it.isNotBlank() },
                        port = if (body.has("port")) body.optInt("port", -1) else -1,
                        reason = body.str("reason", "message", "error", "exception") ?: "سبب غير محدّد"
                    )
                )
                "HEARTBEAT" -> _wsEvents.emit(DinstarWsEvent.Heartbeat)
            }
        }.onFailure { Log.w(TAG, "تعذّر تحليل رسالة WS", it) }
    }

    /** أول قيمة نصية غير فارغة من بين مفاتيح مترادفة. */
    private fun org.json.JSONObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> optString(k).takeIf { it.isNotBlank() } }

    fun destroy() { disconnect(); scope.cancel() }
}

sealed class DinstarWsEvent {
    object Connected : DinstarWsEvent()
    object Heartbeat : DinstarWsEvent()
    data class PortStatusChanged(
        val gatewayId: String?,
        val port: Int,
        val callState: String,
        val signalDbm: Int?,
        val signalUsable: Boolean
    ) : DinstarWsEvent()
    data class CdrReceived(val payload: String) : DinstarWsEvent()
    data class IncomingSms(
        val gatewayId: String?,
        val port: Int,
        val number: String,
        val text: String
    ) : DinstarWsEvent()

    /** قياسات عتاد البوابة، تصل دفعًا بعد كل استعلام حالة على الخادم. */
    data class DeviceStatusChanged(
        val gatewayId: String?,
        val status: DinstarDeviceStatus
    ) : DinstarWsEvent()

    /** رد USSD — يصل غير متزامن بعد إرسال الأمر، فلا يمكن انتظاره في الطلب. */
    data class UssdResponse(
        val gatewayId: String?,
        val port: Int,
        val response: String
    ) : DinstarWsEvent()

    /** تغيّر تحكم بالمنفذ (طاقة، تحويل مكالمات) من أي عميل آخر. */
    data class PortControlChanged(
        val gatewayId: String?,
        val port: Int,
        val control: String
    ) : DinstarWsEvent()

    /** استثناء من البوابة: فشل مكالمة، شريحة منزوعة، منفذ ساقط. */
    data class GatewayException(
        val gatewayId: String?,
        val port: Int,
        val reason: String
    ) : DinstarWsEvent()

    data class Error(val message: String) : DinstarWsEvent()
}
