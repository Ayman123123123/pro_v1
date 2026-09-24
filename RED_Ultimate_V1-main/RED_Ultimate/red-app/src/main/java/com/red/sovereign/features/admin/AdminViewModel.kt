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
)

data class PendingUser(
    val id: String,
    val phoneNumber: String,
    val registeredAt: Long,
    val username: String = "",
    val displayName: String = "",
    val redId: String = ""
)

data class UserOverview(
    val id: String,
    val phoneNumber: String,
    val displayName: String,
    val isOnline: Boolean,
    val lastSeenAt: Long,
    val status: String = "",
    val role: String = "",
    val username: String = "",
    val redId: String = ""
)

/**
 * لوحة الإدارة — بادئة API موحدة مع الباكند: /api/admin/* فقط.
 * (فحص قراءة فقط: AdminV2Controller + AdminController + AdminMonitorController
 *  كلها @RequestMapping("/api/admin")، وAdminMasterController على
 *  /api/master/admin مكرر قديم — لا نستعمله هنا.)
 */
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

    // تمييز الخطأ عن الفراغ: null = لا خطأ (الفراغ حقيقي)، نص = فشل شبكة/خادم.
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()

    init {
        refreshDashboard()
    }

    fun clearError() { _error.value = null }
    fun clearActionMessage() { _actionMessage.value = null }

    fun refreshDashboard(search: String? = null) {
        fetchStats()
        fetchPendingUsers()
        fetchUsers(search)
    }

    fun fetchUsers(search: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val query = buildString {
                append("/api/admin/users?size=1000")
                if (!search.isNullOrBlank()) append("&search=").append(java.net.URLEncoder.encode(search.trim(), "UTF-8"))
            }
            when (val res = client.request("GET", query)) {
                is ApiResult.Success -> {
                    runCatching {
                        val root = parseJsonMap(res.value)
                        val content = root["content"] as? List<*> ?: emptyList<Any>()
                        content.filterIsInstance<Map<*, *>>().map { raw ->
                            val redId = raw["redId"]?.toString().orEmpty()
                            val username = raw["username"]?.toString().orEmpty()
                            val lastSeenAt = parseInstantToMs(raw["lastSeen"] ?: raw["lastSeenAt"])
                            UserOverview(
                                id = raw["id"]?.toString().orEmpty(),
                                phoneNumber = username.ifBlank { redId },
                                displayName = raw["displayName"]?.toString() ?: "بدون اسم",
                                isOnline = lastSeenAt > 0 && (System.currentTimeMillis() - lastSeenAt) < PRESENCE_WINDOW_MS,
                                lastSeenAt = lastSeenAt,
                                status = raw["status"]?.toString().orEmpty(),
                                role = raw["role"]?.toString().orEmpty(),
                                username = username,
                                redId = redId
                            )
                        }
                    }.onSuccess {
                        _users.value = it
                        _error.value = null
                        // عدد المستخدمين من totalElements حين توفره.
                        runCatching {
                            val total = (parseJsonMap(res.value)["totalElements"] as? Number)?.toInt()
                            if (total != null) _systemStats.value = _systemStats.value.copy(usersCount = total)
                            else if (it.isNotEmpty()) _systemStats.value = _systemStats.value.copy(usersCount = it.size)
                        }
                    }.onFailure {
                        _error.value = "تعذر تحليل قائمة المستخدمين"
                    }
                }
                is ApiResult.Error -> {
                    _error.value = "تعذر جلب المستخدمين: ${res.message}"
                }
            }
            _isLoading.value = false
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("DELETE", "/api/admin/users/$userId")) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم حذف المستخدم"
                    fetchUsers()
                    fetchStats()
                }
                is ApiResult.Error -> _error.value = "تعذر حذف المستخدم: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    private fun fetchStats() {
        viewModelScope.launch {
            _isLoading.value = true
            // Canonical: /api/admin/monitor/stats — active_users/total_messages + ذاكرة وuptime.
            when (val res = client.request("GET", "/api/admin/monitor/stats")) {
                is ApiResult.Success -> {
                    runCatching {
                        val map = parseJsonMap(res.value)
                        val activeUsers = (map["active_users"] as? Number)?.toInt() ?: 0
                        _systemStats.value = _systemStats.value.copy(
                            usersCount = _systemStats.value.usersCount.coerceAtLeast(activeUsers),
                            activeCalls = (map["active_calls"] as? Number)?.toInt() ?: 0,
                            activeStreams = (map["active_streams"] as? Number)?.toInt() ?: 0
                        )
                    }
                }
                is ApiResult.Error -> {
                    // لا نُصفّر الإحصائيات ولا نُظهر خطأً قاتلاً — قائمة المستخدمين هي المصدر.
                }
            }
            _isLoading.value = false
        }
    }

    private fun fetchPendingUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("GET", "/api/admin/users/pending")) {
                is ApiResult.Success -> {
                    runCatching {
                        val raw = res.value.trim()
                        val list: List<Any?> = if (raw.startsWith("[")) parseJsonList(raw)
                        else (parseJsonMap(raw)["content"] as? List<*>)?.filterNotNull() ?: emptyList()
                        list.filterIsInstance<Map<*, *>>().map { item ->
                            PendingUser(
                                id = item["id"]?.toString().orEmpty(),
                                phoneNumber = item["phoneNumber"]?.toString()
                                    ?: item["username"]?.toString().orEmpty(),
                                registeredAt = (item["registeredAt"] as? Number)?.toLong()
                                    ?: parseInstantToMs(item["createdAt"]),
                                username = item["username"]?.toString().orEmpty(),
                                displayName = item["displayName"]?.toString().orEmpty(),
                                redId = item["redId"]?.toString().orEmpty()
                            )
                        }
                    }.onSuccess {
                        _pendingUsers.value = it
                        if (_users.value.isEmpty()) _error.value = null
                    }.onFailure {
                        _error.value = "تعذر تحليل الحسابات المعلقة"
                    }
                }
                is ApiResult.Error -> {
                    _error.value = "تعذر جلب الحسابات المعلقة: ${res.message}"
                }
            }
            _isLoading.value = false
        }
    }

    fun approveUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("POST", "/api/admin/users/$userId/approve", jsonBodyOf(emptyMap()))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تمت الموافقة على الحساب"
                    fetchPendingUsers()
                    fetchUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر التوثيق: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** رفض مع سبب — POST /api/admin/users/{id}/reject {"reason": "..."}. */
    fun rejectUser(userId: String, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val clean = reason.trim()
            if (clean.isEmpty()) {
                _error.value = "سبب الرفض مطلوب"
                _isLoading.value = false
                return@launch
            }
            when (val res = client.request("POST", "/api/admin/users/$userId/reject", jsonBodyOf(mapOf("reason" to clean)))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم رفض الحساب"
                    fetchPendingUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر الرفض: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** تعليق — POST /api/admin/users/action {"userId","action":"SUSPENDED","reason"}. */
    fun suspendUser(userId: String, reason: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val body = jsonBodyOf(mapOf("userId" to userId, "action" to "SUSPENDED", "reason" to reason?.trim()))
            when (val res = client.request("POST", "/api/admin/users/action", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تعليق المستخدم"
                    fetchUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر التعليق: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** حظر — POST /api/admin/users/{id}/ban {"reason": "..."}. */
    fun banUser(userId: String, reason: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val body = jsonBodyOf(mapOf("reason" to reason?.trim().orEmpty()))
            when (val res = client.request("POST", "/api/admin/users/$userId/ban", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم حظر المستخدم"
                    fetchUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر الحظر: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    fun unbanUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = client.request("POST", "/api/admin/users/$userId/unban", jsonBodyOf(emptyMap()))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم فك الحظر — عاد معلقاً حتى تسجيل جهاز جديد"
                    fetchUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر فك الحظر: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** دور — PUT /api/admin/users/{id}/role {"role": "ADMIN|USER"}. */
    fun setUserRole(userId: String, role: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val clean = role.trim().uppercase()
            if (clean != "ADMIN" && clean != "USER") {
                _error.value = "دور غير مدعوم: $role"
                _isLoading.value = false
                return@launch
            }
            when (val res = client.request("PUT", "/api/admin/users/$userId/role", jsonBodyOf(mapOf("role" to clean)))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تحديث الدور إلى $clean"
                    fetchUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر تحديث الدور: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    private fun parseInstantToMs(value: Any?): Long {
        return when (value) {
            is Number -> {
                val v = value.toLong()
                // ثوانٍ أم مللي؟ القيم الصغيرة (<1e12) ثوانٍ.
                if (v in 1 until 1_000_000_000_000L) v * 1000 else v
            }
            is String -> runCatching { java.time.Instant.parse(value.trim()).toEpochMilli() }.getOrDefault(0L)
            else -> 0L
        }
    }

    companion object {
        private const val PRESENCE_WINDOW_MS = 5 * 60_000L
    }
}
