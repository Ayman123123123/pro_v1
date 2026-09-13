package com.red.sovereign.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.red.sovereign.calls.CallTelemetry
import com.red.sovereign.calls.WebRtcBootstrap
import com.red.sovereign.core.RedQualityManager
import com.red.sovereign.core.ServerEndpoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * أعلام المطور المقروءة من [SettingsRuntime] مباشرة — لا نسخة ذاكرة منفصلة.
 * WebRtcEngine/YounesCallService تقرأها قبل التسجيل المفصّل أو فرض TURN.
 */
object DevFlags {
    fun isTelemetryOn(): Boolean = SettingsRuntime.current.devTelemetryEnabled
    fun isDebug(): Boolean = SettingsRuntime.current.devDebugMode
    fun isWebrtcLogging(): Boolean = SettingsRuntime.current.devWebrtcLogging
    fun forceTurn(): Boolean = SettingsRuntime.current.devForceTurn
    fun verboseSignaling(): Boolean = SettingsRuntime.current.devVerboseSignaling
}

/** يصدّر Logcat الأخير + لقطة الإعدادات إلى ملف كاش ويعيد مساره. حقيقي لا وهمي. */
suspend fun exportDevLogs(context: Context): File = withContext(Dispatchers.IO) {
    val logcat = runCatching {
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "800"))
        proc.inputStream.bufferedReader().readText().take(120_000)
    }.getOrDefault("logcat unavailable: ${"no output"}")
    val s = SettingsRuntime.current
    val snap = CallTelemetry.snapshot()
    val dump = buildString {
        appendLine("RED dev export ${java.util.Date()}")
        appendLine("endpoint=${ServerEndpoint.url()}")
        appendLine("telemetry=${s.devTelemetryEnabled} debug=${s.devDebugMode} webrtcLog=${s.devWebrtcLogging} forceTurn=${s.devForceTurn} verboseSig=${s.devVerboseSignaling}")
        appendLine("font=${s.fontFamily} bubble=${s.bubbleStyle}")
        appendLine("dnd=${s.dndEnabled} ${s.dndStartMinutes}-${s.dndEndMinutes}")
        appendLine("callRTT=${snap.avgRttMs}ms loss=${snap.maxLoss} quality=${snap.quality} pending=${snap.pending}")
        appendLine("ice=${WebRtcBootstrap.getCachedIce()?.iceServers?.size ?: 0} servers")
        appendLine("tier=${runCatching { RedQualityManager.tier(context) }.getOrDefault("unknown")}")
        appendLine("--- logcat ---")
        append(logcat)
    }
    val out = File(context.cacheDir, "red_dev_logs.txt")
    out.writeText(dump)
    out
}

/**
 * شاشة المطور: مفاتيح Telemetry/Debug/WebRTC دائمة (SharedPreferences عبر
 * SettingsViewModel — لا remember) + عارض إحصاءات حية + تصدير سجلات + Flags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = viewModel.state
    var status by remember { mutableStateOf<String?>(null) }
    var statsTick by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION")
    statsTick.let { }
    val snap = remember(s, statsTick) { CallTelemetry.snapshot() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خيارات المطور") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("مفاتيح دائمة — تُحفظ فوراً وتبقى بعد إغلاق الشاشة", fontWeight = FontWeight.Bold)
            DevSwitch("تفعيل Telemetry", "إرسال إحصاءات المكالمات المجمعة (بلا PII)", s.devTelemetryEnabled, viewModel::setDevTelemetryEnabled)
            DevSwitch("وضع التصحيح", "سجلات مفصلة في Logcat لمسارات الاتصال", s.devDebugMode, viewModel::setDevDebugMode)
            DevSwitch("تسجيل WebRTC", "تتبع ICE/SDP الموسّع عند التشخيص", s.devWebrtcLogging, viewModel::setDevWebrtcLogging)
            Text("Flags", fontWeight = FontWeight.Bold)
            DevSwitch("فرض TURN", "تجاوز المرشحات المباشرة وإجبار الترحيل عبر TURN", s.devForceTurn, viewModel::setDevForceTurn)
            DevSwitch("إشارات مطوّلة", "طباعة عروض SDP الكاملة في السجلات", s.devVerboseSignaling, viewModel::setDevVerboseSignaling)

            Text("إحصاءات حية", fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RTT الوسطي: ${snap.avgRttMs}ms", style = MaterialTheme.typography.bodySmall)
                    Text("أقصى فقد: ${"%.2f".format(snap.maxLoss)}%", style = MaterialTheme.typography.bodySmall)
                    Text("الجودة الأخيرة: ${snap.quality}", style = MaterialTheme.typography.bodySmall)
                    Text("أحداث معلقة: ${snap.pending}", style = MaterialTheme.typography.bodySmall)
                    Text("النقطة: ${ServerEndpoint.url()}", style = MaterialTheme.typography.bodySmall)
                    Text("الشبكة: ${runCatching { RedQualityManager.tier(context).name }.getOrDefault("?")}", style = MaterialTheme.typography.bodySmall)
                    Text("ICE مخزّن: ${WebRtcBootstrap.getCachedIce()?.iceServers?.size ?: 0} خادم", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { statsTick++ }, modifier = Modifier.weight(1f)) { Text("تحديث الإحصاءات") }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (cm == null) {
                            status = "تعذر الوصول إلى الحافظة"
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("devstats", "RTT=${snap.avgRttMs} loss=${snap.maxLoss} q=${snap.quality} pending=${snap.pending} ep=${ServerEndpoint.url()}"))
                            status = "نُسخت الإحصاءات"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("نسخ") }
            }
            Text("السجلات", fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    status = "يُصدَّر…"
                    scope.launch {
                        runCatching {
                            val file = exportDevLogs(context)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "مشاركة سجلات المطور"))
                            status = "صُدّر: ${file.name} (${file.length() / 1024}KB)"
                        }.onFailure { status = "فشل التصدير: ${it.message?.take(100)}" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تصدير السجلات ومشاركتها") }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DevSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onChange)
        }
    }
}
