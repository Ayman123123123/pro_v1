package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HdrStrong
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import com.red.sovereign.util.BatteryOptimizationHelper
import com.red.sovereign.ui.theme.YounesEmerald
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors

/** تسميتا منتقي الجودة — تقابلان قيمتي `dataSaverCalls` لا أكثر. */
private const val QUALITY_SAVER = "توفير البيانات"
private const val QUALITY_AUTO = "تلقائي"

/**
 * شاشة إعدادات المكالمات — Call Settings Screen
 *
 * تتحكم في:
 * - جودة الفيديو (توفير البيانات / تلقائي حسب الشبكة)
 * - كتم تلقائي عند الدخول لمكالمة (دائم عبر YounesSettings.autoMuteOnEntry)
 * - حفظ سجل المكالمات مشفراً (دائماً عبر CallLogCipher)
 * - إعدادات الصوت: مكبر تلقائي + أولوية بلوتوث (دائمان عبر Prefs ويقرأهما
 *   prepareAudio في YounesCallService و GroupCallService عند بدء كل مكالمة)
 * - نغمة المكالمة + الاهتزاز (دائمان عبر call_ringtone_uri/call_vibration
 *   ويقرأهما startRingtone في الخدمتين — اختيار عبر RingtonePickerDialog
 *   بنظام RingtoneManager.ACTION_RINGTONE_PICKER مع معاينة)
 * - إعدادات الخصوصية (إشعارات المكالمات الدائمة)
 * - إعدادات التطوير (debug, telemetry — جلسة فقط)
 *
 * ## ما يُحفظ فعلاً
 *
 * كل ما يلي له حقل حقيقي في [com.red.sovereign.settings.YounesSettings]
 * ويمرّ عبر [SettingsViewModel] فيُكتب في `SharedPreferences` ويُقرأ عبر
 * `SettingsRuntime.current`:
 *
 * - **جودة الفيديو** ⇦ `dataSaverCalls` — يقرأها RedQualityManager.videoProfile.
 * - **إشعارات المكالمات** ⇦ `callNotifications` — تقرأها الخدمات قبل الرنين.
 * - **نغمة المكالمة** ⇦ `callRingtoneUri` — يقرأها startRingtone في الخدمتين.
 * - **اهتزاز الرنين** ⇦ `callVibration` — يقرأها startRingtone قبل Vibrator.
 * - **مكبر تلقائي** ⇦ `autoSpeaker` — يقرأه prepareAudio عند بدء المكالمة.
 * - **أولوية بلوتوث** ⇦ `bluetoothPriority` — يقرأه prepareAudio قبل قرار المكبر.
 * - **كتم تلقائي** ⇦ `autoMuteOnEntry` — يُطبق بعد إنشاء محرك WebRTC.
 *
 * «تشفير سجل المكالمات» ليس مفتاحاً: [CallLogCipher] يُطبَّق دائماً.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings: SettingsViewModel = viewModel()
    // مُخزَّنة فعلاً في YounesSettings (دائمة عبر SharedPreferences + SettingsRuntime)
    val dataSaverCalls = settings.state.dataSaverCalls
    val autoSpeaker = settings.state.autoSpeaker
    val bluetoothPriority = settings.state.bluetoothPriority
    val autoMuteOnEntry = settings.state.autoMuteOnEntry
    // جلسة فقط: لا أثر لها على المكالمات بعد (telemetry/debug)
    val persistentTelemetry = settings.state.devTelemetryEnabled
    val persistentDebug = settings.state.devDebugMode
    val persistentWebrtc = settings.state.devWebrtcLogging
    // التسجيل التلقائي دائم عبر CALL_AUTO_RECORD — يُقرأ قبل CallRecordingManager.start
    // (الحوار الفعلي RecordingConsentDialog يُفتح من شاشة المكالمة النشطة حيث callId حي).
    var confirmClearHistory by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المكالمات", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── قسم الجودة ───────────────────────────────────────────────
            SettingsSectionTitle("جودة المكالمة")
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    VideoQualitySelector(
                        selected = if (dataSaverCalls) QUALITY_SAVER else QUALITY_AUTO
                    ) { settings.setDataSaverCalls(it == QUALITY_SAVER) }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HdrStrong, "جودة", tint = AqyalGold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("جودة الفيديو", color = Color.White, fontSize = 14.sp)
                        }
                        Text(
                            if (dataSaverCalls) "توفير البيانات" else "تلقائي حسب الشبكة",
                            color = AqyalGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── قسم الصوت ───────────────────────────────────────────────
            SettingsSectionTitle("الصوت والمكبر")
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = Icons.Default.Speaker,
                title = "مكبر الصوت التلقائي",
                description = "تفعيل مكبر الصوت عند بدء المكالمة (يُحفظ دائماً ويطبقه prepareAudio)",
                checked = autoSpeaker,
                onCheckedChange = settings::setAutoSpeaker
            )

            SettingRow(
                icon = Icons.Default.Bluetooth,
                title = "أولوية البلوتوث",
                description = "توجيه الصوت لجهاز بلوتوث متصل عند توفره قبل قرار المكبر (يُحفظ دائماً)",
                checked = bluetoothPriority,
                onCheckedChange = settings::setBluetoothPriority
            )

            SettingRow(
                icon = Icons.Default.MicOff,
                title = "كتم تلقائي عند الدخول",
                description = "كتم الميكروفون تلقائياً عند دخول مكالمة (يُحفظ دائماً ويُطبق بعد إنشاء المحرك)",
                checked = autoMuteOnEntry,
                onCheckedChange = settings::setAutoMuteOnEntry
            )

            Spacer(Modifier.height(16.dp))

            // ── قسم النغمة والاهتزاز ────────────────────────────────────
            SettingsSectionTitle("نغمة المكالمة والاهتزاز")
            Spacer(Modifier.height(8.dp))

            CallRingtoneSettingRow(settings)

            SettingRow(
                icon = Icons.Default.Vibration,
                title = "اهتزاز مع الرنين",
                description = "اهتزاز Vibrator مع نغمة المكالمة الواردة (يقرأه startRingtone في الخدمتين)",
                checked = settings.state.callVibration,
                onCheckedChange = settings::setCallVibration
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showRingtoneDialog = true },
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, "حوار النغمة", tint = AqyalGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("حوار النغمة والمعاينة", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "فتح RingtonePickerDialog مع المعاينة ومفتاح الاهتزاز",
                                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp
                            )
                        }
                    }
                    Text("فتح", color = AqyalGold, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── قسم الخصوصية ─────────────────────────────────────────────
            SettingsSectionTitle("الخصوصية والأمان")
            Spacer(Modifier.height(8.dp))

            // بلا مبدّل: التشفير غير قابل للتعطيل — CallLogCipher يُطبَّق في كل
            // مسار كتابة للسجل (YounesCallService/GroupCallService/ConferenceService/
            // LiveStreamService)، فمبدّلٌ هنا كان سيوهم بإمكان إيقافه.
            SettingRow(
                icon = Icons.Default.Security,
                title = "تشفير سجل المكالمات",
                description = "مُفعَّل دائماً — يُشفَّر كل سجل محلياً عبر CallLogCipher"
            )

            SettingRow(
                icon = Icons.Default.Power,
                title = "تسجيل المكالمات",
                description = if (settings.state.callAutoRecord) "تسجيل تلقائي مفعّل دائماً — بموافقة الطرفين عبر RecordingConsentDialog"
                else "السماح بتسجيل المكالمات صوتياً — يتطلب موافقة الطرفين",
                checked = settings.state.callAutoRecord,
                onCheckedChange = settings::setCallAutoRecord
            )

            SettingRow(
                icon = Icons.Default.RadioButtonChecked,
                title = "إشعارات المكالمات",
                description = "رنين المكالمات الواردة وإشعارها على شاشة القفل",
                checked = settings.state.callNotifications,
                onCheckedChange = settings::setCallNotifications
            )

            Spacer(Modifier.height(16.dp))

            // ── قسم موثوقية الرنين والبطارية السيادية ──────────────────────
            SettingsSectionTitle("موثوقية الرنين في الخلفية (UnifiedPush)")
            Spacer(Modifier.height(8.dp))

            val isBatteryIgnored = remember { BatteryOptimizationHelper.isBatteryOptimizationIgnored(context) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isBatteryIgnored) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                            null,
                            tint = if (isBatteryIgnored) YounesEmerald else Color(0xFFFFA000),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isBatteryIgnored) "تحسين البطارية مستثنى (مثالي)" else "تحسين البطارية قد يؤخر الرنين",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                "بدون خوادم Google، يحتاج التطبيق إذن العمل في الخلفية لضمان رنين المكالمات فورياً والتطبيق مقتول.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isBatteryIgnored) {
                            TextButton(
                                onClick = { BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.OpenInNew, null, tint = YounesEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("استثناء البطارية", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        TextButton(
                            onClick = { BatteryOptimizationHelper.openAutostartSettings(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.OpenInNew, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إعدادات التشغيل التلقائي", color = AqyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // موزّع UnifiedPush — بدونه لا يرن التطبيق وهو مقتول (كان غير مكشوف إطلاقاً)
            UnifiedPushDistributorCard()

            Spacer(Modifier.height(16.dp))

            // ── قسم التطوير ───────────────────────────────────────────────
            SettingsSectionTitle("أدوات التطوير")
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = Icons.Default.BugReport,
                title = "تفعيل Telemetry",
                description = "إرسال إحصائيات الأداء لجودة المكالمة (CallQualityManager) — دائم عبر dev_telemetry_enabled",
                checked = persistentTelemetry,
                onCheckedChange = settings::setDevTelemetryEnabled
            )

            SettingRow(
                icon = Icons.Default.Build,
                title = "وضع التصحيح",
                description = "عرض سجلات WebRTC مفصلة في Logcat — دائم عبر dev_debug_mode",
                checked = persistentDebug,
                onCheckedChange = settings::setDevDebugMode
            )

            SettingRow(
                icon = Icons.Default.Build,
                title = "تسجيل WebRTC",
                description = "تتبع ICE/SDP الموسع — دائم عبر dev_webrtc_logging",
                checked = persistentWebrtc,
                onCheckedChange = settings::setDevWebrtcLogging
            )

            Spacer(Modifier.height(16.dp))

            // ── زر حذف السجل ─────────────────────────────────────────────
            // يمسح جدول call_logs فعلياً عبر CallManagerIntegration.clearCallLogs،
            // بتأكيد أولاً لأن الحذف غير قابل للتراجع.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmClearHistory = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A0A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Delete, "حذف سجل المكالمات", tint = Color.Red, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حذف سجل المكالمات بالكامل", color = Color.Red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showRingtoneDialog) {
        RingtonePickerDialog(settings = settings, onDismiss = { showRingtoneDialog = false })
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("حذف سجل المكالمات؟") },
            text = { Text("سيُحذف كل سجل المكالمات المحفوظ على هذا الجهاز. لا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    CallManagerIntegration.clearCallLogs(context)
                }) { Text("حذف", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        title,
        color = AqyalGold,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/**
 * حالة موزّع UnifiedPush — «هل يرن التطبيق وهو مقتول؟»
 *
 * بطاقة البطارية أعلاه تقول إن «تحسين البطارية قد يؤخر الرنين»، لكن السبب الأشيع لعدم
 * الرنين **إطلاقاً** شيء آخر تماماً: غياب موزّع UnifiedPush. حينها
 * `VoipPushRegistrar.ensureDistributor` يسجّل سطراً ويمضي، فلا يُصدر الموزّع نقطة نهاية،
 * فلا يُرفع push token للخادم، فلا يجد الخادم ما يوقظ به الجهاز ⇒ لا رنين أبداً ولا أي
 * إشارة للمستخدم بأن السبب هو الموزّع لا الشبكة ولا البطارية. هذه البطاقة تكشف الحالة
 * صراحةً وتسمح بالاختيار؛ ودوال [VoipPushRegistrar.availableDistributors]
 * و[currentDistributor] و[useDistributor] و[currentEndpoint] كانت مكتوبة بالكامل
 * وغير مستدعاة من أي شاشة (واجهة ميتة).
 */
