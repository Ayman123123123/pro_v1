package com.red.sovereign.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.red.sovereign.calls.YounesCallOverlay
import com.red.sovereign.calls.YounesConferenceOverlay
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.YounesLiveStreamOverlay
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.calls.LiveStreamRuntime
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.RichMessage
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
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private enum class MainSection(val label: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Default.Home),
    CHATS("الدردشات", Icons.Default.Forum),
    GROUPS("المجموعات", Icons.Default.Groups),
    CALLS("المكالمات", Icons.Default.Call),
    MORE("المزيد", Icons.Default.Menu)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedDashboard(account: AuthState.Authenticated, viewModel: AuthViewModel) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(MainSection.HOME) }
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDinstar by remember { mutableStateOf(false) }
    val feed: FeedViewModel = viewModel()
    val stories: StoryViewModel = viewModel()
    val groups: GroupViewModel = viewModel()
    val directory: DirectoryViewModel = viewModel()
    val safety: SafetyViewModel = viewModel()
    val attachments: AttachmentViewModel = viewModel()
    val voiceMessages: VoiceMessageViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    val callHistory: CallHistoryViewModel = viewModel()
    val createStoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(stories::upload) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (section == MainSection.HOME && !showDinstar) FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = YounesEmerald,
                contentColor = Color(0xFF002117)
            ) { Icon(Icons.Default.Add, "إنشاء محتوى") }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f)) {
                MainSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item; showDinstar = false; if (item == MainSection.CALLS) callHistory.load() },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RedTopBar(account.redId, compact = SettingsRuntime.current.compactMode, onSettings = { showSettings = true })
            when {
                showDinstar -> DinstarPhoneScreen(account, viewModel)
                section == MainSection.HOME -> FeedScreen(account, feed, stories, onCreate = { showCreate = true })
                section == MainSection.CHATS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = false)
                section == MainSection.GROUPS -> ChatHubScreen(account, groups, directory, safety, attachments, voiceMessages, showGroups = true)
                section == MainSection.CALLS -> UnifiedCallsScreen(account.redId, callHistory)
                else -> MoreScreen(account, onDinstar = { showDinstar = true }, onSettings = { showSettings = true }, onContacts = { section = MainSection.CHATS })
            }
        }
    }

    if (showCreate) CreateSheet(
        publishing = feed.state == FeedState.Publishing,
        onDismiss = { showCreate = false },
        onPost = { text -> feed.create(text) { showCreate = false } },
        onPoll = { question, options, hours -> feed.createPoll(question, options, hours) { showCreate = false } },
        onStory = { showCreate = false; createStoryPicker.launch(arrayOf("image/*", "video/*")) },
        onLive = { showCreate = false; LiveStreamService.start(context, "stream-${account.redId}", account.redId, true) }
    )
    if (showSettings) YounesSettingsSheet(account, settings, viewModel::logout) { showSettings = false }
    YounesCallOverlay()
    YounesConferenceOverlay()
    YounesLiveStreamOverlay()
}

