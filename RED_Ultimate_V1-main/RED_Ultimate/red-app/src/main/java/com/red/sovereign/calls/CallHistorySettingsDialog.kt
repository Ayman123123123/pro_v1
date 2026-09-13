package com.red.sovereign.calls

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.settings.CallHistoryRetentionPolicy
import com.red.sovereign.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * حوار إعدادات سجل المكالمات — يُفتح من DeviceSettings بند call_history.
 *
 * - احتفاظ أيام (CALL_HISTORY_RETENTION: 7/30/90/365) يُحفظ في SettingsViewModel
 *   ويُطبَّق حذف الأقدم فعلياً عبر [CallHistoryViewModel.pruneExpired].
 * - مزامنة تلقائية (CALL_HISTORY_SYNC) تُحترم في [CallHistoryViewModel.syncNow].
 * - تصدير CSV عبر [CallHistoryViewModel.exportCsvFile] (المستخدمة في CallHistoryScreen)
 *   مع مشاركة حقيقية عبر FileProvider — لا ملف صامت.
 * - مزامنة الآن (GET /api/calls/history) + مسح السجل (DELETE call_logs).
 */
@Composable
fun CallHistorySettingsDialog(
    onDismiss: () -> Unit,
    settingsVm: SettingsViewModel = viewModel(),
    historyVm: CallHistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val retention = settingsVm.state.callHistoryRetentionDays
    val syncEnabled = settingsVm.state.callHistorySync
    var busy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val retentionOptions = listOf(7, 30, 90, 365)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل المكالمات — احتفاظ وتصدير ومزامنة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "الاحتفاظ الحالي: ${CallHistoryRetentionPolicy.labelAr()} — الأقدم يُحذف من الجهاز فعلياً",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                retentionOptions.forEach { days ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            settingsVm.setCallHistoryRetention(days)
                            statusMsg = null
                            historyVm.pruneExpired { deleted ->
                                statusMsg = "طُبِّق الاحتفاظ ($days يوم) — حُذف $deleted سجلاً قديماً"
                            }
                        }.then(Modifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = retention == days, onClick = {
                            settingsVm.setCallHistoryRetention(days)
                            historyVm.pruneExpired { deleted ->
                                statusMsg = "طُبِّق الاحتفاظ ($days يوم) — حُذف $deleted سجلاً قديماً"
                            }
                        })
                        Spacer(Modifier.width(8.dp))
                        Text("$days يوم", fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("المزامنة التلقائية", fontWeight = FontWeight.Medium)
                        Text(
                            if (syncEnabled) "مفعّلة — السجل يُجلب من الخادم عند الفتح"
                            else "متوقفة — عرض محلي فقط دون شبكة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = syncEnabled, onCheckedChange = settingsVm::setCallHistorySync)
                }
                statusMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (busy) CircularProgressIndicator()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            busy = true
                            historyVm.syncNow()
                            scope.launch {
                                kotlinx.coroutines.delay(1200)
                                busy = false
                                statusMsg = "اكتملت المزامنة — ${historyVm.calls.size} سجلاً"
                                historyVm.pruneExpired()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) { Text("مزامنة الآن") }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                val file = historyVm.exportCsvFile(context)
                                busy = false
                                if (file != null) {
                                    val uri = runCatching {
                                        FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".fileprovider",
                                            file
                                        )
                                    }.getOrNull() ?: runCatching {
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".provider",
                                            file
                                        )
                                    }.getOrNull()
                                    if (uri != null) {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(share, "تصدير سجل المكالمات CSV"),
                                            null
                                        )
                                        statusMsg = "صُدِّر ${historyVm.calls.size} سجلاً إلى ${file.name}"
                                    } else {
                                        statusMsg = "صُدِّر إلى الكاش: ${file.absolutePath} (تعذّرت المشاركة — انسخ يدوياً)"
                                    }
                                } else {
                                    statusMsg = "تعذّر التصدير — لا سجلات أو خطأ قرص"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    ) { Text("تصدير CSV") }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            historyVm.clearHistory()
                            statusMsg = "مُسح السجل المحلي بالكامل (جدول call_logs)"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("مسح السجل", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(2.dp))
                Text(
                    "الحفظ: call_history_retention/call_history_sync في younes_user_preferences — التصدير عبر exportCsvFile نفسها في CallHistoryScreen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}
