package com.red.sovereign

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.security.DebugSecurityManager
import com.red.sovereign.security.CertificatePinner
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.AuthFlow
import com.red.sovereign.ui.RedDashboard
import com.red.sovereign.ui.theme.YounesTheme
import com.red.sovereign.ui.theme.SovereignBackground

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                com.red.sovereign.core.RedConnectionService.start(this@MainActivity)
                                com.red.sovereign.calls.YounesCallService.listen(this@MainActivity)
                                val routerIntent = Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java)
                                if (Build.VERSION.SDK_INT >= 26) startForegroundService(routerIntent) else startService(routerIntent)
                            } else {
                                com.red.sovereign.core.RedConnectionService.stop(this@MainActivity)
                                com.red.sovereign.calls.YounesCallService.stop(this@MainActivity)
                                stopService(Intent(this@MainActivity, com.red.sovereign.core.network.SovereignNotificationRouter::class.java))
                            }
                        }
                        if (state is AuthState.Authenticated) RedDashboard(state, authViewModel) else AuthFlow(authViewModel)
                    }
                }
            }
        }
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
}
