package com.red.sovereign.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.core.LocalServerDiscovery
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    // مهلة قصيرة على LAN: 15 ثانية كانت تُبقي شاشة «جارٍ الاتصال» بلا داعٍ.
    private val api = AuthApi(
        application,
        SecureOkHttpClient.build(application, connectTimeout = 4, readTimeout = 6, writeTimeout = 4),
    )
    private val tokens = TokenStore(application)
    private val keys = DeviceKeyManager(application)
    private val localData = LocalRepository(application)
    private val pstn = PstnApi(tokens)
    private var accountCleanup: Job? = null
    private val discovery = LocalServerDiscovery(application)

    var serverState: ServerState by mutableStateOf(ServerState.Ready(ServerEndpoint.url()))
        private set

    var pstnState: PstnState by mutableStateOf(PstnState.Idle)
        private set

    var state: AuthState by mutableStateOf(AuthState.Loading)
        private set

    /** تلميح عابر لاسم حساب وافق عليه الخادم؛ لا يحتوي أو يسترجع كلمة مرور. */
    var loginUsernameHint: String? by mutableStateOf(null)
        private set
    var loginNotice: String? by mutableStateOf(null)
        private set

    init {
        ServerEndpoint.initialize(application)
        serverState = ServerState.Ready(ServerEndpoint.url())
        restore()
    }

    fun discoverServer() = viewModelScope.launch {
        serverState = ServerState.Discovering
        serverState = when (val result = discovery.discover(LocalServerDiscovery.Mode.THOROUGH)) {
            is ApiResult.Success -> ServerState.Ready(result.value)
            is ApiResult.Error -> ServerState.Error(localize(result.message), ServerEndpoint.url())
        }
    }

    /**
     * تعيين عنوان الخادم يدويًا.
     *
     * كان غياب هذا المدخل يجعل المستخدم رهينة الاكتشاف التلقائي لشبكة /24؛
     * إن كان الخادم على شبكة فرعية أخرى أو عبر نفق (WireGuard/Tailscale) فلا
     * سبيل لإدخال عنوانه. يعيد null عند النجاح أو رسالة خطأ عربية.
     */
    fun setServerUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "أدخل عنوان الخادم (مثال: http://192.168.1.10:8088)"
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "يجب أن يبدأ العنوان بـ http:// أو https://"
        }
        return try {
            ServerEndpoint.update(getApplication(), trimmed)
            serverState = ServerState.Ready(ServerEndpoint.url())
            null
        } catch (_: Exception) {
            "عنوان غير صالح. استخدم صيغة http://IP:8088 أو https://domain"
        }
    }

    fun showRegister() { loginUsernameHint = null; loginNotice = null; state = AuthState.Register }
    fun showLogin() { loginUsernameHint = null; loginNotice = null; state = AuthState.Login }
    fun showRecovery() { state = AuthState.Recovery }
    fun showWelcome() { loginUsernameHint = null; loginNotice = null; state = AuthState.Welcome }

    fun register(displayName: String, username: String, password: String) = viewModelScope.launch {
        accountCleanup?.join()
        state = AuthState.Submitting
        val enrollment = runCatching { withContext(Dispatchers.Default) { keys.enrollment() } }
            .getOrElse { state = AuthState.Error("تعذر إنشاء مفاتيح التشفير المحلية"); return@launch }
        when (val result = withServerDiscoveryRetry { api.register(RegisterRequest(username.trim(), password, displayName.trim(), enrollment)) }) {
            is ApiResult.Success -> {
                result.value.deviceId?.let(tokens::rememberDevice)
                tokens.rememberPendingLogin(username.trim())
                state = AuthState.Pending(result.value.user.redId, result.value.user.username, result.value.recoveryCodes.orEmpty())
            }
            is ApiResult.Error -> state = AuthState.Error(localize(result.message))
        }
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        accountCleanup?.join()
        loginUsernameHint = null
        loginNotice = null
        state = AuthState.Submitting
        when (val result = withServerDiscoveryRetry { api.login(LoginRequest(username.trim(), password, tokens.deviceId)) }) {
            is ApiResult.Error -> state = AuthState.Error(localize(result.message))
            is ApiResult.Success -> applyAuth(result.value, username)
        }
    }

    fun checkApproval() {
        loginUsernameHint = tokens.pendingUsername()
        loginNotice = "أدخل كلمة المرور للتحقق من اعتماد الحساب. لا تُحفظ كلمة المرور على هذا الجهاز."
        tokens.clearPendingLogin()
        state = AuthState.Login
    }

    fun recover(redId: String, code: String, newPassword: String) = viewModelScope.launch {
        state = AuthState.Submitting
        state = when (val result = withServerDiscoveryRetry { api.recover(PasswordRecoveryRequest(redId.trim(), code.trim(), newPassword)) }) {
            is ApiResult.Success -> AuthState.RecoveryComplete
            is ApiResult.Error -> AuthState.Error(localize(result.message))
        }
    }

    fun dialPstn(number: String) = viewModelScope.launch {
        pstnState = PstnState.Dialing
        pstnState = when (val result = pstn.dial(number)) {
            is ApiResult.Success -> PstnState.Started(result.value.callId, result.value.usedToday, result.value.dailyLimit)
            is ApiResult.Error -> PstnState.Error(localize(result.message))
        }
    }

    /** إنهاء مكالمة PSTN جارية — يحرّر منفذ DINSTAR في موازن الحمولة */
    fun hangupPstn(callId: String) = viewModelScope.launch {
        when (val result = pstn.hangup(callId)) {
            is ApiResult.Success -> pstnState = PstnState.Idle
            is ApiResult.Error -> pstnState = PstnState.Error(localize(result.message))
        }
    }

    fun clearPstnState() { pstnState = PstnState.Idle }

    /** تحديث اسم المستخدم (username) على الخادم ثم محلياً. */
    fun updateUsername(newUsername: String, done: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        val trimmed = newUsername.trim()
        if (trimmed.length < 3 || trimmed.length > 20) { done(false, "اسم المستخدم يجب أن يكون 3-20 حرفاً"); return@launch }
        val client = AuthorizedApiClient(TokenStore(getApplication()))
        when (val result = client.request("PATCH", "/api/auth/username", Json.encodeToString(mapOf("username" to trimmed)))) {
            is ApiResult.Success -> {
                val updated = state
                if (updated is AuthState.Authenticated) {
                    state = updated.copy(username = trimmed)
                    tokens.saveUsername(trimmed)
                    done(true, "تم تحديث اسم المستخدم")
                } else done(false, "لا يوجد حساب نشط")
            }
            is ApiResult.Error -> done(false, result.message ?: "فشل تحديث الاسم")
        }
    }

    /** تحديث الاسم المعروض (display name) على الخادم ثم محلياً. */
    fun updateDisplayName(newName: String, done: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed.length > 50) { done(false, "الاسم يجب أن يكون 1-50 حرفاً"); return@launch }
        val client = AuthorizedApiClient(TokenStore(getApplication()))
        when (val result = client.request("PATCH", "/api/auth/profile", Json.encodeToString(mapOf("displayName" to trimmed)))) {
            is ApiResult.Success -> { done(true, "تم تحديث الاسم المعروض") }
            is ApiResult.Error -> done(false, result.message ?: "فشل تحديث الاسم")
        }
    }

    fun logout() {
        // نمسح الرموز فورًا ثم كل بيانات الحساب محليًا قبل السماح بدخول جديد.
        // يبقى معرف الجهاز لأنه جهاز موثوق للحساب نفسه، أما المحادثات وOutbox فلا تحمل ownerId.
        val refreshToken = tokens.refreshToken
        tokens.clearSession()
        state = AuthState.Welcome
        accountCleanup = viewModelScope.launch {
            localData.clearAccountData()
            if (!refreshToken.isNullOrBlank()) api.logout(LogoutRequest(refreshToken))
        }
    }

    private fun restore() = viewModelScope.launch {
        val refresh = tokens.refreshToken
        if (refresh == null) {
            state = AuthState.Welcome
            discoverInBackground(LocalServerDiscovery.Mode.FAST)
            return@launch
        }
        val result = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { api.refresh(refresh) }
        when (result) {
            is ApiResult.Success -> {
                tokens.updateTokens(result.value)
                state = AuthState.Authenticated(tokens.redId.orEmpty(), tokens.username.orEmpty(), tokens.pstnEnabled, tokens.isAdmin)
            }
            is ApiResult.Error -> {
                if (result.code == 401 || result.code == 403) {
                    tokens.clearSession()
                    localData.clearAccountData()
                }
                state = AuthState.Welcome
                if (result.code == null) discoverInBackground(LocalServerDiscovery.Mode.FAST)
            }
            null -> {
                state = AuthState.Welcome
                discoverInBackground(LocalServerDiscovery.Mode.FAST)
            }
        }
    }

    private fun discoverInBackground(mode: LocalServerDiscovery.Mode) = viewModelScope.launch {
        if (serverState is ServerState.Discovering) return@launch
        serverState = ServerState.Discovering
        serverState = when (val result = discovery.discover(mode)) {
            is ApiResult.Success -> ServerState.Ready(result.value)
            is ApiResult.Error -> ServerState.Error(localize(result.message), ServerEndpoint.url())
        }
    }

    private fun applyAuth(response: AuthResponse, username: String) {
        when (response.status) {
            "APPROVED" -> {
                tokens.save(response)
                tokens.clearPendingLogin()
                state = AuthState.Authenticated(response.user.redId, response.user.username, response.user.pstnEnabled, response.user.role == "ADMIN")
            }
            "PENDING" -> {
                tokens.rememberPendingLogin(username)
                state = AuthState.Pending(response.user.redId, response.user.username, emptyList())
            }
            "REJECTED" -> state = AuthState.Rejected(response.user.rejectionReason)
            "SUSPENDED" -> state = AuthState.Suspended
            "BANNED" -> state = AuthState.Banned
            else -> state = AuthState.Error("حالة حساب غير معروفة")
        }
    }

    private suspend fun <T> withServerDiscoveryRetry(request: suspend () -> ApiResult<T>): ApiResult<T> {
        val first = request()
        if (first !is ApiResult.Error || first.code != null) return first
        serverState = ServerState.Discovering
        return when (val discovered = discovery.discover(LocalServerDiscovery.Mode.FAST)) {
            is ApiResult.Success -> {
                serverState = ServerState.Ready(discovered.value)
                request()
            }
            is ApiResult.Error -> {
                serverState = ServerState.Error(localize(discovered.message), ServerEndpoint.url())
                first
            }
        }
    }

    private fun localize(value: String) = when {
        value.contains("DEVICE_ID_REQUIRED", ignoreCase = true) ->
            "هذا الحساب يتطلب جهازًا موثوقًا. افتح التطبيق من الجهاز الذي سجلت منه أولًا أو اطلب من الإدارة اعتماد جهازك الجديد."
        value.contains("DEVICE_NOT_RECOGNIZED", ignoreCase = true) ->
            "معرّف الجهاز لم يعد مرتبطًا بهذا الحساب. أعد التسجيل من الجهاز الموثوق أو اطلب اعتماد الجهاز الجديد."
        value.contains("DEVICE_NOT_APPROVED", ignoreCase = true) ->
            "هذا الجهاز بانتظار اعتماد الإدارة؛ لا يمكن تسجيل الدخول منه بعد."
        value.contains("INVALID_CREDENTIALS", ignoreCase = true) || value.contains("401", ignoreCase = true) ->
            "اسم المستخدم أو كلمة المرور غير صحيحة. يرجى التأكد من البيانات والمحاولة مجدداً."
        value.contains("NETWORK_ERROR", ignoreCase = true) || value.contains("Connection refused", ignoreCase = true) ->
            "تعذر الاتصال بخادم يونس. يرجى التأكد من الاتصال بالشبكة أو الخادم المحلي والمحاولة مجدداً."
        value.contains("INVALID_RECOVERY_CODE", ignoreCase = true) ->
            "معرّف يونس أو رمز الاستعادة غير صحيح. أعد التأكد من الرموز المحفوظة."
        value.contains("RATE_LIMITED", ignoreCase = true) || value.contains("Too many attempts", ignoreCase = true) ->
            "محاولات كثيرة متكررة؛ انتظر دقيقة واحدة ثم أعد المحاولة لحماية حسابك."
        value.contains("RED_SERVER_NOT_FOUND_ON_LAN", ignoreCase = true) ||
            value.contains("YOUNES_SERVER_NOT_FOUND", ignoreCase = true) ->
            "لم يُعثر على خادم يونس آمن في الشبكة المحلية. تأكد من اتصال الـ Wi-Fi."
        value.contains("already registered", ignoreCase = true) || value.contains("Username is taken", ignoreCase = true) ->
            "اسم المستخدم هذا محجوز بالفعل؛ يرجى اختيار اسم مستخدم آخر."
        value.contains("3-32 characters", ignoreCase = true) || value.contains("3-20", ignoreCase = true) ->
            "اسم المستخدم يجب أن يكون 3-32 حرفاً إنكليزياً ويبدأ بحرف، دون مسافات أو رموز."
        value.contains("12-128 characters", ignoreCase = true) ->
            "كلمة المرور يجب أن تكون بين 12 و128 حرفاً وتتضمن مزيجاً من الأحرف والأرقام."
        value.contains("contain the username", ignoreCase = true) ->
            "كلمة المرور يجب ألا تحتوي على اسم المستخدم بداخلها لحماية حسابك."
        value.contains("too common", ignoreCase = true) ->
            "كلمة المرور هذه شائعة وسهلة التخمين؛ يرجى اختيار كلمة مرور أكثر تعقيداً."
        value.contains("2-100 visible characters", ignoreCase = true) ->
            "الاسم الظاهر يجب أن يتكون من 2 إلى 100 حرف واضح."
        value.contains("Base64", ignoreCase = true) || value.contains("deviceName", ignoreCase = true) ->
            "تعذر إنشاء مفاتيح التشفير بشكل صحيح. أعد تشغيل التطبيق وحاول مجدداً."
        value.contains("Device enrollment is required", ignoreCase = true) ->
            "تعذر تجهيز بيانات الجهاز المشفرة. أعد المحاولة."
        value.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "انتهت الجلسة أو غير مصرح. يرجى تسجيل الدخول مجدداً."
        value.contains("INVALID_REQUEST", ignoreCase = true) || value.contains("MALFORMED_JSON", ignoreCase = true) || value.contains("VALIDATION_FAILED", ignoreCase = true) ->
            "الطلب غير صالح أو ناقص البيانات. تأكد من اتصالك بالخادم الصحيح ثم أعد المحاولة."
        value.contains("INTERNAL_ERROR", ignoreCase = true) ->
            "حدث خطأ داخلي في الخادم. يرجى المحاولة لاحقاً أو التواصل مع الإدارة."
        else -> value.ifBlank { "حدث خطأ غير متوقع. يرجى المحاولة لاحقاً." }
    }

    private companion object {
        const val RESTORE_TIMEOUT_MS = 3_500L
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Welcome : AuthState
    data object Register : AuthState
    data object Login : AuthState
    data object Recovery : AuthState
    data object RecoveryComplete : AuthState
    data object Submitting : AuthState
    data class Pending(val redId: String, val username: String, val recoveryCodes: List<String>) : AuthState
    data class Authenticated(val redId: String, val username: String, val pstnEnabled: Boolean, val isAdmin: Boolean = false) : AuthState
    data class Rejected(val reason: String?) : AuthState
    data object Suspended : AuthState
    data object Banned : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface ServerState {
    data object Discovering : ServerState
    data class Ready(val url: String) : ServerState
    data class Error(val message: String, val fallbackUrl: String) : ServerState
}

sealed interface PstnState {
    data object Idle : PstnState
    data object Dialing : PstnState
    data class Started(val callId: String, val usedToday: Int, val dailyLimit: Int) : PstnState
    data class Error(val message: String) : PstnState
}
