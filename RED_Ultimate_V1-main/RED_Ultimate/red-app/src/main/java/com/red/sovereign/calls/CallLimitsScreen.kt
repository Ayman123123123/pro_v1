package com.red.sovereign.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.R
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.settings.CallLimitsPolicy
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * شاشة حدود المكالمات — حد يومي PSTN + حد مدة + عدّاد dial_used فعلي.
 *
 * - الحدان يُحفظان دائماً في [SettingsViewModel] (PSTN_DAILY_LIMIT + MAX_CALL_DURATION)
 *   ويُقرآن عبر [CallLimitsPolicy] (مصدر الحقيقة SettingsRuntime).
 * - العدّاد فعلي من مصدرين: (1) عدّ محلي لسجلات PSTN/DINSTAR الصادرة اليوم من
 *   جدول call_logs، (2) حصة الخادم عبر GET /api/pstn/quota عند توفرها — يُعرض
 *   الأكبر منهما (الخادم هو الفيصل) بنص dial_used نفسه في DialPadScreen.
 * - زر «اختبار الحد» يعرض ما سيراه DialPadScreen (مسموح/محظور) دون اتصال حقيقي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLimitsScreen(
    onBack: () -> Unit = {},
    settingsVm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localLimit = settingsVm.state.pstnDailyLimit
    val maxDuration = settingsVm.state.maxCallDurationSeconds

    var localUsedToday by remember { mutableStateOf<Int?>(null) }
    var serverUsed by remember { mutableStateOf<Int?>(null) }
    var serverLimit by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var testMsg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            // 1) عدّ محلي فعلي: مكالمات PSTN/DINSTAR الصادرة اليوم من Room.
            localUsedToday = runCatching {
                val repo = LocalRepository(context.applicationContext)
                val logs = repo.getCallLogs().first()
                val startOfDay = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                logs.count { log ->
                    (log.route.equals("PSTN", true) || log.route.equals("DINSTAR", true)) &&
                        log.direction.equals("OUTGOING", true) && log.timestamp >= startOfDay
                }
            }.getOrNull()
            // 2) حصة الخادم: GET /api/pstn/quota (إن وجد) — بديل GET /api/pstn/ports/status.
            val quota = runCatching {
                val client = AuthorizedApiClient(TokenStore(context.applicationContext))
                val res = client.request("GET", "/api/pstn/quota", "")
                if (res is com.red.sovereign.auth.ApiResult.Success) {
                    val j = Json { ignoreUnknownKeys = true }
                    j.decodeFromString<QuotaResponse>(res.value)
                } else null
            }.getOrNull()
            if (quota != null) {
                serverUsed = quota.usedToday
                serverLimit = quota.dailyLimit
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // العدّاد الفعلي المعروض: الأكبر بين المحلي والخادم (الخادم فيصل عند التعارض).
    val effectiveUsed = maxOf(localUsedToday ?: 0, serverUsed ?: 0)
    // الحد الفعّال: حد الخادم عند توفره وإلا المحلي (0 = بلا حد).
    // إصلاح NPE: كان serverLimit!! يقصف عند سباق refresh (null بين التعيينين).
    val effectiveLimit = serverLimit?.takeIf { it > 0 } ?: localLimit
    val allowed = CallLimitsPolicy.isDialAllowed(effectiveUsed, effectiveLimit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.call_limits)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SovereignColors.SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── العدّاد الفعلي dial_used ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الاستخدام اليومي الفعلي", fontWeight = FontWeight.Bold, color = YounesEmerald)
                    if (loading && localUsedToday == null) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            context.getString(R.string.dial_used, effectiveUsed, effectiveLimit),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (effectiveLimit > 0) {
                            LinearProgressIndicator(
                                progress = { (effectiveUsed.toFloat() / effectiveLimit).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            "محلي اليوم: ${localUsedToday ?: "—"} · الخادم: ${
                                if (serverUsed != null) "$serverUsed / $serverLimit" else "غير متاح (عرض المحلي)"
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (allowed) "الحالة: مسموح — DialPadScreen سيقبل الاتصال"
                            else "الحالة: محظور — DialPadScreen سيعرض حد الحصة اليومي",
                            color = if (allowed) YounesEmerald else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("تحديث العدّاد") }
                }
            }
            // ── الحد اليومي ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الحد اليومي لمكالمات PSTN: ${if (localLimit == 0) "بلا حد" else "$localLimit مكالمة"}", fontWeight = FontWeight.Bold)
                    Slider(
                        value = localLimit.toFloat(),
                        onValueChange = { settingsVm.setPstnDailyLimit(it.toInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                    Text(
                        "0 = بلا حد محلي (حد الخادم من bridge يبقى فيصلاً في DialPadScreen). يُحفظ في PSTN_DAILY_LIMIT.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // ── حد المدة ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الحد الأقصى لمدة المكالمة: ${CallLimitsPolicy.maxDurationLabelAr()}", fontWeight = FontWeight.Bold)
                    Slider(
                        value = maxDuration.toFloat(),
                        onValueChange = { settingsVm.setMaxCallDuration(it.toInt()) },
                        valueRange = 60f..7200f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(300, 900, 1800, 3600).forEach { preset ->
                            OutlinedButton(onClick = { settingsVm.setMaxCallDuration(preset) }) {
                                Text("${preset / 60} د")
                            }
                        }
                    }
                    Text(
                        "يُقرأ عبر CallLimitsPolicy.maxDurationMs/isDurationExceeded — تُنهي شاشة المكالمة النشطة عند التجاوز. يُحفظ في MAX_CALL_DURATION.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    testMsg = if (CallLimitsPolicy.isDialAllowed(effectiveUsed, effectiveLimit))
                        "اختبار الحد: مسموح — $effectiveUsed / $effectiveLimit (DialPadScreen سيقبل)"
                    else "اختبار الحد: محظور — $effectiveUsed / $effectiveLimit (DialPadScreen سيعرض خطأ الحصة)"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("اختبار الحد الآن") }
            testMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** حوار مضمّن لحدود المكالمات داخل DeviceSettings (يُعيد استخدام الشاشة الكاملة). */
@Composable
fun CallLimitsDialog(
    onDismiss: () -> Unit,
    settingsVm: SettingsViewModel = viewModel()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حدود المكالمات", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val localLimit = settingsVm.state.pstnDailyLimit
                val maxDuration = settingsVm.state.maxCallDurationSeconds
                val ctx = LocalContext.current
                var localUsedToday by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    localUsedToday = runCatching {
                        val repo = LocalRepository(ctx.applicationContext)
                        val logs = repo.getCallLogs().first()
                        val startOfDay = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        logs.count { log ->
                            (log.route.equals("PSTN", true) || log.route.equals("DINSTAR", true)) &&
                                log.direction.equals("OUTGOING", true) && log.timestamp >= startOfDay
                        }
                    }.getOrNull()
                }
                val used = localUsedToday ?: 0
                Text(
                    ctx.getString(R.string.dial_used, used, localLimit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("الحد اليومي PSTN: ${if (localLimit == 0) "بلا حد" else "$localLimit"}", fontWeight = FontWeight.Medium)
                Slider(value = localLimit.toFloat(), onValueChange = { settingsVm.setPstnDailyLimit(it.toInt()) }, valueRange = 0f..50f, steps = 49)
                Text("حد المدة: ${CallLimitsPolicy.maxDurationLabelAr()}", fontWeight = FontWeight.Medium)
                Slider(value = maxDuration.toFloat(), onValueChange = { settingsVm.setMaxCallDuration(it.toInt()) }, valueRange = 60f..7200f)
                Text(
                    "الشاشة الكاملة (مع حصة الخادم واختبار الحد) متاحة كـ CallLimitsScreen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

@kotlinx.serialization.Serializable
private data class QuotaResponse(
    val usedToday: Int = 0,
    val dailyLimit: Int = 0
)
