package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════
//  RED CHAT DETAIL SCREEN — الواجهة الرئيسية للمحادثة
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedChatDetailScreen(
    chatId: String,
    onBack: () -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    val scope         = rememberCoroutineScope()
    val listState     = rememberLazyListState()
    var messageText   by remember { mutableStateOf("") }
    var replyingTo    by remember { mutableStateOf<Message?>(null) }
    var showMoreMenu  by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            Message("1", "مرحباً! كيف حالك؟",      "10:28", false, "READ"),
            Message("2", "بخير، شكراً! وأنت؟",      "10:29", true,  "READ"),
            Message("3", "ممتاز! هل راجعت تطبيق RED الجديد؟", "10:31", false, "READ"),
            Message("4", "نعم! الواجهة الجديدة رائعة جداً 🔥", "10:32", true, "READ"),
            Message("5", "أتفق معك! الألوان والتصميم احترافي جداً.", "10:33", false, "READ",
                reactions = mapOf("❤️" to 2, "👍" to 1)),
            Message("6", "سأرسل لك ملف التصاميم الآن.", "10:35", true,  "DELIVERED"),
            Message("7", "رائع، في انتظاره! 🙏",       "10:36", false, "READ"),
            Message("8", "🎤", "10:38", true, "READ", isVoice = true, voiceDuration = "0:12")
        )
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            ChatDetailTopBar(
                chatId       = chatId,
                onBack       = onBack,
                onAudioCall  = { onAudioCall(chatId) },
                onVideoCall  = { onVideoCall(chatId) },
                onMoreMenu   = { showMoreMenu = !showMoreMenu }
            )
        },
        bottomBar = {
            ChatInputSection(
                text         = messageText,
                replyingTo   = replyingTo,
                onTextChange = { messageText = it },
                onCancelReply= { replyingTo = null },
                onSend = {
                    if (messageText.isNotBlank()) {
                        messages.add(Message(
                            id       = System.currentTimeMillis().toString(),
                            content  = messageText,
                            time     = "الآن",
                            isMe     = true,
                            status   = "SENT",
                            replyTo  = replyingTo
                        ))
                        messageText = ""
                        replyingTo  = null
                        scope.launch { listState.animateScrollToItem(messages.size - 1) }
                    }
                },
                onAttach     = { /* open attach sheet */ },
                onRecordVoice= { /* start recording */ }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Background gradient subtle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BgPrimary, BgDeep)))
            )

            LazyColumn(
                state         = listState,
                modifier      = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false
            ) {
                // Date header
                item {
                    DateHeader("اليوم")
                }
                items(messages, key = { it.id }) { msg ->
                    LuxuryChatBubble(
                        message    = msg,
                        isMe       = msg.isMe,
                        onLongPress = { /* show context menu */ },
                        onReact    = { m, emoji ->
                            val idx = messages.indexOf(m)
                            if (idx >= 0) {
                                val current = m.reactions.toMutableMap()
                                current[emoji] = (current[emoji] ?: 0) + 1
                                messages[idx] = m.copy(reactions = current)
                            }
                        },
                        onReplyTo  = { m -> replyingTo = m }
                    )
                }
            }

            // Scroll to bottom FAB
            val showScrollBtn by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            AnimatedVisibility(
                visible  = showScrollBtn,
                enter    = scaleIn() + fadeIn(),
                exit     = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp)
            ) {
                FloatingActionButton(
                    onClick        = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                    containerColor = SurfaceElevated,
                    contentColor   = TextPrimary,
                    modifier       = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    // More menu dropdown
    if (showMoreMenu) {
        DropdownMenu(
            expanded         = showMoreMenu,
            onDismissRequest = { showMoreMenu = false },
            modifier         = Modifier.background(SurfaceElevated)
        ) {
            listOf("بحث" to Icons.Rounded.Search, "وسائط" to Icons.Rounded.Image,
                   "صامت" to Icons.Rounded.NotificationsOff, "حذف المحادثة" to Icons.Rounded.DeleteForever)
                .forEach { (label, icon) ->
                    DropdownMenuItem(
                        text  = { Text(label, color = if (label == "حذف المحادثة") RedPrimary else TextPrimary) },
                        leadingIcon = { Icon(icon, null, tint = if (label == "حذف المحادثة") RedPrimary else TextSecondary) },
                        onClick = { showMoreMenu = false }
                    )
                }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  TOP BAR
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailTopBar(
    chatId: String,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMoreMenu: () -> Unit
) {
    var isTypingAnimated by remember { mutableStateOf(true) }

    TopAppBar(
        modifier = Modifier.shadow(elevation = 0.dp),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "رجوع", tint = TextPrimary)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(BlueAccent, PurpleAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("أ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("أيمن", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    AnimatedContent(targetState = isTypingAnimated) { typing ->
                        if (typing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()
                                Spacer(Modifier.width(4.dp))
                                Text("يكتب...", color = TypingColor, fontSize = 12.sp)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OnlineDot))
                                Spacer(Modifier.width(4.dp))
                                Text("متصل الآن", color = OnlineDot, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onAudioCall) {
                Icon(Icons.Rounded.Call, "مكالمة صوتية", tint = TextPrimary)
            }
            IconButton(onClick = onVideoCall) {
                Icon(Icons.Rounded.Videocam, "مكالمة مرئية", tint = TextPrimary)
            }
            IconButton(onClick = onMoreMenu) {
                Icon(Icons.Rounded.MoreVert, "المزيد", tint = TextPrimary)
            }
        }
    )
}

// Typing indicator dots
@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition()
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(TypingColor.copy(alpha = alpha)))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  DATE HEADER
// ════════════════════════════════════════════════════════════

@Composable
private fun DateHeader(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceMid.copy(alpha = 0.8f))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(text, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  INPUT SECTION
// ════════════════════════════════════════════════════════════

@Composable
private fun ChatInputSection(
    text: String,
    replyingTo: Message?,
    onTextChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRecordVoice: () -> Unit
) {
    Column(modifier = Modifier.background(SurfaceDark)) {
        // Reply preview bar
        AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMid)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(AqyalCyanGlow))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("رد على ${if (reply.isMe) "أنت" else "أيمن"}", color = AqyalCyanGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(reply.content, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Input row
        RedMessageInput(
            text          = text,
            onTextChange  = onTextChange,
            onSend        = onSend,
            onAttach      = onAttach,
            onRecordVoice = onRecordVoice
        )
    }
}