@Composable
private fun RedTopBar(redId: String, compact: Boolean, onSettings: () -> Unit) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        painterResource(R.drawable.younes_icon_master),
        contentDescription = "يونس",
        modifier = Modifier.size(if (compact) 34.dp else 40.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
    Text(" يونس", fontSize = 24.sp, color = AqyalGold, fontWeight = FontWeight.Black)
    Text("  $redId", color = AqyalCyanGlow, fontSize = 11.sp, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    IconButton({}, enabled = false) { Icon(Icons.Default.Search, "بحث — قيد الربط") }
    IconButton(onSettings) { Icon(Icons.Default.Settings, "الإعدادات") }
}

@Composable
private fun FeedScreen(account: AuthState.Authenticated, feed: FeedViewModel, stories: StoryViewModel, onCreate: () -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    var threadPost by remember { mutableStateOf<Post?>(null) }
    var quotePost by remember { mutableStateOf<Post?>(null) }
    var replyText by remember { mutableStateOf("") }
    var quoteText by remember { mutableStateOf("") }
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(stories::upload) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            LazyRow(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { StoryCircle(if (stories.state == StoryState.Uploading) "يرفع…" else "قصتك", true) { storyPicker.launch(arrayOf("image/*", "video/*")) } }
                items(stories.stories, key = Story::id) { story -> StoryCircle(story.ownerDisplayName, false) { stories.open(story) } }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("لك", "أتابعهم", "اليمن").forEachIndexed { i, title ->
                    FilterChip(filter == i, {
                        filter = i
                        feed.load(when (i) { 1 -> "FOLLOWING"; 2 -> "YEMEN"; else -> null })
                    }, { Text(title) })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = onCreate), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar("أ"); Text("ماذا يحدث في يونس؟", color = Color.LightGray, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)); Icon(Icons.Default.Add, null, tint = AqyalGold)
                }
            }
        }
        if (feed.state is FeedState.Message) item { Text((feed.state as FeedState.Message).text, color = AqyalGold, modifier = Modifier.padding(horizontal = 18.dp)) }
        when {
            feed.state == FeedState.Loading -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }
            feed.state is FeedState.Error -> item { EmptyState(Icons.Default.DynamicFeed, "تعذر تحميل نبض يونس", (feed.state as FeedState.Error).message) }
            feed.posts.isEmpty() -> item { EmptyState(Icons.Default.DynamicFeed, "ابدأ مجتمع يونس", "اكتب أول منشور محلي. النظام يدعم السلاسل والاقتباسات والاستطلاعات، بينما المحتوى الخاص ينتظر تشفير E2EE.") }
            else -> items(feed.posts, key = { it.id }) { post -> PostCard(post, account.redId, feed::toggleLike, feed::follow, feed::vote, { threadPost = post; feed.loadThread(post) }, { quotePost = post }) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    threadPost?.let { root ->
        AlertDialog(
            onDismissRequest = { threadPost = null; replyText = ""; feed.closeThread() },
            title = { Text("سلسلة يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val threadState = feed.threadState) {
                        ThreadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                        is ThreadState.Error -> Text(threadState.message, color = MaterialTheme.colorScheme.error)
                        else -> LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(feed.threadPosts, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (item.id == root.id) AqyalSurfaceRaised else AqyalSurfaceNavy)) {
                                    Column(Modifier.padding(12.dp)) { Text("@${item.authorUsername} · ${item.authorRedId}", color = AqyalCyanGlow, fontSize = 10.sp); Text(item.text) }
                                }
                            }
                        }
                    }
                    OutlinedTextField(replyText, { replyText = it }, Modifier.fillMaxWidth(), placeholder = { Text("اكتب ردًا علنيًا في نبض يونس…") }, maxLines = 4)
                    Button({ feed.reply(root, replyText) { replyText = "" } }, Modifier.fillMaxWidth(), enabled = replyText.isNotBlank() && feed.threadState != ThreadState.Publishing) { Text("إرسال الرد") }
                }
            },
            confirmButton = { TextButton({ threadPost = null; replyText = ""; feed.closeThread() }) { Text("إغلاق") } }
        )
    }
    quotePost?.let { quoted ->
        AlertDialog(
            onDismissRequest = { quotePost = null; quoteText = "" },
            title = { Text("اقتباس منشور @${quoted.authorUsername}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Card { Text(quoted.text, Modifier.padding(12.dp), color = Color.Gray) }; OutlinedTextField(quoteText, { quoteText = it }, Modifier.fillMaxWidth(), label = { Text("تعليقك") }, maxLines = 5) } },
            confirmButton = { Button({ feed.quote(quoted, quoteText) { quotePost = null; quoteText = "" } }, enabled = quoteText.isNotBlank() && feed.state != FeedState.Publishing) { Text("نشر الاقتباس") } },
            dismissButton = { TextButton({ quotePost = null; quoteText = "" }) { Text("إلغاء") } }
        )
    }
    val viewer = stories.viewer
    if (viewer !is StoryViewerState.Closed) {
        val currentStoryId = when (viewer) {
            is StoryViewerState.Loading -> viewer.story.id
            is StoryViewerState.Image -> viewer.story.id
            is StoryViewerState.Video -> viewer.story.id
            is StoryViewerState.Unsupported -> viewer.story.id
            is StoryViewerState.Error -> viewer.story.id
            StoryViewerState.Closed -> ""
        }
        StoryFullscreen(
            viewer = viewer,
            storiesList = stories.stories,
            onClose = stories::closeViewer,
            onNext = {
                val idx = stories.stories.indexOfFirst { it.id == currentStoryId }
                if (idx != -1 && idx < stories.stories.size - 1) {
                    stories.open(stories.stories[idx + 1])
                } else {
                    stories.closeViewer()
                }
            },
            onPrev = {
                val idx = stories.stories.indexOfFirst { it.id == currentStoryId }
                if (idx > 0) {
                    stories.open(stories.stories[idx - 1])
                } else {
                    stories.closeViewer()
                }
            }
        )
    }
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
    onQuote: () -> Unit
) = Card(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy.copy(alpha = .96f)),
    shape = RoundedCornerShape(24.dp)
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (post.authorRedId != currentRedId) TextButton({ onFollow(post) }) { Text("متابعة") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip({}, { Text(if (post.visibility == "LOCAL_YEMEN") "نبض محلي" else "عام") }, enabled = false, leadingIcon = { Icon(Icons.Default.Public, null, Modifier.size(15.dp)) })
            AssistChip({}, { Text(if (post.poll != null) "استطلاع" else if (post.parentId != null) "رد" else "منشور") }, enabled = false)
            if (post.kind != "POST") AssistChip({}, { Text(post.kind) }, enabled = false)
        }
        Text(post.text, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
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
            PostAction(Icons.Default.Share, "مشاركة", false) {}
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

@Composable
private fun ChatHubScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    safety: SafetyViewModel,
    attachments: AttachmentViewModel,
    voiceMessages: VoiceMessageViewModel,
    showGroups: Boolean
) {
    LaunchedEffect(directory.contacts.size) { directory.refreshPresence() }
    val tab = if (showGroups) 1 else 0
    var target by remember { mutableStateOf("") }
    var showDirectory by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }
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
    var create by remember { mutableStateOf(false) }
    var showJoinGroup by remember { mutableStateOf(false) }
    var joinToken by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var groupConversationId by remember { mutableStateOf<String?>(null) }
    var groupMessageText by remember { mutableStateOf("") }
    var selectedGroupMember by remember { mutableStateOf<GroupMember?>(null) }
    var deleteGroupId by remember { mutableStateOf<String?>(null) }
    var memberRedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    val decrypted = remember { mutableStateListOf<DecryptedMessage>() }
    val context = LocalContext.current
    
    val typingUsers = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.red.sovereign.core.TypingEventBus.events.collect { event ->
            if (event.isTyping) typingUsers[event.userId] = System.currentTimeMillis() + 5000L
            else typingUsers.remove(event.userId)
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
    androidx.compose.runtime.LaunchedEffect(typingUsers) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = System.currentTimeMillis()
            typingUsers.entries.removeAll { it.value < now }
        }
    }
    
    val conversation = remember(account.redId, target) { conversationId(account.redId, target) }
    androidx.compose.runtime.LaunchedEffect(messageText) {
        if (target.matches(RED_ID_PATTERN)) {
            val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, conversation)
                putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, messageText.isNotEmpty())
            }
            context.startService(intent)
            if (messageText.isNotEmpty()) {
                kotlinx.coroutines.delay(3000)
                val stopIntent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
                    action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
                    putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
                    putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, conversation)
                    putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, false)
                }
                context.startService(stopIntent)
            }
        }
    }
    val localMessages = remember { MessageStore(context) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && target.isNotBlank()) attachments.send(uri, target, conversationId(account.redId, target))
    }
    val groupAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val group = groups.groups.firstOrNull { it.id == selectedGroupId }
        if (uri != null && group != null) groups.updateAvatar(group, uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) attachments.exportTo(uri)
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
        if (granted && target.matches(RED_ID_PATTERN)) voiceMessages.start(target, conversationId(account.redId, target))
        else if (!granted) voiceMessages.permissionDenied()
    }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingCallVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioGranted && cameraGranted && target.matches(RED_ID_PATTERN)) YounesCallService.start(context, target, pendingCallVideo)
    }
    LaunchedEffect(Unit) { DecryptedMessageBus.messages.collect { item ->
        decrypted.add(item)
        if (!item.outgoing && SettingsRuntime.current.readReceipts) RedConnectionService.markRead(context, item.id, item.sequence)
    } }
    LaunchedEffect(target, groupConversationId) {
        val conversationToRestore = groupConversationId ?: target.takeIf(String::isNotBlank)?.let { conversationId(account.redId, it) }
        if (conversationToRestore != null) localMessages.localHistory(conversationToRestore).forEach { stored ->
            if (decrypted.none { it.id == stored.id }) decrypted.add(DecryptedMessage(stored.id, stored.conversationId, stored.senderId, stored.plaintext, stored.timestamp, 0, stored.type, stored.outgoing))
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (tab == 0) Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (directory.requests.isNotEmpty()) {
                Text("طلبات الصداقة", color = AqyalGold, fontWeight = FontWeight.Bold)
                directory.requests.forEach { request ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceRaised)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(request.requester.displayName.take(1)); Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text(request.requester.displayName); Text("@${request.requester.username}", color = AqyalCyanGlow, fontSize = 11.sp) }
                            TextButton({ directory.resolve(request, false) }) { Text("رفض") }
                            Button({ directory.resolve(request, true) }) { Text("قبول") }
                        }
                    }
                }
            }
            if (directory.contacts.isNotEmpty()) {
                Text("الأصدقاء", color = AqyalGold, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(directory.contacts.filterNot { localMessages.conversationPreference(conversationId(account.redId, it.redId)).second }.sortedByDescending { localMessages.conversationPreference(conversationId(account.redId, it.redId)).first }, key = { it.redId }) { person ->
                        Column(Modifier.widthIn(max = 86.dp).clickable { target = person.redId }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(person.displayName.take(1)); Text(person.displayName, maxLines = 1, fontSize = 11.sp); Text("@${person.username}", color = AqyalCyanGlow, maxLines = 1, fontSize = 9.sp)
                            IconButton({ selectedContact = person }, Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, "إعدادات الصديق", Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            val activePerson = directory.contacts.firstOrNull { it.redId == target }
            if (target.isBlank()) Card(Modifier.fillMaxWidth().clickable { showDirectory = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = YounesEmerald)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("بدء محادثة خاصة", fontWeight = FontWeight.SemiBold); Text("ابحث بالاسم الدقيق أو معرّف يونس", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ target = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة لقائمة الدردشات") }
                    Avatar((activePerson?.displayName ?: target).take(1))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(activePerson?.displayName ?: target, fontWeight = FontWeight.SemiBold)
                        Text(activePerson?.let { "@${it.username} · ${it.redId}" } ?: target, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ pendingCallVideo = false; callPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }) { Icon(Icons.Default.Call, "مكالمة صوتية عبر يونس") }
                    IconButton({ pendingCallVideo = true; callPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) }) { Icon(Icons.Default.Videocam, "مكالمة فيديو عبر يونس") }
                    IconButton({
                        val room = conversationId(account.redId, target)
                        ConferenceService.join(context, room, account.redId, true)
                    }) { Icon(Icons.Default.Groups, "مؤتمر فيديو جماعي") }
                    IconButton({ showMessageSearch = true }) { Icon(Icons.Default.Search, "البحث في المحادثة") }
                    IconButton({ safety.open(target) }) { Icon(Icons.Default.Security, "رمز الأمان") }
                    if (activePerson != null) IconButton({ selectedContact = activePerson }) { Icon(Icons.Default.MoreVert, "خيارات المحادثة") }
                }
            }
            val conversation = remember(account.redId, target) { conversationId(account.redId, target) }
            val conversationMessages = resolveRichMessages(decrypted.filter { it.conversationId == conversation })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target.isBlank()) {
                    val groupIds = groups.groups.map(Group::id).toSet()
                    items(localMessages.conversationSummaries(account.redId).filter { !it.archived && it.conversationId !in groupIds }, key = { it.conversationId }) { summary ->
                        Card(Modifier.fillMaxWidth().clickable { target = summary.peerId }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            val contact = directory.contacts.firstOrNull { it.redId == summary.peerId }
                            val displayName = contact?.displayName ?: summary.peerId
                            val isOnline = directory.isOnline(summary.peerId)
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Avatar(displayName.take(1))
                                    if (isOnline) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF00C98C)).border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                    }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(summary.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (summary.pinned) Icon(androidx.compose.material.icons.Icons.Default.Star, "مثبت", tint = Color(0xFFF5C842), modifier = Modifier.size(20.dp))
                                if (summary.mutedUntil > System.currentTimeMillis()) Icon(androidx.compose.material.icons.Icons.Default.NotificationsOff, "مكتوم", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).padding(start = 4.dp))
                            }
                        }
                    }
                }
                if (target.isNotBlank() && conversationMessages.isEmpty()) item {
                    Text("ابدأ المحادثة برسالة. التشفير يُنشأ على الجهاز ولا يرى الخادم النص.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                items(conversationMessages, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.outgoing) Arrangement.End else Arrangement.Start) {
                        Card(
                            Modifier.widthIn(max = 320.dp).clickable { selectedChatMessage = item },
                            colors = CardDefaults.cardColors(containerColor = if (item.outgoing) YounesEmerald.copy(alpha = .82f) else AqyalSurfaceRaised.copy(alpha = .94f)),
                            shape = RoundedCornerShape(
                                topStart = 20.dp, topEnd = 20.dp,
                                bottomStart = if (item.outgoing) 20.dp else 5.dp,
                                bottomEnd = if (item.outgoing) 5.dp else 20.dp
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(if (item.outgoing) "أنت" else item.senderRedId, color = if (item.outgoing) Color(0xB8002018) else AqyalCyanGlow, fontSize = 10.sp)
                                when (item.type) {
                                    "FILE", "IMAGE", "VIDEO", "AUDIO" -> AttachmentMessage(item, attachments)
                                    "VOICE" -> VoiceMessage(item, attachments)
                                    "RICH_TEXT" -> RichTextMessage(item, conversationMessages)
                                    else -> Text(item.plaintext.toString(Charsets.UTF_8), color = if (item.outgoing) Color(0xFF001B14) else Color.White, fontSize = 16.sp)
                                }
                                if (item.outgoing) {
                                    val ticks = when (item.status) {
                                        "READ" -> "✓✓ (مقروء)"
                                        "DELIVERED" -> "✓✓"
                                        else -> "✓"
                                    }
                                    Text(ticks, color = if (item.status == "READ") com.red.sovereign.ui.theme.AqyalCyanGlow else Color(0x99001B14), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                                }
                            }
                        }
                    }
                }
                if (typingUsers.containsKey(target) && target.isNotBlank()) {
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
            (replyToMessage ?: editingMessageId?.let { id -> conversationMessages.firstOrNull { it.id == id } })?.let { referenced ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (editingMessageId != null) "تعديل الرسالة" else "رد على رسالة", color = YounesEmerald, style = MaterialTheme.typography.labelMedium); Text(messageDisplayText(referenced), maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton({ replyToMessage = null; editingMessageId = null }) { Icon(Icons.Default.Close, "إلغاء") } } }
            }
            if (showEmoji) EmojiPicker(onEmoji = { messageText += it })
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
            when (val attachmentState = attachments.state) {
                is AttachmentState.Working -> Text(attachmentState.message, color = AqyalGold, style = MaterialTheme.typography.bodySmall)
                is AttachmentState.Error -> Text("تعذر إكمال المرفق: ${attachmentState.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is AttachmentState.Sent -> Text("تم إرسال ${attachmentState.name} مشفرًا", color = YounesEmerald, style = MaterialTheme.typography.bodySmall)
                is AttachmentState.Downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تم التحقق وفك التشفير: ${attachmentState.name}", color = YounesEmerald, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton({ exportPicker.launch(attachmentState.name) }) { Text("حفظ نسخة") }
                }
                is AttachmentState.Exported -> Text("تم حفظ ${attachmentState.name} في الموقع الذي اخترته", color = YounesEmerald, style = MaterialTheme.typography.bodySmall)
                AttachmentState.Idle -> Unit
            }
            when (val voiceState = voiceMessages.state) {
                is VoiceMessageState.Recording -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (voiceState.paused) "متوقف مؤقتًا ${formatDuration(voiceMessages.elapsedSeconds)}" else "● تسجيل ${formatDuration(voiceMessages.elapsedSeconds)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(voiceMessages::togglePause) { Icon(if (voiceState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (voiceState.paused) "استئناف" else "إيقاف مؤقت") }
                        TextButton(voiceMessages::cancel) { Text("إلغاء") }
                    }
                    VoiceWaveform(voiceMessages.waveform, MaterialTheme.colorScheme.error, Modifier.fillMaxWidth().height(34.dp))
                }
                VoiceMessageState.Sending -> Text("جارٍ تشفير الرسالة الصوتية ورفعها…", color = AqyalGold, style = MaterialTheme.typography.bodySmall)
                is VoiceMessageState.Sent -> Text("تم إرسال رسالة صوتية ${formatDuration(voiceState.durationSeconds)}", color = YounesEmerald, style = MaterialTheme.typography.bodySmall)
                is VoiceMessageState.Error -> Text("تعذر التسجيل: ${voiceState.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                VoiceMessageState.Idle -> Unit
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton({ showEmoji = !showEmoji }) { Icon(Icons.Default.EmojiEmotions, "الرموز التعبيرية") }
                IconButton({ showAttachmentSheet = true }, enabled = target.matches(RED_ID_PATTERN) && attachments.state !is AttachmentState.Working) {
                    Icon(Icons.Default.AttachFile, "إرفاق")
                }
                IconButton({
                    if (voiceMessages.state is VoiceMessageState.Recording) voiceMessages.stopAndSend(target, conversation)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceMessages.start(target, conversation)
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = target.matches(RED_ID_PATTERN) && voiceMessages.state !is VoiceMessageState.Sending) {
                    Icon(if (voiceMessages.state is VoiceMessageState.Recording) Icons.Default.Stop else Icons.Default.Mic, if (voiceMessages.state is VoiceMessageState.Recording) "إيقاف وإرسال" else "تسجيل رسالة صوتية")
                }
                OutlinedTextField(messageText, { messageText = it }, Modifier.weight(1f), placeholder = { Text("رسالة مشفرة…") }, maxLines = 4)
                FilledIconButton({
                    val rich = RichMessage(
                        action = if (editingMessageId != null) "EDIT" else "MESSAGE",
                        text = messageText.trim(), replyTo = replyToMessage?.id, editOf = editingMessageId,
                        expiresAt = disappearingDurationMs?.let { System.currentTimeMillis() + it }
                    )
                    RedConnectionService.sendRichText(context, target, conversation, rich)
                    messageText = ""; showEmoji = false; replyToMessage = null; editingMessageId = null
                }, enabled = target.matches(RED_ID_PATTERN) && messageText.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال") }
            }
            }
        } else Column(Modifier.fillMaxSize().padding(14.dp)) {
            val openGroup = groups.groups.firstOrNull { it.id == groupConversationId }
            if (openGroup == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ create = true }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" إنشاء") }
                    OutlinedButton({ showJoinGroup = true }, Modifier.weight(1f)) { Text("انضمام بدعوة") }
                }
                when {
                    groups.state == GroupState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
                    groups.state is GroupState.Error -> EmptyState(Icons.Default.Groups, "تعذر تحميل المجموعات", (groups.state as GroupState.Error).message)
                    groups.groups.isEmpty() -> EmptyState(Icons.Default.Groups, "لا توجد مجموعات", "أنشئ مجموعة محلية بأدوار مالك ومسؤول وعضو.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(groups.groups, key = { it.id }) { group ->
                            Card(Modifier.fillMaxWidth().clickable { groupConversationId = group.id }) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GroupAvatar(group, groups); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(group.name, fontWeight = FontWeight.Bold); Text("${group.members.size} أعضاء · ${group.description.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                    IconButton({ selectedGroupId = group.id }) { Icon(Icons.Default.MoreVert, "إدارة المجموعة") }
                                }
                            }
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ groupConversationId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة للمجموعات") }
                        GroupAvatar(openGroup, groups); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(openGroup.name, fontWeight = FontWeight.SemiBold); Text("${openGroup.members.size} أعضاء · Sender Keys", color = YounesEmerald, style = MaterialTheme.typography.labelSmall) }
                        IconButton({ selectedGroupId = openGroup.id }) { Icon(Icons.Default.MoreVert, "إدارة المجموعة") }
                    }
                }
                val groupMessages = decrypted.filter { it.conversationId == openGroup.id && it.type == "GROUP_MESSAGE" }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groupMessages.isEmpty()) item { Text("محادثة جماعية مشفرة بـSender Keys. يتغير المفتاح تلقائيًا عند تغير العضوية.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                items(groupMessages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                        Card(
                            Modifier.widthIn(max = 320.dp),
                            colors = CardDefaults.cardColors(containerColor = if (message.outgoing) YounesEmerald.copy(alpha = .82f) else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(
                                topStart = 20.dp, topEnd = 20.dp,
                                bottomStart = if (message.outgoing) 20.dp else 5.dp,
                                bottomEnd = if (message.outgoing) 5.dp else 20.dp
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                if (!message.outgoing) {
                                    val nameColors = listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF00BCD4), Color(0xFFFF9800), Color(0xFF795548))
                                    val colorIndex = kotlin.math.abs(message.senderRedId.hashCode()) % nameColors.size
                                    Text(message.senderRedId.take(12) + "...", color = nameColors[colorIndex], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Text(message.plaintext.toString(Charsets.UTF_8), color = if (message.outgoing) Color(0xFF002118) else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            }
                        }
                    }
                }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(groupMessageText, { groupMessageText = it }, Modifier.weight(1f), placeholder = { Text("رسالة جماعية مشفرة…") }, maxLines = 4)
                    FilledIconButton({ RedConnectionService.sendGroupText(context, openGroup, groupMessageText.trim()); groupMessageText = "" }, enabled = groupMessageText.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال للمجموعة") }
                }
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
        AlertDialog(
            onDismissRequest = { selectedChatMessage = null },
            title = { Text("خيارات الرسالة") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ replyToMessage = message; selectedChatMessage = null }, Modifier.fillMaxWidth()) { Text("رد") }
                OutlinedButton({ pendingForwardMessage = message; showDirectory = true; selectedChatMessage = null }, Modifier.fillMaxWidth()) { Text("إعادة توجيه") }
                if (message.outgoing && message.type == "RICH_TEXT" && payload?.action == "MESSAGE") OutlinedButton({ editingMessageId = message.id; messageText = payload.text; selectedChatMessage = null }, Modifier.fillMaxWidth()) { Text("تعديل") }
                if (message.outgoing) Button({
                    RedConnectionService.sendRichText(context, target, message.conversationId, RichMessage(action = "DELETE", deleteOf = message.id))
                    selectedChatMessage = null
                }, Modifier.fillMaxWidth()) { Text("حذف لدى الجميع") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ساعة" to 3_600_000L, "يوم" to 86_400_000L, "أسبوع" to 604_800_000L).forEach { option -> AssistChip({ disappearingDurationMs = option.second; selectedChatMessage = null }, { Text(option.first) }) }
                }
            } },
            confirmButton = { TextButton({ selectedChatMessage = null }) { Text("إغلاق") } }
        )
    }
    selectedContact?.let { person ->
        AlertDialog(
            onDismissRequest = { selectedContact = null; reportDetails = "" },
            title = { Text(person.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("@${person.username}\n${person.redId}", color = AqyalCyanGlow)
                    OutlinedTextField(reportDetails, { reportDetails = it }, Modifier.fillMaxWidth(), label = { Text("تفاصيل بلاغ اختياري") }, maxLines = 4)
                    OutlinedButton({ safety.open(person.redId); selectedContact = null }, Modifier.fillMaxWidth()) { Text("رمز الأمان والتحقق") }
                    val conversationKey = conversationId(account.redId, person.redId)
                    val preference = localMessages.conversationPreference(conversationKey)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ localMessages.setConversationPreference(conversationKey, "pinned", if (preference.first) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.first) "إلغاء التثبيت" else "تثبيت") }
                        OutlinedButton({ localMessages.setConversationPreference(conversationKey, "archived", if (preference.second) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.second) "إلغاء الأرشفة" else "أرشفة") }
                    }
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "muted_until", System.currentTimeMillis() + 8 * 60 * 60 * 1000L) }, Modifier.fillMaxWidth()) { Text("كتم 8 ساعات") }
                    OutlinedButton({ directory.remove(person); selectedContact = null }, Modifier.fillMaxWidth()) { Text("إزالة من الأصدقاء") }
                    OutlinedButton({ directory.report(person, "SPAM", reportDetails); reportDetails = "" }, Modifier.fillMaxWidth()) { Text("إبلاغ عن إزعاج/احتيال") }
                    Button({ directory.block(person); selectedContact = null }, Modifier.fillMaxWidth()) { Text("حظر المستخدم") }
                }
            },
            confirmButton = { TextButton({ selectedContact = null; reportDetails = "" }) { Text("إغلاق") } }
        )
    }
    val selectedGroup = groups.groups.firstOrNull { it.id == selectedGroupId }
    if (selectedGroup != null) {
        val myRole = selectedGroup.members.firstOrNull { it.redId == account.redId }?.role
        val canManage = myRole == "OWNER" || myRole == "ADMIN"
        AlertDialog(
            onDismissRequest = { selectedGroupId = null },
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
                                if (manageable) Icon(Icons.Default.MoreVert, "إدارة العضو", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (canManage) {
                        OutlinedButton({ groupAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, Modifier.fillMaxWidth()) { Text("تغيير صورة المجموعة") }
                        OutlinedTextField(memberRedId, { memberRedId = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("إضافة عضو بواسطة معرّف يونس") }, singleLine = true)
                        Button({ groups.addMember(selectedGroup, memberRedId) { memberRedId = "" } }, Modifier.fillMaxWidth(), enabled = memberRedId.matches(RED_ID_PATTERN) && groups.state != GroupState.Saving) { Text("إضافة عضو") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ groups.createInvite(selectedGroup) }, Modifier.weight(1f)) { Text("رابط دعوة") }
                            OutlinedButton({ groups.loadJoinRequests(selectedGroup) }, Modifier.weight(1f)) { Text("طلبات الانضمام") }
                        }
                        groups.latestInvite?.let { invite ->
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            Card { Column(Modifier.padding(10.dp)) { Text("دعوة صالحة حتى ${invite.expiresAt}", style = MaterialTheme.typography.bodySmall); Text(invite.token, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AqyalCyanGlow); TextButton({ clipboard.setText(AnnotatedString(invite.token)) }) { Text("نسخ رمز الدعوة") } } }
                        }
                        groups.joinRequests.forEach { request -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("@${request.username}", Modifier.weight(1f)); TextButton({ groups.resolveJoin(selectedGroup, request, false) }) { Text("رفض") }; Button({ groups.resolveJoin(selectedGroup, request, true) }) { Text("قبول") } } }
                    }
                }
            },
            confirmButton = { TextButton({ selectedGroupId = null }) { Text("إغلاق") } },
            dismissButton = {
                if (myRole == "OWNER") TextButton({ deleteGroupId = selectedGroup.id }) { Text("حذف المجموعة", color = MaterialTheme.colorScheme.error) }
                else TextButton({ groups.leave(selectedGroup) { selectedGroupId = null; groupConversationId = null } }) { Text("مغادرة", color = MaterialTheme.colorScheme.error) }
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
                OutlinedButton({ groups.updateRole(selectedGroup, managedMember, if (managedMember.role == "ADMIN") "MEMBER" else "ADMIN"); selectedGroupMember = null }, Modifier.fillMaxWidth()) {
                    Text(if (managedMember.role == "ADMIN") "إرجاعه إلى عضو" else "ترقيته إلى مسؤول")
                }
                OutlinedButton({ groups.transferOwnership(selectedGroup, managedMember) { selectedGroupMember = null; selectedGroupId = null } }, Modifier.fillMaxWidth()) { Text("نقل ملكية المجموعة إليه") }
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
            confirmButton = { Button({ groups.deleteGroup(deleting) { deleteGroupId = null; selectedGroupId = null; groupConversationId = null } }) { Text("حذف نهائي") } },
            dismissButton = { TextButton({ deleteGroupId = null }) { Text("إلغاء") } }
        )
    }
    if (showMessageSearch) AlertDialog(
        onDismissRequest = { showMessageSearch = false; messageSearchQuery = "" },
        title = { Text("البحث داخل المحادثة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(messageSearchQuery, { messageSearchQuery = it }, Modifier.fillMaxWidth(), label = { Text("كلمة أو عبارة") }, singleLine = true)
            val currentConversation = conversationId(account.redId, target)
            val results = if (messageSearchQuery.length >= 2) localMessages.search(messageSearchQuery).filter { it.conversationId == currentConversation } else emptyList()
            LazyColumn(Modifier.height(280.dp)) { items(results, key = { it.id }) { result -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(10.dp)) { Text(if (result.type == "RICH_TEXT") RichMessage.decode(result.plaintext)?.text.orEmpty() else result.plaintext.toString(Charsets.UTF_8), maxLines = 4); Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } } }
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
                when (val state = directory.state) {
                    DirectoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                    is DirectoryState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is DirectoryState.Message -> Text(state.text, color = AqyalGold)
                    DirectoryState.Ready -> if (directory.results.isEmpty()) Text("لا توجد نتائج مطابقة", color = Color.Gray) else LazyColumn(Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(directory.results, key = { it.redId }) { person ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(person.displayName.take(1)); Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(person.displayName, fontWeight = FontWeight.Bold); Text("@${person.username} · ${person.redId}", color = AqyalCyanGlow, fontSize = 10.sp) }
                                    TextButton({
                                        val forward = pendingForwardMessage
                                        if (forward != null) {
                                            RedConnectionService.sendRichText(context, person.redId, conversationId(account.redId, person.redId), RichMessage(text = messageDisplayText(forward), forwardOf = forward.id))
                                            pendingForwardMessage = null
                                        } else target = person.redId
                                        showDirectory = false; directory.clear()
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
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("إنشاء مجموعة جديدة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { /* Future: Add Avatar upload */ }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CameraAlt, "إضافة صورة", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") }, singleLine = true)
            OutlinedTextField(groupDescription, { groupDescription = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("الوصف — اختياري") }, minLines = 2, maxLines = 4)
            Text("المجموعة مشفرة بشكل افتراضي. نستخدم Sender Keys في حالة وجود أعضاء.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ groups.create(name, groupDescription.trim().takeIf(String::isNotEmpty)) { create = false; name = ""; groupDescription = "" } }, enabled = name.trim().length in 2..100 && groups.state != GroupState.Saving) { Text("إنشاء المجموعة") } },
        dismissButton = { OutlinedButton({ create = false; name = ""; groupDescription = "" }) { Text("إلغاء") } })
    if (showJoinGroup) AlertDialog(
        onDismissRequest = { showJoinGroup = false; joinToken = "" },
        title = { Text("الانضمام إلى مجموعة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(joinToken, { joinToken = it.trim() }, Modifier.fillMaxWidth(), label = { Text("رمز الدعوة") }, singleLine = true); Text("قد يتطلب الانضمام موافقة مالك أو مسؤول المجموعة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button({ groups.joinWithToken(joinToken) { showJoinGroup = false; joinToken = "" } }, enabled = joinToken.length >= 32 && groups.state != GroupState.Saving) { Text("إرسال الطلب") } },
        dismissButton = { TextButton({ showJoinGroup = false; joinToken = "" }) { Text("إلغاء") } }
    )
}

@Composable
private fun UnifiedCallsScreen(ownUserId: String, history: CallHistoryViewModel) {
    var filter by remember { mutableStateOf("الكل") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
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
        Text("سجل موحد لكل مكالمة من المحادثات والمجموعات والبث والمساحات والهاتف اليمني.", color = Color.LightGray)
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundCallAction(Icons.Default.Groups, "جماعية", AqyalCyanGlow, true) { showJoinDialog = true }
            RoundCallAction(Icons.Default.LiveTv, "بث مباشر", Color.Red, true) { showLiveDialog = true }
            RoundCallAction(Icons.Default.RecordVoiceOver, "مساحات", Color(0xFFA78BFA), false)
        }
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
                        ConferenceService.join(context, roomInput.trim(), ownUserId, true)
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

    if (showLiveDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDialog = false; roomInput = ""; isBroadcaster = false },
            title = { Text("بث مباشر يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اختر طبيعة المشاركة في البث:", color = Color.Gray, fontSize = 14.sp)
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف البث (مثال: stream-abc)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isBroadcaster, onCheckedChange = { isBroadcaster = it })
                        Text("بدء البث كمنتج (Broadcaster)", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveDialog = false
                        LiveStreamService.start(context, roomInput.trim(), ownUserId, isBroadcaster)
                        roomInput = ""
                    },
                    enabled = roomInput.trim().isNotBlank()
                ) {
                    Text("بدء")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveDialog = false; roomInput = ""; isBroadcaster = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun CallHistoryRow(call: CallHistoryItem) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (call.route == "DINSTAR") AqyalGold else AqyalCyanGlow), contentAlignment = Alignment.Center) {
            Icon(if (call.type == "VIDEO") Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.Black)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(call.peerLabel.ifBlank { call.peerId }, fontWeight = FontWeight.Bold)
            Text("${if (call.direction == "OUTGOING") "صادرة" else "واردة"} · ${call.status}", color = if (call.status == "MISSED") Color.Red else Color.Gray, fontSize = 12.sp)
        }
        AssistChip({}, { Text(if (call.route == "DINSTAR") "DINSTAR صوت" else "يونس ${call.type}") }, enabled = false)
    }
}

@Composable
private fun RoundCallAction(icon: ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    FilledIconButton(onClick, Modifier.size(62.dp), enabled = enabled) { Icon(icon, title, tint = if (enabled) color else Color.Gray, modifier = Modifier.size(30.dp)) }
    Text(title, fontSize = 11.sp); if (!enabled) Text("قيد الربط", color = Color.Gray, fontSize = 9.sp)
}

@Composable
private fun MoreScreen(account: AuthState.Authenticated, onDinstar: () -> Unit, onSettings: () -> Unit, onContacts: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مساحة يونس", style = MaterialTheme.typography.headlineMedium)
        Text("الهوية والخدمات السيادية في مكان واحد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(account.username.take(1))
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium)
                    Text("@${account.username} · ${account.redId}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MoreOption(Icons.Default.SimCard, "الهاتف اليمني", "اتصال صوتي مصرح عبر DINSTAR وشرائح الشبكات اليمنية", AqyalGold, click = onDinstar)
        MoreOption(Icons.Default.Settings, "الإعدادات والخصوصية", "الهوية والأجهزة والخادم والجلسة", YounesEmerald, click = onSettings)
        MoreOption(Icons.Default.Contacts, "جهات الاتصال", "الأصدقاء وطلبات التواصل والحظر", AqyalCyanGlow, click = onContacts)
        MoreOption(Icons.Default.Public, "المجتمعات والقنوات", "قيد التطوير — لن يُعرض كمكتمل قبل اختباره", Color(0xFFA78BFA), enabled = false) { }
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
private fun DinstarPhoneScreen(account: AuthState.Authenticated, viewModel: AuthViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(35.dp)); Column(Modifier.padding(start = 12.dp)) {
                    Text("الهاتف اليمني عبر DINSTAR", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Text(if (account.pstnEnabled) "مصرح لك — مكالمات صوتية فقط" else "غير مفعل — يفعله المسؤول من اللوحة", fontSize = 12.sp)
                }
            }
        }
        PrimaryTabRow(tab) {
            listOf(Icons.Default.Dialpad to "الأرقام", Icons.Default.Star to "المفضلة", Icons.Default.History to "السجل", Icons.Default.Contacts to "جهات الاتصال").forEachIndexed { i, item -> Tab(tab == i, { tab = i }, icon = { Icon(item.first, null) }, text = { Text(item.second, fontSize = 10.sp) }) }
        }
        when (tab) {
            0 -> DialPad(account.pstnEnabled, viewModel)
            1 -> EmptyState(Icons.Default.Star, "المفضلة", "أرقامك اليمنية المفضلة")
            2 -> EmptyState(Icons.Default.History, "سجل DINSTAR", "المكالمات الهاتفية فقط؛ السجل الموحد موجود في مركز المكالمات")
            else -> EmptyState(Icons.Default.Contacts, "جهات الاتصال", "اختيار رقم يمني للاتصال الصوتي")
        }
    }
}

