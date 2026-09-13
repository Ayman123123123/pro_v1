package com.red.sovereign.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Backspace
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
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
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
import com.red.sovereign.auth.PstnState
import com.red.sovereign.calls.CallHistoryItem
import com.red.sovereign.calls.CallHistoryViewModel
import com.red.sovereign.calls.CallRuntime
import com.red.sovereign.calls.CallUiState
import com.red.sovereign.calls.UnifiedCallOverlays
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.ConferenceUiState
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.calls.GroupCallService
import com.red.sovereign.calls.GroupCallRuntime
import com.red.sovereign.calls.GroupCallUiState
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.PinsApi
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.RedQualityManager
import com.red.sovereign.core.ReactionEventBus
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.ConversationSummary
import com.red.sovereign.core.database.MessageReactionEntity
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
import com.red.sovereign.media.VoiceNotePlayer
import com.red.sovereign.media.voice.VoiceBubble
import com.red.sovereign.media.voice.VoiceColors
import com.red.sovereign.media.voice.VoicePreviewActions
import com.red.sovereign.media.voice.VoiceRecorderPanel
import com.red.sovereign.media.voice.VoiceRecordButton
import com.red.sovereign.media.voice.VoiceTimerDisplay
import com.red.sovereign.media.voice.VoiceWaveformCanvas
import com.red.sovereign.media.voice.VoiceCancelProgressBar
import com.red.sovereign.media.voice.VoiceLockIndicator
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.settings.YounesSettingsSheet
import com.red.sovereign.social.FeedState
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.social.Post
import com.red.sovereign.social.ThreadState
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryState
import com.red.sovereign.stories.StoryVideoPlayer
import com.red.sovereign.stories.StoryViewerState
import com.red.sovereign.stories.StoryViewModel
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalRoyalBlue
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.AqyalSurfaceRaised
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.screens.CallsScreen
import com.red.sovereign.ui.screens.GroupsScreen
import com.red.sovereign.features.communities.CommunitiesScreen
import com.red.sovereign.features.contacts.ContactsScreen
import com.red.sovereign.features.chat.SovereignChatInputBar
import java.io.File
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import java.security.MessageDigest

import com.red.sovereign.features.devices.DevicesScreen
import com.red.sovereign.features.explore.RedExploreScreen
import com.red.sovereign.features.privacy.PrivacySettingsScreen
import com.red.sovereign.features.chat.CreateGroupScreen
import com.red.sovereign.features.chat.RedGlobalSearch
import com.red.sovereign.features.chat.SovereignGroupInfoScreen
import com.red.sovereign.features.media.MediaGalleryDialog
import com.red.sovereign.features.profile.BackupScreen
import com.red.sovereign.features.profile.ProfileScreen
import com.red.sovereign.core.YounesId
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.media.EventsScreen
import com.red.sovereign.media.PollsScreen
import com.red.sovereign.ui.screens.MoreScreen
import com.red.sovereign.ui.screens.FeedScreen
import com.red.sovereign.ui.screens.ChatHubScreen

private enum class MainSection(val label: String, val icon: ImageVector) {
    CHATS("الدردشات", Icons.Default.ChatBubble),
    GROUPS("المجموعات", Icons.Default.Groups),
    CALLS("المكالمات", Icons.Default.Call),
    HOME("الرئيسية", Icons.Default.Home),
    MORE("المزيد", Icons.Default.MoreHoriz)
}

private enum class SovereignScreen { DASHBOARD, DEVICES, PRIVACY, EXPLORE, CREATE_GROUP, BACKUP, GROUP_INFO, SEARCH, COMMUNITIES, CONTACTS, PROFILE, EVENTS, POLLS, DINSTAR_ADMIN, SMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedDashboard(account: AuthState.Authenticated, viewModel: AuthViewModel, deepLinkSender: String? = null, deepLinkConversation: String? = null) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(SovereignScreen.DASHBOARD) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    // 👥 فتح محادثة مجموعة من شاشة المجموعات المخصصة
    var openGroupChatId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(MainSection.CHATS) } // الأفضل من واتساب: الدردشات أولاً (الأكثر استخداماً)
    // 🔗 فتح محادثة خاصة من قائمة أعضاء المجموعة أو جهات الاتصال (يتغذى على deepLinkSender في ChatHubScreen)
    var pendingChatTarget by remember { mutableStateOf<String?>(null) }
    // 🔔 Auto-switch to CALLS tab when call starts/ringing — fixes "لا تظهر التبويبة الصحيحة"
    androidx.compose.runtime.LaunchedEffect(CallRuntime.state) {
        when {
            CallRuntime.state is CallUiState.Idle -> section = MainSection.CHATS
            CallRuntime.state !is CallUiState.Idle -> section = MainSection.CALLS
        }
    }
    // 🧹 مسح pendingChatTarget بعد فتح المحادثة حتى لا يُعاد فتحها عند التبديل بين التبويبات
    androidx.compose.runtime.LaunchedEffect(pendingChatTarget, section) {
        if (pendingChatTarget != null && section == MainSection.CHATS) {
            pendingChatTarget = null
        }
    }
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDinstar by remember { mutableStateOf(false) }
    // رقم محادثة SMS يُعبّأ في لوحة الاتصال عند الضغط على «اتصال» من شاشة الرسائل
    var smsPstnPrefill by remember { mutableStateOf<String?>(null) }
    var chatConversationOpen by remember { mutableStateOf(false) }
    // 🔄 إشارة عدّاد لإغلاق المحادثة المفتوحة من زر الرجوع (ChatHubScreen يقرأها ويلغي داخله)
    var closeConversationRequest by remember { mutableIntStateOf(0) }
    // 🔧 إصلاح العيب: dialer حقيقي لإدخال RED ID والاتصال 1-1 من CALLS section
    var showCallDialer by remember { mutableStateOf(false) }
    var dialerRedId by remember { mutableStateOf("") }
    var dialerVideo by remember { mutableStateOf(false) }
    var pendingDialerTarget by remember { mutableStateOf<String?>(null) }
    var pendingDialerVideo by remember { mutableStateOf(false) }
    val dialerCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingDialerVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val redId = pendingDialerTarget
