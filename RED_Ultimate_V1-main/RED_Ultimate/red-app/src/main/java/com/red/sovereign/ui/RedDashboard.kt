package com.red.sovereign.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
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
import com.red.sovereign.calls.YemeniOperatorDetector
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
import com.red.sovereign.settings.PstnConfigScreen
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.settings.YounesSettingsSheet
import com.red.sovereign.auth.SmsIncomingMessage
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
import com.red.sovereign.ui.theme.SovereignGradients
import com.red.sovereign.ui.components.PstnStatusIndicator
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
import com.red.sovereign.features.media.MediaGalleryDialog
import com.red.sovereign.features.profile.BackupScreen
import com.red.sovereign.features.profile.ProfileScreen
import com.red.sovereign.core.YounesId
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.media.EventsScreen
import com.red.sovereign.media.PollsScreen
import com.red.sovereign.ui.theme.CairoFamily
import com.red.sovereign.ui.theme.TajawalFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.components.SovereignBottomBar
import androidx.compose.material3.OutlinedTextFieldDefaults



private enum class SovereignScreen { DASHBOARD, DEVICES, PRIVACY, EXPLORE, CREATE_GROUP, BACKUP, GROUP_INFO, SEARCH, COMMUNITIES, CONTACTS, PROFILE, EVENTS, POLLS, DINSTAR_ADMIN, PSTN_CONFIG }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedDashboard(account: AuthState.Authenticated, viewModel: AuthViewModel, deepLinkSender: String? = null, deepLinkConversation: String? = null) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(SovereignScreen.DASHBOARD) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(MainSection.CHATS) } // الأفضل من واتساب: الدردشات أولاً (الأكثر استخداماً)
    // 🔗 فتح محادثة خاصة من قائمة أعضاء المجموعة أو جهات الاتصال (يتغذى على deepLinkSender في ChatHubScreen)
    var pendingChatTarget by remember { mutableStateOf<String?>(null) }
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
    var showDinstar by remember { mutableStateOf(false) }
    // Ø±Ù‚Ù… Ù…ÙØ¹Ø¨Ù‘Ø£ Ù…Ø³Ø¨Ù‚Ù‹Ø§ Ù„Ø´Ø§Ø´Ø© Ø§Ù„Ù‡Ø§ØªÙ â€” ÙŠØµÙ„ Ù…Ù† Ù„ÙˆØ­Ø© Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„Ø³Ø±ÙŠØ¹Ø© ÙƒÙŠ Ù„Ø§ ÙŠÙØ¹Ø§Ø¯ Ø¥Ø¯Ø®Ø§Ù„Ù‡
    var dinstarPrefill by remember { mutableStateOf("") }
    var chatConversationOpen by remember { mutableStateOf(false) }
    // ðŸ”§ Ø¥ØµÙ„Ø§Ø­ Ø§Ù„Ø¹ÙŠØ¨: dialer Ø­Ù‚ÙŠÙ‚ÙŠ Ù„Ø¥Ø¯Ø®Ø§Ù„ RED ID ÙˆØ§Ù„Ø§ØªØµØ§Ù„ 1-1 Ù…Ù† CALLS section
    var showCallDialer by remember { mutableStateOf(false) }
    var dialerRedId by remember { mutableStateOf("") }
    var dialerVideo by remember { mutableStateOf(false) }
    var pendingDialerTarget by remember { mutableStateOf<String?>(null) }
    var pendingDialerVideo by remember { mutableStateOf(false) }
    // ðŸ”´ Ø§Ù„Ø¨Ø« Ø§Ù„Ù…Ø¨Ø§Ø´Ø± â€” Ø®ÙŠØ§Ø± Ø®Ø§Øµ/Ø¹Ø§Ù… Ø¨ÙƒÙ„Ù…Ø© Ø³Ø±
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
            showDinstar -> showDinstar = false
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

    // ðŸ”” Overlays must be global â€” before early return so they appear on ANY screen (Devices, Privacy, etc.)
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
                    }
                )
            }
            SovereignScreen.SEARCH -> RedGlobalSearch(onBack = { currentScreen = SovereignScreen.DASHBOARD })
            SovereignScreen.DINSTAR_ADMIN -> {
                val dm = remember { com.red.sovereign.features.dinstar.DinstarViewModel(viewModel.getApplication()) }
                com.red.sovereign.features.dinstar.DinstarAdminScreen(dm, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.PSTN_CONFIG -> {
                val dm = remember { com.red.sovereign.features.dinstar.DinstarViewModel(viewModel.getApplication()) }
                PstnConfigScreen(onBack = { currentScreen = SovereignScreen.DASHBOARD }, tokenStore = TokenStore(context), snackbarHostState = remember { SnackbarHostState() })
            }
            SovereignScreen.COMMUNITIES -> {
                val tokens = remember { TokenStore(context) }
                CommunitiesScreen(tokens = tokens, onBack = { currentScreen = SovereignScreen.DASHBOARD })
            }
            SovereignScreen.CONTACTS -> ContactsScreen(directory = directory, onBack = { currentScreen = SovereignScreen.DASHBOARD }, onChat = { person -> currentScreen = SovereignScreen.DASHBOARD; section = MainSection.CHATS }, onCall = { person, video -> com.red.sovereign.calls.YounesCallService.start(context, person.redId, video) }, onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP })
            else -> currentScreen = SovereignScreen.DASHBOARD
        }
        // Still show call overlays even when not on dashboard â€” unified
        UnifiedCallOverlays()
        return
    }

    Scaffold(
        containerColor = SovereignColors.ObsidianDeep,
        floatingActionButton = {
            if (!showDinstar && !chatConversationOpen) when (section) {
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
                    showDinstar = false
                    if (item == MainSection.CALLS) {
                        callHistory.load()
                        directory.refreshPresence()
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(SovereignColors.ObsidianDeep)) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                RedTopBar(account.redId, account.username, compact = SettingsRuntime.current.compactMode, onSettings = { showSettings = true }, onSearch = { currentScreen = SovereignScreen.SEARCH })
            when {
                showDinstar -> DinstarPhoneScreen(account, viewModel, callHistory, prefillNumber = dinstarPrefill)
                section == MainSection.HOME -> FeedScreen(account, feed, stories, onCreate = { showCreate = true })
                section == MainSection.CHATS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = false, deepLinkSender = pendingChatTarget ?: deepLinkSender, deepLinkConversation = deepLinkConversation, onConversationOpen = { chatConversationOpen = it })
                section == MainSection.GROUPS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = true, onManageGroup = { id -> selectedGroupId = id; currentScreen = SovereignScreen.GROUP_INFO }, onCreateGroup = { currentScreen = SovereignScreen.CREATE_GROUP }, onConversationOpen = { chatConversationOpen = it })
                section == MainSection.CALLS -> UnifiedCallsScreen(account.redId, callHistory, onExplore = {
                    currentScreen = SovereignScreen.EXPLORE
                }, onPstn = { dinstarPrefill = ""; showDinstar = true })
                else -> MoreScreen(
                    account,
                    onDinstar = { showDinstar = true },
                    onAdmin = { if (account.isAdmin) currentScreen = SovereignScreen.DINSTAR_ADMIN },
                    onSettings = { showSettings = true },
                    onContacts = { currentScreen = SovereignScreen.CONTACTS },
                    onDevices = { currentScreen = SovereignScreen.DEVICES },
                    onPrivacy = { currentScreen = SovereignScreen.PRIVACY },
                    onBackup = { currentScreen = SovereignScreen.BACKUP },
                    onCommunities = { currentScreen = SovereignScreen.COMMUNITIES },
                    onProfile = { currentScreen = SovereignScreen.PROFILE },
                    onEvents = { currentScreen = SovereignScreen.EVENTS },
                    onPolls = { currentScreen = SovereignScreen.POLLS },
                    onPstnConfig = { currentScreen = SovereignScreen.PSTN_CONFIG }
                )
            }
        }
    }
}

    if (showCreate) CreateSheet(
        publishing = feed.state == FeedState.Publishing,
        onDismiss = { showCreate = false },
        onPost = { text -> feed.create(text) { showCreate = false } },
        onPoll = { question, options, hours -> feed.createPoll(question, options, hours) { showCreate = false } },
        onStory = { showCreate = false; createStoryPicker.launch(arrayOf("image/*", "video/*")) },
        onLive = { showCreate = false; liveTitle = ""; liveIsPrivate = false; livePassword = ""; showLiveCreateDialog = true },
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
            // 🛡️ مؤشر حالة النظام (PSTN/GSM) بجانب الاسم لتعزيز الشعور بالسيادة والتحكم
            PstnStatusIndicator(modifier = Modifier.scale(0.85f))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(YounesEmerald, AqyalCyanGlow, AqyalGold))
                ),
                contentAlignment = Alignment.Center
            ) { Text(post.authorDisplayName.take(1).ifBlank { "ÙŠ" }, color = Color(0xFF03120E), fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(post.authorDisplayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${post.authorUsername} Â· ${post.authorRedId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, "Ø®ÙŠØ§Ø±Ø§Øª") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (post.authorRedId == currentRedId) {
                    DropdownMenuItem(text = { Text("ØªØ¹Ø¯ÙŠÙ„") }, onClick = { showMenu = false; onEdit(post, post.text) })
                    DropdownMenuItem(text = { Text("Ø­Ø°Ù") }, onClick = { showMenu = false; onDelete(post) })
                } else {
                    DropdownMenuItem(text = { Text("Ø¥Ø®ÙØ§Ø¡") }, onClick = { showMenu = false; onHide(post) })
                    DropdownMenuItem(text = { Text("ÙƒØªÙ… @${post.authorUsername}") }, onClick = { showMenu = false; onMute(post) })
                    DropdownMenuItem(text = { Text("Ø¥Ø¨Ù„Ø§Øº") }, onClick = { showMenu = false; onReport(post) })
                }
            }
            if (post.authorRedId != currentRedId) TextButton({ onFollow(post) }) { Text("Ø¥Ø¶Ø§ÙØ© ØµØ¯ÙŠÙ‚") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip({}, { Text(if (post.visibility == "LOCAL_YEMEN") "Ù†Ø¨Ø¶ Ù…Ø­Ù„ÙŠ" else "Ø¹Ø§Ù…") }, enabled = false, leadingIcon = { Icon(Icons.Default.Public, null, Modifier.size(15.dp)) })
            AssistChip({}, { Text(if (post.poll != null) "Ø§Ø³ØªØ·Ù„Ø§Ø¹" else if (post.parentId != null) "Ø±Ø¯" else "Ù…Ù†Ø´ÙˆØ±") }, enabled = false)
            if (post.kind != "POST") AssistChip({}, { Text(post.kind) }, enabled = false)
        }
        Text(post.text, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
        if (post.hashtags.isNotEmpty() || post.mentions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                post.hashtags.forEach { tag -> Text(tag, color = AqyalCyanGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                post.mentions.forEach { m -> Text(m, color = YounesEmerald, fontSize = 13.sp) }
            }
        }
        post.linkCard?.let { card ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(card.title ?: card.url, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(card.description ?: "", color = Color.Gray, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        if (post.editedAt != null) Text("ØªÙ… Ø§Ù„ØªØ¹Ø¯ÙŠÙ„", color = Color.Gray, fontSize = 11.sp)
        post.quotePostId?.let { quotedId ->
            Card(colors = CardDefaults.cardColors(containerColor = AqyalSurfaceRaised.copy(alpha = .72f))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, null, tint = AqyalGold, modifier = Modifier.size(18.dp))
                    Text(" Ø§Ù‚ØªØ¨Ø§Ø³ ÙŠÙˆÙ†Ø³ Â· ${quotedId.take(8)}", color = AqyalGold, fontSize = 12.sp)
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
                            Text("${option.votes} ØµÙˆØª", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text("Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„Ø£ØµÙˆØ§Øª: ${poll.options.sumOf { it.votes }}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PostAction(Icons.Default.FavoriteBorder, "${post.reactionCounts["LIKE"] ?: 0}", true) { onLike(post) }
            PostAction(Icons.AutoMirrored.Filled.Chat, post.replyCount.toString(), true, onThread)
            PostAction(Icons.Default.Repeat, "Ø§Ù‚ØªØ¨Ø§Ø³", true, onQuote)
            PostAction(Icons.Default.Share, "Ù…Ø´Ø§Ø±ÙƒØ©", true) {
                val shareText = buildString {
                    append(post.text)
                    if (post.hashtags.isNotEmpty()) append("\n").append(post.hashtags.joinToString(" "))
                    append("\n\nÙŠÙˆÙ†Ø³ Â· @").append(post.authorUsername)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "Ù…Ø´Ø§Ø±ÙƒØ© Ù…Ù†Ø´ÙˆØ± ÙŠÙˆÙ†Ø³")) }
            }
        }
    }
}

@Composable private fun PostAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) = TextButton(action, enabled = enabled) { Icon(icon, label, Modifier.size(18.dp)); Text(" $label", fontSize = 11.sp) }
@Composable private fun Avatar(text: String) = Box(Modifier.size(42.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) { Text(text, color = Color.Black, fontWeight = FontWeight.Black) }

@Composable private fun GroupAvatar(group: com.red.sovereign.groups.Group, groups: GroupViewModel) {
    LaunchedEffect(group.avatarUrl) { groups.loadAvatar(group) }
    val image = groups.avatars[group.id]
    if (image != null) Image(image, group.name, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Avatar(group.name.take(1))
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
    LaunchedEffect(directory.contacts.size) { directory.refreshPresence() }
    val tab = if (showGroups) 1 else 0
    var target by remember { mutableStateOf("") }
    // ÙØªØ­ Ù…Ø­Ø§Ø¯Ø«Ø© Ù…Ù† Ø¥Ø´Ø¹Ø§Ø± Ø±Ø³Ø§Ù„Ø©
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
    var reportDetails by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
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
    var groupMessageText by remember { mutableStateOf("") }
    var groupEditingMessageId by remember { mutableStateOf<String?>(null) }
    var groupDisappearingMs by remember { mutableStateOf<Long?>(null) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showGroupDisappearingDialog by remember { mutableStateOf(false) }
    var selectedGroupMember by remember { mutableStateOf<GroupMember?>(null) }
    var deleteGroupId by remember { mutableStateOf<String?>(null) }
    var memberRedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    val decrypted = remember { mutableStateListOf<DecryptedMessage>() }
    // ◀️ رجوع هرمي داخل المحادثات — يغلق الطبقات قبل الخروج من التطبيق
    BackHandler(enabled = target.isNotBlank() || groupConversationId != null || showDirectory || showMessageSearch || showMediaGallery || showGroupMediaGallery || selectedContact != null || showJoinGroup || manageGroupId != null || selectedChatMessage != null || showDisappearingDialog || showGroupDisappearingDialog || showGroupAttachmentSheet || showEmoji || showStickers || showGroupEmoji || showGroupStickers || showGroupPollDialog) {
        when {
            selectedChatMessage != null -> selectedChatMessage = null
            selectedContact != null -> selectedContact = null
            showDirectory -> showDirectory = false
            showMessageSearch -> showMessageSearch = false
            showMediaGallery -> showMediaGallery = false
            showGroupMediaGallery -> showGroupMediaGallery = false
            showJoinGroup -> showJoinGroup = false
            manageGroupId != null -> manageGroupId = null
            showDisappearingDialog -> showDisappearingDialog = false
            showGroupDisappearingDialog -> showGroupDisappearingDialog = false
            showGroupAttachmentSheet -> showGroupAttachmentSheet = false
            showGroupEmoji || showGroupStickers -> { showGroupEmoji = false; showGroupStickers = false }
            showEmoji || showStickers -> { showEmoji = false; showStickers = false }
            groupConversationId != null -> groupConversationId = null
            target.isNotBlank() -> target = ""
            showGroupPollDialog -> showGroupPollDialog = false
        }
    }
    val context = LocalContext.current
    val pinApi = remember { PinsApi(com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(context))) }
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
    val repository = remember { com.red.sovereign.core.database.LocalRepository(context) }
    val localMessages = remember { com.red.sovereign.core.MessageStore(context) }
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
    // إعادة بناء أصوات الاستطلاع من السجل المحلي عند فتح المجموعة
    androidx.compose.runtime.LaunchedEffect(groupConversationId, decrypted.size) {
        if (groupConversationId != null) {
            decrypted.filter { it.type == "RICH_TEXT" && it.conversationId == groupConversationId }.forEach { item ->
                RichMessage.decode(item.plaintext)?.let { rich ->
                    if (rich.action == "POLL_VOTE" && rich.pollVoteOf != null) {
                        PollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                    }
                }
            }
        }
    }
    val conversations by repository.getActiveConversations().collectAsState(initial = emptyList())
    // 📥 استعادة عدادات غير المقروء المحفوظة (تنجو من إعادة التشغيل) — ما لم تكن المحادثة مفتوحة حالياً
    androidx.compose.runtime.LaunchedEffect(conversations.size, target, groupConversationId) {
        val openConv = groupConversationId ?: target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) }
        val groupIds = groups.groups.map(com.red.sovereign.groups.Group::id).toSet()
        conversations.forEach { conv ->
            if (conv.id != openConv && conv.unreadCount > 0) {
                if (conv.id in groupIds) groupUnread[conv.id] = conv.unreadCount
                else chatUnread[conv.id] = conv.unreadCount
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(conversations.size, target) {
        if (target.isBlank()) {
            chatDrafts.clear()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                conversations.forEach { conv -> repository.getDraft(conv.id)?.let { if (it.text.isNotBlank()) chatDrafts[conv.id] = it.text } }
            }
        }
    }
    // ØªÙØ§Ø¹Ù„Ø§Øª Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ: messageId -> Ù‚Ø§Ø¦Ù…Ø© Ø§Ù„ØªÙØ§Ø¹Ù„Ø§Øª (Ù„Ù„Ø¹Ø±Ø¶ Ø§Ù„Ø³Ø±ÙŠØ¹ ØªØ­Øª ÙƒÙ„ Ø±Ø³Ø§Ù„Ø©)
    val reactionsByMessage = remember { androidx.compose.runtime.mutableStateMapOf<String, List<MessageReactionEntity>>() }

    val typingUsers = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.core.TypingEventBus.events.collect { event ->
            if (SettingsRuntime.current.typingIndicators && event.userId != account.redId) {
                // Ø§Ù„Ù…ÙØªØ§Ø­ = Ù…Ø¹Ø±Ù Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø© (Ø®Ø§ØµØ© Ø£Ùˆ Ø¬Ù…Ø§Ø¹ÙŠØ©) â€” ÙŠØ¯Ø¹Ù… Ù…Ø¤Ø´Ø± Ø§Ù„ÙƒØªØ§Ø¨Ø© Ø§Ù„Ø¬Ù…Ø§Ø¹ÙŠ
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
    // ØªØ­Ø¯ÙŠØ« ÙÙˆØ±ÙŠ Ù„Ø¹Ø±Ø¶ Ø§Ù„ØªÙØ§Ø¹Ù„Ø§Øª Ø¹Ù†Ø¯ ÙˆØ±ÙˆØ¯ Ø­Ø¯Ø« E2EE (Ø¥Ø¶Ø§ÙØ©/Ø¥Ø²Ø§Ù„Ø©)
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
    // Ø£ØµÙˆØ§Øª Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹ E2EE: ØªÙØ³Ø¬ÙŽÙ‘Ù„ Ù…Ù† Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„ØºÙ†ÙŠØ© Ø§Ù„ÙˆØ§Ø±Ø¯Ø© (POLL_VOTE)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        DecryptedMessageBus.messages.collect { item ->
            if (item.type == "RICH_TEXT") {
                RichMessage.decode(item.plaintext)?.let { rich ->
                    if (rich.action == "POLL_VOTE" && rich.pollVoteOf != null) {
                        PollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                    }
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(typingUsers) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = System.currentTimeMillis()
            typingUsers.entries.removeAll { it.value < now }
        }
    }

    androidx.compose.runtime.LaunchedEffect(messageText) {
        if (target.matches(RED_ID_PATTERN) && SettingsRuntime.current.typingIndicators) {
            val typingConversation = conversationId(account.redId, target)
            val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, typingConversation)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, messageText.isNotEmpty())
            }
            context.startService(intent)
            if (messageText.isNotEmpty()) {
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

    // ðŸ“ Ù…Ø¤Ø´Ø± Ø§Ù„ÙƒØªØ§Ø¨Ø© Ø§Ù„Ø¬Ù…Ø§Ø¹ÙŠ â€” ÙŠÙØ±Ø³Ù„ Ø¨Ù…Ø¹Ø±Ù Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© ÙƒÙ€ target/conversation (Ø§Ù„Ø®Ø§Ø¯Ù… ÙŠØ¨Ø«Ù‡ Ù„Ù„Ø£Ø¹Ø¶Ø§Ø¡)
    androidx.compose.runtime.LaunchedEffect(groupMessageText, groupConversationId) {
        val groupId = groupConversationId ?: return@LaunchedEffect
        if (SettingsRuntime.current.typingIndicators) {
            val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, groupId)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, groupId)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, groupMessageText.isNotEmpty())
            }
            context.startService(intent)
            if (groupMessageText.isNotEmpty()) {
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

    val draftScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(target, groupConversationId) {
        onDispose {
            if (messageText.isNotBlank()) {
                val draftConvId = groupConversationId ?: target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) }
                if (draftConvId != null) {
                    draftScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.saveDraft(draftConvId, messageText)
                    }
                }
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
    var exportingMessageId by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && exportingMessageId != null) { attachments.exportTo(exportingMessageId!!, uri); exportingMessageId = null }
    }
    // ðŸ“Ž Ù…Ø±ÙÙ‚Ø§Øª Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© â€” ØªÙØ±Ø³Ù„ Ø¹Ø¨Ø± Ù…Ø³Ø§Ø± ØªØ´ÙÙŠØ± Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© (Sender Keys)
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
    var showAttachmentSheet by remember { mutableStateOf(false) }
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
        // Ø±ÙØ¶ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ø§ ÙŠÙ…Ù†Ø¹ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© â€” Ù†Ø¨Ø¯Ø£Ù‡Ø§ ØµÙˆØªÙŠØ© Ù…Ø¹ Ø¥Ø¹Ù„Ø§Ù… (Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… ÙŠÙØ¹Ù‘Ù„ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ø§Ø­Ù‚Ø§Ù‹ Ù…Ù† Ø´Ø§Ø±Ø© Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„Ù…Ø­Ø§ÙˆÙ„Ø©).
        if (audioGranted && cleanTarget.isNotBlank()) {
            val startVideo = pendingCallVideo && cameraGranted
            YounesCallService.start(context, cleanTarget, startVideo)
            if (pendingCallVideo && !cameraGranted) {
                android.widget.Toast.makeText(context, "Ù„Ù… ÙŠÙÙ…Ù†Ø­ Ø¥Ø°Ù† Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ â€” Ø¨Ø¯Ø£Øª Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© ØµÙˆØªÙŠØ©. ÙŠÙ…ÙƒÙ†Ùƒ ØªÙØ¹ÙŠÙ„ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù…Ù† Ø´Ø§Ø´Ø© Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    var pendingGroupVideo by remember { mutableStateOf(false) }
    // ðŸ“ž ÙˆØ§ØªØ³Ø§Ø¨: Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª ØªÙ…Ù„Ùƒ ÙÙ‚Ø· Ù…ÙƒØ§Ù„Ù…Ø§Øª ØªØ±Ù† Ø§Ù„Ø¬Ù…ÙŠØ¹ (Ø­ØªÙ‰ 32). Ø§Ù„Ù…Ø³Ø§Ø­Ø§Øª/Ø§Ù„Ù…Ø¤ØªÙ…Ø±Ø§Øª Ù…ÙŠØ²Ø§Øª Ù…Ø³ØªÙ‚Ù„Ø© Ø®Ø§Ø±Ø¬ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª.
    val groupCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingGroupVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        // Ø±ÙØ¶ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ø§ ÙŠÙ…Ù†Ø¹ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ø¬Ù…Ø§Ø¹ÙŠØ© â€” ØªØ¨Ø¯Ø£ ØµÙˆØªÙŠØ© Ù…Ø¹ Ø¥Ø¹Ù„Ø§Ù… (ÙˆØ§ØªØ³Ø§Ø¨: ÙŠÙ…ÙƒÙ† ØªØ´ØºÙŠÙ„ Ø§Ù„ÙÙŠØ¯ÙŠÙˆ Ù„Ø§Ø­Ù‚Ø§Ù‹)
        val effectiveVideo = pendingGroupVideo && cameraGranted
        if (audioGranted && group != null) {
            // ÙˆØ§ØªØ³Ø§Ø¨: Ø­ØªÙ‰ 32 Ù…Ø´Ø§Ø±ÙƒØ§Ù‹ (2024) Ù„ÙƒÙ„ Ù…Ù† Ø§Ù„ØµÙˆØª ÙˆØ§Ù„ÙÙŠØ¯ÙŠÙˆØ› SFU ÙŠÙˆØ³Ø¹ Ø§Ù„Ø³Ù‚ÙØŒ Mesh ÙŠØªØ±Ø§Ø¬Ø¹ Ù„Ù€ 8
            val inviteeMembers = group.members.filter { it.redId != account.redId }
            if (inviteeMembers.isEmpty()) {
                android.widget.Toast.makeText(context, "Ù„Ø§ ÙŠÙˆØ¬Ø¯ Ø£Ø¹Ø¶Ø§Ø¡ Ø¢Ø®Ø±ÙˆÙ† Ù„Ù„Ø§ØªØµØ§Ù„ Ø¨Ù‡Ù… ÙÙŠ Ù‡Ø°Ù‡ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            if (inviteeMembers.size > 32) {
                android.widget.Toast.makeText(context, "Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© ØªØ¯Ø¹Ù… Ø­ØªÙ‰ 32 Ù…Ø´Ø§Ø±ÙƒØ§Ù‹ (Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ø¨Ù‡Ø§ ${group.members.size}). Ø³ÙŠØªÙ… Ø§Ù„Ø§ØªØµØ§Ù„ Ø¨Ø£ÙˆÙ„ 32.", android.widget.Toast.LENGTH_LONG).show()
            }
            val inviteIds = inviteeMembers.take(32).map { it.redId }
            val inviteNames = inviteeMembers.take(32).map { it.username ?: it.redId.take(8) }
            if (pendingGroupVideo && !cameraGranted) {
                android.widget.Toast.makeText(context, "Ù„Ù… ÙŠÙÙ…Ù†Ø­ Ø¥Ø°Ù† Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ â€” Ø³ØªØ¨Ø¯Ø£ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© ØµÙˆØªÙŠØ©. ÙŠÙ…ÙƒÙ†Ùƒ ØªÙØ¹ÙŠÙ„ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ø§Ø­Ù‚Ø§Ù‹.", android.widget.Toast.LENGTH_LONG).show()
            }
            // ÙˆØ§ØªØ³Ø§Ø¨: ÙƒÙ„ Ù…ÙƒØ§Ù„Ù…Ø© Ù…Ø¬Ù…ÙˆØ¹Ø© ØªØ±Ù† Ø¬Ù…ÙŠØ¹ Ø§Ù„Ø£Ø¹Ø¶Ø§Ø¡ Ù…Ø¨Ø§Ø´Ø±Ø© Ø¹Ø¨Ø± GroupCallService (Mesh/SFU)
            com.red.sovereign.calls.GroupCallService.startGroupCall(
                context = context,
                myUserId = account.redId,
                inviteeIds = inviteIds,
                inviteeNames = inviteNames,
                isVideo = effectiveVideo,
                hostName = account.username,
                groupId = group.id
            )

            // Ø±Ø³Ø§Ù„Ø© Ù†Ø¸Ø§Ù… ÙÙŠ Ø¯Ø±Ø¯Ø´Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© â€” Ù…Ø«Ù„ ÙˆØ§ØªØ³Ø§Ø¨: "Ø¨Ø¯Ø£Øª Ù…ÙƒØ§Ù„Ù…Ø© ØµÙˆØªÙŠØ© Ø¬Ù…Ø§Ø¹ÙŠØ© â€” Ø§Ù†Ù‚Ø± Ù„Ù„Ø§Ù†Ø¶Ù…Ø§Ù…"
            val title = if (effectiveVideo) "Ù…ÙƒØ§Ù„Ù…Ø© ÙÙŠØ¯ÙŠÙˆ Ø¬Ù…Ø§Ø¹ÙŠØ© ðŸ“¹" else "Ù…ÙƒØ§Ù„Ù…Ø© ØµÙˆØªÙŠØ© Ø¬Ù…Ø§Ø¹ÙŠØ© ðŸ“ž"
            val rich = com.red.sovereign.core.RichMessage(
                action = "CALL_STARTED",
                text = "Ø¨Ø¯Ø£ $title. ØªØ±Ù† Ø¬Ù…ÙŠØ¹ Ø§Ù„Ø£Ø¹Ø¶Ø§Ø¡ â€” ÙŠÙ…ÙƒÙ† Ø§Ù„Ø§Ù†Ø¶Ù…Ø§Ù… Ø­ØªÙ‰ Ø¨Ø¹Ø¯ Ø¨Ø¯Ø¡ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©."
            )
            com.red.sovereign.core.RedConnectionService.sendGroupRichText(context, group, rich)
        }
    }
    LaunchedEffect(Unit) { DecryptedMessageBus.messages.collect { item ->
        decrypted.add(item)
        if (item.type == "RICH_TEXT") {
            RichMessage.decode(item.plaintext)?.let { rich ->
                // ðŸ” Ø¹Ù„Ø§Ù…Ø© âœï¸ Ù„Ù„Ù…Ø¹Ø¯ÙŽÙ‘Ù„: ÙÙ‚Ø· Ø¥Ù† ÙƒØ§Ù† Ù…ÙØ±Ø³Ù„ Ø§Ù„ØªØ¹Ø¯ÙŠÙ„ Ù‡Ùˆ Ù…Ø§Ù„Ùƒ Ø§Ù„Ø±Ø³Ø§Ù„Ø©
                if (rich.action == "EDIT" && rich.editOf != null) {
                    if (decrypted.any { it.id == rich.editOf && it.senderRedId == item.senderRedId }) editedMessageIds[rich.editOf!!] = true
                }
            }
        }
        // ØªØªØ¨Ø¹ ØºÙŠØ± Ø§Ù„Ù…Ù‚Ø±ÙˆØ¡ Ù„Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„ÙˆØ§Ø±Ø¯Ø© (Ù…Ø§ Ù„Ù… ØªÙƒÙ† Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø©/Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ù…ÙØªÙˆØ­Ø© Ø­Ø§Ù„ÙŠØ§Ù‹)
        if (!item.outgoing) {
            if (item.conversationId.length > 32) {
                if (item.conversationId != groupConversationId) {
                    groupUnread[item.conversationId] = (groupUnread[item.conversationId] ?: 0) + 1
                } else {
                    // Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ù…ÙØªÙˆØ­Ø©: ØªØµÙÙŠØ± Ø§Ù„Ø¹Ø¯Ø§Ø¯ Ø§Ù„Ù…Ø­ÙÙˆØ¸ ÙƒÙŠ Ù„Ø§ ÙŠØªØ±Ø§ÙƒÙ… Ø¹Ù†Ø¯ Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ÙØªØ­
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
        if (!item.outgoing && SettingsRuntime.current.readReceipts) RedConnectionService.markRead(context, item.id, item.sequence)
    } }
    // Ù…Ù„Ø§Ø­Ø¸Ø©: `val conversation` Ù…Ø¹Ø±Ù‘Ù ÙÙŠ Ø³Ø·Ø± Ø³Ø§Ø¨Ù‚ (ChatHubScreen scope) â€” Ù„Ø§ Ù†Ø¹ÙŠØ¯ Ø­Ø³Ø§Ø¨Ù‡ Ù‡Ù†Ø§
    androidx.compose.runtime.LaunchedEffect(target, groupConversationId) {
        val conversationToRestore = groupConversationId ?: target.takeIf(String::isNotBlank)?.let { conversationId(account.redId, it) }
        if (conversationToRestore != null) {
            repository.getDraft(conversationToRestore)?.let { messageText = it.text }
            repository.getLocalHistory(conversationToRestore).collect { entities ->
                entities.forEach { stored ->
                    if (decrypted.none { it.id == stored.id }) decrypted.add(DecryptedMessage(stored.id, stored.conversationId, stored.senderId, stored.encryptedPlaintext, stored.createdAt, 0, stored.messageType, stored.outgoing))
                }
            }
        }
    }
    // ØªØ­Ù…ÙŠÙ„ ØªÙØ§Ø¹Ù„Ø§Øª Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø© Ø§Ù„Ù…ÙØªÙˆØ­Ø© Ù…Ù† Ø§Ù„ØªØ®Ø²ÙŠÙ† Ø§Ù„Ù…Ø­Ù„ÙŠ (Ù…Ø´ÙÙ‘Ø±)
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
                            Text(" Ø·Ù„Ø¨Ø§Øª Ø§Ù„ØµØ¯Ø§Ù‚Ø© Ø§Ù„ÙˆØ§Ø±Ø¯Ø©", color = AqyalGold, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${directory.requests.size}", color = Color.White, modifier = Modifier.background(AqyalGold, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                        directory.requests.forEach { request ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(request.requester.displayName.take(1))
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(request.requester.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("@${request.requester.username} â€¢ ${request.requester.redId.take(12)}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton({ directory.resolve(request, false) }, Modifier.height(38.dp)) { Text("Ø±ÙØ¶", color = Color.Gray) }
                                    Button({ directory.resolve(request, true) }, Modifier.height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) { Text("Ù‚Ø¨ÙˆÙ„", color = Color(0xFF002118)) }
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
                        Text("Ø§Ù„Ø£ØµØ¯Ù‚Ø§Ø¡", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text("${directory.contacts.size}", color = Color.White, fontSize = 12.sp, modifier = Modifier.background(AqyalCyanGlow, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                        Spacer(Modifier.weight(1f))
                        TextButton({ showDirectory = true }) { Text("Ø¥Ø¶Ø§ÙØ© +", color = AqyalGold, fontSize = 12.sp) }
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
                                        Avatar(person.displayName.take(1))
                                        if (online) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF00C98C)).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(person.displayName, maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, overflow = TextOverflow.Ellipsis)
                                    Text(if (online) "Ù…ØªØµÙ„" else "@${person.username}", color = if (online) YounesEmerald else AqyalCyanGlow, maxLines = 1, fontSize = 10.sp)
                                    IconButton({ selectedContact = person }, Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, "Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„ØµØ¯ÙŠÙ‚", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth().clickable { showDirectory = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = YounesEmerald)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Ø¨Ø¯Ø¡ Ù…Ø­Ø§Ø¯Ø«Ø© Ø®Ø§ØµØ©", fontWeight = FontWeight.SemiBold); Text("Ø§Ø¨Ø­Ø« Ø¨Ø§Ù„Ø§Ø³Ù… Ø§Ù„Ø¯Ù‚ÙŠÙ‚ Ø£Ùˆ Ù…Ø¹Ø±Ù‘Ù ÙŠÙˆÙ†Ø³", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                val activePerson = directory.contacts.find { it.redId == target }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ target = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Ø§Ù„Ø¹ÙˆØ¯Ø© Ù„Ù‚Ø§Ø¦Ù…Ø© Ø§Ù„Ø¯Ø±Ø¯Ø´Ø§Øª") }
                    Avatar((activePerson?.displayName ?: target).take(1))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(activePerson?.displayName ?: target, fontWeight = FontWeight.SemiBold)
                        Text(activePerson?.let { val ls = directory.lastSeenLabel(it.redId); ls ?: "@${it.username} Â· ${it.redId}" } ?: target, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            val conversationMessages = resolveRichMessages(decrypted.filter { it.conversationId == conversation })
            androidx.compose.runtime.LaunchedEffect(conversationMessages.size, target) {
                if (conversationMessages.isNotEmpty()) messagesListState.animateScrollToItem(conversationMessages.lastIndex)
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
                                placeholder = { Text("Ø¨Ø­Ø« ÙÙŠ Ø§Ù„Ø¯Ø±Ø¯Ø´Ø§Øªâ€¦") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, shape = RoundedCornerShape(14.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !chatUnreadFilter, onClick = { chatUnreadFilter = false }, label = { Text("Ø§Ù„ÙƒÙ„") })
                                FilterChip(selected = chatUnreadFilter, onClick = { chatUnreadFilter = true }, label = { Text("ØºÙŠØ± Ø§Ù„Ù…Ù‚Ø±ÙˆØ¡") })
                            }
                        }
                    }
                    if (filteredConvos.isEmpty() && allConvos.isNotEmpty()) item {
                        Text("Ù„Ø§ ØªÙˆØ¬Ø¯ Ù…Ø­Ø§Ø¯Ø«Ø§Øª Ù…Ø·Ø§Ø¨Ù‚Ø©", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    }
                    items(filteredConvos, key = { it.id }) { conv ->
                        val unread = chatUnread[conv.id] ?: 0
                        Card(Modifier.fillMaxWidth().clickable { chatUnread.remove(conv.id); target = conv.peerId; scope.launch { repository.clearUnread(conv.id) } }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            val contact = directory.contacts.firstOrNull { it.redId == conv.peerId }
                            val displayName = contact?.displayName ?: conv.peerId
                            val isOnline = directory.isOnline(conv.peerId)
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Avatar(displayName.take(1))
                                    if (isOnline) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF00C98C)).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                    }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (conv.pinned) { Spacer(Modifier.width(3.dp)); Icon(androidx.compose.material.icons.Icons.Default.Star, "Ù…Ø«Ø¨Øª", tint = Color(0xFFF5C842), modifier = Modifier.size(14.dp)) }
                                        if (conv.mutedUntil > System.currentTimeMillis()) { Spacer(Modifier.width(3.dp)); Icon(androidx.compose.material.icons.Icons.Default.NotificationsOff, "Ù…ÙƒØªÙˆÙ…", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                                    }
                                    val draft = chatDrafts[conv.id]
                                    Text(
                                        if (draft != null) "Ù…Ø³ÙˆØ¯Ø©: $draft" else (conv.lastMessageText ?: "Ù„Ø§ ØªÙˆØ¬Ø¯ Ø±Ø³Ø§Ø¦Ù„"),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (draft != null) AqyalGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (conv.lastMessageTimestamp > 0) Text(relativeTime(conv.lastMessageTimestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (unread > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Surface(shape = RoundedCornerShape(10.dp), color = YounesEmerald) { Text(" $unread ", fontSize = 11.sp, color = Color(0xFF002118), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (target.isNotBlank() && conversationMessages.isEmpty()) item {
                    SovereignEmptyConversationState()
                }
                itemsIndexed(conversationMessages, key = { _, it -> it.id }) { index, item ->
                    // ÙØ§ØµÙ„ ØªØ§Ø±ÙŠØ® Ø¨ÙŠÙ† Ø§Ù„Ø£ÙŠØ§Ù… (Ù…Ø«Ù„ ÙˆØ§ØªØ³Ø§Ø¨)
                    val showDate = index == 0 || !isSameDay(conversationMessages[index - 1].timestamp, item.timestamp)

                    Column(Modifier.fillMaxWidth()) {
                        if (showDate) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                                    Text(dateLabel(item.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }


                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.outgoing) Arrangement.End else Arrangement.Start) {
                            if (item.type == "RICH_TEXT" && RichMessage.decode(item.plaintext)?.action == "MESSAGE") {
                                // âœ¨ Ø§Ø³ØªØ®Ø¯Ø§Ù… ÙÙ‚Ø§Ø¹Ø§Øª Ø§Ù„Ø¯Ø±Ø¯Ø´Ø© Ø§Ù„ÙØ§Ø®Ø±Ø© Ù„Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„Ù†ØµÙŠØ©
                                val payload = RichMessage.decode(item.plaintext)
                                LuxuryChatBubble(
                                    message = payload?.text ?: "",
                                    isMe = item.outgoing,
                                    time = formatClockTime(item.timestamp),
                                    status = item.status,
                                    onLongClick = { selectedChatMessage = item }
                                )
                            } else {
                                // Ø§Ù„Ø­ÙØ§Ø¸ Ø¹Ù„Ù‰ Ø§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„Ù‚Ø¯ÙŠÙ… Ù„Ù„ÙˆØ³Ø§Ø¦Ø· ÙˆØ§Ù„Ù…Ø±ÙÙ‚Ø§Øª ÙˆØ§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹Ø§Øª Ø­Ø§Ù„ÙŠØ§Ù‹
                                Card(
                                    Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = { selectedChatMessage = item }),
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
                                            "RICH_TEXT" -> RichTextMessage(item, conversationMessages)
                                            else -> Text(item.plaintext.toString(Charsets.UTF_8), color = if (item.outgoing) Color(0xFF001B14) else Color.White, fontSize = 16.sp)
                                        }
                                        // ØªÙØ§Ø¹Ù„Ø§Øª Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ ØªØ­Øª Ø§Ù„Ø±Ø³Ø§Ù„Ø© (E2EE)
                                        MessageReactions(
                                            reactions = reactionsByMessage[item.id].orEmpty(),
                                            currentRedId = account.redId,
                                            onToggle = { emoji ->
                                                val mine = reactionsByMessage[item.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                                if (mine) RedConnectionService.removeReaction(context, target, conversation, item.id)
                                                else RedConnectionService.sendReaction(context, target, conversation, item.id, emoji)
                                                // ØªØ­Ø¯ÙŠØ« Ù…Ø­Ù„ÙŠ ÙÙˆØ±ÙŠ Ù„Ø§Ø³ØªØ¬Ø§Ø¨Ø© Ø§Ù„ÙˆØ§Ø¬Ù‡Ø© Ù‚Ø¨Ù„ ÙˆØµÙˆÙ„ Ø§Ù„Ø­Ø¯Ø« Ø¹Ø¨Ø± Ø§Ù„Ù€ bus
                                                val current = reactionsByMessage[item.id].orEmpty()
                                                val withoutMine = current.filterNot { it.senderId == account.redId }
                                                reactionsByMessage[item.id] = if (mine) withoutMine else withoutMine + com.red.sovereign.core.database.MessageReactionEntity(item.id, conversation, account.redId, emoji, System.currentTimeMillis())
                                            }
                                        )
                                        // ðŸ• Ø§Ù„ÙˆÙ‚Øª + Ø¹Ù„Ø§Ù…Ø§Øª Ø§Ù„Ù‚Ø±Ø§Ø¡Ø© Ø¯Ø§Ø®Ù„ Ø§Ù„ÙÙ‚Ø§Ø¹Ø© (Ù†Ù…Ø· ÙˆØ§ØªØ³Ø§Ø¨)
                                        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(formatClockTime(item.timestamp), fontSize = 10.sp, color = if (item.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (editedMessageIds.containsKey(item.id)) Text("âœï¸", fontSize = 10.sp)
                                            if (item.outgoing) {
                                                val ticks = when (item.status) {
                                                    "READ" -> "âœ“âœ“"
                                                    "DELIVERED" -> "âœ“âœ“"
                                                    else -> "âœ“"
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
                if (showEmoji) EmojiPicker(onEmoji = { messageText += it })
                if (showStickers && target.matches(RED_ID_PATTERN)) {
                    val stickerTokens = remember { com.red.sovereign.auth.TokenStore(context) }
                    com.red.sovereign.media.StickerPicker(
                        tokens = stickerTokens,
                        onPickSticker = { sticker ->
                            scope.launch {
                                val mediaApi = com.red.sovereign.media.MediaApi(context, com.red.sovereign.auth.AuthorizedApiClient(stickerTokens))
                                mediaApi.grant(sticker.mediaKey, target)
                                val payload = kotlinx.serialization.json.Json.encodeToString(
                                    com.red.sovereign.media.StickerMessagePayload.serializer(),
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "ðŸŽ¨", sticker.name)
                                )
                                com.red.sovereign.core.RedConnectionService.sendPayload(context, target, conversation, "STICKER", payload.toByteArray(Charsets.UTF_8))
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
                                        Text(" â€¢ ${person.displayName}", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                val hashtagQuery = HASHTAG_AUTOCOMPLETE.find(messageText)?.groupValues?.get(1)
                if (hashtagQuery != null) {
                    val popular = listOf("Ù…Ù‡Ù…", "ÙŠÙ…Ù†", "ØªÙ‚Ù†ÙŠØ©", "Ø¹Ø§Ù…", "Ø®Ø§Øµ").filter { it.contains(hashtagQuery, ignoreCase = true) }.take(3)
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

                // ðŸ’¬ Ø´Ø±ÙŠØ· Ø§Ù„Ø¥Ø¯Ø®Ø§Ù„ Ø§Ù„Ø¹ØµØ±ÙŠ Ø§Ù„Ø°ÙƒÙŠ
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
                        RedConnectionService.sendRichText(context, target, conversation, rich)
                        if (editingMessageId != null) editedMessageIds[editingMessageId!!] = true
                        messageText = ""; showEmoji = false; replyToMessage = null; editingMessageId = null
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
        } else Column(Modifier.fillMaxSize().padding(14.dp)) {
            val openGroup = groups.groups.firstOrNull { it.id == groupConversationId }
            if (openGroup == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onCreateGroup, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" Ø¥Ù†Ø´Ø§Ø¡") }
                    OutlinedButton({ showJoinGroup = true }, Modifier.weight(1f)) { Text("Ø§Ù†Ø¶Ù…Ø§Ù… Ø¨Ø¯Ø¹ÙˆØ©") }
                }
                when {
                    groups.state == GroupState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
                    groups.state is GroupState.Error -> (groups.state as? GroupState.Error)?.let { EmptyState(Icons.Default.Groups, "ØªØ¹Ø°Ø± ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª", it.message) }
                    groups.groups.isEmpty() -> EmptyState(Icons.Default.Groups, "Ù„Ø§ ØªÙˆØ¬Ø¯ Ù…Ø¬Ù…ÙˆØ¹Ø§Øª", "Ø£Ù†Ø´Ø¦ Ù…Ø¬Ù…ÙˆØ¹Ø© Ù…Ø­Ù„ÙŠØ© Ø¨Ø£Ø¯ÙˆØ§Ø± Ù…Ø§Ù„Ùƒ ÙˆÙ…Ø³Ø¤ÙˆÙ„ ÙˆØ¹Ø¶Ùˆ.")
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
                                                text = relativeTime(lastGroupMsg?.timestamp ?: groupConvRow?.lastMessageTimestamp ?: 0L),
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
                                                (if (msg.outgoing) "Ø£Ù†Øª: " else "@" + msg.senderRedId.take(8) + ": ") + t
                                            } ?: groupConvRow?.lastMessageText ?: group.description.orEmpty().ifBlank { "Ù…Ø¬Ù…ÙˆØ¹Ø© Ù…Ø´ÙØ±Ø© Ø¨Ù€ Sender Keys" },
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
                                                    text = "${group.members.size} Ø¹Ø¶Ùˆ",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton({ onManageGroup(group.id) }, modifier = Modifier.padding(start = 4.dp)) {
                                    Icon(Icons.Default.MoreVert, "Ø¥Ø¯Ø§Ø±Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // ÙˆØ§ØªØ³Ø§Ø¨: ØªØ±ÙˆÙŠØ³Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© â€” [Ø±Ø¬ÙˆØ¹] [Ø£ÙØ§ØªØ§Ø±+Ø§Ø³Ù…+Ø¹Ø¯Ø¯ Ø§Ù„Ø£Ø¹Ø¶Ø§Ø¡] [ðŸ“ž ØµÙˆØª] [ðŸŽ¥ ÙÙŠØ¯ÙŠÙˆ] [â‹®]
                // Ø§Ù„Ù…Ø¤ØªÙ…Ø±Ø§Øª/Ø§Ù„Ù…Ø³Ø§Ø­Ø§Øª Ø®Ø§Ø±Ø¬ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª ØªÙ…Ø§Ù…Ø§Ù‹ (Ù…ÙŠØ²Ø§Øª Ù…Ø³ØªÙ‚Ù„Ø©)
                val waGroupCall = GroupCallRuntime.state
                val waIsActiveForThisGroup = when (waGroupCall) {
                    is GroupCallUiState.Ringing -> waGroupCall.members.isNotEmpty() && GroupCallRuntime.activeGroupId == openGroup.id
                    is GroupCallUiState.Active -> GroupCallRuntime.activeGroupId == openGroup.id
                    is GroupCallUiState.IncomingGroup -> GroupCallRuntime.activeGroupId == openGroup.id || waGroupCall.groupCallId.contains(openGroup.id.take(8))
                    else -> false
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ groupConversationId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Ø§Ù„Ø¹ÙˆØ¯Ø© Ù„Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª") }
                        GroupAvatar(openGroup, groups)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(openGroup.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val statusText = when {
                                waGroupCall is GroupCallUiState.Active && GroupCallRuntime.activeGroupId == openGroup.id -> if (waGroupCall.isVideo) "Ù…ÙƒØ§Ù„Ù…Ø© ÙÙŠØ¯ÙŠÙˆ Ø¬Ù…Ø§Ø¹ÙŠØ© Ø¬Ø§Ø±ÙŠØ©" else "Ù…ÙƒØ§Ù„Ù…Ø© ØµÙˆØªÙŠØ© Ø¬Ù…Ø§Ø¹ÙŠØ© Ø¬Ø§Ø±ÙŠØ©"
                                waGroupCall is GroupCallUiState.Ringing && GroupCallRuntime.activeGroupId == openGroup.id -> "ØªØ±Ù† Ø§Ù„Ø£Ø¹Ø¶Ø§Ø¡..."
                                waGroupCall is GroupCallUiState.IncomingGroup -> "Ù…ÙƒØ§Ù„Ù…Ø© Ø¬Ù…Ø§Ø¹ÙŠØ© ÙˆØ§Ø±Ø¯Ø©"
                                else -> "${openGroup.members.size} Ø£Ø¹Ø¶Ø§Ø¡ Â· Ù…Ø´ÙÙ‘Ø±Ø© E2EE"
                            }
                            Text(
                                statusText,
                                color = if (waIsActiveForThisGroup) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // ðŸ“ž ÙˆØ§ØªØ³Ø§Ø¨ Ø§Ù„Ù†Ù‚ÙŠ: ÙÙŠØ¯ÙŠÙˆ + ØµÙˆØª ÙÙ‚Ø· â€” Ù„Ø§ Ù…Ø³Ø§Ø­Ø§Øª ÙˆÙ„Ø§ Ù…Ø¤ØªÙ…Ø±Ø§Øª Ù‡Ù†Ø§
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
                // Ø´Ø±ÙŠØ· ÙˆØ§ØªØ³Ø§Ø¨ Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ø§Ù„Ø¬Ø§Ø±ÙŠØ© â€” Ø§Ù†Ø¶Ù…Ø§Ù…/Ø¹ÙˆØ¯Ø©
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
                                is GroupCallUiState.Ringing -> {} // Ø§Ù„Ù…Ø¶ÙŠÙ Ø¨Ø§Ù„ÙØ¹Ù„ ÙÙŠ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©
                                else -> {} // Active: Ø§Ù„Ø¹ÙˆØ¯Ø© Ø¹Ø¨Ø± Overlay
                            }
                        }
                    )
                }
                // ÙƒÙ„ Ø§Ù„Ø£Ù†ÙˆØ§Ø¹ (GROUP_MESSAGE/RICH_TEXT/IMAGE/VIDEO/AUDIO/VOICE/FILE/STICKER) â€”
                // ÙˆØ³Ø§Ø¦Ø· Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© ØªÙØ´ÙÙŽÙ‘Ø± Ø¨Ù€ Sender Keys ÙˆØªØµÙ„ Ø¨Ù†ÙˆØ¹Ù‡Ø§ Ø§Ù„Ø£ØµÙ„ÙŠ ÙˆÙ„Ø§ ÙŠØ¬ÙˆØ² Ø§Ø³ØªØ¨Ø¹Ø§Ø¯Ù‡Ø§.
                val groupMessages = resolveRichMessages(decrypted.filter { it.conversationId == openGroup.id })
                androidx.compose.runtime.LaunchedEffect(groupMessages.size, openGroup.id) {
                    if (groupMessages.isNotEmpty()) groupListState.animateScrollToItem(groupMessages.lastIndex)
                }
                LazyColumn(Modifier.weight(1f), state = groupListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listScope = this
                    if (groupPinnedMessages.isNotEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = 0.08f))) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                                        Text(" Ø±Ø³Ø§Ø¦Ù„ Ù…Ø«Ø¨ØªØ© (${groupPinnedMessages.size})", color = AqyalGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    groupPinnedMessages.values.forEach { pm ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(messageDisplayText(pm), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                            IconButton({ groupPinnedMessages.remove(pm.id) }) { Icon(Icons.Default.Close, "Ø¥Ù„ØºØ§Ø¡ ØªØ«Ø¨ÙŠØª", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (groupMessages.isEmpty()) item { Text("Ù…Ø­Ø§Ø¯Ø«Ø© Ø¬Ù…Ø§Ø¹ÙŠØ© Ù…Ø´ÙØ±Ø© Ø¨Ù€Sender Keys. ÙŠØªØºÙŠØ± Ø§Ù„Ù…ÙØªØ§Ø­ ØªÙ„Ù‚Ø§Ø¦ÙŠÙ‹Ø§ Ø¹Ù†Ø¯ ØªØºÙŠØ± Ø§Ù„Ø¹Ø¶ÙˆÙŠØ©.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                itemsIndexed(groupMessages, key = { _, it -> it.id }) { index, message ->
                    val showDate = index == 0 || !isSameDay(groupMessages[index - 1].timestamp, message.timestamp)

                    Column(Modifier.fillMaxWidth()) {
                        if (showDate) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                                    Text(dateLabel(message.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                            if (message.type == "RICH_TEXT" && RichMessage.decode(message.plaintext)?.action == "MESSAGE") {
                                val payload = RichMessage.decode(message.plaintext)
                                LuxuryChatBubble(
                                    message = payload?.text ?: "",
                                    isMe = message.outgoing,
                                    time = formatClockTime(message.timestamp),
                                    status = message.status,
                                    onLongClick = { selectedChatMessage = message }
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
                                        // Ø£Ù„ÙˆØ§Ù† Ù‡Ø§Ø¯Ø¦Ø© ÙˆÙ…ØªÙ†Ø§Ø³Ù‚Ø© Ù…Ø¹ Ø§Ù„Ù‡ÙˆÙŠØ© (Ù„Ø§ Ù…Ù‡Ø±Ø¬Ø§Ù† Ø£Ù„ÙˆØ§Ù†)
                                        val nameColors = listOf(
                                            Color(0xFF6FD8B0), Color(0xFF7FB5E0), Color(0xFFF0C674), Color(0xFFC9A7E8),
                                            Color(0xFF8FC7E8), Color(0xFFB5D8A0), Color(0xFFE0B8A0)
                                        )
                                        val colorIndex = kotlin.math.abs(message.senderRedId.hashCode()) % nameColors.size
                                        Text(message.senderRedId.take(12) + "...", color = nameColors[colorIndex], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                    when (message.type) {
                                        "RICH_TEXT" -> RichTextMessage(
                                            message, groupMessages,
                                            myRedId = account.redId,
                                            onPollVote = { pollId, optionIndex ->
                                                val vote = RichMessage(action = "POLL_VOTE", pollVoteOf = pollId, pollVoteOption = optionIndex)
                                                RedConnectionService.sendGroupRichText(context, openGroup, vote)
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
                                    // ØªÙØ§Ø¹Ù„Ø§Øª Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ ØªØ­Øª Ø±Ø³Ø§Ù„Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© (E2EE Ø¨Ù€ Sender Keys)
                                    MessageReactions(
                                        reactions = reactionsByMessage[message.id].orEmpty(),
                                        currentRedId = account.redId,
                                        onToggle = { emoji ->
                                            val mine = reactionsByMessage[message.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                            if (mine) RedConnectionService.removeGroupReaction(context, openGroup, message.id)
                                            else RedConnectionService.sendGroupReaction(context, openGroup, message.id, emoji)
                                            val current = reactionsByMessage[message.id].orEmpty()
                                            val withoutMine = current.filterNot { it.senderId == account.redId }
                                            reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + com.red.sovereign.core.database.MessageReactionEntity(message.id, openGroup.id, account.redId, emoji, System.currentTimeMillis())
                                        }
                                    )
                                    // ðŸ• Ø§Ù„ÙˆÙ‚Øª Ø¯Ø§Ø®Ù„ Ø§Ù„ÙÙ‚Ø§Ø¹Ø© (Ù†Ù…Ø· ÙˆØ§ØªØ³Ø§Ø¨)
                                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(formatClockTime(message.timestamp), fontSize = 10.sp, color = if (message.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (editedMessageIds.containsKey(message.id)) Text("âœï¸", fontSize = 10.sp)
                                        if (message.outgoing) {
                                            val ticks = when (message.status) {
                                                "READ" -> "âœ“âœ“"
                                                "DELIVERED" -> "âœ“âœ“"
                                                else -> "âœ“"
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
                // ðŸ“ Ù…Ø¤Ø´Ø± ÙƒØªØ§Ø¨Ø© Ø¬Ù…Ø§Ø¹ÙŠ (Ø§Ù„Ø®Ø§Ø¯Ù… ÙŠØ¨Ø«Ù‡ Ù„Ù„Ø£Ø¹Ø¶Ø§Ø¡ â€” Ù„Ø§ ÙŠØ¸Ù‡Ø± Ù„ÙƒØ§ØªØ¨ Ø§Ù„Ø±Ø³Ø§Ù„Ø© Ù†ÙØ³Ù‡)
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
                if (showGroupEmoji) EmojiPicker(onEmoji = { groupMessageText += it })
                if (showGroupStickers) {
                    com.red.sovereign.media.StickerPicker(
                        tokens = com.red.sovereign.auth.TokenStore(context),
                        onPickSticker = { sticker ->
                            scope.launch {
                                val mediaApi = com.red.sovereign.media.MediaApi(context, com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(context)))
                                mediaApi.grant(sticker.mediaKey, openGroup.id)
                                val payload = kotlinx.serialization.json.Json.encodeToString(
                                    com.red.sovereign.media.StickerMessagePayload.serializer(),
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "ðŸŽ¨", sticker.name)
                                )
                                RedConnectionService.sendGroupText(context, openGroup, payload)
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

                // ðŸ’¬ Ø´Ø±ÙŠØ· Ø§Ù„Ø¥Ø¯Ø®Ø§Ù„ Ø§Ù„Ø¹ØµØ±ÙŠ Ø§Ù„Ø°ÙƒÙŠ Ù„Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©
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
                        RedConnectionService.sendGroupRichText(context, openGroup, rich)
                        if (groupEditingMessageId != null) editedMessageIds[groupEditingMessageId!!] = true
                        groupMessageText = ""; groupReplyToMessage = null; groupEditingMessageId = null; showGroupEmoji = false
                    },
                    replyPreviewText = groupReplyToMessage?.let { "Ø±Ø¯ Ø¹Ù„Ù‰ ${if (it.outgoing) "Ù†ÙØ³Ùƒ" else it.senderRedId.take(12)}: " + messageDisplayText(it) },
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
                    placeholderText = if (groupEditingMessageId != null) "ØªØ¹Ø¯ÙŠÙ„ Ø§Ù„Ø±Ø³Ø§Ù„Ø©â€¦" else if (groupReplyToMessage != null) "Ø§Ù„Ø±Ø¯ Ø¹Ù„Ù‰ Ø±Ø³Ø§Ù„Ø©â€¦" else "Ø±Ø³Ø§Ù„Ø© Ø¬Ù…Ø§Ø¹ÙŠØ© Ù…Ø´ÙØ±Ø©â€¦"
                )
            }
        }
    }
    when (val safetyState = safety.state) {
        SafetyState.Closed -> Unit
        is SafetyState.Loading -> AlertDialog(onDismissRequest = safety::close, title = { Text("Ø±Ù…Ø² Ø§Ù„Ø£Ù…Ø§Ù†") }, text = { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }, confirmButton = { TextButton(safety::close) { Text("Ø¥Ù„ØºØ§Ø¡") } })
        is SafetyState.Error -> AlertDialog(onDismissRequest = safety::close, title = { Text("ØªØ¹Ø°Ø± Ø§Ù„ØªØ­Ù‚Ù‚") }, text = { Text(safetyState.message) }, confirmButton = { TextButton(safety::close) { Text("Ø¥ØºÙ„Ø§Ù‚") } })
        is SafetyState.Ready -> if (showSafetyScanner) AlertDialog(
            onDismissRequest = { showSafetyScanner = false },
            title = { Text("Ø§Ù…Ø³Ø­ Ø±Ù…Ø² Ø§Ù„Ø·Ø±Ù Ø§Ù„Ø¢Ø®Ø±") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp))) {
                        SafetyQrScanner(onCode = { safety.verifyScanned(it); showSafetyScanner = false })
                    }
                    Text("ØªØªÙ… Ø§Ù„Ù…Ø¹Ø§Ù„Ø¬Ø© Ø¹Ù„Ù‰ Ø§Ù„Ø¬Ù‡Ø§Ø² ÙÙ‚Ø·ØŒ ÙˆÙ„Ø§ ØªÙØ±ÙØ¹ ØµÙˆØ± Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ø¥Ù„Ù‰ Ø§Ù„Ø®Ø§Ø¯Ù….", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton({ showSafetyScanner = false }) { Text("Ø¥Ù„ØºØ§Ø¡") } }
        ) else AlertDialog(
            onDismissRequest = { safety.clearScanError(); safety.close() },
            title = { Text(if (safetyState.verified) "ØªÙ… Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„Ù‡ÙˆÙŠØ©" else "Ù…Ù‚Ø§Ø±Ù†Ø© Ø±Ù…Ø² Ø§Ù„Ø£Ù…Ø§Ù†") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(safetyState.qr, "QR Ù„Ø±Ù…Ø² Ø§Ù„Ø£Ù…Ø§Ù†", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Text(safetyState.number, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = AqyalGold)
                Text("Ø§Ù„Ø¬Ù‡Ø§Ø² ${safetyState.deviceId} Â· ${safetyState.fingerprint.chunked(8).joinToString(" ")}", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                safetyState.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center) }
                Text("Ø§Ù…Ø³Ø­ Ø±Ù…Ø² Ø§Ù„Ø·Ø±Ù Ø§Ù„Ø¢Ø®Ø± ÙˆØ¬Ù‡Ù‹Ø§ Ù„ÙˆØ¬Ù‡ØŒ Ø£Ùˆ Ù‚Ø§Ø±Ù† Ø§Ù„Ø±Ù‚Ù… Ø¹Ø¨Ø± Ù‚Ù†Ø§Ø© Ù…ÙˆØ«ÙˆÙ‚Ø© Ù…Ø³ØªÙ‚Ù„Ø©.", fontSize = 11.sp, textAlign = TextAlign.Center)
                if (!safetyState.verified) OutlinedButton({
                    safety.clearScanError()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showSafetyScanner = true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Text(" Ù…Ø³Ø­ Ø±Ù…Ø² Ø§Ù„Ø·Ø±Ù Ø§Ù„Ø¢Ø®Ø±") }
            } },
            confirmButton = { if (!safetyState.verified) Button(safety::markVerified) { Text("Ø§Ù„Ø£Ø±Ù‚Ø§Ù… Ù…ØªØ·Ø§Ø¨Ù‚Ø© ÙŠØ¯ÙˆÙŠÙ‹Ø§") } else TextButton(safety::close) { Text("ØªÙ…") } },
            dismissButton = { if (!safetyState.verified) TextButton(safety::close) { Text("Ø¥Ù„ØºØ§Ø¡") } }
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
                // Ø§Ù„Ù…Ø¹Ø§ÙŠÙ†Ø©: Ø§Ù„Ø±Ø³Ø§Ù„Ø© Ø§Ù„Ù…Ø­Ø¯Ø¯Ø©
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.outgoing) "Ø£Ù†Øª" else (if (isGroupMsg) message.senderRedId.take(12) else "Ø§Ù„Ù…Ø±Ø³Ù„"), color = YounesEmerald, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(messageDisplayText(message), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }

                // ØªÙØ§Ø¹Ù„ Ø³Ø±ÙŠØ¹ Ø¨Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ â€” Ø£Ø¹Ù„Ù‰ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø© (E2EE)
                ReactionEmojiBar(onPick = { emoji ->
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
                    // ØªØ­Ø¯ÙŠØ« Ù…Ø­Ù„ÙŠ ÙÙˆØ±ÙŠ
                    val current = reactionsByMessage[message.id].orEmpty()
                    val withoutMine = current.filterNot { it.senderId == account.redId }
                    reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + MessageReactionEntity(message.id, convId, account.redId, emoji, System.currentTimeMillis())
                    selectedChatMessage = null
                })

                MessageActionRow(Icons.Default.Quickreply, "Ø§Ù„Ø±Ø¯", "Ø±Ø¯ Ø¹Ù„Ù‰ Ù‡Ø°Ù‡ Ø§Ù„Ø±Ø³Ø§Ù„Ø©") {
                    if (isGroupMsg) groupReplyToMessage = message else replyToMessage = message
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Forward, "Ø¥Ø¹Ø§Ø¯Ø© ØªÙˆØ¬ÙŠÙ‡", "Ø£Ø±Ø³Ù„Ù‡Ø§ Ø¥Ù„Ù‰ Ø¬Ù‡Ø© Ø£Ø®Ø±Ù‰") {
                    pendingForwardMessage = message; showDirectory = true; selectedChatMessage = null
                }
                val messageTextForAction = messageDisplayText(message)
                if (messageTextForAction.isNotBlank()) {
                    MessageActionRow(Icons.Default.ContentCopy, "Ù†Ø³Ø®", "Ø§Ù†Ø³Ø® Ø§Ù„Ù†Øµ") {
                        val ctx = context
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ø±Ø³Ø§Ù„Ø©", messageTextForAction))
                        selectedChatMessage = null
                    }
                    MessageActionRow(Icons.Default.Share, "Ù…Ø´Ø§Ø±ÙƒØ©", "Ø´Ø§Ø±Ùƒ Ø¹Ø¨Ø± ØªØ·Ø¨ÙŠÙ‚Ø§Øª Ø£Ø®Ø±Ù‰") {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, messageTextForAction)
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Ù…Ø´Ø§Ø±ÙƒØ© Ø§Ù„Ø±Ø³Ø§Ù„Ø©").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing && message.type == "RICH_TEXT" && payload?.action == "MESSAGE") {
                    MessageActionRow(Icons.Default.Edit, "ØªØ¹Ø¯ÙŠÙ„", "Ø¹Ø¯Ù‘Ù„ Ø§Ù„Ù†Øµ Ø§Ù„Ù…Ø±Ø³Ù„") {
                        if (isGroupMsg) {
                            groupEditingMessageId = message.id; groupMessageText = payload.text
                        } else {
                            editingMessageId = message.id; messageText = payload.text
                        }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing) {
                    MessageActionRow(Icons.Default.Delete, "Ø­Ø°Ù Ù„Ø¯Ù‰ Ø§Ù„Ø¬Ù…ÙŠØ¹", "Ø§Ø­Ø°Ù Ø§Ù„Ø±Ø³Ø§Ù„Ø© Ù„Ø¯Ù‰ Ø§Ù„ÙƒÙ„") {
                        if (isGroupMsg) {
                            val grp = groups.groups.firstOrNull { it.id == message.conversationId }
                            if (grp != null) RedConnectionService.sendGroupRichText(context, grp, RichMessage(action = "DELETE", deleteOf = message.id))
                        } else {
                            RedConnectionService.sendRichText(context, target, message.conversationId, RichMessage(action = "DELETE", deleteOf = message.id))
                        }
                        // ØªØ·Ø¨ÙŠÙ‚ Ù…Ø­Ù„ÙŠ ÙÙˆØ±ÙŠ: Ø§Ù„Ø®Ø§Ø¯Ù… Ù„Ø§ ÙŠØ±Ø¯Ù‘Ø¯ Ø£Ù…Ø± Ø§Ù„Ø­Ø°Ù Ø¥Ù„Ù‰ Ù†ÙØ³ Ø§Ù„Ø¬Ù„Ø³Ø© Ø§Ù„ØªÙŠ Ø£Ø±Ø³Ù„ØªÙ‡
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
                MessageActionRow(Icons.Default.Delete, "Ø­Ø°Ù Ù„Ø¯ÙŠÙ‘", "Ø§Ø­Ø°ÙÙ‡Ø§ Ù…Ù† Ù‡Ø°Ø§ Ø§Ù„Ø¬Ù‡Ø§Ø² ÙÙ‚Ø·") {
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
                    MessageActionRow(if (groupPinnedMessages.containsKey(message.id)) Icons.Default.Star else Icons.Default.StarBorder, if (groupPinnedMessages.containsKey(message.id)) "Ø¥Ù„ØºØ§Ø¡ Ø§Ù„ØªØ«Ø¨ÙŠØª" else "ØªØ«Ø¨ÙŠØª", "ØªØ«Ø¨ÙŠØª Ù‡Ø°Ù‡ Ø§Ù„Ø±Ø³Ø§Ù„Ø© Ø£Ø¹Ù„Ù‰ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") {
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
                MessageActionRow(Icons.Default.NotificationsOff, "ÙƒØªÙ… Ø§Ù„Ø¥Ø´Ø¹Ø§Ø±Ø§Øª", "ÙƒØªÙ… Ù‡Ø°Ù‡ Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø© 8 Ø³Ø§Ø¹Ø§Øª") {
                    val convId = if (isGroupMsg) message.conversationId else conversationId(account.redId, target)
                    val muted = localMessages.conversationPreference(convId).third > System.currentTimeMillis()
                    localMessages.setConversationPreference(convId, "muted_until", if (muted) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L)
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Info, "Ù…Ø¹Ù„ÙˆÙ…Ø§Øª Ø§Ù„Ø±Ø³Ø§Ù„Ø©", "Ø§Ù„ØªÙØ§ØµÙŠÙ„ ÙˆØ§Ù„ÙˆÙ‚Øª ÙˆØ§Ù„Ø­Ø§Ù„Ø©") {
                    messageInfo = message; selectedChatMessage = null
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("1Ø³Ø§Ø¹Ø©" to 3_600_000L, "ÙŠÙˆÙ…" to 86_400_000L, "Ø£Ø³Ø¨ÙˆØ¹" to 604_800_000L, "90ÙŠÙˆÙ…" to 7_776_000_000L, "Ø¥ÙŠÙ‚Ø§Ù" to 0L).forEach { (label, ms) ->
                        OutlinedButton({
                            val value = if (ms > 0) ms else null
                            if (isGroupMsg) {
                                groupDisappearingMs = value
                                localMessages.setConversationDisappearingDuration(message.conversationId, value)
                            } else disappearingDurationMs = value
                            selectedChatMessage = null
                        }, Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
                    }
                }
                TextButton({ selectedChatMessage = null }, Modifier.align(Alignment.CenterHorizontally)) { Text("Ø¥ØºÙ„Ø§Ù‚", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                // Ø±Ø£Ø³ Ø§Ù„ØµØ¯ÙŠÙ‚
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.displayName.take(1))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(localMessages.conversationCustomName(conversationKey) ?: person.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("@${person.username} â€¢ ${person.redId}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Ø¥Ø¹Ø§Ø¯Ø© ØªØ³Ù…ÙŠØ© Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø©
                OutlinedTextField(editingName, { editingName = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Ø§Ø³Ù… Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø© (ØªØ¬Ø§ÙˆØ²)") }, singleLine = true)
                Button({
                    localMessages.setConversationCustomName(conversationKey, editingName.trim())
                    editingName = editingName.trim()
                }, Modifier.fillMaxWidth(), enabled = editingName.isNotBlank() && editingName != person.displayName) { Text("Ø­ÙØ¸ Ø§Ù„Ø§Ø³Ù…") }

                // Ø§Ù„Ø®Ù„ÙÙŠØ© â€” Ø§Ø®ØªÙŠØ§Ø± ØªØ¯Ø±Ø¬ Ù„ÙˆÙ†ÙŠ
                Text("Ø®Ù„ÙÙŠØ© Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø©", color = YounesEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                val wallpapers = listOf(0, 1, 2, 3, 4, 5)
                val wpColors = listOf(
                    Color(0xFF0A1628), Color(0xFF1A3A5F), Color(0xFF004D3A), Color(0xFF3D2E00), Color(0xFF2A0A2A), Color(0xFF002F4A)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(wallpapers) { id ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable { selectedWallpaper = id; localMessages.setConversationWallpaper(conversationKey, id) },
                                shape = RoundedCornerShape(14.dp),
                                color = wpColors[id]
                            ) { if (selectedWallpaper == id) Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White) } }
                        }
                    }
                }

                // ØªØ«Ø¨ÙŠØª / Ø£Ø±Ø´ÙØ© / ÙƒØªÙ…
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "pinned", if (preference.first) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.first) "Ø¥Ù„ØºØ§Ø¡ Ø§Ù„ØªØ«Ø¨ÙŠØª" else "ØªØ«Ø¨ÙŠØª", fontSize = 12.sp) }
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "archived", if (preference.second) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.second) "Ø¥Ù„ØºØ§Ø¡ Ø§Ù„Ø£Ø±Ø´ÙØ©" else "Ø£Ø±Ø´ÙØ©", fontSize = 12.sp) }
                }
                OutlinedButton({ localMessages.setConversationPreference(conversationKey, "muted_until", if (preference.third > System.currentTimeMillis()) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L) }, Modifier.fillMaxWidth()) { Text(if (preference.third > System.currentTimeMillis()) "Ø¥Ù„ØºØ§Ø¡ Ø§Ù„ÙƒØªÙ…" else "ÙƒØªÙ… 8 Ø³Ø§Ø¹Ø§Øª") }
                OutlinedButton({ safety.open(person.redId); selectedContact = null }, Modifier.fillMaxWidth()) { Text("Ø±Ù…Ø² Ø§Ù„Ø£Ù…Ø§Ù† ÙˆØ§Ù„ØªØ­Ù‚Ù‚") }

                // Ø§Ù„Ø­Ø¸Ø± / ÙÙƒ Ø§Ù„Ø­Ø¸Ø±
                val isBlocked = person.redId in blockedIds
                Button({
                    if (isBlocked) directory.unblock(person) else directory.block(person)
                    selectedContact = null
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) YounesEmerald else MaterialTheme.colorScheme.error)) {
                    Text(if (isBlocked) "ÙÙƒ Ø§Ù„Ø­Ø¸Ø±" else "Ø­Ø¸Ø± Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù…", color = if (isBlocked) Color(0xFF002118) else Color.White)
                }

                // Ø¥Ø²Ø§Ù„Ø© / Ø¨Ù„Ø§Øº
                OutlinedButton({ directory.remove(person); selectedContact = null }, Modifier.fillMaxWidth()) { Text("Ø¥Ø²Ø§Ù„Ø© Ù…Ù† Ø§Ù„Ø£ØµØ¯Ù‚Ø§Ø¡") }
                OutlinedTextField(reportDetails, { reportDetails = it }, Modifier.fillMaxWidth(), label = { Text("ØªÙØ§ØµÙŠÙ„ Ø¨Ù„Ø§Øº Ø§Ø®ØªÙŠØ§Ø±ÙŠ") }, maxLines = 2)
                OutlinedButton({ directory.report(person, "SPAM", reportDetails); reportDetails = "" }, Modifier.fillMaxWidth()) { Text("Ø¥Ø¨Ù„Ø§Øº Ø¹Ù† Ø¥Ø²Ø¹Ø§Ø¬/Ø§Ø­ØªÙŠØ§Ù„") }
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
                                Avatar(member.username.take(1)); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("@${member.username}"); Text(member.redId, color = AqyalCyanGlow, fontSize = 10.sp) }
                                AssistChip({}, { Text(groupRoleLabel(member.role)) }, enabled = false)
                                if (manageable) Icon(Icons.Default.MoreVert, "Ø¥Ø¯Ø§Ø±Ø© Ø§Ù„Ø¹Ø¶Ùˆ", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (canManage) {
                        OutlinedButton({ groupAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, Modifier.fillMaxWidth()) { Text("ØªØºÙŠÙŠØ± ØµÙˆØ±Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") }
                        OutlinedTextField(memberRedId, { memberRedId = YounesId.normalizeInput(it) }, Modifier.fillMaxWidth(), label = { Text("Ø¥Ø¶Ø§ÙØ© Ø¹Ø¶Ùˆ Ø¨ÙˆØ§Ø³Ø·Ø© Ù…Ø¹Ø±Ù‘Ù ÙŠÙˆÙ†Ø³") }, placeholder = { Text(YounesId.PLACEHOLDER) }, singleLine = true)
                        Button({ groups.addMember(selectedGroup, memberRedId) { memberRedId = "" } }, Modifier.fillMaxWidth(), enabled = memberRedId.matches(RED_ID_PATTERN) && groups.state != GroupState.Saving) { Text("Ø¥Ø¶Ø§ÙØ© Ø¹Ø¶Ùˆ") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ groups.createInvite(selectedGroup) }, Modifier.weight(1f)) { Text("Ø±Ø§Ø¨Ø· Ø¯Ø¹ÙˆØ©") }
                            OutlinedButton({ groups.loadJoinRequests(selectedGroup) }, Modifier.weight(1f)) { Text("Ø·Ù„Ø¨Ø§Øª Ø§Ù„Ø§Ù†Ø¶Ù…Ø§Ù…") }
                        }
                        groups.latestInvite?.let { invite ->
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card { Column(Modifier.padding(10.dp)) { Text("Ø¯Ø¹ÙˆØ© ØµØ§Ù„Ø­Ø© Ø­ØªÙ‰ ${invite.expiresAt}", style = MaterialTheme.typography.bodySmall); Text(invite.token, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AqyalCyanGlow); TextButton({ clipboard.setText(AnnotatedString(invite.token)) }) { Text("Ù†Ø³Ø® Ø±Ù…Ø² Ø§Ù„Ø¯Ø¹ÙˆØ©") } } }
                        }
                        groups.joinRequests.forEach { request -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("@${request.username}", Modifier.weight(1f)); TextButton({ groups.resolveJoin(selectedGroup, request, false) }) { Text("Ø±ÙØ¶") }; Button({ groups.resolveJoin(selectedGroup, request, true) }) { Text("Ù‚Ø¨ÙˆÙ„") } } }
                    }
                }
            },
            confirmButton = { TextButton({ manageGroupId = null }) { Text("Ø¥ØºÙ„Ø§Ù‚") } },
            dismissButton = {
                if (myRole == "OWNER") TextButton({ deleteGroupId = selectedGroup.id }) { Text("Ø­Ø°Ù Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©", color = MaterialTheme.colorScheme.error) }
                else TextButton({ groups.leave(selectedGroup) { manageGroupId = null; groupConversationId = null } }) { Text("Ù…ØºØ§Ø¯Ø±Ø©", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
    val managedMember = selectedGroupMember
    if (selectedGroup != null && managedMember != null) AlertDialog(
        onDismissRequest = { selectedGroupMember = null },
        title = { Text("Ø¥Ø¯Ø§Ø±Ø© @${managedMember.username}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(managedMember.redId, color = AqyalCyanGlow)
            if (selectedGroup.members.firstOrNull { it.redId == account.redId }?.role == "OWNER") {
                OutlinedButton({ groups.updateRole(selectedGroup, managedMember, if (managedMember.role == "ADMIN") "MEMBER" else "ADMIN"); selectedGroupMember = null }, Modifier.fillMaxWidth()) {
                    Text(if (managedMember.role == "ADMIN") "Ø¥Ø±Ø¬Ø§Ø¹Ù‡ Ø¥Ù„Ù‰ Ø¹Ø¶Ùˆ" else "ØªØ±Ù‚ÙŠØªÙ‡ Ø¥Ù„Ù‰ Ù…Ø³Ø¤ÙˆÙ„")
                }
                OutlinedButton({ groups.transferOwnership(selectedGroup, managedMember) { selectedGroupMember = null; manageGroupId = null } }, Modifier.fillMaxWidth()) { Text("Ù†Ù‚Ù„ Ù…Ù„ÙƒÙŠØ© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ø¥Ù„ÙŠÙ‡") }
            }
            Button({ groups.removeMember(selectedGroup, managedMember); selectedGroupMember = null }, Modifier.fillMaxWidth()) { Text("Ø¥Ø²Ø§Ù„Ø© Ù…Ù† Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") }
            Text("ØªØºÙŠÙŠØ± Ø§Ù„Ø¹Ø¶ÙˆÙŠØ© ÙŠØ¬Ø¨ Ø£Ù† ÙŠØ¯ÙˆØ± Sender Key Ø¹Ù†Ø¯Ù…Ø§ ØªÙƒØªÙ…Ù„ Ù…Ø­Ø§Ø¯Ø«Ø© Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª Ø§Ù„Ù…Ø´ÙØ±Ø©.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ selectedGroupMember = null }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
    )
    groups.groups.firstOrNull { it.id == deleteGroupId }?.let { deleting ->
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("Ø­Ø°Ù ${deleting.name} Ù†Ù‡Ø§Ø¦ÙŠÙ‹Ø§ØŸ") },
            text = { Text("Ø³ÙŠÙØ­Ø°Ù Ø³Ø¬Ù„ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© ÙˆØ¹Ø¶ÙˆÙŠØªÙ‡Ø§ Ù…Ù† Ø§Ù„Ø®Ø§Ø¯Ù…. Ù„Ø§ ÙŠÙ…ÙƒÙ† Ø§Ù„ØªØ±Ø§Ø¬Ø¹ Ø¹Ù† Ø§Ù„Ø¹Ù…Ù„ÙŠØ©.") },
            confirmButton = { Button({ groups.deleteGroup(deleting) { deleteGroupId = null; manageGroupId = null; groupConversationId = null } }) { Text("Ø­Ø°Ù Ù†Ù‡Ø§Ø¦ÙŠ") } },
            dismissButton = { TextButton({ deleteGroupId = null }) { Text("Ø¥Ù„ØºØ§Ø¡") } }
        )
    }
    if (showGroupPollDialog) {
        val openGroupForPoll = groups.groups.firstOrNull { it.id == groupConversationId }
        AlertDialog(
            onDismissRequest = { showGroupPollDialog = false },
            title = { Text("Ø§Ø³ØªØ·Ù„Ø§Ø¹ ÙÙŠ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(groupPollQuestion, { groupPollQuestion = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("Ø§Ù„Ø³Ø¤Ø§Ù„") }, maxLines = 3)
                    groupPollOptions.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { next -> groupPollOptions = groupPollOptions.toMutableList().also { it[index] = next.take(80) } },
                            Modifier.fillMaxWidth(), label = { Text("Ø§Ù„Ø®ÙŠØ§Ø± ${index + 1}") }, singleLine = true
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (groupPollOptions.size < 6) groupPollOptions = groupPollOptions + "" }, Modifier.weight(1f), enabled = groupPollOptions.size < 6) { Text("+ Ø®ÙŠØ§Ø±") }
                        OutlinedButton({ if (groupPollOptions.size > 2) groupPollOptions = groupPollOptions.dropLast(1) }, Modifier.weight(1f), enabled = groupPollOptions.size > 2) { Text("- Ø®ÙŠØ§Ø±") }
                    }
                }
            },
            confirmButton = {
                val validPoll = groupPollQuestion.isNotBlank() && groupPollOptions.count { it.trim().length >= 2 } >= 2
                Button(
                    enabled = validPoll && openGroupForPoll != null,
                    onClick = {
                        val poll = com.red.sovereign.core.InlinePoll(
                            question = groupPollQuestion.trim(),
                            options = groupPollOptions.map { it.trim() }.filter { it.length >= 2 },
                            pollId = "poll-${System.currentTimeMillis()}"
                        )
                        val rich = RichMessage(text = "", poll = poll)
                        openGroupForPoll?.let { RedConnectionService.sendGroupRichText(context, it, rich) }
                        showGroupPollDialog = false
                        groupPollQuestion = ""
                        groupPollOptions = listOf("", "")
                    }
                ) { Text("Ø¥Ø±Ø³Ø§Ù„ Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹") }
            },
            dismissButton = { TextButton({ showGroupPollDialog = false }) { Text("Ø¥Ù„ØºØ§Ø¡") } }
        )
    }
    if (showDisappearingDialog) AlertDialog(
        onDismissRequest = { showDisappearingDialog = false },
        title = { Text("Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„Ù…Ø¤Ù‚ØªØ©") },
        text = { Column {
            Text("Ø³ØªÙØ­Ø°Ù Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„Ø¬Ø¯ÙŠØ¯Ø© ØªÙ„Ù‚Ø§Ø¦ÙŠØ§Ù‹ Ù…Ù† Ø§Ù„Ø·Ø±ÙÙŠÙ† Ø¨Ø¹Ø¯ Ø§Ù†Ù‚Ø¶Ø§Ø¡ Ø§Ù„Ù…Ø¯Ø© Ø§Ù„Ù…Ø®ØªØ§Ø±Ø©.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            listOf(0L to "Ø¥ÙŠÙ‚Ø§Ù", 3_600_000L to "1 Ø³Ø§Ø¹Ø©", 86_400_000L to "24 Ø³Ø§Ø¹Ø©", 604_800_000L to "7 Ø£ÙŠØ§Ù…", 7_776_000_000L to "90 ÙŠÙˆÙ…Ø§Ù‹").forEach { (ms, label) ->
                Row(Modifier.fillMaxWidth().clickable {
                    disappearingDurationMs = if (ms > 0) ms else null
                    showDisappearingDialog = false
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (disappearingDurationMs ?: 0L) == ms, onClick = null)
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = if ((disappearingDurationMs ?: 0L) == ms) FontWeight.Bold else FontWeight.Normal)
                }
            }
        } },
        confirmButton = { TextButton({ showDisappearingDialog = false }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
    )
    if (showGroupDisappearingDialog) AlertDialog(
        onDismissRequest = { showGroupDisappearingDialog = false },
        title = { Text("Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„Ù…Ø¤Ù‚ØªØ© ÙÙŠ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") },
        text = { Column {
            Text("Ø³ØªÙØ­Ø°Ù Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ Ø§Ù„Ø¬Ø¯ÙŠØ¯Ø© ØªÙ„Ù‚Ø§Ø¦ÙŠØ§Ù‹ Ø¨Ø¹Ø¯ Ø§Ù†Ù‚Ø¶Ø§Ø¡ Ø§Ù„Ù…Ø¯Ø© Ø§Ù„Ù…Ø®ØªØ§Ø±Ø©.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            listOf(0L to "Ø¥ÙŠÙ‚Ø§Ù", 3_600_000L to "1 Ø³Ø§Ø¹Ø©", 86_400_000L to "24 Ø³Ø§Ø¹Ø©", 604_800_000L to "7 Ø£ÙŠØ§Ù…", 7_776_000_000L to "90 ÙŠÙˆÙ…Ø§Ù‹").forEach { (ms, label) ->
                Row(Modifier.fillMaxWidth().clickable {
                    groupDisappearingMs = if (ms > 0) ms else null
                    showGroupDisappearingDialog = false
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (groupDisappearingMs ?: 0L) == ms, onClick = null)
                    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = if ((groupDisappearingMs ?: 0L) == ms) FontWeight.Bold else FontWeight.Normal)
                }
            }
        } },
        confirmButton = { TextButton({ showGroupDisappearingDialog = false }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
    )
    if (showMediaGallery && target.isNotBlank()) {
        val convKey = conversationId(account.redId, target)
        MediaGalleryDialog(
            title = "Ø§Ù„ÙˆØ³Ø§Ø¦Ø· Ø§Ù„Ù…Ø´ØªØ±ÙƒØ©",
            messages = decrypted.filter { it.conversationId == convKey },
            attachments = attachments,
            onDismiss = { showMediaGallery = false }
        )
    }
    if (showGroupMediaGallery && groupConversationId != null) {
        MediaGalleryDialog(
            title = "ÙˆØ³Ø§Ø¦Ø· Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©",
            messages = decrypted.filter { it.conversationId == groupConversationId },
            attachments = attachments,
            onDismiss = { showGroupMediaGallery = false }
        )
    }
    if (showMessageSearch) AlertDialog(
        onDismissRequest = { showMessageSearch = false; messageSearchQuery = "" },
        title = { Text("Ø§Ù„Ø¨Ø­Ø« Ø¯Ø§Ø®Ù„ Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø©") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(messageSearchQuery, { messageSearchQuery = it }, Modifier.fillMaxWidth(), label = { Text("ÙƒÙ„Ù…Ø© Ø£Ùˆ Ø¹Ø¨Ø§Ø±Ø©") }, singleLine = true)
            val currentConversation = groupConversationId ?: conversationId(account.redId, target)
            // ðŸ” Ø§Ù„Ø¨Ø­Ø« ÙŠØ³ØªØ¹Ù„Ù… Ø§Ù„Ø³Ø¬Ù„ Ø§Ù„Ù…Ø­Ù„ÙŠ Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ (Room) ÙˆÙŠØ¹Ø±Ø¶ Ø§Ù„Ù†Øµ Ø§Ù„Ù…ÙÙƒÙˆÙƒ ÙÙ‚Ø·
            val searchResults = remember { mutableStateOf<List<com.red.sovereign.core.database.LocalHistoryEntity>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(messageSearchQuery, currentConversation) {
                searchResults.value = if (messageSearchQuery.length >= 2) {
                    repository.searchAll(messageSearchQuery).filter { it.conversationId == currentConversation && searchDisplayText(it).isNotBlank() }
                } else emptyList()
            }
            if (messageSearchQuery.length >= 2) {
                Text("${searchResults.value.size} Ù†ØªÙŠØ¬Ø©", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                LazyColumn(Modifier.height(260.dp)) { items(searchResults.value, key = { it.id }) { result -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(10.dp)) { Text(searchDisplayText(result), maxLines = 4); Row(verticalAlignment = Alignment.CenterVertically) { Text(if (result.outgoing) "Ø£Ù†Øª" else result.senderId.take(12), color = AqyalCyanGlow, style = MaterialTheme.typography.labelSmall); Text(" â€¢ " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.createdAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } } } }
            } else {
                Text("Ø§ÙƒØªØ¨ ÙƒÙ„Ù…ØªÙŠÙ† Ø¹Ù„Ù‰ Ø§Ù„Ø£Ù‚Ù„ Ù„Ù„Ø¨Ø­Ø« ÙÙŠ Ù‡Ø°Ù‡ Ø§Ù„Ù…Ø­Ø§Ø¯Ø«Ø©.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } },
        confirmButton = { TextButton({ showMessageSearch = false; messageSearchQuery = "" }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
    )
    if (showDirectory) AlertDialog(
        onDismissRequest = { showDirectory = false; pendingForwardMessage = null; directory.clear() },
        title = { Text("Ø£Ø´Ø®Ø§Øµ ÙŠÙˆÙ†Ø³") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(directoryQuery, { directoryQuery = it }, Modifier.fillMaxWidth(), label = { Text("username Ø£Ùˆ Ù…Ø¹Ø±Ù‘Ù ÙŠÙˆÙ†Ø³") }, singleLine = true)
                Button({ directory.search(directoryQuery) }, Modifier.fillMaxWidth(), enabled = directoryQuery.trim().length >= 3 && directory.state != DirectoryState.Loading) {
                    Icon(Icons.Default.Search, null); Text(" Ø¨Ø­Ø« Ø¢Ù…Ù†")
                }
                // ðŸ“¤ Ø§Ù„ØªÙˆØ¬ÙŠÙ‡ Ø¥Ù„Ù‰ Ù…Ø¬Ù…ÙˆØ¹Ø© â€” ÙŠØ¸Ù‡Ø± ÙÙ‚Ø· Ø£Ø«Ù†Ø§Ø¡ Ø¬Ù„Ø³Ø© Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªÙˆØ¬ÙŠÙ‡
                if (pendingForwardMessage != null && groups.groups.isNotEmpty()) {
                    Text("Ø§Ù„ØªÙˆØ¬ÙŠÙ‡ Ø¥Ù„Ù‰ Ù…Ø¬Ù…ÙˆØ¹Ø©:", color = AqyalGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.height(170.dp)) {
                        items(groups.groups, key = { it.id }) { group ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val forward = pendingForwardMessage
                                if (forward != null) {
                                    RedConnectionService.sendGroupRichText(context, group, RichMessage(text = messageDisplayText(forward), forwardOf = forward.id))
                                    pendingForwardMessage = null
                                }
                                showDirectory = false; directory.clear()
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                GroupAvatar(group, groups)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${group.members.size} Ø¹Ø¶Ùˆ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Text("ØªÙˆØ¬ÙŠÙ‡", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                when (val state = directory.state) {
                    DirectoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                    is DirectoryState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is DirectoryState.Message -> Text(state.text, color = AqyalGold)
                    DirectoryState.Ready -> if (directory.results.isEmpty()) Text("Ù„Ø§ ØªÙˆØ¬Ø¯ Ù†ØªØ§Ø¦Ø¬ Ù…Ø·Ø§Ø¨Ù‚Ø©", color = Color.Gray) else LazyColumn(Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(directory.results, key = { it.redId }) { person ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(person.displayName.take(1)); Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(person.displayName, fontWeight = FontWeight.Bold); Text("@${person.username} Â· ${person.redId}", color = AqyalCyanGlow, fontSize = 10.sp) }
                                    TextButton({
                                        val forward = pendingForwardMessage
                                        if (forward != null) {
                                            RedConnectionService.sendRichText(context, person.redId, conversationId(account.redId, person.redId), RichMessage(text = messageDisplayText(forward), forwardOf = forward.id))
                                            pendingForwardMessage = null
                                        } else target = person.redId
                                        showDirectory = false; directory.clear()
                                    }) { Text(if (pendingForwardMessage != null) "ØªÙˆØ¬ÙŠÙ‡" else "Ù…Ø­Ø§Ø¯Ø«Ø©") }
                                    Button({ directory.request(person) }) { Text("Ø¥Ø¶Ø§ÙØ©") }
                                }
                            }
                        }
                    }
                    DirectoryState.Idle -> Text("Ø§Ø¨Ø­Ø« Ø¹Ù† Ø´Ø®Øµ Ø¯ÙˆÙ† Ù…Ø´Ø§Ø±ÙƒØ© Ø±Ù‚Ù… Ù‡Ø§ØªÙ Ø£Ùˆ Ø¬Ù‡Ø§Øª Ø§ØªØµØ§Ù„ Ø§Ù„Ø¬Ù‡Ø§Ø².", color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton({ showDirectory = false; pendingForwardMessage = null; directory.clear() }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
    )
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Ø¥Ù†Ø´Ø§Ø¡ Ù…Ø¬Ù…ÙˆØ¹Ø© Ø¬Ø¯ÙŠØ¯Ø©") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { /* Future: Add Avatar upload */ }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CameraAlt, "Ø¥Ø¶Ø§ÙØ© ØµÙˆØ±Ø©", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ø§Ø³Ù… Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") }, singleLine = true)
            OutlinedTextField(groupDescription, { groupDescription = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Ø§Ù„ÙˆØµÙ â€” Ø§Ø®ØªÙŠØ§Ø±ÙŠ") }, minLines = 2, maxLines = 4)
            Text("Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ù…Ø´ÙØ±Ø© Ø¨Ø´ÙƒÙ„ Ø§ÙØªØ±Ø§Ø¶ÙŠ. Ù†Ø³ØªØ®Ø¯Ù… Sender Keys ÙÙŠ Ø­Ø§Ù„Ø© ÙˆØ¬ÙˆØ¯ Ø£Ø¹Ø¶Ø§Ø¡.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ groups.create(name, groupDescription.trim().takeIf(String::isNotEmpty)) { create = false; name = ""; groupDescription = "" } }, enabled = name.trim().length in 2..100 && groups.state != GroupState.Saving) { Text("Ø¥Ù†Ø´Ø§Ø¡ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©") } },
        dismissButton = { OutlinedButton({ create = false; name = ""; groupDescription = "" }) { Text("Ø¥Ù„ØºØ§Ø¡") } })
    if (showJoinGroup) AlertDialog(
        onDismissRequest = { showJoinGroup = false; joinToken = "" },
        title = { Text("Ø§Ù„Ø§Ù†Ø¶Ù…Ø§Ù… Ø¥Ù„Ù‰ Ù…Ø¬Ù…ÙˆØ¹Ø©") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(joinToken, { joinToken = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Ø±Ù…Ø² Ø§Ù„Ø¯Ø¹ÙˆØ©") }, singleLine = true); Text("Ù‚Ø¯ ÙŠØªØ·Ù„Ø¨ Ø§Ù„Ø§Ù†Ø¶Ù…Ø§Ù… Ù…ÙˆØ§ÙÙ‚Ø© Ù…Ø§Ù„Ùƒ Ø£Ùˆ Ù…Ø³Ø¤ÙˆÙ„ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button({ groups.joinWithToken(joinToken) { showJoinGroup = false; joinToken = "" } }, enabled = joinToken.length >= 32 && groups.state != GroupState.Saving) { Text("Ø¥Ø±Ø³Ø§Ù„ Ø§Ù„Ø·Ù„Ø¨") } },
        dismissButton = { TextButton({ showJoinGroup = false; joinToken = "" }) { Text("Ø¥Ù„ØºØ§Ø¡") } }
    )
    messageInfo?.let { info ->
        val richInfo = if (info.type == "RICH_TEXT") RichMessage.decode(info.plaintext) else null
        AlertDialog(
            onDismissRequest = { messageInfo = null },
            title = { Text("Ù…Ø¹Ù„ÙˆÙ…Ø§Øª Ø§Ù„Ø±Ø³Ø§Ù„Ø©") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageInfoRow("Ø§Ù„Ù…Ø±Ø³Ù„", if (info.outgoing) "Ø£Ù†Øª" else info.senderRedId)
                    MessageInfoRow("Ø§Ù„Ù†ÙˆØ¹", when (info.type) {
                        "RICH_TEXT" -> "Ù†Øµ ØºÙ†ÙŠ"; "VOICE" -> "Ø±Ø³Ø§Ù„Ø© ØµÙˆØªÙŠØ©"; "STICKER" -> "Ù…Ù„ØµÙ‚"
                        "IMAGE" -> "ØµÙˆØ±Ø©"; "VIDEO" -> "ÙÙŠØ¯ÙŠÙˆ"; "AUDIO" -> "ØµÙˆØª"; "FILE" -> "Ù…Ù„Ù"
                        else -> info.type
                    })
                    MessageInfoRow("Ø§Ù„ÙˆÙ‚Øª", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(info.timestamp)))
                    MessageInfoRow("Ø§Ù„Ø­Ø§Ù„Ø©", when (info.status) { "READ" -> "Ù…Ù‚Ø±ÙˆØ¡Ø© âœ“âœ“"; "DELIVERED" -> "ÙˆØµÙ„Øª âœ“âœ“"; else -> "Ø£ÙØ±Ø³Ù„Øª âœ“" })
                    if (editedMessageIds.containsKey(info.id)) MessageInfoRow("ØªØ¹Ø¯ÙŠÙ„", "Ù†Ø¹Ù…")
                    if (richInfo?.forwardOf != null) MessageInfoRow("Ø¥Ø¹Ø§Ø¯Ø© ØªÙˆØ¬ÙŠÙ‡", "Ù†Ø¹Ù…")
                    if (richInfo?.replyTo != null) MessageInfoRow("Ø±Ø¯ Ø¹Ù„Ù‰", richInfo?.replyTo?.take(12).orEmpty())
                    if (richInfo?.expiresAt != null) MessageInfoRow("Ø±Ø³Ø§Ù„Ø© Ù…Ø¤Ù‚ØªØ©", "Ù†Ø¹Ù…")
                    MessageInfoRow("Ø§Ù„Ù…Ø¹Ø±Ù‘Ù", info.id.take(16))
                }
            },
            confirmButton = { TextButton({ messageInfo = null }) { Text("Ø¥ØºÙ„Ø§Ù‚") } }
        )
    }
}

@Composable
private fun UnifiedCallsScreen(ownUserId: String, history: CallHistoryViewModel, contacts: List<com.red.sovereign.contacts.PublicRedProfile>, onlineIds: Set<String> = emptySet(), myDisplayName: String = "", onExplore: () -> Unit, onPstn: (String?) -> Unit = {}) {
    var showStatsScreen by remember { mutableStateOf(false) }
    var showScheduledCallsScreen by remember { mutableStateOf(false) }
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showDinstarDialog by remember { mutableStateOf(false) }
    var showGroupCallPicker by remember { mutableStateOf(false) }
    var showCreateConferenceScreen by remember { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var showPublicStreamsSearchDialog by remember { mutableStateOf(false) }
    var publicStreamSearchQuery by remember { mutableStateOf("") }
    var dinstarNumberInput by remember { mutableStateOf("") }
    var newCallTargetInput by remember { mutableStateOf("") }
    var isSpaceHost by remember { mutableStateOf(false) }
    var isBroadcaster by remember { mutableStateOf(false) }
    var roomInput by remember { mutableStateOf("") }
    val context = LocalContext.current

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

    val visible = history.filteredCalls

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Ù…Ø±ÙƒØ² Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª Ø§Ù„Ø³ÙŠØ§Ø¯ÙŠ", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFamily)
                Text("Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª Ø§Ù„ÙØ±Ø¯ÙŠØ©ØŒ Ø§Ù„Ù…Ø¤ØªÙ…Ø±Ø§ØªØŒ ÙˆØ§Ù„Ø¨Ø« Ø§Ù„Ù…Ø¨Ø§Ø´Ø±", color = Color.LightGray, fontSize = 12.sp, fontFamily = TajawalFamily)
            }
            IconButton(
                onClick = { showStatsScreen = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SovereignColors.SurfaceCard)
                    .border(1.dp, SovereignColors.GlassBorder, CircleShape)
            ) {
                Icon(Icons.Filled.Poll, "Ø¥Ø­ØµØ§Ø¦ÙŠØ§Øª ÙˆØªØ­Ù„ÙŠÙ„Ø§Øª Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª", tint = SovereignColors.GoldNeon, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        val callLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { /* will be handled per action */ },
            onDenied = { android.widget.Toast.makeText(context, "Ø§Ù„ØµÙ„Ø§Ø­ÙŠØ§Øª Ù…Ø·Ù„ÙˆØ¨Ø© Ù„Ù„Ø§ØªØµØ§Ù„", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val privateCallLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { showNewCallDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "Ù…Ø·Ù„ÙˆØ¨ Ø¥Ø°Ù† Ø§Ù„Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ† ÙˆØ§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ø¥Ø¬Ø±Ø§Ø¡ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val groupCallLauncher = rememberCallPermissionLauncher(
            needCamera = true,
            onGranted = { showGroupCallPicker = true },
            onDenied = { android.widget.Toast.makeText(context, "Ù…Ø·Ù„ÙˆØ¨ Ø¥Ø°Ù† Ø§Ù„Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ† ÙˆØ§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ø¬Ù…Ø§Ø¹ÙŠØ©", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val conferenceLauncher = rememberCallPermissionLauncher(
            // ðŸ”§ Ø¥ØµÙ„Ø§Ø­ Ø§Ù„Ø´Ø§Ø´Ø© Ø§Ù„Ø³ÙˆØ¯Ø§Ø¡: Ù…Ø¤ØªÙ…Ø± Ø§Ù„ÙÙŠØ¯ÙŠÙˆ ÙŠØ­ØªØ§Ø¬ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ â€” ÙƒØ§Ù†Øª needCamera=false ÙÙ„Ø§ ÙŠÙØ·Ù„Ø¨ Ø§Ù„Ø¥Ø°Ù†
            needCamera = true,
            onGranted = { showJoinDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "Ù…Ø·Ù„ÙˆØ¨ Ø¥Ø°Ù† Ø§Ù„Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ† ÙˆØ§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ù„Ù…Ø¤ØªÙ…Ø±", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val liveLauncher = rememberCallPermissionLauncher(
            // ðŸ”§ Ø§Ù„Ø¨Ø« Ø§Ù„Ù…Ø¨Ø§Ø´Ø± ÙƒÙ…Ø°ÙŠØ¹ ÙŠØ­ØªØ§Ø¬ ÙƒØ§Ù…ÙŠØ±Ø§ + Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ†
            needCamera = true,
            onGranted = { showLiveDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "Ù…Ø·Ù„ÙˆØ¨ Ø¥Ø°Ù† Ø§Ù„Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ† ÙˆØ§Ù„ÙƒØ§Ù…ÙŠØ±Ø§ Ù„Ù„Ø¨Ø«", android.widget.Toast.LENGTH_SHORT).show() }
        )
        val spaceLauncher = rememberCallPermissionLauncher(
            needCamera = false,
            onGranted = { showSpaceDialog = true },
            onDenied = { android.widget.Toast.makeText(context, "Ù…Ø·Ù„ÙˆØ¨ Ø¥Ø°Ù† Ø§Ù„Ù…ÙŠÙƒØ±ÙˆÙÙˆÙ† Ù„Ø¯Ø®ÙˆÙ„ Ø§Ù„Ù…Ø³Ø§Ø­Ø© Ø§Ù„ØµÙˆØªÙŠØ©", android.widget.Toast.LENGTH_SHORT).show() }
        )

        CallsHubLaunchers(
            onNewCall = { privateCallLauncher() },
            onGroupCallPicker = { groupCallLauncher() },
            onConference = { conferenceLauncher() },
            onSpace = { spaceLauncher() },
            onLive = { liveLauncher() },
            onPstn = { showDinstarDialog = true },
            onExplore = { onExplore() },
            onScheduledCalls = {
                showScheduledCallsScreen = true
            }
        )
        Spacer(Modifier.height(14.dp))

        // Ø´Ø±ÙŠØ· Ø§Ù„Ø¨Ø­Ø« Ø§Ù„Ù…Ø¨Ø§Ø´Ø± ÙÙŠ Ø§Ù„Ø³Ø¬Ù„
        OutlinedTextField(
            value = history.searchQuery,
            onValueChange = { history.searchQuery = it },
            placeholder = { Text("Ø¨Ø­Ø« ÙÙŠ Ø³Ø¬Ù„ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª (Ø§Ø³Ù… Ø£Ùˆ Ù…Ø¹Ø±Ù Ø£Ùˆ Ø±Ù‚Ù…)...", fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = SovereignColors.EmeraldNeon, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (history.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { history.searchQuery = "" }) {
                        Icon(Icons.Filled.Close, "Ù…Ø³Ø­", tint = Color.Gray, modifier = Modifier.size(16.dp))
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
            Text("Ø§Ù„Ø³Ø¬Ù„ Ø§Ù„Ù…Ø´ÙØ±", color = Color.White.copy(0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showStatsScreen = true }) {
                Icon(Icons.Filled.Poll, null, tint = SovereignColors.GoldNeon, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ø§Ù„Ø¥Ø­ØµØ§Ø¦ÙŠØ§Øª", color = SovereignColors.GoldNeon, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { showRecordings = true }) {
                Icon(Icons.Default.FiberManualRecord, null, tint = AqyalGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ø§Ù„ØªØ³Ø¬ÙŠÙ„Ø§Øª", color = AqyalGold, fontSize = 12.5.sp)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(CallFilterType.values()) { fType ->
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
            history.error != null -> EmptyState(Icons.Default.History, "ØªØ¹Ø°Ø± ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ø³Ø¬Ù„", history.error.orEmpty())
            visible.isEmpty() -> EmptyState(Icons.Default.History, "Ù„Ø§ ØªÙˆØ¬Ø¯ Ù…ÙƒØ§Ù„Ù…Ø§Øª ØªØ·Ø§Ø¨Ù‚ Ø§Ù„Ø¨Ø­Ø«", "Ø³ØªØ¸Ù‡Ø± Ù‡Ù†Ø§ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª Ø§Ù„Ù…ÙÙ„ØªØ±Ø© Ù…Ø¹ Ø´Ø§Ø±Ø© ØªÙˆØ¶Ø­ Ù…Ø³Ø§Ø± ÙŠÙˆÙ†Ø³ Ø£Ùˆ DINSTAR.")
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
            onJoinExisting = { roomId ->
                ConferenceService.join(context, roomId, ownUserId, true, asHost = false)
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
            onStartBroadcasting = { title, isPriv, pass ->
                val streamId = "stream_${java.util.UUID.randomUUID().toString().take(8)}"
                LiveStreamService.start(context, streamId, ownUserId, true, title)
            },
            onWatchStream = { streamId ->
                LiveStreamService.start(context, streamId, ownUserId, false)
            }
        )
    }

    // ðŸŽ™ï¸ Ø­ÙˆØ§Ø± Ø§Ù„Ù…Ø³Ø§Ø­Ø§Øª Ø§Ù„ØµÙˆØªÙŠØ© â€” ØºØ±ÙØ© ØµÙˆØªÙŠØ© Ø¬Ù…Ø§Ø¹ÙŠØ© (Ù…Ø¤ØªÙ…Ø± Ø¨Ù„Ø§ ÙÙŠØ¯ÙŠÙˆ)
    if (showSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false },
            title = { Text("Ù…Ø³Ø§Ø­Ø© ØµÙˆØªÙŠØ© ÙŠÙˆÙ†Ø³") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ù…Ø³Ø§Ø­Ø© ØµÙˆØªÙŠØ© Ù…Ø´ÙØ±Ø© Ø¹Ø¨Ø± Ø®Ø§Ø¯Ù… SFU â€” ØµÙˆØª ÙÙ‚Ø·ØŒ Ø¨Ù„Ø§ ÙƒØ§Ù…ÙŠØ±Ø§.\nØ§ØªØ±Ùƒ Ø§Ù„Ø­Ù‚Ù„ ÙØ§Ø±ØºÙ‹Ø§ Ù„Ø¥Ù†Ø´Ø§Ø¡ ØºØ±ÙØ© Ø¬Ø¯ÙŠØ¯Ø© Ø¨Ù…Ø¹Ø±Ù‘Ù ØªÙ„Ù‚Ø§Ø¦ÙŠ.",
                        color = Color.Gray, fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ù…Ø¹Ø±Ù Ø§Ù„Ù…Ø³Ø§Ø­Ø© (Ø§Ø®ØªÙŠØ§Ø±ÙŠ â€” Ù…Ø«Ø§Ù„: majlis-01)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isSpaceHost, onCheckedChange = { isSpaceHost = it })
                        Text("Ø§Ù„Ø§Ù†Ø¶Ù…Ø§Ù… ÙƒÙ…Ø¶ÙŠÙ (Ù…ØªØ­Ø¯Ø«)", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSpaceDialog = false
                        // Ù…Ø¹Ø±Ù ØªÙ„Ù‚Ø§Ø¦ÙŠ ÙØ±ÙŠØ¯ Ø¥Ù† Ù„Ù… ÙŠÙØ¯Ø®Ù„ Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… ÙˆØ§Ø­Ø¯Ù‹Ø§
                        val spaceId = roomInput.trim().ifBlank { "space-${ownUserId.lowercase()}-${System.currentTimeMillis() % 100000}" }
                        // video=false â†’ Ù…Ø³Ø§Ø± ØµÙˆØªÙŠ ØµØ±Ù â€” Ù‡Ø°Ø§ Ù‡Ùˆ Ø§Ù„ÙØ±Ù‚ Ø¨ÙŠÙ† Ø§Ù„Ù…Ø³Ø§Ø­Ø© ÙˆØ§Ù„Ù…Ø¤ØªÙ…Ø± Ø§Ù„Ù…Ø±Ø¦ÙŠ
                        ConferenceService.join(context, spaceId, ownUserId, false, asHost = isSpaceHost || roomInput.isBlank())
                        roomInput = ""
                        isSpaceHost = false
                    }
                ) {
                    Text(if (roomInput.isBlank()) "Ø¥Ù†Ø´Ø§Ø¡ Ù…Ø³Ø§Ø­Ø© Ø¬Ø¯ÙŠØ¯Ø©" else "Ø¯Ø®ÙˆÙ„ Ø§Ù„Ù…Ø³Ø§Ø­Ø©")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false }) {
                    Text("Ø¥Ù„ØºØ§Ø¡")
                }
            }
        )
    }

    if (showDinstarDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDinstarDialog = false; dinstarNumberInput = "" },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.red.sovereign.features.pstn.DialPadScreen(
                onDismiss = { showDinstarDialog = false },
                onNavigateToWebRtcCall = { targetNum ->
                    showDinstarDialog = false
                    YounesCallService.start(context, targetNum, false)
                },
                onNavigateToPstnCall = { targetNum ->
                    showDinstarDialog = false
                    onPstn(targetNum)
                }
            )
        }
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
    // Ø´Ø§Ø±Ø© Ø§Ù„Ø­Ø§Ù„Ø©: Ù…Ø±ÙÙˆØ¶Ø© / Ù…Ø´ØºÙˆÙ„ / ÙØ´Ù„Øª â€” Ø¨Ø¯Ù„ Ø£Ù† ØªØ¸Ù‡Ø± ÙƒÙ„Ù‡Ø§ "ÙØ§Ø¦ØªØ©"
    val statusBadge = when (call.status) {
        "REJECTED" -> "Ù…Ø±ÙÙˆØ¶Ø©" to Color(0xFFE53935)
        "BUSY" -> "Ù…Ø´ØºÙˆÙ„" to Color(0xFFFF8F00)
        "FAILED" -> "ÙØ´Ù„Øª" to Color(0xFFB0BEC5)
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
                    else -> if (call.peerId.matches(RED_ID_PATTERN) && call.route != "DINSTAR") {
                        YounesCallService.start(context, call.peerId, call.type == "VIDEO")
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val glyph = callTypeGlyph(call.type, call.route)

        // Ø£ÙØªØ§Ø± Ø§Ù„Ù…ØªØµÙ„
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

        // Ø§Ù„ØªÙØ§ØµÙŠÙ„
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
                // Ø£ÙŠÙ‚ÙˆÙ†Ø© Ø§Ù„Ø³Ù‡Ù…
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
                        if (durationText.isNotEmpty()) append(" â€¢ $durationText")
                        if (call.route == "DINSTAR") append(" â€¢ Ø¹Ø¨Ø± Ø§Ù„Ù‡Ø§ØªÙ")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Ø²Ø± Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„Ø³Ø±ÙŠØ¹
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
                contentDescription = "Ø§ØªØµØ§Ù„",
                tint = YounesEmerald,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RoundCallAction(icon: ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    FilledIconButton(onClick, Modifier.size(62.dp), enabled = enabled) { Icon(icon, title, tint = if (enabled) color else Color.Gray, modifier = Modifier.size(30.dp)) }
    Text(title, fontSize = 11.sp); if (!enabled) Text("Ù‚ÙŠØ¯ Ø§Ù„Ø±Ø¨Ø·", color = Color.Gray, fontSize = 9.sp)
}

}
@Composable
private fun MoreScreen(
    account: AuthState.Authenticated,
    onDinstar: () -> Unit,
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
    onPstnConfig: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ù…Ø³Ø§Ø­Ø© ÙŠÙˆÙ†Ø³", style = MaterialTheme.typography.headlineMedium)
        Text("Ø§Ù„Ù‡ÙˆÙŠØ© ÙˆØ§Ù„Ø®Ø¯Ù…Ø§Øª Ø§Ù„Ø³ÙŠØ§Ø¯ÙŠØ© ÙÙŠ Ù…ÙƒØ§Ù† ÙˆØ§Ø­Ø¯", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth().clickable { onProfile() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(account.username.take(1))
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Ø§Ù„Ø¨Ø±ÙˆÙØ§ÙŠÙ„ Â· Ø§Ù„ØµÙˆØ±Ø© ÙˆØ§Ù„Ø¨Ø§ÙŠÙˆ ÙˆØ§Ù„Ù‡ÙˆÙŠØ©", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MoreOption(Icons.Default.AdminPanelSettings, "Ø§Ù„Ø¥Ø¯Ø§Ø±Ø© Ø§Ù„Ø³ÙŠØ§Ø¯ÙŠØ©", "Ù…Ø±Ø§Ù‚Ø¨Ø© Ø£Ø³Ø·ÙˆÙ„ DINSTAR ÙˆØ¹Ù…Ù„ÙŠØ§Øª ÙŠÙˆÙ†Ø³ Ù…Ø§Ø³ØªØ±", AqyalGold, click = onAdmin)
        MoreOption(Icons.Default.SimCard, "Ø§Ù„Ù‡Ø§ØªÙ Ø§Ù„ÙŠÙ…Ù†ÙŠ", "Ø§ØªØµØ§Ù„ ØµÙˆØªÙŠ Ù…ØµØ±Ø­ Ø¹Ø¨Ø± DINSTAR ÙˆØ´Ø±Ø§Ø¦Ø­ Ø§Ù„Ø´Ø¨ÙƒØ§Øª Ø§Ù„ÙŠÙ…Ù†ÙŠØ©", AqyalGold, click = onDinstar)
        MoreOption(Icons.Default.Security, "Ø§Ù„Ø®ØµÙˆØµÙŠØ© ÙˆØ§Ù„Ø£Ù…Ø§Ù†", "Ù…Ù† ÙŠØ±Ù‰ Ø¨ÙŠØ§Ù†Ø§ØªÙƒØŒ Ø§Ù„ØªØ´ÙÙŠØ±ØŒ ÙˆÙ‚ÙÙ„ Ø§Ù„Ø¨ØµÙ…Ø©", com.red.sovereign.ui.theme.YounesEmerald, click = onPrivacy)
        MoreOption(Icons.Default.CloudSync, "Ø§Ù„Ù†Ø³Ø® Ø§Ù„Ø§Ø­ØªÙŠØ§Ø·ÙŠ", "ØªØ£Ù…ÙŠÙ† Ù…Ø­Ø§Ø¯Ø«Ø§ØªÙƒ ÙˆØ³Ø¬Ù„Ø§ØªÙƒ Ù…Ø­Ù„ÙŠØ§Ù‹", com.red.sovereign.ui.theme.YounesGold, click = onBackup)
        MoreOption(Icons.Default.Devices, "Ø§Ù„Ø£Ø¬Ù‡Ø²Ø© Ø§Ù„Ù…ØªØµÙ„Ø©", "Ø¥Ø¯Ø§Ø±Ø© Ø¬Ù„Ø³Ø§Øª ÙŠÙˆÙ†Ø³ Ø¹Ù„Ù‰ ÙƒØ§ÙØ© Ø£Ø¬Ù‡Ø²ØªÙƒ", com.red.sovereign.ui.theme.AqyalCyanGlow, click = onDevices)
        MoreOption(Icons.Default.Settings, "Ø§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ø¹Ø§Ù…Ø©", "Ø§Ù„Ù‡ÙˆÙŠØ© ÙˆØ§Ù„Ø£Ø¬Ù‡Ø²Ø© ÙˆØ§Ù„Ø®Ø§Ø¯Ù… ÙˆØ§Ù„Ø¬Ù„Ø³Ø©", com.red.sovereign.ui.theme.YounesEmerald, click = onSettings)
        MoreOption(Icons.Default.Contacts, "Ø¬Ù‡Ø§Øª Ø§Ù„Ø§ØªØµØ§Ù„", "Ø§Ù„Ø£ØµØ¯Ù‚Ø§Ø¡ ÙˆØ·Ù„Ø¨Ø§Øª Ø§Ù„ØªÙˆØ§ØµÙ„ ÙˆØ§Ù„Ø­Ø¸Ø±", com.red.sovereign.ui.theme.AqyalCyanGlow, click = onContacts)
        MoreOption(Icons.Default.Public, "Ø§Ù„Ù…Ø¬ØªÙ…Ø¹Ø§Øª ÙˆØ§Ù„Ù‚Ù†ÙˆØ§Øª", "Ù…Ø¬ØªÙ…Ø¹Ø§Øª Ø¹Ø§Ù…Ø© ÙˆÙ‚Ù†ÙˆØ§Øª â€” Ø§Ù†Ø¶Ù… ÙˆØªØ§Ø¨Ø¹ (Ø¹Ø§Ù…ØŒ Ù„ÙŠØ³ Ù…Ø´ÙØ±Ø§Ù‹)", Color(0xFFA78BFA), enabled = true, click = onCommunities)
        MoreOption(Icons.Default.Event, "Ø§Ù„ÙØ¹Ø§Ù„ÙŠØ§Øª", "ÙØ¹Ø§Ù„ÙŠØ§Øª Ù…Ø¬ØªÙ…Ø¹ÙŠØ© Ù…Ø¹ RSVP ÙˆØªØ³Ø¬ÙŠÙ„ Ø­Ø¶ÙˆØ±", Color(0xFFE8B84A), enabled = true, click = onEvents)
        MoreOption(Icons.Default.Poll, "Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹Ø§Øª", "ØªØµÙˆÙŠØª Ù…Ø¬ØªÙ…Ø¹ÙŠ Ù…Ø¹ Ù†ØªØ§Ø¦Ø¬ ÙÙˆØ±ÙŠØ© ÙˆÙ†ÙØ³ÙŽÙ… Ù…Ø¦ÙˆÙŠØ©", Color(0xFF65D7E7), enabled = true, click = onPolls)
        MoreOption(Icons.Default.NetworkCheck, "Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª PSTN / DINSTAR", "Ø¨ÙˆØ§Ø¨Ø© DINSTARØŒ Ø­Ø§Ù„Ø© Ø§Ù„Ø´Ø±Ø§Ø¦Ø­ØŒ SMSCØŒ Ø§Ø®ØªØ¨Ø§Ø± SIP Bridge", AqyalGold, click = onPstnConfig)
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

@Composable
private fun DinstarPhoneScreen(account: AuthState.Authenticated, viewModel: AuthViewModel, history: CallHistoryViewModel? = null, prefillNumber: String = "") {
    var tab by remember { mutableIntStateOf(0) }
    val smsVm: com.red.sovereign.features.sms.SmsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var inChat by remember { mutableStateOf(false) }
    // ðŸ“ž Ø£ÙƒØ«Ø± Ø§Ù„Ø£Ø±Ù‚Ø§Ù… Ø§Ù„ÙŠÙ…Ù†ÙŠØ© Ø§ØªØµØ§Ù„Ù‹Ø§ â€” ØªÙØ´ØªÙ‚ Ù…Ù† Ø³Ø¬Ù„ DINSTAR Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ (Ù„Ø§ Ø¨ÙŠØ§Ù†Ø§Øª ÙˆÙ‡Ù…ÙŠØ©)
    val dinstarCalls = history?.calls?.filter { it.route == "DINSTAR" }.orEmpty()
    val favorites = dinstarCalls.groupingBy { it.peerLabel.ifBlank { it.peerId } }.eachCount()
        .entries.sortedByDescending { it.value }.take(8).map { it.key }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(35.dp)); Column(Modifier.padding(start = 12.dp)) {
                    Text("Ø§Ù„Ù‡Ø§ØªÙ Ø§Ù„ÙŠÙ…Ù†ÙŠ Ø¹Ø¨Ø± DINSTAR", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Text(if (account.pstnEnabled) "Ù…ØµØ±Ø­ Ù„Ùƒ â€” Ù…ÙƒØ§Ù„Ù…Ø§Øª ØµÙˆØªÙŠØ© ÙÙ‚Ø·" else "ØºÙŠØ± Ù…ÙØ¹Ù„ â€” ÙŠÙØ¹Ù„Ù‡ Ø§Ù„Ù…Ø³Ø¤ÙˆÙ„ Ù…Ù† Ø§Ù„Ù„ÙˆØ­Ø©", fontSize = 12.sp)
                }
            }
        }
        PrimaryTabRow(tab) {
            listOf(
                Icons.Default.Dialpad to "Ø§Ù„Ø£Ø±Ù‚Ø§Ù…",
                Icons.AutoMirrored.Filled.Message to "Ø§Ù„Ø±Ø³Ø§Ø¦Ù„",
                Icons.Default.Star to "Ø§Ù„Ù…ÙØ¶Ù„Ø©",
                Icons.Default.History to "Ø§Ù„Ø³Ø¬Ù„",
                Icons.Default.Contacts to "Ø¬Ù‡Ø§Øª Ø§Ù„Ø§ØªØµØ§Ù„"
            ).forEachIndexed { i, item -> Tab(tab == i, { tab = i }, icon = { Icon(item.first, null) }, text = { Text(item.second, fontSize = 10.sp) }) }
        }
        when (tab) {
            0 -> DialPad(account.pstnEnabled, viewModel, prefillNumber)
            // ðŸ“¨ Ø§Ù„Ø±Ø³Ø§Ø¦Ù„ â€” SMS Ø§Ø­ØªØ±Ø§ÙÙŠ: Ù…Ø­Ø§Ø¯Ø«Ø§Øª + Ø¯Ø±Ø¯Ø´Ø© + Ø¥Ø±Ø³Ø§Ù„/Ø§Ø³ØªÙ‚Ø¨Ø§Ù„/ØªØ³Ù„ÙŠÙ…
            1 -> if (inChat && smsVm.chatNumber != null) {
                com.red.sovereign.features.sms.SmsChatScreen(smsVm, onBack = {
                    smsVm.closeChat(); inChat = false
                })
            } else {
                com.red.sovereign.features.sms.SmsConversationsScreen(smsVm, onOpenChat = {
                    smsVm.openChat(it); inChat = true
                })
            }
            // â­ Ø§Ù„Ù…ÙØ¶Ù„Ø© â€” Ø£ÙƒØ«Ø± Ø§Ù„Ø£Ø±Ù‚Ø§Ù… Ø§ØªØµØ§Ù„Ù‹Ø§ Ø¹Ø¨Ø± DINSTAR Ù…Ø¹ Ø¥Ø¹Ø§Ø¯Ø© Ø§ØªØµØ§Ù„ Ø¨Ù†Ù‚Ø±Ø©
            2 -> if (favorites.isEmpty()) {
                EmptyState(Icons.Default.Star, "Ù„Ø§ Ù…ÙØ¶Ù„Ø© Ø¨Ø¹Ø¯", "Ø³ØªØ¸Ù‡Ø± Ù‡Ù†Ø§ Ø£ÙƒØ«Ø± Ø§Ù„Ø£Ø±Ù‚Ø§Ù… Ø§Ù„ÙŠÙ…Ù†ÙŠØ© Ø§ØªØµØ§Ù„Ù‹Ø§ Ø¹Ø¨Ø± DINSTAR ØªÙ„Ù‚Ø§Ø¦ÙŠÙ‹Ø§")
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
                                IconButton(onClick = { if (account.pstnEnabled) { viewModel.clearPstnState(); viewModel.dialPstn(number) } }, enabled = account.pstnEnabled) {
                                    Icon(Icons.Default.Call, "Ø§ØªØµØ§Ù„", tint = if (account.pstnEnabled) YounesEmerald else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            // ðŸ—‚ï¸ Ø³Ø¬Ù„ DINSTAR Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ â€” Ù…ÙÙ„ØªØ± Ù…Ù† Ø§Ù„Ø³Ø¬Ù„ Ø§Ù„Ù…ÙˆØ­Ø¯
            3 -> if (dinstarCalls.isEmpty()) {
                EmptyState(Icons.Default.History, "Ù„Ø§ Ù…ÙƒØ§Ù„Ù…Ø§Øª DINSTAR Ø¨Ø¹Ø¯", "Ø³ØªØ¸Ù‡Ø± Ù‡Ù†Ø§ ÙƒÙ„ Ù…ÙƒØ§Ù„Ù…Ø§ØªÙƒ Ø§Ù„Ù‡Ø§ØªÙÙŠØ© Ø§Ù„ÙŠÙ…Ù†ÙŠØ©")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dinstarCalls.size) { i -> CallHistoryRow(dinstarCalls[i]) }
                }
            }
            else -> EmptyState(Icons.Default.Contacts, "Ø¬Ù‡Ø§Øª Ø§Ù„Ø§ØªØµØ§Ù„", "Ø§Ø®ØªØ± Ø¬Ù‡Ø© Ù…Ù† ØªØ¨ÙˆÙŠØ¨ Ø¬Ù‡Ø§Øª Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠ Ø«Ù… Ø§Ø·Ù„Ø¨Ù‡Ø§ Ø¹Ø¨Ø± DINSTAR")
        }
    }
}

@Composable
private fun DialPad(enabled: Boolean, viewModel: AuthViewModel, prefill: String = "") {
    var number by remember(prefill) { mutableStateOf(prefill) }
    // ðŸ“ž Ø£Ø«Ù†Ø§Ø¡ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ù†Ø´Ø·Ø© Ù†Ø³ØªØ¨Ø¯Ù„ Ø§Ù„Ù„ÙˆØ­Ø© Ø¨Ø´Ø§Ø´Ø© Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„ÙØ§Ø®Ø±Ø© ÙƒØ§Ù…Ù„Ø© Ø§Ù„ØªØ­ÙƒÙ…
    val pstnState = viewModel.pstnState
    // Ù…ÙƒØ§Ù„Ù…Ø© ÙˆØ§Ø±Ø¯Ø©: Ø´Ø§Ø´Ø© Ù‚Ø¨ÙˆÙ„/Ø±ÙØ¶ â€” ÙƒØ§Ù†Øª Ù…Ø¹Ø·Ù‘Ù„Ø© (Ù„Ø§ ØªÙˆØ¬Ø¯ ÙˆØ§Ø¬Ù‡Ø© ØªØ±Ø¨Ø·Ù‡Ø§)
    val incomingPstn = viewModel.incomingPstnCall
    if (incomingPstn != null) {
        com.red.sovereign.features.pstn.IncomingPstnCallScreen(
            number = incomingPstn.fromNumber,
            onAccept = { viewModel.acceptIncomingPstnCall() },
            onDecline = { viewModel.rejectIncomingPstnCall() }
        )
        return
    }
    val isInPstnCall = pstnState is PstnState.Started || pstnState is PstnState.Bridging || pstnState is PstnState.Registering || pstnState is PstnState.Ringing || pstnState is PstnState.Dialing
    if (isInPstnCall) {
        com.red.sovereign.features.pstn.PstnCallScreen(
            number = number,
            state = viewModel.pstnState,
            onHangup = { viewModel.hangupPstn() },
            onMuteToggle = { viewModel.togglePstnMute(it) },
            onSpeakerToggle = { viewModel.togglePstnSpeaker(it) },
            viewModel = viewModel
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number.ifEmpty { "Ø£Ø¯Ø®Ù„ Ø§Ù„Ø±Ù‚Ù…" }, fontSize = 27.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton({ if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "Ø­Ø°Ù") }
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
        Button({ viewModel.clearPstnState(); viewModel.dialPstn(number) }, enabled = enabled && number.filter(Char::isDigit).length >= 6, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Call, null); Text(" Ø§ØªØµØ§Ù„ ØµÙˆØªÙŠ Ø¹Ø¨Ø± DINSTAR") }
        when (val state = viewModel.pstnState) {
            PstnState.Dialing -> CircularProgressIndicator(color = AqyalGold)
            PstnState.Bridging -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = AqyalGold, modifier = Modifier.size(18.dp)); Text("Ø¬Ø§Ø±ÙŠ ØªØ¬Ù‡ÙŠØ² Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„Ø¢Ù…Ù†...", color = AqyalGold, fontSize = 13.sp)
            }
            PstnState.Registering -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = AqyalGold, modifier = Modifier.size(18.dp)); Text("Ø¬Ø§Ø±ÙŠ Ø§Ù„ØªØ³Ø¬ÙŠÙ„ ÙÙŠ Ø¨ÙˆØ§Ø¨Ø© Ø§Ù„ØµÙˆØª...", color = AqyalGold, fontSize = 13.sp)
            }
            PstnState.Ringing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = YounesEmerald, modifier = Modifier.size(18.dp)); Text("Ø¬Ø§Ø±ÙŠ Ø±Ù†ÙŠÙ† Ø§Ù„Ù‡Ø§ØªÙ...", color = YounesEmerald, fontSize = 13.sp)
            }
is PstnState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            PstnState.Idle -> Unit
            is PstnState.Started -> Unit // Ø¹ÙØ§Ù„Ø¬Øª Ø£Ø¹Ù„Ø§Ù‡ Ø¨Ø´Ø§Ø´Ø© Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„ÙƒØ§Ù…Ù„Ø©
            is PstnState.Incoming -> Unit // Ø¹ÙÙˆÙ„Ø¬Øª Ø£Ø¹Ù„Ø§Ù‡ Ø¨Ø´Ø§Ø´Ø© Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„ÙˆØ§Ø±Ø¯Ø©
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(
    publishing: Boolean,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
    onPoll: (String, List<String>, Int) -> Unit,
    onStory: () -> Unit,
    onLive: () -> Unit,
    onExplore: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "", "")) }
    var pollHours by remember { mutableIntStateOf(24) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ø¥Ù†Ø´Ø§Ø¡ ÙÙŠ ÙŠÙˆÙ†Ø³", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (mode != "menu") TextButton({ mode = "menu" }) { Text("Ø§Ù„Ø®ÙŠØ§Ø±Ø§Øª") }
            }
            when (mode) {
                "post" -> {
                    OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth().height(150.dp), placeholder = { Text("Ø§ÙƒØªØ¨ Ù…Ù†Ø´ÙˆØ±Ø§Ù‹ØŒ Ø³Ù„Ø³Ù„Ø©ØŒ ÙÙƒØ±Ø© Ø·ÙˆÙŠÙ„Ø©ØŒ Ø£Ùˆ Ø¥Ø¹Ù„Ø§Ù†Ø§Ù‹ Ù…Ø­Ù„ÙŠØ§Ù‹â€¦") }, maxLines = 7)
                    Text("${text.length}/2000", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Button({ if (text.isNotBlank()) onPost(text.trim()) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !publishing) { if (publishing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Ù†Ø´Ø± Ù…Ø­Ù„ÙŠ") }
                }
                "poll" -> {
                    OutlinedTextField(pollQuestion, { pollQuestion = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("Ø³Ø¤Ø§Ù„ Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹") }, maxLines = 3)
                    pollOptions.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { next -> pollOptions = pollOptions.toMutableList().also { it[index] = next.take(80) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ø§Ù„Ø®ÙŠØ§Ø± ${index + 1}") },
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Ø³Ø§Ø¹Ø©", 24 to "ÙŠÙˆÙ…", 72 to "3 Ø£ÙŠØ§Ù…", 168 to "Ø£Ø³Ø¨ÙˆØ¹").forEach { option ->
                            FilterChip(selected = pollHours == option.first, onClick = { pollHours = option.first }, label = { Text(option.second) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (pollOptions.size < 6) pollOptions = pollOptions + "" }, Modifier.weight(1f), enabled = pollOptions.size < 6) { Text("Ø¥Ø¶Ø§ÙØ© Ø®ÙŠØ§Ø±") }
                        OutlinedButton({ if (pollOptions.size > 2) pollOptions = pollOptions.dropLast(1) }, Modifier.weight(1f), enabled = pollOptions.size > 2) { Text("Ø­Ø°Ù Ø®ÙŠØ§Ø±") }
                    }
                    val validPoll = pollQuestion.isNotBlank() && pollOptions.count { it.trim().length >= 2 } >= 2
                    Button({ onPoll(pollQuestion, pollOptions, pollHours) }, Modifier.fillMaxWidth(), enabled = validPoll && !publishing) { if (publishing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Ù†Ø´Ø± Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹") }
                }
                else -> {
                    CreateOption(Icons.Default.DynamicFeed, "Ù…Ù†Ø´ÙˆØ± Ø£Ùˆ Ø³Ù„Ø³Ù„Ø©", "Ù†Øµ Ø·ÙˆÙŠÙ„ØŒ Ø§Ù‚ØªØ¨Ø§Ø³ØŒ Ù†Ù‚Ø§Ø´ Ù…Ø­Ù„ÙŠ", true) { mode = "post" }
                    CreateOption(Icons.Default.Forum, "Ø§Ø³ØªØ·Ù„Ø§Ø¹ ØªÙØ§Ø¹Ù„ÙŠ", "Ø³Ø¤Ø§Ù„ ÙˆØ®ÙŠØ§Ø±Ø§Øª ÙˆØªØµÙˆÙŠØª ÙØ¹Ù„ÙŠ Ø¹Ø¨Ø± Ø§Ù„Ø®Ø§Ø¯Ù…", true) { mode = "poll" }
                    CreateOption(Icons.Default.AddCircle, "Ø­Ø§Ù„Ø© 24 Ø³Ø§Ø¹Ø©", "ØµÙˆØ±Ø© Ø£Ùˆ ÙÙŠØ¯ÙŠÙˆ ÙŠÙØ­Ø°Ù ØªÙ„Ù‚Ø§Ø¦ÙŠØ§Ù‹", true, onStory)
                    CreateOption(Icons.Default.LiveTv, "Ø¨Ø« Ù…Ø¨Ø§Ø´Ø±", "ÙÙŠØ¯ÙŠÙˆ Ø¹Ø¨Ø± SFU Ø§Ù„Ù…Ø­Ù„ÙŠ", true, onLive)
                    CreateOption(Icons.Default.Explore, "Ø§Ø³ØªÙƒØ´Ø§Ù ÙŠÙˆÙ†Ø³", "Ø§ÙƒØªØ´Ù Ø§Ù„Ø¨Ø«ÙˆØ« ÙˆØ§Ù„ØºØ±Ù Ø§Ù„ØµÙˆØªÙŠØ© Ø§Ù„Ù†Ø´Ø·Ø©", true, onExplore)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun CreateOption(icon: ImageVector, title: String, detail: String, enabled: Boolean, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = if (enabled) AqyalGold else Color.Gray, modifier = Modifier.size(31.dp)); Column(Modifier.padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color.Unspecified else Color.Gray); Text(detail, color = Color.Gray, fontSize = 12.sp) } } }

@Composable internal fun EmptyState(icon: ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }
/**
 * Ù…Ø®Ø²Ù† ØªØµÙˆÙŠØªØ§Øª Ø§Ø³ØªØ·Ù„Ø§Ø¹Ø§Øª Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© (E2EE): pollId -> (Ù…ØµÙˆØª -> ÙÙ‡Ø±Ø³ Ø§Ù„Ø®ÙŠØ§Ø±).
 * ØªÙØ­Ø¯ÙŽÙ‘Ø« Ù…Ù† Ø±Ø³Ø§Ø¦Ù„ POLL_VOTE Ø§Ù„ÙˆØ§Ø±Ø¯Ø©ØŒ ÙˆØªÙÙ‚Ø±Ø£Ù‡Ø§ Ø¨Ø·Ø§Ù‚Ø§Øª Ø§Ù„Ø§Ø³ØªØ·Ù„Ø§Ø¹.
 * ØªÙØ®Ø²ÙŽÙ‘Ù† Ø§Ù„Ù‚ÙŠÙ… ÙƒØ®Ø±ÙŠØ·Ø© Ø«Ø§Ø¨ØªØ© Ø¯Ø§Ø®Ù„ Ø®Ø±ÙŠØ·Ø© Ù…Ù„Ø§Ø­Ø¸Ø© Ù„Ø¶Ù…Ø§Ù† Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªÙˆÙ„ÙŠÙ Ø¹Ù†Ø¯ Ø£ÙŠ ØµÙˆØª.
 */
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

/** Ù†Øµ Ø¨Ø­Ø«ÙŠ Ù†Ø¸ÙŠÙ Ù„Ù„Ø³Ø¬Ù„ Ø§Ù„Ù…Ø­Ù„ÙŠ: ÙŠØ³ØªØ¨Ø¹Ø¯ Ø¥Ø¯Ø®Ø§Ù„Ø§Øª Ø§Ù„Ù†Ø¸Ø§Ù… ÙˆÙŠÙÙƒ Ø´ÙŠÙØ±Ø© Ø£Ø³Ù…Ø§Ø¡ Ø§Ù„ÙˆØ³Ø§Ø¦Ø·. */
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
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(text) }.getOrNull()?.let { if (it.emoji.isNotBlank()) it.emoji else "Ù…Ù„ØµÙ‚" }
                ?: text
        else -> text
    }
}

private fun messageDisplayText(message: DecryptedMessage): String =
    when (message.type) {
        "RICH_TEXT" -> RichMessage.decode(message.plaintext)?.text.orEmpty()
        "GROUP_MESSAGE", "FILE", "IMAGE", "VIDEO", "AUDIO", "VOICE", "STICKER" -> {
            val text = message.plaintext.toString(Charsets.UTF_8)
            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.AttachmentManifest>(text) }.getOrNull()?.let { "ðŸ“Ž ${it.name}" }
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.VoiceManifest>(text) }.getOrNull()?.let { "ðŸŽ¤ ${it.name}" }
                ?: runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(text) }.getOrNull()?.let { "ðŸ–¼ï¸ ${if (it.emoji.isNotBlank()) it.emoji else "Ù…Ù„ØµÙ‚"}" }
                ?: text
        }
        else -> message.plaintext.toString(Charsets.UTF_8)
    }

@Composable
private fun RichTextMessage(
    message: DecryptedMessage,
    conversation: List<DecryptedMessage>,
    myRedId: String? = null,
    onPollVote: ((String, Int?) -> Unit)? = null
) {
    val rich = RichMessage.decode(message.plaintext)
    if (rich == null) { Text("Ø±Ø³Ø§Ù„Ø© ØºÙŠØ± ØµØ§Ù„Ø­Ø©", color = MaterialTheme.colorScheme.error); return }
    rich.replyTo?.let { replyId -> conversation.firstOrNull { it.id == replyId }?.let { quoted -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f))) { Text(messageDisplayText(quoted), Modifier.padding(7.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall) } } }
    if (rich.forwardOf != null) Text("Ù…Ø¹Ø§Ø¯ ØªÙˆØ¬ÙŠÙ‡Ù‡Ø§", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (rich.action == "CALL_STARTED") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            val isVid = rich.text.contains("ÙÙŠØ¯ÙŠÙˆ")
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (message.outgoing) Color(0x33000000) else YounesEmerald.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (isVid) Icons.Default.Videocam else Icons.Default.Headset, null, tint = if (message.outgoing) Color(0xFF003023) else YounesEmerald, modifier = Modifier.size(14.dp))
            }
            Text("Ù…ÙƒØ§Ù„Ù…Ø© Ù†Ø´Ø·Ø©", style = MaterialTheme.typography.labelSmall, color = if (message.outgoing) Color(0xFF003023) else YounesEmerald, fontWeight = FontWeight.Bold)
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
            remaining <= 0 -> "Ø§Ù†ØªÙ‡Øª"
            remaining < 3600000 -> "${remaining/60000}Ø¯"
            remaining < 86400000 -> "${remaining/3600000}Ø³"
            else -> "${remaining/86400000}ÙŠ"
        }
        Text("â³ Ù…Ø¤Ù‚ØªØ© â€¢ $label", style = MaterialTheme.typography.labelSmall, color = AqyalGold)
    }
    if (rich.mentions.isNotEmpty()) Text("Ø°ÙƒØ±: ${rich.mentions.joinToString()}", style = MaterialTheme.typography.labelSmall, color = YounesEmerald)
}

@Composable
private fun InlinePollCard(
    poll: com.red.sovereign.core.InlinePoll,
    isOutgoing: Boolean,
    myRedId: String? = null,
    onVote: ((String, Int?) -> Unit)? = null
) {
    // ØªØµÙˆÙŠØªØ§Øª Ù…ØªØ²Ø§Ù…Ù†Ø© E2EE (Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø§Øª)Ø› ÙˆØ¥Ù„Ø§ ÙŠØ¹Ø±Ø¶ Ø§Ù„Ø¨Ø·Ø§Ù‚Ø© Ù…Ø­Ù„ÙŠØ§Ù‹ ÙÙ‚Ø·
    val synced = myRedId != null && onVote != null
    val votes = if (synced) PollVoteStore.counts(poll.pollId, poll.options.size) else poll.votes
    val myVote = if (synced) myRedId?.let { PollVoteStore.myVote(poll.pollId, it) } else null
    val total = votes.sum().coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, null, tint = YounesEmerald, modifier = Modifier.size(18.dp))
                Text(" Ø§Ø³ØªØ·Ù„Ø§Ø¹ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©", style = MaterialTheme.typography.labelMedium, color = YounesEmerald, fontWeight = FontWeight.Bold)
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
                if (synced) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„Ø£ØµÙˆØ§Øª: $total Â· ØµÙˆØªÙƒ: ${myVote?.let { poll.options.getOrNull(it) } ?: "Ù„Ø§ Ø´ÙŠØ¡"}"
                else "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„Ø£ØµÙˆØ§Øª: $total",
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
        // âºï¸ Ø´Ø±ÙŠØ· Ø§Ù„ØªØ³Ø¬ÙŠÙ„ Ø§Ù„Ø¹Ù„ÙˆÙŠ
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (voiceState.paused) AqyalGold else MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(6.dp))
            Text(
                if (voiceState.paused) "Ù…ØªÙˆÙ‚Ù Ù…Ø¤Ù‚ØªÙ‹Ø§ ${formatDuration(voiceMessages.elapsedSeconds)}"
                else "â— ØªØ³Ø¬ÙŠÙ„ ${formatDuration(voiceMessages.elapsedSeconds)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(voiceMessages::togglePause) {
                Icon(if (voiceState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (voiceState.paused) "Ø§Ø³ØªØ¦Ù†Ø§Ù" else "Ø¥ÙŠÙ‚Ø§Ù Ù…Ø¤Ù‚Øª")
            }
            TextButton(voiceMessages::cancel) { Text("Ø¥Ù„ØºØ§Ø¡") }
        }
        VoiceWaveform(voiceMessages.waveform, MaterialTheme.colorScheme.error, Modifier.fillMaxWidth().height(34.dp))

        // ðŸŽšï¸ Ù…Ù†Ø·Ù‚Ø© Ø§Ù„Ø³Ø­Ø¨ â€” Ø¥Ø°Ø§ Ø§Ù„Ø³Ø­Ø¨ Ù„Ù„ÙŠØ³Ø§Ø±/Ø§Ù„Ø£Ø³ÙÙ„ = Ø¥Ù„ØºØ§Ø¡ ØªØ¯Ø±ÙŠØ¬ÙŠ
        if (cancelProgress > 0f) {
            Text(
                "â†©ï¸ Ø§Ø³Ø­Ø¨ Ù„Ù…Ø¹Ø§ÙˆØ¯Ø© Ø§Ù„ØªØ³Ø¬ÙŠÙ„ â€¢ ${(cancelProgress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // ðŸ”’ Ø¥Ø°Ø§ Ù‚ÙÙÙ„ Ø§Ù„ØªØ³Ø¬ÙŠÙ„ØŒ Ø§Ø¹Ø±Ø¶ Ø£Ø²Ø±Ø§Ø± Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ ÙˆØ§Ù„Ø¥Ù„ØºØ§Ø¡
        if (isLocked) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = voiceMessages::cancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Ø­Ø°Ù") }
                // Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ ÙŠØªÙ… Ø¹Ø¨Ø± Ø²Ø± Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠ ÙÙŠ Ø´Ø±ÙŠØ· Ø§Ù„ÙƒØªØ§Ø¨Ø©
                OutlinedButton(
                    onClick = { /* triggered via main send button */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) { Text("ðŸ”’ Ù…ÙÙ‚ÙÙ„ â€” Ø§Ø³ØªØ®Ø¯Ù… Ø²Ø± Ø§Ù„Ø¥Ø±Ø³Ø§Ù„") }
            }
        } else {
            // ðŸ”“ Ù†ØµÙŠØ­Ø© Ù„Ù„Ù…Ø³ØªØ®Ø¯Ù…: Ø§Ø³Ø­Ø¨ Ù„Ù„Ù‚ÙÙ„ Ø£Ùˆ Ø§Ø±ÙØ¹ Ø§Ù„Ø¥ØµØ¨Ø¹ Ù„Ù„Ø¥Ø±Ø³Ø§Ù„
            Text(
                "ðŸ’¡ Ø§Ø³Ø­Ø¨ Ù„Ù„Ø£Ø¹Ù„Ù‰ Ù„Ù„Ù‚ÙÙ„ â€¢ Ø§Ø±ÙØ¹ Ø§Ù„Ø¥ØµØ¨Ø¹ Ù„Ù„Ø¥Ø±Ø³Ø§Ù„ â€¢ Ø§Ø³Ø­Ø¨ Ù„Ù„Ø£Ø³ÙÙ„ Ù„Ù„Ø¥Ù„ØºØ§Ø¡",
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
                "Ù…Ø¹Ø§ÙŠÙ†Ø© Ø§Ù„Ø±Ø³Ø§Ù„Ø© Ø§Ù„ØµÙˆØªÙŠØ© â€¢ ${formatDuration(duration)}",
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
                Icon(Icons.Default.Close, null); Text(" Ø­Ø°Ù")
            }
            Button(
                onClick = onSend,
                modifier = Modifier.weight(1f),
                enabled = !isSending && duration >= 1
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else { Icon(Icons.Default.Send, null); Text(" Ø¥Ø±Ø³Ø§Ù„") }
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

/** Ø¹Ø±Ø¶ Ø±Ø³Ø§Ù„Ø© Ù…Ù„ØµÙ‚ â€” Ø¥ÙŠÙ…ÙˆØ¬ÙŠ ÙƒØ¨ÙŠØ± ÙƒÙ…Ø¹Ø§ÙŠÙ†Ø© (Ø§Ù„ØµÙˆØ±Ø© Ø§Ù„ÙØ¹Ù„ÙŠØ© ØªÙØ­Ù…Ù‘Ù„ Ø¹Ù†Ø¯ Ø§Ù„ØªÙˆÙØ±). */
@Composable
private fun StickerMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val payload = remember(item.id) {
        runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(item.plaintext.toString(Charsets.UTF_8)) }.getOrNull()
    }
    if (payload == null) {
        Text("Ù…Ù„ØµÙ‚ ØºÙŠØ± ØµØ§Ù„Ø­", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        return
    }
    // ØªØ­Ù…ÙŠÙ„ ØµÙˆØ±Ø© Ø§Ù„Ù…Ù„ØµÙ‚ Ø§Ù„ÙØ¹Ù„ÙŠØ© Ø¹Ø¨Ø± MediaApi (Ø§Ù„Ù…Ù„ØµÙ‚Ø§Øª Ø³ÙŠØ§Ø¯ÙŠØ© ØºÙŠØ± Ù…Ø´ÙÙ‘Ø±Ø© E2EE)
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
            contentDescription = payload.name ?: "Ù…Ù„ØµÙ‚",
            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(14.dp))
        )
    } else {
        // Ù…Ø¹Ø§ÙŠÙ†Ø© Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ Ø£Ø«Ù†Ø§Ø¡ Ø§Ù„ØªØ­Ù…ÙŠÙ„ Ø£Ùˆ Ø¹Ù†Ø¯ Ø§Ù„ØªØ¹Ø°Ø±
        Text(payload.emoji, fontSize = 64.sp)
    }
}

@Composable
private fun VoiceMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<VoiceManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("Ø±Ø³Ø§Ù„Ø© ØµÙˆØªÙŠØ© ØºÙŠØ± ØµØ§Ù„Ø­Ø©", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.downloadVoice(item.id, manifestJson)
    }
    val downloadState = attachments.getDownloadState(item.id)
    val downloadedPath = when (downloadState) {
        is AttachmentState.Downloaded -> downloadState.path
        is AttachmentState.Exported -> downloadState.path
        else -> null
    }
    val downloadedFile = downloadedPath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val isDownloaded = downloadedFile != null
    val isDownloading = downloadState is AttachmentState.Working
    val downloadedUri = downloadedFile?.let { android.net.Uri.fromFile(it) }

    if (downloadedUri != null) {
        // ðŸŽ™ï¸ Ù…Ø´ØºÙ‘Ù„ Ø§Ø­ØªØ±Ø§ÙÙŠ Ù…Ø¹ waveform
        VoiceNotePlayer(
            uri = downloadedUri,
            waveform = manifest.waveform,
            durationSeconds = manifest.durationSeconds,
            isOutgoing = item.outgoing,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // ðŸ’¬ ÙÙ‚Ø§Ø¹Ø© Ø§Ø­ØªØ±Ø§ÙÙŠØ© Ù‚Ø¨Ù„ Ø§Ù„ØªÙ†Ø²ÙŠÙ„
        VoiceBubble(
            manifest = manifest,
            isOutgoing = item.outgoing,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            onPlayPause = { attachments.downloadVoice(item.id, manifestJson) },
            onSeek = { /* no-op before download */ },
            onSpeedChange = { /* no-op before download */ },
            onDownload = { attachments.downloadVoice(item.id, manifestJson) },
            onWaveformTap = { attachments.downloadVoice(item.id, manifestJson) }
        )
    }
}

@Composable
private fun AttachmentMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("Ù…Ø±ÙÙ‚ Ù…Ø´ÙØ± ØºÙŠØ± ØµØ§Ù„Ø­", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    when {
        manifest.mimeType.startsWith("image/") -> ImageMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("video/") -> VideoMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("audio/") -> AudioMessage(item, manifest, attachments)
        else -> FileMessage(item, manifest, attachments)
    }
}

@Composable
private fun ImageMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    val downloadState = attachments.getDownloadState(item.id)
    val downloadedPath = when (downloadState) {
        is AttachmentState.Downloaded -> downloadState.path
        is AttachmentState.Exported -> downloadState.path
        else -> null
    }
    val downloadedFile = downloadedPath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val isWorking = downloadState is AttachmentState.Working

    if (downloadedFile != null) {
        val bitmap = remember(downloadedFile.lastModified()) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
            android.graphics.BitmapFactory.decodeFile(downloadedFile.absolutePath, opts)?.asImageBitmap()
        }
        if (bitmap != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.foundation.Image(
                    bitmap, contentDescription = "ØµÙˆØ±Ø©",
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        val uri = android.net.Uri.fromFile(downloadedFile)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    contentScale = ContentScale.Crop
                )
                // Ø´Ø§Ø±Ø© Ø§Ù„Ø­Ø¬Ù… ÙˆØ§Ù„ØªØ­Ù‚Ù‚ Ø§Ù„Ù…Ø´ÙØ±
                Surface(Modifier.padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Text(" âœ“ Ù…Ø´ÙØ±Ø© â€¢ ${formatBytes(manifest.size)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        } else {
            Text("ØµÙˆØ±Ø© Ù…Ø´ÙØ±Ø© (${manifest.name})", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (isWorking) {
                    CircularProgressIndicator(color = YounesEmerald, strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Ø¬Ø§Ø±Ù ÙÙƒ Ø§Ù„ØªØ´ÙÙŠØ±â€¦", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(manifest.name.take(24), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    Text("${formatBytes(manifest.size)} â€¢ Ù…Ø´ÙØ±Ø©", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    IconButton({ attachments.download(item.id, manifestJson) }, enabled = !isWorking) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = YounesEmerald) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, "ØªÙ†Ø²ÙŠÙ„", tint = Color(0xFF002118)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    val downloadState = attachments.getDownloadState(item.id)
    val downloadedPath = when (downloadState) {
        is AttachmentState.Downloaded -> downloadState.path
        is AttachmentState.Exported -> downloadState.path
        else -> null
    }
    val downloadedFile = downloadedPath?.let { java.io.File(it) }?.takeIf { it.exists() }

    if (downloadedFile != null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                StoryVideoPlayer(android.net.Uri.fromFile(downloadedFile), Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(52.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    onClick = {
                        val uri = android.net.Uri.fromFile(downloadedFile)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                }
            }
        }
    } else {
        val isWorking = downloadState is AttachmentState.Working
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(YounesEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = YounesEmerald, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("ÙÙŠØ¯ÙŠÙˆ Ù…Ø´ÙØ± Â· ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
                else IconButton({ attachments.download(item.id, manifestJson) }, enabled = !isWorking) {
                    Icon(Icons.Default.Download, "ØªÙ†Ø²ÙŠÙ„ Ø§Ù„ÙÙŠØ¯ÙŠÙˆ", tint = YounesEmerald)
                }
            }
        }
    }
}

@Composable
private fun AudioMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    val downloadState = attachments.getDownloadState(item.id)
    val downloadedPath = when (downloadState) {
        is AttachmentState.Downloaded -> downloadState.path
        is AttachmentState.Exported -> downloadState.path
        else -> null
    }
    val downloadedFile = downloadedPath?.let { java.io.File(it) }?.takeIf { it.exists() }

    if (downloadedFile != null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(AqyalCyanGlow.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(20.dp))
                    }
                    Text(manifest.name, Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("âœ“ Ù…Ø´ÙØ±Ø©", color = YounesEmerald, fontSize = 10.sp)
                }
                VoiceNotePlayer(android.net.Uri.fromFile(downloadedFile), isOutgoing = item.outgoing, modifier = Modifier.fillMaxWidth())
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
                    Text("ØµÙˆØª Ù…Ø´ÙØ± Â· ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ attachments.download(item.id, manifestJson) }, enabled = attachments.sendState !is AttachmentState.Working) {
                    Icon(Icons.Default.Download, "ØªÙ†Ø²ÙŠÙ„ Ø§Ù„ØµÙˆØª")
                }
            }
        }
    }
}

@Composable
private fun FileMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    val downloadState = attachments.getDownloadState(item.id)
    val downloadedPath = when (downloadState) {
        is AttachmentState.Downloaded -> downloadState.path
        is AttachmentState.Exported -> downloadState.path
        else -> null
    }
    val downloadedFile = downloadedPath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val isWorking = downloadState is AttachmentState.Working
    val fileColor = when {
        manifest.mimeType.contains("pdf") -> AqyalCyanGlow
        manifest.mimeType.contains("zip") || manifest.mimeType.contains("compressed") -> AqyalGold
        manifest.mimeType.contains("text") || manifest.mimeType.contains("word") -> Color(0xFF4FC3F7)
        manifest.mimeType.contains("sheet") || manifest.mimeType.contains("excel") -> YounesEmerald
        manifest.mimeType.contains("presentation") || manifest.mimeType.contains("powerpoint") -> Color(0xFFF06292)
        else -> AqyalCyanGlow
    }
    Card(
        Modifier.fillMaxWidth().clickable(enabled = downloadedFile != null) {
            downloadedFile?.let { file ->
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, manifest.mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            }
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(fileColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = fileColor, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${manifest.mimeType} Â· ${formatBytes(manifest.size)}${if (downloadedFile != null) " Â· Ø¬Ø§Ù‡Ø² Ù„Ù„ÙØªØ­" else ""}", style = MaterialTheme.typography.labelSmall)
            }
            if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
            else if (downloadedFile == null) {
                IconButton({ attachments.download(item.id, manifestJson) }) {
                    Icon(Icons.Default.Download, "ØªÙ†Ø²ÙŠÙ„ ÙˆÙÙƒ ØªØ´ÙÙŠØ± Ø§Ù„Ù…Ø±ÙÙ‚", tint = YounesEmerald)
                }
            } else {
                Icon(Icons.Default.Check, "ØªÙ… Ø§Ù„ØªÙ†Ø²ÙŠÙ„", tint = YounesEmerald, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun shouldAutoDownload(context: android.content.Context, sizeBytes: Long): Boolean =
    RedQualityManager.shouldAutoDownload(context, sizeBytes)

private fun groupRoleLabel(role: String) = when (role) { "OWNER" -> "Ø§Ù„Ù…Ø§Ù„Ùƒ"; "ADMIN" -> "Ù…Ø³Ø¤ÙˆÙ„"; else -> "Ø¹Ø¶Ùˆ" }

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
        isSameDay(timestamp, now) -> "Ø§Ù„ÙŠÙˆÙ…"
        isSameDay(timestamp, now - 86400000L) -> "Ø£Ù…Ø³"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60000
    return when {
        diff < 60000 -> "Ø§Ù„Ø¢Ù†"
        diff < 3600000 -> "${min}Ø¯"
        diff < 86400000 -> "${diff / 3600000}Ø³"
        diff < 172800000 -> "Ø£Ù…Ø³"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

/** ÙˆÙ‚Øª Ø§Ù„Ø³Ø§Ø¹Ø© Ø¯Ø§Ø®Ù„ Ø§Ù„ÙÙ‚Ø§Ø¹Ø© (Ù…Ø«Ù„ ÙˆØ§ØªØ³Ø§Ø¨: 4:20 Ù… / 11:05 Øµ). */
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

@Composable
private fun MessageInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Ø¹Ø±Ø¶ ØªÙØ§Ø¹Ù„Ø§Øª Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ ØªØ­Øª Ø±Ø³Ø§Ù„Ø© (chips Ù…Ø¹ Ø§Ù„Ø¹Ø¯). Ø§Ù„Ø¶ØºØ· Ø¹Ù„Ù‰ Ø¥ÙŠÙ…ÙˆØ¬ÙŠ = toggle
 * (Ø¥Ø²Ø§Ù„Ø© Ø¥Ù† ÙƒØ§Ù† ØªÙØ§Ø¹Ù„ÙƒØŒ Ù„Ø§ Ø´ÙŠØ¡ Ø¥Ù† Ù„Ù… ÙŠÙƒÙ†). E2EE: Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ Ù…Ø­Ù„ÙŠ ÙÙ‚Ø·.
 */
@Composable
private fun MessageReactions(
    reactions: List<MessageReactionEntity>,
    currentRedId: String,
    onToggle: (emoji: String) -> Unit
) {
    if (reactions.isEmpty()) return
    // ØªØ¬Ù…ÙŠØ¹ Ø­Ø³Ø¨ Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ Ù…Ø¹ Ø§Ù„Ø¹Ø¯ØŒ Ù…Ø±ØªØ¨ ØªÙ†Ø§Ø²Ù„ÙŠØ§Ù‹ Ø­Ø³Ø¨ Ø§Ù„Ø¹Ø¯
    val grouped = remember(reactions) {
        reactions.groupBy { it.emoji }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }
    }
    val myEmoji = remember(reactions, currentRedId) {
        reactions.firstOrNull { it.senderId == currentRedId }?.emoji
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items(grouped.entries.toList(), key = { it.key }) { (emoji, count) ->
            val mine = emoji == myEmoji
            Surface(
                shape = RoundedCornerShape(50),
                color = if (mine) YounesEmerald.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) YounesEmerald else androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable { onToggle(emoji) }
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(emoji, fontSize = 14.sp)
                    Text(count.toString(), fontSize = 11.sp, color = if (mine) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Ù‚Ø§Ø¦Ù…Ø© Ø§Ù„Ø¥ÙŠÙ…ÙˆØ¬ÙŠ Ø§Ù„Ø³Ø±ÙŠØ¹Ø© Ù„Ù„ØªÙØ§Ø¹Ù„ â€” ØªØ¸Ù‡Ø± Ø£Ø¹Ù„Ù‰ Ù‚Ø§Ø¦Ù…Ø© Ø¥Ø¬Ø±Ø§Ø¡Ø§Øª Ø§Ù„Ø±Ø³Ø§Ù„Ø©. */
@Composable
private fun ReactionEmojiBar(onPick: (String) -> Unit) {
    val quick = remember { listOf("ðŸ‘", "â¤ï¸", "ðŸ˜‚", "ðŸ™", "ðŸ”¥", "ðŸ‘", "ðŸ˜®", "ðŸ˜¢", "ðŸŽ‰", "ðŸ’¯") }
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

// Ù…ØµØ¯Ø± Ø§Ù„Ø­Ù‚ÙŠÙ‚Ø© Ø§Ù„ÙˆØ­ÙŠØ¯: core/YounesId.kt. Ø§Ù„Ù†Ù…Ø· ÙƒØ§Ù† Ù…ÙƒØ±Ù‘Ø±Ù‹Ø§ Ù‡Ù†Ø§ ÙˆÙÙŠ
// QrScannerSheet ÙˆSafetyViewModel Ø¨ØµÙŠØ§ØºØ§Øª Ù…ØªØ¨Ø§ÙŠÙ†Ø©ØŒ ÙÙƒØ§Ù† Ù…Ø¹Ø±Ù‘Ù ÙŠÙ‚Ø¨Ù„Ù‡
// Ø£Ø­Ø¯Ù‡Ø§ ÙˆØªØ±ÙØ¶Ù‡ Ø§Ù„Ø´Ø§Ø´Ø© Ø§Ù„ØªØ§Ù„ÙŠØ©.
private val RED_ID_PATTERN = Regex(YounesId.PATTERN)
// Ù†Ø³Ø®Ø© Ø¨Ø¯ÙˆÙ† ^ Ùˆ $ Ù„Ø§Ø³ØªØ®Ø¯Ø§Ù…Ù‡Ø§ Ø¯Ø§Ø®Ù„ Ù†Øµ (Ù…Ø«Ù„ @12345)
internal val RED_ID_PARTIAL = Regex(YounesId.MENTION_PATTERN)
// Ø§Ù„Ù‡Ø§Ø´ØªØ§Ø¬Ø§Øª Ø§Ù„Ø¹Ø±Ø¨ÙŠØ©/Ø§Ù„Ù„Ø§ØªÙŠÙ†ÙŠØ©
// Ø§Ù„Ù‡Ø§Ø´ØªØ§Ø¬ Ù„Ù€ # autocomplete
internal val EMOJI_CATEGORIES = listOf(
    "Ø³Ø±ÙŠØ¹Ø©" to listOf("ðŸ˜€", "ðŸ˜‚", "ðŸ˜", "ðŸ‘", "â¤ï¸", "ðŸ”¥", "ðŸ‘", "ðŸ™", "ðŸŽ‰", "ðŸ˜¢", "ðŸ˜®", "âœ…"),
    "Ø§Ù„ÙˆØ¬ÙˆÙ‡" to listOf("ðŸ˜€", "ðŸ˜ƒ", "ðŸ˜„", "ðŸ˜", "ðŸ˜†", "ðŸ˜…", "ðŸ˜‚", "ðŸ™‚", "ðŸ™ƒ", "ðŸ˜‰", "ðŸ˜Š", "ðŸ¥°", "ðŸ˜", "ðŸ¤©", "ðŸ˜˜", "ðŸ˜‹", "ðŸ˜Ž", "ðŸ¤”", "ðŸ˜´", "ðŸ˜­", "ðŸ˜¡", "ðŸ¥³"),
    "Ø§Ù„Ø¥Ø´Ø§Ø±Ø§Øª" to listOf("ðŸ‘", "ðŸ‘Ž", "ðŸ‘Œ", "âœŒï¸", "ðŸ¤ž", "ðŸ¤Ÿ", "ðŸ¤˜", "ðŸ‘", "ðŸ™Œ", "ðŸ«¶", "ðŸ¤", "ðŸ™", "ðŸ’ª", "ðŸ‘€", "â¤ï¸", "ðŸ’š", "ðŸ’›", "ðŸ’™"),
    "Ø§Ù„Ø£Ø´ÙŠØ§Ø¡" to listOf("ðŸ“±", "ðŸ’»", "âŒš", "ðŸ“·", "ðŸŽ¥", "ðŸŽ™ï¸", "ðŸ”’", "ðŸ”‘", "ðŸ’¡", "ðŸ“Œ", "ðŸ“Ž", "ðŸ“", "ðŸ“„", "ðŸ“š", "ðŸŽ", "ðŸ†", "âœ…", "âš ï¸"),
    "Ø§Ù„Ø·Ø¨ÙŠØ¹Ø©" to listOf("ðŸŒ™", "â˜€ï¸", "â­", "ðŸ”¥", "ðŸŒˆ", "ðŸŒ¹", "ðŸŒ¿", "ðŸŒ³", "ðŸŒŠ", "â›°ï¸", "ðŸª", "ðŸ¦…", "ðŸ", "ðŸ¦‹"),
    "Ø§Ù„Ø·Ø¹Ø§Ù…" to listOf("â˜•", "ðŸµ", "ðŸ¥¤", "ðŸž", "ðŸ¥", "ðŸš", "ðŸ—", "ðŸ¥—", "ðŸŽ", "ðŸ‰", "ðŸ‡", "ðŸ¯", "ðŸŽ‚"),
    "Ø§Ù„Ø³ÙØ±" to listOf("ðŸš—", "ðŸš•", "ðŸšŒ", "âœˆï¸", "ðŸš", "ðŸš¢", "ðŸ—ºï¸", "ðŸ ", "ðŸ¢", "ðŸ¥", "ðŸ«", "ðŸ•Œ", "â›º"),
    "Ø§Ù„Ø±Ù…ÙˆØ²" to listOf("âœ…", "âŒ", "âš ï¸", "â—", "â“", "ðŸ’¯", "âž•", "âž–", "â™»ï¸", "ðŸ”´", "ðŸŸ¢", "ðŸŸ¡", "ðŸ”µ", "ðŸ‡¾ðŸ‡ª")
)
private val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }

internal fun conversationId(first: String, second: String): String {
    if (first.isBlank() || second.isBlank()) return "pending-conversation"
    val canonical = listOf(first, second).sorted().joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}

@Composable
private fun TabButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AqyalGold else Color.Transparent,
            contentColor = if (selected) Color.Black else AqyalGold
        ),
        shape = RoundedCornerShape(12.dp)
    ) { content() }
}