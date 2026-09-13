package com.red.sovereign.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * حوار الموافقة على التسجيل — toggle دائم + موافقة طرفين + ربط فعلي بـ CallRecordingManager.start.
 *
 * - مبدّل «التسجيل التلقائي» يُحفظ دائماً في CALL_AUTO_RECORD (SettingsViewModel)
 *   — كان remember مؤقتاً في CallSettingsScreen فيُفقد عند إغلاق الشاشة.
 * - checkbox «موافقة الطرف الآخر» إلزامية: [CallRecordingManager.start] يرفض
 *   false ويُعيد false دون إنشاء ملف — لا تسجيل صامت أبداً.
 * - زر «بدء التسجيل» يُنشئ CallRecordingManager(context, callId) ويستدعي start
 *   فعلياً، ويعرض النتيجة (نجح/رُفض لغياب الموافقة/فشل الميكروفون).
 *
 * @param callId معرف المكالمة النشطة (يُستخدم اسماً لملف التسجيل المشفر).
 * @param onStarted يُستدعى عند نجاح start الحقيقي (لتحديث شارة التسجيل في الواجهة).
 */
@Composable
fun RecordingConsentDialog(
    callId: String,
    onDismiss: () -> Unit,
    onStarted: (() -> Unit)? = null,
    settingsVm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val autoRecord = settingsVm.state.callAutoRecord
    var peerConsented by remember { mutableStateOf(false) }
    var selfConsented by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    // مدير واحد لكل حوار — start/stop على المثيل نفسه (لا مثيل شبح لكل ضغطة).
    val manager = remember(callId) { CallRecordingManager(context, callId) }

    AlertDialog(
        onDismissRequest = {
            if (!starting) {
                runCatching { manager.release() }
                onDismiss()
            }
        },
        title = { Text("تسجيل المكالمة — موافقة الطرفين", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("التسجيل التلقائي للمكالمات", fontWeight = FontWeight.Medium)
                        Text(
                            if (autoRecord) "مفعّل دائماً — سيُطلب تأكيد الموافقة كل مكالمة"
                            else "متوقف — التسجيل يدوي بزر صريح فقط",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoRecord, onCheckedChange = settingsVm::setCallAutoRecord)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selfConsented, onCheckedChange = { selfConsented = it; statusMsg = null })
                    Spacer(Modifier.width(4.dp))
                    Text("أوافق على تسجيل هذه المكالمة (طرفي)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = peerConsented, onCheckedChange = { peerConsented = it; statusMsg = null })
                    Spacer(Modifier.width(4.dp))
                    Text("الطرف الآخر وافق صراحةً (شرط قانوني)")
                }
                statusMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("بدأ")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "الربط: زر البدء يستدعي CallRecordingManager.start(consentGranted) فعلياً — false ترفض دون ملف. الملف m4a يُشفَّر AES-GCM في filesDir/recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val consent = selfConsented && peerConsented
                    if (!consent) {
                        // نُري السلوك الحقيقي: start(false) ترفض — نستدعيها فعلاً لا نكتفي برسالة.
                        scope.launch {
                            starting = true
                            val ok = runCatching { manager.start(consentGranted = false) }.getOrDefault(false)
                            starting = false
                            statusMsg = if (!ok) "رُفض التسجيل: موافقة الطرفين إلزامية (start أعادت false)"
                            else "خطأ غير متوقع"
                        }
                        return@Button
                    }
                    scope.launch {
                        starting = true
                        // ── الربط الفعلي المطلوب: CallRecordingManager.start ──
                        val ok = runCatching { manager.start(consentGranted = true) }.getOrDefault(false)
                        starting = false
                        if (ok) {
                            CallRuntime.isRecording = true
                            statusMsg = "بدأ التسجيل المشفر — بانر التسجيل ظاهر للطرفين"
                            onStarted?.invoke()
                        } else {
                            statusMsg = "تعذّر بدء التسجيل — تحقق من إذن الميكروفون"
                        }
                    }
                },
                enabled = !starting
            ) { Text(if (starting) "جارٍ البدء…" else "بدء التسجيل") }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                runCatching { manager.release() }
                onDismiss()
            }) { Text("إلغاء") }
        }
    )
}

/**
 * سياسة التسجيل: هل يجب طلب التسجيل التلقائي عند بدء مكالمة؟
 * تُقرأ من CALL_AUTO_RECORD الدائم — لا remember مؤقت.
 */
object CallRecordingPolicy {
    fun shouldAutoRecord(settingsVm: SettingsViewModel): Boolean = settingsVm.state.callAutoRecord
    fun shouldAutoRecordSnapshot(): Boolean =
        com.red.sovereign.settings.SettingsRuntime.current.callAutoRecord
}