if (audioGranted && cameraGranted && redId != null && redId.matches(RED_ID_PATTERN)) {
        YounesCallService.start(context, redId, pendingDialerVideo)
        section = MainSection.CALLS
    }
    pendingDialerTarget = null
    pendingDialerVideo = false
    }
    var pendingGroupsCall by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val groupsCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        pendingGroupsCall?.let { (groupId, video) ->
            val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val cameraGranted = !video || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (audioGranted && cameraGranted) {
                com.red.sovereign.calls.ConferenceService.join(context, groupId, account.redId, video, asHost = true)
            }
            pendingGroupsCall = null
        }
    }
    fun startGroupsCall(groupId: String, video: Boolean) {
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioGranted && cameraGranted) {
            com.red.sovereign.calls.ConferenceService.join(context, groupId, account.redId, video, asHost = true)
        } else {
            pendingGroupsCall = groupId to video
            groupsCallPermissions.launch(buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
            }.toTypedArray())
        }
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
    var pendingStoryVisibility by remember { mutableStateOf("EVERYONE") }
    var pendingStoryAudience by remember { mutableStateOf(listOf<String>()) }
    val createStoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { stories.upload(it, visibleTo = pendingStoryVisibility, audience = pendingStoryAudience) }
        pendingStoryVisibility = "EVERYONE"
        pendingStoryAudience = emptyList()
    }

    // 🔙 زر الرجوع: لا يُخرج من التطبيق بل يرجع خطوة خطوة — أغلق الحوارات أولاً،
    // ثم الشاشة الفرعية، ثم المحادثة المفتوحة، ثم التبويب، وأخيراً صغّر التطبيق للخلفية فقط.
    BackHandler {
        when {
            showSettings -> showSettings = false
            showCreate -> showCreate = false
            showCallDialer -> { showCallDialer = false; dialerRedId = ""; dialerVideo = false }
            showDinstar -> showDinstar = false
            currentScreen != SovereignScreen.DASHBOARD -> currentScreen = SovereignScreen.DASHBOARD
            section == MainSection.GROUPS && openGroupChatId != null -> openGroupChatId = null
            chatConversationOpen -> closeConversationRequest++
            section != MainSection.CHATS -> section = MainSection.CHATS
            else -> (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    // 🔔 Overlays must be global — before early return so they appear on ANY screen (Devices, Privacy, etc.)
    // YounesCallOverlay etc. are placed at the very end as well, but this early placement ensures incoming call is never missed
    if (currentScreen != SovereignScreen.DASHBOARD) {
        when (currentScreen) {
            SovereignScreen.DEVICES -> DevicesScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.PRIVACY -> PrivacySettingsScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.EXPLORE -> {
                val tokens = remember { TokenStore(context) }
                RedExploreScreen(
                    tokens = tokens,
                    ownRedId = account.redId,
                    onBack = { currentScreen = SovereignScreen.DASHBOARD }
                )
            }
            SovereignScreen.CREATE_GROUP -> CreateGroupScreen(
                onBack = { currentScreen = SovereignScreen.DASHBOARD },
                friends = directory.contacts,
                onCreate = { name, description, privacy, memberRedIds ->
                    groups.create(name, description, privacy, memberRedIds) { currentScreen = SovereignScreen.DASHBOARD; section = MainSection.GROUPS }
                }
            )
            SovereignScreen.BACKUP -> BackupScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.PROFILE -> ProfileScreen(
                redId = account.redId,
                username = account.username,
                displayName = account.username,
                onBack = { currentScreen = SovereignScreen.DASHBOARD }
            )
            SovereignScreen.EVENTS -> {
                val tokens = remember { TokenStore(context) }
                EventsScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD }, isAdmin = account.isAdmin)
            }
            SovereignScreen.POLLS -> {
                val tokens = remember { TokenStore(context) }
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
                    },
                    onOpenChat = { groupId ->
                        openGroupChatId = groupId
                        section = MainSection.GROUPS
                        currentScreen = SovereignScreen.DASHBOARD
                    }
                )
            }
            SovereignScreen.SEARCH -> RedGlobalSearch(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.DINSTAR_ADMIN -> {
                val dm = remember { com.red.sovereign.features.dinstar.DinstarViewModel(viewModel.getApplication()) }
                com.red.sovereign.features.dinstar.DinstarAdminScreen(dm, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.COMMUNITIES -> {
                val tokens = remember { TokenStore(context) }
                CommunitiesScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.CONTACTS -> ContactsScreen(directory = directory, onBack = { currentScreen = SovereignScreen.DASHBOARD }, onChat = { person -> currentScreen = SovereignScreen.DASHBOARD; section = MainSection.CHATS }, onCall = { person, video -> com.red.sovereign.calls.YounesCallService.start(context, person.redId, video) }, onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP })
            SovereignScreen.SMS -> {
                val smsVm: com.red.sovereign.sms.SmsViewModel = viewModel()
                com.red.sovereign.sms.SmsConversationsScreen(
                    vm = smsVm,
                    onBack = { currentScreen = SovereignScreen.DASHBOARD },
                    onCallNumber = { number ->
                        // الاتصال برقم محادثة SMS عبر شاشة الهاتف اليمني
                        smsPstnPrefill = number
                        currentScreen = SovereignScreen.DASHBOARD
                        showDinstar = true
                    }
                )
            }
            else -> currentScreen = SovereignScreen.DASHBOARD
        }
        // Still show call overlays even when not on dashboard — unified
        UnifiedCallOverlays()
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!showDinstar && !chatConversationOpen) when (section) {
                MainSection.CHATS -> FloatingActionButton(onClick = { currentScreen = SovereignScreen.CONTACTS }, containerColor = YounesEmerald, contentColor = Color(0xFF002117)) { Icon(Icons.Default.Chat, "دردشة جديدة") }
                MainSection.GROUPS -> FloatingActionButton(onClick = { currentScreen = SovereignScreen.CREATE_GROUP }, containerColor = YounesEmerald, contentColor = Color(0xFF002117)) { Icon(Icons.Default.GroupAdd, "مجموعة جديدة") }
                MainSection.CALLS -> FloatingActionButton(onClick = { showCallDialer = true }, containerColor = YounesEmerald, contentColor = Color(0xFF002117)) { Icon(Icons.Default.Dialpad, "اتصال جديد عبر يونس") }
                MainSection.HOME -> FloatingActionButton(onClick = { showCreate = true }, containerColor = YounesEmerald, contentColor = Color(0xFF002117)) { Icon(Icons.Default.Add, "إنشاء محتوى") }
                else -> {}
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f)) {
                // remember lambdas per item لتفادي إعادة التوليد كل recompose
                MainSection.entries.forEach { item ->
                    val itemLabel = item.label
                    val isSelected = section == item
                    val onClick = remember(item) { { section = item; showDinstar = false; if (item == MainSection.CALLS) { callHistory.load(); directory.refreshPresence() } } }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = onClick,
                        icon = { Icon(item.icon, itemLabel) },
                        label = { Text(itemLabel, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RedTopBar(account.redId, account.username, compact = SettingsRuntime.current.compactMode, onSettings = { showSettings = true }, onSearch = { currentScreen = SovereignScreen.SEARCH })
            when {
                showDinstar -> DinstarPhoneScreen(account, viewModel, callHistory, prefillNumber = smsPstnPrefill, onOpenSms = { currentScreen = SovereignScreen.SMS })
                section == MainSection.HOME -> FeedScreen(account, feed, stories, onCreate = { showCreate = true })
                section == MainSection.CHATS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = false, deepLinkSender = pendingChatTarget ?: deepLinkSender, deepLinkConversation = deepLinkConversation, onConversationOpen = { chatConversationOpen = it }, closeSignal = closeConversationRequest)
                section == MainSection.GROUPS -> if (openGroupChatId == null) GroupsScreen(
                    groups = groups,
                    onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP },
                    onOpenGroupChat = { id -> openGroupChatId = id },
                    onOpenGroupInfo = { id -> selectedGroupId = id; currentScreen = SovereignScreen.GROUP_INFO },
                    onStartGroupCall = { group, video ->
                        startGroupsCall(group.id, video)
                    }
                ) else ChatHubScreen(
                    account, groups, directory, safety, attachments, voiceMessages,
                    showGroups = true,
                    initialGroupId = openGroupChatId,
                    onManageGroup = { id -> selectedGroupId = id; currentScreen = SovereignScreen.GROUP_INFO },
                    onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP },
                    onConversationOpen = { chatConversationOpen = it },
                    onGroupChatClosed = { openGroupChatId = null },
                    closeSignal = closeConversationRequest
                )
                section == MainSection.CALLS -> CallsScreen(
                    ownUserId = account.redId,
                    history = callHistory,
                    directory = directory,
                    myDisplayName = account.username,
                    onExplore = { currentScreen = SovereignScreen.EXPLORE },
                    onPstn = { showDinstar = true }
                )
                else -> MoreScreen(
                    account,
                    onDinstar = { showDinstar = true },
                    onAdmin = { currentScreen = SovereignScreen.DINSTAR_ADMIN },
                    onSettings = { showSettings = true },
                    onContacts = { currentScreen = SovereignScreen.CONTACTS },
                    onDevices = { currentScreen = SovereignScreen.DEVICES },
                    onPrivacy = { currentScreen = SovereignScreen.PRIVACY },
                    onBackup = { currentScreen = SovereignScreen.BACKUP },
                    onCommunities = { currentScreen = SovereignScreen.COMMUNITIES },
                    onProfile = { currentScreen = SovereignScreen.PROFILE },
                    onEvents = { currentScreen = SovereignScreen.EVENTS },
                    onPolls = { currentScreen = SovereignScreen.POLLS },
                    onSms = { currentScreen = SovereignScreen.SMS }
                )
            }
        }
    }

