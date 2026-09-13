package com.red.sovereign.features.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.jsonBodyOf
import com.red.sovereign.core.parseJsonList
import com.red.sovereign.core.parseJsonMap
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

data class UserOverview(
    val id: String,
    val phoneNumber: String,
    val displayName: String,
    val isOnline: Boolean,
    val lastSeenAt: Long
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val tokens = TokenStore(application)
    private val client = AuthorizedApiClient(tokens)

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats = _systemStats.asStateFlow()

    private val _pendingUsers = MutableStateFlow<List<PendingUser>>(emptyList())
    val pendingUsers = _pendingUsers.asStateFlow()

    private val _users = MutableStateFlow<List<UserOverview>>(emptyList())
    val users = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        fetchStats()
        fetchPendingUsers()
        fetchUsers()
    }

    private fun fetchUsers() {
        viewModelScope.launch {
            // First fetch the regular users list
            val allUsersMap = mutableMapOf<String, UserOverview>()
            when (val res = client.request("GET", "/api/admin/users?size=1000")) {
                is ApiResult.Success -> {
                    runCatching {
                        val content = parseJsonMap(res.value)["content"] as? List<*>
                        content?.filterIsInstance<Map<*, *>>()?.forEach { raw ->
                            val redId = raw["redId"]?.toString().orEmpty()
                            allUsersMap[redId] = UserOverview(
                                id = raw["id"]?.toString().orEmpty(),
                                phoneNumber = raw["username"]?.toString().orEmpty(), // Using username for phoneNumber since that's typical
                                displayName = raw["displayName"]?.toString() ?: "بدون اسم",
                                isOnline = false,
                                lastSeenAt = 0L
                            )
                        }
                    }
                }
                is ApiResult.Error -> {}
            }

            // Then fetch the presence status
            when (val presenceRes = client.request("GET", "/api/admin/presence/online")) {
                is ApiResult.Success -> {
                    runCatching {
                        val data = parseJsonMap(presenceRes.value)
                        val usersPresence = data["users"] as? List<*>
                        usersPresence?.filterIsInstance<Map<*, *>>()?.forEach { raw ->
                            val redId = raw["redId"]?.toString().orEmpty()
                            val existing = allUsersMap[redId]
                            if (existing != null) {
                                allUsersMap[redId] = existing.copy(
                                    isOnline = true,
                                    lastSeenAt = (raw["lastActiveMs"] as? Number)?.toLong() ?: 0L
                                )
                            }
                        }
                    }
                }
                is ApiResult.Error -> {}
            }

            _users.value = allUsersMap.values.toList()
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            client.request("DELETE", "/api/admin/users/$userId")
            fetchUsers()
            fetchStats()
            _isLoading.value = false
        }
    }

    private fun fetchStats() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("GET", "/api/master/admin/system/stats")) {
                is ApiResult.Success -> {
                    runCatching {
                        val map = parseJsonMap(res.value)
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
                        _pendingUsers.value = parseJsonList(res.value)
                            .filterIsInstance<Map<*, *>>()
                            .map { raw ->
                                PendingUser(
                                    id = raw["id"]?.toString().orEmpty(),
                                    phoneNumber = raw["phoneNumber"]?.toString().orEmpty(),
                                    registeredAt = (raw["registeredAt"] as? Number)?.toLong() ?: 0L
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
            client.request("POST", "/api/master/admin/hardware/dinstar/action", jsonBodyOf(body))
            _isLoading.value = false
        }
    }
}
