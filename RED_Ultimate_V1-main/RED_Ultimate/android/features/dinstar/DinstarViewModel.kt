package com.red.features.dinstar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/**
 * 🏛️ YOUNES Dinstar ViewModel — العقل المركزي لبوابة DINSTAR
 * 
 * المسؤوليات:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ 1. جلب حالة المنافذ من الباكند (GET /api/admin/dinstar/status) │
 * │ 2. اكتشاف البوابة (GET /api/admin/dinstar/discover)           │
 * │ 3. إعادة تعيين المنفذ (POST /api/admin/dinstar/ports/{p}/reset) │
 * │ 4. إرسال USSD (POST /api/admin/dinstar/ports/{p}/ussd)       │
 * │ 5. جلب CDR (GET /api/admin/dinstar/cdr)                      │
 * │ 6. المراقبة الحية — تحديث تلقائي كل 10 ثواني                 │
 * │ 7. اختيار أفضل منفذ للمكالمة (Signal-Based Load Balancing)    │
 * │ 8. تتبع إحصائيات المكالمات                                    │
 * └──────────────────────────────────────────────────────────────┘
 * 
 * خوارزمية اختيار المنفذ:
 * 1. تصفية: فقط المنافذ المسجلة + بدون مكالمة نشطة + إشارة ≥ 20%
 * 2. ترجيح: المنفذ ذو الإشارة الأعلى يُفضل
 * 3. تنويع: إذا كان هناك منفذ بنفس الإشارة، نختار الأقل استخداماً (round-robin)
 * 4. مشغل: إذا كان الرقم يمنياً، نفضل منفذ نفس المشغل
 * 
 * @property backendUrl عنوان الباكند (مثال: http://192.168.1.50:8080)
 */
