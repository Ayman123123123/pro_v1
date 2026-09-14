package com.red.sovereign.features.dinstar

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🏛️ YOUNES Dinstar ViewModel — Ultimate Core
 */
class DinstarViewModel(application: Application) : AndroidViewModel(application) {

    private val tokens = TokenStore(application)
    private val client = AuthorizedApiClient(tokens)
    private val wsBridge = DinstarWebSocketBridge()
    private val mapper = ObjectMapper()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _gatewayStatus = MutableStateFlow(DinstarGatewayStatus())
    val gatewayStatus = _gatewayStatus.asStateFlow()

    /** حالة الأسطول كاملًا — عدة بوابات معًا. */
    private val _fleetStatus = MutableStateFlow(DinstarFleetStatus())
    val fleetStatus = _fleetStatus.asStateFlow()

    private val _cdrRecords = MutableStateFlow<List<DinstarCdr>>(emptyList())
    val cdrRecords = _cdrRecords.asStateFlow()

    private val _commandResult = MutableStateFlow<DinstarCommandResult?>(null)
    val commandResult = _commandResult.asStateFlow()

    // ── الميزات المستعادة من النسخ القديمة (DinstarSmsScreen) ──
    private val _incomingSms = MutableStateFlow<List<DinstarIncomingSms>>(emptyList())
    val incomingSms = _incomingSms.asStateFlow()

    private val _smsSendResults = MutableStateFlow<List<DinstarSmsResult>>(emptyList())
    val smsSendResults = _smsSendResults.asStateFlow()

    private val _smsQueueCount = MutableStateFlow(0)
    val smsQueueCount = _smsQueueCount.asStateFlow()

    init {
        refreshStatus()
        connectWebSocket()
    }

