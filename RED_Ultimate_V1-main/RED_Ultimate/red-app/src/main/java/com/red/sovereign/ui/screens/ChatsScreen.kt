package com.red.sovereign.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.red.sovereign.auth.AuthState
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.ConferenceUiState
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.PinsApi
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.ReactionEventBus
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.TypingEventBus
import com.red.sovereign.core.YounesId
import com.red.sovereign.core.database.MessageReactionEntity
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.crypto.DecryptedMessageBus
import com.red.sovereign.crypto.MessageAckBus
import com.red.sovereign.crypto.SafetyQrScanner
import com.red.sovereign.crypto.SafetyState
import com.red.sovereign.crypto.SafetyViewModel
import com.red.sovereign.features.chat.SovereignChatInputBar
import com.red.sovereign.features.media.MediaGalleryDialog
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupMember
import com.red.sovereign.groups.GroupState
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.groups.inviteShareLink
import com.red.sovereign.groups.parseInviteToken
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.VoiceMessageState
import com.red.sovereign.media.VoiceMessageViewModel
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.ChatPollVoteStore
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.AqyalSurfaceRaised
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesMuted
import com.red.sovereign.core.InlinePoll
import com.red.sovereign.core.database.ConversationEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

// Helpers extracted from RedDashboard.kt

val RED_ID_PATTERN = Regex(YounesId.PATTERN)
private val RED_ID_PARTIAL = Regex(YounesId.MENTION_PATTERN)
private val HASHTAG_PARTIAL = Regex("#[\\w\\u0600-\\u06FF]{2,30}")
private val USERNAME_PARTIAL = Regex("@([A-Za-z0-9_.]{1,20})$")
private val HASHTAG_AUTOCOMPLETE = Regex("#([\\w\\u0600-\\u06FF]{1,20})$")
private val EMOJI_CATEGORIES = listOf(
    "الوجوه" to listOf("😀", "😁", "😂", "🤣", "😊", "😍", "😎", "🤔", "😢", "😡", "🥳", "😴"),
    "الإيماءات" to listOf("👍", "👎", "👏", "🙏", "👋", "✌️", "🤝", "💪", "👀", "🫶", "🤲", "👌", "✋", "🤚", "👊", "✊", "🤟", "🫡", "💅", "🙌", "🤗", "🫂"),
    "الحب" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖", "💘", "💯", "💢", "💥", "✨", "⭐", "🌟"),
    "الطبيعة" to listOf("🌹", "🌸", "🌺", "🌻", "🌴", "🌵", "🍀", "🌙", "⭐", "☀️", "⛅", "🌧️", "❄️", "🔥", "💧", "🌊", "🐱", "🐶"),
    "الطعام" to listOf("🍎", "🍇", "🍉", "🍌", "🍒", "🍓", "🍕", "🍔", "🍩", "🍰", "☕", "🍵", "🍯", "🌶️", "🍗", "🍜"),
    "الأنشطة" to listOf("⚽", "🏀", "🎮", "🎲", "🎯", "🎨", "🎭", "🎬", "🎤", "🎧", "🎹", "🥁", "🏆", "🥇"),
    "السفر" to listOf("✈️", "🚗", "🚀", "🚢", "🏠", "🕌", "⛰️", "🏖️", "🗺️", "🧳", "🚲", "🛵", "🚦"),
    "الأشياء" to listOf("💡", "📱", "💻", "⌚", "📷", "🎁", "💰", "🔑", "🔒", "📚", "✏️", "📌", "📎", "🔔")
)
private val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }

internal fun conversationId(first: String, second: String): String {
    if (first.isBlank() || second.isBlank()) return "pending-conversation"
    val canonical = listOf(first, second).sorted().joinToString("|")
    return java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}

private fun groupRoleLabel(role: String) = when (role) { "OWNER" -> "المالك"; "ADMIN" -> "المشرف"; else -> "عضو" }

internal fun messageDisplayText(message: DecryptedMessage): String =
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

private fun resolveRichMessages(source: List<DecryptedMessage>): List<DecryptedMessage> {
    val visible = linkedMapOf<String, DecryptedMessage>()
    source.sortedBy(DecryptedMessage::timestamp).forEach { message ->
        val rich = if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext) else null
        when {
            rich?.action == "DELETE" && rich.deleteOf != null -> visible.remove(rich.deleteOf)
            rich?.action == "EDIT" && rich.editOf != null -> visible[rich.editOf]?.let { original -> visible[rich.editOf] = original.copy(plaintext = RichMessage.encode(RichMessage(text = rich.text, replyTo = RichMessage.decode(original.plaintext)?.replyTo))) }
            else -> visible[message.id] = message
        }
    }
    return visible.values.toList()
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
        diff < 3600000 -> "قبل ${min} د"
        diff < 86400000 -> "قبل ${diff / 3600000} س"
        diff < 172800000 -> "أمس"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}

private fun formatClockTime(timestamp: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(timestamp))

@Composable
private fun Avatar(text: String) = Box(Modifier.size(42.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) { Text(text, color = Color.Black, fontWeight = FontWeight.Black) }

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) = Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }

@Composable
fun MessageInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MessageReactions(
    reactions: List<MessageReactionEntity>,
    currentRedId: String,
    onToggle: (emoji: String) -> Unit
) {
    if (reactions.isEmpty()) return
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
                border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) YounesEmerald else Color.Transparent),
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

