package com.red.sovereign.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.AuthState
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

private enum class SettingsPage { ROOT, ACCOUNT, PRIVACY, APPEARANCE, CHATS, NOTIFICATIONS, DATA, CALLS, DEVICES, SERVER, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YounesSettingsSheet(
    account: AuthState.Authenticated,
    viewModel: SettingsViewModel,
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
                SettingsPage.ACCOUNT -> AccountSettings(account)
                SettingsPage.PRIVACY -> PrivacySettings(viewModel)
                SettingsPage.APPEARANCE -> AppearanceSettings(viewModel)
                SettingsPage.CHATS -> ChatSettings(viewModel)
                SettingsPage.NOTIFICATIONS -> NotificationSettings(viewModel)
                SettingsPage.DATA -> DataSettings(viewModel)
                SettingsPage.CALLS -> CallSettings(viewModel)
                SettingsPage.DEVICES -> DevicesSettings(deviceSettings)
                SettingsPage.SERVER -> ServerSettings()
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

@Composable private fun AccountSettings(account: AuthState.Authenticated) = SettingsList {
    item { InfoCard("هوية يونس", "@${account.username}\n${account.redId}", Icons.Default.VpnKey) }
    item { InfoCard("حالة PSTN", if (account.pstnEnabled) "مصرح بالاتصال اليمني عبر DINSTAR" else "غير مفعل لهذا الحساب", Icons.Default.Call) }
    item { InfoCard("تغيير الاسم وكلمة المرور", "يتطلب endpoint موثق وإبطال الجلسات؛ لن يظهر كزر وهمي.", Icons.Default.Lock) }
}

@Composable private fun PrivacySettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("إيصالات القراءة", "إرسال READ بعد فتح الرسالة", vm.state.readReceipts, vm::setReadReceipts) }
    item { LockedSetting("مؤشر الكتابة", "سيُفعّل بعد ربط debounce ودورة حياة محرر الرسالة دون تسريب زائد للبيانات الوصفية") }
    item { LockedSetting("معاينات الروابط", "متوقفة حتى اكتمال proxy آمن وحماية SSRF وإخفاء عنوان IP") }
    item { LockedSetting("حماية لقطات الشاشة", "مفعلة إجباريًا للمحادثات والمفاتيح الحساسة") }
    item { LockedSetting("مفاتيح الهوية", "تبقى داخل Android Keystore ولا يمكن تصديرها") }
}

@Composable private fun AppearanceSettings(vm: SettingsViewModel) = SettingsList {
    item {
        Text("حجم الخط · ${(vm.state.fontScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
        Slider(value = vm.state.fontScale, onValueChange = vm::setFontScale, valueRange = .85f..1.30f, steps = 8)
    }
    item { ToggleSetting("تباين مرتفع", "حدود ونصوص أوضح", vm.state.highContrast, vm::setHighContrast) }
    item { ToggleSetting("واجهة مدمجة", "مسافات أقل للقوائم الطويلة", vm.state.compactMode, vm::setCompactMode) }
    item { LockedSetting("الحركة الهادئة", "الخلفيات المتحركة والمؤثرات المستمرة معطلة افتراضيًا لحماية التركيز والبطارية") }
    item { LockedSetting("RTL والعربية", "مفعلة تلقائيًا حسب لغة النظام") }
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
}

@Composable private fun NotificationSettings(vm: SettingsViewModel) = SettingsList {
    item { ToggleSetting("إشعارات الرسائل", "تنبيه عند وصول رسالة مشفرة", vm.state.messageNotifications, vm::setMessageNotifications) }
    item { LockedSetting("إشعارات المكالمات", "ستُفعّل مع خدمة المكالمات الواردة وFull-screen intent الموثق") }
    item { ToggleSetting("إظهار محتوى الرسالة", "غير موصى به على شاشة القفل", vm.state.notificationPreview, vm::setNotificationPreview) }
    item { InfoCard("قنوات Android", "يمكن ضبط الصوت والاهتزاز من إعدادات نظام Android لكل قناة.", Icons.Default.Notifications) }
}

@Composable private fun DataSettings(vm: SettingsViewModel) = SettingsList {
    item { InfoCard("ذاكرة التخزين المؤقت", formatBytes(vm.cacheBytes), Icons.Default.Storage) }
    item { Button(vm::clearCache, Modifier.fillMaxWidth()) { Text("مسح cache والوسائط المفكوكة المؤقتة") } }
    item { InfoCard("الحد الحالي للمرفق", "99 MiB قبل التشفير", Icons.Default.DataUsage) }
    item { LockedSetting("النسخ الاحتياطي السحابي", "معطل لحماية مفاتيح الهوية والمحادثات") }
}

@Composable private fun CallSettings(vm: SettingsViewModel) = SettingsList {
    item { LockedSetting("توفير بيانات المكالمات", "سيُربط بقيود bitrate وطبقات simulcast بعد اكتمال WebRTC Android") }
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

@Composable private fun ServerSettings() = SettingsList {
    item { InfoCard("نقطة YOUNES الحالية", ServerEndpoint.url(), Icons.Default.Wifi) }
    item { LockedSetting("اكتشاف LAN", "يعمل في Debug ويتحقق من /health وبصمة سلطة الهوية قبل حفظ العنوان") }
    item { LockedSetting("الوصول البعيد", "استخدم WireGuard أو TLS موثقًا؛ لا تفتح HTTP المحلي مباشرة للإنترنت") }
    item { LockedSetting("أسرار الخادم", "لا تُعرض كلمات المرور أو JWT أو مفاتيح السلطة داخل التطبيق") }
}

@Composable private fun AboutSettings() = SettingsList {
    item { InfoCard("YOUNES · يونس", "1.0.0-alpha · Local-first sovereign platform", Icons.Default.Info) }
    item { InfoCard("التشفير", "libsignal PQXDH + Double Ratchet + Kyber prekeys", Icons.Default.Security) }
    item { InfoCard("الشفافية", "لا نعرض ميزة غير مكتملة كمكتملة، ولا بيانات أجهزة وهمية.", Icons.Default.Info) }
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
    SettingsPage.CALLS -> "المكالمات"; SettingsPage.DEVICES -> "الأجهزة والشهادات"; SettingsPage.SERVER -> "الخادم والشبكة"; SettingsPage.ABOUT -> "حول يونس"; SettingsPage.ROOT -> "الإعدادات"
}
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024 -> "%.1f MiB".format(bytes / 1048576.0); bytes >= 1024 -> "%.1f KiB".format(bytes / 1024.0); else -> "$bytes B" }
private data class SettingDestination(val page: SettingsPage, val icon: ImageVector, val title: String, val detail: String, val color: Color)
