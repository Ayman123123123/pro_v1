package com.red.sovereign.features.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SystemStats(
    val usersCount: Int = 0,
    val activeCalls: Int = 0,
    val activeStreams: Int = 0,
    val dinstarPortsOnline: Int = 0
)

data class PendingUser(
    val id: String,
    val phoneNumber: String,
    val registeredAt: Long
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val tokens = TokenStore(application)
    private val client = AuthorizedApiClient(tokens)
    private val mapper = ObjectMapper()

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats = _systemStats.asStateFlow()

    private val _pendingUsers = MutableStateFlow<List<PendingUser>>(emptyList())
    val pendingUsers = _pendingUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        fetchStats()
        fetchPendingUsers()
    }

    private fun fetchStats() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("GET", "/api/master/admin/system/stats")) {
                is ApiResult.Success -> {
                    runCatching {
                        val map = mapper.readValue(res.value, Map::class.java) as Map<String, Any?>
                        _systemStats.value = SystemStats(
                            usersCount = (map["usersCount"] as? Number)?.toInt() ?: 0,
                            activeCalls = (map["activeCalls"] as? Number)?.toInt() ?: 0,
                            activeStreams = (map["activeStreams"] as? Number)?.toInt() ?: 0,
                            dinstarPortsOnline = (map["dinstarPortsOnline"] as? Number)?.toInt() ?: 0
                        )
                    }
                }
                is ApiResult.Error -> {}
            }
            _isLoading.value = false
        }
    }

    private fun fetchPendingUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("GET", "/api/master/admin/users/pending")) {
                is ApiResult.Success -> {
                    runCatching {
                        val list = mapper.readValue(res.value, List::class.java) as List<Map<String, Any?>>
                        _pendingUsers.value = list.map {
                            PendingUser(
                                id = it["id"]?.toString().orEmpty(),
                                phoneNumber = it["phoneNumber"]?.toString().orEmpty(),
                                registeredAt = (it["registeredAt"] as? Number)?.toLong() ?: 0L
                            )
                        }
                    }
                }
                is ApiResult.Error -> {}
            }
            _isLoading.value = false
        }
    }

    fun approveUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            client.request("POST", "/api/master/admin/users/approve?userId=$userId")
            fetchPendingUsers() // Refresh list after approval
            _isLoading.value = false
        }
    }

    fun rebootDinstar() {
        viewModelScope.launch {
            _isLoading.value = true
            val body = mapOf("action" to "REBOOT")
            client.request("POST", "/api/master/admin/hardware/dinstar/action", mapper.writeValueAsString(body))
            _isLoading.value = false
        }
    }
}
