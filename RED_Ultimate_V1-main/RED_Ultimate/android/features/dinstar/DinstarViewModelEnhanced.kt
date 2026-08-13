package com.red.sovereign.features.dinstar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.data.dinstar.DinstarApiManager
import com.red.sovereign.data.dinstar.DinstarWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel محسّن لـ DINSTAR
 * يدعم WebSocket والتحكم الكامل بالمنافذ
 */
class DinstarViewModel : ViewModel(), DinstarWebSocketClient.DinstarWebSocketListener {

    private var apiManager: DinstarApiManager? = null
    private var webSocketClient: DinstarWebSocketClient? = null

    // ===== State Flows =====
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus.asStateFlow()

    private val _ports = MutableStateFlow<List<PortInfo>>(emptyList())
    val ports: StateFlow<List<PortInfo>> = _ports.asStateFlow()

    private val _cdrRecords = MutableStateFlow<List<CdrRecord>>(emptyList())
    val cdrRecords: StateFlow<List<CdrRecord>> = _cdrRecords.asStateFlow()

    private val _incomingSms = MutableStateFlow<List<SmsMessage>>(emptyList())
    val incomingSms: StateFlow<List<SmsMessage>> = _incomingSms.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ===== Data Classes =====

    data class ConnectionState(
        val isConnected: Boolean,
        val message: String = ""
    ) {
        companion object {
            val Disconnected = ConnectionState(false, "غير متصل")
            val Connecting = ConnectionState(false, "جاري الاتصال...")
            val Connected = ConnectionState(true, "متصل")
        }
    }

    data class DeviceStatus(
        val cpuUsed: String?,
        val memoryTotal: String?,
        val memoryUsed: String?,
        val memoryFree: String?,
        val flashTotal: String?,
        val flashUsed: String?,
        val flashFree: String?,
        val temperature: String?,
        val uptime: String?
    )

    data class PortInfo(
        val port: Int,
        val status: String,
        val signal: Int,
        val operator: String?,
        val imsi: String?,
        val imei: String?,
        val iccid: String?,
        val number: String?,
        val powerState: Boolean = true,
        val callForwardEnabled: Boolean = false,
        val callForwardNumber: String? = null
    )

    data class CdrRecord(
        val port: Int,
        val startTime: String?,
        val answerTime: String?,
        val endTime: String?,
        val duration: Int,
        val callerNumber: String?,
        val calleeNumber: String?,
        val direction: String?,
        val callType: String?,
        val codec: String?,
        val hangupCause: String?
    )

    data class SmsMessage(
        val port: Int,
        val sender: String?,
        val text: String?,
        val timestamp: String?
    )

    data class Alert(
        val gatewayId: String,
        val type: String,
        val severity: String,
        val message: String,
        val timestamp: Long
    )

    // ===== Initialization =====

    fun initialize(baseUrl: String, username: String, password: String, wsUrl: String) {
        apiManager = DinstarApiManager(baseUrl, username, password)
        
        webSocketClient = DinstarWebSocketClient(wsUrl, this).also {
            _connectionState.value = ConnectionState.Connecting
            it.connect()
        }
    }

    // ===== Public Methods =====