if (showCreate) CreateSheet(
        publishing = feed.state == FeedState.Publishing,
        contacts = directory.contacts,
        onDismiss = { showCreate = false },
        onPost = { text -> feed.create(text) { showCreate = false } },
        onPoll = { question, options, hours -> feed.createPoll(question, options, hours) { showCreate = false } },
        onStory = { visibleTo, audience ->
            showCreate = false
            pendingStoryVisibility = visibleTo
            pendingStoryAudience = audience
            createStoryPicker.launch(arrayOf("image/*", "video/*"))
        },
        onTextStory = { text, color, visibleTo, audience -> stories.createTextStory(text, color, visibleTo, audience); showCreate = false },
        onLive = { showCreate = false; LiveStreamService.start(context, "stream-${account.redId}", account.redId, true) },
        onExplore = { showCreate = false; currentScreen = SovereignScreen.EXPLORE }
    )
    if (showSettings) YounesSettingsSheet(account, settings, viewModel, viewModel::logout) { showSettings = false }
    UnifiedCallOverlays()

    // 🔧 إصلاح العيب: dialer لإدخال RED ID والاتصال 1-1 صوت/فيديو (بدل تحويل لـ DINSTAR)
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
        Text("يونس • @$username", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(redId, color = AqyalCyanGlow, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    IconButton(onSearch) { Icon(Icons.Default.Search, "البحث الشامل") }
    IconButton(onSettings) { Icon(Icons.Default.Settings, "الإعدادات") }
}



@Composable private fun Avatar(text: String) = Box(Modifier.size(42.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) { Text(text, color = Color.Black, fontWeight = FontWeight.Black) }


@Composable
private fun UnifiedCallsScreen(ownUserId: String, history: CallHistoryViewModel, contacts: List<com.red.sovereign.contacts.PublicRedProfile>, onlineIds: Set<String> = emptySet(), myDisplayName: String = "", onExplore: () -> Unit, onPstn: () -> Unit = {}) {
    var filter by remember { mutableStateOf("الكل") }
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showDinstarDialog by remember { mutableStateOf(false) }
    var showGroupCallPicker by remember { mutableStateOf(false) }
    var showPublicStreamsSearchDialog by remember { mutableStateOf(false) }
    var publicStreamSearchQuery by remember { mutableStateOf("") }
    var dinstarNumberInput by remember { mutableStateOf("") }
    var newCallTargetInput by remember { mutableStateOf("") }
    var isSpaceHost by remember { mutableStateOf(false) }
    var isBroadcaster by remember { mutableStateOf(false) }
    var roomInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val visible = history.calls.filter { call -> when (filter) {
        "فائتة" -> call.status == "MISSED"; "صوت" -> call.type == "VOICE"; "فيديو" -> call.type == "VIDEO"
        "جماعية" -> call.type == "GROUP"; "بث" -> call.type == "LIVE"; "مساحات" -> call.type == "SPACE"
        "DINSTAR" -> call.route == "DINSTAR"; else -> true
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("مركز المكالمات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("الفردي يرن. المؤتمر والمساحة والبث أزرار مستقلة هنا — ليست أزرار الدردشة.", color = Color.LightGray, fontSize = 13.sp)
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
            needCamera = false,
            onGranted = { showJoinDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون للانضمام للمؤتمر", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val liveLauncher = rememberCallPermissionLauncher(
            needCamera = false,
            onGranted = { showLiveDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون للبث أو المشاهدة بالمشاركة", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val spaceLauncher = rememberCallPermissionLauncher(
            needCamera = false,
            onGranted = { showSpaceDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون لدخول المساحة الصوتية", android.widget.Toast.LENGTH_SHORT).show() }
        )

        CallsHubLaunchers(
            onGroupCallPicker = { groupCallLauncher() },
            onConference = { conferenceLauncher() },
            onLive = { liveLauncher() }
        )
        Spacer(Modifier.height(16.dp))
        Text("السجل", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("الكل", "فائتة", "صوت", "فيديو", "جماعية", "بث", "مساحات", "DINSTAR")) { title -> FilterChip(filter == title, { filter = title }, { Text(title) }) }
        }
        when {
            history.loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            history.error != null -> EmptyState(Icons.Default.History, "تعذر تحميل السجل", history.error.orEmpty())
            visible.isEmpty() -> EmptyState(Icons.Default.History, "لا توجد مكالمات", "ستظهر هنا كل المكالمات مع شارة توضح مسار يونس أو DINSTAR.")
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
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; roomInput = "" },
            title = { Text("الانضمام إلى مؤتمر جماعي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل اسم الغرفة أو معرف المؤتمر للاتصال الآمن عبر SFU:", color = Color.Gray, fontSize = 14.sp)
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف الغرفة (مثال: red-room-123)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinDialog = false
                        ConferenceService.join(context, roomInput.trim(), ownUserId, true, asHost = true)
                        roomInput = ""
                    },
                    enabled = roomInput.trim().isNotBlank()
                ) {
                    Text("انضمام")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false; roomInput = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    var streamTitleInput by remember { mutableStateOf("") }
    var isPrivateStream by remember { mutableStateOf(false) }
    var streamPasswordInput by remember { mutableStateOf("") }

    if (showLiveDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDialog = false; roomInput = ""; streamTitleInput = ""; streamPasswordInput = ""; isPrivateStream = false; isBroadcaster = false },
            title = { Text("مركز البث المباشر 🔴") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("انضم لمشاهدة بث عام عبر البحث بالاسم/المعرف أو أنشئ بثك الخاص:", color = Color.Gray, fontSize = 13.sp)
                    
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف البث أو رابط الدعوة (مثال: stream-123)") },
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isBroadcaster, onCheckedChange = { isBroadcaster = it })
                        Text("بدء البث كمنتج (Broadcaster)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (isBroadcaster) {
                        OutlinedTextField(
                            value = streamTitleInput,
                            onValueChange = { streamTitleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("عنوان البث (مثال: بث سيادي عام)") },
                            singleLine = true
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = isPrivateStream, onCheckedChange = { isPrivateStream = it })
                            Text("بث خاص بكلمة سر 🔒", fontSize = 14.sp)
                        }

                        if (isPrivateStream) {
                            OutlinedTextField(
                                value = streamPasswordInput,
                                onValueChange = { streamPasswordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("كلمة سر البث الخاص") },
                                singleLine = true
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveDialog = false
                        val finalStreamId = roomInput.trim().ifBlank { "stream_${UUID.randomUUID().toString().take(8)}" }
                        LiveStreamService.start(context, finalStreamId, ownUserId, isBroadcaster, streamTitleInput.trim().ifBlank { "بث مباشر يونس" })
                        roomInput = ""
                        streamTitleInput = ""
                        streamPasswordInput = ""
                        isPrivateStream = false
                    },
                    enabled = roomInput.trim().isNotBlank() || isBroadcaster
                ) {
                    Text(if (isBroadcaster) "إنشاء وبدء البث" else "انضمام للبث")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveDialog = false; roomInput = ""; streamTitleInput = ""; streamPasswordInput = ""; isPrivateStream = false; isBroadcaster = false }) {
                    Text("إلغاء")
                }
            }
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

    if (showDinstarDialog) {
        val operator = YemeniOperatorDetector.getOperatorInfo(dinstarNumberInput)
        AlertDialog(
            onDismissRequest = { showDinstarDialog = false; dinstarNumberInput = "" },
            title = { Text("لوحة اتصال الهاتف اليمني (DINSTAR GSM)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اتصال آمن ومباشر بأي رقم هاتف يمني ثابت أو محمول عبر بوابات Dinstar GSM:", color = Color.Gray, fontSize = 13.sp)
                    
                    OutlinedTextField(
                        value = dinstarNumberInput,
                        onValueChange = { dinstarNumberInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("أدخل الرقم (مثال: 777123456)") },
                        singleLine = true
                    )

                    if (operator != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(operator.brandColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("الشبكة المكتشفة: ${operator.name}", color = operator.brandColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(operator.technology, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDinstarDialog = false
                        dinstarNumberInput = ""
                        onPstn()
                    }
                ) {
                    Text("فتح الهاتف اليمني")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDinstarDialog = false; dinstarNumberInput = "" }) {
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
                        placeholder = { Text("معرف يونس (مثال: 10001)") },
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
                    else -> if (call.peerId.matches(RED_ID_PATTERN) && call.route != "DINSTAR") {
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
            Text(
                text = call.peerLabel.ifBlank { call.peerId },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (isMissed) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
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
                        if (call.route == "DINSTAR") append(" • عبر الهاتف")
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
                    else -> if (call.peerId.matches(RED_ID_PATTERN) && call.route != "DINSTAR") {
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
private fun RoundCallAction(icon: ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    FilledIconButton(onClick, Modifier.size(62.dp), enabled = enabled) { Icon(icon, title, tint = if (enabled) color else Color.Gray, modifier = Modifier.size(30.dp)) }
    Text(title, fontSize = 11.sp); if (!enabled) Text("قيد الربط", color = Color.Gray, fontSize = 9.sp)
}

@Composable
private fun DinstarPhoneScreen(
    account: AuthState.Authenticated,
    viewModel: AuthViewModel,
    history: CallHistoryViewModel? = null,
    prefillNumber: String? = null,
    onOpenSms: () -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    // 📞 أكثر الأرقام اليمنية اتصالًا — تُشتق من سجل DINSTAR الحقيقي (لا بيانات وهمية)
    val dinstarCalls = history?.calls?.filter { it.route == "DINSTAR" }.orEmpty()
    val favorites = dinstarCalls.groupingBy { it.peerLabel.ifBlank { it.peerId } }.eachCount()
        .entries.sortedByDescending { it.value }.take(8).map { it.key }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(35.dp)); Column(Modifier.padding(start = 12.dp)) {
                    Text("الهاتف اليمني عبر DINSTAR", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Text(if (account.pstnEnabled) "مصرح لك — مكالمات صوتية فقط" else "غير مفعل — يفعله المسؤول من اللوحة", fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                // 📨 مدخل رسائل الهاتف اليمني (SMS) — شاشة كاملة خارج هذه اللوحة
                IconButton(onClick = onOpenSms) {
                    Icon(Icons.Default.Sms, "رسائل الهاتف اليمني", tint = AqyalGold)
                }
            }
        }
        PrimaryTabRow(tab) {
            listOf(Icons.Default.Dialpad to "الأرقام", Icons.Default.Star to "المفضلة", Icons.Default.History to "السجل", Icons.Default.Contacts to "جهات الاتصال").forEachIndexed { i, item -> Tab(tab == i, { tab = i }, icon = { Icon(item.first, null) }, text = { Text(item.second, fontSize = 10.sp) }) }
        }
        when (tab) {
            0 -> DialPad(account.pstnEnabled, viewModel, initialNumber = prefillNumber)
            // ⭐ المفضلة — أكثر الأرقام اتصالًا عبر DINSTAR مع إعادة اتصال بنقرة
            1 -> if (favorites.isEmpty()) {
                EmptyState(Icons.Default.Star, "لا مفضلة بعد", "ستظهر هنا أكثر الأرقام اليمنية اتصالًا عبر DINSTAR تلقائيًا")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites.size) { i ->
                        val number = favorites[i]
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = AqyalGold)
                                Text(number, Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.Bold)
                                com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
                                    Text(op.name, color = op.brandColor, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                                }
                                IconButton(onClick = { if (account.pstnEnabled) com.red.sovereign.calls.PstnCallManager.start(context, number) }, enabled = account.pstnEnabled) {
                                    Icon(Icons.Default.Call, "اتصال", tint = if (account.pstnEnabled) YounesEmerald else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            // 🗂️ سجل DINSTAR الحقيقي — مفلتر من السجل الموحد
            2 -> if (dinstarCalls.isEmpty()) {
                EmptyState(Icons.Default.History, "لا مكالمات DINSTAR بعد", "ستظهر هنا كل مكالماتك الهاتفية اليمنية")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dinstarCalls.size) { i -> CallHistoryRow(dinstarCalls[i]) }
                }
            }
            else -> EmptyState(Icons.Default.Contacts, "جهات الاتصال", "اختر جهة من تبويب جهات الاتصال الرئيسي ثم اطلبها عبر DINSTAR")
        }
    }
}

@Composable
private fun DialPad(enabled: Boolean, viewModel: AuthViewModel, initialNumber: String? = null) {
    var number by remember(initialNumber) { mutableStateOf(initialNumber?.filter(Char::isDigit).orEmpty()) }
    val context = LocalContext.current
    // حالة مكالمة DINSTAR الحية — يعرضها الغلاف الموحد أيضاً
    val pstnLive = com.red.sovereign.calls.PstnCallRuntime.state
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number.ifEmpty { "أدخل الرقم" }, fontSize = 27.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton({ if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "حذف") }
        }
        com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Box(Modifier.size(8.dp).background(op.brandColor, CircleShape))
                Text("  ${op.name} (${op.technology})", color = op.brandColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { digit -> FilledIconButton({ number += digit }, Modifier.size(64.dp)) { Text(digit, fontSize = 23.sp) } } }
        }
        Button(
            { com.red.sovereign.calls.PstnCallManager.start(context, number) },
            enabled = enabled && number.filter(Char::isDigit).length >= 6 && pstnLive == com.red.sovereign.calls.PstnCallState.Idle,
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Default.Call, null); Text(" اتصال صوتي عبر DINSTAR") }
        when (val live = pstnLive) {
            is com.red.sovereign.calls.PstnCallState.Preparing -> Text("جارٍ تجهيز الجسر الصوتي…", color = AqyalGold, fontSize = 13.sp)
            is com.red.sovereign.calls.PstnCallState.Ringing -> Text("جارٍ الرنين على الشبكة — رنين الناقل الحقيقي", color = AqyalGold, fontSize = 13.sp)
            is com.red.sovereign.calls.PstnCallState.Active -> Text("المكالمة نشطة (${(System.currentTimeMillis() - live.startedAt) / 1000} ث) — التحكم في الغلاف الحي", color = YounesEmerald, fontSize = 13.sp)
            is com.red.sovereign.calls.PstnCallState.Failed -> Text(live.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            is com.red.sovereign.calls.PstnCallState.Ended -> Text("انتهت المكالمة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(
    publishing: Boolean,
    contacts: List<com.red.sovereign.contacts.PublicRedProfile>,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
    onPoll: (String, List<String>, Int) -> Unit,
    onStory: (String, List<String>) -> Unit,
    onTextStory: (String, String, String, List<String>) -> Unit,
    onLive: () -> Unit,
    onExplore: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "", "")) }
    var pollHours by remember { mutableIntStateOf(24) }
    var storyText by remember { mutableStateOf("") }
    var storyBackground by remember { mutableStateOf("#1565C0") }
    var storyVisibility by remember { mutableStateOf("EVERYONE") }
    val storyAudience = remember { mutableStateListOf<String>() }
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
                            singleLine = true
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
                    Button({ onPoll(pollQuestion, pollOptions, pollHours) }, Modifier.fillMaxWidth(), enabled = validPoll && !publishing) { if (publishing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("نشر الاستطلاع") }
                }
                "story_media", "story_text" -> {
                    if (mode == "story_media") {
                        Text("اختر وسائط الحالة ثم حدد من يشاهدها:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    } else {
                        OutlinedTextField(
                            value = storyText,
                            onValueChange = { storyText = it.take(500) },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            placeholder = { Text("اكتب حالة قصيرة ومميزة…") },
                            maxLines = 7
                        )
                        Text("لون الخلفية", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("#1565C0", "#7B1FA2", "#00695C", "#C62828", "#263238").forEach { color ->
                                val parsed = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.Gray)
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(parsed)
                                        .border(if (storyBackground == color) 3.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { storyBackground = color }
                                )
                            }
                        }
                    }
                    Text("من يستطيع المشاهدة؟", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("EVERYONE" to "الجميع", "CONTACTS" to "جهاتي", "SELECTED" to "محددون").forEach { (value, label) ->
                            FilterChip(
                                selected = storyVisibility == value,
                                onClick = { storyVisibility = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (storyVisibility == "SELECTED") {
                        if (contacts.isEmpty()) {
                            Text("لا توجد جهات اتصال — أضف أشخاصاً أولاً", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                                items(contacts, key = { it.redId }) { contact ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            if (contact.redId in storyAudience) storyAudience.remove(contact.redId)
                                            else storyAudience.add(contact.redId)
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = contact.redId in storyAudience, onCheckedChange = {
                                            if (contact.redId in storyAudience) storyAudience.remove(contact.redId)
                                            else storyAudience.add(contact.redId)
                                        })
                                        Text(contact.displayName.ifBlank { contact.username }, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                        Text(contact.redId, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            Text("اخترت ${storyAudience.size} شخصاً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = {
                            val audience = if (storyVisibility == "SELECTED") storyAudience.toList() else emptyList()
                            if (mode == "story_media") onStory(storyVisibility, audience)
                            else onTextStory(storyText.trim(), storyBackground, storyVisibility, audience)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (mode == "story_media" || storyText.trim().isNotEmpty()) && !publishing &&
                            (storyVisibility != "SELECTED" || storyAudience.isNotEmpty())
                    ) { Text(if (mode == "story_media") "اختيار صورة أو فيديو" else "نشر الحالة النصية") }
                }
                else -> {
                    CreateOption(Icons.Default.DynamicFeed, "منشور أو سلسلة", "نص طويل، اقتباس، نقاش محلي", true) { mode = "post" }
                    CreateOption(Icons.Default.Forum, "استطلاع تفاعلي", "سؤال وخيارات وتصويت فعلي عبر الخادم", true) { mode = "poll" }
                    CreateOption(Icons.Default.AddCircle, "حالة 24 ساعة", "صورة أو فيديو يُحذف تلقائياً", true) { mode = "story_media" }
                    CreateOption(Icons.Default.Edit, "حالة نصية", "نص ملون يختفي تلقائياً بعد 24 ساعة", true) { mode = "story_text" }
                    CreateOption(Icons.Default.LiveTv, "بث مباشر", "فيديو عبر SFU المحلي", true, onLive)
                    CreateOption(Icons.Default.Explore, "استكشاف يونس", "اكتشف البثوث والغرف الصوتية النشطة", true, onExplore)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun CreateOption(icon: ImageVector, title: String, detail: String, enabled: Boolean, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = if (enabled) AqyalGold else Color.Gray, modifier = Modifier.size(31.dp)); Column(Modifier.padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color.Unspecified else Color.Gray); Text(detail, color = Color.Gray, fontSize = 12.sp) } } }

@Composable private fun EmptyState(icon: ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }

private val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }

private fun messageDisplayText(message: DecryptedMessage): String =
    when (message.type) {
        "RICH_TEXT" -> RichMessage.decode(message.plaintext)?.text.orEmpty()
        "GROUP_MESSAGE" -> {
            val text = message.plaintext.toString(Charsets.UTF_8)
            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.AttachmentManifest>(text) }.getOrNull()?.name
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.VoiceManifest>(text) }.getOrNull()?.name
                ?: text
        }
        else -> message.plaintext.toString(Charsets.UTF_8)
    }

private object PollVoteStore {
    val votersByPoll = androidx.compose.runtime.mutableStateMapOf<String, Map<String, Int>>()
    fun record(pollId: String, voter: String, optionIndex: Int?) {
        val next = (votersByPoll[pollId] ?: emptyMap()).toMutableMap()
        if (optionIndex == null) next.remove(voter) else next[voter] = optionIndex
        votersByPoll[pollId] = next
    }
    fun counts(pollId: String, optionCount: Int): List<Int> {
        val counts = IntArray(optionCount)
        votersByPoll[pollId]?.values?.forEach { idx -> if (idx in counts.indices) counts[idx]++ }
        return counts.toList()
    }
    fun myVote(pollId: String, me: String): Int? = votersByPoll[pollId]?.get(me)
}

@Composable
internal fun RichTextMessage(
    message: DecryptedMessage,
    conversation: List<DecryptedMessage>,
    myRedId: String? = null,
    onPollVote: ((String, Int?) -> Unit)? = null
) {
    val rich = RichMessage.decode(message.plaintext)
    if (rich == null) { Text("رسالة غير صالحة", color = MaterialTheme.colorScheme.error); return }
    rich.replyTo?.let { replyId -> conversation.firstOrNull { it.id == replyId }?.let { quoted -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f))) { Text(messageDisplayText(quoted), Modifier.padding(7.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall) } } }
    if (rich.forwardOf != null) Text("معاد توجيهها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    if (rich.action == "CALL_STARTED") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            val isVid = rich.text.contains("فيديو")
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (message.outgoing) Color(0x33000000) else YounesEmerald.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (isVid) Icons.Default.Videocam else Icons.Default.Headset, null, tint = if (message.outgoing) Color(0xFF003023) else YounesEmerald, modifier = Modifier.size(14.dp))
            }
            Text("مكالمة نشطة", style = MaterialTheme.typography.labelSmall, color = if (message.outgoing) Color(0xFF003023) else YounesEmerald, fontWeight = FontWeight.Bold)
        }
    }

    val annotated = remember(rich.text, rich.mentions, rich.hashtags) {
        val t = rich.text
        val mentions = rich.mentions + RED_ID_PARTIAL.findAll(t).map { it.value }.toList()
        val hashtags = rich.hashtags + HASHTAG_PARTIAL.findAll(t).map { it.value }.toList()
        androidx.compose.ui.text.buildAnnotatedString {
            append(t)
            mentions.forEach { m -> val idx = t.indexOf(m); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = YounesEmerald, fontWeight = FontWeight.Bold), idx, idx + m.length) }
            hashtags.forEach { h -> val idx = t.indexOf(h); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = AqyalCyanGlow), idx, idx + h.length) }
        }
    }
    Text(annotated, color = if (message.outgoing) Color(0xFF001B14) else MaterialTheme.colorScheme.onSurface)
    rich.poll?.let { poll ->
        InlinePollCard(poll, isOutgoing = message.outgoing, myRedId = myRedId, onVote = onPollVote)
    }
    rich.expiresAt?.let {
        val remaining = (it - System.currentTimeMillis()).coerceAtLeast(0)
        val label = when {
            remaining <= 0 -> "انتهت"
            remaining < 3600000 -> "${remaining/60000}د"
            remaining < 86400000 -> "${remaining/3600000}س"
            else -> "${remaining/86400000}ي"
        }
        Text("⏳ مؤقتة • $label", style = MaterialTheme.typography.labelSmall, color = AqyalGold)
    }
    if (rich.mentions.isNotEmpty()) Text("ذكر: ${rich.mentions.joinToString()}", style = MaterialTheme.typography.labelSmall, color = YounesEmerald)
}

@Composable
private fun InlinePollCard(
    poll: com.red.sovereign.core.InlinePoll,
    isOutgoing: Boolean,
    myRedId: String? = null,
    onVote: ((String, Int?) -> Unit)? = null
) {
    // تصويتات متزامنة E2EE (المجموعات)؛ وإلا يعرض البطاقة محلياً فقط
    val synced = myRedId != null && onVote != null
    val votes = if (synced) PollVoteStore.counts(poll.pollId, poll.options.size) else poll.votes
    val myVote = if (synced) PollVoteStore.myVote(poll.pollId, myRedId!!) else null
    val total = votes.sum().coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, null, tint = YounesEmerald, modifier = Modifier.size(18.dp))
                Text(" استطلاع المجموعة", style = MaterialTheme.typography.labelMedium, color = YounesEmerald, fontWeight = FontWeight.Bold)
            }
            Text(poll.question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            poll.options.forEachIndexed { index, option ->
                val optionVotes = votes.getOrElse(index) { 0 }
                val ratio = (optionVotes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val isSelected = if (synced) myVote == index else myVote == index
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = !poll.isClosed && onVote != null) {
                        if (onVote != null) onVote(poll.pollId, if (myVote == index) null else index)
                    },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) YounesEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (poll.isClosed || myVote != null) Text("${(ratio * 100).toInt()}%", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (poll.isClosed || myVote != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = YounesEmerald, trackColor = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
            Text(
                if (synced) "إجمالي الأصوات: $total · صوتك: ${myVote?.let { poll.options.getOrNull(it) } ?: "لا شيء"}"
                else "إجمالي الأصوات: $total",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceRecordingControls(
    voiceState: VoiceMessageState.Recording,
    voiceMessages: VoiceMessageViewModel,
    isLocked: Boolean,
    cancelProgress: Float
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dragOffsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var dragOffsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

    Column {
        // ⏺️ شريط التسجيل العلوي
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (voiceState.paused) AqyalGold else MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(6.dp))
            Text(
                if (voiceState.paused) "متوقف مؤقتًا ${formatDuration(voiceMessages.elapsedSeconds)}"
                else "● تسجيل ${formatDuration(voiceMessages.elapsedSeconds)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(voiceMessages::togglePause) {
                Icon(if (voiceState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (voiceState.paused) "استئناف" else "إيقاف مؤقت")
            }
            TextButton(voiceMessages::cancel) { Text("إلغاء") }
        }
        VoiceWaveform(voiceMessages.waveform, MaterialTheme.colorScheme.error, Modifier.fillMaxWidth().height(34.dp))

        // 🎚️ منطقة السحب — إذا السحب لليسار/الأسفل = إلغاء تدريجي
        if (cancelProgress > 0f) {
            Text(
                "↩️ اسحب لمعاودة التسجيل • ${(cancelProgress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // 🔒 إذا قُفل التسجيل، اعرض أزرار الإرسال والإلغاء
        if (isLocked) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = voiceMessages::cancel,
                    modifier = Modifier.weight(1f)
                ) { Text("حذف") }
                // الإرسال يتم عبر زر الإرسال الرئيسي في شريط الكتابة
                OutlinedButton(
                    onClick = { /* triggered via main send button */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) { Text("🔒 مُقفل — استخدم زر الإرسال") }
            }
        } else {
            // 🔓 نصيحة للمستخدم: اسحب للقفل أو ارفع الإصبع للإرسال
            Text(
                "💡 اسحب للأعلى للقفل • ارفع الإصبع للإرسال • اسحب للأسفل للإلغاء",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun VoicePreviewControls(
    duration: Int,
    waveform: List<Int>,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    isSending: Boolean
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = YounesEmerald)
            Spacer(Modifier.width(6.dp))
            Text(
                "معاينة الرسالة الصوتية • ${formatDuration(duration)}",
                color = YounesEmerald,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        VoiceWaveform(waveform, YounesEmerald, Modifier.fillMaxWidth().height(34.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                enabled = !isSending
            ) {
                Icon(Icons.Default.Close, null); Text(" حذف")
            }
            Button(
                onClick = onSend,
                modifier = Modifier.weight(1f),
                enabled = !isSending && duration >= 1
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else { Icon(Icons.Default.Send, null); Text(" إرسال") }
            }
        }
    }
}

@Composable
private fun VoiceWaveform(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val samples = values.ifEmpty { List(24) { 8 } }
        val step = size.width / samples.size.coerceAtLeast(1)
        samples.forEachIndexed { index, value ->
            val height = (size.height * (value.coerceIn(4, 100) / 100f)).coerceAtLeast(3f)
            val x = step * index + step / 2
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2), end = androidx.compose.ui.geometry.Offset(x, (size.height + height) / 2), strokeWidth = (step * .42f).coerceIn(2f, 7f), cap = StrokeCap.Round)
        }
    }
}

/** عرض رسالة ملصق — إيموجي كبير كمعاينة (الصورة الفعلية تُحمّل عند التوفر). */
@Composable
internal fun StickerMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val payload = remember(item.id) {
        runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(item.plaintext.toString(Charsets.UTF_8)) }.getOrNull()
    }
    if (payload == null) {
        Text("ملصق غير صالح", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        return
    }
    // تحميل صورة الملصق الفعلية عبر MediaApi (الملصقات سيادية غير مشفّرة E2EE)
    val context = LocalContext.current
    var stickerBitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        if (payload.mediaKey.isNotBlank()) {
            val tokens = com.red.sovereign.auth.TokenStore(context)
            val media = com.red.sovereign.media.MediaApi(context, com.red.sovereign.auth.AuthorizedApiClient(tokens))
            val path = if (payload.mediaKey.startsWith("/api/media/")) payload.mediaKey else "/api/media/$payload.mediaKey"
            runCatching {
                when (val r = media.download(path, 10 * 1024 * 1024)) {
                    is com.red.sovereign.auth.ApiResult.Success -> BitmapFactory.decodeByteArray(r.value, 0, r.value.size)?.asImageBitmap()
                    is com.red.sovereign.auth.ApiResult.Error -> null
                }
            }.getOrNull()?.let { stickerBitmap = it }
        }
    }
    val bitmap = stickerBitmap
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = payload.name ?: "ملصق",
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(14.dp))
        )
    } else {
        // معاينة الإيموجي أثناء التحميل أو عند التعذر
        Text(payload.emoji, fontSize = 64.sp)
    }
}

@Composable
internal fun VoiceMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<VoiceManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("رسالة صوتية غير صالحة", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    val isDownloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> true
        is AttachmentState.Exported -> true
        else -> false
    }
    val isDownloading = attachments.getDownloadState(item.id) is AttachmentState.Working
    val downloadError = (attachments.getDownloadState(item.id) as? AttachmentState.Error)?.message
    val downloadedUri = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> android.net.Uri.fromFile(java.io.File(current.path))
        is AttachmentState.Exported -> android.net.Uri.fromFile(java.io.File(current.path))
        else -> null
    }

    if (downloadedUri != null) {
        // 🎙️ مشغّل احترافي مع waveform
        VoiceNotePlayer(
            uri = downloadedUri,
            waveform = manifest.waveform,
            durationSeconds = manifest.durationSeconds,
            isOutgoing = item.outgoing,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // 💬 فقاعة احترافية قبل التنزيل
        VoiceBubble(
            manifest = manifest,
            isOutgoing = item.outgoing,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            errorMessage = downloadError,
            onPlayPause = { attachments.download(item.id, manifestJson) },
            onSeek = { /* no-op before download */ },
            onSpeedChange = { /* no-op before download */ },
            onDownload = { attachments.download(item.id, manifestJson) },
            onWaveformTap = { attachments.download(item.id, manifestJson) }
        )
    }
}

@Composable
internal fun AttachmentMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("مرفق مشفر غير صالح", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    when {
        manifest.mimeType.startsWith("image/") -> ImageMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("video/") -> VideoMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("audio/") -> AudioMessage(item, manifest, attachments)
        else -> FileMessage(item, manifest, attachments)
    }
}

/** فتح مرفق مُفكَّك عبر FileProvider — آمن على Android 7+ (بدل Uri.fromFile الممنوع). */
private fun openAttachment(context: android.content.Context, path: String) {
    runCatching {
        val file = java.io.File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
        val mime = context.contentResolver.getType(uri) ?: run {
            val ext = file.extension.lowercase()
            if (ext.isBlank()) "*/*" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "فتح المرفق"))
    }
}

