package com.red.sovereign.features.dinstar

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.jsonBodyOf
import com.red.sovereign.core.parseJsonMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🏛️ YOUNES Dinstar ViewModel — إدارة أسطول بوابات GSM.
 *
 * يخدم [DinstarAdminScreen] (المنافذ/USSD/إعادة التشغيل) و
 * [DinstarSmsScreen] (إرسال مجمّع/وارد/نتائج). كل المسارات تحت
 * `/api/admin/dinstar/…` وتتطلب دور ADMIN.
 */
class DinstarViewModel(application: Application) : AndroidViewModel(application) {

    private val tokens = TokenStore(application)
    private val client = AuthorizedApiClient(tokens)
    private val wsBridge = DinstarWebSocketBridge()

    private val _gatewayStatus = MutableStateFlow(DinstarGatewayStatus())
    val gatewayStatus = _gatewayStatus.asStateFlow()

    /** حالة الأسطول كاملًا — عدة بوابات معًا. */
    private val _fleetStatus = MutableStateFlow(DinstarFleetStatus())
    val fleetStatus = _fleetStatus.asStateFlow()

    private val _cdrRecords = MutableStateFlow<List<DinstarCdr>>(emptyList())
    val cdrRecords = _cdrRecords.asStateFlow()

    private val _commandResult = MutableStateFlow<DinstarCommandResult?>(null)
    val commandResult = _commandResult.asStateFlow()

    private val _smsHistory = MutableStateFlow<List<DinstarSms>>(emptyList())
    val smsHistory = _smsHistory.asStateFlow()

    // ── الوارد الفوري ونتائج الإرسال المجمّع (تبويبا «واردة» و«نتائج») ──
    private val _incomingSms = MutableStateFlow<List<DinstarIncomingSms>>(emptyList())
    val incomingSms = _incomingSms.asStateFlow()

    private val _smsSendResults = MutableStateFlow<List<DinstarSmsResult>>(emptyList())
    val smsSendResults = _smsSendResults.asStateFlow()

    /** عدد الرسائل المنتظرة في طابور الجهاز — مؤشر تحميل البوابة. */
    private val _smsQueueCount = MutableStateFlow(0)
    val smsQueueCount = _smsQueueCount.asStateFlow()

    /**
     * بوابة العتاد الإدارية `red.dinstar.enabled` من GET /health/detailed.
     * null = غير معروف بعد (جارٍ الجلب أو 403 لغير الإدمن)؛ false = معطل
     * إدارياً (وضع الإنترنت فقط) فتُخفى أقسام الأسطول؛ true = العتاد متاح.
     */
    private val _dinstarEnabled = MutableStateFlow<Boolean?>(null)
    val dinstarEnabled = _dinstarEnabled.asStateFlow()

    init {
        refreshStatus()
        fetchDinstarEnabled()
        connectWebSocket()
    }