class DinstarViewModel(
    private val backendUrl: String = "http://192.168.1.50:8080"
) : ViewModel() {

    companion object {
        private const val TAG = "RED.DinstarVM"
        private const val REFRESH_INTERVAL_MS = 10_000L // كل 10 ثواني
        private const val MAX_REFRESH_RETRIES = 3
        private const val SIGNAL_THRESHOLD = 20 // الحد الأدنى للإشارة %
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mapper = ObjectMapper()

    // ─── State Flows ───

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

    // ─── Round-Robin Counter (لتنويع المكالمات بين المنافذ) ───
    private val roundRobinCounter = java.util.concurrent.atomic.AtomicInteger(0)

    // ─── Live Monitoring Job ───
    private var monitoringJob: Job? = null

    // ═══════════════════════════════════════════════════════
    // 🔍 الاكتشاف والاتصال
    // ═══════════════════════════════════════════════════════

    /**
     * اكتشاف البوابة — يتصل بالباكند ويطلب /api/admin/dinstar/discover
     * الباكند بدوره يتصل بجهاز Dinstar عبر Digest Auth
     */
    fun discoverGateway() {
        viewModelScope.launch {
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
                        // جلب الحالة الكاملة بعد الاكتشاف
                        refreshStatus()
                        // بدء المراقبة الحية
                        startLiveMonitoring()
                    } else {
                        _connectionState.value = BackendConnectionState.ERROR
                        _commandResult.value = DinstarCommandResult.Error(
                            response["message"]?.toString() ?: "فشل اكتشاف البوابة"
                        )
                    }
                } else {
                    _connectionState.value = BackendConnectionState.ERROR
                    _commandResult.value = DinstarCommandResult.Error("لا يمكن الوصول للباكند")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discover failed", e)
                _connectionState.value = BackendConnectionState.ERROR
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📊 جلب الحالة والمنافذ
    // ═══════════════════════════════════════════════════════

    /**
     * جلب حالة جميع المنافذ — GET /api/admin/dinstar/status
     * يُحدّث gatewayStatus فوراً
     */
    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val response = apiGet("/api/admin/dinstar/status")
                if (response != null) {
                    // الرد هو List<Map<String, Any?>> — كل خريطة هي منفذ
                    @Suppress("UNCHECKED_CAST")
                    val portsList = when (response) {
                        is List<*> -> response as? List<Map<String, Any?>>
                        is Map<*, *> -> {
                            // إذا كان الرد ملفوفاً في كائن
                            (response["info"] as? List<Map<String, Any?>>) ?: 
                            (response["ports"] as? List<Map<String, Any?>>) ?:
                            listOf(response as Map<String, Any?>)
                        }
                        else -> null
                    }

                    if (portsList != null) {
                        val ports = portsList.mapIndexed { idx, raw ->
                            parsePortFromApi(raw, idx)
                        }
                        
                        val previous = _gatewayStatus.value
                        _gatewayStatus.value = previous.copy(
                            isOnline = true,
                            ports = ports,
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        _connectionState.value = BackendConnectionState.CONNECTED
                        
                        // تحديث الإحصائيات
                        computeStatistics()
                    }
                } else {
                    _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Status refresh failed", e)
                _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
            }
        }
    }

    /**
     * جلب معلومات منفذ واحد — GET /api/admin/dinstar/ports/{port}
     */
    fun getPortInfo(port: Int) {
        viewModelScope.launch {
            try {
                val response = apiGet("/api/admin/dinstar/ports/$port")
                if (response != null) {
                    val portInfo = response["status"] as? Map<String, Any?>
                    if (portInfo != null) {
                        val updatedPort = parsePortFromApi(portInfo, port)
                        val currentPorts = _gatewayStatus.value.ports.toMutableList()
                        if (port in currentPorts.indices) {
                            currentPorts[port] = updatedPort
                        } else {
                            currentPorts.add(updatedPort)
                        }
                        _gatewayStatus.value = _gatewayStatus.value.copy(ports = currentPorts)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Port info failed for port $port", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ أوامر التحكم
    // ═══════════════════════════════════════════════════════

    /**
     * إعادة تعيين منفذ — POST /api/admin/dinstar/ports/{port}/reset
     * يُعيد تشغيل وحدة SIM في المنفذ المحدد
     */
    fun resetPort(port: Int) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            try {
                val response = apiPost("/api/admin/dinstar/ports/$port/reset")
                if (response != null) {
                    _commandResult.value = DinstarCommandResult.Success(
                        "تم إعادة تعيين المنفذ $port بنجاح",
                        mapOf("port" to port)
                    )
                    // تحديث الحالة بعد التعيين
                    delay(3000) // انتظار 3 ثواني حتى يعود المنفذ
                    refreshStatus()
                } else {
                    _commandResult.value = DinstarCommandResult.Error("فشل إعادة التعيين")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reset port $port failed", e)
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            }
        }
    }

    /**
     * إرسال كود USSD — POST /api/admin/dinstar/ports/{port}/ussd
     * مثال: *123# لمعرفة الرصيد
     */
    fun sendUssd(port: Int, code: String) {
        if (!code.matches(Regex("^[*#0-9]{2,30}$"))) {
            _commandResult.value = DinstarCommandResult.Error("كود USSD غير صالح")
            return
        }

        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            try {
                val body = mapper.writeValueAsString(mapOf("code" to code))
                val response = apiPostWithBody("/api/admin/dinstar/ports/$port/ussd", body)
                if (response != null) {
                    _commandResult.value = DinstarCommandResult.Success(
                        "تم إرسال USSD: $code",
                        response
                    )
                } else {
                    _commandResult.value = DinstarCommandResult.Error("فشل إرسال USSD")
                }
            } catch (e: Exception) {
                Log.e(TAG, "USSD failed for port $port", e)
                _commandResult.value = DinstarCommandResult.Error("خطأ: ${e.message}")
            }
        }
    }

    /**
     * جلب سجل المكالمات CDR — GET /api/admin/dinstar/cdr
     */
    fun queryCdr() {
        viewModelScope.launch {
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
                    _commandResult.value = DinstarCommandResult.Success("تم جلب سجل المكالمات")
                }
            } catch (e: Exception) {
                Log.e(TAG, "CDR query failed", e)
                _commandResult.value = DinstarCommandResult.Error("خطأ في جلب السجل: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * جلب قدرات الجهاز — GET /api/admin/dinstar/capabilities
     */
    fun getCapabilities() {
        viewModelScope.launch {
            try {
                val response = apiGet("/api/admin/dinstar/capabilities")
                if (response != null) {
                    Log.i(TAG, "Dinstar capabilities: ${response.keys}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capabilities query failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 المراقبة الحية (Live Monitoring)
    // ═══════════════════════════════════════════════════════

    /**
     * بدء المراقبة الحية — تحديث تلقائي كل REFRESH_INTERVAL_MS
     * يستخدم خوارزمية Exponential Backoff عند الفشل
     */
    fun startLiveMonitoring() {
        if (monitoringJob?.isActive == true) return
        
        monitoringJob = viewModelScope.launch {
            var consecutiveFailures = 0
            
            while (isActive) {
                try {
                    val response = apiGet("/api/admin/dinstar/status")
                    if (response != null) {
                        @Suppress("UNCHECKED_CAST")
                        val portsList = when (response) {
                            is List<*> -> response as? List<Map<String, Any?>>
                            is Map<*, *> -> (response["info"] as? List<Map<String, Any?>>) ?:
                                            (response["ports"] as? List<Map<String, Any?>>)
                            else -> null
                        }
                        
                        if (portsList != null) {
                            val ports = portsList.mapIndexed { idx, raw -> parsePortFromApi(raw, idx) }
                            _gatewayStatus.value = _gatewayStatus.value.copy(
                                isOnline = true,
                                ports = ports,
                                lastUpdated = System.currentTimeMillis()
                            )
                            _connectionState.value = BackendConnectionState.CONNECTED
                            consecutiveFailures = 0
                            computeStatistics()
                        }
                    } else {
                        consecutiveFailures++
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    Log.w(TAG, "Monitoring poll failed ($consecutiveFailures): ${e.message}")
                }
                
                // Exponential Backoff: 10s, 20s, 40s, 80s (max 60s)
                val delayMs = if (consecutiveFailures == 0) {
                    REFRESH_INTERVAL_MS
                } else {
                    minOf(REFRESH_INTERVAL_MS * (1L shl minOf(consecutiveFailures, 4)), 60_000L)
                }
                
                if (consecutiveFailures >= MAX_REFRESH_RETRIES && _connectionState.value != BackendConnectionState.DISCONNECTED) {
                    _connectionState.value = BackendConnectionState.ERROR
                }
                
                delay(delayMs)
            }
        }
    }

    /**
     * إيقاف المراقبة الحية
     */
    fun stopLiveMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 خوارزمية اختيار المنفذ الذكية
    // ═══════════════════════════════════════════════════════

    /**
     * اختيار أفضل منفذ لإجراء مكالمة PSTN
     * 
     * الخوارزمية:
     * 1. تصفية: فقط المنافذ المسجلة + IDLE + إشارة ≥ SIGNAL_THRESHOLD
     * 2. إذا كان الرقم يمنياً: نُفضل منفذ نفس المشغل (أقل تكلفة بين-مشغلي)
     * 3. ترتيب حسب: إشارة (نزولي) → آخر استخدام (تصاعدي)
     * 4. إذا تعادل: round-robin للتنويع
     * 
     * @param targetNumber الرقم المطلوب الاتصال به (لتحديد المشغل المفضل)
     * @return أفضل منفذ أو null إذا لم يوجد منفذ متاح
     */
    fun selectOptimalPort(targetNumber: String? = null): DinstarPort? {
        val ports = _gatewayStatus.value.ports
        val available = ports.filter { it.isAvailable }
        
        if (available.isEmpty()) return null

        // خطوة 1: تحديد المشغل المفضل إذا كان الرقم يمنياً
        val preferredOperator = targetNumber?.let { YemenOperator.fromNumber(it) }
            ?.takeIf { it != YemenOperator.UNKNOWN }

        // خطوة 2: ترجيح المنافذ حسب المشغل
        val scored = available.map { port ->
            val signalScore = port.signalPercent.toDouble()
            val operatorBonus = if (preferredOperator != null && port.simType == preferredOperator) 30.0 else 0.0
            val roundRobinBonus = if (port.index == roundRobinCounter.get() % ports.size) 5.0 else 0.0
            Triple(port, signalScore + operatorBonus + roundRobinBonus, signalScore)
        }

        // خطوة 3: اختيار الأعلى ترجيحاً
        val best = scored.maxByOrNull { it.second }
        
        // خطوة 4: تحديث round-robin
        best?.first?.let { roundRobinCounter.incrementAndGet() }
        
        return best?.first
    }

    /**
     * وصف خوارزمية الاختيار بالعربية
     */
    fun getSelectionDescription(number: String?): String {
        val available = _gatewayStatus.value.ports.count { it.isAvailable }
        val operator = number?.let { YemenOperator.fromNumber(it) }
        val hasMatchingPort = operator != null && operator != YemenOperator.UNKNOWN &&
            _gatewayStatus.value.ports.any { it.simType == operator && it.isAvailable }
        
        return when {
            available == 0 -> "لا توجد منافذ متاحة"
            hasMatchingPort -> "منفذ ${operator?.arabicName} مفضل (إشارة عالية + نفس المشغل)"
            else -> "$available منفذ متاح — أفضل إشارة: ${_gatewayStatus.value.bestPortForCall?.signalPercent ?: 0}%"
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📊 الإحصائيات
    // ═══════════════════════════════════════════════════════

    private fun computeStatistics() {
        val status = _gatewayStatus.value
        val cdr = _cdrRecords.value
        
        val callsByOperator = cdr.groupBy { it.operator }.mapValues { it.value.size }
        val totalDurationMin = cdr.sumOf { it.durationSeconds } / 60
        val totalCostYer = cdr.sumOf { it.costYer }
        val successCount = cdr.count { it.callState == "COMPLETED" || it.callState == "ANSWERED" }
        val successRate = if (cdr.isNotEmpty()) successCount.toFloat() / cdr.size else 0f
        val peakConcurrent = status.activeCallCount // تقريب

        _statistics.value = DinstarStatistics(
            totalCallsToday = cdr.size,
            totalDurationMinutesToday = totalDurationMin,
            totalCostYerToday = totalCostYer,
            callsByOperator = callsByOperator,
            avgSignalAllPorts = status.averageSignal,
            successRate = successRate,
            peakConcurrency = peakConcurrent
        )
    }

    // ═══════════════════════════════════════════════════════
    // 🌐 HTTP API Helpers
    // ═══════════════════════════════════════════════════════

    private suspend fun apiGet(path: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val url = "$backendUrl$path"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

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
        try {
            val url = "$backendUrl$path"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(okhttp3.RequestBody.Companion.create(null, ByteArray(0)))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "API POST $path → HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.bytes() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(body, Map::class.java) as Map<String, Any?>
            }
        } catch (e: Exception) {
            Log.e(TAG, "API POST $path failed", e)
            null
        }
    }

    private suspend fun apiPostWithBody(path: String, jsonBody: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val url = "$backendUrl$path"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonBody.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "API POST $path → HTTP ${response.code}")
                    return@withContext null
                }
                val responseBody = response.body?.bytes() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(responseBody, Map::class.java) as Map<String, Any?>
            }
        } catch (e: Exception) {
            Log.e(TAG, "API POST $path with body failed", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 Parsers
    // ═══════════════════════════════════════════════════════

    private fun parsePortFromApi(raw: Map<String, Any?>, fallbackIndex: Int): DinstarPort {
        val index = (raw["index"] as? Number)?.toInt() ?: (raw["port"] as? Number)?.toInt() ?: fallbackIndex
        val signalRaw = (raw["signalRaw"] as? Number)?.toInt() ?: (raw["signal"] as? Number)?.toInt() ?: 0
        val signalPercent = (raw["signal"] as? Number)?.toInt() ?: ((signalRaw / 31.0 * 100).toInt())
        val regState = raw["status"]?.toString() ?: raw["reg"]?.toString() ?: "UNREGISTERED"
        val callState = raw["callState"]?.toString() ?: raw["callstate"]?.toString() ?: "IDLE"
        val operatorStr = raw["operator"]?.toString() ?: raw["operatorName"]?.toString() ?: ""
        val number = raw["numberMasked"]?.toString() ?: raw["number"]?.toString()
        val imsi = raw["imsiMasked"]?.toString() ?: raw["imsi"]?.toString()
        val iccid = raw["iccidMasked"]?.toString() ?: raw["iccid"]?.toString()
        
        val simType = YemenOperator.fromApiOperatorName(operatorStr)
        
        val isHealthy = regState == "REGISTERED" && 
            callState != "ACTIVE" && 
            signalPercent >= SIGNAL_THRESHOLD

        return DinstarPort(
            index = index,
            radioType = raw["radioType"]?.toString() ?: raw["type"]?.toString() ?: "GSM",
            registrationState = regState,
            callState = callState,
            signalPercent = signalPercent.coerceIn(0, 100),
            signalRaw = signalRaw.coerceIn(0, 31),
            gprsState = raw["gprs"]?.toString() ?: "DETACH",
            operatorName = if (operatorStr.isNotBlank()) operatorStr else simType.arabicName,
            numberMasked = number,
            imsiMasked = imsi,
            iccidMasked = iccid,
            simType = simType,
            isHealthy = isHealthy
        )
    }

    private fun parseCdrFromApi(raw: Map<String, Any?>): DinstarCdr {
        return DinstarCdr(
            id = raw["id"]?.toString() ?: "",
            port = (raw["port"] as? Number)?.toInt() ?: 0,
            phoneNumber = raw["number"]?.toString() ?: raw["dst"]?.toString() ?: "",
            direction = raw["direction"]?.toString() ?: "outgoing",
            durationSeconds = (raw["duration"] as? Number)?.toInt() ?: (raw["billsec"] as? Number)?.toInt() ?: 0,
            startTime = (raw["startTime"] as? Number)?.toLong() ?: (raw["calldate"] as? Number)?.toLong() ?: 0L,
            endTime = (raw["endTime"] as? Number)?.toLong() ?: 0L,
            callState = raw["disposition"]?.toString() ?: raw["status"]?.toString() ?: "COMPLETED",
            signalStrength = (raw["signal"] as? Number)?.toInt() ?: 0,
            operatorName = raw["operator"]?.toString() ?: ""
        )
    }

    // ═══════════════════════════════════════════════════════
    // 🧹 Lifecycle
    // ═══════════════════════════════════════════════════════

    fun clearCommandResult() {
        _commandResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveMonitoring()
        client.dispatcher.executorService.shutdown()
    }
}

// ─── Extension helpers ───

private fun String.toMediaType() = okhttp3.MediaType.Companion.toMediaType(this)
private fun String.toRequestBody(mediaType: okhttp3.MediaType) = 
    okhttp3.RequestBody.Companion.toRequestBody(mediaType, this.toByteArray())