/** شارة "محفوظ في الملفات" تظهر بعد اكتمال الحفظ التلقائي في التنزيلات. */
@Composable
private fun SavedBadge(item: DecryptedMessage, attachments: AttachmentViewModel, modifier: Modifier = Modifier) {
    if (attachments.savedDownloadUri(item.id) != null) {
        Surface(modifier.padding(6.dp), shape = RoundedCornerShape(8.dp), color = YounesEmerald.copy(alpha = 0.9f)) {
            Text("✓ محفوظ في الملفات", color = Color(0xFF002118), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun ImageMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
    if (downloaded != null) {
        val file = java.io.File(downloaded.first)
        val bitmap = remember(file.lastModified()) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }
        if (bitmap != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.foundation.Image(
                    bitmap, contentDescription = "صورة",
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        openAttachment(context, file.absolutePath)
                    },
                    contentScale = ContentScale.Crop
                )
                SavedBadge(item, attachments, Modifier.align(Alignment.TopEnd))
                // شارة الحجم والتحقق المشفر
                Surface(Modifier.padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Text(" ✓ مشفرة • ${formatBytes(manifest.size)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (isWorking) {
                    CircularProgressIndicator(color = YounesEmerald, strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("جارٍ فك التشفير…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(manifest.name.take(24), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    Text("${formatBytes(manifest.size)} • مشفرة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = YounesEmerald) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, "تنزيل", tint = Color(0xFF002118)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded != null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                StoryVideoPlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(52.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    onClick = { openAttachment(context, downloaded.first) }
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                }
                SavedBadge(item, attachments, Modifier.align(Alignment.TopEnd))
            }
        }
    } else {
        val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(YounesEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = YounesEmerald, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("فيديو مشفر · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
                else IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                    Icon(Icons.Default.Download, "تنزيل الفيديو", tint = YounesEmerald)
                }
            }
        }
    }
}

@Composable
private fun AudioMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded != null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(AqyalCyanGlow.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(20.dp))
                    }
                    Text(manifest.name, Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("✓ مشفرة", color = YounesEmerald, fontSize = 10.sp)
                    SavedBadge(item, attachments)
                }
                VoiceNotePlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), isOutgoing = item.outgoing, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AqyalCyanGlow.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("صوت مشفر · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.sendState !is AttachmentState.Working) {
                    Icon(Icons.Default.Download, "تنزيل الصوت")
                }
            }
        }
    }
}

