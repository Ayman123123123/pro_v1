package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  DATA MODELS
// ════════════════════════════════════════════════════════════

data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isGroup: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val lastSeen: String = "",
    val avatarColor: Color = RedPrimary,
    val isTyping: Boolean = false
)

data class StoryPreview(
    val id: String,
    val name: String,
    val avatarColor: Color,
    val hasNew: Boolean,
    val isLive: Boolean = false
)

// ════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit
) {
    var searchQuery    by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") }
    var showSearch     by remember { mutableStateOf(false) }

    val filters = listOf("الكل", "غير مقروء", "مجموعات", "مفضلة")

    val allChats = remember {
        listOf(
            ChatPreview("1", "أيمن", "مرحباً، هل راجعت التحديث الجديد؟", "10:30", 2, true, isPinned = true, avatarColor = BlueAccent),
            ChatPreview("2", "فريق التقنية", "الإصدار غداً! 🚀", "أمس", 5, false, isGroup = true, avatarColor = PurpleAccent),
            ChatPreview("3", "علي", "هل يمكننا الاتصال لاحقاً؟", "أمس", 0, false, avatarColor = GreenAccent),
            ChatPreview("4", "مجموعة التصميم", "تم رفع الملفات الجديدة.", "الأحد", 3, false, isGroup = true, isTyping = true, avatarColor = OrangeAccent),
            ChatPreview("5", "سارة", "شكراً جزيلاً!", "الأحد", 0, true, avatarColor = Color(0xFFE91E63)),
            ChatPreview("6", "خالد", "متى الاجتماع؟", "السبت", 1, false, avatarColor = AqyalCyan),
            ChatPreview("7", "مجتمع RED", "تحديث جديد: الواجهة الجديدة رائعة! 🔴", "الجمعة", 12, false, isGroup = true, avatarColor = RedPrimary),
            ChatPreview("8", "نورة", "أرسلت الملف", "الخميس", 0, true, isMuted = true, avatarColor = GoldenAccent)
        )
    }

    val stories = remember {
        listOf(
            StoryPreview("s0", "أنت", RedDeep, false),
            StoryPreview("s1", "أيمن", BlueAccent, true, isLive = true),
            StoryPreview("s2", "سارة", Color(0xFFE91E63), true),
            StoryPreview("s3", "خالد", AqyalCyan, true),
            StoryPreview("s4", "نورة", GoldenAccent, false),
            StoryPreview("s5", "RED", RedPrimary, true, isLive = true),
        )
    }

    val pinnedChats   = allChats.filter { it.isPinned }
    val displayedChats = allChats.filter { chat ->
        (searchQuery.isEmpty() || chat.name.contains(searchQuery, ignoreCase = true) ||
         chat.lastMessage.contains(searchQuery, ignoreCase = true)) &&
        when (selectedFilter) {
            "غير مقروء" -> chat.unreadCount > 0
            "مجموعات"   -> chat.isGroup
            "مفضلة"     -> chat.isPinned
            else         -> true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ── Header ──────────────────────────────────────────
            item {
                ChatListHeader(
                    showSearch  = showSearch,
                    searchQuery = searchQuery,
                    onSearchToggle     = { showSearch = !showSearch },
                    onSearchChange     = { searchQuery = it },
                    onNewChatClick     = onNewChatClick
                )
            }

            // ── Stories / Live Bar ───────────────────────────────
            item {
                AnimatedVisibility(visible = !showSearch) {
                    StoriesBar(stories = stories, onStoryClick = {})
                }
            }

            // ── Filter Chips ─────────────────────────────────────
            item {
                LazyRow(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick  = { selectedFilter = filter },
                            label    = { Text(filter, fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor    = RedPrimary,
                                selectedLabelColor        = TextOnRed,
                                containerColor            = SurfaceMid,
                                labelColor                = TextSecondary
                            ),
                            border = null,
                            shape  = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // ── Pinned Section ────────────────────────────────────
            if (pinnedChats.isNotEmpty() && selectedFilter == "الكل" && searchQuery.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PushPin, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مثبتة", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                items(pinnedChats, key = { it.id }) { chat ->
                    ChatListItem(chat = chat, onChatClick = onChatClick)
                }
                item { HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            // ── Chat Items ────────────────────────────────────────
            items(displayedChats.filter { !it.isPinned || selectedFilter != "الكل" || searchQuery.isNotEmpty() }, key = { it.id }) { chat ->
                ChatListItem(chat = chat, onChatClick = onChatClick, modifier = Modifier.animateItemPlacement())
            }

            // ── Bottom Padding ────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }
        }

        // ── FAB ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !showSearch,
            enter   = scaleIn() + fadeIn(),
            exit    = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            FloatingActionButton(
                onClick          = onNewChatClick,
                containerColor   = RedPrimary,
                contentColor     = TextOnRed,
                shape            = RoundedCornerShape(16.dp),
                modifier         = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.Edit, "محادثة جديدة", modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  HEADER
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListHeader(
    showSearch: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onNewChatClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
    ) {
        AnimatedVisibility(visible = !showSearch) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo + Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(GradientRedPrimary)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("R", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("المحادثات", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                // Actions
                Row {
                    IconButton(onClick = onSearchToggle) {
                        Icon(Icons.Rounded.Search, "بحث", tint = TextSecondary)
                    }
                    IconButton(onClick = { /* Camera */ }) {
                        Icon(Icons.Outlined.CameraAlt, "كاميرا", tint = TextSecondary)
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSearch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Rounded.ArrowBack, "رجوع", tint = TextPrimary)
                }
                OutlinedTextField(
                    value          = searchQuery,
                    onValueChange  = onSearchChange,
                    modifier       = Modifier.fillMaxWidth().height(52.dp),
                    placeholder    = { Text("ابحث في المحادثات...", color = TextSecondary, fontSize = 15.sp) },
                    leadingIcon    = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                    trailingIcon   = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                    },
                    singleLine     = true,
                    shape          = RoundedCornerShape(16.dp),
                    colors         = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor         = SurfaceMid,
                        focusedBorderColor     = RedPrimary,
                        unfocusedBorderColor   = Color.Transparent,
                        cursorColor            = RedPrimary,
                        focusedTextColor       = TextPrimary,
                        unfocusedTextColor     = TextPrimary
                    )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  STORIES BAR
// ════════════════════════════════════════════════════════════

@Composable
private fun StoriesBar(stories: List<StoryPreview>, onStoryClick: (String) -> Unit) {
    LazyRow(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stories) { story ->
            StoryBubble(story = story, onClick = { onStoryClick(story.id) })
        }
    }
}

@Composable
private fun StoryBubble(story: StoryPreview, onClick: () -> Unit) {
    val isMe = story.name == "أنت"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Ring
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            story.isLive -> Brush.sweepGradient(GradientLive)
                            story.hasNew -> Brush.sweepGradient(GradientRedPrimary)
                            isMe         -> Brush.sweepGradient(listOf(SurfaceLight, SurfaceLight))
                            else         -> Brush.sweepGradient(listOf(TextTertiary, TextTertiary))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(story.avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = story.name.take(1).uppercase(),
                        color      = Color.White,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // LIVE badge
            if (story.isLive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RedPrimary)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            // Add button for self
            if (isMe) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(RedPrimary)
                        .border(2.dp, BgPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text      = if (isMe) "حالتك" else story.name.take(7),
            color     = TextSecondary,
            fontSize  = 11.sp,
            maxLines  = 1
        )
    }
}

// ════════════════════════════════════════════════════════════
//  CHAT LIST ITEM
// ════════════════════════════════════════════════════════════

@Composable
fun ChatListItem(
    chat: ChatPreview,
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onChatClick(chat.id) }
            .background(if (chat.isPinned) SurfaceDark.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(chat.avatarColor, chat.avatarColor.copy(alpha = 0.7f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = chat.name.take(if (chat.isGroup) 2 else 1).uppercase(),
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Online dot
            if (chat.isOnline && !chat.isGroup) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(OnlineDot)
                        .border(2.dp, BgPrimary, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Text info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text       = chat.name,
                        color      = TextPrimary,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (chat.isMuted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.NotificationsOff, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text     = chat.time,
                    color    = if (chat.unreadCount > 0) RedPrimary else TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (chat.isTyping) {
                        Text("يكتب...", color = TypingColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text(
                            text     = chat.lastMessage,
                            color    = TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape)
                            .background(if (chat.isMuted) TextTertiary else RedPrimary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        tint       = AqyalCyanGlow,
                        modifier   = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    HorizontalDivider(
        color    = DividerColor,
        modifier = Modifier.padding(start = 84.dp)
    )
}