    fun refreshDeviceStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            apiManager?.getDeviceStatus { result ->
                result.fold(
                    onSuccess = { status ->
                        _deviceStatus.value = DeviceStatus(
                            cpuUsed = status["cpu_used"] as? String,
                            memoryTotal = status["memory_total"] as? String,
                            memoryUsed = status["memory_used"] as? String,
                            memoryFree = status["memory_free"] as? String,
                            flashTotal = status["flash_total"] as? String,
                            flashUsed = status["flash_used"] as? String,
                            flashFree = status["flash_free"] as? String,
                            temperature = status["temperature"] as? String,
                            uptime = status["uptime"] as? String
                        )
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل جلب حالة الجهاز: ${error.message}"
                    }
                )
                _isLoading.value = false
            }
        }
    }

    fun refreshPorts() {
        viewModelScope.launch {
            _isLoading.value = true
            apiManager?.getPortInfo { result ->
                result.fold(
                    onSuccess = { ports ->
                        _ports.value = ports.map { port ->
                            PortInfo(
                                port = port["port"] as? Int ?: 0,
                                status = port["status"] as? String ?: "",
                                signal = port["signal"] as? Int ?: 0,
                                operator = port["operator"] as? String,
                                imsi = port["imsi"] as? String,
                                imei = port["imei"] as? String,
                                iccid = port["iccid"] as? String,
                                number = port["number"] as? String
                            )
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل جلب معلومات المنافذ: ${error.message}"
                    }
                )
                _isLoading.value = false
            }
        }
    }

    fun refreshCdrRecords(port: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            apiManager?.getCdrRecords(port = port) { result ->
                result.fold(
                    onSuccess = { records ->
                        _cdrRecords.value = records.map { cdr ->
                            CdrRecord(
                                port = cdr["port"] as? Int ?: 0,
                                startTime = cdr["start_time"] as? String,
                                answerTime = cdr["answer_time"] as? String,
                                endTime = cdr["end_time"] as? String,
                                duration = cdr["duration"] as? Int ?: 0,
                                callerNumber = cdr["caller_number"] as? String,
                                calleeNumber = cdr["callee_number"] as? String,
                                direction = cdr["direction"] as? String,
                                callType = cdr["call_type"] as? String,
                                codec = cdr["codec"] as? String,
                                hangupCause = cdr["hangup_cause"] as? String
                            )
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل جلب سجل المكالمات: ${error.message}"
                    }
                )
                _isLoading.value = false
            }
        }
    }

    fun refreshIncomingSms(port: Int? = null) {
        viewModelScope.launch {
            apiManager?.getIncomingSms(port = port) { result ->
                result.fold(
                    onSuccess = { messages ->
                        _incomingSms.value = messages.map { sms ->
                            SmsMessage(
                                port = sms["port"] as? Int ?: 0,
                                sender = sms["sender"] as? String,
                                text = sms["text"] as? String,
                                timestamp = sms["timestamp"] as? String
                            )
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل جلب الرسائل: ${error.message}"
                    }
                )
            }
        }
    }

    fun sendUssd(port: Int, code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            apiManager?.sendUssd(port, code) { result ->
                result.fold(
                    onSuccess = { response ->
                        // سيتم تحديث الحالة عبر WebSocket
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل إرسال USSD: ${error.message}"
                    }
                )
                _isLoading.value = false
            }
        }
    }

    fun setPortPower(port: Int, powerOn: Boolean) {
        viewModelScope.launch {
            apiManager?.setPortPower(port, powerOn) { result ->
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            // تحديث الحالة محلياً
                            _ports.value = _ports.value.map { 
                                if (it.port == port) it.copy(powerState = powerOn) else it 
                            }
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل تغيير حالة المنفذ: ${error.message}"
                    }
                )
            }
        }
    }

    fun setCallForward(port: Int, enabled: Boolean, number: String? = null) {
        viewModelScope.launch {
            apiManager?.setCallForward(port, enabled, number) { result ->
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            _ports.value = _ports.value.map { 
                                if (it.port == port) {
                                    it.copy(
                                        callForwardEnabled = enabled,
                                        callForwardNumber = if (enabled) number else null
                                    )
                                } else it 
                            }
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل تعيين تحويل المكالمات: ${error.message}"
                    }
                )
            }
        }
    }

    fun sendSms(port: Int, phoneNumber: String, message: String) {
        viewModelScope.launch {
            _isLoading.value = true
            apiManager?.sendSms(port, phoneNumber, message) { result ->
                result.fold(
                    onSuccess = { response ->
                        // سيتم تحديث الحالة عبر WebSocket
                    },
                    onFailure = { error ->
                        _errorMessage.value = "فشل إرسال الرسالة: ${error.message}"
                    }
                )
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ===== WebSocket Listener Implementation =====

    override fun onConnected() {
        _connectionState.value = ConnectionState.Connected
        // جلب البيانات الأولية بعد الاتصال
        refreshDeviceStatus()
        refreshPorts()
        refreshCdrRecords()
        refreshIncomingSms()
    }

    override fun onDisconnected(code: Int, reason: String) {
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun onError(error: Throwable) {
        _errorMessage.value = "خطأ في الاتصال: ${error.message}"
    }

    override fun onPortStatusUpdate(gatewayId: String, port: Int, status: Map<String, Any>) {
        _ports.value = _ports.value.map {
            if (it.port == port) {
                it.copy(
                    status = status["status"] as? String ?: it.status,
                    signal = status["signal"] as? Int ?: it.signal,
                    operator = status["operator"] as? String ?: it.operator
                )
            } else it
        }
    }

    override fun onDeviceStatusUpdate(gatewayId: String, status: Map<String, Any>) {
        _deviceStatus.value = DeviceStatus(
            cpuUsed = status["cpu_used"] as? String,
            memoryTotal = status["memory_total"] as? String,
            memoryUsed = status["memory_used"] as? String,
            memoryFree = status["memory_free"] as? String,
            flashTotal = status["flash_total"] as? String,
            flashUsed = status["flash_used"] as? String,
            flashFree = status["flash_free"] as? String,
            temperature = status["temperature"] as? String,
            uptime = status["uptime"] as? String
        )
    }

    override fun onUssdResponse(gatewayId: String, port: Int, response: Map<String, Any>) {
        // يمكن عرض استجابة USSD في واجهة المستخدم
    }

    override fun onPortControl(gatewayId: String, port: Int, control: Map<String, Any>) {
        _ports.value = _ports.value.map {
            if (it.port == port) {
                it.copy(
                    powerState = control["power"] as? Boolean ?: it.powerState,
                    callForwardEnabled = control["callForward"] as? Boolean ?: it.callForwardEnabled,
                    callForwardNumber = control["number"] as? String ?: it.callForwardNumber
                )
            } else it
        }
    }

    override fun onNewCdr(gatewayId: String, cdr: Map<String, Any>) {
        val record = CdrRecord(
            port = cdr["port"] as? Int ?: 0,
            startTime = cdr["start_time"] as? String,
            answerTime = cdr["answer_time"] as? String,
            endTime = cdr["end_time"] as? String,
            duration = cdr["duration"] as? Int ?: 0,
            callerNumber = cdr["caller_number"] as? String,
            calleeNumber = cdr["callee_number"] as? String,
            direction = cdr["direction"] as? String,
            callType = cdr["call_type"] as? String,
            codec = cdr["codec"] as? String,
            hangupCause = cdr["hangup_cause"] as? String
        )
        _cdrRecords.value = listOf(record) + _cdrRecords.value
    }

    override fun onIncomingSms(gatewayId: String, port: Int, sms: Map<String, Any>) {
        val message = SmsMessage(
            port = port,
            sender = sms["sender"] as? String,
            text = sms["text"] as? String,
            timestamp = sms["timestamp"] as? String
        )
        _incomingSms.value = listOf(message) + _incomingSms.value
    }

    override fun onAlert(gatewayId: String, alert: Map<String, Any>) {
        val newAlert = Alert(
            gatewayId = gatewayId,
            type = alert["type"] as? String ?: "",
            severity = alert["severity"] as? String ?: "INFO",
            message = alert["message"] as? String ?: "",
            timestamp = System.currentTimeMillis()
        )
        _alerts.value = listOf(newAlert) + _alerts.value
    }

    // ===== Cleanup =====

    override fun onCleared() {
        super.onCleared()
        webSocketClient?.disconnect()
    }
}
