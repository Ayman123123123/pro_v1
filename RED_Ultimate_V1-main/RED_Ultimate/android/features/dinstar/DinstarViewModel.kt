package com.red.features.dinstar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 🏛️ YOUNES Dinstar ViewModel — النواة الاحترافية V2
 * 
 * التقنيات المستخدمة:
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ 1. SharedFlow  — أحداث Dinstar الحية (multicast, hot stream)  │
 * │ 2. StateFlow   — حالة UI المركزية (single source of truth)     │
 * │ 3. Exponential Backoff — إعادة اتصال ذكية                      │
 * │ 4. Weighted Fair Queuing — اختيار منفذ عادل                     │
 * │ 5. Circuit Breaker — حماية من الانهيار المتتابع                │
 * │ 6. Sliding Window — تتبع نسبة نجاح المكالمات                   │
 * │ 7. OkHttp ConnectionPool — تجمع اتصالات HTTP                    │
 * │ 8. Jackson ObjectMapper — تحليل JSON فعال                       │
 * │ 9. Atomic Variables — خيط آمن بدون أقفال                       │
 * │ 10. Coroutine SupervisorJob — عزل أعطال الفرعية                 │
 * └──────────────────────────────────────────────────────────────────┘
 */
class DinstarViewModel(
    private val backendUrl: String = "http://192.168.1.50:8080"
) : ViewModel() {

    companion object {
        private const val TAG = "RED.DinstarVM"
        private const val REFRESH_INTERVAL_MS = 10_000L
        private const val MAX_REFRESH_RETRIES = 3
        private const val SIGNAL_THRESHOLD = 20
        private const val CIRCUIT_BREAKER_THRESHOLD = 5      // 5 أعطال متتالية → دائرة مفتوحة
        private const val CIRCUIT_BREAKER_RESET_MS = 30_000L  // بعد 30ث → نصف مفتوحة
        private const val SUCCESS_WINDOW_SIZE = 20            // نافذة آخر 20 مكالمة
    }

    // ─── OkHttp مع ConnectionPool فعال ───
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))  // 5 اتصالات، 30ث keep-alive
        .retryOnConnectionFailure(true)
        .build()

    private val mapper = ObjectMapper()

    // ═══════════════════════════════════════════════════════
    // 📡 StateFlows — Single Source of Truth
    // ═══════════════════════════════════════════════════════

    private val _gatewayStatus = MutableStateFlow(DinstarGatewayStatus())
    val gatewayStatus: StateFlow<DinstarGatewayStatus> = _gatewayStatus.asStateFlow()

    private val _cdrRecords = MutableStateFlow<List<DinstarCdr>>(emptyList())
    val cdrRecords: StateFlow<List<DinstarCdr>> = _cdrRecords.asStateFlow()

    private val _commandResult = MutableStateFlow<DinstarCommandResult?>(null)
    val commandResult: StateFlow<DinstarCommandResult?> = _commandResult.asStateFlow()

    private val _connectionState = MutableStateFlow(BackendConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BackendConnectionState> = _connectionState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statistics = MutableStateFlow(DinstarStatistics())
    val statistics: StateFlow<DinstarStatistics> = _statistics.asStateFlow()

    // ─── SMS State ───
    private val _smsQueueCount = MutableStateFlow(0)
    val smsQueueCount: StateFlow<Int> = _smsQueueCount.asStateFlow()

    private val _incomingSms = MutableStateFlow<List<DinstarIncomingSms>>(emptyList())
    val incomingSms: StateFlow<List<DinstarIncomingSms>> = _incomingSms.asStateFlow()

    private val _smsSendResults = MutableStateFlow<List<DinstarSmsResult>>(emptyList())
    val smsSendResults: StateFlow<List<DinstarSmsResult>> = _smsSendResults.asStateFlow()

    // ═══════════════════════════════════════════════════════
    // 🔥 SharedFlow — أحداث حية (one-time events)
    // ═══════════════════════════════════════════════════════

    private val _dinstarEvents = MutableSharedFlow<DinstarEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** تدفق أحداث Dinstar الحية — يُستمع إليه من عدة أماكن */
    val dinstarEvents: SharedFlow<DinstarEvent> = _dinstarEvents.asSharedFlow()

    // ═══════════════════════════════════════════════════════
    // ⚡ Circuit Breaker — حماية من الانهيار
    // ═══════════════════════════════════════════════════════

    private val circuitFailureCount = AtomicInteger(0)
    private val circuitLastFailureTime = AtomicLong(0)
    private val circuitOpen = AtomicBoolean(false)

    private enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

    private fun getCircuitState(): CircuitState {
        if (!circuitOpen.get()) return CircuitState.CLOSED
        val elapsed = System.currentTimeMillis() - circuitLastFailureTime.get()
        return if (elapsed > CIRCUIT_BREAKER_RESET_MS) CircuitState.HALF_OPEN else CircuitState.OPEN
    }

    private fun recordCircuitSuccess() {
        circuitFailureCount.set(0)
        circuitOpen.set(false)
    }

    private fun recordCircuitFailure() {
        val failures = circuitFailureCount.incrementAndGet()
        circuitLastFailureTime.set(System.currentTimeMillis())
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpen.set(true)
            Log.w(TAG, "⚠️ Circuit Breaker OPEN — $failures consecutive failures")
            _dinstarEvents.tryEmit(DinstarEvent.CircuitBreakerOpen(failures))
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📊 Sliding Window Success Tracker
    // ═══════════════════════════════════════════════════════

    private val successWindow = ArrayDeque<Boolean>(SUCCESS_WINDOW_SIZE)

    private fun recordCallSuccess(success: Boolean) {
        synchronized(successWindow) {
            if (successWindow.size >= SUCCESS_WINDOW_SIZE) successWindow.removeFirst()
            successWindow.addLast(success)
        }
    }

    private fun getSuccessRate(): Float {
        synchronized(successWindow) {
            if (successWindow.isEmpty()) return 1f
            return successWindow.count { it }.toFloat() / successWindow.size
        }
    }

    // ─── Load Balancing State ───
    private val roundRobinCounter = AtomicInteger(0)
    private val portUsageCounter = AtomicIntegerArray(8) // تتبع استخدام كل منفذ

    // ─── Monitoring ───
    private var monitoringJob: Job? = null
    private val supervisorJob = SupervisorJob()

    // ═══════════════════════════════════════════════════════
    // 🔍 Discovery & Connection
    // ═══════════════════════════════════════════════════════

    fun discoverGateway() {
        viewModelScope.launch(supervisorJob) {
            _connectionState.value = BackendConnectionState.CONNECTING
            _isLoading.value = true
            _commandResult.value = DinstarCommandResult.Loading

            try {
                val response = apiGet("/api/admin/dinstar/discover")
                if (response != null) {
                    val success = response["success"] as? Boolean ?: false
                    if (success) {
                        _connectionState.value = BackendConnectionState.CONNECTED
                        _commandResult.value = DinstarCommandResult.Success("تم اكتشاف البوابة بنجاح")
                        recordCircuitSuccess()
                        refreshStatus()
                        startLiveMonitoring()
                        _dinstarEvents.tryEmit(DinstarEvent.GatewayDiscovered(response["gatewayIp"]?.toString() ?: ""))
                    } else {
                        _connectionState.value = BackendConnectionState.ERROR
                        _commandResult.value = DinstarCommandResult.Error(response["message"]?.toString() ?: "فشل الاكتشاف")
                        recordCircuitFailure()
                    }
                } else {
                    _connectionState.value = BackendConnectionState.ERROR
                    _commandResult.value = DinstarCommandResult.Error("لا يمكن الوصول للباكند")
                    recordCircuitFailure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discover failed", e)
                _connectionState.value = BackendConnectionState.ERROR
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
                recordCircuitFailure()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📊 Status & Port Info
    // ═══════════════════════════════════════════════════════

    fun refreshStatus() {
        viewModelScope.launch(supervisorJob) {
            if (getCircuitState() == CircuitState.OPEN) {
                Log.w(TAG, "Circuit OPEN — skipping refresh")
                return@launch
            }
            try {
                val response = apiGet("/api/admin/dinstar/status")
                if (response != null) {
                    val ports = parsePortsResponse(response)
                    _gatewayStatus.value = _gatewayStatus.value.copy(
                        isOnline = true, ports = ports, lastUpdated = System.currentTimeMillis()
                    )
                    _connectionState.value = BackendConnectionState.CONNECTED
                    recordCircuitSuccess()
                    computeStatistics()
                    _dinstarEvents.tryEmit(DinstarEvent.PortsUpdated(ports.size))
                } else {
                    _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
                    recordCircuitFailure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Status refresh failed", e)
                _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
                recordCircuitFailure()
            }
        }
    }

    fun getPortInfo(port: Int) {
        viewModelScope.launch(supervisorJob) {
            try {
                val response = apiGet("/api/admin/dinstar/ports/$port")
                if (response != null) {
                    val portInfo = response["status"] as? Map<String, Any?>
                    if (portInfo != null) {
                        val updatedPort = parsePortFromApi(portInfo, port)
                        val currentPorts = _gatewayStatus.value.ports.toMutableList()
                        if (port in currentPorts.indices) currentPorts[port] = updatedPort
                        else currentPorts.add(updatedPort)
                        _gatewayStatus.value = _gatewayStatus.value.copy(ports = currentPorts)
                        _dinstarEvents.tryEmit(DinstarEvent.PortInfoUpdated(port))
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Port info failed for port $port", e) }
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ Commands: Reset, USSD, SMS, CDR, Capabilities
    // ═══════════════════════════════════════════════════════

    fun resetPort(port: Int) {
        viewModelScope.launch(supervisorJob) {
            _commandResult.value = DinstarCommandResult.Loading
            try {
                val response = apiPost("/api/admin/dinstar/ports/$port/reset")
                if (response != null) {
                    _commandResult.value = DinstarCommandResult.Success("تم إعادة تعيين المنفذ $port بنجاح", mapOf("port" to port))
                    _dinstarEvents.tryEmit(DinstarEvent.PortReset(port))
                    delay(3000)
                    refreshStatus()
                } else {
                    _commandResult.value = DinstarCommandResult.Error("فشل إعادة التعيين")
                }
            } catch (e: Exception) {
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            }
        }
    }

    fun sendUssd(port: Int, code: String) {
        if (!code.matches(Regex("^[*#0-9]{2,30}$"))) {
            _commandResult.value = DinstarCommandResult.Error("كود USSD غير صالح")
            return
        }
        viewModelScope.launch(supervisorJob) {
            _commandResult.value = DinstarCommandResult.Loading
            try {
                val body = mapper.writeValueAsString(mapOf("code" to code))
                val response = apiPostWithBody("/api/admin/dinstar/ports/$port/ussd", body)
                if (response != null) {
                    _commandResult.value = DinstarCommandResult.Success("تم إرسال USSD: $code", response)
                    _dinstarEvents.tryEmit(DinstarEvent.UssdSent(port, code))
                }
            } catch (e: Exception) {
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            }
        }
    }

    /**
     * إرسال SMS عبر Dinstar — POST /api/admin/dinstar/sms/send
     * 
     * يدعم:
     * - إرسال فردي (نص + رقم)
     * - إرسال مجمّع (نص + أرقام متعددة + منافذ محددة)
     * - GSM 7-bit و UCS2 encoding
     * - user_id لتتبع حالة التسليم
     * - #param# لنصوص مخصصة لكل رقم
     */
    fun sendSms(
        text: String,
        numbers: List<String>,
        ports: List<Int> = emptyList(),
        encoding: String = "GSM7BIT", // GSM7BIT أو UCS2
        requestStatusReport: Boolean = true
    ) {
        viewModelScope.launch(supervisorJob) {
            _commandResult.value = DinstarCommandResult.Loading
            _dinstarEvents.tryEmit(DinstarEvent.SmsSending(numbers.size))

            try {
                val params = numbers.mapIndexed { idx, number ->
                    mapOf(
                        "number" to number,
                        "user_id" to (idx + 1)
                    )
                }
                val body = mutableMapOf<String, Any>(
                    "text" to text,
                    "param" to params,
                    "encoding" to encoding,
                    "request_status_report" to requestStatusReport
                )
                if (ports.isNotEmpty()) body["port"] = ports

                val json = mapper.writeValueAsString(body)
                val response = apiPostWithBody("/api/admin/dinstar/sms/send", json)

                if (response != null) {
                    val taskId = (response["task_id"] as? Number)?.toInt() ?: -1
                    val queueCount = (response["sms_in_queue"] as? Number)?.toInt() ?: 0
                    _smsQueueCount.value = queueCount
                    _commandResult.value = DinstarCommandResult.Success(
                        "تم إرسال ${numbers.size} رسالة (task=$taskId, queue=$queueCount)",
                        response
                    )
                    _dinstarEvents.tryEmit(DinstarEvent.SmsSent(taskId, numbers.size))
                } else {
                    _commandResult.value = DinstarCommandResult.Error("فشل إرسال SMS")
                }
            } catch (e: Exception) {
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            }
        }
    }

    /** جلب نتائج إرسال SMS — POST /api/admin/dinstar/sms/result */
    fun querySmsResult(userIds: List<Int> = emptyList()) {
        viewModelScope.launch(supervisorJob) {
            try {
                val body = mapper.writeValueAsString(mapOf("user_id" to userIds))
                val response = apiPostWithBody("/api/admin/dinstar/sms/result", body)
                if (response != null) {
                    @Suppress("UNCHECKED_CAST")
                    val results = (response["result"] as? List<Map<String, Any?>>) ?: emptyList()
                    _smsSendResults.value = results.map { parseSmsResult(it) }
                    _dinstarEvents.tryEmit(DinstarEvent.SmsResultsUpdated(results.size))
                }
            } catch (e: Exception) { Log.e(TAG, "SMS result query failed", e) }
        }
    }

    /** جلب SMS الواردة — GET /api/admin/dinstar/sms/incoming */
    fun queryIncomingSms() {
        viewModelScope.launch(supervisorJob) {
            try {
                val response = apiGet("/api/admin/dinstar/sms/incoming")
                if (response != null) {
                    @Suppress("UNCHECKED_CAST")
                    val smsList = (response["sms"] as? List<Map<String, Any?>>) ?: emptyList()
                    _incomingSms.value = smsList.map { parseIncomingSms(it) }
                    _dinstarEvents.tryEmit(DinstarEvent.IncomingSmsReceived(smsList.size))
                }
            } catch (e: Exception) { Log.e(TAG, "Incoming SMS query failed", e) }
        }
    }

    /** جلب عدد SMS في الطابور — GET /api/admin/dinstar/sms/queue */
    fun querySmsQueueCount() {
        viewModelScope.launch(supervisorJob) {
            try {
                val response = apiGet("/api/admin/dinstar/sms/queue")
                if (response != null) {
                    _smsQueueCount.value = (response["count"] as? Number)?.toInt() ?: 0
                }
            } catch (e: Exception) { Log.e(TAG, "SMS queue query failed", e) }
        }
    }

    /** إيقاف مهمة إرسال SMS — POST /api/admin/dinstar/sms/stop */
    fun stopSmsTask(taskId: Int) {
        viewModelScope.launch(supervisorJob) {
            try {
                val response = apiPost("/api/admin/dinstar/sms/stop?task_id=$taskId")
                if (response != null) {
                    _commandResult.value = DinstarCommandResult.Success("تم إيقاف المهمة $taskId")
                    _dinstarEvents.tryEmit(DinstarEvent.SmsTaskStopped(taskId))
                }
            } catch (e: Exception) { Log.e(TAG, "Stop SMS task failed", e) }
        }
    }

    /** جلب CDR — GET /api/admin/dinstar/cdr */
    fun queryCdr() {
        viewModelScope.launch(supervisorJob) {
            _isLoading.value = true
            try {
                val response = apiGet("/api/admin/dinstar/cdr")
                if (response != null) {
                    @Suppress("UNCHECKED_CAST")
                    val cdrList = (response["cdr"] as? List<Map<String, Any?>>) ?:
                                  (response["info"] as? List<Map<String, Any?>>) ?:
                                  (if (response is List<*>) response as? List<Map<String, Any?>> else null)
                    if (cdrList != null) {
                        _cdrRecords.value = cdrList.map { parseCdrFromApi(it) }
                        computeStatistics()
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "CDR query failed", e) }
            finally { _isLoading.value = false }
        }
    }

    /** جلب قدرات الجهاز */
    fun getCapabilities() {
        viewModelScope.launch(supervisorJob) {
            try {
                val response = apiGet("/api/admin/dinstar/capabilities")
                if (response != null) Log.i(TAG, "Capabilities: ${response.keys}")
            } catch (e: Exception) { Log.e(TAG, "Capabilities query failed", e) }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 Live Monitoring — Exponential Backoff
    // ═══════════════════════════════════════════════════════

    fun startLiveMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = viewModelScope.launch(supervisorJob + Dispatchers.IO) {
            var consecutiveFailures = 0
            while (isActive) {
                try {
                    when (getCircuitState()) {
                        CircuitState.OPEN -> {
                            delay(CIRCUIT_BREAKER_RESET_MS)
                            continue
                        }
                        CircuitState.HALF_OPEN -> {
                            Log.i(TAG, "Circuit HALF-OPEN — probing...")
                        }
                        CircuitState.CLOSED -> {}
                    }

                    val response = apiGet("/api/admin/dinstar/status")
                    if (response != null) {
                        val ports = parsePortsResponse(response)
                        _gatewayStatus.value = _gatewayStatus.value.copy(
                            isOnline = true, ports = ports, lastUpdated = System.currentTimeMillis()
                        )
                        _connectionState.value = BackendConnectionState.CONNECTED
                        consecutiveFailures = 0
                        recordCircuitSuccess()
                        computeStatistics()

                        // كشف تغييرات حالة المكالمة
                        detectCallStateChanges(ports)
                    } else {
                        consecutiveFailures++
                        recordCircuitFailure()
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    recordCircuitFailure()
                    Log.w(TAG, "Monitoring failed ($consecutiveFailures): ${e.message}")
                }

                val delayMs = if (consecutiveFailures == 0) REFRESH_INTERVAL_MS
                else minOf(REFRESH_INTERVAL_MS * (1L shl minOf(consecutiveFailures, 4)), 60_000L)

                if (consecutiveFailures >= MAX_REFRESH_RETRIES) {
                    _connectionState.value = BackendConnectionState.ERROR
                }
                delay(delayMs)
            }
        }
    }

    fun stopLiveMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /** كشف تغييرات حالة المكالمة وإصدار أحداث */
    private fun detectCallStateChanges(newPorts: List<DinstarPort>) {
        val oldPorts = _gatewayStatus.value.ports
        newPorts.forEach { newPort ->
            val oldPort = oldPorts.find { it.index == newPort.index }
            if (oldPort != null && oldPort.callState != newPort.callState) {
                _dinstarEvents.tryEmit(DinstarEvent.CallStateChanged(
                    port = newPort.index,
                    oldState = oldPort.callState,
                    newState = newPort.callState,
                    operator = newPort.operatorName
                ))
                // تسجيل نجاح/فشل المكالمة
                if (newPort.callState == "IDLE" && oldPort.callState == "ACTIVE") {
                    recordCallSuccess(true)
                    portUsageCounter.decrementAndGet(newPort.index.coerceIn(0, 7))
                }
            }
            if (newPort.callState == "ACTIVE" && (oldPort == null || oldPort.callState != "ACTIVE")) {
                portUsageCounter.incrementAndGet(newPort.index.coerceIn(0, 7))
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 خوارزمية اختيار المنفذ — Weighted Fair Queuing
    // ═══════════════════════════════════════════════════════

    /**
     * اختيار أفضل منفذ — WFQ (Weighted Fair Queuing)
     * 
     * الأوزان:
     * - w_signal = signalPercent (0-100) — الإشارة هي العامل الأهم
     * - w_operator = +35 إذا نفس مشغل الرقم (وفر تكلفة بين-مشغلي)
     * - w_usage = -usageCount × 5 — عقاب المنافذ الأكثر استخداماً (fair queuing)
     * - w_roundRobin = +8 للمنفذ التالي في الدور (تنويع)
     * - w_successRate = successRate × 10 — تفصيل المنافذ ذات النجاح الأعلى
     * 
     * المجموع: w_signal + w_operator + w_usage + w_roundRobin + w_successRate
     */
    fun selectOptimalPort(targetNumber: String? = null): DinstarPort? {
        val ports = _gatewayStatus.value.ports
        val available = ports.filter { it.isAvailable }
        if (available.isEmpty()) return null

        val preferredOperator = targetNumber?.let { YemenOperator.fromNumber(it) }
            ?.takeIf { it != YemenOperator.UNKNOWN }

        val scored = available.map { port ->
            val wSignal = port.signalPercent.toDouble()
            val wOperator = if (preferredOperator != null && port.simType == preferredOperator) 35.0 else 0.0
            val wUsage = -portUsageCounter.get(port.index.coerceIn(0, 7)) * 5.0
            val wRoundRobin = if (port.index == roundRobinCounter.get() % 8) 8.0 else 0.0
            val wSuccessRate = getSuccessRate() * 10.0
            val totalScore = wSignal + wOperator + wUsage + wRoundRobin + wSuccessRate

            PortScore(port, totalScore, wSignal, wOperator, wUsage)
        }

        val best = scored.maxByOrNull { it.totalScore }
        best?.port?.let { roundRobinCounter.incrementAndGet() }
        return best?.port
    }

    fun getSelectionDescription(number: String?): String {
        val available = _gatewayStatus.value.ports.count { it.isAvailable }
        val operator = number?.let { YemenOperator.fromNumber(it) }
        val hasMatch = operator != null && operator != YemenOperator.UNKNOWN &&
            _gatewayStatus.value.ports.any { it.simType == operator && it.isAvailable }
        val circuitState = getCircuitState()
        return when {
            circuitState == CircuitState.OPEN -> "⛔ Circuit Breaker مفتوح — لا اتصال"
            available == 0 -> "لا توجد منافذ متاحة"
            hasMatch -> "منفذ ${operator?.arabicName} مفضل (وفر تكلفة + إشارة عالية)"
            else -> "$available منفذ متاح — أفضل إشارة: ${_gatewayStatus.value.bestPortForCall?.signalPercent ?: 0}%"
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📊 Statistics
    // ═══════════════════════════════════════════════════════

    private fun computeStatistics() {
        val status = _gatewayStatus.value
        val cdr = _cdrRecords.value
        val callsByOperator = cdr.groupBy { it.operator }.mapValues { it.value.size }
        val totalDurationMin = cdr.sumOf { it.durationSeconds } / 60
        val totalCostYer = cdr.sumOf { it.costYer }
        val successCount = cdr.count { it.callState == "COMPLETED" || it.callState == "ANSWERED" }
        val successRate = if (cdr.isNotEmpty()) successCount.toFloat() / cdr.size else 0f

        _statistics.value = DinstarStatistics(
            totalCallsToday = cdr.size,
            totalDurationMinutesToday = totalDurationMin,
            totalCostYerToday = totalCostYer,
            callsByOperator = callsByOperator,
            avgSignalAllPorts = status.averageSignal,
            successRate = successRate,
            peakConcurrency = status.activeCallCount
        )
    }

    // ═══════════════════════════════════════════════════════
    // 🌐 HTTP API — مع Circuit Breaker
    // ═══════════════════════════════════════════════════════

    private suspend fun apiGet(path: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        if (getCircuitState() == CircuitState.OPEN) {
            Log.w(TAG, "Circuit OPEN — blocking GET $path")
            return@withContext null
        }
        try {
            val url = "$backendUrl$path"
            val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "API GET $path → HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.bytes() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(body, Map::class.java) as Map<String, Any?>
            }
        } catch (e: Exception) {
            Log.e(TAG, "API GET $path failed", e)
            null
        }
    }

    private suspend fun apiPost(path: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        if (getCircuitState() == CircuitState.OPEN) return@withContext null
        try {
            val url = "$backendUrl$path"
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .post("".toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.bytes() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(body, Map::class.java) as Map<String, Any?>
            }
        } catch (e: Exception) { Log.e(TAG, "API POST $path failed", e); null }
    }

    private suspend fun apiPostWithBody(path: String, jsonBody: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        if (getCircuitState() == CircuitState.OPEN) return@withContext null
        try {
            val url = "$backendUrl$path"
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .header("Content-Type", "application/json").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.bytes() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: Exception) { Log.e(TAG, "API POST $path with body failed", e); null }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 Parsers
    // ═══════════════════════════════════════════════════════

    private fun parsePortsResponse(response: Map<String, Any?>): List<DinstarPort> {
        @Suppress("UNCHECKED_CAST")
        val portsList = when (response) {
            is List<*> -> response as? List<Map<String, Any?>>
            is Map<*, *> -> (response["info"] as? List<Map<String, Any?>>) ?:
                            (response["ports"] as? List<Map<String, Any?>>)
            else -> null
        }
        return portsList?.mapIndexed { idx, raw -> parsePortFromApi(raw, idx) } ?: emptyList()
    }

    private fun parsePortFromApi(raw: Map<String, Any?>, fallbackIndex: Int): DinstarPort {
        val index = (raw["index"] as? Number)?.toInt() ?: (raw["port"] as? Number)?.toInt() ?: fallbackIndex
        val signalRaw = (raw["signalRaw"] as? Number)?.toInt() ?: (raw["signal"] as? Number)?.toInt() ?: 0
        val signalPercent = (raw["signal"] as? Number)?.toInt() ?: ((signalRaw / 31.0 * 100).toInt())
        val regState = raw["status"]?.toString() ?: raw["reg"]?.toString() ?: "UNREGISTERED"
        val callState = raw["callState"]?.toString() ?: raw["callstate"]?.toString() ?: "IDLE"
        val operatorStr = raw["operator"]?.toString() ?: raw["operatorName"]?.toString() ?: ""
        val simType = YemenOperator.fromApiOperatorName(operatorStr)
        val isHealthy = regState == "REGISTERED" && callState != "ACTIVE" && signalPercent >= SIGNAL_THRESHOLD

        return DinstarPort(
            index = index, radioType = raw["radioType"]?.toString() ?: "GSM",
            registrationState = regState, callState = callState,
            signalPercent = signalPercent.coerceIn(0, 100), signalRaw = signalRaw.coerceIn(0, 31),
            gprsState = raw["gprs"]?.toString() ?: "DETACH",
            operatorName = if (operatorStr.isNotBlank()) operatorStr else simType.arabicName,
            numberMasked = raw["numberMasked"]?.toString() ?: raw["number"]?.toString(),
            imsiMasked = raw["imsiMasked"]?.toString() ?: raw["imsi"]?.toString(),
            iccidMasked = raw["iccidMasked"]?.toString() ?: raw["iccid"]?.toString(),
            simType = simType, isHealthy = isHealthy
        )
    }

    private fun parseCdrFromApi(raw: Map<String, Any?>): DinstarCdr = DinstarCdr(
        id = raw["id"]?.toString() ?: "", port = (raw["port"] as? Number)?.toInt() ?: 0,
        phoneNumber = raw["number"]?.toString() ?: raw["destination_number"]?.toString() ?: "",
        direction = raw["direction"]?.toString() ?: "outgoing",
        durationSeconds = (raw["duration"] as? Number)?.toInt() ?: (raw["billsec"] as? Number)?.toInt() ?: 0,
        startTime = (raw["start_date"] as? String)?.let { parseDateToEpoch(it) } ?: 0L,
        callState = raw["disposition"]?.toString() ?: raw["hangup"]?.toString() ?: "COMPLETED",
        signalStrength = (raw["signal"] as? Number)?.toInt() ?: 0,
        operatorName = raw["operator"]?.toString() ?: ""
    )

    private fun parseSmsResult(raw: Map<String, Any?>): DinstarSmsResult = DinstarSmsResult(
        port = (raw["port"] as? Number)?.toInt() ?: 0,
        userId = (raw["user_id"] as? Number)?.toInt() ?: 0,
        number = raw["number"]?.toString() ?: "",
        time = raw["time"]?.toString() ?: "",
        status = raw["status"]?.toString() ?: "UNKNOWN",
        count = (raw["count"] as? Number)?.toInt() ?: 0,
        successCount = (raw["succ_count"] as? Number)?.toInt() ?: 0,
        refId = (raw["ref_id"] as? Number)?.toInt() ?: 0
    )

    private fun parseIncomingSms(raw: Map<String, Any?>): DinstarIncomingSms = DinstarIncomingSms(
        id = (raw["incoming_sms_id"] as? Number)?.toLong() ?: 0L,
        port = (raw["port"] as? Number)?.toInt() ?: 0,
        number = raw["number"]?.toString() ?: "",
        smsc = raw["smsc"]?.toString() ?: "",
        timestamp = raw["timestamp"]?.toString() ?: "",
        text = raw["text"]?.toString() ?: ""
    )

    private fun parseDateToEpoch(dateStr: String): Long {
        return runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }

    // ═══════════════════════════════════════════════════════
    // 🧹 Lifecycle
    // ═══════════════════════════════════════════════════════

    fun clearCommandResult() { _commandResult.value = null }

    override fun onCleared() {
        super.onCleared()
        stopLiveMonitoring()
        supervisorJob.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

// ━━━━ نماذج SMS ━━━━

data class DinstarSmsResult(
    val port: Int, val userId: Int, val number: String,
    val time: String, val status: String,
    val count: Int, val successCount: Int, val refId: Int
)

data class DinstarIncomingSms(
    val id: Long, val port: Int, val number: String,
    val smsc: String, val timestamp: String, val text: String
)

// ━━━━ أحداث Dinstar الحية ━━━━

sealed class DinstarEvent {
    data class GatewayDiscovered(val ip: String) : DinstarEvent()
    data class PortsUpdated(val count: Int) : DinstarEvent()
    data class PortInfoUpdated(val port: Int) : DinstarEvent()
    data class PortReset(val port: Int) : DinstarEvent()
    data class UssdSent(val port: Int, val code: String) : DinstarEvent()
    data class SmsSending(val count: Int) : DinstarEvent()
    data class SmsSent(val taskId: Int, val count: Int) : DinstarEvent()
    data class SmsTaskStopped(val taskId: Int) : DinstarEvent()
    data class SmsResultsUpdated(val count: Int) : DinstarEvent()
    data class IncomingSmsReceived(val count: Int) : DinstarEvent()
    data class CallStateChanged(val port: Int, val oldState: String, val newState: String, val operator: String) : DinstarEvent()
    data class CircuitBreakerOpen(val failures: Int) : DinstarEvent()
}

data class PortScore(
    val port: DinstarPort,
    val totalScore: Double,
    val wSignal: Double,
    val wOperator: Double,
    val wUsage: Double
)
