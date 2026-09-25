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

    // عدّاد تحميل: 3 جلبات متوازية كانت تتصارع على _isLoading (آخر من ينتهي يصفّره
    // ويخفي سبينر البقية) — الآن يُصفَّر فقط عند اكتمال الكل.
    private var loadingCount = 0
    private fun startLoading() {
        loadingCount++
        _isLoading.value = true
    }
    private fun stopLoading() {
        loadingCount = (loadingCount - 1).coerceAtLeast(0)
        if (loadingCount == 0) _isLoading.value = false
    }

    // آخر فلتر بحث: إجراءات الحذف/التوثيق/الحظر كانت تعيد الجلب بلا فلتر
    // فتضيع نتيجة بحث المشرف بعد كل إجراء — الآن تُحفظ وتُعاد.
    private var lastSearch: String? = null

    init {
        refreshDashboard()
    }

    fun clearError() { _error.value = null }
    fun clearActionMessage() { _actionMessage.value = null }

    fun refreshDashboard(search: String? = null) {
        if (search != null) lastSearch = search.trim().takeIf { it.isNotBlank() }
        else if (search == null) {
            // null الصريح من التحديث الدوري يحافظ على الفلتر؛ المسح يمرر "".
        }
        fetchStats()
        fetchPendingUsers()
        fetchUsers(lastSearch)
    }

    fun fetchUsers(search: String? = null) {
        if (search != null) lastSearch = search.trim().takeIf { it.isNotBlank() }
        viewModelScope.launch {
            startLoading()
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
                            // phoneNumber الحقيقي أولاً — كان يُشتق من username/redId فيُعرض معرّف
                            // على أنه رقم هاتف (وهم). الترتيب: phoneNumber ثم username ثم redId.
                            val phone = raw["phoneNumber"]?.toString()?.takeIf { it.isNotBlank() }
                                ?: username.ifBlank { redId }
                            val lastSeenAt = parseInstantToMs(raw["lastSeen"] ?: raw["lastSeenAt"])
                            UserOverview(
                                id = raw["id"]?.toString().orEmpty(),
                                phoneNumber = phone,
                                displayName = raw["displayName"]?.toString() ?: "بدون اسم",
                                // انحراف الساعة للأمام كان يعرض متصلاً وهمياً (diff سالب < النافذة).
                                isOnline = lastSeenAt > 0 && run { val diff = System.currentTimeMillis() - lastSeenAt; diff in 0 until PRESENCE_WINDOW_MS },
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
            stopLoading()
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            startLoading()
            when (val res = client.request("DELETE", "/api/admin/users/$userId")) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم حذف المستخدم"
                    _error.value = null
                    fetchUsers(lastSearch)
                    fetchStats()
                }
                is ApiResult.Error -> _error.value = "تعذر حذف المستخدم: ${res.message}"
            }
            stopLoading()
        }
    }

    private fun fetchStats() {
        viewModelScope.launch {
            startLoading()
            // Canonical: /api/admin/monitor/stats — يقبل snake_case وcamelCase معاً
            // (الخادم قد يعيد activeCalls/activeUsers/totalUsers حسب النسخة).
            when (val res = client.request("GET", "/api/admin/monitor/stats")) {
                is ApiResult.Success -> {
                    runCatching {
                        val map = parseJsonMap(res.value)
                        fun intOf(vararg keys: String): Int =
                            keys.firstNotNullOfOrNull { (map[it] as? Number)?.toInt() } ?: 0
                        val activeUsers = intOf("active_users", "activeUsers", "onlineUsers", "online_users")
                        val totalUsers = intOf("total_users", "totalUsers", "usersCount", "users_count")
                        _systemStats.value = _systemStats.value.copy(
                            usersCount = totalUsers.takeIf { it > 0 }
                                ?: _systemStats.value.usersCount.coerceAtLeast(activeUsers),
                            activeCalls = intOf("active_calls", "activeCalls", "calls_active", "ongoingCalls"),
                            activeStreams = intOf("active_streams", "activeStreams", "streams_active", "liveStreams")
                        )
                    }
                }
                is ApiResult.Error -> {
                    // لا نُصفّر الإحصائيات ولا نُظهر خطأً قاتلاً — قائمة المستخدمين هي المصدر.
                }
            }
            stopLoading()
        }
    }

    private fun fetchPendingUsers() {
        viewModelScope.launch {
            startLoading()
            val parsed = parsePendingBody(
                when (val res = client.request("GET", "/api/admin/users/pending")) {
                    is ApiResult.Success -> return@launch handlePendingSuccess(res.value)
                    is ApiResult.Error -> {
                        // fallback: بعض نسخ الباكند بلا /pending — فلترة حالة عبر القائمة العامة.
                        if (res.message.contains("404", true) || res.message.contains("not found", true)) {
                            when (val fb = client.request("GET", "/api/admin/users?status=PENDING&size=1000")) {
                                is ApiResult.Success -> return@launch handlePendingSuccess(fb.value)
                                is ApiResult.Error -> "تعذر جلب الحسابات المعلقة: ${fb.message} (الأصل: ${res.message})"
                            }
                        } else {
                            "تعذر جلب الحسابات المعلقة: ${res.message}"
                        }
                    }
                }
            )
            _error.value = parsed
            stopLoading()
        }
    }

    private fun handlePendingSuccess(body: String) {
        runCatching {
            val raw = body.trim()
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
            stopLoading()
        }.onFailure {
            _error.value = "تعذر تحليل الحسابات المعلقة"
            stopLoading()
        }
    }

    private fun parsePendingBody(errorText: String): String = errorText

    fun approveUser(userId: String) {
        viewModelScope.launch {
            startLoading()
            when (val res = client.request("POST", "/api/admin/users/$userId/approve", jsonBodyOf(emptyMap()))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تمت الموافقة على الحساب"
                    _error.value = null
                    fetchPendingUsers()
                    fetchUsers(lastSearch)
                }
                is ApiResult.Error -> _error.value = "تعذر التوثيق: ${res.message}"
            }
            stopLoading()
        }
    }

    /** رفض مع سبب — POST /api/admin/users/{id}/reject {"reason": "..."}. */
    fun rejectUser(userId: String, reason: String) {
        viewModelScope.launch {
            startLoading()
            val clean = reason.trim().take(300)
            if (clean.isEmpty()) {
                _error.value = "سبب الرفض مطلوب"
                stopLoading()
                return@launch
            }
            when (val res = client.request("POST", "/api/admin/users/$userId/reject", jsonBodyOf(mapOf("reason" to clean)))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم رفض الحساب"
                    _error.value = null
                    fetchPendingUsers()
                }
                is ApiResult.Error -> _error.value = "تعذر الرفض: ${res.message}"
            }
            stopLoading()
        }
    }

    /** تعليق — POST /api/admin/users/action {"userId","action":"SUSPENDED","reason"}. */
    fun suspendUser(userId: String, reason: String? = null) {
        viewModelScope.launch {
            startLoading()
            val body = jsonBodyOf(mapOf("userId" to userId, "action" to "SUSPENDED", "reason" to reason?.trim()))
            when (val res = client.request("POST", "/api/admin/users/action", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تعليق المستخدم"
                    _error.value = null
                    fetchUsers(lastSearch)
                }
                is ApiResult.Error -> _error.value = "تعذر التعليق: ${res.message}"
            }
            stopLoading()
        }
    }

    /** حظر — POST /api/admin/users/{id}/ban {"reason": "..."}. */
    fun banUser(userId: String, reason: String? = null) {
        viewModelScope.launch {
            startLoading()
            val body = jsonBodyOf(mapOf("reason" to reason?.trim().orEmpty()))
            when (val res = client.request("POST", "/api/admin/users/$userId/ban", body)) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم حظر المستخدم"
                    _error.value = null
                    fetchUsers(lastSearch)
                }
                is ApiResult.Error -> _error.value = "تعذر الحظر: ${res.message}"
            }
            stopLoading()
        }
    }

    fun unbanUser(userId: String) {
        viewModelScope.launch {
            startLoading()
            when (val res = client.request("POST", "/api/admin/users/$userId/unban", jsonBodyOf(emptyMap()))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم فك الحظر — عاد معلقاً حتى تسجيل جهاز جديد"
                    _error.value = null
                    fetchUsers(lastSearch)
                }
                is ApiResult.Error -> _error.value = "تعذر فك الحظر: ${res.message}"
            }
            stopLoading()
        }
    }

    /** دور — PUT /api/admin/users/{id}/role {"role": "ADMIN|USER"}. */
    fun setUserRole(userId: String, role: String) {
        viewModelScope.launch {
            startLoading()
            val clean = role.trim().uppercase()
            if (clean != "ADMIN" && clean != "USER") {
                _error.value = "دور غير مدعوم: $role"
                stopLoading()
                return@launch
            }
            when (val res = client.request("PUT", "/api/admin/users/$userId/role", jsonBodyOf(mapOf("role" to clean)))) {
                is ApiResult.Success -> {
                    _actionMessage.value = "تم تحديث الدور إلى $clean"
                    _error.value = null
                    fetchUsers(lastSearch)
                }
                is ApiResult.Error -> _error.value = "تعذر تحديث الدور: ${res.message}"
            }
            stopLoading()
        }
    }

    private fun parseInstantToMs(value: Any?): Long {
        return when (value) {
            is Number -> {
                val v = value.toLong()
                // ثوانٍ أم مللي؟ القيم الصغيرة (<1e12) ثوانٍ.
                if (v in 1 until 1_000_000_000_000L) v * 1000 else v
            }
            is String -> {
                val t = value.trim()
                if (t.isEmpty()) return 0L
                // رقم كنص (مللي أو ثوانٍ) قبل محاولة ISO-8601.
                t.toLongOrNull()?.let { v ->
                    return if (v in 1 until 1_000_000_000_000L) v * 1000 else v
                }
                runCatching { java.time.Instant.parse(t).toEpochMilli() }.getOrDefault(0L)
            }
            else -> 0L
        }
    }

    companion object {
        private const val PRESENCE_WINDOW_MS = 5 * 60_000L
    }
}