@Composable
private fun MessageActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
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
private fun ReactionEmojiBar(onPick: (String) -> Unit) {
    val quick = remember { listOf("??", "??", "??", "??", "??", "??", "??", "??", "??", "??") }
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
                Icon(Icons.Default.CameraAlt, null, tint = YounesEmerald, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("الكاميرا", fontWeight = FontWeight.Medium)
                Text("التقط صورة أو فيديو", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onGallery(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, tint = AqyalCyanGlow, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("المعرض", fontWeight = FontWeight.Medium)
                Text("اختر من صورك وفيديوهاتك", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onDocument(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = AqyalGold, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("ملف", fontWeight = FontWeight.Medium)
                Text("PDF والمستندات الأخرى", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun GroupAvatar(group: Group, groups: GroupViewModel) {
    LaunchedEffect(group.avatarUrl) { groups.loadAvatar(group) }
    val image = groups.avatars[group.id]
    if (image != null) Image(image, group.name, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Avatar(group.name.take(1))
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * 🇾🇪 YOUNES Sovereign — شاشة المحادثات (المركز)
 * تجمع المحادثات الخاصة والمجموعات: بحث فوري، فلاتر،
 * شارات غير المقروء، مسودات، وأرشفة — بتصميم عربي كامل.
 * تُعرض من RedDashboard.kt عند فتح التبويب.
 */
@Composable
fun ChatHubScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    safety: SafetyViewModel,
    attachments: AttachmentViewModel,
    voiceMessages: VoiceMessageViewModel,
    showGroups: Boolean,
    deepLinkSender: String? = null,
    deepLinkConversation: String? = null,
    initialGroupId: String? = null,
    onManageGroup: (String) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onConversationOpen: (Boolean) -> Unit = {},
    onGroupChatClosed: () -> Unit = {},
    /** إشارة إغلاق خارجية من النظام (زر الرجوع): تُغلق الطبقات من الداخل للخارج. */
    closeSignal: Int = 0
) {
    LaunchedEffect(directory.contacts.size) { directory.refreshPresence() }
    val tab = if (showGroups) 1 else 0
    var target by remember { mutableStateOf("") }
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
    var groupConversationId by remember(initialGroupId) { mutableStateOf(initialGroupId) }
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
    // الاستطلاعات المغلقة محلياً (تُخفى فور الإغلاق دون انتظار الخادم).
    val locallyClosedPolls = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val blockedIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { blockedIds.clear(); blockedIds.addAll(directory.blocked) }
    var groupMessageText by remember { mutableStateOf("") }
    var groupEditingMessageId by remember { mutableStateOf<String?>(null) }
    var groupDisappearingMs by remember { mutableStateOf<Long?>(null) }
    var selectedGroupMember by remember { mutableStateOf<GroupMember?>(null) }
    var deleteGroupId by remember { mutableStateOf<String?>(null) }
    var memberRedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var showEditGroupInfo by remember { mutableStateOf(false) }
    var editGroupName by remember { mutableStateOf("") }
    var editGroupDesc by remember { mutableStateOf("") }
    val decrypted = remember { mutableStateListOf<DecryptedMessage>() }
    // دمج الاستطلاعات المغلقة: من نص الرسالة المشفرة (حيث pollId مع isClosed=true) + المحلية.
    val closedPollIds = remember(decrypted.size, locallyClosedPolls.size) {
        deriveClosedPollIds(decrypted) + locallyClosedPolls.keys
    }
    val context = LocalContext.current
    val pinApi = remember(context) {
        val tokens = com.red.sovereign.auth.TokenStore(context)
        val client = com.red.sovereign.auth.AuthorizedApiClient(tokens)
        PinsApi(client)
    }
    var messageInfo by remember { mutableStateOf<DecryptedMessage?>(null) }
    val editedMessageIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // تحديث المثبتات عند الطلب (GROUP_SYNC بدل polling 30s)
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        val gid = groupConversationId ?: return@LaunchedEffect
        refreshGroupPins(pinApi, gid, groupPinnedMessages, decrypted)
        com.red.sovereign.core.GroupSyncBus.events.collect { changedId ->
            if (changedId == groupConversationId) refreshGroupPins(pinApi, changedId, groupPinnedMessages, decrypted)
        }
    }
    // LEGENDARY: علامة قراءة تلقائية للمجموعة (أساس "من قرأ" — بأعلى تسلسل + debounce)
    androidx.compose.runtime.LaunchedEffect(groupConversationId, decrypted.size) {
        val gid = groupConversationId ?: return@LaunchedEffect
        val grp = groups.groups.firstOrNull { it.id == gid } ?: return@LaunchedEffect
        val maxSeq = decrypted.asSequence()
            .filter { it.conversationId == gid && !it.outgoing }
            .maxOfOrNull { it.sequence } ?: return@LaunchedEffect
        if (maxSeq > 0) {
            kotlinx.coroutines.delay(1200)
            if (groupConversationId == gid) groups.markGroupRead(grp, maxSeq)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repository = remember { com.red.sovereign.core.database.LocalRepository(context) }
    val localMessages = remember { com.red.sovereign.core.MessageStore(context) }
    var groupMuted by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(groupConversationId) {
        groupMuted = if (groupConversationId != null) {
            localMessages.conversationPreference(groupConversationId!!).third > System.currentTimeMillis()
        } else false
    }
    androidx.compose.runtime.LaunchedEffect(groupConversationId, decrypted.size) {
        if (groupConversationId != null) {
            decrypted.filter { it.type == "RICH_TEXT" && it.conversationId == groupConversationId }.forEach { item ->
                RichMessage.decode(item.plaintext)?.let { rich ->
                    if (rich.action == "POLL_VOTE" && rich.pollVoteOf != null) {
                        PollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                        // تثبيت التصويت (RichTextMessage) في مخزن ChatPollVoteStore ليعكس على نتائج الاستطلاع.
                        ChatPollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                    }
                }
            }
        }
    }
    val conversations by repository.getActiveConversations().collectAsState(initial = emptyList())
    androidx.compose.runtime.LaunchedEffect(conversations.size, target) {
        if (target.isBlank()) {
            chatDrafts.clear()
            withContext(Dispatchers.IO) {
                conversations.forEach { conv -> repository.getDraft(conv.id)?.let { if (it.text.isNotBlank()) chatDrafts[conv.id] = it.text } }
            }
        }
    }
    val reactionsByMessage = remember { androidx.compose.runtime.mutableStateMapOf<String, List<MessageReactionEntity>>() }

    val typingUsers = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        TypingEventBus.events.collect { event ->
            if (SettingsRuntime.current.typingIndicators) {
                if (event.isTyping) typingUsers[event.userId] = System.currentTimeMillis() + 5000L
                else typingUsers.remove(event.userId)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        MessageAckBus.acks.collect { ack ->
            val index = decrypted.indexOfFirst { it.id == ack.messageId }
            if (index != -1) {
                decrypted[index] = decrypted[index].copy(status = ack.status)
            }
        }
    }
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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        DecryptedMessageBus.messages.collect { item ->
            if (item.type == "RICH_TEXT") {
                RichMessage.decode(item.plaintext)?.let { rich ->
                    if (rich.action == "POLL_VOTE" && rich.pollVoteOf != null) {
                        PollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                        ChatPollVoteStore.record(rich.pollVoteOf!!, item.senderRedId, rich.pollVoteOption)
                    }
                }
            }
        }
    }
    // LEGENDARY FIX: تنظيف مؤشر الكتابة بعد انتهاء المهلة + إلغاء الحلقة عند فراغ القائمة (منع تسرب البطارية)
    androidx.compose.runtime.LaunchedEffect(typingUsers.size) {
        if (typingUsers.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(2000)
            val now = System.currentTimeMillis()
            val removed = typingUsers.entries.removeAll { it.value < now }
            if (typingUsers.isEmpty()) break
            if (removed) continue
        }
    }

    // LEGENDARY FIX: throttle الكتابة 4s + heartbeat عند المسح حتى يعرف الطرف أنك توقفت (منع بقاء Service يبث + stop بعد 3s)
    var lastTypingSentAt by remember { mutableStateOf(0L) }
    var lastTypingValue by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(messageText) {
        if (!target.matches(RED_ID_PATTERN) || !SettingsRuntime.current.typingIndicators) return@LaunchedEffect
        val now = android.os.SystemClock.uptimeMillis()
        val wantTyping = messageText.isNotEmpty()
        // إرسال فقط عند تغير الحالة أو مرور 4s لمنع إغراق الشبكة بالتكرار
        if (wantTyping == lastTypingValue && now - lastTypingSentAt < 4000) return@LaunchedEffect
        lastTypingValue = wantTyping
        lastTypingSentAt = now
        val typingConversation = conversationId(account.redId, target)
        val intent = Intent(context, com.red.sovereign.core.RedConnectionService::class.java).apply {
            action = com.red.sovereign.core.RedConnectionService.ACTION_SEND_TYPING
            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_TARGET, target)
            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_CONVERSATION, typingConversation)
            putExtra(com.red.sovereign.core.RedConnectionService.EXTRA_IS_TYPING, wantTyping)
        }
        context.startService(intent)
    }

    androidx.compose.runtime.DisposableEffect(target, groupConversationId) {
        // LEGENDARY FIX: حفظ المسودة بـscope محلي يُلغى عند المغادرة بدل GlobalScope المسرب
        val draftScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        onDispose {
            if (messageText.isNotBlank()) {
                val draftConvId = groupConversationId ?: target.takeIf { it.isNotBlank() }?.let { conversationId(account.redId, it) }
                if (draftConvId != null) {
                    val snapshot = messageText
                    draftScope.launch {
                        runCatching { repository.saveDraft(draftConvId, snapshot) }
                    }
                }
            }
            draftScope.cancel()
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
    // زر الرجوع يغلق الطبقات من الداخل للخارج (closeSignal): آخر شيت مفتوح أولاً ثم المحادثة نفسها
    LaunchedEffect(closeSignal) {
        if (closeSignal == 0) return@LaunchedEffect
        if (showGroups) { groupConversationId = null; return@LaunchedEffect }
        when {
            showEmoji -> showEmoji = false
            showStickers -> showStickers = false
            showAttachmentSheet -> showAttachmentSheet = false
            showMediaGallery -> showMediaGallery = false
            showMessageSearch -> showMessageSearch = false
            showDirectory -> showDirectory = false
            selectedChatMessage != null -> selectedChatMessage = null
            replyToMessage != null -> replyToMessage = null
            editingMessageId != null -> editingMessageId = null
            else -> target = ""
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showSafetyScanner = granted
        if (!granted) safety.cameraPermissionDenied()
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cleanTarget = YounesId.normalizeInput(target).ifBlank { target }
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
        val cleanTarget = YounesId.normalizeInput(target).ifBlank { target }
        val video = pendingCallVideo
        pendingCallVideo = false
        if (audioGranted && cameraGranted && cleanTarget.isNotBlank()) YounesCallService.start(context, cleanTarget, video)
    }
    var pendingGroupVideo by remember { mutableStateOf(false) }
    var pendingGroupRing by remember { mutableStateOf(true) }
    val groupCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingGroupVideo || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val group = groups.groups.firstOrNull { it.id == groupConversationId }
        val video = pendingGroupVideo
        val ring = pendingGroupRing
        pendingGroupVideo = false
        pendingGroupRing = true
        if (audioGranted && cameraGranted && group != null) {
            val memberCount = group.members.size
            val cap = if (video) 6 else 12
            if (memberCount > cap && ring) {
                android.widget.Toast.makeText(context, "المجموعة ممتلئة (للمكالمات) — الحد هو $cap مشارك (لديك $memberCount). جرب مكالمة صوتية بعدد أقل.", android.widget.Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            ConferenceService.join(context, group.id, account.redId, video, inviteRedIds = if (ring) group.members.map { it.redId } else emptyList())

            val title = if (ring) {
                if (video) "مكالمة فيديو جماعية 📹" else "مكالمة صوتية جماعية 📞"
            } else {
                if (video) "انضم للفيديو 📹" else "انضم للصوت 📞"
            }
            val rich = RichMessage(
                action = "CALL_STARTED",
                text = "📞 $title. اضغط للانضمام — الدعوة وصلت للجميع."
            )
            RedConnectionService.sendGroupRichText(context, group, rich)
        }
    }
    LaunchedEffect(Unit) { DecryptedMessageBus.messages.collect { item ->
        decrypted.add(item)
        if (item.type == "RICH_TEXT") {
            RichMessage.decode(item.plaintext)?.let { rich ->
                if (rich.action == "EDIT" && rich.editOf != null) editedMessageIds[rich.editOf!!] = true
            }
        }
        if (!item.outgoing) {
            if (item.conversationId.length > 32) {
                if (item.conversationId != groupConversationId) groupUnread[item.conversationId] = (groupUnread[item.conversationId] ?: 0) + 1
            } else {
                if (item.conversationId != conversationId(account.redId, target)) chatUnread[item.conversationId] = (chatUnread[item.conversationId] ?: 0) + 1
            }
        }
        if (!item.outgoing && SettingsRuntime.current.readReceipts) RedConnectionService.markRead(context, item.id, item.sequence)
    } }
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
                            Text("طلبات التواصل الواردة", color = AqyalGold, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${directory.requests.size}", color = Color.White, modifier = Modifier.background(AqyalGold, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                        directory.requests.forEach { request ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(request.requester.displayName.take(1))
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(request.requester.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("@${request.requester.username} • ${request.requester.redId.take(12)}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton({ directory.resolve(request, false) }, Modifier.heightIn(min = 48.dp)) { Text("رفض", color = YounesMuted) }
                                    Button({ directory.resolve(request, true) }, Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) { Text("قبول", color = Color(0xFF002118)) }
                                }
                            }
                        }
                    }
                }
            }
            if (directory.state is DirectoryState.Message) {
                Card(colors = CardDefaults.cardColors(containerColor = YounesEmerald.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Text((directory.state as DirectoryState.Message).text, color = YounesEmerald, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
            if (target.isBlank()) {
                if (directory.contacts.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("جهات الاتصال", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text("${directory.contacts.size}", color = Color.White, fontSize = 12.sp, modifier = Modifier.background(AqyalCyanGlow, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                        Spacer(Modifier.weight(1f))
                        TextButton({ showDirectory = true }) { Text("عرض الكل +", color = AqyalGold, fontSize = 12.sp) }
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
                                    Text(if (online) "متصل" else "@${person.username}", color = if (online) YounesEmerald else AqyalCyanGlow, maxLines = 1, fontSize = 10.sp)
                                    IconButton({ selectedContact = person }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(48.dp)) { Icon(Icons.Default.MoreVert, "المزيد من الخيارات", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth().clickable { showDirectory = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = YounesEmerald)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("دليل جهات الاتصال", fontWeight = FontWeight.SemiBold); Text("ابحث بالاسم أو المعرف للتواصل", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                val activePerson = directory.contacts.find { it.redId == target }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ target = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة لقائمة المحادثات") }
                    Avatar((activePerson?.displayName ?: target).take(1))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(activePerson?.displayName ?: target, fontWeight = FontWeight.SemiBold)
                        Text(activePerson?.let { val ls = directory.lastSeenLabel(it.redId); ls ?: "@${it.username} • ${it.redId}" } ?: target, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    com.red.sovereign.ui.PrivateChatCallActions(
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
            }
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
            // LEGENDARY: مجلدات محلية — تُحسب قبل LazyColumn (remember ممنوع داخل DSL)
            // SnapshotStateList حتى يعيد التركيب عند الإنشاء/الحذف/القفل من الحوار.
            val folders = remember {
                androidx.compose.runtime.mutableStateListOf(
                    *com.red.sovereign.core.PersonalChatFoldersStore.load(context).toTypedArray()
                )
            }
            var selectedFolderId by remember { mutableStateOf<String?>(null) }
            val folderPeers = remember(selectedFolderId, folders) {
                com.red.sovereign.core.PersonalChatFoldersStore.filterPeerIds(
                    folders, selectedFolderId, com.red.sovereign.core.UnlockedFolders.ids.toSet())
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
                                (!chatUnreadFilter || (chatUnread[conv.id] ?: 0) > 0) &&
                                (folderPeers == null || conv.peerId in folderPeers)
                        }
                        .sortedWith(
                            compareByDescending<ConversationEntity> { it.pinned }
                                .thenByDescending { it.lastMessageTimestamp }
                        )
                    if (allConvos.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                chatSearchQuery, { chatSearchQuery = it }, Modifier.fillMaxWidth(),
                                placeholder = { Text("ابحث في المحادثات…") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, shape = RoundedCornerShape(14.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !chatUnreadFilter, onClick = { chatUnreadFilter = false }, label = { Text("الكل") })
                                FilterChip(selected = chatUnreadFilter, onClick = { chatUnreadFilter = true }, label = { Text("غير المقروء") })
                            }
                            // رسائلي — محادثة الذات E2EE
                            Card(Modifier.fillMaxWidth().clickable { target = account.redId },
                                colors = CardDefaults.cardColors(containerColor = YounesEmerald.copy(alpha = 0.12f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bookmark, null, tint = YounesEmerald)
                                    Spacer(Modifier.width(8.dp))
                                    Text("رسائلي — ملاحظات ومرفقات خاصة", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                            // المجلدات: filter chips + زر إدارة (إنشاء/قفل/حذف)
                            run {
                                var showFolderDialog by remember { mutableStateOf(false) }
                                var newFolderName by remember { mutableStateOf("") }
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(selected = selectedFolderId == null,
                                            onClick = { selectedFolderId = null }, label = { Text("الكل") })
                                    }
                                    items(folders, key = { it.id }) { f ->
                                        FilterChip(
                                            selected = selectedFolderId == f.id,
                                            onClick = { selectedFolderId = if (selectedFolderId == f.id) null else f.id },
                                            label = { Text((if (f.locked) "🔒 " else "") + f.name) }
                                        )
                                    }
                                    item {
                                        FilterChip(selected = false, onClick = { showFolderDialog = true },
                                            label = { Text("+ مجلد") })
                                    }
                                }
                                if (showFolderDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showFolderDialog = false; newFolderName = "" },
                                        title = { Text("المجلدات") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = newFolderName,
                                                    onValueChange = { newFolderName = it.take(32) },
                                                    label = { Text("اسم مجلد جديد") }, singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                folders.forEach { f ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text((if (f.locked) "🔒 " else "") + f.name + " (${f.peerIds.size})",
                                                            modifier = Modifier.weight(1f), fontSize = 14.sp)
                                                        TextButton(onClick = {
                                                            com.red.sovereign.core.PersonalChatFoldersStore.setLocked(
                                                                context, f.id, !f.locked)
                                                            folders.clear()
                                                            folders.addAll(com.red.sovereign.core.PersonalChatFoldersStore.load(context))
                                                        }) { Text(if (f.locked) "فتح" else "قفل", fontSize = 12.sp) }
                                                        TextButton(onClick = {
                                                            com.red.sovereign.core.PersonalChatFoldersStore.delete(context, f.id)
                                                            folders.clear()
                                                            folders.addAll(com.red.sovereign.core.PersonalChatFoldersStore.load(context))
                                                            if (selectedFolderId == f.id) selectedFolderId = null
                                                        }) { Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                if (newFolderName.trim().length >= 2) {
                                                    com.red.sovereign.core.PersonalChatFoldersStore.create(context, newFolderName.trim())
                                                    folders.clear()
                                                    folders.addAll(com.red.sovereign.core.PersonalChatFoldersStore.load(context))
                                                    newFolderName = ""
                                                }
                                            }, enabled = newFolderName.trim().length >= 2) { Text("إنشاء") }
                                        },
                                        dismissButton = { TextButton({ showFolderDialog = false; newFolderName = "" }) { Text("إغلاق") } }
                                    )
                                }
                            }
                        }
                    }
                    if (filteredConvos.isEmpty() && allConvos.isNotEmpty()) item {
                        Text("لا توجد نتائج مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    }
                    items(filteredConvos, key = { it.id }) { conv ->
                        val unread = chatUnread[conv.id] ?: 0
                        Card(Modifier.fillMaxWidth().clickable { chatUnread.remove(conv.id); target = conv.peerId }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                                        if (conv.pinned) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.Star, "مثبتة", tint = Color(0xFFF5C842), modifier = Modifier.size(14.dp)) }
                                        if (conv.mutedUntil > System.currentTimeMillis()) { Spacer(Modifier.width(3.dp)); Icon(Icons.Default.NotificationsOff, "مكتومة", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                                    }
                                    val draft = chatDrafts[conv.id]
                                    Text(
                                        if (draft != null) "مسودة: $draft" else (conv.lastMessageText ?: "ابدأ المحادثة"),
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
                    Text("هذه بداية المحادثة. الرسائل مشفرة ولا تُقرأ إلا على جهازك.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                itemsIndexed(conversationMessages, key = { _, it -> it.id }) { index, item ->
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
                                    "FILE", "IMAGE", "VIDEO", "AUDIO" -> com.red.sovereign.ui.AttachmentMessage(item, attachments)
                                    "VOICE" -> com.red.sovereign.ui.VoiceMessage(item, attachments)
                                    "STICKER" -> com.red.sovereign.ui.StickerMessage(item, attachments)
                                    "RICH_TEXT" -> com.red.sovereign.ui.RichTextMessage(item, conversationMessages)
                                    else -> Text(item.plaintext.toString(Charsets.UTF_8), color = if (item.outgoing) Color(0xFF001B14) else Color.White, fontSize = 16.sp)
                                }
                                MessageReactions(
                                    reactions = reactionsByMessage[item.id].orEmpty(),
                                    currentRedId = account.redId,
                                    onToggle = { emoji ->
                                        val mine = reactionsByMessage[item.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                        if (mine) RedConnectionService.removeReaction(context, target, conversation, item.id)
                                        else RedConnectionService.sendReaction(context, target, conversation, item.id, emoji)
                                        val current = reactionsByMessage[item.id].orEmpty()
                                        val withoutMine = current.filterNot { it.senderId == account.redId }
                                        reactionsByMessage[item.id] = if (mine) withoutMine else withoutMine + MessageReactionEntity(item.id, conversation, account.redId, emoji, System.currentTimeMillis())
                                    }
                                )
                                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(formatClockTime(item.timestamp), fontSize = 10.sp, color = if (item.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (editedMessageIds.containsKey(item.id)) Text("معدّلة", fontSize = 10.sp)
                                    if (item.outgoing) {
                                        val ticks = when (item.status) {
                                            "READ" -> "✓✓"
                                            "DELIVERED" -> "✓✓"
                                            else -> "✓"
                                        }
                                        Text(ticks, color = if (item.status == "READ") AqyalCyanGlow else Color(0x99001B14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
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
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "⭐", sticker.name)
                                )
                                RedConnectionService.sendPayload(context, target, conversation, "STICKER", payload.toByteArray(Charsets.UTF_8))
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
                                        Text(" • ${person.displayName}", color = YounesMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                val hashtagQuery = HASHTAG_AUTOCOMPLETE.find(messageText)?.groupValues?.get(1)
                if (hashtagQuery != null) {
                        val popular = listOf("عام", "هام", "اجتماع", "عاجل", "فكرة").filter { it.contains(hashtagQuery, ignoreCase = true) }.take(3)
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
                    onToggleDisappearing = {
                        disappearingDurationMs = if (disappearingDurationMs == null) 86400000L else null
                    },
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
                            voiceMessages.stopAndPreview(target, conversation)
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
            // AUTO-FIX (groups visibility): the groups tab previously never refreshed its list —
            // a freshly created group only appeared after an app restart.
            androidx.compose.runtime.LaunchedEffect(Unit) { groups.load() }
            val openGroup = groups.groups.firstOrNull { it.id == groupConversationId }
            if (openGroup == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onCreateGroup, Modifier.weight(1f)) { Icon(Icons.Default.Add, "إنشاء مجموعة"); Text(" إنشاء") }
                    OutlinedButton({ showJoinGroup = true }, Modifier.weight(1f)) { Text("الانضمام برمز") }
                }
                when {
                    groups.state == GroupState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(30.dp))
                    groups.state is GroupState.Error -> EmptyState(Icons.Default.Groups, "تعذر تحميل المجموعات", (groups.state as GroupState.Error).message)
                    groups.groups.isEmpty() -> EmptyState(Icons.Default.Groups, "لا توجد مجموعات", "أنشئ مجموعة جديدة أو انضم عبر رمز من صديق.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                        items(groups.groups, key = { it.id }) { group ->
                            val lastGroupMsg = decrypted.filter { it.conversationId == group.id }.maxByOrNull { it.timestamp }
                            val unread = groupUnread[group.id] ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { groupUnread.remove(group.id); groupConversationId = group.id }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GroupAvatar(group, groups)

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

                                        if (lastGroupMsg != null) {
                                            Text(
                                                text = relativeTime(lastGroupMsg.timestamp),
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
                                                (if (msg.outgoing) "أنت: " else "@" + msg.senderRedId.take(8) + ": ") + t
                                            } ?: group.description.orEmpty().ifBlank { "مجموعة مشفرة عبر Sender Keys" },
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
                                    Icon(Icons.Default.MoreVert, "خيارات المجموعة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                val liveRoomId = when (val live = ConferenceRuntime.state) {
                    is ConferenceUiState.Active -> live.roomId
                    is ConferenceUiState.Connecting -> live.roomId
                    is ConferenceUiState.Incoming -> live.roomId
                    else -> ""
                }
                val groupSessionLive = liveRoomId == openGroup.id
                val groupSessionVideo = ConferenceRuntime.isVideoEnabled && groupSessionLive
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ groupConversationId = null; onGroupChatClosed() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "العودة للمجموعات") }
                        GroupAvatar(openGroup, groups)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(openGroup.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (groupSessionLive) (if (groupSessionVideo) "اجتماع فيديو نشط" else "مساحة صوتية نشطة") else "${openGroup.members.size} عضو • مشفرة",
                                color = if (groupSessionLive) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        com.red.sovereign.ui.GroupChatCallActionsLegacy(
                            spaceLive = groupSessionLive && !groupSessionVideo,
                            meetingLive = groupSessionLive && groupSessionVideo,
                            onVideoCall = { pendingGroupVideo = true; pendingGroupRing = true; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) },
                            onVoiceCall = { pendingGroupVideo = false; pendingGroupRing = true; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            onSpace = { pendingGroupVideo = false; pendingGroupRing = false; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            onMeeting = { pendingGroupVideo = true; pendingGroupRing = false; groupCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) },
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
                if (groupSessionLive) {
                    com.red.sovereign.ui.GroupLiveSessionBanner(
                        isVideo = groupSessionVideo,
                        inSession = ConferenceRuntime.state is ConferenceUiState.Active || ConferenceRuntime.state is ConferenceUiState.Connecting,
                        onJoinOrReturn = {
                            if (ConferenceRuntime.state is ConferenceUiState.Incoming) {
                                ConferenceService.join(context, openGroup.id, account.redId, groupSessionVideo, asHost = false)
                            }
                        }
                    )
                }
                val groupMessages = applyGroupPollClosedState(
                    resolveRichMessages(decrypted.filter { it.conversationId == openGroup.id && (it.type == "GROUP_MESSAGE" || it.type == "RICH_TEXT") }),
                    closedPollIds
                )
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
                                        Text(" الرسائل المثبتة (${groupPinnedMessages.size})", color = AqyalGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    groupPinnedMessages.values.forEach { pm ->
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(messageDisplayText(pm), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                            IconButton({
                                                groupPinnedMessages.remove(pm.id)
                                                scope.launch {
                                                    when (pinApi.unpin(pm.id)) {
                                                        is com.red.sovereign.auth.ApiResult.Error -> {
                                                            groupPinnedMessages[pm.id] = pm
                                                            android.widget.Toast.makeText(context, "تعذر إلغاء التثبيت — تحقق من الاتصال", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        else -> Unit
                                                    }
                                                }
                                            }) { Icon(Icons.Default.Close, "إلغاء التثبيت", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (groupMessages.isEmpty()) item { Text("مجموعة مشفرة عبر Sender Keys. ابدأ المحادثة برسالة ترحب بباقي الأعضاء.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
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
                                    val nameColors = listOf(
                                        Color(0xFF6FD8B0), Color(0xFF7FB5E0), Color(0xFFF0C674), Color(0xFFC9A7E8),
                                        Color(0xFF8FC7E8), Color(0xFFB5D8A0), Color(0xFFE0B8A0)
                                    )
                                    val colorIndex = kotlin.math.abs(message.senderRedId.hashCode()) % nameColors.size
                                    Text(message.senderRedId.take(12) + "...", color = nameColors[colorIndex], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                when (message.type) {
                                    "RICH_TEXT" -> com.red.sovereign.ui.RichTextMessage(
                                        message, groupMessages,
                                        myRedId = account.redId,
                                        onPollVote = { pollId, optionIndex ->
                                            // تثبيت التصويت محلياً أولاً لاستجابة فورية ثم بث الحدث للجميع.
                                            PollVoteStore.record(pollId, account.redId, optionIndex)
                                            ChatPollVoteStore.record(pollId, account.redId, optionIndex)
                                            val vote = RichMessage(action = "POLL_VOTE", pollVoteOf = pollId, pollVoteOption = optionIndex)
                                            RedConnectionService.sendGroupRichText(context, openGroup, vote)
                                        }
                                    )
                                    "FILE", "IMAGE", "VIDEO", "AUDIO" -> com.red.sovereign.ui.AttachmentMessage(message, attachments)
                                    "VOICE" -> com.red.sovereign.ui.VoiceMessage(message, attachments)
                                    "STICKER" -> com.red.sovereign.ui.StickerMessage(message, attachments)
                                    "GROUP_MESSAGE" -> {
                                        val text = message.plaintext.toString(Charsets.UTF_8)
                                        when {
                                            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.VoiceManifest>(text) }.isSuccess -> com.red.sovereign.ui.VoiceMessage(message, attachments)
                                            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.AttachmentManifest>(text) }.isSuccess -> com.red.sovereign.ui.AttachmentMessage(message, attachments)
                                            runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(text) }.isSuccess -> com.red.sovereign.ui.StickerMessage(message, attachments)
                                            else -> Text(text, color = if (message.outgoing) Color(0xFF002118) else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                        }
                                    }
                                    else -> Text(message.plaintext.toString(Charsets.UTF_8), color = if (message.outgoing) Color(0xFF002118) else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                }
                                // إغلاق الاستطلاع: بث نسخة مغلقة منه (يحمل نفس المعرف) للجميع.
                                // قارئ الرسائل E2EE يستخرج الحالة من pollId مع isClosed=true بدل أي تخزين.
                                if (message.type == "RICH_TEXT" && message.outgoing) {
                                    val openPoll = RichMessage.decode(message.plaintext)?.poll?.takeIf { !it.isClosed }
                                    if (openPoll != null && openPoll.pollId.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                locallyClosedPolls[openPoll.pollId] = true
                                                val closed = RichMessage(
                                                    text = "تم إغلاق الاستطلاع: ${openPoll.question}",
                                                    poll = openPoll.copy(isClosed = true)
                                                )
                                                RedConnectionService.sendGroupRichText(context, openGroup, closed)
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) { Text("إغلاق الاستطلاع", color = AqyalGold, fontSize = 12.sp) }
                                    }
                                }
                                MessageReactions(
                                    reactions = reactionsByMessage[message.id].orEmpty(),
                                    currentRedId = account.redId,
                                    onToggle = { emoji ->
                                        val mine = reactionsByMessage[message.id].orEmpty().any { it.emoji == emoji && it.senderId == account.redId }
                                        if (mine) RedConnectionService.removeGroupReaction(context, openGroup, message.id)
                                        else RedConnectionService.sendGroupReaction(context, openGroup, message.id, emoji)
                                        val current = reactionsByMessage[message.id].orEmpty()
                                        val withoutMine = current.filterNot { it.senderId == account.redId }
                                        reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + MessageReactionEntity(message.id, openGroup.id, account.redId, emoji, System.currentTimeMillis())
                                    }
                                )
                                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(formatClockTime(message.timestamp), fontSize = 10.sp, color = if (message.outgoing) Color(0x99001B14) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (editedMessageIds.containsKey(message.id)) Text("معدّلة", fontSize = 10.sp)
                                    if (message.outgoing) {
                                        val ticks = when (message.status) {
                                            "READ" -> "✓✓"
                                            "DELIVERED" -> "✓✓"
                                            else -> "✓"
                                        }
                                        Text(ticks, color = if (message.status == "READ") AqyalCyanGlow else Color(0x99001B14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
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
                                    com.red.sovereign.media.StickerMessagePayload(sticker.mediaKey, sticker.emojiTags.firstOrNull() ?: "??", sticker.name)
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

                SovereignChatInputBar(
                    messageText = groupMessageText,
                    onMessageChange = { groupMessageText = it },
                    onSend = {
                        // LEGENDARY: منع @all لغير الأدمن في الكبيرة (>32) قبل الإرسال (واتساب 8/2026)
                        val myRole = openGroup.members.firstOrNull { it.redId == account.redId }?.role
                        if (com.red.sovereign.core.GroupMentions.containsAllMention(groupMessageText) &&
                            !com.red.sovereign.core.GroupMentions.canUseAll(openGroup.members.size, account.redId)) {
                            android.widget.Toast.makeText(context,
                                "@all للمشرفين فقط في المجموعات الكبيرة", android.widget.Toast.LENGTH_LONG).show()
                            return@SovereignChatInputBar
                        }
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
                        groupMessageText = ""; groupReplyToMessage = null; groupEditingMessageId = null; showGroupEmoji = false; groupDisappearingMs = null
                    },
                    replyPreviewText = groupReplyToMessage?.let { "الرد على ${if (it.outgoing) "نفسك" else it.senderRedId.take(12)}: " + messageDisplayText(it) },
                    editingPreviewText = groupEditingMessageId?.let { id -> groupMessages.firstOrNull { it.id == id }?.let { messageDisplayText(it) } },
                    onCancelReplyOrEdit = { groupReplyToMessage = null; groupEditingMessageId = null },
                    disappearingMs = groupDisappearingMs,
                    onToggleDisappearing = {
                        groupDisappearingMs = if (groupDisappearingMs == null) 86400000L else null
                    },
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
                    onVoiceRelease = {
                        if (voiceMessages.state is VoiceMessageState.Recording) {
                            voiceMessages.stopAndPreview()
                        }
                    },
                    onStopAndPreview = { voiceMessages.stopAndPreview() },
                    onVoiceClick = {
                        if (voiceMessages.state is VoiceMessageState.Recording) {
                            voiceMessages.stopAndPreview()
                        } else if (voiceMessages.state is VoiceMessageState.Preview) {
                            voiceMessages.stopAndSendToGroup(openGroup)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            voiceMessages.startForGroup(openGroup)
                        } else {
                            groupVoiceMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    placeholderText = if (groupEditingMessageId != null) "تحرير الرسالة…" else if (groupReplyToMessage != null) "الرد على رسالة…" else "اكتب رسالة مشفرة…"
                )
            }
        }
    }
    when (val safetyState = safety.state) {
        SafetyState.Closed -> Unit
        is SafetyState.Loading -> AlertDialog(onDismissRequest = safety::close, title = { Text("فحص الأمان") }, text = { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }, confirmButton = { TextButton(safety::close) { Text("إغلاق") } })
        is SafetyState.Error -> AlertDialog(onDismissRequest = safety::close, title = { Text("خطأ الفحص") }, text = { Text(safetyState.message) }, confirmButton = { TextButton(safety::close) { Text("إغلاق") } })
        is SafetyState.Ready -> if (showSafetyScanner) AlertDialog(
            onDismissRequest = { showSafetyScanner = false },
            title = { Text("تحقق من رمز الأمان") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp))) {
                        SafetyQrScanner(onCode = { safety.verifyScanned(it); showSafetyScanner = false })
                    }
                    Text("وجّه الكاميرا نحو رمز الطرف لقراءة بصمته والتحقق من التطابق.", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton({ showSafetyScanner = false }) { Text("إغلاق") } }
        ) else AlertDialog(
            onDismissRequest = { safety.clearScanError(); safety.close() },
            title = { Text(if (safetyState.verified) "تم التحقق بنجاح" else "تحقق من البصمة") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(safetyState.qr, "رمز QR للتحقق", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Text(safetyState.number, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = AqyalGold)
                Text("الجهاز ${safetyState.deviceId} • ${safetyState.fingerprint.chunked(8).joinToString(" ")}", fontSize = 12.sp, color = YounesMuted, textAlign = TextAlign.Center)
                safetyState.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center) }
                Text("قارن هذه الأرقام مع ما لدى الطرف عبر قناة موثوقة قبل المتابعة.", fontSize = 11.sp, textAlign = TextAlign.Center)
                if (!safetyState.verified) OutlinedButton({
                    safety.clearScanError()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showSafetyScanner = true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Text(" مسح رمز الطرف") }
            } },
            confirmButton = { if (!safetyState.verified) Button(safety::markVerified) { Text("تأكيد التحقق من البصمة") } else TextButton(safety::close) { Text("تم") } },
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
                Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.outgoing) "أنت" else (if (isGroupMsg) message.senderRedId.take(12) else "الطرف"), color = YounesEmerald, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(messageDisplayText(message), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }

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
                    val current = reactionsByMessage[message.id].orEmpty()
                    val withoutMine = current.filterNot { it.senderId == account.redId }
                    reactionsByMessage[message.id] = if (mine) withoutMine else withoutMine + MessageReactionEntity(message.id, convId, account.redId, emoji, System.currentTimeMillis())
                    selectedChatMessage = null
                })

                MessageActionRow(Icons.Default.Quickreply, "رد", "الرد على هذه الرسالة") {
                    if (isGroupMsg) groupReplyToMessage = message else replyToMessage = message
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Forward, "إعادة توجيه", "إرسالها لجهة أخرى") {
                    pendingForwardMessage = message; showDirectory = true; selectedChatMessage = null
                }
                val messageTextForAction = messageDisplayText(message)
                if (messageTextForAction.isNotBlank()) {
                    MessageActionRow(Icons.Default.ContentCopy, "نسخ", "نسخ النص") {
                        val ctx = context
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("رسالة", messageTextForAction))
                        selectedChatMessage = null
                    }
                    MessageActionRow(Icons.Default.Share, "مشاركة", "أرسل عبر تطبيق آخر") {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, messageTextForAction)
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الرسالة").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing && message.type == "RICH_TEXT" && payload?.action == "MESSAGE") {
                    MessageActionRow(Icons.Default.Edit, "تحرير", "تعديل هذه الرسالة") {
                        if (isGroupMsg) {
                            groupEditingMessageId = message.id; groupMessageText = payload.text
                        } else {
                            editingMessageId = message.id; messageText = payload.text
                        }
                        selectedChatMessage = null
                    }
                }
                if (message.outgoing) {
                    MessageActionRow(Icons.Default.Delete, "حذف هذه الرسالة", "إزالة الرسالة من الجميع") {
                        if (isGroupMsg) {
                            val grp = groups.groups.firstOrNull { it.id == message.conversationId }
                            if (grp != null) RedConnectionService.sendGroupRichText(context, grp, RichMessage(action = "DELETE", deleteOf = message.id))
                        } else {
                            RedConnectionService.sendRichText(context, target, message.conversationId, RichMessage(action = "DELETE", deleteOf = message.id))
                        }
                        selectedChatMessage = null
                    }
                }
                MessageActionRow(Icons.Default.Delete, "حذف محلي", "إزالته من جهازك فقط") {
                    scope.launch { repository.deleteLocalMessage(message.id) }
                    reactionsByMessage.remove(message.id)
                    decrypted.removeAll { it.id == message.id }
                    selectedChatMessage = null
                }
                if (isGroupMsg) {
                    MessageActionRow(if (groupPinnedMessages.containsKey(message.id)) Icons.Default.Star else Icons.Default.StarBorder, if (groupPinnedMessages.containsKey(message.id)) "إلغاء التثبيت" else "تثبيت", "يظهر أعلى المحادثة لباقي الأعضاء") {
                        if (groupPinnedMessages.containsKey(message.id)) {
                            groupPinnedMessages.remove(message.id)
                            scope.launch {
                                // rollback عند فشل الخادم — نعيد الرسالة المثبتة محلياً.
                                when (pinApi.unpin(message.id)) {
                                    is com.red.sovereign.auth.ApiResult.Error -> {
                                        groupPinnedMessages[message.id] = message
                                        android.widget.Toast.makeText(context, "تعذر إلغاء التثبيت — تحقق من الاتصال", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    else -> Unit
                                }
                            }
                        } else {
                            groupPinnedMessages[message.id] = message
                            scope.launch {
                                when (pinApi.pin(message.id, groupId = message.conversationId, expiresInSeconds = GROUP_PIN_EXPIRES_SECONDS)) {
                                    is com.red.sovereign.auth.ApiResult.Error -> {
                                        groupPinnedMessages.remove(message.id)
                                        android.widget.Toast.makeText(context, "تعذر التثبيت — تحقق من الاتصال", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        selectedChatMessage = null
                    }
                }
                MessageActionRow(Icons.Default.NotificationsOff, "كتم المحادثة", "كتم لمدة 8 ساعات") {
                    val convId = if (isGroupMsg) message.conversationId else conversationId(account.redId, target)
                    val muted = localMessages.conversationPreference(convId).third > System.currentTimeMillis()
                    localMessages.setConversationPreference(convId, "muted_until", if (muted) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L)
                    selectedChatMessage = null
                }
                MessageActionRow(Icons.Default.Info, "معلومات الرسالة", "التفاصيل وحالة التسليم") {
                    messageInfo = message; selectedChatMessage = null
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("ساعة" to 3_600_000L, "يوم" to 86_400_000L, "أسبوع" to 604_800_000L).forEach { (label, ms) ->
                        OutlinedButton({ disappearingDurationMs = ms; selectedChatMessage = null }, Modifier.weight(1f)) { Text(label, fontSize = 12.sp) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.displayName.take(1))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(localMessages.conversationCustomName(conversationKey) ?: person.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("@${person.username} • ${person.redId}", color = AqyalCyanGlow, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedTextField(editingName, { editingName = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("اسم مخصص (خاص بك)") }, singleLine = true)
                Button({
                    localMessages.setConversationCustomName(conversationKey, editingName.trim())
                    editingName = editingName.trim()
                }, Modifier.fillMaxWidth(), enabled = editingName.isNotBlank() && editingName != person.displayName) { Text("حفظ الاسم") }

                Text("خلفية المحادثة", color = YounesEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "pinned", if (preference.first) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.first) "إلغاء التثبيت" else "تثبيت", fontSize = 12.sp) }
                    OutlinedButton({ localMessages.setConversationPreference(conversationKey, "archived", if (preference.second) 0 else 1) }, Modifier.weight(1f)) { Text(if (preference.second) "إلغاء الأرشفة" else "أرشفة", fontSize = 12.sp) }
                }
                OutlinedButton({ localMessages.setConversationPreference(conversationKey, "muted_until", if (preference.third > System.currentTimeMillis()) 0 else System.currentTimeMillis() + 8 * 60 * 60 * 1000L) }, Modifier.fillMaxWidth()) { Text(if (preference.third > System.currentTimeMillis()) "إلغاء الكتم" else "كتم 8 ساعات") }
                OutlinedButton({ safety.open(person.redId); selectedContact = null }, Modifier.fillMaxWidth()) { Text("فحص رمز الأمان") }

                val isBlocked = person.redId in blockedIds
                Button({
                    if (isBlocked) directory.unblock(person) else directory.block(person)
                    selectedContact = null
                }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) YounesEmerald else MaterialTheme.colorScheme.error)) {
                    Text(if (isBlocked) "فك الحظر" else "حظر جهة الاتصال", color = if (isBlocked) Color(0xFF002118) else Color.White)
                }

                OutlinedButton({ directory.remove(person); selectedContact = null }, Modifier.fillMaxWidth()) { Text("حذف من جهات الاتصال") }
                OutlinedTextField(reportDetails, { reportDetails = it }, Modifier.fillMaxWidth(), label = { Text("تفاصيل بلاغ الإزعاج") }, maxLines = 2)
                OutlinedButton({ directory.report(person, "SPAM", reportDetails); reportDetails = "" }, Modifier.fillMaxWidth()) { Text("الإبلاغ عن إزعاج/احتيال") }
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
                    Text(selectedGroup.description.orEmpty(), color = YounesMuted)
                    LazyColumn(Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectedGroup.members, key = { it.id }) { member ->
                            val manageable = canManage && member.role != "OWNER" && member.redId != account.redId && (myRole == "OWNER" || member.role == "MEMBER")
                            Row(Modifier.fillMaxWidth().clickable(enabled = manageable) { selectedGroupMember = member }, verticalAlignment = Alignment.CenterVertically) {
                                Avatar(member.username.take(1)); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text("@${member.username}"); Text(member.redId, color = AqyalCyanGlow, fontSize = 10.sp) }
                                AssistChip({}, { Text(groupRoleLabel(member.role)) }, enabled = false)
                                if (manageable) Icon(Icons.Default.MoreVert, "خيارات العضو", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (canManage) {
                        OutlinedButton({ groupAvatarPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, Modifier.fillMaxWidth()) { Text("تغيير صورة المجموعة") }
                        OutlinedButton({
                            editGroupName = selectedGroup.name
                            editGroupDesc = selectedGroup.description.orEmpty()
                            showEditGroupInfo = true
                        }, Modifier.fillMaxWidth()) { Text("تعديل اسم/وصف المجموعة") }
                        OutlinedTextField(memberRedId, { memberRedId = YounesId.normalizeInput(it) }, Modifier.fillMaxWidth(), label = { Text("إضافة عضو بمعرف RED-ID") }, placeholder = { Text(YounesId.PLACEHOLDER) }, singleLine = true)
                        Button({ groups.addMember(selectedGroup, memberRedId) { memberRedId = "" } }, Modifier.fillMaxWidth(), enabled = memberRedId.matches(RED_ID_PATTERN) && groups.state != GroupState.Saving) { Text("إضافة عضو") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ groups.createInvite(selectedGroup) }, Modifier.weight(1f)) { Text("رابط دعوة") }
                            OutlinedButton({ groups.loadJoinRequests(selectedGroup) }, Modifier.weight(1f)) { Text("طلبات الانضمام") }
                        }
                        groups.latestInvite?.let { invite ->
                            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                            val shareLink = inviteShareLink(invite.token)
                            val countdown = inviteRemainingLabel(invite.expiresAt)
                            Card {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("رابط صالح حتى ${invite.expiresAt}", style = MaterialTheme.typography.bodySmall)
                                    if (countdown != null) Text(countdown, style = MaterialTheme.typography.bodySmall, color = AqyalGold)
                                    Text("الاستخدامات: ${invite.uses} / ${invite.maxUses}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(shareLink, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AqyalCyanGlow)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton({ clipboard.setText(AnnotatedString(shareLink)) }) { Text("نسخ الرابط") }
                                        TextButton({
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "انضم إلى مجموعتي عبر: $shareLink")
                                            }
                                            runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة الدعوة")) }
                                        }) { Text("مشاركة الدعوة") }
                                    }
                                }
                            }
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
    // حوار تعديل معلومات المجموعة (اسم/وصف) عبر GroupViewModel.updateInfo (PATCH).
    if (showEditGroupInfo && selectedGroup != null) {
        AlertDialog(
            onDismissRequest = { showEditGroupInfo = false },
            title = { Text("تعديل معلومات المجموعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editGroupName, { editGroupName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") }, singleLine = true)
                    OutlinedTextField(editGroupDesc, { editGroupDesc = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("وصف المجموعة") }, minLines = 2, maxLines = 4)
                }
            },
            confirmButton = {
                Button(
                    onClick = { groups.updateInfo(selectedGroup, editGroupName, editGroupDesc.takeIf { it.isNotBlank() }) { showEditGroupInfo = false } },
                    enabled = editGroupName.trim().length in 2..100 && groups.state != GroupState.Saving
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton({ showEditGroupInfo = false }) { Text("إلغاء") } }
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
                    Text(if (managedMember.role == "ADMIN") "تنزيل إلى عضو" else "ترقية إلى مشرف")
                }
                OutlinedButton({ groups.transferOwnership(selectedGroup, managedMember) { selectedGroupMember = null; manageGroupId = null } }, Modifier.fillMaxWidth()) { Text("نقل ملكية المجموعة له") }
            }
            Button({ groups.removeMember(selectedGroup, managedMember); selectedGroupMember = null }, Modifier.fillMaxWidth()) { Text("إزالة من المجموعة") }
            Text("إزالة العضو تلغي مفاتيح Sender Key الخاصة به لمنع قراءة الرسائل اللاحقة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ selectedGroupMember = null }) { Text("إغلاق") } }
    )
    groups.groups.firstOrNull { it.id == deleteGroupId }?.let { deleting ->
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("حذف ${deleting.name} نهائياً؟") },
            text = { Text("سيُحذف سجل المحادثة المحلي للمجموعة. لا يمكن التراجع عن الحذف.") },
            confirmButton = { Button({ groups.deleteGroup(deleting) { deleteGroupId = null; manageGroupId = null; groupConversationId = null } }) { Text("حذف نهائي") } },
            dismissButton = { TextButton({ deleteGroupId = null }) { Text("إلغاء") } }
        )
    }
    if (showGroupPollDialog) {
        val openGroupForPoll = groups.groups.firstOrNull { it.id == groupConversationId }
        AlertDialog(
            onDismissRequest = { showGroupPollDialog = false },
            title = { Text("استطلاع في المجموعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(groupPollQuestion, { groupPollQuestion = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("السؤال") }, maxLines = 3)
                    groupPollOptions.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { next -> groupPollOptions = groupPollOptions.toMutableList().also { it[index] = next.take(80) } },
                            Modifier.fillMaxWidth(), label = { Text("خيار ${index + 1}") }, singleLine = true
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ if (groupPollOptions.size < 6) groupPollOptions = groupPollOptions + "" }, Modifier.weight(1f), enabled = groupPollOptions.size < 6) { Text("+ خيار") }
                        OutlinedButton({ if (groupPollOptions.size > 2) groupPollOptions = groupPollOptions.dropLast(1) }, Modifier.weight(1f), enabled = groupPollOptions.size > 2) { Text("- خيار") }
                    }
                }
            },
            confirmButton = {
                val validPoll = groupPollQuestion.isNotBlank() && groupPollOptions.count { it.trim().length >= 2 } >= 2
                Button(
                    enabled = validPoll && openGroupForPoll != null,
                    onClick = {
                        val poll = InlinePoll(
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
                ) { Text("نشر الاستطلاع") }
            },
            dismissButton = { TextButton({ showGroupPollDialog = false }) { Text("إلغاء") } }
        )
    }
    if (showMediaGallery && target.isNotBlank()) {
        val convKey = conversationId(account.redId, target)
        MediaGalleryDialog(
            title = "وسائط المحادثة",
            messages = decrypted.filter { it.conversationId == convKey },
            attachments = attachments,
            onDismiss = { showMediaGallery = false }
        )
    }
    if (showGroupMediaGallery && groupConversationId != null) {
        MediaGalleryDialog(
            title = "وسائط المجموعة",
            messages = decrypted.filter { it.conversationId == groupConversationId },
            attachments = attachments,
            onDismiss = { showGroupMediaGallery = false }
        )
    }
    if (showMessageSearch) AlertDialog(
        onDismissRequest = { showMessageSearch = false; messageSearchQuery = "" },
        title = { Text("البحث في الرسائل") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(messageSearchQuery, { messageSearchQuery = it }, Modifier.fillMaxWidth(), label = { Text("كلمة البحث") }, singleLine = true)
            val currentConversation = groupConversationId ?: conversationId(account.redId, target)
            val results = if (messageSearchQuery.length >= 2) localMessages.search(messageSearchQuery).filter { it.conversationId == currentConversation } else emptyList()
            LazyColumn(Modifier.height(280.dp)) { items(results, key = { it.id }) { result -> Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Column(Modifier.padding(10.dp)) { Text(if (result.type == "RICH_TEXT") RichMessage.decode(result.plaintext)?.text.orEmpty() else result.plaintext.toString(Charsets.UTF_8), maxLines = 4); Row(verticalAlignment = Alignment.CenterVertically) { Text(if (result.outgoing) "أنت" else result.senderId.take(12), color = AqyalCyanGlow, style = MaterialTheme.typography.labelSmall); Text(" • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(result.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } } } } }
        } },
        confirmButton = { TextButton({ showMessageSearch = false; messageSearchQuery = "" }) { Text("إغلاق") } }
    )
    if (showDirectory) AlertDialog(
        onDismissRequest = { showDirectory = false; pendingForwardMessage = null; directory.clear() },
        title = { Text("الدليل العام") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(directoryQuery, { directoryQuery = it }, Modifier.fillMaxWidth(), label = { Text("username أو الاسم الكامل") }, singleLine = true)
                Button({ directory.search(directoryQuery) }, Modifier.fillMaxWidth(), enabled = directoryQuery.trim().length >= 3 && directory.state != DirectoryState.Loading) {
                    Icon(Icons.Default.Search, null); Text(" بدء البحث")
                }
                when (val state = directory.state) {
                    DirectoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                    is DirectoryState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is DirectoryState.Message -> Text(state.text, color = AqyalGold)
                    DirectoryState.Ready -> if (directory.results.isEmpty()) Text("لا توجد نتائج مطابقة", color = YounesMuted) else LazyColumn(Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(directory.results, key = { it.redId }) { person ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(person.displayName.take(1)); Column(Modifier.weight(1f).padding(start = 9.dp)) { Text(person.displayName, fontWeight = FontWeight.Bold); Text("@${person.username} • ${person.redId}", color = AqyalCyanGlow, fontSize = 10.sp) }
                                    TextButton({
                                        val forward = pendingForwardMessage
                                        if (forward != null) {
                                            RedConnectionService.sendRichText(context, person.redId, conversationId(account.redId, person.redId), RichMessage(text = messageDisplayText(forward), forwardOf = forward.id))
                                            pendingForwardMessage = null
                                        } else target = person.redId
                                        showDirectory = false; directory.clear()
                                    }) { Text(if (pendingForwardMessage != null) "توجيه" else "مراسلة") }
                                    Button({ directory.request(person) }) { Text("إضافة") }
                                }
                            }
                        }
                    }
                    DirectoryState.Idle -> Text("ابحث عن أي شخص بمعرفه أو اسمه لبدء محادثة مشفرة.", color = YounesMuted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton({ showDirectory = false; pendingForwardMessage = null; directory.clear() }) { Text("إغلاق") } }
    )
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("إنشاء مجموعة جديدة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CameraAlt, "صورة المجموعة", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") }, singleLine = true)
            OutlinedTextField(groupDescription, { groupDescription = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("وصف المجموعة") }, minLines = 2, maxLines = 4)
            Text("المجموعات مشفرة بين الأعضاء. مفاتيح Sender Keys تُوزع عند الإنشاء.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ groups.create(name, groupDescription.trim().takeIf(String::isNotEmpty)) { create = false; name = ""; groupDescription = "" } }, enabled = name.trim().length in 2..100 && groups.state != GroupState.Saving) { Text("إنشاء المجموعة") } },
        dismissButton = { OutlinedButton({ create = false; name = ""; groupDescription = "" }) { Text("إلغاء") } })
    if (showJoinGroup) AlertDialog(
        onDismissRequest = { showJoinGroup = false; joinToken = "" },
        title = { Text("الانضمام برمز دعوة") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(joinToken, { joinToken = it }, Modifier.fillMaxWidth(), label = { Text("الصق رمز الدعوة") }, placeholder = { Text("red.ly/g/… أو الرمز") }, singleLine = true)
            Text("الصق رابط الدعوة (https://red.ly/g/… أو red://join?token=…) لعرض المعاينة أولاً. لا تنضم لأي مجموعة لا تعرف مصدرها.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ groups.joinWithToken(joinToken) { showJoinGroup = false; joinToken = "" } }, enabled = parseInviteToken(joinToken).isNotBlank() && groups.state != GroupState.Saving) { Text("تأكيد الانضمام") } },
        dismissButton = { TextButton({ showJoinGroup = false; joinToken = "" }) { Text("إلغاء") } }
    )
    messageInfo?.let { info ->
        val richInfo = if (info.type == "RICH_TEXT") RichMessage.decode(info.plaintext) else null
        // LEGENDARY: معلومات دقيقة بلا بيانات وهمية — الحالات الخمس + Bidi + نسخ المعرف
        val statusText = when (info.status.uppercase()) {
            "READ" -> "✓✓ مقروءة"
            "DELIVERED" -> "✓✓ مستلمة"
            "SENT" -> "✓✓ مرسلة للخادم"
            "SENDING", "PENDING", "QUEUED" -> "◷ قيد الإرسال (في صندوق الصادر)"
            "FAILED", "ERROR", "DEAD_LETTER" -> "⚠ فشلت — أعد الإرسال من الفقاعة"
            else -> info.status
        }
        AlertDialog(
            onDismissRequest = { messageInfo = null },
            title = { Text("معلومات الرسالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageInfoRow("المرسل", if (info.outgoing) "أنت" else info.senderRedId)
                    MessageInfoRow("النوع", when (info.type) {
                        "RICH_TEXT" -> "نص عادي"; "VOICE" -> "رسالة صوتية"; "STICKER" -> "ملصق"
                        "IMAGE" -> "صورة"; "VIDEO" -> "فيديو"; "AUDIO" -> "صوت"; "FILE" -> "ملف"
                        else -> info.type
                    })
                    MessageInfoRow("الوقت", android.text.BidiFormatter.getInstance().unicodeWrap(
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(info.timestamp))))
                    MessageInfoRow("الحالة", statusText)
                    if (editedMessageIds.containsKey(info.id)) MessageInfoRow("معدّلة", "نعم")
                    if (richInfo?.forwardOf != null) MessageInfoRow("إعادة توجيه", "نعم" +
                        if ((richInfo.forwardCount ?: 0) > 5) " (كثيرة التحويل)" else "")
                    if (richInfo?.replyTo != null) MessageInfoRow("رد على", richInfo.replyTo!!.take(12))
                    if (richInfo?.expiresAt != null) MessageInfoRow("اختفاء ذاتي", "نعم — تُحذف تلقائياً")
                    MessageInfoRow("المعرف", info.id.take(16))
                    if (info.status.uppercase() in setOf("FAILED", "ERROR", "DEAD_LETTER")) {
                        Text("تلميح: الفشل يعني عدم وصولها للخادم — لا تحذفها قبل نجاح إعادة الإرسال.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    // LEGENDARY: من قرأ (مجموعات، رسائلي الصادرة) — عدد + أسماء من الخادم
                    val infoGroup = remember(info.conversationId) {
                        groups.groups.firstOrNull { it.id == info.conversationId }
                    }
                    if (infoGroup != null && info.outgoing && info.sequence > 0) {
                        androidx.compose.runtime.LaunchedEffect(info.id) {
                            groups.loadGroupReaders(infoGroup, info.sequence)
                        }
                        val readers = groups.groupReaders.filter { it.lastReadSequence >= info.sequence }
                        MessageInfoRow("قرأها", if (readers.isEmpty()) "لا أحد بعد" else "${readers.size} من ${infoGroup.members.size}")
                        if (readers.isNotEmpty()) {
                            Text(readers.take(10).joinToString(" • ") { it.username.ifBlank { it.redId.take(8) } },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ messageInfo = null }) { Text("إغلاق") } }
        )
    }
}

/** مدة التثبيت الافتراضية للمجموعة (7 ايام بالثواني). */
private const val GROUP_PIN_EXPIRES_SECONDS = 7 * 24 * 3600L

/** تحديث المثبتات عند الطلب — تستدعى من LaunchedEffect (جلبة اولية + GROUP_SYNC). */
private suspend fun refreshGroupPins(
    pinApi: PinsApi,
    gid: String,
    groupPinnedMessages: MutableMap<String, DecryptedMessage>,
    decrypted: List<DecryptedMessage>
) {
    when (val r = pinApi.listForGroup(gid)) {
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
}

/**
 * اشتقاق الاستطلاعات المغلقة: أي نسخة تحمل نفس pollId مع isClosed=true
 * (الأحدث يغلب نسخة E2EE عبر بث المجموعات) تعني إغلاقه لدى كل الأعضاء.
 */
private fun deriveClosedPollIds(messages: List<DecryptedMessage>): Set<String> {
    val closed = mutableSetOf<String>()
    messages.forEach { message ->
        if (message.type != "RICH_TEXT") return@forEach
        val poll = RichMessage.decode(message.plaintext)?.poll
        if (poll != null && poll.isClosed && poll.pollId.isNotBlank()) closed.add(poll.pollId)
    }
    return closed
}

/**
 * تطبيق حالة الإغلاق على نسخ الاستطلاع المعروضة (دمج نسخ نفس pollId)
 * بحيث تبقى نسخة واحدة تعكس آخر حالة — نسخة الإغلاق تغلب نسخة الفتح.
 * أصوات POLL_VOTE (بلا poll) لا تمس.
 */
private fun applyGroupPollClosedState(messages: List<DecryptedMessage>, closedPollIds: Set<String>): List<DecryptedMessage> {
    if (closedPollIds.isEmpty()) return messages
    val seenPoll = mutableSetOf<String>()
    val out = ArrayList<DecryptedMessage>(messages.size)
    messages.forEach { message ->
        val rich = if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext) else null
        val poll = rich?.poll
        if (poll == null || poll.pollId.isBlank()) { out.add(message); return@forEach }
        if (!seenPoll.add(poll.pollId)) return@forEach
        if (poll.pollId in closedPollIds && !poll.isClosed) {
            out.add(message.copy(plaintext = RichMessage.encode(rich.copy(poll = poll.copy(isClosed = true)))))
        } else {
            out.add(message)
        }
    }
    return out
}

/** نص العد التنازلي للدعوة — null عند انتهاء الصلاحية يعني إخفاء البطاقة. */
private fun inviteRemainingLabel(expiresAt: String): String? {
    val expiryMs = parseInviteExpiryMs(expiresAt) ?: return null
    val remaining = expiryMs - System.currentTimeMillis()
    if (remaining <= 0) return "انتهت صلاحية الدعوة"
    val minutes = remaining / 60000
    val label = when {
        minutes < 1 -> "ينتهي بعد أقل من دقيقة"
        minutes < 60 -> "ينتهي بعد $minutes دقيقة"
        minutes < 1440 -> "ينتهي بعد ${minutes / 60} ساعة"
        else -> "ينتهي بعد ${minutes / 1440} يوم"
    }
    return "⏳ $label"
}

private fun parseInviteExpiryMs(expiresAt: String): Long? {
    val t = expiresAt.trim()
    if (t.isEmpty()) return null
    runCatching { return java.time.Instant.parse(t).toEpochMilli() }
    runCatching { return java.time.OffsetDateTime.parse(t.replace(" ", "T")).toInstant().toEpochMilli() }
    listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd").forEach { pattern ->
        runCatching { return java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(t)?.time }
    }
    t.toLongOrNull()?.let { return if (it < 1_000_000_000_000L) it * 1000 else it }
    return null
}