    /**
     * تحديث حالة الأسطول كله.
     *
     * كان يستدعي `/api/admin/dinstar/status` الذي يُرجع منافذ **بوابة
     * واحدة** كمصفوفة مسطّحة، فكان مستحيلًا معرفة أي جهاز يملك أي منفذ.
     * صار يستدعي `/fleet/ports` الذي يُرجع المنافذ مجمّعة تحت بواباتها.
     */
    fun refreshStatus() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/fleet/ports")) {
                is ApiResult.Success -> {
                    runCatching {
                        val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                        @Suppress("UNCHECKED_CAST")
                        val entries = (root["gateways"] as? List<Map<String, Any?>>).orEmpty()

                        val gateways = entries.map { entry ->
                            @Suppress("UNCHECKED_CAST")
                            val gw = (entry["gateway"] as? Map<String, Any?>).orEmpty()
                            @Suppress("UNCHECKED_CAST")
                            val rawPorts = (entry["ports"] as? List<Map<String, Any?>>).orEmpty()

                            DinstarGatewayStatus(
                                gatewayId = gw["id"]?.toString(),
                                name = gw["name"]?.toString().orEmpty(),
                                // البوابة متصلة فقط إن لم يرافقها خطأ وكانت حالتها ONLINE
                                isOnline = entry["error"] == null && gw["healthState"]?.toString() == "ONLINE",
                                gatewayIp = gw["host"]?.toString().orEmpty(),
                                model = gw["model"]?.toString().orEmpty(),
                                firmware = gw["firmwareVersion"]?.toString().orEmpty(),
                                ports = rawPorts.map { parsePort(it) },
                                lastUpdated = System.currentTimeMillis()
                            )
                        }

                        _fleetStatus.value = DinstarFleetStatus(
                            gateways = gateways,
                            lastUpdated = System.currentTimeMillis()
                        )
                        // توافق مع الشاشات التي ما تزال تعرض بوابة واحدة
                        _gatewayStatus.value = gateways.firstOrNull() ?: DinstarGatewayStatus()
                    }.onFailure { Log.w(TAG, "تعذّر تحليل حالة الأسطول", it) }
                }
                is ApiResult.Error -> {
                    _fleetStatus.value = _fleetStatus.value.copy(
                        gateways = _fleetStatus.value.gateways.map { it.copy(isOnline = false) }
                    )
                    _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
                }
            }
        }
    }

    /**
     * تحويل منفذ من استجابة الخادم.
     *
     * الخادم هو مرجع تفسير الإشارة (3GPP TS 27.007 §8.5) ويرسل
     * `signalDbm` و`signalUsable` محسوبَين. لا يُعيد التطبيق الحساب حتى
     * لا يتفرّع منطقان يختلفان. القيم `null` تُنقل كما هي: القراءة 99
     * تعني «لا قياس» لا صفرًا ولا 100%.
     */
    private fun parsePort(raw: Map<String, Any?>): DinstarPort {
        val operator = raw["operator"]?.toString()
        return DinstarPort(
            index = (raw["index"] as? Number)?.toInt() ?: (raw["port"] as? Number)?.toInt() ?: 0,
            radioType = raw["radioType"]?.toString() ?: "GSM",
            registrationState = raw["status"]?.toString() ?: "UNREGISTERED",
            callState = raw["callState"]?.toString() ?: "IDLE",
            signalPercent = (raw["signal"] as? Number)?.toInt(),
            signalDbm = (raw["signalDbm"] as? Number)?.toInt(),
            signalRaw = (raw["signalRaw"] as? Number)?.toInt(),
            signalUsable = raw["signalUsable"] as? Boolean ?: false,
            gprsState = raw["gprs"]?.toString() ?: "DETACH",
            operatorName = operator ?: "غير معروف",
            numberMasked = raw["numberMasked"]?.toString(),
            imsiMasked = raw["imsiMasked"]?.toString(),
            iccidMasked = raw["iccidMasked"]?.toString(),
            simType = YemenOperator.fromApiOperatorName(operator)
        )
    }

    /**
     * إرسال SMS عبر مسار الإدارة — فردي أو مجمّع.
     *
     * [ports] فارغة = كل المنافذ المتاحة (يقررها الخادم/الجهاز)،
     * [encoding] الافتراضي AUTO يشتق من النص فلا تصل العربية «?????».
     */
    fun sendSms(text: String, numbers: List<String>, ports: List<Int> = emptyList(), encoding: String = "AUTO") {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mutableMapOf<String, Any?>(
                "text" to text,
                "param" to numbers.map { mapOf("number" to it) },
                "encoding" to encoding
            )
            if (ports.isNotEmpty()) body["port"] = ports
            when (val response = client.request("POST", "/api/admin/dinstar/sms/send", mapper.writeValueAsString(body))) {
                is ApiResult.Success -> {
                    val parsed = runCatching {
                        mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    }.getOrNull()
                    val status = parsed?.get("status")?.toString() ?: "ACCEPTED"
                    _smsSendResults.value = listOf(
                        DinstarSmsResult(
                            taskId = (parsed?.get("taskId") as? Number)?.toLong(),
                            numbers = numbers,
                            status = status,
                            queueCount = (parsed?.get("queueCount") as? Number)?.toInt(),
                            ports = ports,
                            encoding = encoding
                        )
                    ) + _smsSendResults.value
                    _smsQueueCount.value = (parsed?.get("queueCount") as? Number)?.toInt() ?: _smsQueueCount.value
                    _commandResult.value = DinstarCommandResult.Success("تم إرسال الرسائل بنجاح")
                }
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error(response.message ?: "فشل إرسال الرسائل")
            }
        }
    }

    /** جلب الوارد المخزّن من الجهاز الآن — يُستخدم في تبويب «واردة». */
    fun fetchIncomingSms() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/incoming")) {
                is ApiResult.Success -> runCatching {
                    val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    val items = (root["messages"] as? List<Map<String, Any?>>).orEmpty()
                    val fetched = items.mapNotNull { raw ->
                        val number = raw["number"]?.toString() ?: return@mapNotNull null
                        DinstarIncomingSms(
                            gatewayId = raw["gatewayId"]?.toString(),
                            port = (raw["port"] as? Number)?.toInt() ?: (raw["port_index"] as? Number)?.toInt() ?: -1,
                            number = number,
                            text = raw["text"]?.toString().orEmpty(),
                            receivedAt = (raw["time"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    if (fetched.isNotEmpty()) {
                        _incomingSms.value = fetched + _incomingSms.value
                    }
                }.onFailure { Log.w(TAG, "تعذّر تحليل الوارد", it) }
                is ApiResult.Error -> Unit
            }
        }
    }

    /** عدد رسائل الإرسال في طابور الجهاز. */
    fun fetchQueueCount() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/queue")) {
                is ApiResult.Success -> runCatching {
                    val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    _smsQueueCount.value = (root["sms_in_queue"] as? Number)?.toInt()
                        ?: (root["count"] as? Number)?.toInt() ?: 0
                }.getOrNull()
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun connectWebSocket() {
        wsBridge.connect(tokens.accessToken)
        viewModelScope.launch {
            wsBridge.wsEvents.collect { event ->
                when (event) {
                    is DinstarWsEvent.PortStatusChanged -> refreshStatus()
                    is DinstarWsEvent.CdrReceived -> queryCdr()
                    // الوارد الفوري — الميزة التي كانت تعمل عبر DinstarSmsScreen القديمة
                    is DinstarWsEvent.IncomingSms -> _incomingSms.value =
                        listOf(DinstarIncomingSms(event.gatewayId, event.port, event.number, event.text)) +
                            _incomingSms.value
                    else -> Unit
                }
            }
        }
    }

    private fun queryCdr() {
        viewModelScope.launch {
            client.request("GET", "/api/admin/dinstar/cdr")
        }
    }

    override fun onCleared() {
        wsBridge.destroy()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DinstarViewModel"
    }
}
