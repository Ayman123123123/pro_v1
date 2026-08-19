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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
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
    // LocalFragmentActivity أُزيل من Compose الحديث — النمط القياسي: cast من Context.
    // MainActivity يرث ComponentActivity الذي يرث FragmentActivity، فالcast آمن.
    val activity = context as? FragmentActivity
    val executor = ContextCompat.getMainExecutor(context)
    var statusMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // فحص توفر البصمة
    val biometricManager = BiometricManager.from(context)
    val canAuthenticate = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("قفل يونس")
        .setSubtitle("استخدم بصمتك أو نمط جهازك لفتح التطبيق")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    fun showPrompt() {
        if (activity == null) {
            // لا Activity متاحة — لا نفتح التطبيق (نبقي مقفلاً)، نطلب من المستخدم المحاولة مجدداً
            statusMessage = "تعذّر بدء المصادقة — حاول مرة أخرى"
            return
        }
        // الأمان: الفشل لا يفتح التطبيق — فقط النجاح هو الذي يفتحه.
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
            override fun onAuthenticationFailed() {
                statusMessage = "بصمة غير صحيحة — حاول مرة أخرى"
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // خطأ (إلغاء/قفل مؤقت/فشل متكرر) — نبقي التطبيق مقفلاً، نعرض السبب
                statusMessage = errString.toString().ifBlank { "تعذّرت المصادقة — حاول مرة أخرى" }
            }
        })
        runCatching { prompt.authenticate(promptInfo) }
            .onFailure { statusMessage = "تعذّر بدء المصادقة — حاول مرة أخرى" }
        // لا onUnlocked() عند الفشل — القفل إجباري
    }

    // إطلاق البصمة تلقائياً عند ظهور الشاشة (فقط إن كانت متوفرة)
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
            enabled = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS,
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Default.Lock, null)
            Text(" فتح بالبصمة")
        }
        // رسالة الحالة (فشل/إرشاد) — تبقى التطبيق مقفلاً حتى النجاح
        statusMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Spacer(Modifier.height(12.dp))
            Text(
                "لا توجد بصمة/نمط مُسجّل على الجهاز. فعّل قفل الشاشة من إعدادات Android أولاً.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}
