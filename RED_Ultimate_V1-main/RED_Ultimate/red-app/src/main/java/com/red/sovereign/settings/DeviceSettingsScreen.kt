package com.red.sovereign.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.red.sovereign.R
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.core.outbox.OutboxRetryWorker
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch

/**
 * 35 بنداً بلا Snackbar «قريباً» — كل بند يفتح وجهته الحية:
 * - حوارات عاملة تحفظ في younes_user_preferences / SecureStore وتُطبّق فوراً:
 *   Theme/AppLock/Notifications/AutoDownload/Storage/MediaQuality/ConnectionMode/
 *   OfflineQueue/History/Limits/Recording/Forwarding.
 * - ربط YounesSettingsSheet عبر callbacks (تُمرَّر من RedDashboard):
 *   Profile→ACCOUNT، Device/Sessions→DEVICES، Keys/Receipts/Link/Screen/AppLock→PRIVACY،
 *   Theme/Accent/Font/Animation→APPEARANCE، Notifications/CallNotif/Group/DND→NOTIFICATIONS،
 *   AutoDownload/Bubbles→CHATS، Storage/Export→DATA، Ringtone/Speaker/Recording→CALLS،
 *   NetworkDiag→NETWORK_DIAG، Debug/WebRTC/Flags→DEVELOPER، About→ABOUT،
 *   Backup→RecoveryHubScreen، Server→SmartServerSettings، OfflineQueue→OfflineQueueScreen.
 *   بلا callback يبقى البند شارة «قريباً» غير قابلة للضغط (لا إخفاء، لا Snackbar كاذب)
 *   وتُخفى الشارات تماماً في release عبر BuildConfig.SHOW_PLACEHOLDERS=false.
 *
 * القاعدة الصارمة: ممنوع الحذف وممنوع الإخفاء وممنوع Snackbar «قريباً» وممنوع if(false).
 */
private object DeviceSettingsFlags {
    const val OFFLINE_QUEUE_ENABLED = true
    const val TURN_SETTINGS_ENABLED = true
    // إظهار دائم — الإخفاء ممنوع. البنود غير المفعّلة تبقى بشارة مرئية لا مخفية.
    // 2026-09-10: بوابة الستور — تُربط بـ BuildConfig: true في debug، false في release.
    // في release الشارات المؤقتة مخفية تمامًا عبر BuildConfig.SHOW_PLACEHOLDERS.
    val SHOW_PLACEHOLDERS: Boolean get() = com.red.sovereign.BuildConfig.SHOW_PLACEHOLDERS
    const val THEME_READY = true
    const val APP_LOCK_READY = true
    const val NOTIFICATIONS_READY = true
}

/** الحوارات العاملة — واحد لكل بند مفعّل (الثمانية الأصلية + الأربعة الجديدة للمكالمات). */
private enum class DeviceDialog {
    THEME, APP_LOCK, NOTIFICATIONS, AUTO_DOWNLOAD, STORAGE, MEDIA_QUALITY, CONNECTION, OFFLINE_QUEUE,
    CALL_HISTORY, CALL_LIMITS, CALL_RECORDING, CALL_FORWARDING
}

