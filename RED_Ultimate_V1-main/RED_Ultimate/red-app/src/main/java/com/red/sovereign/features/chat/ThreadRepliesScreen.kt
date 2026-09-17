package com.red.sovereign.features.chat

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.database.LocalHistoryEntity
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🧵 YOUNES Sovereign — Thread Replies System
 *
 * ميزات:
 * - عرض الردود على رسالة معينة في شريط جانبي/أسفل
 * - مؤشر الرسائل غير المقروءة في الثريد
 * - التنقل بين الرسالة الأصلية والردود
 * - دعم الردود المتداخلة (Nested replies)
 * - علامة @mention للمرسل الأصلي
 * - إشعار عند رد جديد في الثريد
 */

data class ThreadMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val replyToMessageId: String?,
    val replyToSenderId: String?,
    val status: String,
    val messageType: String,
    val forwardOf: String? = null,
    val forwardCount: Int = 0,
    val reactions: List<String> = emptyList()
)

data class ThreadState(
    val rootMessage: ThreadMessage? = null,
    val replies: List<ThreadMessage> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val offset: Int = 0
)

class ThreadViewModel(
    private val repository: LocalRepository,
    private val conversationId: String,
    private val currentUserId: String
) {
    private val _state = MutableStateFlow(ThreadState())
    val state: kotlinx.coroutines.flow.StateFlow<ThreadState> = _state.asStateFlow()

    fun openThread(rootMessageId: String) {
        _state.update { it.copy(loading = true, error = null) }
        // تحميل الرسالة الجذرية
        val root = repository.getLocalHistoryEntry(rootMessageId)
            ?.let { entity ->
                val rich = RichMessage.decode(entity.encryptedPlaintext)
                ThreadMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    text = rich?.text ?: entity.encryptedPlaintext.toString(Charsets.UTF_8),
                    timestamp = entity.createdAt,
                    replyToMessageId = entity.replyToMessageId,
                    replyToSenderId = entity.replyToSenderId,
                    status = entity.status,
                    messageType = entity.messageType
                )
            }

        if (root != null) {
            _state.update { it.copy(rootMessage = root, loading = false) }
            loadReplies(rootMessageId)
        } else {
            _state.update { it.copy(loading = false, error = "الرسالة غير موجودة") }
        }
    }

    fun loadReplies(rootMessageId: String, offset: Int = 0) {
        val replies = repository.getThreadReplies(conversationId, rootMessageId, offset, 50)
            .map { entity ->
                val rich = RichMessage.decode(entity.encryptedPlaintext)
                ThreadMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    text = rich?.text ?: entity.encryptedPlaintext.toString(Charsets.UTF_8),
                    timestamp = entity.createdAt,
                    replyToMessageId = entity.replyToMessageId,
                    replyToSenderId = entity.replyToSenderId,
                    status = entity.status,
                    messageType = entity.messageType
                )
            }

        val current = _state.value
        val allReplies = if (offset == 0) replies else current.replies + replies
        val newUnread = replies.count { it.senderId != currentUserId && it.timestamp > current.replies.lastOrNull()?.timestamp ?: 0L }

        _state.update {
            it.copy(
                replies = allReplies,
                unreadCount = it.unreadCount + newUnread,
                hasMore = replies.size >= 50,
                offset = offset + replies.size,
                loading = false
            )
        }
    }

    fun markAsRead() {
        _state.update { it.copy(unreadCount = 0) }
    }

    fun sendReply(text: String, rootMessageId: String) {
        // TODO: إرسال الرد عبر RedConnectionService
        // سيتم إضافته في MessageStore/RedConnectionService
    }

    fun closeThread() {
        _state.value = ThreadState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
    onSendReply: (String) -> Unit
) {
    val state = viewModel.state.collectAsState().value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // تحديث حالة القراءة عند فتح الثريد
    LaunchedEffect(state.rootMessage?.id) {
        if (state.rootMessage != null && state.unreadCount > 0) {
            viewModel.markAsRead()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
    ) {
        // Header
        Surface(
            color = SovereignColors.SurfaceNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                    Column {
                        Text("الردود", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        state.rootMessage?.let { root ->
                            Text("${state.replies.size} رد${if (state.replies.size != 1) "ود" else ""}", fontSize = 12.sp, color = AqyalGold)
                        }
                    }
                }

                if (state.unreadCount > 0) {
                    Text(
                        text = "${state.unreadCount} غير مقروء",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AqyalGold,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .background(AqyalGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // رسالة الجذر (الأصلية)
        state.rootMessage?.let { root ->
            Surface(
                color = SovereignColors.SurfaceNavy.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الرسالة الأصلية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            java.text.DateFormat.getTimeInstance().format(java.util.Date(root.timestamp)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ThreadMessageBubble(
                        message = root,
                        isRoot = true,
                        currentUserId = currentUserId
                    )
                }
            }
        }

        // فاصل
        androidx.compose.foundation.layout.Divider(
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        // قائمة الردود
        if (state.loading && state.replies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AqyalGold)
            }
        } else if (state.replies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Forum, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("لا توجد ردود بعد", fontSize = 16.sp, color = Color.Gray)
                    Text("كن أول من يرد على هذه الرسالة", fontSize = 13.sp, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.replies, key = { it.id }) { reply ->
                    ThreadMessageBubble(
                        message = reply,
                        isRoot = false,
                        currentUserId = currentUserId
                    )
                }

                // تحميل المزيد
                if (state.hasMore && !state.loading) {
                    item {
                        OutlinedButton(
                            onClick = {
                                scope.launch { viewModel.loadReplies(state.rootMessage!!.id, state.offset) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedButtonDefaults.colors(
                                focusedBorderColor = AqyalGold,
                                unfocusedBorderColor = Color.Gray,
                                contentColor = AqyalGold
                            )
                        ) {
                            Text("تحميل المزيد", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // شريط إرسال الرد
        ThreadReplyInputBar(
            onSend = onSendReply
        )
    }
}

@Composable
fun ThreadMessageBubble(
    message: ThreadMessage,
    isRoot: Boolean,
    currentUserId: String
) {
    val isMe = message.senderId == currentUserId
    val time = java.text.DateFormat.getTimeInstance().format(java.util.Date(message.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            // Avatar للمرسل
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SovereignColors.Cyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    message.senderId.take(1).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovereignColors.Cyan
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            crossAxisSize = CrossAxisSize.Min,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (!isMe) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(message.senderId.take(12), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Cyan)
                    if (message.replyToSenderId != null) {
                        Text("رد على ${message.replyToSenderId!!.take(8)}", fontSize = 10.sp, color = AqyalGold)
                    }
                }
            }

            // محتوى الرسالة
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isRoot) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Reply, null, tint = AqyalGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("الأصلية", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    Text(
                        text = message.text,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (message.forwardOf != null || message.forwardCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (message.forwardCount > 5) "كثيرة التحويل" else "محوّلة",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // وقت + حالة
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                if (isMe) {
                    val tick = when (message.status.uppercase()) {
                        "READ" -> "✓✓"
                        "DELIVERED" -> "✓✓"
                        "SENT" -> "✓✓"
                        "SENDING", "PENDING" -> "◷"
                        "FAILED" -> "⚠"
                        else -> "✓"
                    }
                    val tickColor = when (message.status.uppercase()) {
                        "READ" -> AqyalGold
                        "FAILED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                    Text(tick, fontSize = 11.sp, color = tickColor, fontWeight = if (message.status.uppercase() == "READ") FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (isMe) {
            Spacer(Modifier.width(8.dp))
            // Avatar فارغ للمحاذاة
            Box(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun ThreadReplyInputBar(
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { /* TODO: إيموجي */ }) {
                Icon(Icons.Default.EmojiEmotions, "إيموجي", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("اكتب رداً...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SovereignColors.Cyan,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            if (text.isNotBlank()) {
                IconButton(onClick = { onSend(text); text = "" }) {
                    Icon(Icons.Default.Send, "إرسال", tint = SovereignColors.Cyan, modifier = Modifier.size(24.dp))
                }
            } else {
                IconButton(onClick = { /* TODO: مرفقات */ }) {
                    Icon(Icons.Default.Add, "مرفق", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { /* TODO: صوت */ }) {
                    Icon(Icons.Default.Mic, "صوت", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * شريحة ثريد في شاشة الدردشة الرئيسية (Inline Thread Preview)
 */
@Composable
fun ThreadPreviewChip(
    replyCount: Int,
    unreadCount: Int,
    lastReplyPreview: String?,
    lastReplySender: String?,
    lastReplyTime: Long,
    onClick: () -> Unit
) {
    if (replyCount == 0) return

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(start = 48.dp) // محاذاة مع الفقاعات
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Forum, null, tint = AqyalGold, modifier = Modifier.size(18.dp))

                Column(crossAxisSize = CrossAxisSize.Min) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$replyCount رد", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
                        if (unreadCount > 0) {
                            Text(
                                "$unreadCount جديد",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .background(AqyalGold, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    lastReplyPreview?.let { preview ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            lastReplySender?.let { sender ->
                                Text("$sender: ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(preview, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text(
                java.text.DateFormat.getTimeInstance().format(java.util.Date(lastReplyTime)),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
