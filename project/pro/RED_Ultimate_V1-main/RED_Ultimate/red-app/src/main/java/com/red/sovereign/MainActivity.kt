package com.red.sovereign

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
                                RedConnectionService.start(this@MainActivity)
                                YounesCallService.listen(this@MainActivity)
                            } else {
                                RedConnectionService.stop(this@MainActivity)
                                YounesCallService.stop(this@MainActivity)
                            }
                        }
                        if (state is AuthState.Authenticated) RedDashboard(state, authViewModel) else AuthFlow(authViewModel)
                    }
                }
            }
        }
    }
}
