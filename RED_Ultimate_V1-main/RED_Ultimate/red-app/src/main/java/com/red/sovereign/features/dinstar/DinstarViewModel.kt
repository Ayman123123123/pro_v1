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

    private val _cdrRecords = MutableStateFlow<List<DinstarCdr>>(emptyList())
    val cdrRecords = _cdrRecords.asStateFlow()

    private val _commandResult = MutableStateFlow<DinstarCommandResult?>(null)
    val commandResult = _commandResult.asStateFlow()

    init {
        refreshStatus()
        connectWebSocket()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            when (val response = client.request("GET", "/api/admin/dinstar/status")) {
                is ApiResult.Success -> {
                    runCatching {
                        val portsList = mapper.readValue(response.value, List::class.java) as List<Map<String, Any?>>
                        val ports = portsList.mapIndexed { idx, raw ->
                            val regState = raw["status"]?.toString() ?: "UNREGISTERED"
                            val callState = raw["callState"]?.toString() ?: "IDLE"
                            val signal = (raw["signal"] as? Number)?.toInt() ?: 0
                            DinstarPort(
                                index = idx,
                                registrationState = regState,
                                callState = callState,
                                signalPercent = signal,
                                operatorName = raw["operator"]?.toString() ?: "YEMEN",
                                simType = YemenOperator.fromApiOperatorName(raw["operator"]?.toString())
                            )
                        }
                        _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = true, ports = ports, lastUpdated = System.currentTimeMillis())
                    }
                }
                is ApiResult.Error -> _gatewayStatus.value = _gatewayStatus.value.copy(isOnline = false)
            }
        }
    }

    fun sendSms(text: String, numbers: List<String>) {
        viewModelScope.launch {
            _commandResult.value = DinstarCommandResult.Loading
            val body = mapOf("text" to text, "param" to numbers.map { mapOf("number" to it) })
            when (val response = client.request("POST", "/api/admin/dinstar/sms/send", mapper.writeValueAsString(body))) {
                is ApiResult.Success -> _commandResult.value = DinstarCommandResult.Success("تم إرسال الرسائل بنجاح")
                is ApiResult.Error -> _commandResult.value = DinstarCommandResult.Error(response.message ?: "فشل إرسال الرسائل")
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
}