@Composable
private fun DialPad(enabled: Boolean, viewModel: AuthViewModel) {
    var number by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number.ifEmpty { "أدخل الرقم" }, fontSize = 27.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton({ if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "حذف") }
        }
        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { digit -> FilledIconButton({ number += digit }, Modifier.size(64.dp)) { Text(digit, fontSize = 23.sp) } } }
        }
        Button({ viewModel.clearPstnState(); viewModel.dialPstn(number) }, enabled = enabled && number.filter(Char::isDigit).length >= 6, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Call, null); Text(" اتصال صوتي عبر DINSTAR") }
        when (val state = viewModel.pstnState) {
            PstnState.Dialing -> CircularProgressIndicator(color = AqyalGold)
            is PstnState.Started -> Text("بدأ الاتصال · ${state.usedToday}/${state.dailyLimit} اليوم", color = AqyalGold)
            is PstnState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            PstnState.Idle -> Unit
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
    onLive: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") }
    var text by remember { mutableStateOf("") }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "", "")) }
    var pollHours by remember { mutableIntStateOf(24) }
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
                else -> {
                    CreateOption(Icons.Default.DynamicFeed, "منشور أو سلسلة", "نص طويل، اقتباس، نقاش محلي", true) { mode = "post" }
                    CreateOption(Icons.Default.Forum, "استطلاع تفاعلي", "سؤال وخيارات وتصويت فعلي عبر الخادم", true) { mode = "poll" }
                    CreateOption(Icons.Default.AddCircle, "حالة 24 ساعة", "صورة أو فيديو يُحذف تلقائياً", true, onStory)
                    CreateOption(Icons.Default.LiveTv, "بث مباشر", "فيديو عبر SFU المحلي", true, onLive)
                    CreateOption(Icons.Default.RecordVoiceOver, "مساحة صوتية", "قريباً: غرفة صوتية عامة مرتبطة بالمؤتمرات", false) {}
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun CreateOption(icon: ImageVector, title: String, detail: String, enabled: Boolean, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = if (enabled) AqyalGold else Color.Gray, modifier = Modifier.size(31.dp)); Column(Modifier.padding(horizontal = 14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color.Unspecified else Color.Gray); Text(detail, color = Color.Gray, fontSize = 12.sp) } } }

