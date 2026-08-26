package com.red.sovereign.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.core.LocalServerDiscovery
import com.red.sovereign.auth.UserResponse
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
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

    /** WebRTC-PSTN bridge: connect to Asterisk via WSS, dial through DINSTAR GSM */
    private var pstnWebRtc: com.red.sovereign.calls.PstnWebRtcManager? = null
    var incomingPstnCall: IncomingPstnCall? by mutableStateOf(null); private set

    /**
     * قناة أحداث الخادم الحيّة لمكالمة PSTN الصادرة عبر `/ws/pstn`.
     *
     * مسار SIP في `PstnWebRtcManager` يستشعر RINGING/ANSWERED من إشارة SIP
     * المحلية، لكنه أعمى عن مراحل قناة GSM الفعلية على البوابة. الخادم صار
     * يبثّ `PSTN_CALL_EVENT` (RINGING/BRIDGING/ACTIVE/ENDED) من أحداث
     * Asterisk الحقيقية — وهي المصدر الموثوق لمرحلة الطرف البعيد. نستهلكها
     * هنا لتصحيح حالة الواجهة حتى لو تأخّرت إشارة SIP أو غابت.
     */
    private var pstnEventSocket: com.red.sovereign.features.sms.PstnEventSocket? = null
    @Volatile private var activePstnCallId: String? = null

    private fun startPstnEventStream() {
        if (pstnEventSocket != null) return
        pstnEventSocket = com.red.sovereign.features.sms.PstnEventSocket(
            tokens = tokens,
            onEnvelope = ::onPstnServerEvent
        ).also { it.connect() }
    }

    private fun stopPstnEventStream() {
        pstnEventSocket?.disconnect()
        pstnEventSocket = null
        activePstnCallId = null
    }

    /** يحوّل أحداث الخادم إلى انتقالات حالة الواجهة للمكالمة الصادرة الجارية. */
    private fun onPstnServerEvent(e: com.red.sovereign.features.sms.PstnWsEnvelope) {
        if (e.type != "PSTN_CALL_EVENT") return
        val callId = e.callId ?: return
        // تجاهل أحداث مكالمة أخرى (مثلاً مكالمة سابقة انتهت متأخرة).
        val current = activePstnCallId
        if (current != null && callId != current) return
        val prev = pstnState as? PstnState.Started
        when (e.event) {
            "RINGING" -> pstnState = PstnState.Ringing
            "BRIDGING" -> pstnState = PstnState.Bridging
            "ACTIVE" -> pstnState = PstnState.Started(
                callId = callId,
                usedToday = prev?.usedToday ?: 0,
                dailyLimit = prev?.dailyLimit ?: 0,
                answered = true
            )
            "ENDED" -> {
                pstnWebRtc?.release()
                pstnWebRtc = null
                pstnState = PstnState.Idle
                stopPstnEventStream()
            }
            else -> Unit
        }
    }

    fun dialPstn(number: String) = viewModelScope.launch {
        refreshPstnEntitlement()
        pstnState = PstnState.Bridging
        startPstnEventStream()
        val mgr = com.red.sovereign.calls.PstnWebRtcManager(getApplication())
        pstnWebRtc = mgr
        mgr.call(number, object : com.red.sovereign.calls.PstnWebRtcManager.Events {
            override fun onConnected() { pstnState = PstnState.Registering }
            override fun onRinging() { pstnState = PstnState.Ringing }
            override fun onAnswered(usedToday: Int, dailyLimit: Int) {
                activePstnCallId = mgr.currentCallId
                pstnState = PstnState.Started(mgr.currentCallId ?: "", usedToday, dailyLimit)
            }
            override fun onHangup(cause: String?) {
                pstnState = PstnState.Idle
                pstnWebRtc?.release()
                pstnWebRtc = null
                stopPstnEventStream()
            }
            override fun onIncoming(sdp: String, fromNumber: String) {
                incomingPstnCall = IncomingPstnCall(sdp = sdp, fromNumber = fromNumber)
                pstnState = PstnState.Incoming(fromNumber)
            }
            override fun onError(message: String) {
                pstnState = PstnState.Error(localize(message))
                stopPstnEventStream()
            }
        })
    }

    fun acceptIncomingPstnCall() {
        val incoming = incomingPstnCall ?: return
        pstnWebRtc?.answerIncoming(incoming.sdp)
        incomingPstnCall = null
    }

    fun rejectIncomingPstnCall() {
        pstnWebRtc?.rejectIncoming()
        pstnWebRtc?.release()
        pstnWebRtc = null
        incomingPstnCall = null
        pstnState = PstnState.Idle
        stopPstnEventStream()
    }

    fun hangupPstn() = viewModelScope.launch {
        pstnWebRtc?.hangup()
        pstnWebRtc?.release()
        pstnWebRtc = null
        pstnState = PstnState.Idle
        stopPstnEventStream()
    }

    /** كتم/إلغاء كتم الميكروفون في مكالمة PSTN النشطة. */
    fun togglePstnMute(mute: Boolean) {
        pstnWebRtc?.isMuted = mute
    }

    /** تبديل مكبر الصوت في مكالمة PSTN النشطة. */
    fun togglePstnSpeaker(speakerOn: Boolean) {
        pstnWebRtc?.isSpeaker = speakerOn
    }

    fun clearPstnState() { pstnState = PstnState.Idle }

    // 📨 SMS Methods
    fun sendSms(recipient: String, text: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        val result = pstn.sendSms(recipient, text)
        when (result) {
            is ApiResult.Success -> onResult(true, "تم الإرسال")
            is ApiResult.Error -> onResult(false, result.message ?: "فشل الإرسال")
        }
    }

    fun loadSmsInbox(onResult: (List<SmsIncomingMessage>) -> Unit = { }) = viewModelScope.launch {
        val result = pstn.getInbox()
        when (result) {
            is ApiResult.Success -> onResult(result.value)
            is ApiResult.Error -> onResult(emptyList())
        }
    }

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
        tokens.clearSession()
        pendingCredentials = null
        state = AuthState.Welcome
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
                if (result.code == 401 || result.code == 403) tokens.clearSession()
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
        val result = discovery.discover(mode)
        if (result is ApiResult.Error && mode == LocalServerDiscovery.Mode.FAST) {
            // الفحص السريع فشل → تصعيد تلقائي لمسح LAN الشامل قبل إظهار الخطأ.
            val thorough = discovery.discover(LocalServerDiscovery.Mode.THOROUGH)
            if (thorough is ApiResult.Success) {
                serverState = ServerState.Ready(thorough.value)
                return@launch
            }
        }
        serverState = when (result) {
            is ApiResult.Success -> ServerState.Ready(result.value)
            is ApiResult.Error -> ServerState.Error(localize(result.message), ServerEndpoint.url())
        }
    }

    private fun applyAuth(response: AuthResponse, username: String, password: String) {
        when (response.status) {
            "APPROVED" -> {
                tokens.save(response)
                pendingCredentials = null
                state = AuthState.Authenticated(response.user.redId, response.user.username, response.user.pstnEnabled, response.user.role == "ADMIN")
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

    /** يجلب حالة PSTN من الخادم (/api/auth/me) ويحدث الحالة المحلية.
     * يستدعى عند: استئناف التطبيق، قبل الاتصال، ودورياً أثناء شاشة DINSTAR. */
    fun refreshPstnEntitlement() = viewModelScope.launch {
        val result = api.me()
        if (result is ApiResult.Success) {
            val user = result.value
            tokens.store.put("pstn_enabled", user.pstnEnabled.toString())
            // تحديث AuthState الحالي إن كان مصادقاً
            state = when (val current = state) {
                is AuthState.Authenticated -> current.copy(pstnEnabled = user.pstnEnabled)
                else -> current
            }
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
        value.contains("USERNAME_TAKEN", ignoreCase = true) ->
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
        value.contains("Account is not approved", ignoreCase = true) || value.contains("PENDING_APPROVAL", ignoreCase = true) ->
            "حسابك قيد المراجعة من الإدارة؛ ستتمكن من الدخول بعد الموافقة."
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
    data object Bridging : PstnState
    data object Registering : PstnState
    data object Ringing : PstnState
    data class Incoming(val fromNumber: String) : PstnState
    data class Started(
        val callId: String,
        val usedToday: Int,
        val dailyLimit: Int,
        val answered: Boolean = true,
        val ringing: Boolean = false
    ) : PstnState
    data class Error(val message: String) : PstnState
}

data class IncomingPstnCall(val sdp: String, val fromNumber: String)