@Composable
private fun FileMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    val fileColor = when {
        manifest.mimeType.contains("pdf") -> AqyalCyanGlow
        manifest.mimeType.contains("zip") || manifest.mimeType.contains("compressed") -> AqyalGold
        manifest.mimeType.contains("text") || manifest.mimeType.contains("word") -> Color(0xFF4FC3F7)
        manifest.mimeType.contains("sheet") || manifest.mimeType.contains("excel") -> YounesEmerald
        manifest.mimeType.contains("presentation") || manifest.mimeType.contains("powerpoint") -> Color(0xFFF06292)
        else -> AqyalCyanGlow
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(fileColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = fileColor, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${manifest.mimeType} · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
            }
            if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
            else if (downloaded != null) {
                // 📂 بعد التحميل: فتح الملف + شارة الحفظ التلقائي في التنزيلات
                SavedBadge(item, attachments)
                IconButton({ openAttachment(context, downloaded.first) }, enabled = !isWorking) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "فتح الملف", tint = YounesEmerald)
                }
            } else IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                Icon(Icons.Default.Download, "تنزيل وفك تشفير المرفق", tint = YounesEmerald)
            }
        }
    }
}

private fun shouldAutoDownload(context: android.content.Context, sizeBytes: Long): Boolean =
    RedQualityManager.shouldAutoDownload(context, sizeBytes)

