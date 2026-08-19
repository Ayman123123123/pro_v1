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

    /** الرسائل الواردة على شرائح البوابة. */
    private val _incomingSms = MutableStateFlow<List<DinstarIncomingSms>>(emptyList())
    val incomingSms = _incomingSms.asStateFlow()

    /** عدد الرسائل المنتظرة في طابور الإرسال. */
    private val _smsQueueCount = MutableStateFlow(0)
    val smsQueueCount = _smsQueueCount.asStateFlow()

    /** قدرات الطراز المكتشَف (عدد المنافذ، دعم USSD، إلخ). */
    private val _capabilities = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val capabilities = _capabilities.asStateFlow()

    /** معاينة قرار التوجيه القادمة من الخادم لرقم مُدخَل. */
    private val _routingPreview = MutableStateFlow<Map<String, Any?>?>(null)
    val routingPreview = _routingPreview.asStateFlow()

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
     * إرسال SMS عبر البوابة — POST /api/admin/dinstar/sms/send
     *
     * يدعم الإرسال الفردي والمجمّع، وتحديد منافذ بعينها، وترميز
     * GSM 7-bit أو UCS2 (لازم للنص العربي)، وطلب تقرير التسليم.
     *
     * الترميز يُختار تلقائيًا حين لا يُمرَّر: أي محرف خارج مجموعة
     * GSM 7-bit — والعربية كلها كذلك — يفرض UCS2، وإلا بُترت الرسالة
     * أو وصلت محارف مشوّهة.
     */
    fun sendSms(
        text: String,
        numbers: List<String>,
        ports: List<Int> = emptyList(),
        encoding: String? = null,
        requestStatusReport: Boolean = true
    ) {
        if (text.isBlank()) {
            _commandResult.value = DinstarCommandResult.Error("نص الرسالة فارغ")
            return
        }
        if (numbers.isEmpty()) {
            _commandResult.value = DinstarCommandResult.Error("لا توجد أرقام مستقبِلة")
            return
        }
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = buildMap<String, Any?> {
                put("text", text)
                put("param", numbers.map { mapOf("number" to it) })
                put("encoding", encoding ?: detectEncoding(text))
                put("request_status_report", requestStatusReport)
                if (ports.isNotEmpty()) put("port", ports)
            }
            when (val response = client.request("POST", "/api/admin/dinstar/sms/send", mapper.writeValueAsString(body))) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("تم إرسال الرسائل بنجاح")
                    querySmsQueueCount()
                }
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل إرسال الرسائل" }, response.code)
            }
        }
    }

    /**
     * GSM 7-bit لا يغطي العربية؛ أي محرف فوق U+007F يوجب UCS2.
     * (المجموعة الممتدة تضم قلة من الرموز اللاتينية فقط، وتجاهلها
     * يميل إلى الأمان: UCS2 يمرّ دائمًا.)
     */
    private fun detectEncoding(text: String): String =
        if (text.any { it.code > 0x7F }) "UCS2" else "GSM7BIT"

    // ═══════════════════ إدارة المنافذ ═══════════════════

    /** تفاصيل منفذ واحد — GET /ports/{port} */
    fun getPortInfo(port: Int) {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/ports/$port")) {
                is ApiResult.Success -> runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val raw = (root["status"] as? Map<String, Any?>) ?: root
                    val updated = parsePort(raw)
                    // الاستبدال بالفهرس المنطقي للمنفذ لا بموضعه في القائمة:
                    // القائمة قد تكون مرتّبة أو ناقصة منافذ غير مسجّلة.
                    val current = _gatewayStatus.value.ports.toMutableList()
                    val at = current.indexOfFirst { it.index == updated.index }
                    if (at >= 0) current[at] = updated else current.add(updated)
                    _gatewayStatus.value = _gatewayStatus.value.copy(ports = current.sortedBy { it.index })
                }.onFailure { Log.w(TAG, "تعذّر تحليل بيانات المنفذ $port", it) }
                is ApiResult.Error -> Log.w(TAG, "فشل جلب المنفذ $port: ${response.message}")
            }
        }
    }

    /** إعادة تعيين منفذ — POST /ports/{port}/reset */
    fun resetPort(port: Int) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$port/reset")) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("تم إعادة تعيين المنفذ $port")
                    // الشريحة تحتاج وقتًا لإعادة التسجيل على الشبكة؛
                    // التحديث الفوري يُظهرها UNREGISTERED فيُقلق بلا سبب.
                    delay(PORT_RESET_SETTLE_MS)
                    refreshStatus()
                }
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل إعادة التعيين" }, response.code)
            }
        }
    }

    /** تشغيل/إطفاء منفذ — POST /ports/{port}/power */
    fun setPortPower(port: Int, on: Boolean) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mapper.writeValueAsString(mapOf("power" to if (on) "on" else "off"))
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$port/power", body)) {
                is ApiResult.Success -> {
                    _commandResult.value =
                        DinstarCommandResult.Success(if (on) "تم تشغيل المنفذ $port" else "تم إطفاء المنفذ $port")
                    delay(PORT_RESET_SETTLE_MS)
                    refreshStatus()
                }
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل تغيير حالة المنفذ" }, response.code)
            }
        }
    }

    /**
     * إرسال كود USSD — POST /ports/{port}/ussd
     *
     * يُتحقق من الشكل محليًا قبل الإرسال: أكواد USSD أرقام و`*` و`#`
     * فقط، وإرسال غيرها يُعلّق جلسة على البوابة بلا داعٍ.
     */
    fun sendUssd(port: Int, code: String) {
        if (!USSD_PATTERN.matches(code)) {
            _commandResult.value = DinstarCommandResult.Error("كود USSD غير صالح")
            return
        }
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mapper.writeValueAsString(mapOf("code" to code))
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$port/ussd", body)) {
                is ApiResult.Success -> _commandResult.value =
                    DinstarCommandResult.Success("تم إرسال USSD: $code")
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل إرسال USSD" }, response.code)
            }
        }
    }

    /** تحويل المكالمات على منفذ — POST /ports/{port}/callforward */
    fun setCallForward(port: Int, param: String, number: String) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mapper.writeValueAsString(mapOf("param" to param, "number" to number))
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$port/callforward", body)) {
                is ApiResult.Success -> _commandResult.value =
                    DinstarCommandResult.Success("تم ضبط تحويل المكالمات للمنفذ $port")
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل ضبط التحويل" }, response.code)
            }
        }
    }

    /** إلغاء كل تحويلات المكالمات على منفذ. */
    fun cancelCallForward(port: Int) = setCallForward(port, "CancelAll", "")

    // ═══════════════════ الرسائل الواردة والطابور ═══════════════════

    /** الرسائل الواردة — GET /sms/incoming */
    fun queryIncomingSms() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/incoming")) {
                is ApiResult.Success -> runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val list = (root["sms"] as? List<Map<String, Any?>>).orEmpty()
                    _incomingSms.value = list.map { raw ->
                        DinstarIncomingSms(
                            port = (raw["port"] as? Number)?.toInt() ?: -1,
                            number = raw["number"]?.toString().orEmpty(),
                            text = raw["text"]?.toString().orEmpty(),
                            timestamp = raw["timestamp"]?.toString().orEmpty()
                        )
                    }
                }.onFailure { Log.w(TAG, "تعذّر تحليل الرسائل الواردة", it) }
                is ApiResult.Error -> Log.w(TAG, "فشل جلب الرسائل الواردة: ${response.message}")
            }
        }
    }

    /** عدد الرسائل المنتظرة في الطابور — GET /sms/queue */
    fun querySmsQueueCount() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/queue")) {
                is ApiResult.Success -> runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val root = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                    _smsQueueCount.value = (root["count"] as? Number)?.toInt()
                        ?: (root["queue_count"] as? Number)?.toInt() ?: 0
                }.onFailure { Log.w(TAG, "تعذّر تحليل عدد الطابور", it) }
                is ApiResult.Error -> Log.w(TAG, "فشل جلب عدد الطابور: ${response.message}")
            }
        }
    }

    /** إيقاف مهمة إرسال جارية — POST /sms/stop */
    fun stopSmsTask(taskId: Int) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            when (val response = client.request("POST", "/api/admin/dinstar/sms/stop?task_id=$taskId")) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("تم إيقاف مهمة الإرسال $taskId")
                    querySmsQueueCount()
                }
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل إيقاف المهمة" }, response.code)
            }
        }
    }

    // ═══════════════════ الجهاز والتوجيه ═══════════════════

    /** قدرات الطراز المكتشَف — GET /capabilities */
    fun getCapabilities() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/capabilities")) {
                is ApiResult.Success -> runCatching {
                    @Suppress("UNCHECKED_CAST")
                    _capabilities.value = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                }.onFailure { Log.w(TAG, "تعذّر تحليل قدرات الجهاز", it) }
                is ApiResult.Error -> Log.w(TAG, "فشل جلب القدرات: ${response.message}")
            }
        }
    }

    /** اكتشاف البوابات على الشبكة — POST /discover */
    fun discoverGateway() {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            when (val response = client.request("POST", "/api/admin/dinstar/discover")) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("اكتمل اكتشاف البوابات")
                    refreshStatus()
                    getCapabilities()
                }
                is ApiResult.Error -> _commandResult.value =
                    DinstarCommandResult.Error(response.message.ifBlank { "فشل اكتشاف البوابات" }, response.code)
            }
        }
    }

    /**
     * معاينة المنفذ الذي سيُستخدم لرقم ما — POST /routing/select
     *
     * الاختيار يجري في الخادم عمدًا: `DinstarLoadBalancer` يوازن عبر
     * **الأسطول كله** (كل البوابات) بالإشارة ومطابقة المشغّل
     * «داخل الشبكة» والاستخدام والدور، ويرى حالة لا يملكها التطبيق.
     * تكرار الخوارزمية هنا كان سيُنتج مصدرَي حقيقة يتفرّعان، ويعرض
     * للمستخدم منفذًا غير الذي تمرّ عبره مكالمته فعلًا.
     */
    fun previewRouting(number: String) {
        if (number.isBlank()) {
            _routingPreview.value = null
            return
        }
        viewModelScope.launch {
            val body = mapper.writeValueAsString(mapOf("number" to number))
            when (val response = client.request("POST", "/api/admin/dinstar/routing/select", body)) {
                is ApiResult.Success -> runCatching {
                    @Suppress("UNCHECKED_CAST")
                    _routingPreview.value = mapper.readValue(response.value, Map::class.java) as Map<String, Any?>
                }.onFailure { Log.w(TAG, "تعذّر تحليل معاينة التوجيه", it) }
                is ApiResult.Error -> {
                    _routingPreview.value = null
                    Log.w(TAG, "فشل معاينة التوجيه: ${response.message}")
                }
            }
        }
    }

    /** وصف مختصر لحالة التوجيه لعرضه تحت حقل الرقم. */
    fun getSelectionDescription(number: String?): String {
        val ports = _gatewayStatus.value.ports
        val available = ports.count { it.isAvailable }
        val operator = number?.let { YemenOperator.fromNumber(it) }
        val hasOnNet = operator != null && operator != YemenOperator.UNKNOWN &&
            ports.any { it.simType == operator && it.isAvailable }
        return when {
            available == 0 -> "لا توجد منافذ متاحة"
            hasOnNet -> "منفذ ${operator?.arabicName} مفضّل — مكالمة داخل الشبكة أقل كلفة"
            else -> "$available منفذ متاح"
        }
    }

    fun clearCommandResult() { _commandResult.value = null }

    private fun connectWebSocket() {
        wsBridge.connect(tokens.accessToken)
        viewModelScope.launch {
            wsBridge.wsEvents.collect { event ->
                when (event) {
                    is DinstarWsEvent.PortStatusChanged -> refreshStatus()
                    is DinstarWsEvent.CdrReceived -> queryCdr()
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

        /**
         * مهلة استقرار بعد إعادة تعيين منفذ أو تغيير طاقته: الشريحة
         * تحتاج ثوانٍ لإعادة التسجيل على الشبكة، والتحديث قبلها يُظهر
         * المنفذ غير مسجّل فيبدو العطل حيث لا عطل.
         */
        const val PORT_RESET_SETTLE_MS = 3_000L

        /** أكواد USSD: أرقام و`*` و`#` فقط، بطول معقول. */
        val USSD_PATTERN = Regex("^[*#0-9]{2,30}$")
    }
}
