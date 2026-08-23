package com.red.sovereign

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.security.AppLockScreen
import com.red.sovereign.security.AppLockPolicy
import com.red.sovereign.security.DebugSecurityManager
import com.red.sovereign.security.CertificatePinner
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.AuthFlow
import com.red.sovereign.ui.RedDashboard
import com.red.sovereign.ui.theme.YounesTheme
import com.red.sovereign.ui.theme.SovereignBackground

class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    /** مستهلك أحداث PSTN الدائم — يبدأ مع جلسة الدخول ويحوّل PSTN_INCOMING إلى رنين. */
    private var pstnCoordinator: com.red.sovereign.calls.PstnIncomingCallCoordinator? = null

    /** مراقب دورة الحياة لاستئناف صلاحيات PSTN — مُخزَّن لتجنب التسريب عند إعادة التركيب. */
    private val pstnLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (authViewModel.state is AuthState.Authenticated) {
                authViewModel.refreshPstnEntitlement()
            }
        }
    }
    private var pstnObserverRegistered = false

    private val appPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        Log.i("Permissions", "Initial permissions granted: $grants")
    }
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i("LocalNetwork", if (granted) "LAN access granted" else "LAN access denied")
    }
    private var localNetworkPermissionRequested = false

    /** المعرّف المستهدف من الإشعار (conversationId + sender) لفتح المحادثة مباشرة. */
    private var deepLinkConversation by mutableStateOf<String?>(null)
    private var deepLinkSender by mutableStateOf<String?>(null)

    /** حالة قفل التطبيق بعد تجاوز مهلة الخلفية. */
    private var appLocked by mutableStateOf(false)
    private var backgroundedAtElapsedMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        // Initialize security manager
        DebugSecurityManager.initialize(application)
        
        // Log security recommendations
        DebugSecurityManager.getSecurityRecommendations().forEach { rec ->
            Log.i("Security", "[${rec.severity}] ${rec.title}: ${rec.description}")
        }

        // Private messages, recovery codes and device identity must not leak through screenshots
        // or the Android recent-apps thumbnail. A user-controlled exception can be added for public feed export later.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        SettingsRuntime.initialize(application)
        enableEdgeToEdge()
        setContent {
            val preferences = SettingsRuntime.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, preferences.fontScale)) {
                YounesTheme(highContrast = preferences.highContrast) {
                    SovereignBackground {
                        val state = authViewModel.state
                        LaunchedEffect(state is AuthState.Authenticated) {
                            if (state is AuthState.Authenticated) {
                                val needed = buildList {
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.RECORD_AUDIO)
                                    }
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.CAMERA)
                                    }
                                    if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.BLUETOOTH_CONNECT)
                                    }
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.READ_PHONE_STATE)
                                    }
                                    // Android 14+ Foreground service specific permissions (optional, but good if the OS enforces them at runtime)
                                    // Though typically they are declared in manifest and granted automatically if CAMERA/RECORD_AUDIO are granted.
                                }
                                if (needed.isNotEmpty()) {
                                    appPermissions.launch(needed.toTypedArray())
                                }
                                runCatching { com.red.sovereign.core.RedConnectionService.start(this@MainActivity) }
                                runCatching { com.red.sovereign.calls.YounesCallService.listen(this@MainActivity) }
                                // بدء مستهلك أحداث PSTN الدائم (كان ميتاً — لا مستدعِ له)
                                runCatching {
                                    val coordinator = pstnCoordinator
                                        ?: com.red.sovereign.calls.PstnIncomingCallCoordinator(application)
                                            .also { pstnCoordinator = it }
                                    coordinator.start()
                                }.onFailure { Log.w("PstnCoordinator", "start failed: ${it.message}") }
                                // ملاحظة: لا نسجّل YounesConnectionService القديم — TelegramBridge (CallsManager)
                                // يُسجَّل تلقائياً من YounesCallService.onCreate. تسجيل الاثنين معاً يخلق
                                // PhoneAccount مزدوجاً → مكالمات نظام عالقة/مكررة على بعض الأجهزة.
runCatching { com.red.sovereign.calls.VoipPushRegistrar.register(this@MainActivity) }
            // منع الانهيار على Android 12+ عند فتح التطبيق من إشعار من الخلفية
            val routerIntent = Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(routerIntent) else startService(routerIntent)
            } catch (_: Exception) {
                try { startService(routerIntent) } catch (_: Exception) {}
            }
            // تحديث صلاحيات PSTN عند استئناف التطبيق — مسجل مرة واحدة فقط
            if (!pstnObserverRegistered) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(pstnLifecycleObserver)
                pstnObserverRegistered = true
            }
            } else {
                                com.red.sovereign.core.RedConnectionService.stop(this@MainActivity)
                                com.red.sovereign.calls.YounesCallService.stop(this@MainActivity)
                                stopService(Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java))
                                pstnCoordinator?.stop()
                            }
                        }
                        if (state is AuthState.Authenticated) {
                            if (appLocked && SettingsRuntime.current.appLockEnabled) {
                                AppLockScreen(onUnlocked = { appLocked = false })
                            } else {
                                RedDashboard(state, authViewModel, deepLinkSender, deepLinkConversation)
                            }
                        } else AuthFlow(authViewModel)
                    }
                }
            }
        }
    }

    /** استخراج بيانات المحادثة من إشعار الرسالة. */
    private fun handleNotificationIntent(notificationIntent: Intent?) {
        if (notificationIntent == null) return
        notificationIntent.getStringExtra("conversation_id")?.let { deepLinkConversation = it }
        notificationIntent.getStringExtra("sender_red_id")?.let { deepLinkSender = it }
    }

    /** عند فتح التطبيق من إشعار بينما هو مفتوح (launchMode singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            pstnCoordinator?.destroy()
            pstnCoordinator = null
            if (pstnObserverRegistered) {
                runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(pstnLifecycleObserver) }
                pstnObserverRegistered = false
            }
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter PiP if video call is active
        try {
            val callActive = com.red.sovereign.calls.CallRuntime.state is com.red.sovereign.calls.CallUiState.Active
            val confActive = com.red.sovereign.calls.ConferenceRuntime.state is com.red.sovereign.calls.ConferenceUiState.Active
            val isVideo = com.red.sovereign.calls.CallRuntime.localVideo != null || com.red.sovereign.calls.ConferenceRuntime.localVideo != null
            if ((callActive || confActive) && isVideo && android.os.Build.VERSION.SDK_INT >= 26) {
                val params = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(9, 16)).build()
                enterPictureInPictureMode(params)
            }
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        // targetSdk 37 enforces this runtime permission before OkHttp/WebSocket
        // can reach the sovereign LAN server. Request it before login/discovery.
        if (Build.VERSION.SDK_INT >= 37 &&
            !localNetworkPermissionRequested &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionRequested = true
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAtElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    /** قفل التطبيق فقط بعد بقائه بالخلفية لمدة كافية. */
    override fun onResume() {
        super.onResume()
        val shouldLock = authViewModel.state is AuthState.Authenticated && AppLockPolicy.shouldLock(
            lockEnabled = SettingsRuntime.current.appLockEnabled,
            backgroundedAtElapsedMs = backgroundedAtElapsedMs,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )
        backgroundedAtElapsedMs = null
        if (shouldLock) {
            appLocked = true
        }
    }
}