private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val d1 = cal.get(java.util.Calendar.DAY_OF_YEAR); val y1 = cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = b
    return y1 == cal.get(java.util.Calendar.YEAR) && d1 == cal.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun dateLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> "اليوم"
        isSameDay(timestamp, now - 86400000L) -> "أمس"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60000
    return when {
        diff < 60000 -> "الآن"
        diff < 3600000 -> "${min}د"
        diff < 86400000 -> "${diff / 3600000}س"
        diff < 172800000 -> "أمس"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

/** وقت الساعة داخل الفقاعة (مثل واتساب: 4:20 م / 11:05 ص). */
private fun formatClockTime(timestamp: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(timestamp))

@Composable
private fun MessageActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(YounesEmerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = YounesEmerald, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

/** قائمة الإيموجي السريعة للتفاعل — تظهر أعلى قائمة إجراءات الرسالة. */
@Composable
private fun ReactionEmojiBar(onPick: (String) -> Unit) {
    val quick = remember { listOf("👍", "❤️", "😂", "🙏", "🔥", "👏", "😮", "😢", "🎉", "💯") }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        items(quick) { emoji ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp).clickable { onPick(emoji) }
            ) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun EmojiPicker(onEmoji: (String) -> Unit) {
    var category by remember { mutableIntStateOf(0) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                items(EMOJI_CATEGORIES.indices.toList()) { index ->
                    FilterChip(selected = category == index, onClick = { category = index }, label = { Text(EMOJI_CATEGORIES[index].first) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                items(EMOJI_CATEGORIES[category].second) { emoji ->
                    TextButton({ onEmoji(emoji) }) { Text(emoji, fontSize = 24.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    onDismiss: () -> Unit
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = MaterialTheme.colorScheme.surface
) {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إرفاق", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).clickable(onClick = { onCamera(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Camera, null, tint = YounesEmerald, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("الكاميرا", fontWeight = FontWeight.Medium)
                Text("التقط صورة أو فيديو", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onGallery(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Photo, null, tint = AqyalCyanGlow, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("المعرض", fontWeight = FontWeight.Medium)
                Text("اختر من الصور والفيديوهات", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onDocument(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = AqyalGold, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("ملف", fontWeight = FontWeight.Medium)
                Text("PDF، مستندات، مضغوطات", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// مصدر الحقيقة الوحيد: core/YounesId.kt. النمط كان مكرّرًا هنا وفي
// QrScannerSheet وSafetyViewModel بصياغات متباينة، فكان معرّف يقبله
// أحدها وترفضه الشاشة التالية.
private val RED_ID_PATTERN = Regex(YounesId.PATTERN)
// نسخة بدون ^ و $ لاستخدامها داخل نص (مثل @12345)
private val RED_ID_PARTIAL = Regex(YounesId.MENTION_PATTERN)
// الهاشتاجات العربية/اللاتينية
private val HASHTAG_PARTIAL = Regex("#[\\w\\u0600-\\u06FF]{2,30}")
// اسم المستخدم للـ @ autocomplete
private val USERNAME_PARTIAL = Regex("@([A-Za-z0-9_.]{1,20})$")
// الهاشتاج لـ # autocomplete
private val HASHTAG_AUTOCOMPLETE = Regex("#([\\w\\u0600-\\u06FF]{1,20})$")
private val EMOJI_CATEGORIES = listOf(
    "سريعة" to listOf("😀", "😂", "😍", "👍", "❤️", "🔥", "👏", "🙏", "🎉", "😢", "😮", "✅"),
    "الوجوه" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "🙃", "😉", "😊", "🥰", "😍", "🤩", "😘", "😋", "😎", "🤔", "😴", "😭", "😡", "🥳"),
    "الإشارات" to listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "🙌", "🫶", "🤝", "🙏", "💪", "👀", "❤️", "💚", "💛", "💙"),
    "الأشياء" to listOf("📱", "💻", "⌚", "📷", "🎥", "🎙️", "🔒", "🔑", "💡", "📌", "📎", "📁", "📄", "📚", "🎁", "🏆", "✅", "⚠️"),
    "الطبيعة" to listOf("🌙", "☀️", "⭐", "🔥", "🌈", "🌹", "🌿", "🌳", "🌊", "⛰️", "🐪", "🦅", "🐝", "🦋"),
    "الطعام" to listOf("☕", "🍵", "🥤", "🍞", "🥐", "🍚", "🍗", "🥗", "🍎", "🍉", "🍇", "🍯", "🎂"),
    "السفر" to listOf("🚗", "🚕", "🚌", "✈️", "🚁", "🚢", "🗺️", "🏠", "🏢", "🏥", "🏫", "🕌", "⛺"),
    "الرموز" to listOf("✅", "❌", "⚠️", "❗", "❓", "💯", "➕", "➖", "♻️", "🔴", "🟢", "🟡", "🔵", "🇾🇪")
)

private fun conversationId(first: String, second: String): String {
    if (first.isBlank() || second.isBlank()) return "pending-conversation"
    val canonical = listOf(first, second).sorted().joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}