    /**
     * قراءة `dinstar.enabled` من GET /health/detailed (محمي ADMIN).
     * 403 لغير الإدمن تُبقي القيمة null — وقرار الإخفاء لغير الإدمن
     * يُحسم أصلاً ببوابة الدور، فلا تُسوء هذه القراءة شيئاً.
     */
    fun fetchDinstarEnabled() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/health/detailed")) {
                is ApiResult.Success -> runCatching {
                    val root = parseJsonMap(response.value)
                    val dinstar = root["dinstar"] as? Map<*, *>
                    (dinstar?.get("enabled") as? Boolean)?.let { _dinstarEnabled.value = it }
                }
                is ApiResult.Error -> Unit
            }
        }
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
            fetchDinstarEnabled()
            when (val response = client.request("GET", "/api/admin/dinstar/fleet/ports")) {
                is ApiResult.Success -> {
                    runCatching {
                        val root = parseJsonMap(response.value)
                        val entries = (root["gateways"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

                        val gateways = entries.map { entry ->
                            val gw = (entry["gateway"] as? Map<*, *>).orEmpty()
                            val rawPorts = (entry["ports"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

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
    private fun parsePort(raw: Map<*, *>): DinstarPort {
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
     * [ports] فارغة = يقرر الخادم/الجهاز المنفذ المتاح،
     * [encoding] الافتراضي AUTO يشتقّ الترميز من النص فلا تصل العربية
     * «?????» (GSM‑7 لا يحمل العربية، فتحتاج UCS2).
     *
     * تسجّل النتيجة في [smsSendResults] وتُحدّث [smsQueueCount] من
     * استجابة الجهاز، فيرى المسؤول ما وصل فعلاً بدل رسالة نجاح عامة.
     */
    fun sendSms(
        text: String,
        numbers: List<String>,
        ports: List<Int> = emptyList(),
        encoding: String = "AUTO"
    ) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mutableMapOf<String, Any?>(
                "text" to text,
                "param" to numbers.map { mapOf("number" to it) },
                "encoding" to encoding
            )
            if (ports.isNotEmpty()) body["port"] = ports
            when (val response = client.request("POST", "/api/admin/dinstar/sms/send", jsonBodyOf(body))) {
                is ApiResult.Success -> {
                    val parsed = runCatching { parseJsonMap(response.value) }.getOrNull()
                    val status = parsed?.get("status")?.toString() ?: "ACCEPTED"
                    val queue = (parsed?.get("queueCount") as? Number)?.toInt()
                    _smsSendResults.value = listOf(
                        DinstarSmsResult(
                            taskId = (parsed?.get("taskId") as? Number)?.toLong(),
                            numbers = numbers,
                            status = status,
                            queueCount = queue,
                            ports = ports,
                            encoding = encoding
                        )
                    ) + _smsSendResults.value
                    if (queue != null) _smsQueueCount.value = queue
                    _commandResult.value = DinstarCommandResult.Success(
                        if (numbers.size == 1) "تم إرسال الرسالة إلى ${numbers.first()}"
                        else "تم إرسال ${numbers.size} رسالة"
                    )
                    val newItems = numbers.map { DinstarSms(number = it, content = text, direction = "OUT") }
                    _smsHistory.value = newItems + _smsHistory.value
                }
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error(response.message ?: "فشل إرسال الرسائل")
            }
        }
    }

    fun sendSms(number: String, text: String) {
        sendSms(text, listOf(number))
    }

    /**
     * جلب الوارد المخزّن على الجهاز الآن — تبويب «واردة».
     *
     * البوابة تحفظ الوارد في ذاكرتها؛ المقبس الحي يوصل الجديد فقط، فلولا
     * هذا الجلب لبقيت الرسائل التي وصلت قبل فتح الشاشة غير مرئية.
     */
    fun fetchIncomingSms() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/incoming")) {
                is ApiResult.Success -> runCatching {
                    val root = parseJsonMap(response.value)
                    val items = (root["messages"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                    val fetched = items.mapNotNull { raw ->
                        val number = raw["number"]?.toString() ?: return@mapNotNull null
                        DinstarIncomingSms(
                            gatewayId = raw["gatewayId"]?.toString(),
                            port = (raw["port"] as? Number)?.toInt()
                                ?: (raw["port_index"] as? Number)?.toInt() ?: -1,
                            number = number,
                            text = raw["text"]?.toString().orEmpty(),
                            receivedAt = (raw["time"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }
                    // الدمج بالمفتاح (رقم + وقت) حتى لا تتكرر رسالة وصلت
                    // عبر المقبس ثم عادت في الجلب.
                    if (fetched.isNotEmpty()) {
                        val existing = _incomingSms.value
                        val known = existing.map { it.number to it.receivedAt }.toSet()
                        val fresh = fetched.filterNot { (it.number to it.receivedAt) in known }
                        if (fresh.isNotEmpty()) {
                            _incomingSms.value = (fresh + existing).sortedByDescending { it.receivedAt }
                        }
                    }
                }.onFailure { Log.w(TAG, "تعذّر تحليل الوارد", it) }
                is ApiResult.Error -> Unit
            }
        }
    }

    /** عدد رسائل الإرسال المنتظرة في طابور الجهاز. */
    fun fetchQueueCount() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/sms/queue")) {
                is ApiResult.Success -> runCatching {
                    val root = parseJsonMap(response.value)
                    _smsQueueCount.value = (root["sms_in_queue"] as? Number)?.toInt()
                        ?: (root["count"] as? Number)?.toInt() ?: 0
                }.onFailure { Log.w(TAG, "تعذّر تحليل طابور SMS", it) }
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
                    // الوارد الفوري: يصل عبر `DINSTAR_SMS` من الخادم ويُدرج
                    // في رأس القائمة، فيراه المسؤول بلا تحديث يدوي.
                    is DinstarWsEvent.IncomingSms -> {
                        val message = DinstarIncomingSms(
                            gatewayId = event.gatewayId,
                            port = event.port,
                            number = event.number,
                            text = event.text
                        )
                        _incomingSms.value = listOf(message) + _incomingSms.value
                        _smsHistory.value = listOf(
                            DinstarSms(number = event.number, content = event.text, direction = "IN")
                        ) + _smsHistory.value
                    }
                    else -> Unit
                }
            }
        }
    }

    fun resetPort(portIndex: Int) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$portIndex/reset", "{}")) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("تم إعادة تشغيل المنفذ $portIndex بنجاح")
                    refreshStatus()
                }
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error(response.message ?: "فشل إعادة تشغيل المنفذ")
            }
        }
    }

    fun sendUssd(portIndex: Int, code: String) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mapOf("code" to code)
            when (val response = client.request("POST", "/api/admin/dinstar/ports/$portIndex/ussd", jsonBodyOf(body))) {
                is ApiResult.Success -> {
                    _commandResult.value = DinstarCommandResult.Success("تم إرسال رمز USSD: $code. جاري الاستعلام...")
                    delay(2500)
                    pollUssdResult(portIndex)
                }
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error(response.message ?: "فشل إرسال كود USSD")
            }
        }
    }

    fun pollUssdResult(portIndex: Int) {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/ports/$portIndex/ussd")) {
                is ApiResult.Success -> {
                    runCatching {
                        val root = parseJsonMap(response.value)
                        val text = root["text"]?.toString() ?: root["result"]?.toString() ?: response.value
                        _commandResult.value = DinstarCommandResult.Success("نتيجة USSD (منفذ $portIndex):\n$text")
                    }.onFailure {
                        _commandResult.value = DinstarCommandResult.Success("نتيجة USSD: ${response.value}")
                    }
                }
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error("لم يتم استلام رد USSD بعد")
            }
        }
    }

    fun clearCommandResult() {
        _commandResult.value = null
    }

    fun queryCdr() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/cdr")) {
                is ApiResult.Success -> runCatching {
                    val root = parseJsonMap(response.value)
                    val items = (root["cdrs"] as? List<*>) ?: (root["records"] as? List<*>) ?: emptyList<Any>()
                    val records = items.filterIsInstance<Map<*, *>>().mapNotNull { raw ->
                        val number = raw["phoneNumber"]?.toString() ?: raw["number"]?.toString() ?: raw["caller"]?.toString() ?: return@mapNotNull null
                        val port = (raw["port"] as? Number)?.toInt() ?: -1
                        DinstarCdr(
                            id = raw["id"]?.toString().orEmpty(),
                            port = port,
                            phoneNumber = number,
                            direction = raw["direction"]?.toString() ?: "outgoing",
                            durationSeconds = (raw["durationSeconds"] as? Number)?.toInt() ?: (raw["duration"] as? Number)?.toInt() ?: 0,
                            startTime = (raw["startTime"] as? Number)?.toLong() ?: (raw["timestamp"] as? Number)?.toLong() ?: 0L,
                            callState = raw["callState"]?.toString() ?: raw["status"]?.toString() ?: "COMPLETED",
                            costYer = (raw["costYer"] as? Number)?.toInt() ?: 0
                        )
                    }
                    _cdrRecords.value = records
                }.onFailure { err ->
                    Log.e(TAG, "Failed to parse CDR records", err)
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Failed to query CDR records: ${response.message}")
                }
            }
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
