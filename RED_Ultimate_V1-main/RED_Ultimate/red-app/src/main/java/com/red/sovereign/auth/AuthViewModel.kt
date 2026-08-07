package com.red.sovereign.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.core.LocalServerDiscovery
import com.red.sovereign.core.ServerEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AuthApi()
    private val tokens = TokenStore(application)
    private val keys = DeviceKeyManager(application)
    private val pstn = PstnApi(tokens)
    private val discovery = LocalServerDiscovery(application)

    var serverState: ServerState by mutableStateOf(ServerState.Ready(ServerEndpoint.url()))
        private set

    var pstnState: PstnState by mutableStateOf(PstnState.Idle)
        private set

    var state: AuthState by mutableStateOf(AuthState.Loading)
        private set
    private var pendingCredentials: Pair<String, String>? = null

    init {
        ServerEndpoint.initialize(application)
        serverState = ServerState.Ready(ServerEndpoint.url())
        restore()
    }

    fun discoverServer() = viewModelScope.launch {
        serverState = ServerState.Discovering
        serverState = when (val result = discovery.discover()) {
            is ApiResult.Success -> ServerState.Ready(result.value)
            is ApiResult.Error -> ServerState.Error(localize(result.message), ServerEndpoint.url())
        }
    }

    fun showRegister() { state = AuthState.Register }
    fun showLogin() { state = AuthState.Login }
    fun showRecovery() { state = AuthState.Recovery }
    fun showWelcome() { state = AuthState.Welcome }

    fun register(displayName: String, username: String, password: String) = viewModelScope.launch {
        state = AuthState.Submitting
        val enrollment = runCatching { withContext(Dispatchers.Default) { keys.enrollment() } }
            .getOrElse { state = AuthState.Error("تعذر إنشاء مفاتيح التشفير المحلية"); return@launch }
        when (val result = withServerDiscoveryRetry { api.register(RegisterRequest(username.trim(), password, displayName.trim(), enrollment)) }) {
            is ApiResult.Success -> {
                result.value.deviceId?.let(tokens::rememberDevice)
                pendingCredentials = username.trim() to password
                state = AuthState.Pending(result.value.user.redId, result.value.user.username, result.value.recoveryCodes.orEmpty())
            }
            is ApiResult.Error -> state = AuthState.Error(localize(result.message))
        }
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        state = AuthState.Submitting
        when (val result = withServerDiscoveryRetry { api.login(LoginRequest(username.trim(), password, tokens.deviceId)) }) {
            is ApiResult.Error -> state = AuthState.Error(localize(result.message))
            is ApiResult.Success -> applyAuth(result.value, username, password)
        }
    }

    fun checkApproval() {
        val credentials = pendingCredentials
        if (credentials == null) state = AuthState.Login
        else login(credentials.first, credentials.second)
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

    fun clearPstnState() { pstnState = PstnState.Idle }

    fun logout() {
        tokens.clearSession()
        pendingCredentials = null
        state = AuthState.Welcome
    }

    private fun restore() = viewModelScope.launch {
        val refresh = tokens.refreshToken
        if (refresh == null) { state = AuthState.Welcome; return@launch }
        when (val result = withServerDiscoveryRetry { api.refresh(refresh) }) {
            is ApiResult.Success -> {
                tokens.updateTokens(result.value)
                state = AuthState.Authenticated(tokens.redId.orEmpty(), tokens.username.orEmpty(), tokens.pstnEnabled)
            }
            is ApiResult.Error -> { tokens.clearSession(); state = AuthState.Welcome }
        }
    }

    private fun applyAuth(response: AuthResponse, username: String, password: String) {
        when (response.status) {
            "APPROVED" -> {
                tokens.save(response)
                pendingCredentials = null
                state = AuthState.Authenticated(response.user.redId, response.user.username, response.user.pstnEnabled)
            }
            "PENDING" -> {
                pendingCredentials = username to password
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
        return when (val discovered = discovery.discover()) {
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

    private fun localize(value: String) = when (value) {
        "INVALID_CREDENTIALS" -> "اسم المستخدم أو كلمة المرور غير صحيحة"
        "NETWORK_ERROR" -> "تعذر الاتصال بخادم يونس المحلي"
        "INVALID_RECOVERY_CODE" -> "معرّف يونس أو رمز الاستعادة غير صحيح"
        "RATE_LIMITED" -> "محاولات كثيرة؛ انتظر قليلاً ثم أعد المحاولة"
        "RED_SERVER_NOT_FOUND_ON_LAN" -> "لم يُعثر على خادم يونس موثوق في الشبكة المحلية"
        "LAN_DISCOVERY_DISABLED_IN_RELEASE" -> "الاكتشاف التلقائي متاح للنسخة المحلية فقط"
        "Username is already registered" -> "اسم المستخدم محجوز؛ اختر اسمًا آخر"
        "Password must contain 12-128 characters" -> "كلمة المرور يجب أن تكون بين 12 و128 محرفًا"
        "Password must not contain the username" -> "لا تستخدم اسم المستخدم داخل كلمة المرور"
        "Password is too common" -> "كلمة المرور شائعة جدًا؛ اختر عبارة أطول وفريدة"
        "Display name must be 2-100 visible characters" -> "الاسم الظاهر يجب أن يكون بين محرفين و100 دون رموز تحكم"
        else -> value
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
    data class Authenticated(val redId: String, val username: String, val pstnEnabled: Boolean) : AuthState
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