private data class DistributorStatus(
    val installed: List<String> = emptyList(),
    val active: String? = null,
    val endpoint: String? = null
) {
    /** موزّع مُسجَّل + نقطة نهاية معلنة = الخادم يملك ما يوقظ به هذا الجهاز فعلاً. */
    val healthy: Boolean get() = active != null && !endpoint.isNullOrBlank()
}

@Composable
private fun UnifiedPushDistributorCard() {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    // كل نداءات UnifiedPush تعبر حدود عملية (binder) — تُنفَّذ خارج الخيط الرئيسي
    val status by produceState(DistributorStatus(), refreshTick) {
        value = withContext(Dispatchers.IO) {
            DistributorStatus(
                installed = VoipPushRegistrar.availableDistributors(context),
                active = VoipPushRegistrar.currentDistributor(context),
                endpoint = VoipPushRegistrar.currentEndpoint(context)
            )
        }
    }
    val accent = when {
        status.healthy -> YounesEmerald
        status.installed.isEmpty() -> Color(0xFFFF5252)
        else -> Color(0xFFFFA000)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B26)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.healthy) Icons.Default.CheckCircle else Icons.Default.Security,
                    null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            status.healthy -> "الرنين من تطبيق مقتول: جاهز"
                            status.installed.isEmpty() -> "لا يوجد موزّع — لن يرن التطبيق وهو مقتول"
                            else -> "الموزّع غير مُسجَّل بعد"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        when {
                            status.healthy -> "الموزّع: ${status.active}"
                            status.installed.isEmpty() ->
                                "بلا موزّع UnifiedPush لا يستطيع الخادم إيقاظ هذا الجهاز، فلا يرن وهو مقتول. " +
                                    "ثبّت موزّعاً (مثل ntfy) ثم اضغط إعادة الفحص."
                            else -> "اختر موزّعاً لتفعيل الإيقاظ السيادي (بلا خوادم Google)."
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            status.installed.forEach { pkg ->
                TextButton(
                    onClick = {
                        VoipPushRegistrar.useDistributor(context, pkg)
                        refreshTick++
                    }
                ) {
                    Icon(
                        if (pkg == status.active) Icons.Default.CheckCircle else Icons.Default.RadioButtonChecked,
                        null,
                        tint = if (pkg == status.active) YounesEmerald else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pkg,
                        color = if (pkg == status.active) YounesEmerald else Color.White,
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = { refreshTick++ }) {
                Icon(Icons.Default.OpenInNew, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("إعادة الفحص", color = AqyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * منتقي جودة الفيديو — خيارَان فقط لأنهما وحدهما لهما أثر حقيقي.
 *
 * كانت القائمة `LOW/MEDIUM/HIGH/AUTO` بلا أي مخزّن، والجودة في التطبيق تُحسب
 * في [com.red.sovereign.core.RedQualityManager.videoProfile] من مستوى الشبكة
 * ومن `dataSaverCalls` وحده. فأربع درجات ثلاثٌ منها بلا مقابل كانت واجهة
 * كاذبة؛ الخيارَان هنا يقابلان قيمتي ذلك الحقل تماماً.
 */
@Composable
fun VideoQualitySelector(selected: String, onSelect: (String) -> Unit) {
    val qualities = listOf(QUALITY_SAVER, QUALITY_AUTO)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        qualities.forEach { q ->
            QualityChip(
                label = q,
                selected = selected == q,
                onClick = { onSelect(q) }
            )
        }
    }
}

@Composable
fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 96.dp, minHeight = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) AqyalGold else SovereignColors.SurfaceDark)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) Color(0xFF0A0F18) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        if (selected) {
            Icon(Icons.Default.Done, "محدد", tint = AqyalGold, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, title, tint = AqyalGold, modifier = Modifier.size(24.dp))
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            if (onCheckedChange != null) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AqyalGold,
                        checkedTrackColor = AqyalGold.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