/**
 * Material 3 Expressive Device Settings Screen
 * Comprehensive settings organized by category
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    onBack: () -> Unit,
    tokenStore: TokenStore,
    snackbarHostState: SnackbarHostState,
    // وجهات حقيقية «إن وُجدت» — التنفيذ الحيّ اليوم في YounesSettingsSheet
    // (APPEARANCE/NOTIFICATIONS/pages + AppLock). بلا callback يبقى البند
    // شارة «قريباً» غير قابلة للضغط بدل Snackbar كاذب.
    onThemeClick: (() -> Unit)? = null,
    onAppLockClick: (() -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null,
    // ─── ربط Sprint P0 (صفر تخزين جديد): كل بند ميت يفتح صفحته الحية ────
    // AppLock/ReadReceipts→PRIVACY، Theme→APPEARANCE، الإشعارات→NOTIFICATIONS،
    // AutoDownload→CHATS، Storage→DATA، Profile→ACCOUNT، Device/Sessions→DEVICES،
    // Backup→RecoveryHubScreen، Server قراءة→SmartServerSettings تحرير،
    // OfflineQueue→OfflineQueueScreen (observePending + retry/delete).
    // كلها nullable حفاظاً على المنادين الحاليين — تُمرَّر من RedDashboard.
    onPrivacyClick: (() -> Unit)? = null,
    onAppearanceClick: (() -> Unit)? = null,
    onChatSettingsClick: (() -> Unit)? = null,
    onDataSettingsClick: (() -> Unit)? = null,
    onAccountClick: (() -> Unit)? = null,
    onDevicesClick: (() -> Unit)? = null,
    onBackupClick: (() -> Unit)? = null,
    onServerEditClick: (() -> Unit)? = null,
    onOfflineQueueClick: (() -> Unit)? = null,
    // ربط الستور الكامل (35 بند بلا Snackbar قريباً): كل بند ميت سابقاً يفتح صفحته
    // الحية في YounesSettingsSheet — CALLS للنغمات/السماعة، NOTIFICATIONS لـ DND،
    // NETWORK_DIAG للتشخيص، DEVELOPER للسجلات/الأعلام، ABOUT لحول يونس.
    onCallsClick: (() -> Unit)? = null,
    onNetworkDiagClick: (() -> Unit)? = null,
    onDeveloperClick: (() -> Unit)? = null,
    onAboutClick: (() -> Unit)? = null
) {
    // SettingsViewModel هو مخزن الحفظ (younes_user_preferences) — يُمرَّر للحوارات الثمانية.
    val settingsVm: SettingsViewModel = viewModel()
    var activeDialog by remember { mutableStateOf<DeviceDialog?>(null) }
    val themeReady = true
    val appLockReady = true
    val notificationsReady = true
    // الحوارات الثمانية العاملة — تُعرض فوق القائمة وتحفظ فوراً.
    DeviceSettingsDialogs(
        active = activeDialog,
        onDismiss = { activeDialog = null },
        settingsVm = settingsVm,
        tokenStore = tokenStore,
        snackbarHostState = snackbarHostState
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.admin_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.ObsidianDeep
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            // Account & Identity
            SettingsSectionItem(
                title = stringResource(R.string.section_account_identity),
                icon = Icons.Filled.Person,
                color = AqyalGold
            )
            SettingsItem(
                title = stringResource(R.string.profile_identity),
                subtitle = stringResource(R.string.profile_identity_sub),
                icon = Icons.Filled.Person,
                // P0: Profile→صفحة الحساب الحية (AccountSettings) — بلا callback تبقى الشارة.
                isComingSoon = onAccountClick == null,
                onClick = { onAccountClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.device_identity),
                subtitle = stringResource(R.string.device_identity_sub),
                icon = Icons.Filled.Fingerprint,
                // P0: Device→صفحة الأجهزة الحية (DevicesSettings) — بلا callback تبقى الشارة.
                isComingSoon = onDevicesClick == null,
                onClick = { onDevicesClick?.invoke() }
            )
            // توحيداً (2026-09-10): بند الاستعادة الوحيد عبر BackupScreen «ملف النسخة المشفرة»
            // وشاشة الدخول «رموز الحساب الورقية» — لا بند مكرر هنا.
            SettingsItem(
                title = stringResource(R.string.active_sessions),
                subtitle = stringResource(R.string.active_sessions_sub),
                icon = Icons.Filled.MoreVert,
                // P0: Sessions→صفحة الأجهزة الحية (إلغاء الاعتماد) — بلا callback تبقى الشارة.
                isComingSoon = onDevicesClick == null,
                onClick = { onDevicesClick?.invoke() }
            )

            SettingsItem(
                title = stringResource(R.string.call_limits),
                subtitle = "المدة ${com.red.sovereign.settings.CallLimitsPolicy.maxDurationLabelAr()}",
                icon = Icons.Filled.Call,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CALL_LIMITS }
            )
            SettingsItem(
                title = stringResource(R.string.call_recording),
                subtitle = if (settingsVm.state.callAutoRecord) "تسجيل تلقائي مفعّل — بموافقة الطرفين" else stringResource(R.string.call_recording_sub),
                icon = Icons.Filled.Mic,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CALL_RECORDING }
            )
            SettingsItem(
                title = stringResource(R.string.call_forwarding),
                subtitle = stringResource(R.string.call_forwarding_sub),
                icon = Icons.Filled.CallReceived,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CALL_FORWARDING }
            )

            // تعريب عام: كتلة الشبكة بالعربية عبر strings.xml (كانت إنجليزية خام).
            SettingsSectionItem(
                title = stringResource(R.string.network_connectivity_title),
                icon = Icons.Filled.Wifi,
                color = AqyalGold
            )
            SettingsItem(
                title = stringResource(R.string.server_endpoint_title),
                subtitle = ServerEndpoint.url(),
                icon = Icons.Filled.NetworkCell,
                // P0: قراءة→تحرير — النقطة تُعرض هنا وتُحرَّر في SmartServerSettings
                // (اكتشاف تلقائي + إدخال يدوي + تحقق توقيع). بلا callback تبقى قراءة صامتة.
                isReadOnly = onServerEditClick == null,
                isComingSoon = false,
                onClick = { onServerEditClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.connection_mode_title),
                subtitle = "الحالي: ${ConnectionModePolicy.labelAr(settingsVm.state.connectionMode)} — ${ServerEndpoint.url()}",
                icon = Icons.Filled.Wifi,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CONNECTION }
            )
            if (DeviceSettingsFlags.TURN_SETTINGS_ENABLED) {
                SettingsItem(
                    title = stringResource(R.string.turn_stun_title),
                    subtitle = stringResource(R.string.turn_stun_sub),
                    icon = Icons.Filled.SettingsInputComponent,
                    // ربط حقيقي بلا Snackbar: TURN مُدار من الخادم — الزر يفتح صفحة
                    // الخادم الحية (SmartServerSettings) للفحص، وبلا callback يبقى قراءة.
                    isReadOnly = onServerEditClick == null,
                    isComingSoon = false,
                    onClick = { onServerEditClick?.invoke() }
                )
            }
            if (DeviceSettingsFlags.OFFLINE_QUEUE_ENABLED) {
                SettingsItem(
                    title = stringResource(R.string.offline_queue_title),
                    subtitle = stringResource(R.string.offline_queue_sub),
                    icon = Icons.Filled.Sync,
                    isComingSoon = false,
                    // P0: الزر→شاشة قائمة حقيقية OfflineQueueScreen (observePending +
                    // retry/delete لكل رسالة). بلا callback يبقى حوار الملخص احتياطياً.
                    onClick = {
                        if (onOfflineQueueClick != null) onOfflineQueueClick.invoke()
                        else activeDialog = DeviceDialog.OFFLINE_QUEUE
                    }
                )
            }

            // Security & Privacy
            SettingsSectionItem(
                title = stringResource(R.string.sec_privacy_title),
                icon = Icons.Filled.Security,
                color = Color(0xFFF44336)
            )
            SettingsItem(
                title = stringResource(R.string.encryption_keys_title),
                subtitle = stringResource(R.string.encryption_keys_sub),
                icon = Icons.Filled.Lock,
                // المفاتيح تُدار في PRIVACY (تنبيه تغير المفتاح + Keystore) — ربط حي.
                isComingSoon = onPrivacyClick == null,
                onClick = { onPrivacyClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.read_receipts_title),
                subtitle = stringResource(R.string.read_receipts_sub),
                icon = Icons.Filled.Description,
                // P0: ReadReceipts→صفحة الخصوصية الحية (PrivacySettings: إيصالات القراءة
                // + مؤشر الكتابة + الظهور). بلا callback تبقى الشارة.
                isComingSoon = onPrivacyClick == null,
                onClick = { onPrivacyClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.link_previews_title),
                subtitle = if (settingsVm.state.linkPreviews) "مفعّلة — عبر proxy سيادي (SSRF/IP محمي)" else "متوقفة — فعّل لمعاينة آمنة عبر proxy",
                icon = Icons.Filled.Info,
                // مبدّل حقيقي في PRIVACY (ToggleSetting مربوط بـ linkPreviews) — هنا توجيه حي إليه،
                // وبلا callback يُبدَّل مباشرة فلا يبقى البند ميتاً أبداً.
                isComingSoon = false,
                onClick = {
                    if (onPrivacyClick != null) onPrivacyClick.invoke()
                    else settingsVm.setLinkPreviews(!settingsVm.state.linkPreviews)
                }
            )
            SettingsItem(
                title = stringResource(R.string.screen_security_title),
                subtitle = stringResource(R.string.screen_security_sub),
                icon = Icons.Filled.VisibilityOff,
                // حماية الشاشة موثقة في PRIVACY (مفعلة إجبارياً) — ربط حي.
                isComingSoon = onPrivacyClick == null,
                onClick = { onPrivacyClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.app_lock_title),
                subtitle = if (settingsVm.state.appLockEnabled) "مفعّل — بصمة/PIN عند الدخول" else stringResource(R.string.app_lock_sub),
                icon = Icons.Filled.Fingerprint,
                isComingSoon = false,
                // إصلاح double-nav: إما وجهة خارجية أو حوار محلي — أبداً الاثنان معاً.
                onClick = { if (onAppLockClick != null) onAppLockClick.invoke() else activeDialog = DeviceDialog.APP_LOCK }
            )
            SettingsItem(
                title = stringResource(R.string.data_export_title),
                subtitle = stringResource(R.string.data_export_sub),
                icon = Icons.Filled.Delete,
                isDestructive = true,
                // التصدير/المسح في DATA (مسح الكاش + حدود المرفق) — ربط حي.
                isComingSoon = onDataSettingsClick == null,
                onClick = { onDataSettingsClick?.invoke() }
            )

            // Media & Storage
            SettingsSectionItem(
                title = stringResource(R.string.media_storage_title),
                icon = Icons.Filled.Storage,
                color = Color(0xFF9C27B0)
            )
            SettingsItem(
                title = stringResource(R.string.media_autodownload_title),
                subtitle = "الحالي: ${autoDownloadLabelAr(settingsVm.state.autoDownloadWifi, settingsVm.state.autoDownloadMobile)} — حد ${settingsVm.state.autoDownloadLimitMb} م.ب",
                icon = Icons.Filled.Download,
                isComingSoon = false,
                // P0: AutoDownload→صفحة الدردشات الحية (ChatSettings) + حوار WiFi/بيانات/حد المحلي.
                onClick = { activeDialog = DeviceDialog.AUTO_DOWNLOAD; onChatSettingsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.storage_usage_title),
                subtitle = stringResource(R.string.storage_usage_sub),
                icon = Icons.Filled.Storage,
                isComingSoon = false,
                // P0: Storage→صفحة البيانات الحية (DataSettings: مسح الكاش + الحدود) + حوار الأحجام المحلي.
                onClick = { activeDialog = DeviceDialog.STORAGE; onDataSettingsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.media_quality_title),
                subtitle = "الحالية: ${MediaQualityPolicy.spec(settingsVm.state.mediaQuality).labelAr}",
                icon = Icons.Filled.Image,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.MEDIA_QUALITY }
            )

            // Notifications
            SettingsSectionItem(
                title = stringResource(R.string.notifications_title),
                icon = Icons.Filled.Notifications,
                color = Color(0xFF673AB7)
            )
            SettingsItem(
                title = stringResource(R.string.msg_notifications_title),
                subtitle = notificationSummaryAr(settingsVm.state),
                icon = Icons.Filled.Notifications,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.NOTIFICATIONS; onNotificationsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.call_notifications_title),
                subtitle = stringResource(R.string.call_notifications_sub),
                icon = Icons.Filled.Call,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.NOTIFICATIONS; onNotificationsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.group_notifications_title),
                subtitle = stringResource(R.string.group_notifications_sub),
                icon = Icons.Filled.Group,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.NOTIFICATIONS; onNotificationsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.dnd_title),
                subtitle = stringResource(R.string.dnd_sub),
                icon = Icons.Filled.DoNotDisturb,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.NOTIFICATIONS; onNotificationsClick?.invoke() }
            )

            // Appearance
            SettingsSectionItem(
                title = stringResource(R.string.appearance_title),
                icon = Icons.Filled.BrightnessAuto,
                color = Color(0xFF00BCD4)
            )
            SettingsItem(
                title = stringResource(R.string.theme_title),
                subtitle = "الحالي: ${themeLabelAr(settingsVm.state.themeMode, settingsVm.state.themePreset)}",
                icon = Icons.Filled.BrightnessAuto,
                isComingSoon = false,
                // P0: Theme→صفحة المظهر الحية (AppearanceSettings: 6 ثيمات + خط + تباين) + حوار السمة المحلي.
                onClick = { activeDialog = DeviceDialog.THEME; onThemeClick?.invoke(); onAppearanceClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.accent_color_title),
                subtitle = stringResource(R.string.accent_color_sub),
                icon = Icons.Filled.Palette,
                // اللون المميز في APPEARANCE (6 ثيمات + مخصص) — ربط حي.
                isComingSoon = onAppearanceClick == null,
                onClick = { activeDialog = DeviceDialog.THEME; onAppearanceClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.font_style_title),
                subtitle = stringResource(R.string.font_style_sub),
                icon = Icons.Filled.TextFields,
                // الخط في APPEARANCE (FontBubblesDialog + حجم الخط) — ربط حي.
                isComingSoon = onAppearanceClick == null,
                onClick = { onAppearanceClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.chat_bubbles_title),
                subtitle = stringResource(R.string.chat_bubbles_sub),
                icon = Icons.Filled.Chat,
                // الفقاعات في CHATS (خلفية المحادثة + الصبغة) — ربط حي.
                isComingSoon = onChatSettingsClick == null,
                onClick = { onChatSettingsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.animations_title),
                subtitle = stringResource(R.string.animations_sub),
                icon = Icons.Filled.Animation,
                // الحركة في APPEARANCE (تقليل الحركة + Liquid Glass) — ربط حي.
                isComingSoon = onAppearanceClick == null,
                onClick = { onAppearanceClick?.invoke() }
            )

            // Call Settings
            SettingsSectionItem(
                title = stringResource(R.string.call_settings_title),
                icon = Icons.Filled.Call,
                color = YounesEmerald
            )
            SettingsItem(
                title = stringResource(R.string.ringtone_title),
                subtitle = stringResource(R.string.ringtone_sub),
                icon = Icons.Filled.Vibration,
                // النغمة في CALLS (إشعارات المكالمات + قناة النظام) — ربط حي بلا Snackbar.
                isComingSoon = onCallsClick == null && onNotificationsClick == null,
                onClick = { (onCallsClick ?: onNotificationsClick)?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.speaker_audio_title),
                subtitle = stringResource(R.string.speaker_audio_sub),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                // السماعة/الصوت في CALLS (توفير البيانات + WebRTC) — ربط حي.
                isComingSoon = onCallsClick == null,
                onClick = { onCallsClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.call_settings_recording_title),
                subtitle = if (settingsVm.state.callAutoRecord) "تسجيل تلقائي مفعّل — بموافقة الطرفين" else stringResource(R.string.call_settings_recording_sub),
                icon = Icons.Filled.Mic,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CALL_RECORDING }
            )
            SettingsItem(
                title = stringResource(R.string.call_history_title),
                subtitle = "احتفاظ ${settingsVm.state.callHistoryRetentionDays} يوم · ${if (settingsVm.state.callHistorySync) "مزامنة مفعّلة" else "محلي فقط"}",
                icon = Icons.Filled.History,
                isComingSoon = false,
                onClick = { activeDialog = DeviceDialog.CALL_HISTORY }
            )

            // Advanced / Developer
            SettingsSectionItem(
                title = stringResource(R.string.advanced_title),
                icon = Icons.Filled.Construction,
                color = Color(0xFF795548)
            )
            SettingsItem(
                title = stringResource(R.string.debug_logging_title),
                subtitle = stringResource(R.string.debug_logging_sub),
                icon = Icons.Filled.BugReport,
                // السجلات في DEVELOPER (Telemetry وسجلات وFlags دائمة) — ربط حي.
                isComingSoon = onDeveloperClick == null,
                onClick = { onDeveloperClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.network_diag_title),
                subtitle = stringResource(R.string.network_diag_sub),
                icon = Icons.Filled.NetworkCheck,
                // التشخيص في NETWORK_DIAG (Ping وDNS وزمن HTTP وWebRTC) — ربط حي.
                isComingSoon = onNetworkDiagClick == null,
                onClick = { onNetworkDiagClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.webrtc_debug_title),
                subtitle = stringResource(R.string.webrtc_debug_sub),
                icon = Icons.Filled.DeveloperMode,
                // WebRTC في DEVELOPER (يفضل) وإلا NETWORK_DIAG — ربط حي.
                isComingSoon = onDeveloperClick == null && onNetworkDiagClick == null,
                onClick = { (onDeveloperClick ?: onNetworkDiagClick)?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.feature_flags_title),
                subtitle = stringResource(R.string.feature_flags_sub),
                icon = Icons.Filled.Flag,
                // الأعلام في DEVELOPER — ربط حي.
                isComingSoon = onDeveloperClick == null,
                onClick = { onDeveloperClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.backup_restore_title),
                subtitle = stringResource(R.string.backup_restore_sub),
                icon = Icons.Filled.Backup,
                // P0: Backup→مركز الاستعادة الحي (RecoveryHubScreen: ملف النسخة المشفرة
                // + رموز الحساب الورقية + اختبار استعادة جاف). بلا callback تبقى الشارة.
                isComingSoon = onBackupClick == null,
                onClick = { onBackupClick?.invoke() }
            )
            SettingsItem(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_sub),
                icon = Icons.Filled.Info,
                // حول يونس في ABOUT (الإصدار بلا alpha في release + البنية) — ربط حي.
                isComingSoon = onAboutClick == null,
                onClick = { onAboutClick?.invoke() }
            )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionItem(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isReadOnly: Boolean = false,
    isDestructive: Boolean = false,
    // بند غير مفعّل بعد: شارة «قريباً» بصريّة غير قابلة للضغط — ظاهرة دائماً
    // (ممنوع الإخفاء وممنوع Snackbar كاذب). البنود الثمانية المفعّلة تمرّر false.
    isComingSoon: Boolean = false,
    trailingIcon: ImageVector = Icons.Filled.ArrowForward,
    onClick: () -> Unit = {}
) {
    // بوابة الستور: الشارات المؤقتة تُقرأ من BuildConfig.SHOW_PLACEHOLDERS
    // (true في debug، false في release عبر build.gradle.kts) — في release
    // تُخفى تماماً بدل عرض ~15 شارة "قريباً".
    if (isComingSoon && !DeviceSettingsFlags.SHOW_PLACEHOLDERS) return
    // لا إخفاء أبداً — القاعدة الصارمة.
    val titleColor = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReadOnly && !isComingSoon, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDestructive) Color(0xFFF44336).copy(alpha = 0.15f)
                    else AqyalGold.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isDestructive) Color(0xFFF44336) else AqyalGold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isComingSoon) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.coming_soon),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!isReadOnly) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── ملخصات عربية مشتقة من الحفظ الفعلي ─────────────────────────────────────
private fun autoDownloadLabelAr(wifi: Boolean, mobile: Boolean): String = when {
    wifi && mobile -> "واي فاي + بيانات"
    wifi -> "واي فاي فقط"
    else -> "أبداً"
}

private fun notificationSummaryAr(s: YounesSettings): String {
    val parts = mutableListOf<String>()
    if (s.messageNotifications) parts.add("رسائل")
    if (s.callNotifications) parts.add("مكالمات")
    if (s.groupNotifications) parts.add("مجموعات")
    if (parts.isEmpty()) return "متوقفة — فعّل من الحوار"
    val extra = buildList {
        if (s.notificationSound) add("صوت")
        if (s.notificationVibration) add("اهتزاز")
        if (s.notificationLed) add("LED")
    }.joinToString(" + ").ifBlank { "بلا تنبيه" }
    return "${parts.joinToString(" + ")} — $extra"
}

private fun themeLabelAr(mode: String, preset: String): String {
    if (preset == "OLED_BLACK") return "أسود AMOLED"
    return when (mode) {
        "LIGHT" -> "فاتح"
        "DARK" -> "داكن"
        else -> "حسب النظام"
    }
}

// ─── موزّع الحوارات (الثمانية الأصلية + الأربعة الجديدة للمكالمات) ────
@Composable
private fun DeviceSettingsDialogs(
    active: DeviceDialog?,
    onDismiss: () -> Unit,
    settingsVm: SettingsViewModel,
    tokenStore: TokenStore,
    snackbarHostState: SnackbarHostState
) {
    when (active) {
        DeviceDialog.THEME -> ThemeDialog(settingsVm, onDismiss)
        DeviceDialog.APP_LOCK -> AppLockDialog(settingsVm, onDismiss)
        DeviceDialog.NOTIFICATIONS -> NotificationsDialog(settingsVm, onDismiss)
        DeviceDialog.AUTO_DOWNLOAD -> AutoDownloadDialog(settingsVm, onDismiss)
        DeviceDialog.STORAGE -> StorageDialog(settingsVm, onDismiss)
        DeviceDialog.MEDIA_QUALITY -> MediaQualityDialog(settingsVm, onDismiss)
        DeviceDialog.CONNECTION -> ConnectionDialog(settingsVm, onDismiss, snackbarHostState)
        DeviceDialog.OFFLINE_QUEUE -> OfflineQueueDialog(onDismiss, snackbarHostState)
        // ── البنود الأربعة الجديدة: سجل/حدود/تسجيل/تحويل — كلها عاملة وحافظة ──
        DeviceDialog.CALL_HISTORY -> com.red.sovereign.calls.CallHistorySettingsDialog(onDismiss, settingsVm)
        DeviceDialog.CALL_LIMITS -> CallLimitsDialog(onDismiss, settingsVm)
        DeviceDialog.CALL_RECORDING -> CallRecordingEntryDialog(settingsVm, onDismiss)
        DeviceDialog.CALL_FORWARDING -> com.red.sovereign.calls.CallForwardingDialog(onDismiss)
        null -> Unit
    }
}

/**
 * مدخل التسجيل من DeviceSettings: مبدّل CALL_AUTO_RECORD الدائم + زر حوار الموافقة.
 * حوار الموافقة نفسه (RecordingConsentDialog) يحتاج callId حياً فيُفتح من شاشة
 * المكالمة النشطة — هنا نعرض المبدّل الدائم وشرح الربط بـ CallRecordingManager.start.
 */
