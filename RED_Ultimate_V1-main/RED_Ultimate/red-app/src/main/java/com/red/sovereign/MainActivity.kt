package com.red.sovereign

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.security.AppLockScreen
import com.red.sovereign.security.DebugSecurityManager
import com.red.sovereign.security.CertificatePinner
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.AuthFlow
import com.red.sovereign.ui.RedDashboard
import com.red.sovereign.ui.theme.YounesTheme
import com.red.sovereign.ui.theme.SovereignBackground

class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i("LocalNetwork", if (granted) "LAN access granted" else "LAN access denied")
    }
    private var localNetworkPermissionRequested = false

    /** المعرّف المستهدف من الإشعار (conversationId + sender) لفتح المحادثة مباشرة. */
    private var deepLinkConversation by mutableStateOf<String?>(null)
    private var deepLinkSender by mutableStateOf<String?>(null)

    /** حالة قفل التطبيق — تُفعّل عند onResume إن كان AppLock مفعّلاً. */
    private var appLocked by mutableStateOf(false)

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
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                runCatching { com.red.sovereign.core.RedConnectionService.start(this@MainActivity) }
                                runCatching { com.red.sovereign.calls.YounesCallService.listen(this@MainActivity) }
                                runCatching { com.red.sovereign.calls.YounesConnectionService.register(this@MainActivity) }
                                runCatching { com.red.sovereign.calls.VoipPushRegistrar.register(this@MainActivity) }
                                // منع الانهيار على Android 12+ عند فتح التطبيق من إشعار من الخلفية
                                val routerIntent = Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java)
                                try {
                                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(routerIntent) else startService(routerIntent)
                                } catch (_: Exception) {
                                    try { startService(routerIntent) } catch (_: Exception) {}
                                }
                            } else {
                                com.red.sovereign.core.RedConnectionService.stop(this@MainActivity)
                                com.red.sovereign.calls.YounesCallService.stop(this@MainActivity)
                                stopService(Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java))
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

    private var lastBackgroundAt = 0L

    override fun onPause() {
        super.onPause()
        lastBackgroundAt = System.currentTimeMillis()
    }

    /** قفل بعد مغادرة حقيقية (≥15 ث) حتى لا يعيد حوار البصمة قفل التطبيق فوراً. */
    override fun onResume() {
        super.onResume()
        if (authViewModel.state is AuthState.Authenticated && SettingsRuntime.current.appLockEnabled) {
            val awayMs = System.currentTimeMillis() - lastBackgroundAt
            if (lastBackgroundAt == 0L || awayMs >= 15_000L) {
                appLocked = true
            }
        }
    }
}
