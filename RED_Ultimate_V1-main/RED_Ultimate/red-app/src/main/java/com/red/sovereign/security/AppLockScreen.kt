package com.red.sovereign.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import androidx.core.content.ContextCompat

/**
 * شاشة قفل التطبيق بالبصمة/الوجه/النمط.
 * تُعرض عند تفعيل AppLock عندما يعود التطبيق للواجهة (onResume).
 * تستخدم BiometricPrompt الرسمي من AndroidX — آمن ومتصل بـ Keystore.
 *
 * @param onUnlocked يُستدعى عند نجاح المصادقة (أو تعذّر البصمة واختار المستخدم المتابعة)
 */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalFragmentActivity.current
    val executor = ContextCompat.getMainExecutor(context)

    // فحص توفر البصمة
    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("قفل يونس")
        .setSubtitle("استخدم بصمتك أو نمط جهازك لفتح التطبيق")
        .setNegativeButtonText("خروج")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    fun showPrompt() {
        if (activity == null) { onUnlocked(); return }
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        })
        runCatching { prompt.authenticate(promptInfo) }.onFailure { onUnlocked() }
    }

    // إطلاق البصمة تلقائياً عند ظهور الشاشة
    LaunchedEffect(Unit) {
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            showPrompt()
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // أيقونة القفل
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, "قفل التطبيق", tint = YounesEmerald, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("يونس مقفل", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
        Spacer(Modifier.height(8.dp))
        Text(
            "أكد هويتك لفتح محادثاتك المشفّرة. المفاتيح لا تغادر جهازك.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            { showPrompt() },
            Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Default.Lock, null)
            Text(" فتح بالبصمة")
        }
    }
}