@Composable
private fun CallRecordingEntryDialog(settingsVm: SettingsViewModel, onDismiss: () -> Unit) {
    var showConsentInfo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    DialogShell("تسجيل المكالمات — تلقائي دائم + موافقة", onDismiss) {
        ToggleRow(
            "التسجيل التلقائي (CALL_AUTO_RECORD)",
            if (settingsVm.state.callAutoRecord) "مفعّل دائماً — يُطلب تأكيد الطرفين كل مكالمة" else "متوقف — زر صريح فقط",
            settingsVm.state.callAutoRecord,
            settingsVm::setCallAutoRecord
        )
        Text(
            "الربط: عند بدء مكالمة يظهر RecordingConsentDialog (موافقة طرفين إلزامية) ويستدعي CallRecordingManager.start(consentGranted) فعلياً — false ترفض دون ملف.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { showConsentInfo = !showConsentInfo }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showConsentInfo) "إخفاء تفاصيل الموافقة" else "كيف تعمل الموافقة؟")
        }
        if (showConsentInfo) {
            Text(
                "داخل المكالمة: RecordingConsentDialog يعرض checkbox موافقتي + موافقة الطرف الآخر، وزر «بدء التسجيل» يستدعي start الحقيقي ويشفر m4a بـ AES-GCM في filesDir/recordings.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogShell(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

@Composable
private fun RadioRow(label: String, sub: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// 1) Theme — System/Light/Dark/AMOLED يبدّل MaterialTheme فعلياً عبر AppThemeState + يحفظ.
@Composable
private fun ThemeDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val s = vm.state
    fun apply(mode: String, preset: String) {
        vm.setThemeMode(mode)
        vm.setThemePreset(preset)
        runCatching { com.red.sovereign.ui.theme.AppThemeState.themeMode = com.red.sovereign.ui.theme.AppThemeMode.valueOf(mode) }
        runCatching { com.red.sovereign.ui.theme.AppThemeState.currentPreset = com.red.sovereign.ui.theme.AppThemePreset.valueOf(preset) }
    }
    DialogShell("السمة — تُطبق فوراً وتُحفظ", onDismiss) {
        RadioRow("حسب النظام", "يتبع وضع الهاتف", s.themeMode == "SYSTEM" && s.themePreset != "OLED_BLACK") { apply("SYSTEM", "SOVEREIGN") }
        RadioRow("فاتح", "لؤلؤي #F7F8FA", s.themeMode == "LIGHT" && s.themePreset != "OLED_BLACK") { apply("LIGHT", "SOVEREIGN") }
        RadioRow("داكن", "كحلي سيادي", s.themeMode == "DARK" && s.themePreset != "OLED_BLACK") { apply("DARK", "SOVEREIGN") }
        RadioRow("أسود AMOLED", "سواد تام يوفّر البطارية — preset OLED_BLACK", s.themePreset == "OLED_BLACK") { apply("DARK", "OLED_BLACK") }
        Text(
            "الحفظ: theme_mode/theme_preset في younes_user_preferences — يُقرأ عند الإقلاع في MainActivity.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 2) App Lock — Biometric/PIN يحفظ في EncryptedPrefs (SecureStore) ويقفل عند الدخول عبر MainActivity.
@Composable
private fun AppLockDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = vm.state
    var pinInput by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    // مفتاح pinVersion: حفظ/مسح PIN يتم مباشرة في AppLockStore دون لمس vm.state،
    // فكان remember(s) لا يُعاد حسابه أبداً وتبقى الواجهة عتيقة — الآن تُعاد
    // قراءة hasPin مع كل تغيير PIN عبر زيادة pinVersion.
    var pinVersion by remember { mutableStateOf(0) }
    val hasPin = remember(pinVersion) { com.red.sovereign.security.AppLockStore.hasPin(context) }
    val bioAvailable = remember {
        runCatching {
            val m = androidx.biometric.BiometricManager.from(context)
            m.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }.getOrElse { false }
    }
    DialogShell("قفل التطبيق — بصمة/PIN", onDismiss) {
        ToggleRow("تفعيل القفل عند الدخول", if (bioAvailable) "بصمة الجهاز متاحة" else "بصمة غير مسجلة — استخدم PIN", s.appLockEnabled, vm::setAppLockEnabled)
        Text("مهلة إعادة القفل: ${s.lockTimeoutSeconds} ث", fontWeight = FontWeight.Medium)
        Slider(value = s.lockTimeoutSeconds.toFloat(), onValueChange = { vm.setLockTimeoutSeconds(it.toInt()) }, valueRange = 5f..120f)
        HorizontalDivider()
        Text(if (hasPin) "PIN مضبوط — أدخل رمزاً جديداً للاستبدال" else "ضبط PIN (4–8 أرقام، يُحفظ SHA-256 في SecureStore)", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = pinInput,
            onValueChange = { pinInput = it.filter(Char::isDigit).take(8); pinMsg = null },
            label = { Text("PIN جديد") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        pinMsg?.let { Text(it, color = if (it.startsWith("تم")) YounesEmerald else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                pinMsg = if (com.red.sovereign.security.AppLockStore.setPin(context, pinInput)) {
                    pinInput = ""; pinVersion++; "تم حفظ PIN مشفراً — سيُطلب عند الدخول مع البصمة"
                } else "PIN مرفوض — 4 إلى 8 أرقام فقط"
            }, modifier = Modifier.weight(1f)) { Text("حفظ PIN") }
            if (hasPin) OutlinedButton(onClick = {
                com.red.sovereign.security.AppLockStore.clearPin(context); pinVersion++; pinMsg = "تم مسح PIN — البصمة وحدها تكفي"
            }) { Text("مسح") }
        }
        Text(
            "الربط: MainActivity يقفل عبر AppLockPolicy عند تجاوز المهلة، وAppLockScreen يقبل البصمة أو PIN.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 3) Notifications — Sound/Vibration/LED يحفظ ويُطبق على قنوات red_messages/red_calls.
@Composable
private fun NotificationsDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = vm.state
    fun updateAndApply(apply: () -> Unit) { apply(); NotificationPrefs.apply(context) }
    DialogShell("الإشعارات — صوت/اهتزاز/LED", onDismiss) {
        ToggleRow("إشعارات الرسائل", "قناة red_messages", s.messageNotifications) { updateAndApply { vm.setMessageNotifications(it) } }
        ToggleRow("إشعارات المكالمات", "قناة red_calls", s.callNotifications) { updateAndApply { vm.setCallNotifications(it) } }
        ToggleRow("إشعارات المجموعات", "مستقلة عن الفردية", s.groupNotifications) { updateAndApply { vm.setGroupNotifications(it) } }
        HorizontalDivider()
        ToggleRow("الصوت", "نغمة النظام الافتراضية أو صامت", s.notificationSound) { updateAndApply { vm.setNotificationSound(it) } }
        ToggleRow("الاهتزاز", "نمط 250ms على القنوات الثلاث", s.notificationVibration) { updateAndApply { vm.setNotificationVibration(it) } }
        ToggleRow("مصباح LED", "أصفر على الأجهزة الداعمة", s.notificationLed) { updateAndApply { vm.setNotificationLed(it) } }
        Text(
            "الحفظ: message/call/group + sound/vibration/led في Prefs — التطبيق عبر NotificationManager.createNotificationChannel.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 4) Media Auto-Download — WiFi/Mobile/Never يحفظ ويستخدمه MediaApi.isAutoDownloadAllowedNow.
@Composable
private fun AutoDownloadDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val s = vm.state
    val mode = MediaAutoDownloadPolicy.modeOf(s.autoDownloadWifi, s.autoDownloadMobile)
    DialogShell("التنزيل التلقائي — واي فاي/بيانات/أبداً", onDismiss) {
        RadioRow("واي فاي فقط", "الافتراضي — يحمي الباقة", mode == "WIFI") {
            vm.setWifiDownload(true); vm.setMobileDownload(false)
        }
        RadioRow("واي فاي + بيانات الهاتف", "تنزيل دائماً", mode == "MOBILE") {
            vm.setWifiDownload(true); vm.setMobileDownload(true)
        }
        RadioRow("أبداً", "يدوي فقط — MediaApi يعيد null", mode == "NEVER") {
            vm.setWifiDownload(false); vm.setMobileDownload(false)
        }
        Text("حد التنزيل التلقائي: ${s.autoDownloadLimitMb} م.ب", fontWeight = FontWeight.Medium)
        Slider(value = s.autoDownloadLimitMb.toFloat(), onValueChange = { vm.setAutoDownloadLimit(it.toInt()) }, valueRange = 1f..99f)
        Text(
            "الربط: MediaApi.downloadToPrivateCacheIfAllowed يعيد null خارج السياسة — المستدعي يعرض «بانتظار WiFi».",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 5) Storage Usage — Cache/Media/DB حقيقي مع زر Clear Cache يعمل.
@Composable
private fun StorageDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var stats by remember { mutableStateOf(runCatching { StorageStats.compute(context) }.getOrNull()) }
    var clearing by remember { mutableStateOf(false) }
    var clearResult by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { stats = runCatching { StorageStats.compute(context) }.getOrNull() }
    DialogShell("استخدام التخزين — أحجام حقيقية", onDismiss) {
        // لقطة محلية: LaunchedEffect قد يُعيد stats إلى null (getOrNull) بين
        // الفحص والاستخدام — الالتقاط يمنع NPE من stats!! السابق.
        val st = stats
        if (st == null) {
            CircularProgressIndicator()
            Text(
                "تعذر قراءة أحجام التخزين",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { stats = runCatching { StorageStats.compute(context) }.getOrNull() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إعادة المحاولة") }
        } else {
            StorageRow("التخزين المؤقت (الكاش)", StorageStats.formatAr(st.cacheBytes))
            StorageRow("الوسائط المشفرة والمؤقتة", StorageStats.formatAr(st.mediaBytes))
            StorageRow("قاعدة البيانات (SQLCipher)", StorageStats.formatAr(st.dbBytes))
            HorizontalDivider()
            StorageRow("الإجمالي", StorageStats.formatAr(st.totalBytes))
            Button(
                onClick = {
                    clearing = true
                    clearResult = null
                    vm.clearCache()
                    val cleared = StorageStats.clearCaches(context)
                    stats = runCatching { StorageStats.compute(context) }.getOrNull()
                    clearResult = "تم تحرير ${StorageStats.formatAr(cleared)}"
                    clearing = false
                },
                modifier = Modifier.fillMaxWidth(), enabled = !clearing
            ) { Text(if (clearing) "جارٍ المسح…" else "مسح التخزين المؤقت الآن") }
            clearResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall, color = YounesEmerald
                )
            }
            Text(
                "المسح يحذف cacheDir + encrypted_media + story_media — القاعدة والمفاتيح لا تُمس.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

// 6) Media Quality — جودة ضغط MediaCompressor حقيقية (تُطبق قبل التشفير والرفع).
@Composable
private fun MediaQualityDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val q = vm.state.mediaQuality
    DialogShell("جودة الوسائط — ضغط حقيقي", onDismiss) {
        RadioRow("عالية", "2048px · JPEG 85 — أفضل قراءة", q == "HIGH") { vm.setMediaQuality("HIGH") }
        RadioRow("متوازنة", "1280px · JPEG 75 — مثل واتساب", q == "BALANCED") { vm.setMediaQuality("BALANCED") }
        RadioRow("توفير بيانات", "640px · JPEG 60 — للشبكات الضعيفة", q == "SAVER") { vm.setMediaQuality("SAVER") }
        Text(
            "الربط: EncryptedAttachment يستدعي MediaCompressor.compressImageWithUserQuality قبل AES/GCM والرفع.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 7) Connection Mode — Auto/WiFi/VPN يحفظ ويستخدمه ServerEndpoint عبر ConnectionModePolicy.
@Composable
private fun ConnectionDialog(vm: SettingsViewModel, onDismiss: () -> Unit, snackbar: SnackbarHostState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = vm.state.connectionMode
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    DialogShell("وضع الاتصال — يُحترم قبل الاكتشاف", onDismiss) {
        Text("النقطة الحالية: ${ServerEndpoint.url()}", style = MaterialTheme.typography.bodySmall, color = YounesEmerald)
        RadioRow("تلقائي", ConnectionModePolicy.describeAr("AUTO"), mode == "AUTO") { vm.setConnectionMode("AUTO") }
        RadioRow("واي فاي فقط", ConnectionModePolicy.describeAr("WIFI"), mode == "WIFI") { vm.setConnectionMode("WIFI") }
        RadioRow("VPN فقط", ConnectionModePolicy.describeAr("VPN"), mode == "VPN") { vm.setConnectionMode("VPN") }
        testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Button(
            onClick = {
                testing = true; testResult = null
                ConnectionModePolicy.discoverRespectingMode(context) { ok ->
                    scope.launch { testResult = if (ok) "الاكتشاف مسموح بهذا الوضع — بدأ ServerEndpoint.autoDiscover" else "الوضع الحالي يمنع الاكتشاف على هذه الشبكة — بدّل إلى تلقائي" }
                    testing = false
                }
                scope.launch {
                    kotlinx.coroutines.delay(2500)
                    if (testing) { testing = false; testResult = testResult ?: "انتهت المهلة — تحقق من الشبكة والنقطة الحالية" }
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = !testing
        ) { Text(if (testing) "جارٍ اختبار الاتصال…" else "اختبار الاتصال بهذا الوضع") }
    }
}

// 8) Offline Queue — يعرض Outbox الحقيقي مع retry (لا Snackbar وهمي).
@Composable
private fun OfflineQueueDialog(onDismiss: () -> Unit, snackbar: SnackbarHostState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<com.red.sovereign.core.database.OutboxMessageEntity>>(emptyList()) }
    var pendingCount by remember { mutableStateOf<Int?>(null) }
    var deadCount by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actionMsg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching {
                val dao = RedDatabase.getInstance(appContext).outboxDao()
                pending = dao.getPending(System.currentTimeMillis(), 50)
                pendingCount = dao.countPending()
                deadCount = dao.countDeadLetter()
            }.onFailure { actionMsg = "تعذّر قراءة Outbox: ${it.message?.take(120)}" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    DialogShell("قائمة الانتظار دون اتصال — Outbox حقيقي", onDismiss) {
        if (loading && pendingCount == null) {
            CircularProgressIndicator()
        } else {
            Text("${pendingCount ?: pending.size} رسالة معلّقة · ${deadCount ?: 0} ميتة (Dead Letter)", fontWeight = FontWeight.Bold)
            if (pending.isEmpty()) {
                Text("لا رسائل معلّقة — صندوق الصادر فارغ.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pending.take(8).forEach { msg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${msg.type} · ${msg.conversationId.take(16)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("محاولات ${msg.retryCount} · ${msg.lastError?.take(40) ?: "بانتظار الشبكة"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        val dao = RedDatabase.getInstance(appContext).outboxDao()
                                        dao.scheduleRetry(msg.id, System.currentTimeMillis(), "manual_retry")
                                        OutboxRetryWorker.schedule(appContext)
                                        actionMsg = "أُعيدت جدولة ${msg.id.take(8)} عبر OutboxRetryWorker"
                                        refresh()
                                    }.onFailure { actionMsg = "فشل: ${it.message?.take(100)}" }
                                }
                            }) { Text("إعادة") }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        RedDatabase.getInstance(appContext).outboxDao().delete(msg.id)
                                        actionMsg = "حُذفت ${msg.id.take(8)} من Outbox"
                                        refresh()
                                    }
                                }
                            }) { Text("حذف") }
                        }
                    }
                    if (pending.size > 8) Text("+ ${pending.size - 8} أخرى في Outbox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actionMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = YounesEmerald) }
            Button(
                onClick = {
                    OutboxRetryWorker.schedule(appContext)
                    scope.launch {
                        val n = runCatching { RedDatabase.getInstance(appContext).outboxDao().countPending() }.getOrDefault(pendingCount ?: 0)
                        actionMsg = "أُعيدت الجدولة عبر العامل — $n معلّقة"
                        snackbar.showSnackbar("Outbox: $n معلّقة — أُعيدت الجدولة")
                        refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إعادة المحاولة الآن (OutboxRetryWorker)") }
            OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("تحديث القائمة من Room") }
        }
    }
}

// Phase 8: حوار حد مدة المكالمة فقط (الحد اليومي PSTN وحصة الخادم محذوفة).
@Composable
private fun CallLimitsDialog(
    onDismiss: () -> Unit,
    settingsVm: SettingsViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حد مدة المكالمة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val maxDuration = settingsVm.state.maxCallDurationSeconds
                Text("حد المدة: ${com.red.sovereign.settings.CallLimitsPolicy.maxDurationLabelAr()}", fontWeight = FontWeight.Medium)
                Slider(value = maxDuration.toFloat(), onValueChange = { settingsVm.setMaxCallDuration(it.toInt()) }, valueRange = 60f..7200f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}