@Composable private fun EmptyState(icon: ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }
private fun resolveRichMessages(source: List<DecryptedMessage>): List<DecryptedMessage> {
    val visible = linkedMapOf<String, DecryptedMessage>()
    source.sortedBy(DecryptedMessage::timestamp).forEach { message ->
        val rich = if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext) else null
        when {
            rich?.action == "DELETE" && rich.deleteOf != null -> visible.remove(rich.deleteOf)
            rich?.action == "EDIT" && rich.editOf != null -> visible[rich.editOf]?.let { original -> visible[rich.editOf] = original.copy(plaintext = RichMessage.encode(RichMessage(text = rich.text, replyTo = RichMessage.decode(original.plaintext)?.replyTo))) }
            rich?.expiresAt != null && rich.expiresAt <= System.currentTimeMillis() -> Unit
            else -> visible[message.id] = message
        }
    }
    return visible.values.toList()
}

private fun messageDisplayText(message: DecryptedMessage): String =
    if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext)?.text.orEmpty() else message.plaintext.toString(Charsets.UTF_8)

@Composable
private fun RichTextMessage(message: DecryptedMessage, conversation: List<DecryptedMessage>) {
    val rich = RichMessage.decode(message.plaintext)
    if (rich == null) { Text("رسالة غير صالحة", color = MaterialTheme.colorScheme.error); return }
    rich.replyTo?.let { replyId -> conversation.firstOrNull { it.id == replyId }?.let { quoted -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f))) { Text(messageDisplayText(quoted), Modifier.padding(7.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall) } } }
    if (rich.forwardOf != null) Text("معاد توجيهها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(rich.text, color = if (message.outgoing) Color(0xFF001B14) else MaterialTheme.colorScheme.onSurface)
    rich.expiresAt?.let { Text("رسالة مؤقتة", style = MaterialTheme.typography.labelSmall) }
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

@Composable
private fun VoiceMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<VoiceManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("رسالة صوتية غير صالحة", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(manifestJson)
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        VoiceNotePlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxWidth())
    } else Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Mic, null, tint = if (item.outgoing) Color(0xFF00382A) else YounesEmerald)
        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
            Text("رسالة صوتية", fontWeight = FontWeight.SemiBold)
            VoiceWaveform(manifest.waveform, if (item.outgoing) Color(0xFF00513E) else YounesEmerald, Modifier.fillMaxWidth().height(30.dp))
            Text("${formatDuration(manifest.durationSeconds)} · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
        }
        IconButton({ attachments.download(manifestJson) }, enabled = attachments.state !is AttachmentState.Working) {
            Icon(Icons.Default.Download, "تنزيل وتشغيل الرسالة الصوتية")
        }
    }
}

