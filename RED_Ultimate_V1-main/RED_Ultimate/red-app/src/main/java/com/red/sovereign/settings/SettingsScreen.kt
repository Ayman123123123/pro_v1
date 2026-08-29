package com.red.sovereign.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

private enum class SettingsPage { ROOT, ACCOUNT, PRIVACY, APPEARANCE, CHATS, NOTIFICATIONS, DATA, CALLS, DEVICES, SERVER, SERVER_ADVANCED, FOLDERS, STARRED, BLOCKED, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YounesSettingsSheet(
    account: AuthState.Authenticated,
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    logout: () -> Unit,
    dismiss: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    val deviceSettings: DeviceSettingsViewModel = viewModel()
    LaunchedEffect(page) { if (page == SettingsPage.DEVICES) deviceSettings.load() }
    var confirmLogout by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != SettingsPage.ROOT) IconButton({ page = SettingsPage.ROOT }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(if (page == SettingsPage.ROOT) "الإعدادات" else pageTitle(page), style = MaterialTheme.typography.headlineSmall)
                    Text("تحكم محلي واضح دون إعدادات وهمية", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            when (page) {
                SettingsPage.ROOT -> SettingsRoot(account, viewModel.cacheBytes, onPage = { page = it }, onLogout = { confirmLogout = true })
                SettingsPage.ACCOUNT -> AccountSettings(account, authViewModel)
                SettingsPage.PRIVACY -> PrivacySettings(viewModel)
                SettingsPage.APPEARANCE -> AppearanceSettings(viewModel)
                SettingsPage.CHATS -> ChatSettings(viewModel)
                SettingsPage.NOTIFICATIONS -> NotificationSettings(viewModel)
                SettingsPage.DATA -> DataSettings(viewModel)
                SettingsPage.CALLS -> CallSettings(viewModel)
                SettingsPage.DEVICES -> DevicesSettings(deviceSettings)
                SettingsPage.SERVER -> ServerSettings(onAdvanced = { page = SettingsPage.SERVER_ADVANCED })
                // الشاشة الغنية (اكتشاف تلقائي + إدخال يدوي + تحقق توقيع السلطة)
                // كانت مكتوبة بالكامل لكن غير موصولة بأي تنقّل، فبقيت كوداً ميتاً.
                SettingsPage.SERVER_ADVANCED -> SmartServerSettingsScreen(onBack = { page = SettingsPage.SERVER })
                SettingsPage.FOLDERS -> FolderSettings()
                SettingsPage.STARRED -> StarredSettings()
                SettingsPage.BLOCKED -> BlockedSettings()
                SettingsPage.ABOUT -> AboutSettings()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("تسجيل الخروج؟") },
        text = { Text("سيتم حذف رموز الجلسة من هذا الهاتف. مفاتيح الهوية المحلية لا تُرفع إلى الخادم.") },
        confirmButton = { Button({ confirmLogout = false; logout() }) { Text("تسجيل الخروج") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("إلغاء") } }
    )
}

@Composable
private fun SettingsRoot(account: AuthState.Authenticated, cacheBytes: Long, onPage: (SettingsPage) -> Unit, onLogout: () -> Unit) {
    val rows = listOf(
        SettingDestination(SettingsPage.ACCOUNT, Icons.Default.Person, "الحساب والهوية", "@${account.username} · ${account.redId}", YounesEmerald),
        SettingDestination(SettingsPage.PRIVACY, Icons.Default.Security, "الخصوصية والأمان", "إيصالات القراءة والكتابة والروابط", Color(0xFF65D7E7)),
        SettingDestination(SettingsPage.APPEARANCE, Icons.Default.Palette, "المظهر والوصولية", "الخط والتباين والحركة والكثافة", Color(0xFFA78BFA)),
        SettingDestination(SettingsPage.CHATS, Icons.AutoMirrored.Filled.Chat, "الدردشات والوسائط", "التنزيل وسرعة الصوت وسلوك المحادثة", Color(0xFF5CC8FF)),
        SettingDestination(SettingsPage.NOTIFICATIONS, Icons.Default.Notifications, "الإشعارات", "الرسائل والمكالمات ومعاينة المحتوى", Color(0xFFFFB65C)),
        SettingDestination(SettingsPage.DATA, Icons.Default.Storage, "البيانات والتخزين", "${formatBytes(cacheBytes)} مستخدمة في cache", Color(0xFF8BC34A)),
        SettingDestination(SettingsPage.CALLS, Icons.Default.Call, "المكالمات", "توفير البيانات والصوت وDINSTAR المنفصل", AqyalGold),
        SettingDestination(SettingsPage.DEVICES, Icons.Default.Devices, "الأجهزة والشهادات", "الأجهزة المعتمدة وتنبيهات المفاتيح", Color(0xFFEC7FA9)),
        SettingDestination(SettingsPage.SERVER, Icons.Default.Wifi, "الخادم والشبكة", "Local-first وWireGuard وحالة نقطة الاتصال", Color(0xFF4DD0E1)),
        SettingDestination(SettingsPage.FOLDERS, Icons.Default.Folder, "مجلدات الدردشة", "تنظيم محلي مثل تلجرام — على الجهاز فقط", Color(0xFF81C784)),
        SettingDestination(SettingsPage.STARRED, Icons.Default.Star, "الرسائل المميّزة", "الرسائل التي نجّمتها من المحادثة", AqyalGold),
        SettingDestination(SettingsPage.BLOCKED, Icons.Default.Block, "المحظورون", "من لا يصل إليك برسالة أو مكالمة", Color(0xFFE57373)),
        SettingDestination(SettingsPage.ABOUT, Icons.Default.Info, "حول يونس", "الإصدار والبنية والتراخيص", MaterialTheme.colorScheme.onSurfaceVariant)
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(570.dp)) {
        items(rows) { row -> DestinationRow(row) { onPage(row.page) } }
        item { OutlinedButton(onLogout, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("تسجيل الخروج من هذا الجهاز") } }
    }
}

@Composable
private fun DestinationRow(row: SettingDestination, click: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = click),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(row.icon, null, tint = row.color) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium)
            Text(row.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun AccountSettings(account: AuthState.Authenticated, authViewModel: AuthViewModel) {
    var username by remember(account.username) { mutableStateOf(account.username) }
    var displayName by remember { mutableStateOf("") }
    var savingUsername by remember { mutableStateOf(false) }
    var savingName by remember { mutableStateOf(false) }
    var msgUsername by remember { mutableStateOf<String?>(null) }
    var msgName by remember { mutableStateOf<String?>(null) }
    SettingsList {
        item {
            // رأس البروفايل
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(YounesEmerald, AqyalCyanGlow, AqyalGold))), contentAlignment = Alignment.Center) {
                        Text(account.username.take(1), color = Color(0xFF03120E), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("يونس • @${account.username}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(account.redId, color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { InfoCard("حالة PSTN", if (account.pstnEnabled) "مصرح بالاتصال اليمني عبر DINSTAR" else "غير مفعل لهذا الحساب", Icons.Default.Call) }
        item {
            Text("اسم المستخدم", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(username, { username = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true)
            msgUsername?.let { Text(it, color = if (it.startsWith("تم")) YounesEmerald else MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Button({ savingUsername = true; authViewModel.updateUsername(username) { ok, m -> savingUsername = false; msgUsername = m } }, Modifier.fillMaxWidth(), enabled = username.isNotBlank() && username != account.username && !savingUsername) {
                if (savingUsername) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("حفظ اسم المستخدم")
            }
        }
        item {
            Text("الاسم المعروض (البروفايل)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(displayName, { displayName = it.take(50) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(account.username) })
            msgName?.let { Text(it, color = if (it.startsWith("تم")) YounesEmerald else MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Button({ savingName = true; authViewModel.updateDisplayName(displayName) { ok, m -> savingName = false; msgName = m } }, Modifier.fillMaxWidth(), enabled = displayName.isNotBlank() && !savingName) {
                if (savingName) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("حفظ الاسم المعروض")
            }
        }
        item { InfoCard("كلمة المرور", "تغيير كلمة المرور يتطلب إبطال الجلسات الحالية — يُدار من الخادم بصلاحيات إضافية.", Icons.Default.Lock) }
    }
}

@Composable private fun PrivacySettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("قفل التطبيق بالبصمة", "اطلب بصمة/نمط الجهاز لفتح يونس — مفاتيحك محمية بخطوة إضافية", vm.state.appLockEnabled, vm::setAppLockEnabled) }
    item {
        Text("مهلة إعادة القفل · ${vm.state.lockTimeoutSeconds} ث", fontWeight = FontWeight.SemiBold)
        Slider(value = vm.state.lockTimeoutSeconds.toFloat(), onValueChange = { vm.setLockTimeoutSeconds(it.toInt()) }, valueRange = 5f..120f, steps = 22)
    }
    item { ToggleSetting("إخفاء آخر ظهور", "مثل واتساب: إن أخفيت ظهورك لن ترى ظهور الآخرين", vm.state.hideLastSeen, vm::setHideLastSeen) }
    item { VisibilityPicker("آخر ظهور والحالة", vm.state.lastSeenVisibility, vm::setLastSeenVisibility) }
    item { VisibilityPicker("صورة البروفايل", vm.state.profilePhotoVisibility, vm::setProfilePhotoVisibility) }
    item { VisibilityPicker("النبذة", vm.state.aboutVisibility, vm::setAboutVisibility) }
    item { VisibilityPicker("من يضيفني لمجموعة", vm.state.whoCanAddToGroups, vm::setWhoCanAddToGroups) }
    item { VisibilityPicker("من يتصل بي عبر يونس", vm.state.whoCanCall, vm::setWhoCanCall) }
    item { ToggleSetting("إيصالات القراءة", "إرسال READ بعد فتح الرسالة — إن أوقفتها لن ترى إيصالات الآخرين", vm.state.readReceipts, vm::setReadReceipts) }
    item { ToggleSetting("مؤشر الكتابة", "أرسل «يكتب…» فقط أثناء الكتابة الفعلية", vm.state.typingIndicators, vm::setTypingIndicators) }
    item { LockedSetting("معاينات الروابط", "متوقفة حتى اكتمال proxy آمن وحماية SSRF وإخفاء عنوان IP") }
    item { LockedSetting("حماية لقطات الشاشة", "مفعلة إجباريًا للمحادثات والمفاتيح الحساسة") }
    item { LockedSetting("مفاتيح الهوية", "تبقى داخل Android Keystore ولا يمكن تصديرها") }
}

@Composable private fun AppearanceSettings(vm: SettingsViewModel) {
    // مزامنة AppThemeState مع SettingsViewModel
    val s = vm.state
    androidx.compose.runtime.LaunchedEffect(s.themePreset, s.themeMode, s.liquidGlassEnabled, s.customPrimary, s.highContrast, s.reduceMotion, s.fontScale) {
        runCatching { com.red.sovereign.ui.theme.AppThemeState.currentPreset = com.red.sovereign.ui.theme.AppThemePreset.valueOf(s.themePreset) }
        runCatching { com.red.sovereign.ui.theme.AppThemeState.themeMode = com.red.sovereign.ui.theme.AppThemeMode.valueOf(s.themeMode) }
        com.red.sovereign.ui.theme.AppThemeState.highContrast = s.highContrast
        com.red.sovereign.ui.theme.AppThemeState.liquidGlassEnabled = s.liquidGlassEnabled
        com.red.sovereign.ui.theme.AppThemeState.reduceMotion = s.reduceMotion
        com.red.sovereign.ui.theme.AppThemeState.fontScale = s.fontScale
        com.red.sovereign.ui.theme.AppThemeState.customPrimary = if (s.customPrimary != 0) Color(s.customPrimary) else null
    }
    SettingsList {
        item {
            Text("حجم الخط · ${(vm.state.fontScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(value = vm.state.fontScale, onValueChange = vm::setFontScale, valueRange = .85f..1.30f, steps = 8)
        }
        item { ToggleSetting("تباين مرتفع", "حدود ونصوص أوضح — AAA 7:1", vm.state.highContrast, vm::setHighContrast) }
        item { ToggleSetting("واجهة مدمجة", "مسافات أقل للقوائم الطويلة", vm.state.compactMode, vm::setCompactMode) }
        item { ToggleSetting("تقليل الحركة", "يحترم prefers-reduced-motion — يثبت Liquid Glass", vm.state.reduceMotion, vm::setReduceMotion) }
        item { ToggleSetting("Liquid Glass 2026", "زجاج سائل شفاف كتليجرام/واتساب — يحتاج إعادة تشغيل بصرية", vm.state.liquidGlassEnabled, vm::setLiquidGlass) }
        item {
            Text("نمط الإضاءة", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf("SYSTEM" to "تلقائي", "DARK" to "ليلي", "LIGHT" to "فاتح").forEach { (id, label) ->
                    val sel = vm.state.themeMode == id
                    AssistChip(
                        onClick = { vm.setThemeMode(id) },
                        label = { Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = { if (sel) Text("●") },
                        colors = if (sel) AssistChipDefaults.assistChipColors(containerColor = YounesEmerald, labelColor = Color(0xFF06090F)) else AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
        item {
            Text("الثيم — Liquid Glass", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("6 خيارات: سيادي/تلجرام/واتساب/أوليد/ديناميكي (Material You)/مخصص", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            val themes = listOf(
                "SOVEREIGN" to Triple("سيادي", Color(0xFF14C79A), Color(0xFF0A0F18)),
                "TELEGRAM_DARK" to Triple("تلجرام", Color(0xFF2AABEE), Color(0xFF0E1621)),
                "WHATSAPP_DARK" to Triple("واتساب", Color(0xFF00A884), Color(0xFF0B141A)),
                "OLED_BLACK" to Triple("أوليد", Color(0xFF00E676), Color(0xFF000000)),
                "DYNAMIC" to Triple("ديناميكي", Color(0xFF4FC3F7), Color(0xFF1A263D)),
                "CUSTOM" to Triple("مخصص", if (vm.state.customPrimary != 0) Color(vm.state.customPrimary) else Color(0xFFE0B551), Color(0xFF131C29))
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    themes.take(3).forEach { (id, info) ->
                        val (label, accent, bg) = info
                        val sel = vm.state.themePreset == id
                        Card(
                            Modifier.weight(1f).clickable { vm.setThemePreset(id) },
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                    if (sel) Icon(Icons.Default.Check, null, tint = Color(0xFF06090F), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    themes.drop(3).forEach { (id, info) ->
                        val (label, accent, bg) = info
                        val sel = vm.state.themePreset == id
                        Card(
                            Modifier.weight(1f).clickable { vm.setThemePreset(id) },
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                    if (sel) Icon(Icons.Default.Check, null, tint = Color(0xFF06090F), modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (vm.state.themePreset == "CUSTOM") {
                Spacer(Modifier.height(10.dp))
                Text("لون مخصص — اختر واحداً يضمن ≥4.5:1:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(
                        Color(0xFF14C79A) to "زمرد",
                        Color(0xFFE0B551) to "ذهب",
                        Color(0xFF4D9FE8) to "أزرق",
                        Color(0xFFE53935) to "أحمر",
                        Color(0xFF8E24AA) to "بنفسجي",
                        Color(0xFF00ACC1) to "تركواز"
                    ).forEach { (c, _) ->
                        val sel = vm.state.customPrimary == c.value.toInt()
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .clickable { vm.setCustomPrimary(c.value.toInt()) }
                                .then(if (sel) Modifier.background(Color.White.copy(alpha = 0.0f)) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item { LockedSetting("RTL والعربية", "مفعلة تلقائيًا حسب لغة النظام — Plex Arabic ثنائي النص") }
    }
}

@Composable private fun ChatSettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("تنزيل الوسائط على Wi‑Fi", "الوسائط المصرح بها فقط", vm.state.autoDownloadWifi, vm::setWifiDownload) }
    item { ToggleSetting("تنزيل عبر بيانات الهاتف", "مغلق افتراضيًا لحماية الباقة", vm.state.autoDownloadMobile, vm::setMobileDownload) }
    item {
        Text("حد التنزيل التلقائي · ${vm.state.autoDownloadLimitMb} MiB", fontWeight = FontWeight.SemiBold)
        Slider(value = vm.state.autoDownloadLimitMb.toFloat(), onValueChange = { vm.setAutoDownloadLimit(it.toInt()) }, valueRange = 1f..99f, steps = 13)
    }
    item { Text("سرعة الرسائل الصوتية الافتراضية", fontWeight = FontWeight.SemiBold) }
    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1f, 1.5f, 2f).forEach { speed -> AssistChip({ vm.setDefaultPlaybackSpeed(speed) }, { Text("${speed}×") }, leadingIcon = { if (vm.state.defaultPlaybackSpeed == speed) Text("●") }) } } }
    item { ToggleSetting("Enter للإرسال", "زر الإدخال يرسل الرسالة بدل سطر جديد", vm.state.enterToSend, vm::setEnterToSend) }
    item { ToggleSetting("حفظ الوسائط في المعرض", "بعد فك التشفير فقط وبموافقتك — الافتراضي مغلق", vm.state.saveMediaToGallery, vm::setSaveMediaToGallery) }
    item { ToggleSetting("أرشفة المكتوم تلقائياً", "إن كتمت محادثة دائماً تُنقل للأرشيف", vm.state.autoArchiveMuted, vm::setAutoArchiveMuted) }
}

@Composable private fun NotificationSettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("إشعارات الرسائل", "تنبيه عند وصول رسالة مشفرة", vm.state.messageNotifications, vm::setMessageNotifications) }
    item { ToggleSetting("إشعارات المجموعات", "تنبيهات المحادثات الجماعية بشكل مستقل", vm.state.groupNotifications, vm::setGroupNotifications) }
    item { ToggleSetting("إشعارات المكالمات", "رنين وارد عبر خدمة المكالمات الأمامية", vm.state.callNotifications, vm::setCallNotifications) }
    item { ToggleSetting("إظهار محتوى الرسالة", "غير موصى به على شاشة القفل", vm.state.notificationPreview, vm::setNotificationPreview) }
    item { InfoCard("قنوات Android", "الصوت والاهتزاز من إعدادات النظام: رسائل يونس، مكالمات يونس، DINSTAR.", Icons.Default.Notifications) }
}

@Composable private fun DataSettings(vm: SettingsViewModel) = SettingsList {
    item { InfoCard("ذاكرة التخزين المؤقت", formatBytes(vm.cacheBytes), Icons.Default.Storage) }
    item { Button(vm::clearCache, Modifier.fillMaxWidth()) { Text("مسح cache والوسائط المفكوكة المؤقتة") } }
    item { InfoCard("الحد الحالي للمرفق", "99 MiB قبل التشفير", Icons.Default.DataUsage) }
    item { LockedSetting("النسخ الاحتياطي السحابي", "معطل لحماية مفاتيح الهوية والمحادثات") }
}

@Composable private fun CallSettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("توفير بيانات المكالمات", "يخفض bitrate ويُفضّل الطبقات الأخف على الشبكات الضعيفة", vm.state.dataSaverCalls, vm::setDataSaverCalls) }
    item { InfoCard("مكالمات يونس", "WebRTC / TURN / mediasoup — لا تستخدم SIM", Icons.Default.Call) }
    item { InfoCard("الهاتف اليمني", "DINSTAR منفصل ويستهلك رصيد الشريحة", Icons.Default.Call) }
}

@Composable private fun DevicesSettings(vm: DeviceSettingsViewModel) = SettingsList {
    item { InfoCard("شهادة الجهاز", "الدخول والمراسلة يتطلبان جهازًا معتمدًا وشهادة غير منتهية.", Icons.Default.Devices) }
    if (vm.loading) item { Text("جارٍ تحميل الأجهزة المعتمدة…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    vm.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    items(vm.devices, key = { it.id }) { device ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Devices, null, tint = if (vm.isCurrent(device)) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant); Text(device.deviceName, Modifier.weight(1f).padding(horizontal = 9.dp), fontWeight = FontWeight.SemiBold); Text(if (vm.isCurrent(device)) "هذا الجهاز" else device.status, style = MaterialTheme.typography.labelSmall) }
                Text("${device.platform} · ${device.identityFingerprint.chunked(8).joinToString(" ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("انتهاء الشهادة: ${device.certificateExpiresAt ?: "غير متاحة"}", style = MaterialTheme.typography.labelSmall)
                if (!vm.isCurrent(device) && device.status == "APPROVED") OutlinedButton({ vm.revoke(device) }, Modifier.fillMaxWidth()) { Text("إلغاء اعتماد هذا الجهاز") }
            }
        }
    }
    item { LockedSetting("تنبيه تغير المفتاح", "يجب إعادة مقارنة Safety Number عند تغير بصمة الجهاز") }
}

@Composable private fun ServerSettings(onAdvanced: () -> Unit) = SettingsList {
    item { InfoCard("نقطة YOUNES الحالية", ServerEndpoint.url(), Icons.Default.Wifi) }
    item {
        Button(onAdvanced, Modifier.fillMaxWidth()) { Text("إعدادات الخادم المتقدمة (اكتشاف وإدخال يدوي)") }
    }
    item { LockedSetting("اكتشاف LAN", "يعمل في Debug ويتحقق من /health وبصمة سلطة الهوية قبل حفظ العنوان") }
    item { LockedSetting("الوصول البعيد", "استخدم WireGuard أو TLS موثقًا؛ لا تفتح HTTP المحلي مباشرة للإنترنت") }
    item { LockedSetting("أسرار الخادم", "لا تُعرض كلمات المرور أو JWT أو مفاتيح السلطة داخل التطبيق") }
}

@Composable private fun AboutSettings() = SettingsList {
    item { InfoCard("YOUNES · يونس", "1.0.0-alpha · Local-first sovereign platform", Icons.Default.Info) }
    item { InfoCard("التشفير", "libsignal PQXDH + Double Ratchet + Kyber prekeys", Icons.Default.Security) }
    item { InfoCard("الشفافية", "لا نعرض ميزة غير مكتملة كمكتملة، ولا بيانات أجهزة وهمية.", Icons.Default.Info) }
}

@Composable private fun FolderSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val org = remember { com.red.sovereign.core.ChatOrganizationStore(context) }
    var name by remember { mutableStateOf("") }
    val revision = org.revision
    val folders = remember(revision) { org.folders() }
    SettingsList {
        item { Text("المجلدات تُخزَّن على الجهاز فقط — لا تُرفع للخادم ولا تكسر E2EE.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        item {
            OutlinedTextField(name, { name = it.take(32) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم مجلد جديد") })
            Button({ if (name.isNotBlank()) { org.createFolder(name); name = "" } }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && folders.size < 12) { Text("إنشاء مجلد") }
        }
        items(folders, key = { it.id }) { folder ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = YounesEmerald)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(folder.name, fontWeight = FontWeight.SemiBold)
                        Text("${folder.chatIds.size} محادثة", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton({ org.deleteFolder(folder.id) }) { Text("حذف") }
                }
            }
        }
    }
}

@Composable private fun StarredSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val org = remember { com.red.sovereign.core.ChatOrganizationStore(context) }
    val revision = org.revision
    val ids = remember(revision) { org.starredIds() }
    SettingsList {
        item { InfoCard("الرسائل المميّزة", if (ids.isEmpty()) "نجّم رسالة من الضغط الطويل داخل المحادثة." else "${ids.size} رسالة محفوظة محلياً", Icons.Default.Star) }
        items(ids.toList()) { id ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(id.take(16), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton({ org.toggleStarred(id) }) { Text("إزالة") }
                }
            }
        }
    }
}

@Composable private fun BlockedSettings() {
    val directory: com.red.sovereign.contacts.DirectoryViewModel = viewModel()
    SettingsList {
        item { InfoCard("المحظورون", "الحظر يمنع الرسائل والمكالمات من هذا المعرّف على جهازك والخادم.", Icons.Default.Block) }
        if (directory.blocked.isEmpty()) item { Text("لا يوجد محظورون حالياً.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(directory.blocked) { redId ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(redId, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedButton({
                        val profile = directory.contacts.firstOrNull { it.redId == redId }
                            ?: com.red.sovereign.contacts.PublicRedProfile(redId, redId.takeLast(8), redId)
                        directory.unblock(profile)
                    }) { Text("فك الحظر") }
                }
            }
        }
    }
}

@Composable private fun VisibilityPicker(title: String, current: String, onChange: (String) -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EVERYONE" to "الجميع", "CONTACTS" to "الأصدقاء", "NOBODY" to "لا أحد").forEach { (id, label) ->
                AssistChip({ onChange(id) }, { Text(label) }, leadingIcon = { if (current == id) Text("●") })
            }
        }
    }
}

@Composable private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(
    modifier = Modifier.height(570.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content
)

@Composable private fun ToggleSetting(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, change)
    }
}

@Composable private fun LockedSetting(title: String, detail: String) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, tint = YounesEmerald); Column(Modifier.padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun InfoCard(title: String, detail: String, icon: ImageVector) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = YounesEmerald); Column(Modifier.padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun pageTitle(page: SettingsPage) = when (page) {
    SettingsPage.ACCOUNT -> "الحساب والهوية"; SettingsPage.PRIVACY -> "الخصوصية والأمان"; SettingsPage.APPEARANCE -> "المظهر والوصولية"
    SettingsPage.CHATS -> "الدردشات والوسائط"; SettingsPage.NOTIFICATIONS -> "الإشعارات"; SettingsPage.DATA -> "البيانات والتخزين"
    SettingsPage.CALLS -> "المكالمات"; SettingsPage.DEVICES -> "الأجهزة والشهادات"; SettingsPage.SERVER -> "الخادم والشبكة"
    SettingsPage.SERVER_ADVANCED -> "الخادم المتقدم"
    SettingsPage.FOLDERS -> "مجلدات الدردشة"; SettingsPage.STARRED -> "الرسائل المميّزة"; SettingsPage.BLOCKED -> "المحظورون"
    SettingsPage.ABOUT -> "حول يونس"; SettingsPage.ROOT -> "الإعدادات"
}
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024 -> "%.1f MiB".format(bytes / 1048576.0); bytes >= 1024 -> "%.1f KiB".format(bytes / 1024.0); else -> "$bytes B" }
private data class SettingDestination(val page: SettingsPage, val icon: ImageVector, val title: String, val detail: String, val color: Color)
