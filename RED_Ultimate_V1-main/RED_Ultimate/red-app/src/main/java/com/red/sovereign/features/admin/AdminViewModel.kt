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
import java.net.URLEncoder

/**
 * بادئة API الموحدة للوحة الإدارة: /api/admin (AdminV2Controller للإجراءات
 * المفصّلة + AdminController للإجراء العام + AdminDataOverviewController
 * للإحصائيات). البادئة القديمة /api/master/admin (AdminMasterController)
 * legacy بلا رفض بسبب/تعليق/دور، فلا تُستخدم هنا.
 */
private const val ADMIN_BASE = "/api/admin"
/** نافذة اعتبار المستخدم متصلاً — تطابق PRESENCE_WINDOW_MS في الخادم (5 دقائق). */
private const val PRESENCE_WINDOW_MS = 5 * 60_000L

data class SystemStats(
    val usersCount: Int = 0,
    val activeCalls: Int = 0,
    val onlineCount: Int = 0,
    val pendingCount: Int = 0,
)

data class PendingUser(
    val id: String,
    val username: String,
    val displayName: String,
    val redId: String,
    val createdAtMillis: Long
)

data class UserOverview(
    val id: String,
    val redId: String,
    val username: String,
    val displayName: String,
    val status: String,
    val role: String,
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

    /** خطأ قسم المعلقين — null تعني: لا خطأ (فارغ = لا معلقين فعلاً). */
    private val _pendingError = MutableStateFlow<String?>(null)
    val pendingError = _pendingError.asStateFlow()

    /** خطأ قسم المستخدمين — null تعني: لا خطأ (فارغ = لا مستخدمين فعلاً). */
    private val _usersError = MutableStateFlow<String?>(null)
    val usersError = _usersError.asStateFlow()

    /** رسالة إجراء عابرة (توثيق/رفض/تعليق/حظر/دور). */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        refreshDashboard()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        fetchUsers()
    }

    fun clearActionMessage() { _actionMessage.value = null }

    fun refreshDashboard() {
        fetchStats()
        fetchPendingUsers()
        fetchUsers()
    }

    private fun fetchUsers() {
        viewModelScope.launch {
            _usersError.value = null
            val query = _searchQuery.value.trim()
            val path = buildString {
                append("$ADMIN_BASE/users?size=200")
                if (query.isNotEmpty()) append("&search=").append(URLEncoder.encode(query, "UTF-8"))
            }
            when (val res = client.request("GET", path)) {
                is ApiResult.Success -> {
                    runCatching {
                        val root = parseJsonMap(res.value)
                        @Suppress("UNCHECKED_CAST")
                        val content = (root["content"] as? List<*>)
                            ?.filterIsInstance<Map<*, *>>()
                            .orEmpty()
                        val now = System.currentTimeMillis()
                        content.map { raw ->
                            val lastSeen = parseTimeMillis(raw["lastSeen"])
                            UserOverview(
                                id = raw["id"]?.toString().orEmpty(),
                                redId = raw["redId"]?.toString().orEmpty(),
                                username = raw["username"]?.toString().orEmpty(),
                                displayName = raw["displayName"]?.toString() ?: "بدون اسم",
                                status = raw["status"]?.toString().orEmpty(),
                                role = raw["role"]?.toString() ?: "USER",
                                // لا endpoint حضور مفصّل للمشرفين في الخادم —
                                // متصل = آخر ظهور داخل نافذة الحضور.
                                isOnline = lastSeen > 0 && now - lastSeen < PRESENCE_WINDOW_MS,
                                lastSeenAt = lastSeen
                            )
                        }.filter { it.id.isNotBlank() }
                    }.onSuccess { _users.value = it }
                        .onFailure { _usersError.value = "تعذر تحليل قائمة المستخدمين" }
                }
                is ApiResult.Error -> _usersError.value = res.message
            }
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
            when (val res = client.request("GET", "$ADMIN_BASE/operations/overview")) {
                is ApiResult.Success -> {
                    runCatching {
                        val map = parseJsonMap(res.value)
                        @Suppress("UNCHECKED_CAST")
                        val users = map["users"] as? Map<*, *> ?: emptyMap<Any, Any>()
                        @Suppress("UNCHECKED_CAST")
                        val comms = map["communications"] as? Map<*, *> ?: emptyMap<Any, Any>()
                        _systemStats.value = SystemStats(
                            usersCount = (users["total"] as? Number)?.toInt() ?: 0,
                            activeCalls = (comms["activeCalls"] as? Number)?.toInt() ?: 0,
                            onlineCount = (users["online"] as? Number)?.toInt() ?: 0,
                            pendingCount = (users["pending"] as? Number)?.toInt() ?: 0,
                        )
                    }
                }
                is ApiResult.Error -> {
                    // احتياطي: /api/admin/monitor/stats بنفس البادئة الموحدة.
                    when (val fallback = client.request("GET", "$ADMIN_BASE/monitor/stats")) {
                        is ApiResult.Success -> runCatching {
                            val map = parseJsonMap(fallback.value)
                            _systemStats.value = _systemStats.value.copy(
                                onlineCount = (map["active_users"] as? Number)?.toInt() ?: 0
                            )
                        }
                        is ApiResult.Error -> Unit
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private fun fetchPendingUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _pendingError.value = null
            when (val res = client.request("GET", "$ADMIN_BASE/users/pending")) {
                is ApiResult.Success -> {
                    runCatching {
                        parseJsonList(res.value)
                            .filterIsInstance<Map<*, *>>()
                            .map { raw ->
                                PendingUser(
                                    id = raw["id"]?.toString().orEmpty(),
                                    username = raw["username"]?.toString().orEmpty(),
                                    displayName = raw["displayName"]?.toString() ?: "بدون اسم",
                                    redId = raw["redId"]?.toString().orEmpty(),
                                    createdAtMillis = parseTimeMillis(raw["createdAt"])
                                )
                            }.filter { it.id.isNotBlank() }
                    }.onSuccess { _pendingUsers.value = it }
                        .onFailure { _pendingError.value = "تعذر تحليل قائمة المعلقين" }
                }
                is ApiResult.Error -> _pendingError.value = res.message
            }
            _isLoading.value = false
        }
    }

    fun approveUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            when (val res = client.request("POST", "$ADMIN_BASE/users/$userId/approve")) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم توثيق الحساب"
                    fetchPendingUsers()
                    fetchStats()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر التوثيق: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** رفض حساب معلق مع سبب (يُحفظ في rejectionReason ويُبطل الجلسات). */
    fun rejectUser(userId: String, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            val body = jsonBodyOf(mapOf("reason" to reason.trim().takeIf { it.isNotBlank() }))
            when (val res = client.request("POST", "$ADMIN_BASE/users/$userId/reject", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم رفض الحساب"
                    fetchPendingUsers()
                    fetchStats()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر الرفض: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** تعليق حساب عبر الإجراء العام (SUSPENDED مدعوم في /users/action فقط). */
    fun suspendUser(userId: String, reason: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            val body = jsonBodyOf(
                mapOf(
                    "userId" to userId,
                    "action" to "SUSPENDED",
                    "reason" to reason?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            when (val res = client.request("POST", "$ADMIN_BASE/users/action", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تعليق الحساب"
                    fetchUsers()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر التعليق: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    fun banUser(userId: String, reason: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            val body = jsonBodyOf(mapOf("reason" to reason?.trim()?.takeIf { it.isNotBlank() }))
            when (val res = client.request("POST", "$ADMIN_BASE/users/$userId/ban", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم حظر الحساب"
                    fetchUsers()
                    fetchStats()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر الحظر: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    fun unbanUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            when (val res = client.request("POST", "$ADMIN_BASE/users/$userId/unban")) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم فك الحظر"
                    fetchUsers()
                    fetchStats()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر فك الحظر: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** تبديل الدور بين USER وADMIN. */
    fun setUserRole(userId: String, role: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _actionMessage.value = null
            val body = jsonBodyOf(mapOf("role" to role))
            when (val res = client.request("PUT", "$ADMIN_BASE/users/$userId/role", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تحديث الدور إلى $role"
                    fetchUsers()
                }
                is ApiResult.Error -> _actionMessage.value = "تعذر تحديث الدور: ${res.message}"
            }
            _isLoading.value = false
        }
    }

    /** يحوّل طابعاً زمنياً (رقم ثوانٍ/ملّي أو ISO-8601 أو null) إلى ملّي ثانية. */
    private fun parseTimeMillis(value: Any?): Long {
        if (value == null) return 0L
        if (value is Number) {
            val n = value.toLong()
            return if (n > 1_000_000_000_000L) n else n * 1000L
        }
        val s = value.toString().trim()
        if (s.isEmpty()) return 0L
        runCatching { return java.time.Instant.parse(s).toEpochMilli() }
        s.toLongOrNull()?.let {
            return if (it > 1_000_000_000_000L) it else it * 1000L
        }
        return 0L
    }

}