@Composable
private fun AttachmentMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("مرفق مشفر غير صالح", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(manifestJson)
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
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (downloaded?.second == manifest.name) {
            val file = java.io.File(downloaded.first)
            val bitmap = remember(file.lastModified()) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap, contentDescription = "صورة",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("صورة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.state !is AttachmentState.Working) {
                        Icon(Icons.Default.Download, "تنزيل")
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            StoryVideoPlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxWidth().height(220.dp))
        }
    } else {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(YounesEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = YounesEmerald, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("فيديو مشفر · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.state !is AttachmentState.Working) {
                    Icon(Icons.Default.Download, "تنزيل الفيديو")
                }
            }
        }
    }
}

@Composable
private fun AudioMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(30.dp))
                    Text(manifest.name, Modifier.padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                }
                VoiceNotePlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxWidth())
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
                IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.state !is AttachmentState.Working) {
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(manifest.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${manifest.mimeType} · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
        }
        IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.state !is AttachmentState.Working) {
            Icon(Icons.Default.Download, "تنزيل وفك تشفير المرفق")
        }
    }
}

private fun shouldAutoDownload(context: android.content.Context, sizeBytes: Long): Boolean {
    val preferences = SettingsRuntime.current
    if (sizeBytes > preferences.autoDownloadLimitMb * 1024L * 1024L) return false
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> preferences.autoDownloadWifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> preferences.autoDownloadMobile
        else -> false
    }
}

private fun groupRoleLabel(role: String) = when (role) { "OWNER" -> "المالك"; "ADMIN" -> "مسؤول"; else -> "عضو" }

private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
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

private val RED_ID_PATTERN = Regex("^(RED|YNS)-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$")
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
private val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }

private fun conversationId(first: String, second: String): String {
    if (first.isBlank() || second.isBlank()) return "pending-conversation"
    val canonical = listOf(first, second).sorted().joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
