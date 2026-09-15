package com.red.sovereign.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.red.sovereign.ui.screens.ChatScrollPolicy.PRESENCE_DEBOUNCE_MS
import com.red.sovereign.ui.screens.scrollOnce
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.R
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.CallHistoryItem
import com.red.sovereign.calls.CallHistoryViewModel
import com.red.sovereign.calls.CallFilterType
import com.red.sovereign.features.calls.CallStatsScreen
import com.red.sovereign.calls.CallRuntime
import com.red.sovereign.calls.CallUiState
import com.red.sovereign.calls.UnifiedCallOverlays
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.ConferenceUiState
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.calls.CreateConferenceScreen
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.calls.GroupCallRuntime
import com.red.sovereign.calls.GroupCallService
import com.red.sovereign.calls.GroupCallUiState
import com.red.sovereign.calls.GroupCallMemberStatus
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.PinsApi
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.ReactionEventBus
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.UuidV7
import com.red.sovereign.core.ConversationSummary
import com.red.sovereign.core.database.MessageReactionEntity
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.core.outbox.OutboxRetryWorker
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.crypto.DecryptedMessageBus
import com.red.sovereign.crypto.SafetyQrScanner
import com.red.sovereign.crypto.SafetyState
import com.red.sovereign.crypto.SafetyViewModel
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupMember
import com.red.sovereign.groups.GroupState
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.media.AttachmentManifest
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.VoiceManifest
import com.red.sovereign.media.VoiceMessageState
import com.red.sovereign.media.VoiceMessageViewModel
import com.red.sovereign.media.voice.VoiceBubble
import com.red.sovereign.media.voice.VoiceColors
import com.red.sovereign.media.voice.VoicePreviewActions
import com.red.sovereign.media.voice.VoiceRecorderPanel
import com.red.sovereign.media.voice.VoiceRecordButton
import com.red.sovereign.media.voice.VoiceTimerDisplay
import com.red.sovereign.media.voice.VoiceWaveformCanvas
import com.red.sovereign.media.voice.VoiceCancelProgressBar
import com.red.sovereign.media.voice.VoiceLockIndicator
import com.red.sovereign.settings.DeviceSettingsScreen
import com.red.sovereign.settings.OfflineQueueScreen
import com.red.sovereign.settings.SettingsPage
import com.red.sovereign.settings.SmartServerSettingsScreen
import com.red.sovereign.features.profile.RecoveryHubScreen
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.settings.YounesSettingsSheet
import com.red.sovereign.social.FeedState
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.social.Post
import com.red.sovereign.social.ThreadState
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryState
import com.red.sovereign.stories.StoryViewerState
import com.red.sovereign.stories.StoryViewModel
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalRoyalBlue
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.AqyalSurfaceRaised
import com.red.sovereign.ui.theme.SovereignGradients
import com.red.sovereign.ui.components.SovereignAvatar
import com.red.sovereign.features.chat.LuxuryChatBubble
import androidx.compose.ui.draw.scale
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.features.communities.CommunitiesScreen
import com.red.sovereign.features.contacts.ContactsScreen
import com.red.sovereign.features.chat.SovereignChatInputBar
import com.red.sovereign.ui.components.SovereignEmptyConversationState
import java.io.File
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.security.MessageDigest

import com.red.sovereign.features.devices.DevicesScreen
import com.red.sovereign.features.explore.RedExploreScreen
import com.red.sovereign.features.privacy.PrivacySettingsScreen
import com.red.sovereign.features.chat.CreateGroupScreen
import com.red.sovereign.features.chat.RedGlobalSearch
import com.red.sovereign.features.chat.SovereignGroupInfoScreen
import com.red.sovereign.features.profile.BackupScreen
import com.red.sovereign.features.profile.ProfileScreen
import com.red.sovereign.core.YounesId
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.media.EventsScreen
import com.red.sovereign.media.PollsScreen
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.components.SovereignBottomBar
import com.red.sovereign.ui.components.rememberSovereignHaze
import com.red.sovereign.ui.components.sovereignHazeSource
import androidx.compose.material3.OutlinedTextFieldDefaults



