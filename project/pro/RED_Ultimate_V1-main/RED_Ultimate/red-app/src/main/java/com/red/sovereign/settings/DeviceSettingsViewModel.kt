package com.red.sovereign.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SettingsDevice(
    val id: String,
    val deviceName: String,
    val platform: String,
    val identityFingerprint: String,
    val status: String,
    val authorizationCertificate: String? = null,
    val certificateExpiresAt: String? = null,
    val createdAt: String
)

class DeviceSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val json = Json { ignoreUnknownKeys = true }
    private val currentDeviceId = TokenStore(application).deviceId
    val devices = mutableStateListOf<SettingsDevice>()
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    fun load() = viewModelScope.launch {
        loading = true; error = null
        when (val response = client.request("GET", "/api/devices")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<SettingsDevice>>(response.value) }
                .onSuccess { devices.clear(); devices.addAll(it) }.onFailure { error = "INVALID_DEVICE_RESPONSE" }
            is ApiResult.Error -> error = response.message
        }
        loading = false
    }

    fun revoke(device: SettingsDevice) = viewModelScope.launch {
        if (device.id == currentDeviceId) { error = "لا يمكن إلغاء الجهاز الحالي من هذه الشاشة؛ سجل الخروج أولًا"; return@launch }
        loading = true
        when (val response = client.request("DELETE", "/api/devices/${device.id}")) {
            is ApiResult.Success -> devices.removeAll { it.id == device.id }
            is ApiResult.Error -> error = response.message
        }
        loading = false
    }

    fun isCurrent(device: SettingsDevice) = device.id == currentDeviceId
}