private enum class SovereignScreen { DASHBOARD, DEVICES, PRIVACY, EXPLORE, CREATE_GROUP, BACKUP, GROUP_INFO, SEARCH, COMMUNITIES, CONTACTS, PROFILE, EVENTS, POLLS, ADMIN, DEVICE_SETTINGS, OFFLINE_QUEUE, RECOVERY_HUB, SMART_SERVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedDashboard(account: AuthState.Authenticated, viewModel: AuthViewModel, deepLinkSender: String? = null, deepLinkConversation: String? = null) {
    val context = LocalContext.current
    // زجاج مثلج حقيقي للشريط السفلي — المصدر: محتوى الشاشة فوقه.
    val hazeState = rememberSovereignHaze()
    var currentScreen by remember { mutableStateOf(SovereignScreen.DASHBOARD) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(MainSection.CHATS) } // الأفضل من واتساب: الدردشات أولاً (الأكثر استخداماً)
    // 🔗 فتح محادثة خاصة من قائمة أعضاء المجموعة أو جهات الاتصال (يتغذى على deepLinkSender في ChatHubScreen)
    var pendingChatTarget by remember { mutableStateOf<String?>(null) }
    // صورة مجموعة شاشة التأسيس: تُرفع عند التأكيد عبر GroupViewModel.create(avatarUri).
    // مرفوعة هنا (لا داخل when) لأن rememberLauncherForActivityResult يجب أن يُستدعى بلا شرط.
    var createScreenAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val createScreenAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            createScreenAvatarUri = uri
        }
    }
    // 🔔 Auto-switch to CALLS tab when call starts/ringing — fixes "لا تظهر التبويبة الصحيحة"
    androidx.compose.runtime.LaunchedEffect(CallRuntime.state) {
        if (CallRuntime.state !is CallUiState.Idle) section = MainSection.CALLS
    }
    // 🧹 مسح pendingChatTarget بعد فتح المحادثة حتى لا يُعاد فتحها عند التبديل بين التبويبات
    androidx.compose.runtime.LaunchedEffect(pendingChatTarget, section) {
        if (pendingChatTarget != null && section == MainSection.CHATS) {
            kotlinx.coroutines.delay(600)
            pendingChatTarget = null
        }
    }
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // ربط الستور الكامل: الصفحة المستهدفة داخل YounesSettingsSheet عند فتحه من
    // بنود DeviceSettingsScreen الـ35 (Keys/Receipts→PRIVACY، Theme/Accent/Font→APPEARANCE،
    // Notifications/DND→NOTIFICATIONS، AutoDownload/Bubbles→CHATS، Storage/Export→DATA،
    // Ringtone/Speaker/SOS→CALLS، Profile→ACCOUNT، Device/Sessions→DEVICES،
    // NetworkDiag→NETWORK_DIAG، Debug/Flags→DEVELOPER، About→ABOUT) — بلا تخزين جديد.
    var settingsInitialPage by remember { mutableStateOf(SettingsPage.ROOT) }
    val openSettingsAt: (SettingsPage) -> Unit = { page ->
        settingsInitialPage = page
        showSettings = true
    }
    // رقم مُعبّأ مسبقًا لشاشة الهاتف — يصل من لوحة الاتصال السريعة كي لا يُعاد إدخاله
    var chatConversationOpen by remember { mutableStateOf(false) }
    // 🔧 إصلاح العيب: dialer حقيقي لإدخال RED ID والاتصال 1-1 من CALLS section
    var showCallDialer by remember { mutableStateOf(false) }
    var dialerRedId by remember { mutableStateOf("") }
    var dialerVideo by remember { mutableStateOf(false) }
    var pendingDialerTarget by remember { mutableStateOf<String?>(null) }
    var pendingDialerVideo by remember { mutableStateOf(false) }
    // 🔴 البث المباشر — خيار خاص/عام بكلمة سر
    var showLiveCreateDialog by remember { mutableStateOf(false) }
    var liveTitle by remember { mutableStateOf("") }
    var liveIsPrivate by remember { mutableStateOf(false) }
    var livePassword by remember { mutableStateOf("") }
    val dialerCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingDialerVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val redId = pendingDialerTarget
        if (audioGranted && cameraGranted && redId != null && redId.matches(RED_ID_PATTERN)) {
            YounesCallService.start(context, redId, pendingDialerVideo)
            section = MainSection.CALLS
        }
        pendingDialerTarget = null
    }

    val feed: FeedViewModel = viewModel()
    // ... (rest of ViewModels)
    val stories: StoryViewModel = viewModel()
    val groups: GroupViewModel = viewModel()
    val directory: DirectoryViewModel = viewModel()
    val safety: SafetyViewModel = viewModel()
    val attachments: AttachmentViewModel = viewModel()
    val voiceMessages: VoiceMessageViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    val callHistory: CallHistoryViewModel = viewModel()
    val createStoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(stories::upload) }

    // ◀️ زر الرجوع — لا يخرج من التطبيق مباشرة، بل يتنقل هرمياً (واتساب)
    val activity = LocalContext.current as? android.app.Activity
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            showCreate -> showCreate = false
            showSettings -> showSettings = false
            showLiveCreateDialog -> { showLiveCreateDialog = false; livePassword = "" }
            showCallDialer -> { showCallDialer = false; dialerRedId = ""; dialerVideo = false }
            currentScreen != SovereignScreen.DASHBOARD -> currentScreen = SovereignScreen.DASHBOARD
            section != MainSection.CHATS -> section = MainSection.CHATS
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    activity?.finish()
                } else {
                    lastBackPress = now
                    android.widget.Toast.makeText(context, "اضغط مرة أخرى للخروج", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 🔔 Overlays must be global — before early return so they appear on ANY screen (Devices, Privacy, etc.)
    // YounesCallOverlay etc. are placed at the very end as well, but this early placement ensures incoming call is never missed
    if (currentScreen != SovereignScreen.DASHBOARD) {
        when (currentScreen) {
            SovereignScreen.DEVICES -> DevicesScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.PRIVACY -> PrivacySettingsScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.EXPLORE -> {
                val tokens = rememberDashboardTokenStore()
                RedExploreScreen(
                    tokens = tokens,
                    ownRedId = account.redId,
                    onBack = { currentScreen = SovereignScreen.DASHBOARD }
                )
            }
            SovereignScreen.CREATE_GROUP -> {
                CreateGroupScreen(
                    onBack = { currentScreen = SovereignScreen.DASHBOARD; createScreenAvatarUri = null },
                    friends = directory.contacts,
                    avatarUri = createScreenAvatarUri,
                    onPickAvatar = { createScreenAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                    onCreate = { name, description, privacy, memberRedIds, avatarUri ->
                        groups.create(name, description, privacy, memberRedIds, avatarUri) { currentScreen = SovereignScreen.DASHBOARD; section = MainSection.GROUPS; createScreenAvatarUri = null }
                    },
                    isSaving = groups.state == com.red.sovereign.groups.GroupState.Saving,
                    externalError = (groups.state as? com.red.sovereign.groups.GroupState.Error)?.message
                )
            }
            SovereignScreen.BACKUP -> BackupScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.PROFILE -> ProfileScreen(
                redId = account.redId,
                username = account.username,
                displayName = account.username,
                onBack = { currentScreen = SovereignScreen.DASHBOARD }
            )
            SovereignScreen.EVENTS -> {
                val tokens = rememberDashboardTokenStore()
                EventsScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD }, isAdmin = account.isAdmin)
            }
            SovereignScreen.POLLS -> {
                val tokens = rememberDashboardTokenStore()
                PollsScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD }, isAdmin = account.isAdmin)
            }
            SovereignScreen.GROUP_INFO -> {
                val infoGroup = groups.groups.firstOrNull { it.id == selectedGroupId }
                SovereignGroupInfoScreen(
                    group = infoGroup,
                    groups = groups,
                    friends = directory.contacts,
                    ownRedId = account.redId,
                    onBack = { currentScreen = SovereignScreen.DASHBOARD },
                    onMessage = { redId ->
                        pendingChatTarget = redId
                        section = MainSection.CHATS
                        currentScreen = SovereignScreen.DASHBOARD
                    }
                )
            }
            SovereignScreen.SEARCH -> RedGlobalSearch(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            // Phase 8: مدخل الإدارة السيادية — كان DINSTAR (محذوف)؛ الآن لوحة الإدارة الحقيقية.
            SovereignScreen.ADMIN -> {
                if (!account.isAdmin) { currentScreen = SovereignScreen.DASHBOARD; return }
                val adminVm: com.red.sovereign.features.admin.AdminViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                com.red.sovereign.features.admin.AdminDashboardScreen(adminVm, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            // ربط P0: شاشة إعدادات الجهاز بكل callbacks نحو الصفحات الحية —
            // الشيت يُفتح بالصفحة المستهدفة، والشاشات الكاملة عبر currentScreen.
            SovereignScreen.DEVICE_SETTINGS -> {
                DeviceSettingsScreen(
                    onBack = { currentScreen = SovereignScreen.DASHBOARD },
                    tokenStore = rememberDashboardTokenStore(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onThemeClick = { openSettingsAt(SettingsPage.APPEARANCE) },
                    onAppearanceClick = { openSettingsAt(SettingsPage.APPEARANCE) },
                    onAppLockClick = { openSettingsAt(SettingsPage.PRIVACY) },
                    onPrivacyClick = { openSettingsAt(SettingsPage.PRIVACY) },
                    onNotificationsClick = { openSettingsAt(SettingsPage.NOTIFICATIONS) },
                    onChatSettingsClick = { openSettingsAt(SettingsPage.CHATS) },
                    onDataSettingsClick = { openSettingsAt(SettingsPage.DATA) },
                    onAccountClick = { openSettingsAt(SettingsPage.ACCOUNT) },
                    onDevicesClick = { openSettingsAt(SettingsPage.DEVICES) },
                    onBackupClick = { currentScreen = SovereignScreen.RECOVERY_HUB },
                    onServerEditClick = { currentScreen = SovereignScreen.SMART_SERVER },
                    onOfflineQueueClick = { currentScreen = SovereignScreen.OFFLINE_QUEUE },
                    // الستور الكامل: النغمات/السماعة/SOS→CALLS، التشخيص→NETWORK_DIAG،
                    // السجلات/الأعلام→DEVELOPER، حول→ABOUT — بلا شارة قريباً.
                    onCallsClick = { openSettingsAt(SettingsPage.CALLS) },
                    onNetworkDiagClick = { openSettingsAt(SettingsPage.NETWORK_DIAG) },
                    onDeveloperClick = { openSettingsAt(SettingsPage.DEVELOPER) },
                    onAboutClick = { openSettingsAt(SettingsPage.ABOUT) }
                )
            }
            // ربط P0: قائمة Outbox الحقيقية (observePending + retry/delete لكل رسالة).
            SovereignScreen.OFFLINE_QUEUE -> {
                OfflineQueueScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            // ربط P0: مركز الاستعادة الحي (نسخة مشفرة + رموز ورقية + فحص جاف).
            SovereignScreen.RECOVERY_HUB -> {
                RecoveryHubScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            // ربط P0: تحرير نقطة الخادم (اكتشاف + إدخال يدوي + تحقق توقيع).
            SovereignScreen.SMART_SERVER -> {
                SmartServerSettingsScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.COMMUNITIES -> {
                val tokens = rememberDashboardTokenStore()
                CommunitiesScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.CONTACTS -> ContactsScreen(directory = directory, onBack = { currentScreen = SovereignScreen.DASHBOARD }, onChat = { person -> currentScreen = SovereignScreen.DASHBOARD; section = MainSection.CHATS }, onCall = { person, video -> com.red.sovereign.calls.YounesCallService.start(context, person.redId, video) }, onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP })
            else -> currentScreen = SovereignScreen.DASHBOARD
        }
        // Still show call overlays even when not on dashboard — unified
        UnifiedCallOverlays()
        // ربط P0: الشيت فوق الشاشات الفرعية أيضاً — بدونه تفتح بنود
        // DeviceSettingsScreen صفحةً لا تُعرض أبداً (return مبكر فوق).
        if (showSettings) YounesSettingsSheet(account, settings, viewModel, viewModel::logout, { showSettings = false; settingsInitialPage = SettingsPage.ROOT }, initialPage = settingsInitialPage)
        return
    }

    Scaffold(
        containerColor = SovereignColors.ObsidianDeep,
        floatingActionButton = {
            if (!chatConversationOpen) when (section) {
                MainSection.CHATS -> FloatingActionButton(
                    onClick = { currentScreen = SovereignScreen.CONTACTS },
                    containerColor = YounesEmerald,
                    contentColor = Color(0xFF002117),
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Chat, "دردشة جديدة") }
                MainSection.GROUPS -> FloatingActionButton(
                    onClick = { currentScreen = SovereignScreen.CREATE_GROUP },
                    containerColor = YounesEmerald,
                    contentColor = Color(0xFF002117),
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.GroupAdd, "مجموعة جديدة") }
                MainSection.CALLS -> FloatingActionButton(
                    onClick = { showCallDialer = true },
                    containerColor = YounesEmerald,
                    contentColor = Color(0xFF002117),
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Dialpad, "اتصال جديد") }
                MainSection.HOME -> FloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = YounesEmerald,
                    contentColor = Color(0xFF002117),
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Add, "إنشاء") }
                else -> {}
            }
        },
        bottomBar = {
            SovereignBottomBar(
                currentSection = section,
                onSectionSelected = { item ->
                    section = item
                    if (item == MainSection.CALLS) {
                        callHistory.load()
                        directory.refreshPresence()
                    }
                },
                hazeState = hazeState
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().sovereignHazeSource(hazeState).background(SovereignColors.ObsidianDeep)) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                RedTopBar(account.redId, account.username, compact = SettingsRuntime.current.compactMode, onSettings = { showSettings = true }, onSearch = { currentScreen = SovereignScreen.SEARCH })
                // 📴 بانر الطابور دون اتصال: يعرض عدد الرسائل المعلقة من Room
                // ويعيد جدولة العامل الحقيقي OutboxRetryWorker بضغطة واحدة،
                // وزر العرض يفتح OfflineQueueScreen (القائمة الكاملة retry/delete).
                OfflineOutboxBanner(onOpenQueue = { currentScreen = SovereignScreen.OFFLINE_QUEUE })
            when {
                section == MainSection.HOME -> FeedScreen(account, feed, stories, directory, onCreate = { showCreate = true })
                section == MainSection.CHATS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = false, deepLinkSender = pendingChatTarget ?: deepLinkSender, deepLinkConversation = deepLinkConversation, onConversationOpen = { chatConversationOpen = it })
                section == MainSection.GROUPS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = true, onManageGroup = { id -> selectedGroupId = id; currentScreen = SovereignScreen.GROUP_INFO }, onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP }, onConversationOpen = { chatConversationOpen = it })
                section == MainSection.CALLS -> UnifiedCallsScreen(
                    ownUserId = account.redId,
                    history = callHistory,
                    // بلا هذين المعاملين كان الاستدعاء يُربط بنسخة أضعف محذوفة،
                    // فتختفي خمس ميزات مكتوبة ومترجمة: منتقي
                    // المكالمة الجماعية بحالة الاتصال، إنشاء المؤتمر، تسجيلات
                    // المكالمات وإحصاءاتها، المكالمات المجدولة، وبحث جهات الاتصال
                    // داخل حوار المكالمة.
                    contacts = directory.contacts,
                    onlineIds = directory.onlineIds.toSet(),
                    // ما يراه المستلم كاسم للمضيف في دعوة المكالمة الجماعية؛
                    // فارغًا كان يظهر بلا اسم. نفس المصدر المستخدم في ProfileScreen.
                    myDisplayName = account.username,
                    onExplore = { currentScreen = SovereignScreen.EXPLORE },
                )
                else -> MoreScreen(
                    account,
                    onAdmin = { if (account.isAdmin) currentScreen = SovereignScreen.ADMIN },
                    onSettings = { showSettings = true },
                    onContacts = { currentScreen = SovereignScreen.CONTACTS },
                    onDevices = { currentScreen = SovereignScreen.DEVICES },
                    onPrivacy = { currentScreen = SovereignScreen.PRIVACY },
                    onBackup = { currentScreen = SovereignScreen.BACKUP },
                    onCommunities = { currentScreen = SovereignScreen.COMMUNITIES },
                    onProfile = { currentScreen = SovereignScreen.PROFILE },
                    onEvents = { currentScreen = SovereignScreen.EVENTS },
                    onPolls = { currentScreen = SovereignScreen.POLLS },
                    onDeviceSettings = { currentScreen = SovereignScreen.DEVICE_SETTINGS }
                )
            }
        }
    }
}

    if (showCreate) CreateSheet(
        publishing = feed.state == FeedState.Publishing,
        onDismiss = { showCreate = false },
        onPost = { text -> feed.create(text) { showCreate = false } },
        onPoll = { question, options, hours, images -> feed.createPoll(question, options, hours, optionImages = images) { showCreate = false } },
        onStory = { showCreate = false; createStoryPicker.launch(arrayOf("image/*", "video/*")) },
        onLive = { showCreate = false; liveTitle = ""; liveIsPrivate = false; livePassword = ""; showLiveCreateDialog = true },
        onExplore = { showCreate = false; currentScreen = SovereignScreen.EXPLORE }
    )
    if (showSettings) YounesSettingsSheet(account, settings, viewModel, viewModel::logout, { showSettings = false; settingsInitialPage = SettingsPage.ROOT }, initialPage = settingsInitialPage)
    UnifiedCallOverlays()

    // dialer لإدخال RED ID والاتصال 1-1 صوت/فيديو
    if (showCallDialer) {
        AlertDialog(
            onDismissRequest = { showCallDialer = false; dialerRedId = ""; dialerVideo = false },
            title = { Text("مكالمة جديدة عبر يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أدخل معرّف يونس للاتصال به مباشرة:\nمثال: ${YounesId.PLACEHOLDER}", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = dialerRedId,
                        onValueChange = { dialerRedId = YounesId.normalizeInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(YounesId.PLACEHOLDER) },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = dialerVideo, onCheckedChange = { dialerVideo = it })
                        Text("مكالمة فيديو", fontSize = 14.sp)
                    }
                    val valid = dialerRedId.matches(RED_ID_PATTERN)
                    if (dialerRedId.isNotBlank() && !valid) {
                        Text(YounesId.ERROR_MESSAGE, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                val perms = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (dialerVideo) add(Manifest.permission.CAMERA)
                }.toTypedArray()
                Button(
                    enabled = dialerRedId.matches(RED_ID_PATTERN),
                    onClick = {
                        val redId = dialerRedId
                        val video = dialerVideo
                        showCallDialer = false
                        dialerRedId = ""
                        dialerVideo = false
                        pendingDialerTarget = redId
                        pendingDialerVideo = video
                        dialerCallPermissions.launch(perms)
                    }
                ) { Text(if (dialerVideo) "اتصال فيديو" else "اتصال صوتي") }
            },
            dismissButton = { TextButton({ showCallDialer = false; dialerRedId = ""; dialerVideo = false }) { Text("إلغاء") } }
        )
    }

    // 🔴 حوار إنشاء البث المباشر — خاص بكلمة سر أو عام + دعوة أصدقاء
    if (showLiveCreateDialog) {
        val livePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val camOk = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val micOk = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (camOk && micOk) {
                val titleFinal = liveTitle.trim().ifBlank { "بث مباشر ${account.username}" }
                val pw = if (liveIsPrivate) livePassword.trim().takeIf { it.isNotBlank() } else null
                if (liveIsPrivate && pw.isNullOrBlank()) {
                    android.widget.Toast.makeText(context, "أدخل كلمة سر للبث الخاص", android.widget.Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                showLiveCreateDialog = false
                LiveStreamService.start(context, "stream-${account.redId}-${System.currentTimeMillis()}", account.redId, true, titleFinal, liveIsPrivate, pw)
                livePassword = ""
            }
        }
        AlertDialog(
            onDismissRequest = { showLiveCreateDialog = false; livePassword = "" },
            title = { Text("بدء بث مباشر 🔴", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = liveTitle,
                        onValueChange = { liveTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("عنوان البث (اختياري)") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(if (liveIsPrivate) Icons.Filled.Lock else Icons.Filled.Public, null, tint = if (liveIsPrivate) Color(0xFFE53935) else YounesEmerald)
                        Column(Modifier.weight(1f)) {
                            Text(if (liveIsPrivate) "بث خاص بكلمة سر" else "بث عام (بدون كلمة سر)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (liveIsPrivate) "المشاهدون يحتاجون كلمة السر" else "يمكن للجميع المشاهدة", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = liveIsPrivate, onCheckedChange = { liveIsPrivate = it })
                    }
                    if (liveIsPrivate) {
                        OutlinedTextField(
                            value = livePassword,
                            onValueChange = { livePassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("كلمة السر") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                    }
                    Text("سيتمكن الأصدقاء من الانضمام عبر دعوة أو رابط younes://livestream/<id>", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = { livePermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text("بدء البث") }
            },
            dismissButton = { TextButton({ showLiveCreateDialog = false; livePassword = "" }) { Text("إلغاء") } }
        )
    }
}


@Composable
private fun RedTopBar(redId: String, username: String, compact: Boolean, onSettings: () -> Unit, onSearch: () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        painterResource(R.drawable.younes_icon_master),
        contentDescription = "يونس",
        modifier = Modifier.size(if (compact) 34.dp else 40.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    Column(Modifier.weight(1f).padding(start = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("يونس • @$username", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(redId, color = AqyalCyanGlow, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    IconButton(onSearch) { Icon(Icons.Default.Search, "البحث الشامل") }
    IconButton(onSettings) { Icon(Icons.Default.Settings, "الإعدادات") }
}

@Composable
private fun StoryCircle(label: String, own: Boolean, click: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = click)) {
    Box(Modifier.size(66.dp).clip(CircleShape).background(if (own) AqyalGold else AqyalCyanGlow), contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(AqyalRoyalBlue), contentAlignment = Alignment.Center) {
            Icon(if (own) Icons.Default.Add else Icons.Default.Person, null)
        }
    }
    Text(label, fontSize = 11.sp, maxLines = 1)
}

@Composable
private fun PostCard(
    post: Post,
    currentRedId: String,
    onLike: (Post) -> Unit,
    onFollow: (Post) -> Unit,
    onVote: (Post, String) -> Unit,
    onThread: () -> Unit,
    onQuote: () -> Unit,
    onEdit: (Post, String) -> Unit = { _, _ -> },
    onDelete: (Post) -> Unit = {},
    onHide: (Post) -> Unit = {},
    onMute: (Post) -> Unit = {},
    onReport: (Post) -> Unit = {}
) = Card(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy.copy(alpha = .96f)),
    shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var showEditHistory by androidx.compose.runtime.remember(post.id) { androidx.compose.runtime.mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(YounesEmerald, AqyalCyanGlow, AqyalGold))
                ),
                contentAlignment = Alignment.Center
            ) { Text(post.authorDisplayName.take(1).ifBlank { "ي" }, color = Color(0xFF03120E), fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(post.authorDisplayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${post.authorUsername} · ${post.authorRedId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, "خيارات") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (post.authorRedId == currentRedId) {
                    DropdownMenuItem(text = { Text("تعديل") }, onClick = { showMenu = false; onEdit(post, post.text) })
                    DropdownMenuItem(text = { Text("حذف") }, onClick = { showMenu = false; onDelete(post) })
                } else {
                    DropdownMenuItem(text = { Text("إخفاء") }, onClick = { showMenu = false; onHide(post) })
                    DropdownMenuItem(text = { Text("كتم @${post.authorUsername}") }, onClick = { showMenu = false; onMute(post) })
                    DropdownMenuItem(text = { Text("إبلاغ") }, onClick = { showMenu = false; onReport(post) })
                }
            }
            if (post.authorRedId != currentRedId) TextButton({ onFollow(post) }) { Text("إضافة صديق") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // شارات عرض فقط (كانت AssistChip معطلة بـ onClick فارغ) — نصوص ثابتة بلا تفاعل وهمي.
            Text(if (post.visibility == "LOCAL_YEMEN") "نبض محلي" else "عام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (post.poll != null) "استطلاع" else if (post.parentId != null) "رد" else "منشور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (post.kind != "POST") Text(post.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(post.text, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
        if (post.hashtags.isNotEmpty() || post.mentions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                post.hashtags.forEach { tag -> Text(tag, color = AqyalCyanGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                post.mentions.forEach { m -> Text(m, color = YounesEmerald, fontSize = 13.sp) }
            }
        }
        if (SettingsRuntime.current.linkPreviews) post.linkCard?.let { card ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(card.title ?: card.url, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(card.description ?: "", color = Color.Gray, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        if (post.editedAt != null) TextButton({ showEditHistory = true }) { Text("تم التعديل — عرض السجل", color = Color.Gray, fontSize = 11.sp) }
        if (showEditHistory) AlertDialog(
            onDismissRequest = { showEditHistory = false },
            title = { Text("سجل التعديلات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.editHistory.isEmpty()) Text("لا يوجد سجل متاح", fontSize = 13.sp)
                    post.editHistory.forEach { entry ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.text, fontSize = 13.sp)
                                Text(entry.editedAt, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showEditHistory = false }) { Text("إغلاق") } }
        )
        post.quotePostId?.let { quotedId ->
            Card(colors = CardDefaults.cardColors(containerColor = AqyalSurfaceRaised.copy(alpha = .72f))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, null, tint = AqyalGold, modifier = Modifier.size(18.dp))
                    Text(" اقتباس يونس · ${quotedId.take(8)}", color = AqyalGold, fontSize = 12.sp)
                }
            }
        }
        post.poll?.let { poll ->
            val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poll.options.forEach { option ->
                    val ratio = (option.votes.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
                    Card(
                        Modifier.fillMaxWidth().clickable { onVote(post, option.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(option.text, fontWeight = FontWeight.SemiBold)
                                Text("${(ratio * 100).toInt()}%", color = AqyalCyanGlow, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                                color = YounesEmerald,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Text("${option.votes} صوت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text("إجمالي الأصوات: ${poll.options.sumOf { it.votes }}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PostAction(Icons.Default.FavoriteBorder, "${post.reactionCounts["LIKE"] ?: 0}", true) { onLike(post) }
            PostAction(Icons.AutoMirrored.Filled.Chat, post.replyCount.toString(), true, onThread)
            PostAction(Icons.Default.Repeat, "اقتباس", true, onQuote)
            PostAction(Icons.Default.Share, "مشاركة", true) {
                val shareText = buildString {
                    append(post.text)
                    if (post.hashtags.isNotEmpty()) append("\n").append(post.hashtags.joinToString(" "))
                    append("\n\nيونس · @").append(post.authorUsername)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة منشور يونس")) }
            }
        }
    }
}

@Composable private fun PostAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) = TextButton(action, enabled = enabled) { Icon(icon, label, Modifier.size(18.dp)); Text(" $label", fontSize = 11.sp) }
// (حُذف المغلف الرقيق Avatar — استورد SovereignAvatar مباشرة من ui/components/Avatar.kt. 2026-09-10)

@Composable private fun GroupAvatar(group: com.red.sovereign.groups.Group, groups: GroupViewModel) {
    // موحد: التنفيذ في components/Avatar.kt مع LaunchedEffect(group.id, group.avatarUrl).
    // كان LaunchedEffect(avatarUrl) وحده — نفس null لمجموعتين يعلّق الصورة.
    com.red.sovereign.ui.components.SovereignGroupAvatar(group, groups, themed = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHubScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    safety: SafetyViewModel,
    attachments: AttachmentViewModel,
    voiceMessages: VoiceMessageViewModel,
    showGroups: Boolean,
    deepLinkSender: String? = null,
    deepLinkConversation: String? = null,
    onManageGroup: (String) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onConversationOpen: (Boolean) -> Unit = {}
) {
    // الحضور الجماعي بمانع عاصفة: كان المفتاح contacts.size فيطلق
    // refreshPresence مع كل إضافة أثناء المزامنة الأولى؛ الآن snapshotFlow
    // بهوية جهات الاتصال + debounce 2000ms — لا منطق محذوف، فقط المفتاح.
    LaunchedEffect(directory) {
        snapshotFlow { directory.contacts.map { it.redId } }
            .debounce(PRESENCE_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { directory.refreshPresence() }
    }
    // نبضة الحضور الدورية: تحديث online/lastSeen كل 60 ثانية أثناء بقاء
    // الشاشة مفتوحة، حتى لو لم تتغير قائمة جهات الاتصال.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            runCatching { directory.refreshPresence() }
        }
    }
    val tab = if (showGroups) 1 else 0
    var target by remember { mutableStateOf("") }
    // فتح محادثة من إشعار رسالة
    LaunchedEffect(deepLinkSender, deepLinkConversation) {
        if (!showGroups && deepLinkSender != null && deepLinkSender.matches(RED_ID_PATTERN)) target = deepLinkSender
    }
    var showDirectory by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }
    var showMediaGallery by remember { mutableStateOf(false) }
    var showGroupMediaGallery by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<PublicRedProfile?>(null) }
    var directoryQuery by remember { mutableStateOf("") }
    // استخراج 2026-09-10: بحث الدليل مع debounce في DashboardSearch.kt — الزر اليدوي يبقى، وهذا تأثير تلقائي فوقه.
    DebouncedDirectorySearchEffect(query = directoryQuery, directory = directory)
    var reportDetails by remember { mutableStateOf("") }
    // مسودات بلا تسرب: كل محادثة لها خانة مستقلة بمفتاح هدفها.
    // كان `remember { }` بلا مفتاح فيبقى نص المحادثة A ظاهرًا عند فتح B.
    // الآن التبديل ينشئ خانة فارغة ثم يملؤها أثر الاستعادة من Room.
    var messageText by remember(target) { mutableStateOf("") }
    var selectedChatMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var replyToMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var pendingForwardMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var disappearingDurationMs by remember { mutableStateOf<Long?>(null) }
    var pendingCallVideo by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var showJoinGroup by remember { mutableStateOf(false) }
    var joinToken by remember { mutableStateOf("") }
    var manageGroupId by remember { mutableStateOf<String?>(null) }
    var groupConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target, groupConversationId) { onConversationOpen(target.isNotBlank() || groupConversationId != null) }
    var showGroupEmoji by remember { mutableStateOf(false) }
    var showGroupStickers by remember { mutableStateOf(false) }
    var groupReplyToMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var showGroupAttachmentSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showGroupVoicePanel by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showGroupPollDialog by remember { mutableStateOf(false) }
    var groupPollQuestion by remember { mutableStateOf("") }
    var groupPollOptions by remember { mutableStateOf(listOf("", "")) }
    val messagesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val groupListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val groupUnread = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    val chatUnread = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var chatSearchQuery by remember { mutableStateOf("") }
    var chatUnreadFilter by remember { mutableStateOf(false) }
    val chatDrafts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val groupPinnedMessages = remember { androidx.compose.runtime.mutableStateMapOf<String, DecryptedMessage>() }
    val blockedIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { blockedIds.clear(); blockedIds.addAll(directory.blocked) }
    var groupMessageText by remember(groupConversationId) { mutableStateOf("") }
    var groupEditingMessageId by remember { mutableStateOf<String?>(null) }
    var groupDisappearingMs by remember { mutableStateOf<Long?>(null) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showGroupDisappearingDialog by remember { mutableStateOf(false) }
    var selectedGroupMember by remember { mutableStateOf<GroupMember?>(null) }
    var deleteGroupId by remember { mutableStateOf<String?>(null) }
    var memberRedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    // صورة المجموعة الجديدة المختارة قبل الإنشاء — تُعاين محلياً ثم تُرفع عند
    // الضغط على «إنشاء المجموعة» عبر GroupViewModel.create(avatarUri) الذي يحفظ avatarUrl.
    var pendingCreateAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val decrypted = remember { mutableStateListOf<DecryptedMessage>() }
    // ◀️ رجوع هرمي داخل المحادثات — يغلق الطبقات قبل الخروج من التطبيق
    // السياسة (الأولوية) في DashboardBackPolicy.topSheet() — هنا فقط الربط بالـ setters.
    val dashboardBackState = DashboardBackState(
        hasTarget = target.isNotBlank(),
        hasGroupConversation = groupConversationId != null,
        showDirectory = showDirectory,
        showMessageSearch = showMessageSearch,
        showMediaGallery = showMediaGallery,
        showGroupMediaGallery = showGroupMediaGallery,
        hasSelectedContact = selectedContact != null,
        showJoinGroup = showJoinGroup,
        hasManageGroup = manageGroupId != null,
        hasSelectedChatMessage = selectedChatMessage != null,
        showDisappearingDialog = showDisappearingDialog,
        showGroupDisappearingDialog = showGroupDisappearingDialog,
        showGroupAttachmentSheet = showGroupAttachmentSheet,
        showAttachmentSheet = showAttachmentSheet,
        showGroupVoicePanel = showGroupVoicePanel,
        showGroupMenu = showGroupMenu,
        showEmoji = showEmoji,
        showStickers = showStickers,
        showGroupEmoji = showGroupEmoji,
        showGroupStickers = showGroupStickers,
        showGroupPollDialog = showGroupPollDialog
    )
    val dispatchDashboardBack = rememberDashboardBackDispatcher(dashboardBackState) { sheet ->
        when (sheet) {
            DashboardSheet.SelectedChatMessage -> selectedChatMessage = null
            DashboardSheet.SelectedContact -> selectedContact = null
            DashboardSheet.Directory -> showDirectory = false
            DashboardSheet.MessageSearch -> showMessageSearch = false
            DashboardSheet.MediaGallery -> showMediaGallery = false
            DashboardSheet.GroupMediaGallery -> showGroupMediaGallery = false
            DashboardSheet.JoinGroup -> showJoinGroup = false
            DashboardSheet.ManageGroup -> manageGroupId = null
            DashboardSheet.DisappearingDialog -> showDisappearingDialog = false
            DashboardSheet.GroupDisappearingDialog -> showGroupDisappearingDialog = false
            DashboardSheet.GroupPollDialog -> showGroupPollDialog = false
            DashboardSheet.GroupAttachmentSheet -> showGroupAttachmentSheet = false
            DashboardSheet.AttachmentSheet -> showAttachmentSheet = false
            DashboardSheet.GroupVoicePanel -> showGroupVoicePanel = false
            DashboardSheet.GroupMenu -> showGroupMenu = false
            DashboardSheet.GroupEmojiStickers -> { showGroupEmoji = false; showGroupStickers = false }
            DashboardSheet.EmojiStickers -> { showEmoji = false; showStickers = false }
            DashboardSheet.GroupConversation -> groupConversationId = null
            DashboardSheet.TargetConversation -> target = ""
        }
    }
    BackHandler(enabled = dashboardBackState.isBackEnabled()) {
        dispatchDashboardBack()
    }
    val context = LocalContext.current
    // توحيد 2026-09-10: مخازن واحدة عبر DashboardStoresViewModel (لا نسخ remember متفرقة).
    val dashboardStores = rememberDashboardStores()
    val pinApi = remember(dashboardStores) { PinsApi(dashboardStores.apiClient) }
    var messageInfo by remember { mutableStateOf<DecryptedMessage?>(null) }
    val editedMessageIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // مزامنة تثبيت رسائل المجموعة مع الخادم — عند فتحها ثم كل 30 ثانية
    // (يلتقط تثبيتات الأعضاء الآخرين أثناء بقائك في المحادثة)
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        while (groupConversationId != null) {
            when (val r = pinApi.listForGroup(groupConversationId.orEmpty())) {
                is com.red.sovereign.auth.ApiResult.Success -> {
                    val known = r.value.map { it.messageUuid }.toSet()
                    groupPinnedMessages.keys.retainAll(known)
                    r.value.forEach { pin ->
                        if (!groupPinnedMessages.containsKey(pin.messageUuid)) {
                            decrypted.firstOrNull { it.id == pin.messageUuid }?.let { groupPinnedMessages[it.id] = it }
                        }
                    }
                }
                is com.red.sovereign.auth.ApiResult.Error -> Unit
            }
            kotlinx.coroutines.delay(30_000)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repository = dashboardStores.repository
    val localMessages = dashboardStores.localMessages
    // كتم المجموعة: يُقرأ من التفضيلات المحلية عند فتح المجموعة
    var groupMuted by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        groupMuted = if (groupConversationId != null) {
            localMessages.conversationPreference(groupConversationId.orEmpty()).third > System.currentTimeMillis()
        } else false
        groupDisappearingMs = groupConversationId
            ?.let(localMessages::conversationDisappearingDuration)
            ?.takeIf { it > 0L }
    }
    // مؤقت الاختفاء الفردي: يُقرأ من التفضيلات المحلية عند فتح المحادثة
    // (كان in-memory فقط فيضيع عند إعادة الفتح ويتسرب بين المحادثات).
    androidx.compose.runtime.LaunchedEffect(target) {
        disappearingDurationMs = target.takeIf { it.isNotBlank() }
            ?.let { conversationId(account.redId, it) }
            ?.let(localMessages::conversationDisappearingDuration)
            ?.takeIf { it > 0L }
    }
    // إعادة بناء أصوات الاستطلاع من السجل المحلي عند فتح المجموعة
    // المفتاح معرف آخر رسالة لا الحجم — يمنع إعادة فك الكل مع كل حذف/تحديث.
    androidx.compose.runtime.LaunchedEffect(groupConversationId, decrypted.lastOrNull()?.id) {
        if (groupConversationId != null) {
            decrypted.filter { it.type == "RICH_TEXT" && it.conversationId == groupConversationId }.forEach { item ->
                RichMessage.decode(item.plaintext)?.let { rich ->
                    val pollId = rich.pollVoteOf ?: return@let
                    if (rich.action == "POLL_VOTE") {
                        ChatPollVoteStore.record(pollId, item.senderRedId, rich.pollVoteOption)
                    }
                }
            }
        }
    }
    val conversations by repository.getActiveConversations().collectAsState(initial = emptyList())
    // 📥 استعادة عدادات غير المقروء المحفوظة (تنجو من إعادة التشغيل) — ما لم تكن المحادثة مفتوحة حالياً
    // المفتاح معرف آخر محادثة لا الحجم — يمنع إعادة المسح مع كل رسالة.
    androidx.compose.runtime.LaunchedEffect(conversations.lastOrNull()?.id, target, groupConversationId) {
        val openConv = groupConversationId ?: target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) }
        val groupIds = groups.groups.map(com.red.sovereign.groups.Group::id).toSet()
        conversations.forEach { conv ->
            if (conv.id != openConv && conv.unreadCount > 0) {
                if (conv.id in groupIds) groupUnread[conv.id] = conv.unreadCount
                else chatUnread[conv.id] = conv.unreadCount
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(conversations.lastOrNull()?.id, target) {
        if (target.isBlank()) {
            chatDrafts.clear()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                conversations.forEach { conv -> repository.getDraft(conv.id)?.let { if (it.text.isNotBlank()) chatDrafts[conv.id] = it.text } }
            }
        }
    }
    // تفاعلات الإيموجي: messageId -> قائمة التفاعلات (للعرض السريع تحت كل رسالة)
    val reactionsByMessage = remember { androidx.compose.runtime.mutableStateMapOf<String, List<MessageReactionEntity>>() }
    // الرسائل المُعلَّمة (Starred): مجموعة معرفات الرسائل المُعلَّمة محلياً
    val starredMessageIds = remember { androidx.compose.runtime.mutableStateSetOf<String>() }
    // تحميل الرسائل المُعلَّمة من قاعدة البيانات
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repository.getAllStarredMessages().collect { starredList ->
            starredMessageIds.clear()
            starredMessageIds.addAll(starredList.map { it.messageId })
        }
    }

    val typingUsers = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.core.TypingEventBus.events.collect { event ->
            if (SettingsRuntime.current.typingIndicators && event.userId != account.redId) {
                // المفتاح = معرف المحادثة (خاصة أو جماعية) — يدعم مؤشر الكتابة الجماعي
                val key = event.conversationId
                if (event.isTyping) typingUsers[key] = System.currentTimeMillis() + 5000L
                else typingUsers.remove(key)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.crypto.MessageAckBus.acks.collect { ack ->
            val index = decrypted.indexOfFirst { it.id == ack.messageId }
            if (index != -1) {
                decrypted[index] = decrypted[index].copy(status = ack.status)
            }
        }
    }
    // Phase-1 (2026-09-14): استبدال عنصر نائب بالمحتوى الحقيقي بعد نجاح الفك المتأخر.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.crypto.DecryptedMessageUpdateBus.updates.collect { item ->
            val index = decrypted.indexOfFirst { it.id == item.id }
            if (index != -1) decrypted[index] = item else decrypted.add(item)
        }
    }
    // Phase-1 (2026-09-14): تنبيه فوري عند فشل الإرسال (كان يضيع بصمت).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.crypto.MessageSendErrorBus.errors.collect { error ->
            android.widget.Toast.makeText(context, error.arabicMessage, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    // تحديث فوري لعرض التفاعلات عند ورود حدث E2EE (إضافة/إزالة)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        ReactionEventBus.events.collect { event ->
            val current = reactionsByMessage[event.messageId].orEmpty()
            val withoutSender = current.filterNot { it.senderId == event.senderId }
            val updated = if (event.remove || event.emoji == null) {
                withoutSender
            } else {
                withoutSender + MessageReactionEntity(event.messageId, event.conversationId, event.senderId, event.emoji, event.timestamp)
            }
            reactionsByMessage[event.messageId] = updated.sortedBy { it.timestamp }
        }
    }
    // أصوات الاستطلاع E2EE: تُسجَّل من الرسائل الغنية الواردة (POLL_VOTE)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            DecryptedMessageBus.messages.collect { item ->
                runCatching {
                    if (item.type == "RICH_TEXT") {
                        RichMessage.decode(item.plaintext)?.let { rich ->
                            val pollId = rich.pollVoteOf ?: return@let
                            if (rich.action == "POLL_VOTE") {
                                ChatPollVoteStore.record(pollId, item.senderRedId, rich.pollVoteOption)
                            }
                        }
                    }
                }.onFailure { e ->
                    android.util.Log.w("RedDashboard", "Poll-vote item skipped", e)
                }
            }
        }.onFailure { e ->
            android.util.Log.e("RedDashboard", "Poll-vote collector failed", e)
        }
    }
    // منظف مؤشر الكتابة — المفتاح Unit لا الخريطة نفسها (كانت LaunchedEffect(typingUsers)
    // تُعيد التشغيل مع كل حدث كتابة = عاصفة؛ الحلقة الداخلية تكفي).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = System.currentTimeMillis()
            typingUsers.entries.removeAll { it.value < now }
        }
    }

    // مؤشر الكتابة الخاص — snapshotFlow + debounce 500ms بدل المفتاح messageText
    // (كان يُطلق startService مع كل حرف = عاصفة؛ الآن يُرسل بعد توقف الكتابة — لا منطق محذوف).
    androidx.compose.runtime.LaunchedEffect(target) {
        snapshotFlow { messageText }
            .debounce(500)
            .distinctUntilChanged()
            .collect { text ->
                if (target.matches(RED_ID_PATTERN) && SettingsRuntime.current.typingIndicators) {
                    val typingConversation = conversationId(account.redId, target)
                    val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                        action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, typingConversation)
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, text.isNotEmpty())
                    }
                    context.startService(intent)
                    if (text.isNotEmpty()) {
                        kotlinx.coroutines.delay(3000)
                        val stopIntent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                            action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, typingConversation)
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, false)
                        }
                        context.startService(stopIntent)
                    }
                }
            }
    }

    // 📝 مؤشر الكتابة الجماعي — snapshotFlow + debounce 500ms بدل مفتاح النص الخام
    // (كان يُطلق startService مع كل حرف؛ الآن بعد توقف الكتابة — لا منطق محذوف).
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        snapshotFlow { groupMessageText }
            .debounce(500)
            .distinctUntilChanged()
            .collect { text ->
                val groupId = groupConversationId ?: return@collect
                if (SettingsRuntime.current.typingIndicators) {
                    val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                        action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, groupId)
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, groupId)
                        putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, text.isNotEmpty())
                    }
                    context.startService(intent)
                    if (text.isNotEmpty()) {
                        kotlinx.coroutines.delay(3000)
                        val stopIntent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                            action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, groupId)
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, groupId)
                            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, false)
                        }
                        context.startService(stopIntent)
                    }
                }
            }
    }

    val draftScope = androidx.compose.runtime.rememberCoroutineScope()
    // حفظ تلقائي للمسودتين مع debounce — الخاص والجماعي منفصلان تمامًا
    // حتى لا تتسرب مسودة الخاص إلى الجماعي والعكس.
    androidx.compose.runtime.LaunchedEffect(target) {
        snapshotFlow { messageText }
            .debounce(500)
            .distinctUntilChanged()
            .collect { text ->
                val convId = target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) } ?: return@collect
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (text.isNotBlank()) repository.saveDraft(convId, text)
                    else repository.deleteDraft(convId)
                }
            }
    }
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        snapshotFlow { groupMessageText }
            .debounce(500)
            .distinctUntilChanged()
            .collect { text ->
                val convId = groupConversationId ?: return@collect
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (text.isNotBlank()) repository.saveDraft(convId, text)
                    else repository.deleteDraft(convId)
                }
            }
    }
    // شبكة أمان عند مغادرة الشاشة — يحفظ ما لم يلحقه الـ debounce.
    androidx.compose.runtime.DisposableEffect(target, groupConversationId) {
        onDispose {
            val privateConv = target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) }
            val privateText = messageText
            val groupConv = groupConversationId
            val groupText = groupMessageText
            if (privateConv != null && privateText.isNotBlank()) {
                draftScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.saveDraft(privateConv, privateText) }
            }
            if (groupConv != null && groupText.isNotBlank()) {
                draftScope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.saveDraft(groupConv, groupText) }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && target.isNotBlank()) attachments.send(uri, target, conversationId(account.redId, target))
    }
    val groupAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        if (uri != null && group != null) groups.updateAvatar(group, uri)
    }
    // منتقي صورة المجموعة الجديدة (حوار الإنشاء) — يخزن Uri محلياً للمعاينة،
    // والرفع عبر MediaApi + حفظ avatarUrl يتم داخل GroupViewModel.create(avatarUri) عند التأكيد.
    val createAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingCreateAvatarUri = uri
        }
    }
    var exportingMessageId by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pendingExportId = exportingMessageId ?: return@rememberLauncherForActivityResult
        if (uri != null) { attachments.exportTo(pendingExportId, uri); exportingMessageId = null }
    }
    // 📎 مرفقات المجموعة — تُرسل عبر مسار تشفير المجموعة (Sender Keys)
    val groupFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        if (uri != null && group != null) attachments.sendToGroup(uri, group)
    }
    val groupCameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val file = File(context.cacheDir, "camera/latest_photo.jpg")
            val group = groups.groups.firstOrNull { it.id == groupConversationId }
            if (file.isFile && group != null) {
                val providerUri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                attachments.sendToGroup(providerUri, group)
            }
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val file = File(context.cacheDir, "camera/latest_photo.jpg")
            if (file.isFile && target.isNotBlank()) {
                val providerUri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                attachments.send(providerUri, target, conversationId(account.redId, target))
            }
        }
    }
    // showAttachmentSheet الفردية مُعلنة مبكراً بجانب طبقات المجموعة (قبل DashboardBackState)
    // لتُشمل في سياسة الرجوع — لا إعلان مكرر هنا.
    var showSafetyScanner by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showSafetyScanner = granted
        if (!granted) safety.cameraPermissionDenied()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cleanTarget = com.red.sovereign.core.YounesId.normalizeInput(target).ifBlank { target }
        if (granted && cleanTarget.isNotBlank()) voiceMessages.start(cleanTarget, conversationId(account.redId, cleanTarget))
        else if (!granted) voiceMessages.permissionDenied()
    }
    val groupVoiceMicrophonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        if (granted && group != null) voiceMessages.startForGroup(group)
        else if (!granted) voiceMessages.permissionDenied()
    }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingCallVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val cleanTarget = com.red.sovereign.core.YounesId.normalizeInput(target).ifBlank { target }
        // رفض الكاميرا لا يمنع المكالمة — نبدأها صوتية مع إعلام (المستخدم يفعّل الكاميرا لاحقاً من شارة إعادة المحاولة).
        if (audioGranted && cleanTarget.isNotBlank()) {
            val startVideo = pendingCallVideo && cameraGranted
            YounesCallService.start(context, cleanTarget, startVideo)
            if (pendingCallVideo && !cameraGranted) {
                android.widget.Toast.makeText(context, "لم يُمنح إذن الكاميرا — بدأت المكالمة صوتية. يمكنك تفعيل الكاميرا من شاشة المكالمة.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    var pendingGroupVideo by remember { mutableStateOf(false) }
    // 📞 واتساب: المجموعات تملك فقط مكالمات ترن الجميع (حتى 32). المساحات/المؤتمرات ميزات مستقلة خارج المجموعات.
    val groupCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingGroupVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        // رفض الكاميرا لا يمنع المكالمة الجماعية — تبدأ صوتية مع إعلام (واتساب: يمكن تشغيل الفيديو لاحقاً)
        val effectiveVideo = pendingGroupVideo && cameraGranted
        if (audioGranted && group != null) {
            // صلاحية المجموعة: بدء المكالمات قد يكون للمشرفين فقط.
            if (group.settings.onlyAdminsCanCall) {
                val myRole = group.members.firstOrNull { it.redId == account.redId }?.role?.uppercase()
                if (myRole != "OWNER" && myRole != "ADMIN") {
                    android.widget.Toast.makeText(context, "بدء المكالمات للمشرفين فقط في هذه المجموعة", android.widget.Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
            }
            // واتساب: حتى 32 مشاركاً (2024) لكل من الصوت والفيديو؛ SFU يوسع السقف، Mesh يتراجع لـ 8
            val inviteeMembers = group.members.filter { it.redId != account.redId }
            if (inviteeMembers.isEmpty()) {
                android.widget.Toast.makeText(context, "لا يوجد أعضاء آخرون للاتصال بهم في هذه المجموعة", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            if (inviteeMembers.size > 32) {
                android.widget.Toast.makeText(context, "مكالمة المجموعة تدعم حتى 32 مشاركاً (المجموعة بها ${group.members.size}). سيتم الاتصال بأول 32.", android.widget.Toast.LENGTH_LONG).show()
            }
            val inviteIds = inviteeMembers.take(32).map { it.redId }
            val inviteNames = inviteeMembers.take(32).map { it.username ?: it.redId.take(8) }
            if (pendingGroupVideo && !cameraGranted) {
                android.widget.Toast.makeText(context, "لم يُمنح إذن الكاميرا — ستبدأ المكالمة صوتية. يمكنك تفعيل الكاميرا لاحقاً.", android.widget.Toast.LENGTH_LONG).show()
            }
            // واتساب: كل مكالمة مجموعة ترن جميع الأعضاء مباشرة عبر GroupCallService (Mesh/SFU)
            // المعرف يُولَّد هنا (لا داخل الخدمة) ليُضمَّن في رسالة النظام — به يعمل الانضمام المتأخر.
            val groupCallId = UUID.randomUUID().toString()
            com.red.sovereign.calls.GroupCallService.startGroupCall(
                context = context,
                myUserId = account.redId,
                inviteeIds = inviteIds,
                inviteeNames = inviteNames,
                isVideo = effectiveVideo,
                hostName = account.username,
                groupId = group.id,
                groupCallId = groupCallId
            )

            // رسالة نظام في دردشة المجموعة — مثل واتساب: "بدأت مكالمة صوتية جماعية — انقر للانضمام"
            val title = if (effectiveVideo) "مكالمة فيديو جماعية 📹" else "مكالمة صوتية جماعية 📞"
            val rich = com.red.sovereign.core.RichMessage(
                action = "CALL_STARTED",
                text = "بدأ $title. ترن جميع الأعضاء — يمكن الانضمام حتى بعد بدء المكالمة.",
                callId = groupCallId,
                callIsVideo = effectiveVideo
            )
            com.red.sovereign.core.RedConnectionService.sendGroupRichText(context, group, rich)
        }
    }
    LaunchedEffect(Unit) {
        // جامع الرسائل محمي: خطأ في رسالة واحدة لا يلغي التدفق كله.
        // catch للتدفق + try/catch لكل رسالة + إعادة المحاولة عند فشل المنبع.
        try {
            DecryptedMessageBus.messages
                .retryWhen { _, attempt ->
                    if (attempt > 0) kotlinx.coroutines.delay(1000)
                    true
                }
                .catch { e ->
                    android.util.Log.e("RedDashboard", "DecryptedMessageBus flow error", e)
                }.collect { item ->
                        try {
                            // العرض المتفائل قد يكون أضافها مسبقاً بنفس المعرف — لا تكرار.
                            if (decrypted.none { it.id == item.id }) decrypted.add(item)
                            if (item.type == "RICH_TEXT") {
                                RichMessage.decode(item.plaintext)?.let { rich ->
                                    // 🔐 علامة ✏️ للمعدَّل: فقط إن كان مُرسل التعديل هو مالك الرسالة
                                    val editedId = rich.editOf ?: return@let
                                    if (rich.action == "EDIT") {
                                        if (decrypted.any { it.id == editedId && it.senderRedId == item.senderRedId }) editedMessageIds[editedId] = true
                                    }
                                }
                            }
                            // تتبع غير المقروء للرسائل الواردة (ما لم تكن المحادثة/المجموعة مفتوحة حالياً)
                            if (!item.outgoing) {
                                if (item.conversationId.length > 32) {
                                    if (item.conversationId != groupConversationId) {
                                        groupUnread[item.conversationId] = (groupUnread[item.conversationId] ?: 0) + 1
                                    } else {
                                        // المجموعة مفتوحة: تصفير العداد المحفوظ كي لا يتراكم عند إعادة الفتح
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.clearUnread(item.conversationId) }
                                    }
                                } else {
                                    if (item.conversationId != conversationId(account.redId, target)) {
                                        chatUnread[item.conversationId] = (chatUnread[item.conversationId] ?: 0) + 1
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.clearUnread(item.conversationId) }
                                    }
                                }
                            }
                            if (!item.outgoing && !com.red.sovereign.core.isPendingDecryptPlaceholder(item.plaintext) && localMessages.effectiveReadReceipts(item.conversationId, SettingsRuntime.current.readReceipts)) RedConnectionService.markRead(context, item.id, item.sequence)
                        } catch (e: Exception) {
                            android.util.Log.e("RedDashboard", "Skipping bad message id=${item.id}", e)
                        }
                    }
        } catch (e: Exception) {
            android.util.Log.e("RedDashboard", "DecryptedMessageBus collector cancelled, will retry on recomposition", e)
        }
    }
    // ملاحظة: `val conversation` معرّف في سطر سابق (ChatHubScreen scope) — لا نعيد حسابه هنا
    // استعادة المسودة + مزامنة السجل: المسودة تُوجَّه لخانتها الصحيحة
    // (خاص/جماعي) ولا تُكتب فوق نص يكتبه المستخدم حاليًا.
    androidx.compose.runtime.LaunchedEffect(target, groupConversationId) {
        val conversationToRestore = groupConversationId ?: target.takeIf(String::isNotBlank)?.let { conversationId(account.redId, it) }
        if (conversationToRestore != null) {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.getDraft(conversationToRestore)
            }
            if (saved != null && saved.text.isNotBlank()) {
                if (groupConversationId != null) {
                    if (groupMessageText.isBlank()) groupMessageText = saved.text
                } else {
                    if (messageText.isBlank()) messageText = saved.text
                }
            }
            repository.getLocalHistory(conversationToRestore).collect { entities ->
                entities.forEach { stored ->
                    if (decrypted.none { it.id == stored.id }) decrypted.add(DecryptedMessage(stored.id, stored.conversationId, stored.senderId, stored.encryptedPlaintext, stored.createdAt, 0, stored.messageType, stored.outgoing))
                }
            }
        }
    }
    // تحميل تفاعلات المحادثة المفتوحة من التخزين المحلي (مشفّر)
    androidx.compose.runtime.LaunchedEffect(target, groupConversationId) {
        val convId = groupConversationId ?: target.takeIf(String::isNotBlank)?.let { conversationId(account.redId, it) }
        if (convId != null) {
            repository.reactionsForConversation(convId).collect { all ->
                reactionsByMessage.clear()
                all.groupBy { it.messageId }.forEach { (msgId, list) -> reactionsByMessage[msgId] = list.sortedBy { it.timestamp } }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (tab == 0) Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (directory.requests.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = AqyalGold)
                            Text(" طلبات الصداقة الواردة", color = AqyalGold, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${directory.requests.size}", color = Color.White, modifier = Modifier.background(AqyalGold, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                        directory.requests.forEach { request ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SovereignAvatar(request.requester.displayName.take(1))
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(request.requester.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        // G3: Red ID الكامل ظاهر (كان مقتطعاً take(12)).
                                        Text("@${request.requester.username} • ${request.requester.redId}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton({ directory.resolve(request, false) }, Modifier.height(38.dp)) { Text("رفض", color = Color.Gray) }
                                    Button({ directory.resolve(request, true) }, Modifier.height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) { Text("قبول", color = Color(0xFF002118)) }
                                }
                            }
                        }
                    }
                }
            }
            if (directory.state is DirectoryState.Message) {
                Card(colors = CardDefaults.cardColors(containerColor = YounesEmerald.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    (directory.state as? DirectoryState.Message)?.let { Text(it.text, color = YounesEmerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp) }
                }
            }
            if (target.isBlank()) {
                if (directory.contacts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الأصدقاء", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text("${directory.contacts.size}", color = Color.White, fontSize = 12.sp, modifier = Modifier.background(AqyalCyanGlow, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                        Spacer(Modifier.weight(1f))
                        TextButton({ showDirectory = true }) { Text("إضافة +", color = AqyalGold, fontSize = 12.sp) }
                    }
                    val sortedContacts = remember(directory.contacts, conversations, directory) {
                        directory.contacts
                            .filter { person -> conversations.none { it.peerId == person.redId && it.archived } }
                            .sortedWith(
                                compareByDescending<PublicRedProfile> { directory.isOnline(it.redId) }
                                    .thenByDescending { conversations.find { c -> c.peerId == it.redId }?.pinned ?: false }
                                    .thenBy { it.displayName }
                            )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sortedContacts, key = { it.redId }) { person ->
                            val online = directory.isOnline(person.redId)
                            Card(
                                Modifier.widthIn(max = 150.dp).clickable { target = person.redId },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        SovereignAvatar(person.displayName.take(1))
                                        if (online) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF00C98C)).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(person.displayName, maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, overflow = TextOverflow.Ellipsis)
                                    Text(if (online) "متصل" else "@${person.username}", color = if (online) YounesEmerald else AqyalCyanGlow, maxLines = 1, fontSize = 10.sp)
                                    IconButton({ selectedContact = person }, Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, "إعدادات الصديق", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth().clickable { showDirectory = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = YounesEmerald)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("بدء محادثة خاصة", fontWeight = FontWeight.SemiBold); Text("ابحث بالاسم الدقيق أو معرّف يونس", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                val activePerson = directory.contacts.find { it.redId == target }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ target = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة لقائمة الدردشات") }
                    SovereignAvatar((activePerson?.displayName ?: target).take(1))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(activePerson?.displayName ?: target, fontWeight = FontWeight.SemiBold)
                        Text(activePerson?.let { val ls = directory.lastSeenLabel(it.redId); ls ?: "@${it.username} · ${it.redId}" } ?: target, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PrivateChatCallActions(
                        onVideoCall = { pendingCallVideo = true; callPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) },
                        onVoiceCall = { pendingCallVideo = false; callPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                        onSearch = { showMessageSearch = true },
                        onMedia = { showMediaGallery = true },
                        onSafety = { safety.open(target) },
                        onProfile = activePerson?.let { person -> { selectedContact = person } }
                    )
                }
            }
            com.red.sovereign.calls.InlineChatCallBar(peerId = target)
            val conversation = remember(account.redId, target) { conversationId(account.redId, target) }
            // P0 (2026-09-14): derivedStateOf بدل remember(decrypted,..) — remember لا يعيد الحساب
            // عند تغيّر محتوى SnapshotStateList (نفس المثيل) فكانت القائمة تتجمد فارغة ولا تظهر
            // الرسائل لا للمرسل ولا للمستقبل. derivedStateOf يتتبع القراءات ويعيد الحساب تلقائياً.
            val conversationMessages by remember(conversation) {
                derivedStateOf { resolveRichMessages(decrypted.filter { it.conversationId == conversation }) }
            }
            androidx.compose.runtime.LaunchedEffect(conversationMessages.lastOrNull()?.id, target) {
                // G3: تمرير آمن موحد — scrollOnce بلا انهيار عند تقلص القائمة أثناء الحذف.
                if (conversationMessages.isNotEmpty()) messagesListState.scrollOnce(conversationMessages.lastIndex)
            }
            val chatWallpaperId = localMessages.conversationWallpaper(conversation)
            val chatWallpaperBrush = when (chatWallpaperId) {
                1 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1A3A5F), Color(0xFF0A1628)))
                2 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF004D3A), Color(0xFF0A1628)))
                3 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3D2E00), Color(0xFF0A1628)))
                4 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2A0A2A), Color(0xFF0A1628)))
                5 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF002F4A), Color(0xFF0A1628)))
                else -> null
            }
            LazyColumn(
                Modifier.weight(1f).then(if (chatWallpaperBrush != null) Modifier.background(chatWallpaperBrush) else Modifier),
                state = messagesListState, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val listScope = this
                if (target.isBlank()) {
                    val groupIds = groups.groups.map(Group::id).toSet()
                    val allConvos = conversations.filter { it.id !in groupIds }
                    val filteredConvos = allConvos
                        .filter { conv ->
                            val name = directory.contacts.firstOrNull { it.redId == conv.peerId }?.displayName ?: conv.peerId
                            (chatSearchQuery.isBlank() || name.contains(chatSearchQuery, ignoreCase = true) || conv.lastMessageText.orEmpty().contains(chatSearchQuery, ignoreCase = true)) &&
                                (!chatUnreadFilter || (chatUnread[conv.id] ?: 0) > 0)
                        }
                        .sortedWith(
                            compareByDescending<com.red.sovereign.core.database.ConversationEntity> { it.pinned }
                                .thenByDescending { it.lastMessageTimestamp }
                        )
                    if (allConvos.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                chatSearchQuery, { chatSearchQuery = it }, Modifier.fillMaxWidth(),
                                placeholder = { Text("بحث في الدردشات…") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, shape = RoundedCornerShape(14.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !chatUnreadFilter, onClick = { chatUnreadFilter = false }, label = { Text("الكل") })
                                FilterChip(selected = chatUnreadFilter, onClick = { chatUnreadFilter = true }, label = { Text("غير المقروء") })
                            }
                        }
                    }
                    if (filteredConvos.isEmpty() && allConvos.isNotEmpty()) item {
                        Text("لا توجد محادثات مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    }
                    items(filteredConvos, key = { it.id }) { conv ->
                        val unread = chatUnread[conv.id] ?: 0
                        var showConvoMenu by remember { mutableStateOf(false) }
                        Card(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = { chatUnread.remove(conv.id); target = conv.peerId; scope.launch { repository.clearUnread(conv.id) } },
                                    onLongClick = { showConvoMenu = true }
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val contact = directory.contacts.firstOrNull { it.redId == conv.peerId }
                            val displayName = contact?.displayName ?: conv.peerId
                            val isOnline = directory.isOnline(conv.peerId)
                            Box {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        SovereignAvatar(displayName.take(1))
                                        if (isOnline) {
                                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF00C98C)).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            if (conv.pinned) { Spacer(Modifier.width(3.dp)); Icon(androidx.compose.material.icons.Icons.Default.Star, "مثبت", tint = Color(0xFFF5C842), modifier = Modifier.size(14.dp)) }
                                            if (conv.mutedUntil > System.currentTimeMillis()) { Spacer(Modifier.width(3.dp)); Icon(androidx.compose.material.icons.Icons.Default.NotificationsOff, "مكتوم", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                                            if (unread > 0) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.FiberManualRecord, "غير مقروء", tint = YounesEmerald, modifier = Modifier.size(10.dp)) }
                                        }
                                        val draft = chatDrafts[conv.id]
                                        Text(
                                            if (draft != null) "مسودة: $draft" else (conv.lastMessageText ?: "لا توجد رسائل"),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (draft != null) AqyalGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (conv.lastMessageTimestamp > 0) Text(dashboardRelativeTime(conv.lastMessageTimestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (unread > 0) {
                                            Spacer(Modifier.height(4.dp))
                                            Surface(shape = RoundedCornerShape(10.dp), color = YounesEmerald) { Text(" $unread ", fontSize = 11.sp, color = Color(0xFF002118), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                        }
                                    }
                                }
                                DropdownMenu(expanded = showConvoMenu, onDismissRequest = { showConvoMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("تحديد كغير مقروء") },
                                        leadingIcon = { Icon(Icons.Default.MarkEmailUnread, null) },
                                        onClick = {
                                            showConvoMenu = false
                                            val lastTs = conv.lastMessageTimestamp.takeIf { it > 0 } ?: System.currentTimeMillis()
                                            chatUnread[conv.id] = (chatUnread[conv.id] ?: 0).coerceAtLeast(1)
                                            scope.launch { repository.setUnreadCount(conv.id, (chatUnread[conv.id] ?: 1).coerceAtLeast(1)) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (conv.pinned) "إلغاء التثبيت" else "تثبيت") },
                                        leadingIcon = { Icon(if (conv.pinned) Icons.Default.StarBorder else Icons.Default.Star, null) },
                                        onClick = {
                                            showConvoMenu = false
                                            scope.launch { repository.setPinned(conv.id, !conv.pinned) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (conv.mutedUntil > System.currentTimeMillis()) "إلغاء الكتم" else "كتم 8 ساعات") },
                                        leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                                        onClick = {
                                            showConvoMenu = false
                                            val mutedUntil = if (conv.mutedUntil > System.currentTimeMillis()) 0L else System.currentTimeMillis() + 8 * 60 * 60 * 1000L
                                            scope.launch { repository.setMutedUntil(conv.id, mutedUntil) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("أرشفة") },
                                        leadingIcon = { Icon(Icons.Default.Archive, null) },
                                        onClick = {
                                            showConvoMenu = false
                                            scope.launch { repository.setArchived(conv.id, true) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("حذف المحادثة", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showConvoMenu = false
                                            scope.launch { repository.deleteConversation(conv.id) }
                                            chatUnread.remove(conv.id)
                                            chatDrafts.remove(conv.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (target.isNotBlank() && conversationMessages.isEmpty()) item {
                    SovereignEmptyConversationState()
                }
                itemsIndexed(conversationMessages, key = { _, it -> it.id }) { index, item ->
                    // فاصل تاريخ بين الأيام (مثل واتساب)
                    val showDate = index == 0 || !dashboardIsSameDay(conversationMessages[index - 1].timestamp, item.timestamp)

                    Column(Modifier.fillMaxWidth()) {
                        if (showDate) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                                    Text(dashboardDateLabel(item.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }


                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.outgoing) Arrangement.End else Arrangement.Start) {
                            if (item.type == "RICH_TEXT" && RichMessage.decode(item.plaintext)?.action == "MESSAGE") {
                                // ✨ استخدام فقاعات الدردشة الفاخرة للرسائل النصية
                                val payload = RichMessage.decode(item.plaintext)
                                LuxuryChatBubble(
                                    message = payload?.text ?: "",
                                    isMe = item.outgoing,
                                    time = dashboardFormatClockTime(item.timestamp),
                                    status = item.status,
                                    onLongClick = { selectedChatMessage = item },
                                    // G3: معرف المرسل في الخاص — اسم جهة الاتصال + Red ID الكامل للوارد.
                                    senderName = if (item.outgoing) "" else (activePerson?.displayName.orEmpty()),
                                    senderRedId = if (item.outgoing) "" else item.senderRedId
                                )
                            } else {
                                // الحفاظ على التصميم القديم للوسائط والمرفقات والاستطلاعات حالياً
                                Card(
                                    Modifier.widthIn(max = 320.dp).combinedClickable(onClick = { messageInfo = item }, onLongClick = { selectedChatMessage = item }),
                                    colors = CardDefaults.cardColors(containerColor = if (item.outgoing) YounesEmerald.copy(alpha = .82f) else AqyalSurfaceRaised.copy(alpha = .94f)),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp, topEnd = 20.dp,
                                        bottomStart = if (item.outgoing) 20.dp else 5.dp,
                                        bottomEnd = if (item.outgoing) 5.dp else 20.dp
                                    )
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        when (item.type) {
                                            "FILE", "IMAGE", "VIDEO", "AUDIO" -> AttachmentMessage(item, attachments)
                                            "VOICE" -> VoiceMessage(item, attachments)
                                            "STICKER" -> StickerMessage(item, attachments)
                                            "RICH_TEXT" -> RichTextMessage(
                                                item,
                                                conversationMessages,
                                                myRedId = account.redId,
                                                onPollVote = { pollId, optionIndex ->
                                                    ChatPollVoteStore.record(pollId, account.redId, optionIndex)
                                                    RedConnectionService.sendPollVote(context, target, conversation, pollId, optionIndex)
                                                }
                                            )
                                            else -> Text(item.plaintext.toString(Charsets.UTF_8), color = if (item.outgoing) Color(0xFF001B14) else Color.White, fontSize = 16.sp)
                                        }
                                        // تفاعلات الإيموجي تحت الرسالة (E2EE)
                                        MessageReactions(
                                            reactions = reactionsByMessage[item.id].orEmpty(),
                                            currentRedId = account.redId,
                                            onToggle = { emoji: String ->
                                                val mine = reactionsByMessage[item.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                                if (mine) RedConnectionService.removeReaction(context, target, conversation, item.id)
                                                else RedConnectionService.sendReaction(context, target, conversation, item.id, emoji)
                                                // تحديث محلي فوري لاستجابة الواجهة قبل وصول الحدث عبر الـ bus
                                                val current = reactionsByMessage[item.id].orEmpty()
                                                val withoutMine = current.filterNot { it.senderId == account.redId }
                                                reactionsByMessage[item.id] = if (mine) withoutMine else withoutMine + com.red.sovereign.core.database.MessageReactionEntity(item.id, conversation, account.redId, emoji, System.currentTimeMillis())
                                            }
                                        )
                                        // 🕐 الوقت + علامات القراءة داخل الفقاعة (نمط واتساب)
                                        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(dashboardFormatClockTime(item.timestamp), fontSize = 10.sp, color = if (item.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (editedMessageIds.containsKey(item.id)) Text("✏️", fontSize = 10.sp)
                                            if (item.outgoing) {
                                                val ticks = when (item.status) {
                                                    "READ" -> "✓✓"
                                                    "DELIVERED" -> "✓✓"
                                                    else -> "✓"
                                                }
                                                Text(ticks, color = if (item.status == "READ") com.red.sovereign.ui.theme.AqyalCyanGlow else Color(0x99001B14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (typingUsers.containsKey(conversation) && target.isNotBlank()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(
                                Modifier.padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = AqyalSurfaceRaised.copy(alpha = .94f)),
                                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp)
                            ) {
                                val lottieComposition by com.airbnb.lottie.compose.rememberLottieComposition(
                                    com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.red.sovereign.R.raw.typing_dots)
                                )
                                com.airbnb.lottie.compose.LottieAnimation(
                                    composition = lottieComposition,
                                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                    modifier = Modifier.width(60.dp).height(30.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (target.isNotBlank()) {
                if (showEmoji) EmojiPicker(onEmoji = { emoji: String -> messageText += emoji })
                if (showStickers && target.matches(RED_ID_PATTERN)) {
                    val stickerTokens = rememberDashboardTokenStore()
                    val stickerExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, exception ->
                        android.util.Log.e("RedDashboard", "Sticker send failed", exception)
                        android.widget.Toast.makeText(context, "فشل إرسال الملصق: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    com.red.sovereign.media.StickerPicker(
                        tokens = stickerTokens,
                        onPickSticker = { sticker ->
                            scope.launch(stickerExceptionHandler) {
                                val mediaApi = com.red.sovereign.media.MediaApi(context, com.red.sovereign.auth.AuthorizedApiClient(stickerTokens))
                                mediaApi.grant(sticker.mediaKey, target)
                                val payload = kotlinx.serialization.json.Json.encodeToString(
                                    com.red.sovereign.media.StickerMessagePayload.serializer(),
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "🎨", sticker.name)
                                )
                                com.red.sovereign.core.RedConnectionService.sendPayload(context, target, conversation, "STICKER", payload.toByteArray(Charsets.UTF_8), UuidV7.next())
                                showStickers = false
                            }
                        }
                    )
                }
                if (showAttachmentSheet) AttachmentSheet(
                    onCamera = {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "latest_photo.jpg")
                        val providerUri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                        cameraPicker.launch(providerUri)
                    },
                    onGallery = { filePicker.launch(arrayOf("image/*", "video/*")) },
                    onDocument = { filePicker.launch(arrayOf("application/pdf", "text/plain", "application/zip", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) },
                    onDismiss = { showAttachmentSheet = false }
                )

                // Mentions @ + Hashtags # autocomplete popup
                val mentionQuery = USERNAME_PARTIAL.find(messageText)?.groupValues?.get(1)
                if (mentionQuery != null && directory.contacts.isNotEmpty()) {
                    val suggestions = directory.contacts.filter { it.username.contains(mentionQuery, ignoreCase = true) || it.displayName.contains(mentionQuery, ignoreCase = true) }.take(3)
                    if (suggestions.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Column {
                                suggestions.forEach { person ->
                                    Row(Modifier.fillMaxWidth().clickable {
                                        messageText = messageText.replace(USERNAME_PARTIAL, "@${person.redId} ")
                                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("@${person.username}", color = YounesEmerald, fontWeight = FontWeight.Bold)
                                        Text(" • ${person.displayName}", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                val hashtagQuery = HASHTAG_AUTOCOMPLETE.find(messageText)?.groupValues?.get(1)
                if (hashtagQuery != null) {
                    val popular = listOf("مهم", "يمن", "تقنية", "عام", "خاص").filter { it.contains(hashtagQuery, ignoreCase = true) }.take(3)
                    if (popular.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                popular.forEach { tag ->
                                    AssistChip(onClick = { messageText = messageText.replace(HASHTAG_AUTOCOMPLETE, "#$tag ") }, label = { Text("#$tag", color = AqyalCyanGlow) })
                                }
                            }
                        }
                    }
                }

                // 💬 شريط الإدخال العصري الذكي
                SovereignChatInputBar(
                    messageText = messageText,
                    onMessageChange = { messageText = it },
                    onSend = {
                        val rich = RichMessage(
                            action = if (editingMessageId != null) "EDIT" else "MESSAGE",
                            text = messageText.trim(), replyTo = replyToMessage?.id, editOf = editingMessageId,
                            expiresAt = disappearingDurationMs?.let { System.currentTimeMillis() + it },
                            mentions = RED_ID_PARTIAL.findAll(messageText).map { it.value }.toList(),
                            hashtags = HASHTAG_PARTIAL.findAll(messageText).map { it.value }.toList(),
                            disappearingMs = disappearingDurationMs
                        )
                        RedConnectionService.sendRichText(context, target, conversation, rich, UuidV7.next())
                        val sentEditId = editingMessageId
                        if (sentEditId != null) editedMessageIds[sentEditId] = true
                        messageText = ""; showEmoji = false; replyToMessage = null; editingMessageId = null
                        // المسودة استُهلكت بالإرسال — تُحذف من Room حتى لا تعود عند إعادة الفتح.
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.deleteDraft(conversation) }
                    },
                    replyPreviewText = replyToMessage?.let { messageDisplayText(it) },
                    editingPreviewText = editingMessageId?.let { id -> conversationMessages.firstOrNull { it.id == id }?.let { messageDisplayText(it) } },
                    onCancelReplyOrEdit = { replyToMessage = null; editingMessageId = null },
                    disappearingMs = disappearingDurationMs,
                    onToggleDisappearing = { showDisappearingDialog = true },
                    onToggleEmoji = { showEmoji = !showEmoji; showStickers = false },
                    onToggleAttachments = { showAttachmentSheet = true },
                    voiceState = voiceMessages.state,
                    voiceMessages = voiceMessages,
                    hasRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                    onVoicePress = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceMessages.start(target, conversation)
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onVoiceRelease = {
                        if (voiceMessages.state is VoiceMessageState.Recording) {
                            voiceMessages.stopAndSend(target, conversation)
                        }
                    },
                    onStopAndPreview = { voiceMessages.stopAndPreview(target, conversation) },
                    onVoiceClick = {
                        if (voiceMessages.state is VoiceMessageState.Recording) {
                            voiceMessages.stopAndPreview(target, conversation)
                        } else if (voiceMessages.state is VoiceMessageState.Preview) {
                            voiceMessages.stopAndSend(target, conversation)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceMessages.start(target, conversation)
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
        if (groupConversationId != null) Column(Modifier.fillMaxSize().padding(14.dp)) {
            val openGroup = groups.groups.firstOrNull { it.id == groupConversationId }
            if (openGroup == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onCreateGroup, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" إنشاء") }
                    OutlinedButton({ showJoinGroup = true }, Modifier.weight(1f)) { Text("انضمام بدعوة") }
                }
                when {
                    groups.state == GroupState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
                    groups.state is GroupState.Error -> (groups.state as? GroupState.Error)?.let { EmptyState(Icons.Default.Groups, "تعذر تحميل المجموعات", it.message) }
                    groups.groups.isEmpty() -> EmptyState(Icons.Default.Groups, "لا توجد مجموعات", "أنشئ مجموعة محلية بأدوار مالك ومسؤول وعضو.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(groups.groups, key = { it.id }) { group ->
                            val lastGroupMsg = decrypted.filter { it.conversationId == group.id }.maxByOrNull { it.timestamp }
                            val groupConvRow = conversations.firstOrNull { it.id == group.id }
                            val unread = groupUnread[group.id] ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { groupUnread.remove(group.id); groupConversationId = group.id; scope.launch { repository.clearUnread(group.id) } }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GroupAvatar(group, groups) // Assumes this uses a proper size like 50.dp

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (lastGroupMsg != null || groupConvRow != null) {
                                            Text(
                                                text = dashboardRelativeTime(lastGroupMsg?.timestamp ?: groupConvRow?.lastMessageTimestamp ?: 0L),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = lastGroupMsg?.let { msg ->
                                                val t = messageDisplayText(msg)
                                                // G3: معرف المرسل الكامل في معاينة القائمة (كان take(8) مبتوراً).
                                                (if (msg.outgoing) "أنت: " else "@" + msg.senderRedId + ": ") + t
                                            } ?: groupConvRow?.lastMessageText ?: group.description.orEmpty().ifBlank { "مجموعة مشفرة بـ Sender Keys" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (unread > 0) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = YounesEmerald,
                                                modifier = Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                                    Text(
                                                        text = "$unread",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF002118),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${group.members.size} عضو",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton({ onManageGroup(group.id) }, modifier = Modifier.padding(start = 4.dp)) {
                                    Icon(Icons.Default.MoreVert, "إدارة المجموعة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // واتساب: ترويسة المجموعة — [رجوع] [أفاتار+اسم+عدد الأعضاء] [📞 صوت] [🎥 فيديو] [⋮]
                // المؤتمرات/المساحات خارج المجموعات تماماً (ميزات مستقلة)
                val waGroupCall = GroupCallRuntime.state
                val waIsActiveForThisGroup = when (waGroupCall) {
                    is GroupCallUiState.Ringing -> waGroupCall.members.isNotEmpty() && GroupCallRuntime.activeGroupId == openGroup.id
                    is GroupCallUiState.Active -> GroupCallRuntime.activeGroupId == openGroup.id
                    is GroupCallUiState.IncomingGroup -> GroupCallRuntime.activeGroupId == openGroup.id || waGroupCall.groupCallId.contains(openGroup.id.take(8))
                    else -> false
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ groupConversationId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة للمجموعات") }
                        GroupAvatar(openGroup, groups)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(openGroup.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val statusText = when {
                                waGroupCall is GroupCallUiState.Active && GroupCallRuntime.activeGroupId == openGroup.id -> if (waGroupCall.isVideo) "مكالمة فيديو جماعية جارية" else "مكالمة صوتية جماعية جارية"
                                waGroupCall is GroupCallUiState.Ringing && GroupCallRuntime.activeGroupId == openGroup.id -> "ترن الأعضاء..."
                                waGroupCall is GroupCallUiState.IncomingGroup -> "مكالمة جماعية واردة"
                                else -> "${openGroup.members.size} أعضاء · مشفّرة E2EE"
                            }
                            Text(
                                statusText,
                                color = if (waIsActiveForThisGroup) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // 📞 واتساب النقي: فيديو + صوت فقط — لا مساحات ولا مؤتمرات هنا
                        GroupChatCallActions(
                            onVideoCall = { pendingGroupVideo = true; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) },
                            onVoiceCall = { pendingGroupVideo = false; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            onInfo = { onManageGroup(openGroup.id) },
                            onSearch = { showMessageSearch = true },
                            onMedia = { showGroupMediaGallery = true },
                            onAvatar = { groupAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                            onPoll = { showGroupPollDialog = true },
                            onLeave = { groups.leave(openGroup) { groupConversationId = null } },
                            muted = groupMuted,
                            onToggleMute = {
                                val newMuted = !groupMuted
                                localMessages.setConversationPreference(openGroup.id, "muted_until", if (newMuted) System.currentTimeMillis() + 8 * 60 * 60 * 1000L else 0)
                                groupMuted = newMuted
                            }
                        )
                    }
                }
                // شريط واتساب لمكالمة المجموعة الجارية — انضمام/عودة
                if (waIsActiveForThisGroup) {
                    val isVideoActive = (waGroupCall as? GroupCallUiState.Active)?.isVideo == true || (waGroupCall as? GroupCallUiState.Ringing)?.isVideo == true || (waGroupCall as? GroupCallUiState.IncomingGroup)?.isVideo == true
                    val count = when (waGroupCall) {
                        is GroupCallUiState.Active -> waGroupCall.members.count { it.status == GroupCallMemberStatus.JOINED } + 1
                        is GroupCallUiState.Ringing -> waGroupCall.members.size
                        is GroupCallUiState.IncomingGroup -> waGroupCall.otherMembers.size + 1
                        else -> 0
                    }
                    val inCall = waGroupCall is GroupCallUiState.Active
                    WhatsAppGroupCallBanner(
                        isVideo = isVideoActive,
                        participantCount = count,
                        isInCall = inCall,
                        groupName = openGroup.name,
                        onJoinOrReturn = {
                            when (waGroupCall) {
                                is GroupCallUiState.IncomingGroup -> GroupCallService.accept(context, waGroupCall.groupCallId, account.redId, waGroupCall.isVideo)
                                is GroupCallUiState.Ringing -> {} // المضيف بالفعل في المكالمة
                                else -> {} // Active: العودة عبر Overlay
                            }
                        }
                    )
                }
                // كل الأنواع (GROUP_MESSAGE/RICH_TEXT/IMAGE/VIDEO/AUDIO/VOICE/FILE/STICKER) —
                // وسائط المجموعة تُشفَّر بـ Sender Keys وتصل بنوعها الأصلي ولا يجوز استبعادها.
                // P0 (2026-09-14): نفس علة remember(decrypted,..) في الخاص — تجمّد قائمة المجموعة.
                // derivedStateOf يعيد الحساب عند كل إضافة/تعديل في decrypted.
                val groupMessages by remember(openGroup.id) {
                    derivedStateOf { resolveRichMessages(decrypted.filter { it.conversationId == openGroup.id }) }
                }
                androidx.compose.runtime.LaunchedEffect(groupMessages.lastOrNull()?.id, openGroup.id) {
                    // G3: تمرير آمن موحد بلا انهيار عند تقلص القائمة.
                    if (groupMessages.isNotEmpty()) groupListState.scrollOnce(groupMessages.lastIndex)
                }
                LazyColumn(Modifier.weight(1f), state = groupListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listScope = this
                    if (groupPinnedMessages.isNotEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = 0.08f))) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                                        Text(" رسائل مثبتة (${groupPinnedMessages.size})", color = AqyalGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    groupPinnedMessages.values.forEach { pm ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(messageDisplayText(pm), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                            IconButton({ groupPinnedMessages.remove(pm.id) }) { Icon(Icons.Default.Close, "إلغاء تثبيت", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (groupMessages.isEmpty()) item { Text("محادثة جماعية مشفرة بـSender Keys. يتغير المفتاح تلقائيًا عند تغير العضوية.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                itemsIndexed(groupMessages, key = { _, it -> it.id }) { index, message ->
                    val showDate = index == 0 || !dashboardIsSameDay(groupMessages[index - 1].timestamp, message.timestamp)

                    Column(Modifier.fillMaxWidth()) {
                        if (showDate) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                                    Text(dashboardDateLabel(message.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                            if (message.type == "RICH_TEXT" && RichMessage.decode(message.plaintext)?.action == "MESSAGE") {
                                val payload = RichMessage.decode(message.plaintext)
                                // G3: معرف المرسل في المجموعة — اسم العضو + Red ID الكامل للوارد.
                                val groupSenderName = if (message.outgoing) "" else (
                                    openGroup.members.firstOrNull { it.redId == message.senderRedId }?.username?.takeIf { it.isNotBlank() }
                                        ?: directory.contacts.firstOrNull { it.redId == message.senderRedId }?.displayName.orEmpty()
                                )
                                LuxuryChatBubble(
                                    message = payload?.text ?: "",
                                    isMe = message.outgoing,
                                    time = dashboardFormatClockTime(message.timestamp),
                                    status = message.status,
                                    onLongClick = { selectedChatMessage = message },
                                    senderName = groupSenderName,
                                    senderRedId = if (message.outgoing) "" else message.senderRedId
                                )
                            } else {
                                Card(
                                    Modifier.widthIn(max = 320.dp).combinedClickable(onClick = { groupReplyToMessage = message }, onLongClick = { selectedChatMessage = message }),
                                    colors = CardDefaults.cardColors(containerColor = if (message.outgoing) YounesEmerald.copy(alpha = .82f) else MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp, topEnd = 20.dp,
                                        bottomStart = if (message.outgoing) 20.dp else 5.dp,
                                        bottomEnd = if (message.outgoing) 5.dp else 20.dp
                                    )
                                ) {
                                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    if (!message.outgoing) {
                                        // ألوان هادئة ومتناسقة مع الهوية (لا مهرجان ألوان)
                                        val nameColors = listOf(
                                            Color(0xFF6FD8B0), Color(0xFF7FB5E0), Color(0xFFF0C674), Color(0xFFC9A7E8),
                                            Color(0xFF8FC7E8), Color(0xFFB5D8A0), Color(0xFFE0B8A0)
                                        )
                                        val colorIndex = kotlin.math.abs(message.senderRedId.hashCode()) % nameColors.size
                                        // G3: اسم + Red ID ظاهر — الاسم من عضوية المجموعة أو جهات الاتصال، وRed ID الكامل تحته.
                                        val memberName = openGroup.members.firstOrNull { it.redId == message.senderRedId }?.username?.takeIf { it.isNotBlank() }
                                            ?: directory.contacts.firstOrNull { it.redId == message.senderRedId }?.displayName.orEmpty()
                                        Text(
                                            memberName.ifBlank { message.senderRedId },
                                            color = nameColors[colorIndex], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (memberName.isNotBlank()) {
                                            Text(
                                                message.senderRedId,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        } else {
                                            Spacer(Modifier.height(2.dp))
                                        }
                                    }
                                    when (message.type) {
                                        "RICH_TEXT" -> RichTextMessage(
                                            message,
                                            groupMessages,
                                            myRedId = account.redId,
                                            onPollVote = { pollId, optionIndex ->
                                                ChatPollVoteStore.record(pollId, account.redId, optionIndex)
                                                RedConnectionService.sendGroupPollVote(context, openGroup, pollId, optionIndex)
                                            }
                                        )
                                        "FILE", "IMAGE", "VIDEO", "AUDIO" -> AttachmentMessage(message, attachments)
                                        "VOICE" -> VoiceMessage(message, attachments)
                                        "STICKER" -> StickerMessage(message, attachments)
                                        "GROUP_MESSAGE" -> {
                                            val text = message.plaintext.toString(Charsets.UTF_8)
                                            when {
                                                runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.VoiceManifest>(text) }.isSuccess -> VoiceMessage(message, attachments)
                                                runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.AttachmentManifest>(text) }.isSuccess -> AttachmentMessage(message, attachments)
                                                runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(text) }.isSuccess -> StickerMessage(message, attachments)
                                                else -> Text(text, color = if (message.outgoing) Color(0xFF002118) else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                            }
                                        }
                                        else -> Text(message.plaintext.toString(Charsets.UTF_8), color = if (message.outgoing) Color(0xFF002118) else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                    }
                                    // تفاعلات الإيموجي تحت رسالة المجموعة (E2EE بـ Sender Keys)
                                    MessageReactions(
                                        reactions = reactionsByMessage[message.id].orEmpty(),
                                        currentRedId = account.redId,
                                        onToggle = { emoji: String ->
                                            val mine = reactionsByMessage[message.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                            if (mine) RedConnectionService.removeGroupReaction(context, openGroup, message.id)
                                            else RedConnectionService.sendGroupReaction(context, openGroup, message.id, emoji)
                                            val current = reactionsByMessage[message.id].orEmpty()
                                            val withoutMine = current.filterNot { it.senderId == account.redId }
                                            reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + com.red.sovereign.core.database.MessageReactionEntity(message.id, openGroup.id, account.redId, emoji, System.currentTimeMillis())
                                        }
                                    )
                                    // 🕐 الوقت داخل الفقاعة (نمط واتساب)
                                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(dashboardFormatClockTime(message.timestamp), fontSize = 10.sp, color = if (message.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (editedMessageIds.containsKey(message.id)) Text("✏️", fontSize = 10.sp)
                                        if (message.outgoing) {
                                            val ticks = when (message.status) {
                                                "READ" -> "✓✓"
                                                "DELIVERED" -> "✓✓"
                                                else -> "✓"
                                            }
                                            Text(ticks, color = if (message.status == "READ") com.red.sovereign.ui.theme.AqyalCyanGlow else Color(0x99001B14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                }
                // 📝 مؤشر كتابة جماعي (الخادم يبثه للأعضاء — لا يظهر لكاتب الرسالة نفسه)
                if (typingUsers.containsKey(openGroup.id)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(
                                Modifier.padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp)
                            ) {
                                val lottieComposition by com.airbnb.lottie.compose.rememberLottieComposition(
                                    com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.red.sovereign.R.raw.typing_dots)
                                )
                                com.airbnb.lottie.compose.LottieAnimation(
                                    composition = lottieComposition,
                                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                    modifier = Modifier.width(60.dp).height(30.dp)
                                )
                            }
                        }
                }
                if (showGroupEmoji) EmojiPicker(onEmoji = { emoji: String -> groupMessageText += emoji })
                if (showGroupStickers) {
                    val groupStickerTokens = rememberDashboardTokenStore()
                    com.red.sovereign.media.StickerPicker(
                        tokens = groupStickerTokens,
                        onPickSticker = { sticker ->
                            scope.launch {
                                val mediaApi = com.red.sovereign.media.MediaApi(context, com.red.sovereign.auth.AuthorizedApiClient(groupStickerTokens))
                                mediaApi.grant(sticker.mediaKey, openGroup.id)
                                val payload = kotlinx.serialization.json.Json.encodeToString(
                                    com.red.sovereign.media.StickerMessagePayload.serializer(),
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "🎨", sticker.name)
                                )
                                RedConnectionService.sendGroupText(context, openGroup, payload, UuidV7.next())
                                showGroupStickers = false
                            }
                        }
                    )
                }
                if (showGroupAttachmentSheet) AttachmentSheet(
                    onCamera = {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "latest_photo.jpg")
                        val providerUri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                        groupCameraPicker.launch(providerUri)
                    },
                    onGallery = { groupFilePicker.launch(arrayOf("image/*", "video/*")) },
                    onDocument = { groupFilePicker.launch(arrayOf("application/pdf", "text/plain", "application/zip", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) },
                    onDismiss = { showGroupAttachmentSheet = false }
                )

                // 💬 شريط الإدخال العصري الذكي للمجموعة
                SovereignChatInputBar(
                    messageText = groupMessageText,
                    onMessageChange = { groupMessageText = it },
                    onSend = {
                        val rich = RichMessage(
                            action = if (groupEditingMessageId != null) "EDIT" else "MESSAGE",
                            text = groupMessageText.trim(), replyTo = groupReplyToMessage?.id, editOf = groupEditingMessageId,
                            expiresAt = groupDisappearingMs?.let { System.currentTimeMillis() + it },
                            mentions = RED_ID_PARTIAL.findAll(groupMessageText).map { it.value }.toList(),
                            hashtags = HASHTAG_PARTIAL.findAll(groupMessageText).map { it.value }.toList(),
                            disappearingMs = groupDisappearingMs
                        )
                        RedConnectionService.sendGroupRichText(context, openGroup, rich, UuidV7.next())
                        val sentGroupEditId = groupEditingMessageId
                        if (sentGroupEditId != null) editedMessageIds[sentGroupEditId] = true
                        groupMessageText = ""; groupReplyToMessage = null; groupEditingMessageId = null; showGroupEmoji = false
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) { repository.deleteDraft(openGroup.id) }
                    },
                    replyPreviewText = groupReplyToMessage?.let { "رد على ${if (it.outgoing) "نفسك" else it.senderRedId}: " + messageDisplayText(it) },
                    editingPreviewText = groupEditingMessageId?.let { id -> groupMessages.firstOrNull { it.id == id }?.let { messageDisplayText(it) } },
                    onCancelReplyOrEdit = { groupReplyToMessage = null; groupEditingMessageId = null },
                    disappearingMs = groupDisappearingMs,
                    onToggleDisappearing = { showGroupDisappearingDialog = true },
                    onToggleEmoji = { showGroupEmoji = !showGroupEmoji; showGroupStickers = false },
                    onToggleAttachments = { showGroupAttachmentSheet = true },
                    voiceState = voiceMessages.state,
                    voiceMessages = voiceMessages,
                    hasRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                    onVoicePress = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceMessages.startForGroup(openGroup)
                        } else {
                            groupVoiceMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onVoiceRelease = { voiceMessages.stopAndSendToGroup(openGroup) },
                    onStopAndPreview = { voiceMessages.stopAndPreview() },
                    onVoiceClick = {
                        if (voiceMessages.state is VoiceMessageState.Recording) {
                            voiceMessages.stopAndSendToGroup(openGroup)
                        } else if (voiceMessages.state is VoiceMessageState.Preview) {
                            voiceMessages.stopAndSendToGroup(openGroup)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceMessages.startForGroup(openGroup)
                        } else {
                            groupVoiceMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    placeholderText = if (groupEditingMessageId != null) "تعديل الرسالة…" else if (groupReplyToMessage != null) "الرد على رسالة…" else "رسالة جماعية مشفرة…"
                )
            }
        }
    }
    when (val safetyState = safety.state) {
        SafetyState.Closed -> Unit
        is SafetyState.Loading -> AlertDialog(onDismissRequest = safety::close, title = { Text("رمز الأمان") }, text = { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }, confirmButton = { TextButton(safety::close) { Text("إلغاء") } })
        is SafetyState.Error -> AlertDialog(onDismissRequest = safety::close, title = { Text("تعذر التحقق") }, text = { Text(safetyState.message) }, confirmButton = { TextButton(safety::close) { Text("إغلاق") } })
        is SafetyState.Ready -> if (showSafetyScanner) AlertDialog(
            onDismissRequest = { showSafetyScanner = false },
            title = { Text("امسح رمز الطرف الآخر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp))) {
                        SafetyQrScanner(onCode = { safety.verifyScanned(it); showSafetyScanner = false })
                    }
                    Text("تتم المعالجة على الجهاز فقط، ولا تُرفع صور الكاميرا إلى الخادم.", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton({ showSafetyScanner = false }) { Text("إلغاء") } }
        ) else AlertDialog(
            onDismissRequest = { safety.clearScanError(); safety.close() },
            title = { Text(if (safetyState.verified) "تم التحقق من الهوية" else "مقارنة رمز الأمان") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(safetyState.qr, "QR لرمز الأمان", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Text(safetyState.number, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = AqyalGold)
                Text("الجهاز ${safetyState.deviceId} · ${safetyState.fingerprint.chunked(8).joinToString(" ")}", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                safetyState.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center) }
                Text("امسح رمز الطرف الآخر وجهًا لوجه، أو قارن الرقم عبر قناة موثوقة مستقلة.", fontSize = 11.sp, textAlign = TextAlign.Center)
                if (!safetyState.verified) OutlinedButton({
                    safety.clearScanError()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showSafetyScanner = true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Text(" مسح رمز الطرف الآخر") }
            } },
            confirmButton = { if (!safetyState.verified) Button(safety::markVerified) { Text("الأرقام متطابقة يدويًا") } else TextButton(safety::close) { Text("تم") } },
            dismissButton = { if (!safetyState.verified) TextButton(safety::close) { Text("إلغاء") } }
        )
    }
    selectedChatMessage?.let { message ->
        val payload = if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext) else null
        val isGroupMsg = message.conversationId.length > 32
        ModalBottomSheet(
            onDismissRequest = { selectedChatMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // المعاينة: الرسالة المحددة
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.outgoing) "أنت" else (if (isGroupMsg) message.senderRedId else "المرسل"), color = YounesEmerald, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(messageDisplayText(message), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }

                // تفاعل سريع بالإيموجي — أعلى القائمة (E2EE)
                ReactionEmojiBar(onPick = { emoji: String ->
                    val convId = message.conversationId
                    val mine = reactionsByMessage[message.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                    if (isGroupMsg) {
                        val grp = groups.groups.firstOrNull { it.id == convId }
                        if (grp != null) {
                            if (mine) RedConnectionService.removeGroupReaction(context, grp, message.id)
                            else RedConnectionService.sendGroupReaction(context, grp, message.id, emoji)
                        }
                    } else {
                        if (mine) RedConnectionService.removeReaction(context, target, convId, message.id)
                        else RedConnectionService.sendReaction(context, target, convId, message.id, emoji)
                    }
                    // تحديث محلي فوري
                    val current = reactionsByMessage[message.id].orEmpty()
                    val withoutMine = current.filterNot { it.senderId == account.redId }
                    reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + MessageReactionEntity(message.id, convId, account.redId, emoji, System.currentTimeMillis())
                    selectedChatMessage = null
                })

                MessageActionRow(Icons.Default.Quickreply, "الرد", "رد على هذه الرسالة") {
                    if (isGroupMsg) groupReplyToMessage = message else replyToMessage = message
                    selectedChatMessage = null
                }
                MessageActionRow(
                    if (starredMessageIds.contains(message.id)) Icons.Default.Star else Icons.Default.StarBorder,
                    if (starredMessageIds.contains(message.id)) "إلغاء التعليمة" else "تعليم الرسالة",
                    "حفظ هذه الرسالة في قائمة المُعلَّمة"
                ) {
                    val msgText = messageDisplayText(message)
                    scope.launch {
                        if (starredMessageIds.contains(message.id)) {
                            repository.unstarMessage(message.id)
                            starredMessageIds.remove(message.id)
                        } else {
                            repository.starMessage(message.id, message.conversationId, message.senderRedId, msgText, message.type)
                            starredMessageIds.add(message.id)
                        }
                    }
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Forward, "إعادة توجيه", "أرسلها إلى جهة أخرى") {
                    pendingForwardMessage = message; showDirectory = true; selectedChatMessage = null
                }
                val messageTextForAction = messageDisplayText(message)
                if (messageTextForAction.isNotBlank()) {
                    MessageActionRow(Icons.Default.ContentCopy, "نسخ", "انسخ النص") {
                        val ctx = context
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: run {
                            android.widget.Toast.makeText(ctx, "الحافظة غير متاحة", android.widget.Toast.LENGTH_SHORT).show()
                            return@MessageActionRow
                        }
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("رسالة", messageTextForAction))
                        selectedChatMessage = null
                    }
                    MessageActionRow(Icons.Default.Share, "مشاركة", "شارك عبر تطبيقات أخرى") {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, messageTextForAction)
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الرسالة").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing && message.type == "RICH_TEXT" && payload?.action == "MESSAGE") {
                    MessageActionRow(Icons.Default.Edit, "تعديل", "عدّل النص المرسل") {
                        if (isGroupMsg) {
                            groupEditingMessageId = message.id; groupMessageText = payload.text
                        } else {
                            editingMessageId = message.id; messageText = payload.text
                        }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing) {
                    MessageActionRow(Icons.Default.Delete, "حذف لدى الجميع", "احذف الرسالة لدى الكل") {
                        if (isGroupMsg) {
                            val grp = groups.groups.firstOrNull { it.id == message.conversationId }
                            if (grp != null) RedConnectionService.sendGroupRichText(context, grp, RichMessage(action = "DELETE", deleteOf = message.id))
                        } else {
                            RedConnectionService.sendRichText(context, target, message.conversationId, RichMessage(action = "DELETE", deleteOf = message.id))
                        }
                        // تطبيق محلي فوري: الخادم لا يردّد أمر الحذف إلى نفس الجلسة التي أرسلته
                        scope.launch {
                            repository.deleteLocalMessage(message.id)
                            repository.deleteReactionsForMessage(message.id)
                        }
                        reactionsByMessage.remove(message.id)
                        editedMessageIds.remove(message.id)
                        decrypted.removeAll { it.id == message.id }
                        selectedChatMessage = null
                    }
                }
                MessageActionRow(Icons.Default.Delete, "حذف لديّ", "احذفها من هذا الجهاز فقط") {
                    scope.launch {
                        repository.deleteLocalMessage(message.id)
                        repository.deleteReactionsForMessage(message.id)
                    }
                    reactionsByMessage.remove(message.id)
                    editedMessageIds.remove(message.id)
                    groupPinnedMessages.remove(message.id)
                    decrypted.removeAll { it.id == message.id }
                    selectedChatMessage = null
                }
                if (isGroupMsg) {
                    MessageActionRow(if (groupPinnedMessages.containsKey(message.id)) Icons.Default.Star else Icons.Default.StarBorder, if (groupPinnedMessages.containsKey(message.id)) "إلغاء التثبيت" else "تثبيت", "تثبيت هذه الرسالة أعلى المجموعة") {
                        if (groupPinnedMessages.containsKey(message.id)) {
                            groupPinnedMessages.remove(message.id)
                            scope.launch { pinApi.unpin(message.id) }
                        } else {
                            groupPinnedMessages[message.id] = message
                            scope.launch { pinApi.pin(message.id, groupId = message.conversationId) }
                        }
                        selectedChatMessage = null
                    }
                }
                MessageActionRow(Icons.Default.NotificationsOff, "كتم الإشعارات", "كتم هذه المحادثة 8 ساعات") {
                    val convId = if (isGroupMsg) message.conversationId else conversationId(account.redId, target)
                    val muted = localMessages.conversationPreference(convId).third > System.currentTimeMillis()
                    localMessages.setConversationPreference(convId, "muted_until", if (muted) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L)
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Info, "معلومات الرسالة", "التفاصيل والوقت والحالة") {
                    messageInfo = message; selectedChatMessage = null
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("1ساعة" to 3_600_000L, "يوم" to 86_400_000L, "أسبوع" to 604_800_000L, "90يوم" to 7_776_000_000L, "إيقاف" to 0L).forEach { (label, ms) ->
                        OutlinedButton({
                            val value = if (ms > 0) ms else null
                            if (isGroupMsg) {
                                groupDisappearingMs = value
                                localMessages.setConversationDisappearingDuration(message.conversationId, value)
                            } else {
                                disappearingDurationMs = value
                                val convId = message.conversationId.takeIf { it.isNotBlank() }
                                    ?: conversationId(account.redId, target)
                                localMessages.setConversationDisappearingDuration(convId, value)
                            }
                            selectedChatMessage = null
                        }, Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                    }
                }
                TextButton({ selectedChatMessage = null }, Modifier.align(Alignment.CenterHorizontally)) { Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    selectedContact?.let { person ->
        val conversationKey = remember(person.redId) { conversationId(account.redId, person.redId) }
        val preference = localMessages.conversationPreference(conversationKey)
        var editingName by remember(person.redId) { mutableStateOf(localMessages.conversationCustomName(conversationKey) ?: person.displayName) }
        var selectedWallpaper by remember(person.redId) { mutableStateOf(localMessages.conversationWallpaper(conversationKey)) }
        ModalBottomSheet(
            onDismissRequest = { selectedContact = null; reportDetails = "" },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // رأس الصديق
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SovereignAvatar(person.displayName.take(1))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(localMessages.conversationCustomName(conversationKey) ?: person.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("@${person.username} • ${person.redId}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // إعادة تسمية المحادثة
                OutlinedTextField(editingName, { editingName = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("اسم المحادثة (تجاوز)") }, singleLine = true)
                Button({
                    localMessages.setConversationCustomName(conversationKey, editingName.trim())
                    editingName = editingName.trim()
                }, Modifier.fillMaxWidth(), enabled = editingName.isNotBlank() && editingName != person.displayName) { Text("حفظ الاسم") }

                // الخلفية — اختيار تدرج لوني
                Text("خلفية المحادثة", color = YounesEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                val wallpapers = listOf(0, 1, 2, 3, 4, 5)
                val wpColors = listOf(
                    Color(0xFF0A1628), Color(0xFF1A3A5F), Color(0xFF004D3A), Color(0xFF3D2E00), Color(0xFF2A0A2A), Color(0xFF002F4A)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(wallpapers, key = { it }) { id ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable { selectedWallpaper = id; localMessages.setConversationWallpaper(conversationKey, id) },
                                shape = RoundedCornerShape(14.dp),
                                color = wpColors[id]
                            ) { if (selectedWallpaper == id) Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White) } }
                        }
                    }
                }

                // تثبيت / أرشفة / كتم
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "pinned", if (preference.first) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.first) "إلغاء التثبيت" else "تثبيت", fontSize = 12.sp) }
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "archived", if (preference.second) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.second) "إلغاء الأرشفة" else "أرشفة", fontSize = 12.sp) }
                }
                OutlinedButton({ localMessages.setConversationPreference(conversationKey, "muted_until", if (preference.third > System.currentTimeMillis()) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L) }, Modifier.fillMaxWidth()) { Text(if (preference.third > System.currentTimeMillis()) "إلغاء الكتم" else "كتم 8 ساعات") }
                // إيصالات القراءة لهذه المحادثة: عام / تشغيل / إيقاف (تجاوز الإعداد العام)
                var receiptsMode by remember(person.redId) { mutableStateOf(localMessages.conversationReadReceipts(conversationKey)) }
                Text("إيصالات القراءة", color = YounesEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "عام", 1 to "تشغيل", 2 to "إيقاف").forEach { (mode, label) ->
                        OutlinedButton({
                            receiptsMode = mode
                            localMessages.setConversationReadReceipts(conversationKey, mode)
                        }, Modifier.weight(1f)) {
                            Text(label, fontSize = 12.sp, fontWeight = if (receiptsMode == mode) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                OutlinedButton({ safety.open(person.redId); selectedContact = null }, Modifier.fillMaxWidth()) { Text("رمز الأمان والتحقق") }

                // الحظر / فك الحظر
                val isBlocked = person.redId in blockedIds
                Button({
                    if (isBlocked) directory.unblock(person) else directory.block(person)
                    selectedContact = null
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) YounesEmerald else MaterialTheme.colorScheme.error)) {
                    Text(if (isBlocked) "فك الحظر" else "حظر المستخدم", color = if (isBlocked) Color(0xFF002118) else Color.White)
                }

                // إزالة / بلاغ
                OutlinedButton({ directory.remove(person); selectedContact = null }, Modifier.fillMaxWidth()) { Text("إزالة من الأصدقاء") }
                OutlinedTextField(reportDetails, { reportDetails = it }, Modifier.fillMaxWidth(), label = { Text("تفاصيل بلاغ اختياري") }, maxLines = 2)
                OutlinedButton({ directory.report(person, "SPAM", reportDetails); reportDetails = "" }, Modifier.fillMaxWidth()) { Text("إبلاغ عن إزعاج/احتيال") }
            }
        }
    }
    val selectedGroup = groups.groups.firstOrNull { it.id == manageGroupId }
    if (selectedGroup != null) {
        val myRole = selectedGroup.members.firstOrNull { it.redId == account.redId }?.role
        val canManage = myRole == "OWNER" || myRole == "ADMIN"
        AlertDialog(
            onDismissRequest = { manageGroupId = null },
            title = { Text(selectedGroup.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedGroup.description.orEmpty(), color = Color.Gray)
                    LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectedGroup.members, key = { it.id }) { member ->
                            val manageable = canManage && member.role != "OWNER" && member.redId != account.redId && (myRole == "OWNER" || member.role == "MEMBER")
                            Row(Modifier.fillMaxWidth().clickable(enabled = manageable) { selectedGroupMember = member }, verticalAlignment = Alignment.CenterVertically) {
                                SovereignAvatar(member.username.take(1)); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("@${member.username}"); Text(member.redId, color = AqyalCyanGlow, fontSize = 10.sp) }
                                // شارة دور عرض فقط (كانت AssistChip معطلة) — نص ثابت.
                                Text(dashboardGroupRoleLabel(member.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (manageable) Icon(Icons.Default.MoreVert, "إدارة العضو", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (canManage) {
                        OutlinedButton({ groupAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, Modifier.fillMaxWidth()) { Text("تغيير صورة المجموعة") }
                        OutlinedTextField(memberRedId, { memberRedId = YounesId.normalizeInput(it) }, Modifier.fillMaxWidth(), label = { Text("إضافة عضو بواسطة معرّف يونس") }, placeholder = { Text(YounesId.PLACEHOLDER) }, singleLine = true)
                        Button({ groups.addMember(selectedGroup, memberRedId) { memberRedId = "" } }, Modifier.fillMaxWidth(), enabled = memberRedId.matches(RED_ID_PATTERN) && groups.state != GroupState.Saving) { Text("إضافة عضو") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ groups.createInvite(selectedGroup) }, Modifier.weight(1f)) { Text("رابط دعوة") }
                            OutlinedButton({ groups.loadJoinRequests(selectedGroup) }, Modifier.weight(1f)) { Text("طلبات الانضمام") }
                        }
                        groups.latestInvite?.let { invite ->
                            val sysClipboard = context
                                .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            Card { Column(Modifier.padding(10.dp)) {
                                Text("دعوة صالحة حتى ${invite.expiresAt}", style = MaterialTheme.typography.bodySmall)
                                Text(invite.token, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AqyalCyanGlow)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton({
                                        val cm = sysClipboard ?: run {
                                            android.widget.Toast.makeText(context, "الحافظة غير متاحة", android.widget.Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("دعوة", invite.token))
                                    }) { Text("نسخ رمز الدعوة") }
                                    TextButton({ groups.createInvite(selectedGroup) }) { Text("رمز جديد") }
                                    TextButton({ groups.clearInvite() }) { Text("إخفاء") }
                                }
                            } }
                        }
                        groups.joinRequests.forEach { request -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("@${request.username}", Modifier.weight(1f)); TextButton({ groups.resolveJoin(selectedGroup, request, false) }) { Text("رفض") }; Button({ groups.resolveJoin(selectedGroup, request, true) }) { Text("قبول") } } }
                    }
                }
            },
            confirmButton = { TextButton({ manageGroupId = null }) { Text("إغلاق") } },
            dismissButton = {
                if (myRole == "OWNER") TextButton({ deleteGroupId = selectedGroup.id }) { Text("حذف المجموعة", color = MaterialTheme.colorScheme.error) }
                else TextButton({ groups.leave(selectedGroup) { manageGroupId = null; groupConversationId = null } }) { Text("مغادرة", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    val managedMember = selectedGroupMember
    if (selectedGroup != null && managedMember != null) AlertDialog(
        onDismissRequest = { selectedGroupMember = null },
        title = { Text("إدارة @${managedMember.username}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(managedMember.redId, color = AqyalCyanGlow)
            if (selectedGroup.members.firstOrNull { it.redId == account.redId }?.role == "OWNER") {
                // دورة الأدوار: عضو → مراقب → مشرف → عضو (الخادم يدعم MODERATOR فعلياً).
                val nextRole = when (managedMember.role) {
                    "MEMBER" -> "MODERATOR"
                    "MODERATOR" -> "ADMIN"
                    else -> "MEMBER"
                }
                val nextLabel = when (managedMember.role) {
                    "MEMBER" -> "ترقيته إلى مراقب"
                    "MODERATOR" -> "ترقيته إلى مسؤول"
                    else -> "إرجاعه إلى عضو"
                }
                OutlinedButton({ groups.updateRole(selectedGroup, managedMember, nextRole); selectedGroupMember = null }, Modifier.fillMaxWidth()) {
                    Text(nextLabel)
                }
                OutlinedButton({ groups.transferOwnership(selectedGroup, managedMember) { selectedGroupMember = null; manageGroupId = null } }, Modifier.fillMaxWidth()) { Text("نقل ملكية المجموعة إليه") }
            }
            Button({ groups.removeMember(selectedGroup, managedMember); selectedGroupMember = null }, Modifier.fillMaxWidth()) { Text("إزالة من المجموعة") }
            Text("تغيير العضوية يجب أن يدور Sender Key عندما تكتمل محادثة المجموعات المشفرة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ selectedGroupMember = null }) { Text("إغلاق") } }
    )
    groups.groups.firstOrNull { it.id == deleteGroupId }?.let { deleting ->
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("حذف ${deleting.name} نهائيًا؟") },
            text = { Text("سيُحذف سجل المجموعة وعضويتها من الخادم. لا يمكن التراجع عن العملية.") },
            confirmButton = { Button({ groups.deleteGroup(deleting) { deleteGroupId = null; manageGroupId = null; groupConversationId = null } }) { Text("حذف نهائي") } },
            dismissButton = { TextButton({ deleteGroupId = null }) { Text("إلغاء") } }
        )
    }
    if (showGroupPollDialog) {
        val openGroupForPoll = groups.groups.firstOrNull { it.id == groupConversationId }
        // تفكيك 2026-09-10: الحوار في DashboardPoll.kt — هنا تمرير فقط.
        DashboardGroupPollDialog(
            openGroup = openGroupForPoll,
            question = groupPollQuestion,
            onQuestionChange = { groupPollQuestion = it },
            options = groupPollOptions,
            onOptionsChange = { groupPollOptions = it },
            onDismiss = { showGroupPollDialog = false },
            onSendPoll = { q, opts ->
                val poll = com.red.sovereign.core.InlinePoll(
                    question = q,
                    options = opts,
                    pollId = "poll-${System.currentTimeMillis()}"
                )
                val rich = RichMessage(text = "", poll = poll)
                openGroupForPoll?.let { RedConnectionService.sendGroupRichText(context, it, rich, UuidV7.next()) }
                showGroupPollDialog = false
                groupPollQuestion = ""
                groupPollOptions = listOf("", "")
            }
        )
    }
    if (showDisappearingDialog) AlertDialog(
        onDismissRequest = { showDisappearingDialog = false },
        title = { Text("الرسائل المؤقتة") },
        text = { Column {
            Text("ستُحذف الرسائل الجديدة تلقائياً من الطرفين بعد انقضاء المدة المختارة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            listOf(0L to "إيقاف", 3_600_000L to "1 ساعة", 86_400_000L to "24 ساعة", 604_800_000L to "7 أيام", 7_776_000_000L to "90 يوماً").forEach { (ms, label) ->
                Row(Modifier.fillMaxWidth().clickable {
                    disappearingDurationMs = if (ms > 0) ms else null
                    showDisappearingDialog = false
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (disappearingDurationMs ?: 0L) == ms, onClick = {
                        disappearingDurationMs = if (ms > 0) ms else null
                        showDisappearingDialog = false
                    })
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = if ((disappearingDurationMs ?: 0L) == ms) FontWeight.Bold else FontWeight.Normal)
                }
            }
        } },
        confirmButton = { TextButton({ showDisappearingDialog = false }) { Text("إغلاق") } }
    )
    if (showGroupDisappearingDialog) AlertDialog(
        onDismissRequest = { showGroupDisappearingDialog = false },
        title = { Text("الرسائل المؤقتة في المجموعة") },
        text = { Column {
            Text("ستُحذف الرسائل الجديدة تلقائياً بعد انقضاء المدة المختارة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            listOf(0L to "إيقاف", 3_600_000L to "1 ساعة", 86_400_000L to "24 ساعة", 604_800_000L to "7 أيام", 7_776_000_000L to "90 يوماً").forEach { (ms, label) ->
                Row(Modifier.fillMaxWidth().clickable {
                    groupDisappearingMs = if (ms > 0) ms else null
                    showGroupDisappearingDialog = false
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (groupDisappearingMs ?: 0L) == ms, onClick = {
                        groupDisappearingMs = if (ms > 0) ms else null
                        showGroupDisappearingDialog = false
                    })
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = if ((groupDisappearingMs ?: 0L) == ms) FontWeight.Bold else FontWeight.Normal)
                }
            }
        } },
        confirmButton = { TextButton({ showGroupDisappearingDialog = false }) { Text("إغلاق") } }
    )
    // تفكيك 2026-09-10: المعرضان في DashboardGallery.kt — هنا تمرير فقط.
    DashboardGalleryOverlays(
        showMediaGallery = showMediaGallery,
        target = target,
        myRedId = account.redId,
        decrypted = decrypted,
        attachments = attachments,
        onDismissMedia = { showMediaGallery = false },
        showGroupMediaGallery = showGroupMediaGallery,
        groupConversationId = groupConversationId,
        onDismissGroupMedia = { showGroupMediaGallery = false }
    )
    if (showMessageSearch) AlertDialog(
        onDismissRequest = { showMessageSearch = false; messageSearchQuery = "" },
        title = { Text("البحث داخل المحادثة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(messageSearchQuery, { messageSearchQuery = it }, Modifier.fillMaxWidth(), label = { Text("كلمة أو عبارة") }, singleLine = true)
            val currentConversation = groupConversationId ?: conversationId(account.redId, target)
            // بحث موحد مع debounce: الاستعلام الخام يُثبَّت عبر rememberDebouncedMessageQuery
            // (300ms + حد أدنى حرفين — نفس سياسة RedGlobalSearch) ثم يُستعلم Room مرة واحدة.
            // كان LaunchedEffect(messageSearchQuery) يستعلم مع كل حرف = عاصفة Room.
            val stableQuery = rememberDebouncedMessageQuery(messageSearchQuery)
            // 🔍 البحث يستعلم السجل المحلي الحقيقي (Room) ويعرض النص المفكوك فقط
            val searchResults = remember { mutableStateOf<List<com.red.sovereign.core.database.LocalHistoryEntity>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(stableQuery, currentConversation) {
                searchResults.value = if (stableQuery.length >= MESSAGE_SEARCH_MIN_LENGTH) {
                    repository.searchAll(stableQuery).filter { it.conversationId == currentConversation && searchDisplayText(it).isNotBlank() }
                } else emptyList()
            }
            if (stableQuery.length >= MESSAGE_SEARCH_MIN_LENGTH) {
                Text("${searchResults.value.size} نتيجة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                LazyColumn(Modifier.height(260.dp)) { items(searchResults.value, key = { it.id }) { result -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(10.dp)) { Text(searchDisplayText(result), maxLines = 4); Row(verticalAlignment = Alignment.CenterVertically) { Text(if (result.outgoing) "أنت" else result.senderId, color = AqyalCyanGlow, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(" • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } } } }
            } else {
                Text("اكتب كلمتين على الأقل للبحث في هذه المحادثة.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } },
        confirmButton = { TextButton({ showMessageSearch = false; messageSearchQuery = "" }) { Text("إغلاق") } }
    )
    if (showDirectory) AlertDialog(
        onDismissRequest = { showDirectory = false; pendingForwardMessage = null; directory.clear() },
        title = { Text("أشخاص يونس") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(directoryQuery, { directoryQuery = it }, Modifier.fillMaxWidth(), label = { Text("username أو معرّف يونس") }, singleLine = true)
                Button({ directory.search(directoryQuery) }, Modifier.fillMaxWidth(), enabled = directoryQuery.trim().length >= 3 && directory.state != DirectoryState.Loading) {
                    Icon(Icons.Default.Search, null); Text(" بحث آمن")
                }
                // 📤 التوجيه إلى مجموعة — يظهر فقط أثناء جلسة إعادة التوجيه
                if (pendingForwardMessage != null && groups.groups.isNotEmpty()) {
                    Text("التوجيه إلى مجموعة:", color = AqyalGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.height(170.dp)) {
                        items(groups.groups, key = { it.id }) { group ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val forward = pendingForwardMessage
                                if (forward != null) {
                                    RedConnectionService.sendGroupRichText(context, group, RichMessage(text = messageDisplayText(forward), forwardOf = forward.id), UuidV7.next())
                                    pendingForwardMessage = null
                                }
                                showDirectory = false; directory.clear()
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GroupAvatar(group, groups)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${group.members.size} عضو", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Text("توجيه", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                when (val state = directory.state) {
                    DirectoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                    is DirectoryState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is DirectoryState.Message -> Text(state.text, color = AqyalGold)
                    DirectoryState.Ready -> if (directory.results.isEmpty()) Text("لا توجد نتائج مطابقة", color = Color.Gray) else LazyColumn(Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(directory.results, key = { it.redId }) { person ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SovereignAvatar(person.displayName.take(1)); Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(person.displayName, fontWeight = FontWeight.Bold); Text("@${person.username} · ${person.redId}", color = AqyalCyanGlow, fontSize = 10.sp) }
                                    TextButton({
                                        val forward = pendingForwardMessage
                                        if (forward != null) {
                                            RedConnectionService.sendRichText(context, person.redId, conversationId(account.redId, person.redId), RichMessage(text = messageDisplayText(forward), forwardOf = forward.id), UuidV7.next())
                                            pendingForwardMessage = null
                                        } else target = person.redId
                                        showDirectory = false
                                    }) { Text(if (pendingForwardMessage != null) "توجيه" else "محادثة") }
                                    Button({ directory.request(person) }) { Text("إضافة") }
                                }
                            }
                        }
                    }
                    DirectoryState.Idle -> Text("ابحث عن شخص دون مشاركة رقم هاتف أو جهات اتصال الجهاز.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton({ showDirectory = false; pendingForwardMessage = null; directory.clear() }) { Text("إغلاق") } }
    )
    if (create) AlertDialog(onDismissRequest = { create = false; pendingCreateAvatarUri = null }, title = { Text("إنشاء مجموعة جديدة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // صورة المجموعة حيّة: نقرة تفتح منتقي الصور، والمعاينة محلية فورية،
            // والرفع عبر MediaApi + حفظ avatarUrl يتم في GroupViewModel.create(avatarUri) عند التأكيد.
            val createAvatarPreview = remember(pendingCreateAvatarUri) {
                pendingCreateAvatarUri?.let { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }
            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { createAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, contentAlignment = Alignment.Center) {
                if (createAvatarPreview != null) Image(createAvatarPreview, "صورة المجموعة", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.CameraAlt, "إضافة صورة", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") }, singleLine = true)
            OutlinedTextField(groupDescription, { groupDescription = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("الوصف — اختياري") }, minLines = 2, maxLines = 4)
            Text("المجموعة مشفرة بشكل افتراضي. نستخدم Sender Keys في حالة وجود أعضاء.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ groups.create(name, groupDescription.trim().takeIf(String::isNotEmpty), avatarUri = pendingCreateAvatarUri) { create = false; name = ""; groupDescription = ""; pendingCreateAvatarUri = null } }, enabled = name.trim().length in 2..100 && groups.state != GroupState.Saving) { Text("إنشاء المجموعة") } },
        dismissButton = { OutlinedButton({ create = false; name = ""; groupDescription = ""; pendingCreateAvatarUri = null }) { Text("إلغاء") } })
    if (showJoinGroup) AlertDialog(
        onDismissRequest = { showJoinGroup = false; joinToken = "" },
        title = { Text("الانضمام إلى مجموعة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(joinToken, { joinToken = it.trim() }, Modifier.fillMaxWidth(), label = { Text("رمز الدعوة") }, singleLine = true); Text("قد يتطلب الانضمام موافقة مالك أو مسؤول المجموعة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button({ groups.joinWithToken(joinToken) { showJoinGroup = false; joinToken = "" } }, enabled = joinToken.length >= 32 && groups.state != GroupState.Saving) { Text("إرسال الطلب") } },
        dismissButton = { TextButton({ showJoinGroup = false; joinToken = "" }) { Text("إلغاء") } }
    )
    messageInfo?.let { info ->
        // تفكيك: التنفيذ في DashboardSheets.kt — هنا تمرير فقط.
        MessageInfoDialog(
            info = info,
            isEdited = editedMessageIds.containsKey(info.id),
            onDismiss = { messageInfo = null }
        )
    }
}

}

@Composable
private fun UnifiedCallsScreen(ownUserId: String, history: CallHistoryViewModel, contacts: List<com.red.sovereign.contacts.PublicRedProfile>, onlineIds: Set<String> = emptySet(), myDisplayName: String = "", onExplore: () -> Unit) {
    var showStatsScreen by remember { mutableStateOf(false) }
    var showScheduledCallsScreen by remember { mutableStateOf(false) }
    var showLanScreen by remember { mutableStateOf(false) }
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showGroupCallPicker by remember { mutableStateOf(false) }
    var showCreateConferenceScreen by remember { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var showPublicStreamsSearchDialog by remember { mutableStateOf(false) }
    var publicStreamSearchQuery by remember { mutableStateOf("") }
    var newCallTargetInput by remember { mutableStateOf("") }
    var isSpaceHost by remember { mutableStateOf(false) }
    var isBroadcaster by remember { mutableStateOf(false) }
    var roomInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (showStatsScreen) {
        CallStatsScreen(
            viewModel = history,
            onBack = { showStatsScreen = false }
        )
        return
    }

    if (showScheduledCallsScreen) {
        com.red.sovereign.features.calls.ScheduledCallsScreen(
            onBack = { showScheduledCallsScreen = false }
        )
        return
    }

    // P2-LAN: مكالمات نفس الواي فاي P2P (اكتشاف NSD + WebRTC host-only)
    if (showLanScreen) {
        com.red.sovereign.features.lan.LanPeersScreen(
            myRedId = ownUserId,
            myName = myDisplayName,
            contactRedIds = contacts.map { it.redId }.toSet(),
            onClose = { showLanScreen = false }
        )
        return
    }

    val visible = history.filteredCalls

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("مركز المكالمات السيادي", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily)
                Text("المكالمات الفردية، المؤتمرات، والبث المباشر", color = Color.LightGray, fontSize = 12.sp, fontFamily = PlexArabicFamily)
            }
            IconButton(
                onClick = { showStatsScreen = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SovereignColors.SurfaceCard)
                    .border(1.dp, SovereignColors.GlassBorder, CircleShape)
            ) {
                Icon(Icons.Filled.Poll, "إحصائيات وتحليلات المكالمات", tint = SovereignColors.GoldNeon, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        val callLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { /* will be handled per action */ },
            onDenied = { android.widget.Toast.makeText(context, "الصلاحيات مطلوبة للاتصال", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val privateCallLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { showNewCallDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا لإجراء المكالمة", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val groupCallLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { showGroupCallPicker = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للمكالمة الجماعية", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val conferenceLauncher = rememberCallPermissionLauncher(
            // 🔧 إصلاح الشاشة السوداء: مؤتمر الفيديو يحتاج الكاميرا — كانت needCamera=false فلا يُطلب الإذن
            needCamera = true,
            onGranted = { showJoinDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للمؤتمر", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val liveLauncher = rememberCallPermissionLauncher(
            // 🔧 البث المباشر كمذيع يحتاج كاميرا + ميكروفون
            needCamera = true,
            onGranted = { showLiveDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للبث", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val spaceLauncher = rememberCallPermissionLauncher(
            needCamera = false,
            onGranted = { showSpaceDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون لدخول المساحة الصوتية", android.widget.Toast.LENGTH_SHORT).show() }
        )

        CallsHubLaunchers(
            onNewCall = { privateCallLauncher() },
            onGroupCallPicker = { groupCallLauncher() },
            onConference = { conferenceLauncher() },
            onSpace = { spaceLauncher() },
            onLive = { liveLauncher() },
            onExplore = { onExplore() },
            onScheduledCalls = {
                showScheduledCallsScreen = true
            }
        )
        // P2-LAN: دخول مكالمات نفس الواي فاي (تعمل بلا إنترنت)
        androidx.compose.material3.TextButton(
            onClick = { showLanScreen = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                containerColor = SovereignColors.SurfaceCard
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                "مكالمات نفس الواي فاي — P2P بلا إنترنت",
                color = SovereignColors.EmeraldNeon,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        // شريط البحث المباشر في السجل
        OutlinedTextField(
            value = history.searchQuery,
            onValueChange = { history.searchQuery = it },
            placeholder = { Text("بحث في سجل المكالمات (اسم أو معرف أو رقم)...", fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = SovereignColors.EmeraldNeon, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (history.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { history.searchQuery = "" }) {
                        Icon(Icons.Filled.Close, "مسح", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SovereignColors.SurfaceCard,
                unfocusedContainerColor = SovereignColors.ObsidianDeep,
                focusedBorderColor = SovereignColors.EmeraldNeon,
                unfocusedBorderColor = SovereignColors.GlassBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("السجل المشفر", color = Color.White.copy(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showStatsScreen = true }) {
                Icon(Icons.Filled.Poll, null, tint = SovereignColors.GoldNeon, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("الإحصائيات", color = SovereignColors.GoldNeon, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { showRecordings = true }) {
                Icon(Icons.Default.FiberManualRecord, null, tint = AqyalGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("التسجيلات", color = AqyalGold, fontSize = 12.5.sp)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(CallFilterType.values(), key = { it }) { fType ->
                FilterChip(
                    selected = history.selectedFilter == fType,
                    onClick = { history.selectedFilter = fType },
                    label = { Text(fType.label, fontSize = 11.sp, fontWeight = if (history.selectedFilter == fType) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            history.loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            history.error != null -> EmptyState(Icons.Default.History, "تعذر تحميل السجل", history.error.orEmpty())
            visible.isEmpty() -> EmptyState(Icons.Default.History, "لا توجد مكالمات تطابق البحث", "ستظهر هنا المكالمات المفلترة.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible, key = { it.id }) { CallHistoryRow(it) } }
        }
    }

    if (showGroupCallPicker) {
        GroupCallPickerDialog(
            contacts = contacts,
            onlineIds = onlineIds,
            onDismiss = { showGroupCallPicker = false },
            onStartCall = { selectedIds, isVideo ->
                showGroupCallPicker = false
                val selectedNames = selectedIds.map { id ->
                    contacts.find { it.redId == id }?.displayName ?: id
                }
                GroupCallService.startGroupCall(
                    context = context,
                    myUserId = ownUserId,
                    inviteeIds = selectedIds,
                    inviteeNames = selectedNames,
                    isVideo = isVideo,
                    hostName = myDisplayName
                )
            }
        )
    }

    if (showJoinDialog) {
        ConferenceHubDialog(
            onDismiss = { showJoinDialog = false },
            onCreateNew = { showCreateConferenceScreen = true },
            onJoinExisting = { roomId, password ->
                ConferenceService.join(context, roomId, ownUserId, true, asHost = false, joinPassword = password)
            }
        )
    }

    if (showCreateConferenceScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateConferenceScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CreateConferenceScreen(
                friendIds = contacts.map { it.redId },
                friendNames = contacts.map { it.displayName },
                myUserId = ownUserId,
                onBack = { showCreateConferenceScreen = false },
                onLaunched = { showCreateConferenceScreen = false }
            )
        }
    }

    if (showRecordings) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showRecordings = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.red.sovereign.features.calls.CallRecordingsScreen(onBack = { showRecordings = false })
        }
    }

    if (showLiveDialog) {
        LiveStreamHubDialog(
            onDismiss = { showLiveDialog = false },
            onStartBroadcasting = { title, audience, pass, friendIds, category ->
                // FIX gate: تحقق ثانٍ قبل البث (الحوار مفتوح ببوابة لكن قد يُسحب الإذن أثناء فتحه)
                val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCam || !hasMic) {
                    android.widget.Toast.makeText(context, "امنح الكاميرا والميكروفون أولاً — افتح البث من زر البث (سيطلب الإذن)", android.widget.Toast.LENGTH_LONG).show()
                    return@LiveStreamHubDialog
                }
                // الجمهور: PUBLIC عام / FRIENDS أصدقاء بدعوات + سر / PRIVATE خاص بسر.
                val isPriv = audience != "PUBLIC"
                val password = if (isPriv) pass.trim().takeIf { it.isNotBlank() } else null
                showLiveDialog = false
                val rawId = "stream_${java.util.UUID.randomUUID().toString().take(8)}"
                val streamId = com.red.sovereign.calls.RoomSeparationPolicy.normalizeStreamId(rawId)
                LiveStreamService.start(
                    context = context,
                    streamId = streamId,
                    userId = ownUserId,
                    isBroadcaster = true,
                    title = title,
                    isPrivate = isPriv,
                    password = password,
                    category = category
                )
                // بث الأصدقاء: دعوة المختارين فور الإطلاق عبر الخادم.
                // FIX: استخدم streamId المطبّع (كان raw يسبب 404)
                // إصلاح السباق Legendary V2: إعادة محاولة حتى 5 مرات (1s) بدل تأخير ثابت 2.5s
                // قد يسبق registerBroadcaster أو يتأخر عنه.
                if (audience == "FRIENDS" && friendIds.isNotEmpty()) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val api = com.red.sovereign.auth.AuthorizedApiClient(DashboardStoresHolder.tokenStore(context))
                            val body = org.json.JSONObject()
                                .put("friendIds", org.json.JSONArray(friendIds))
                                .toString()
                            var sent = false
                            repeat(5) { attempt ->
                                if (sent) return@repeat
                                when (val r = api.request("POST", "/api/livestream/$streamId/invite", body)) {
                                    is com.red.sovereign.auth.ApiResult.Success -> sent = true
                                    is com.red.sovereign.auth.ApiResult.Error -> {
                                        // البث لم يُسجل بعد؟ انتظر ثم أعد المحاولة
                                        if (attempt < 4) kotlinx.coroutines.delay(1000)
                                    }
                                }
                            }
                        }
                    }
                    // إصلاح الخصوصية: لا تعرض كلمة السر في Toast/سجل الشاشة
                    android.widget.Toast.makeText(
                        context,
                        "بث الأصدقاء بدأ — أُرسلت الدعوات (${friendIds.size})",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            onWatchStream = { streamId, password ->
                showLiveDialog = false
                LiveStreamService.watch(context, streamId, ownUserId, password)
            },
            friends = contacts
        )
    }

    // 🎙️ حوار المساحات الصوتية — غرفة صوتية جماعية (مؤتمر بلا فيديو)
    if (showSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false },
            title = { Text("مساحة صوتية يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "مساحة صوتية مشفرة عبر خادم SFU — صوت فقط، بلا كاميرا.\nاترك الحقل فارغًا لإنشاء غرفة جديدة بمعرّف تلقائي.",
                        color = Color.Gray, fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف المساحة (اختياري — مثال: majlis-01)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isSpaceHost, onCheckedChange = { isSpaceHost = it })
                        Text("الانضمام كمضيف (متحدث)", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSpaceDialog = false
                        // معرف تلقائي فريد إن لم يُدخل المستخدم واحدًا
                        val spaceId = roomInput.trim().ifBlank { "space-${ownUserId.lowercase()}-${System.currentTimeMillis() % 100000}" }
                        // video=false → مسار صوتي صرف — هذا هو الفرق بين المساحة والمؤتمر المرئي
                        ConferenceService.join(context, spaceId, ownUserId, false, asHost = isSpaceHost || roomInput.isBlank())
                        roomInput = ""
                        isSpaceHost = false
                    }
                ) {
                    Text(if (roomInput.isBlank()) "إنشاء مساحة جديدة" else "دخول المساحة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showNewCallDialog) {
        AlertDialog(
            onDismissRequest = { showNewCallDialog = false; newCallTargetInput = "" },
            title = { Text("مكالمة جديدة مشفرة E2EE 📞") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أدخل معرّف يونس أو اختر من جهات اتصالك للاتصال الفوري:", color = Color.Gray, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newCallTargetInput,
                        onValueChange = { newCallTargetInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرّف يونس (مثال: 10001)") },
                        singleLine = true
                    )
                    if (contacts.isNotEmpty()) {
                        Text("جهات الاتصال السريعة:", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filtered = contacts.filter {
                                newCallTargetInput.isBlank() || it.displayName.contains(newCallTargetInput, true) || it.redId.contains(newCallTargetInput) || it.username.contains(newCallTargetInput, true)
                            }
                            items(filtered, key = { it.redId }) { contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("@${contact.username} · ${contact.redId}", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                showNewCallDialog = false
                                                YounesCallService.start(context, contact.redId, video = false)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Call, "صوت", tint = YounesEmerald, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                showNewCallDialog = false
                                                YounesCallService.start(context, contact.redId, video = true)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Videocam, "فيديو", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clean = com.red.sovereign.core.YounesId.normalizeInput(newCallTargetInput).ifBlank { newCallTargetInput.trim() }
                            showNewCallDialog = false
                            YounesCallService.start(context, clean, video = false)
                            newCallTargetInput = ""
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) {
                        Text("صوتية")
                    }
                    Button(
                        onClick = {
                            val clean = com.red.sovereign.core.YounesId.normalizeInput(newCallTargetInput).ifBlank { newCallTargetInput.trim() }
                            showNewCallDialog = false
                            YounesCallService.start(context, clean, video = true)
                            newCallTargetInput = ""
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) {
                        Text("فيديو")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCallDialog = false; newCallTargetInput = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showPublicStreamsSearchDialog) {
        AlertDialog(
            onDismissRequest = { showPublicStreamsSearchDialog = false; publicStreamSearchQuery = "" },
            title = { Text("اكتشاف البثوث العامة والمساحات 🌐") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ابحث عن بث مباشر عام أو مساحة صوتية باسم البث أو المُبث:", color = Color.Gray, fontSize = 13.sp)
                    OutlinedTextField(
                        value = publicStreamSearchQuery,
                        onValueChange = { publicStreamSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("اسم البث أو اسم الشخص أو المعرّف") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPublicStreamsSearchDialog = false
                        val finalStreamId = publicStreamSearchQuery.trim().ifBlank { "public-stream-1" }
                        LiveStreamService.start(context, finalStreamId, ownUserId, false)
                        publicStreamSearchQuery = ""
                    }
                ) {
                    Text("انضمام للبث المباشر")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublicStreamsSearchDialog = false; publicStreamSearchQuery = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

}

@Composable
private fun CallHistoryRow(call: CallHistoryItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isMissed = call.status == "MISSED"
    val isOutgoing = call.direction == "OUTGOING"
    // شارة الحالة: مرفوضة / مشغول / فشلت — بدل أن تظهر كلها "فائتة"
    val statusBadge = when (call.status) {
        "REJECTED" -> "مرفوضة" to Color(0xFFE53935)
        "BUSY" -> "مشغول" to Color(0xFFFF8F00)
        "FAILED" -> "فشلت" to Color(0xFFB0BEC5)
        else -> null
    }

    val durationSec = (call.endedAt?.toLongOrNull() ?: 0L) - (call.answeredAt?.toLongOrNull() ?: 0L)
    val durationText = if (durationSec > 0) {
        val mm = durationSec / 60; val ss = durationSec % 60
        "%d:%02d".format(mm, ss)
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (call.type) {
                    "LIVE" -> LiveStreamService.start(context, call.id, call.peerId, false)
                    "SPACE" -> ConferenceService.join(context, call.id, call.peerId, false, asHost = false)
                    "GROUP" -> ConferenceService.join(context, call.id, call.peerId, true, asHost = false)
                    else -> if (call.peerId.matches(RED_ID_PATTERN)) {
                        YounesCallService.start(context, call.peerId, call.type == "VIDEO")
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val glyph = callTypeGlyph(call.type, call.route)

        // أفتار المتصل
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(glyph.second.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(glyph.first, null, tint = glyph.second, modifier = Modifier.size(24.dp))
        }

        Spacer(Modifier.width(14.dp))

        // التفاصيل
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = call.peerLabel.ifBlank { call.peerId },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isMissed) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (statusBadge != null) {
                    Text(
                        text = statusBadge.first,
                        color = statusBadge.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusBadge.second.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // أيقونة السهم
                val arrowIcon = when {
                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                    isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                    else -> Icons.AutoMirrored.Filled.CallReceived
                }
                val arrowColor = when {
                    isMissed -> Color(0xFFF44336)
                    isOutgoing -> YounesEmerald
                    else -> Color(0xFF4CAF50)
                }

                Icon(arrowIcon, contentDescription = null, tint = arrowColor, modifier = Modifier.size(14.dp))

                Text(
                    text = buildString {
                        val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(call.startedAt.toLongOrNull() ?: System.currentTimeMillis()))
                        append(date)
                        if (durationText.isNotEmpty()) append(" • $durationText")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // زر الاتصال السريع
        IconButton(
            onClick = {
                when (call.type) {
                    "LIVE" -> LiveStreamService.start(context, call.id, call.peerId, false)
                    "SPACE" -> ConferenceService.join(context, call.id, call.peerId, false, asHost = false)
                    "GROUP" -> ConferenceService.join(context, call.id, call.peerId, true, asHost = false)
                    else -> if (call.peerId.matches(RED_ID_PATTERN)) {
                        YounesCallService.start(context, call.peerId, call.type == "VIDEO")
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (call.type == "VIDEO" || call.type == "LIVE") Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "اتصال",
                tint = YounesEmerald,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RoundCallAction(icon: ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) {
    // تفكيك عام: التنفيذ في DashboardCalls.kt — هنا تمرير فقط.
    DashboardRoundCallAction(icon = icon, title = title, color = color, enabled = enabled, onClick = onClick)
}

@Composable
private fun MoreScreen(
    account: AuthState.Authenticated,
    onAdmin: () -> Unit,
    onSettings: () -> Unit,
    onContacts: () -> Unit,
    onDevices: () -> Unit,
    onPrivacy: () -> Unit,
    onBackup: () -> Unit,
    onCommunities: () -> Unit = {},
    onProfile: () -> Unit = {},
    onEvents: () -> Unit = {},
    onPolls: () -> Unit = {},
    // ربط P0: مدخل شاشة إعدادات الجهاز (البنود المربوطة بالصفحات الحية).
    onDeviceSettings: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مساحة يونس", style = MaterialTheme.typography.headlineMedium)
        Text("الهوية والخدمات السيادية في مكان واحد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth().clickable { onProfile() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SovereignAvatar(account.username.take(1))
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("البروفايل · الصورة والبايو والهوية", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MoreOption(Icons.Default.AdminPanelSettings, "الإدارة السيادية", "عمليات يونس ماستر", AqyalGold, click = onAdmin)
        MoreOption(Icons.Default.Security, "الخصوصية والأمان", "من يرى بياناتك، التشفير، وقفل البصمة", com.red.sovereign.ui.theme.YounesEmerald, click = onPrivacy)
        MoreOption(Icons.Default.CloudSync, "النسخ الاحتياطي", "تأمين محادثاتك وسجلاتك محلياً", com.red.sovereign.ui.theme.YounesGold, click = onBackup)
        MoreOption(Icons.Default.Devices, "الأجهزة المتصلة", "إدارة جلسات يونس على كافة أجهزتك", com.red.sovereign.ui.theme.AqyalCyanGlow, click = onDevices)
        MoreOption(Icons.Default.Settings, "الإعدادات العامة", "الهوية والأجهزة والخادم والجلسة", com.red.sovereign.ui.theme.YounesEmerald, click = onSettings)
        // ربط P0: شاشة إعدادات الجهاز — كل بند يفتح وجهته الحية (خصوصية/مظهر/
        // إشعارات/دردشات/بيانات/حساب/أجهزة/استعادة/خادم/طابور) بدل الشارات الميتة.
        MoreOption(Icons.Default.Tune, "إعدادات الجهاز", "السمة والقفل والإشعارات والتخزين والطابور دون اتصال", com.red.sovereign.ui.theme.YounesEmerald, click = onDeviceSettings)
        MoreOption(Icons.Default.Contacts, "جهات الاتصال", "الأصدقاء وطلبات التواصل والحظر", com.red.sovereign.ui.theme.AqyalCyanGlow, click = onContacts)
        MoreOption(Icons.Default.Public, "المجتمعات والقنوات", "مجتمعات عامة وقنوات — انضم وتابع (عام، ليس مشفراً)", Color(0xFFA78BFA), enabled = true, click = onCommunities)
        MoreOption(Icons.Default.Event, "الفعاليات", "فعاليات مجتمعية مع RSVP وتسجيل حضور", Color(0xFFE8B84A), enabled = true, click = onEvents)
        MoreOption(Icons.Default.Poll, "الاستطلاعات", "تصويت مجتمعي مع نتائج فورية ونِسَم مئوية", Color(0xFF65D7E7), enabled = true, click = onPolls)
    }
}

@Composable
private fun MoreOption(icon: ImageVector, title: String, detail: String, color: Color, enabled: Boolean = true, click: () -> Unit) =
    Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(
    publishing: Boolean,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
    onPoll: (String, List<String>, Int, List<String?>) -> Unit,
    onStory: () -> Unit,
    onLive: () -> Unit,
    onExplore: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "", "")) }
    var pollHours by remember { mutableIntStateOf(24) }
    // صور الخيارات (نمط X 2026): objectKey لكل خيار (null = نصي)، تُرفع
    // فور الاختيار عبر /api/media. تُعرض أزرار الصور فقط مع ≤4 خيارات.
    var pollImages by remember { mutableStateOf(List<String?>(6) { null }) }
    var uploadingPollImage by remember { mutableStateOf(false) }
    var pollImageTarget by remember { mutableIntStateOf(-1) }
    val sheetContext = LocalContext.current
    val pollMediaApi = remember(sheetContext) {
        com.red.sovereign.media.MediaApi(
            sheetContext.applicationContext,
            // توحيد 2026-09-10: نفس TokenStore عبر Holder (داخل remember غير-Composable).
            com.red.sovereign.auth.AuthorizedApiClient(DashboardStoresHolder.tokenStore(sheetContext.applicationContext))
        )
    }
    val pollScope = androidx.compose.runtime.rememberCoroutineScope()
    val pollImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val idx = pollImageTarget
        pollImageTarget = -1
        if (uri != null && idx in pollOptions.indices) {
            uploadingPollImage = true
            pollScope.launch {
                val key = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    when (val up = pollMediaApi.upload(uri)) {
                        is com.red.sovereign.auth.ApiResult.Success -> up.value.objectKey
                        is com.red.sovereign.auth.ApiResult.Error -> null
                    }
                }
                pollImages = pollImages.toMutableList().also { if (idx in it.indices) it[idx] = key }
                uploadingPollImage = false
                if (key == null) {
                    android.widget.Toast.makeText(sheetContext, "تعذر رفع صورة الخيار", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("إنشاء في يونس", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (mode != "menu") TextButton({ mode = "menu" }) { Text("الخيارات") }
            }
            when (mode) {
                "post" -> {
                    OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth().height(150.dp), placeholder = { Text("اكتب منشوراً، سلسلة، فكرة طويلة، أو إعلاناً محلياً…") }, maxLines = 7)
                    Text("${text.length}/2000", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Button({ if (text.isNotBlank()) onPost(text.trim()) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !publishing) { if (publishing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("نشر محلي") }
                }
                "poll" -> {
                    OutlinedTextField(pollQuestion, { pollQuestion = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("سؤال الاستطلاع") }, maxLines = 3)
                    pollOptions.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { next -> pollOptions = pollOptions.toMutableList().also { it[index] = next.take(80) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("الخيار ${index + 1}") },
                            singleLine = true,
                            trailingIcon = {
                                // صورة الخيار (X): متاحة فقط مع 4 خيارات أو أقل.
                                if (pollOptions.size <= 4) {
                                    val hasImage = pollImages.getOrNull(index) != null
                                    IconButton(
                                        onClick = {
                                            if (hasImage) {
                                                pollImages = pollImages.toMutableList().also { it[index] = null }
                                            } else {
                                                pollImageTarget = index
                                                pollImagePicker.launch(arrayOf("image/*"))
                                            }
                                        },
                                        enabled = !uploadingPollImage
                                    ) {
                                        Icon(
                                            if (hasImage) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                                            if (hasImage) "إزالة صورة الخيار" else "إضافة صورة للخيار",
                                            tint = if (hasImage) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "ساعة", 24 to "يوم", 72 to "3 أيام", 168 to "أسبوع").forEach { option ->
                            FilterChip(selected = pollHours == option.first, onClick = { pollHours = option.first }, label = { Text(option.second) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (pollOptions.size < 6) pollOptions = pollOptions + "" }, Modifier.weight(1f), enabled = pollOptions.size < 6) { Text("إضافة خيار") }
                        OutlinedButton({ if (pollOptions.size > 2) pollOptions = pollOptions.dropLast(1) }, Modifier.weight(1f), enabled = pollOptions.size > 2) { Text("حذف خيار") }
                    }
                    val validPoll = pollQuestion.isNotBlank() && pollOptions.count { it.trim().length >= 2 } >= 2
                    if (uploadingPollImage) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = YounesEmerald, strokeWidth = 2.dp)
                            Text("جارٍ رفع صورة الخيار…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Button(
                        {
                            onPoll(pollQuestion, pollOptions, pollHours, pollImages.take(pollOptions.size))
                            pollImages = List(6) { null }
                        },
                        Modifier.fillMaxWidth(), enabled = validPoll && !publishing && !uploadingPollImage
                    ) { if (publishing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("نشر الاستطلاع") }
                }
                else -> {
                    CreateOption(Icons.Default.DynamicFeed, "منشور أو سلسلة", "نص طويل، اقتباس، نقاش محلي", true) { mode = "post" }
                    CreateOption(Icons.Default.Forum, "استطلاع تفاعلي", "سؤال وخيارات وتصويت فعلي عبر الخادم", true) { mode = "poll" }
                    CreateOption(Icons.Default.AddCircle, "حالة 24 ساعة", "صورة أو فيديو يُحذف تلقائياً", true, onStory)
                    CreateOption(Icons.Default.LiveTv, "بث مباشر", "فيديو عبر SFU المحلي", true, onLive)
                    CreateOption(Icons.Default.Explore, "استكشاف يونس", "اكتشف البثوث والغرف الصوتية النشطة", true, onExplore)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun CreateOption(icon: ImageVector, title: String, detail: String, enabled: Boolean, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = if (enabled) AqyalGold else Color.Gray, modifier = Modifier.size(31.dp)); Column(Modifier.padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color.Unspecified else Color.Gray); Text(detail, color = Color.Gray, fontSize = 12.sp) } } }

@Composable internal fun EmptyState(icon: ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }
// (حُذف PollVoteStore الميت + InlinePollCard المكررة: البطاقة الحية في MessageContent.kt
//  عبر RichTextMessage، والأصوات الحية في ChatPollVoteStore — كانت النسخة هنا بلا منادين
//  وتحمل خطأ tautology في isSelected. 2026-09-10)

/** نص بحثي نظيف للسجل المحلي: يستبعد إدخالات النظام ويفك شيفرة أسماء الوسائط. */
private fun searchDisplayText(entity: com.red.sovereign.core.database.LocalHistoryEntity): String {
    val text = entity.encryptedPlaintext.toString(Charsets.UTF_8)
    return when {
        entity.messageType == "RICH_TEXT" -> {
            val rich = RichMessage.decode(entity.encryptedPlaintext)
            if (rich == null || rich.action in setOf("POLL_VOTE", "REACTION", "REACTION_REMOVE", "DELETE", "EDIT")) "" else rich.text.orEmpty()
        }
        entity.messageType == "GROUP_MESSAGE" || entity.messageType in setOf("FILE", "IMAGE", "VIDEO", "AUDIO", "VOICE", "STICKER") ->
            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.AttachmentManifest>(text) }.getOrNull()?.name
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.VoiceManifest>(text) }.getOrNull()?.name
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(text) }.getOrNull()?.let { if (it.emoji.isNotBlank()) it.emoji else "ملصق" }
                ?: text
        else -> text
    }
}

// (حُذفت VoiceRecordingControls/PreviewControls/Waveform الميتة: النسخ الحية في
//  MessageContent.kt — كانت هنا بلا منادين. 2026-09-10)

// (حُذفت فقاعات Image/Video/Audio/FileMessage الميتة: النسخ الحية في MessageContent.kt
//  عبر AttachmentMessage — كانت هنا بلا منادين وتضاعف منطق فك التشفير. 2026-09-10)

/**
 * 📴 بانر الطابور دون اتصال — تفكيك: التنفيذ في DashboardOffline.kt
 * (DashboardOfflineOutboxBanner) — هنا تمرير فقط لتقليص الوحش.
 */
@Composable
private fun OfflineOutboxBanner(onOpenQueue: () -> Unit = {}) {
    DashboardOfflineOutboxBanner(onOpenQueue)
}

// (نُقلت groupRoleLabel/formatDuration/formatBytes/isSameDay/dateLabel/relativeTime/
//  formatClockTime إلى DashboardMedia.kt بصيغة dashboard* وLocale("ar") — كانت هنا
//  تستخدم Locale.US ووحدات MB/KB الإنجليزية. 2026-09-10)
// (حُذف MessageInfoRow الممرر: حوار المعلومات انتقل لـ DashboardSheets.kt
//  وينادي DashboardMessageInfoRow مباشرةً. 2026-09-10)

// تفكيك: RED_ID_PATTERN/RED_ID_PARTIAL/EMOJI_CATEGORIES/ATTACHMENT_JSON/conversationId
// نُقلت إلى DashboardIdentifiers.kt (نفس الحزمة — تُستخدم هنا مباشرة بلا import).

@Composable
private fun TabButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // تفكيك عام: التنفيذ في DashboardChat.kt — هنا تمرير فقط.
    DashboardTabButton(selected = selected, onClick = onClick, modifier = modifier, content = content)
}
