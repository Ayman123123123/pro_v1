package com.red.sovereign.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Star
import com.red.sovereign.core.RichMessage
import com.red.sovereign.ui.resolveRichMessages
import com.red.sovereign.ui.shouldMergeWithPrevious
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.crypto.DecryptedMessage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.core.database.ChatHistoryPagingViewModel
import com.red.sovereign.core.database.LocalHistoryEntity

import com.red.sovereign.features.chat.LuxuryChatBubble
import com.red.sovereign.settings.ChatFontPolicy
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.screens.scrollOnce
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.red.sovereign.ui.components.ChatWallpaper
import com.red.sovereign.ui.components.rememberSovereignHaze
import com.red.sovereign.ui.components.sovereignHazeEffect
import com.red.sovereign.ui.components.sovereignHazeSource
import com.red.sovereign.ui.theme.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    contactName: String,
    contactStatus: String = "متصل ومؤمّن",
    messages: List<DecryptedMessage>,
    currentRedId: String,
    onNavigateBack: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onReplyClick: (DecryptedMessage) -> Unit = {},
    onDeleteClick: (List<DecryptedMessage>) -> Unit = {},
    onForwardClick: (List<DecryptedMessage>) -> Unit = {},
    onStarClick: (List<DecryptedMessage>) -> Unit = {},
    // تفاعل/معلومات لقائمة الضغط المطوّل (رد/نسخ/تفاعل/معلومات) — افتراضي no-op للتوافق.
    onReactionClick: (DecryptedMessage, String) -> Unit = { _, _ -> },
    onInfoClick: (DecryptedMessage) -> Unit = {},
    // تفاعلات معروضة تحت الفقاعة: messageId -> (emoji -> count).
    reactionsByMessage: Map<String, Map<String, Int>> = emptyMap(),
    // ربط replyToMessage الداخلي بالإرسال الخارجي: يُستدعى عند كل تغيير (تعيين/إلغاء).
    onReplyStateChanged: (DecryptedMessage?) -> Unit = {},
    isTyping: Boolean = false,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // G3: معرف جهة الاتصال — اسم + Red ID ظاهر في الترويسة.
    contactRedId: String = "",
    // G3: أسماء المرسلين للدردشات الجماعية (RedId -> اسم العرض).
    senderNames: Map<String, String> = emptyMap(),
    // P1-A: يُستدعى بنص "#topic-name " عند إنشاء موضوع جديد ليُلحقه المُستدعي
    // (RedDashboard/ChatsScreen) بحقل الإدخال. افتراضيًا no-op فلا يكسر المُستدعين.
    onTopicCreated: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // زجاج مثلج حقيقي للشريط العلوي — المصدر: قائمة الرسائل تحته.
    val hazeState = rememberSovereignHaze()
    // خلفية المحادثة: حزمة المستخدم أو السيادية الافتراضية.
    val wallpaperContext = androidx.compose.ui.platform.LocalContext.current
    val customTheme = remember { CustomThemeStore.loadActiveCustomTheme(wallpaperContext) }
    val wallpaperDark = when (AppThemeState.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    // G3: منسق الوقت يُنشأ مرة واحدة بدل إعادة الإنشاء لكل رسالة في كل تركيب.
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // ── Multi-Select Mode ──
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedMessageIds = remember { mutableStateSetOf<String>() }

    // ── Reply State ──
    var replyToMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    // ربط الداخلي بالخارجي: كل تعيين/إلغاء داخلي يُبلَّغ للمرسِل الخارجي (bottomBar).
    LaunchedEffect(replyToMessage) { onReplyStateChanged(replyToMessage) }

    // resolveRichMessages: إسقاط المحذوف/المحرَّر والم reactions/المنتهية قبل العرض.
    val resolvedMessages = remember(messages) { resolveRichMessages(messages) }
    // isEdited: أي id استهدفه EDIT يوسم كمُعدَّل (resolve يستبدل النص ويسقط رسالة EDIT).
    val editedIds = remember(messages) {
        val edits = HashSet<String>()
        messages.forEach { m ->
            if (m.type == "RICH_TEXT") {
                val r = runCatching { RichMessage.decode(m.plaintext) }.getOrNull()
                if (r?.action == "EDIT" && r.editOf != null) edits += r.editOf!!
            }
        }
        edits
    }

    // ── P1-A: فلترة Thread حسب الموضوع (محليًا فقط، لا تمس قاعدة البيانات) ──
    // المصدر: RichMessage.topicId أولًا ثم hashtags ثم #topic من النص.
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var showCreateTopic by remember { mutableStateOf(false) }
    var topicInput by remember { mutableStateOf("") }
    // مواضيع أُنشئت من الزر قبل وصول أي رسالة بها — تظهر كـ chips فورًا.
    val createdTopics = remember { mutableStateSetOf<String>() }
    val extractedTopics = remember(resolvedMessages) { resolvedMessages.flatMap { it.threadTopics() }.distinct().sorted() }
    val allTopics = remember(extractedTopics, createdTopics.toList()) {
        (extractedTopics + createdTopics.toList()).distinct().sorted()
    }
    // إن حُذفت كل رسائل الموضوع المحدد يُعاد التعيين تلقائيًا إلى «الكل».
    LaunchedEffect(allTopics) {
        if (selectedTopic != null && selectedTopic !in allTopics) selectedTopic = null
    }
    val filteredMessages = remember(resolvedMessages, selectedTopic) {
        if (selectedTopic == null) resolvedMessages
        else resolvedMessages.filter { it.threadTopics().contains(selectedTopic) }
    }
    // shouldMergeWithPrevious + hideSenderHeader: دمج بصري للمتتالية من نفس المرسل (<دقيقتين).
    val hideHeaderById = remember(filteredMessages) {
        filteredMessages.mapIndexed { index, m ->
            m.id to shouldMergeWithPrevious(filteredMessages.getOrNull(index - 1), m)
        }.toMap()
    }

    // المفتاح معرف آخر رسالة لا الحجم — يمنع عاصفة إعادة التمرير.
    LaunchedEffect(filteredMessages.lastOrNull()?.id) {
        if (filteredMessages.isNotEmpty()) {
            // G3: scrollOnce موحد بدل animate — أخف ولا يرمي عند تقلص القائمة
            // أثناء الحذف/التحرير (IndexOutOfBounds كان يسقط الشاشة).
            listState.scrollOnce(filteredMessages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (multiSelectMode && selectedMessageIds.isNotEmpty()) {
                MultiSelectTopBar(
                    selectedCount = selectedMessageIds.size,
                    onSelectAll = {
                        selectedMessageIds.clear()
                        selectedMessageIds.addAll(filteredMessages.map { it.id })
                    },
                    onClearSelection = {
                        selectedMessageIds.clear()
                        multiSelectMode = false
                    },
                    onDelete = {
                        val selected = filteredMessages.filter { it.id in selectedMessageIds }
                        onDeleteClick(selected)
                        selectedMessageIds.clear()
                        multiSelectMode = false
                    },
                    onForward = {
                        val selected = filteredMessages.filter { it.id in selectedMessageIds }
                        onForwardClick(selected)
                        selectedMessageIds.clear()
                        multiSelectMode = false
                    },
                    onStar = {
                        val selected = filteredMessages.filter { it.id in selectedMessageIds }
                        onStarClick(selected)
                        selectedMessageIds.clear()
                        multiSelectMode = false
                    }
                )
            } else {
                ChatThreadTopBar(
                    contactName = contactName,
                    contactStatus = contactStatus,
                    onNavigateBack = onNavigateBack,
                    onAudioCallClick = onAudioCallClick,
                    onVideoCallClick = onVideoCallClick,
                    contactRedId = contactRedId,
                    hazeState = hazeState
                )
            }
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .sovereignHazeSource(hazeState)
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // هالة شبكية خافتة فوق الخلفية — لا تمس تباين النص
            Box(
                Modifier.fillMaxSize()
                    .background(SovereignGradients.meshChat)
            )
            // زخرفة المحادثة الهندسية — 4% فقط، خلف الرسائل وفوق الهالة
            ChatWallpaper(
                package_ = customTheme,
                isDark = wallpaperDark
            )
            Column(Modifier.fillMaxSize()) {
                // ── Reply Banner ──
                replyToMessage?.let { replyMsg ->
                    val replyText = runCatching { String(replyMsg.plaintext, Charsets.UTF_8) }.getOrDefault("")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (replyMsg.outgoing) "الرد على نفسك" else "الرد على ${senderNames[replyMsg.senderRedId]?.orEmpty() ?: replyMsg.senderRedId.take(8)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = replyText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { replyToMessage = null }) {
                                Icon(Icons.Default.Close, "إلغاء الرد", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                // ── P1-A: chips فلترة Topics أعلى الشاشة (الكل + المستخرجة + زر إنشاء) ──
                TopicFilterBar(
                    topics = allTopics,
                    selectedTopic = selectedTopic,
                    onSelectTopic = { selectedTopic = it },
                    onCreateClick = {
                        topicInput = ""
                        showCreateTopic = true
                    }
                )
                if (showCreateTopic) {
                    AlertDialog(
                        onDismissRequest = { showCreateTopic = false },
                        title = { Text("موضوع جديد", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text(
                                    "سيُضاف كهاشتاغ #topic-name في رسالتك القادمة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = topicInput,
                                    onValueChange = { topicInput = it },
                                    label = { Text("اسم الموضوع") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val slug = RichMessage.normalizeTopicName(topicInput)
                                    if (slug.isNotBlank()) {
                                        createdTopics.add(slug)
                                        selectedTopic = slug
                                        onTopicCreated("#$slug ")
                                    }
                                    showCreateTopic = false
                                }
                            ) { Text("إنشاء") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateTopic = false }) { Text("إلغاء") }
                        }
                    )
                }
                // LEGENDARY: تجميع الرسائل مع فواصل تاريخ بأسلوب واتساب — قبل LazyColumn
                // (remember لا يعمل داخل DSL). كانت الرسائل مكدسة بلا سياق زمني.
                val groupedWithDates = remember(filteredMessages) {
                    val out = ArrayList<ChatListItem>(filteredMessages.size + 4)
                    var lastDay: String? = null
                    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    filteredMessages.forEach { m ->
                        val day = runCatching {
                            java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(m.timestamp),
                                java.time.ZoneId.systemDefault()
                            ).format(dayFmt)
                        }.getOrDefault("")
                        if (day.isNotBlank() && day != lastDay) {
                            lastDay = day
                            out += ChatListItem.DateHeader(day)
                        }
                        out += ChatListItem.Message(m)
                    }
                    out
                }
                LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                // LEGENDARY: تباعد ذكي — رسائل متتالية من نفس المرسل متقاربة (4dp) والبقية 8dp (كانت 8dp ثابتة تسبب تباعداً مزعجاً)
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
            if (filteredMessages.isEmpty() && selectedTopic != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "لا رسائل في موضوع #$selectedTopic بعد",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            // LEGENDARY: contentType يمنع إعادة تركيب الفقاعات كلها + فواصل التاريخ من groupedWithDates أعلاه
            items(
                groupedWithDates,
                key = { when (it) { is ChatListItem.DateHeader -> "date-${it.day}"; is ChatListItem.Message -> it.msg.id } },
                contentType = { when (it) { is ChatListItem.DateHeader -> "date"; is ChatListItem.Message -> if (it.msg.outgoing) "out" else "in" } }
            ) { item ->
                when (item) {
                    is ChatListItem.DateHeader -> DateSeparatorChip(item.day)
                    is ChatListItem.Message -> {
                        val message = item.msg
                // G3: نص الوقت يُحسب مرة لكل رسالة (مفتاح id) بدل كل تركيب.
                val messageTimeText = remember(message.id, message.timestamp) {
                    runCatching {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault()).format(timeFormatter)
                    }.getOrDefault("--:--")
                }
                // فك الغني: RICH_TEXT يعرض rich.text لا JSON الخام.
                val richOf = remember(message.id, message.timestamp) {
                    if (message.type == "RICH_TEXT") runCatching { RichMessage.decode(message.plaintext) }.getOrNull() else null
                }
                // G3: فك التشفير النصي مرة لكل رسالة بدل مرتين في كل تركيب.
                val messageText = remember(message.id, message.timestamp, richOf) {
                    richOf?.text?.takeIf { it.isNotEmpty() }
                        ?: runCatching { String(message.plaintext, Charsets.UTF_8) }.getOrDefault("")
                }
                // اقتباس الرد: نص الأصل من القائمة المحلولة (يبقى بعد resolveRichMessages).
                val replyId = richOf?.replyTo
                val replyQuoted = remember(replyId, filteredMessages) {
                    replyId?.let { id -> filteredMessages.firstOrNull { it.id == id } }
                }
                val replyQuotedText = remember(replyQuoted) {
                    replyQuoted?.let { q ->
                        if (q.type == "RICH_TEXT") runCatching { RichMessage.decode(q.plaintext)?.text }.getOrNull()?.takeIf { it.isNotEmpty() }
                            ?: runCatching { String(q.plaintext, Charsets.UTF_8) }.getOrDefault("")
                        else runCatching { String(q.plaintext, Charsets.UTF_8) }.getOrDefault("")
                    }
                }

                var showMenu by remember { mutableStateOf(false) }
                var showInfo by remember { mutableStateOf(false) }
                val clipCtx = androidx.compose.ui.platform.LocalContext.current
                val sysClipboard = clipCtx
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val scope = rememberCoroutineScope()

                val isSelected = selectedMessageIds.contains(message.id)

                Box {
                    LuxuryChatBubble(
                        message = messageText,
                        isMe = message.outgoing,
                        time = messageTimeText,
                        status = message.status,
                        onLongClick = {
                            if (multiSelectMode) {
                                if (isSelected) selectedMessageIds.remove(message.id)
                                else selectedMessageIds.add(message.id)
                                if (selectedMessageIds.isEmpty()) multiSelectMode = false
                            } else {
                                showMenu = true
                            }
                        },
                        onClick = {
                            if (multiSelectMode) {
                                if (isSelected) selectedMessageIds.remove(message.id)
                                else selectedMessageIds.add(message.id)
                                if (selectedMessageIds.isEmpty()) multiSelectMode = false
                            }
                        },
                        onSwipeReply = { msgId ->
                            val repliedMsg = filteredMessages.firstOrNull { it.id == msgId }
                            repliedMsg?.let {
                                replyToMessage = it
                                onReplyClick(it)
                            }
                        },
                        messageId = message.id,
                        senderName = if (message.outgoing) "" else senderNames[message.senderRedId].orEmpty(),
                        senderRedId = if (message.outgoing) "" else message.senderRedId,
                        fontFamily = ChatFontPolicy.familyFor(SettingsRuntime.current.fontFamily),
                        bubbleStyle = SettingsRuntime.current.bubbleStyle,
                        isSelected = isSelected,
                        forwardOf = richOf?.forwardOf,
                        forwardCount = richOf?.forwardCount ?: 0,
                        richMessage = richOf,
                        replyToId = replyId,
                        replyToText = replyQuotedText,
                        replyToSender = replyQuoted?.let { if (it.outgoing) "نفسك" else senderNames[it.senderRedId]?.orEmpty() ?: it.senderRedId.take(8) },
                        onReplyClick = {
                            val target = replyId ?: return@LuxuryChatBubble
                            scope.launch {
                                val idx = groupedWithDates.indexOfFirst { it is ChatListItem.Message && it.msg.id == target }
                                if (idx >= 0) runCatching { listState.scrollOnce(idx) }
                            }
                        },
                        reactions = reactionsByMessage[message.id] ?: emptyMap(),
                        onReactionClick = { emoji -> onReactionClick(message, emoji) },
                        isEdited = editedIds.contains(message.id),
                        hideSenderHeader = hideHeaderById[message.id] == true
                    )

                    if (!multiSelectMode) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("رد") },
                                onClick = {
                                    showMenu = false
                                    replyToMessage = message
                                    onReplyClick(message)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("نسخ") },
                                onClick = {
                                    showMenu = false
                                    val cm = sysClipboard ?: run {
                                        android.widget.Toast.makeText(clipCtx, "الحافظة غير متاحة", android.widget.Toast.LENGTH_SHORT).show()
                                        return@DropdownMenuItem
                                    }
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("رسالة", messageText)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تفاعل 👍") },
                                onClick = {
                                    showMenu = false
                                    onReactionClick(message, "👍")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("معلومات") },
                                onClick = {
                                    showMenu = false
                                    showInfo = true
                                    onInfoClick(message)
                                }
                            )
                        }
                        if (showInfo) {
                            AlertDialog(
                                onDismissRequest = { showInfo = false },
                                title = { Text("معلومات الرسالة", fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MessageInfoLine("المعرف", message.id.take(16))
                                        MessageInfoLine("الوقت", messageTimeText)
                                        MessageInfoLine("الحالة", message.status)
                                        MessageInfoLine("المرسل", if (message.outgoing) "أنت" else message.senderRedId.take(16))
                                        if (editedIds.contains(message.id)) MessageInfoLine("التحرير", "مُعدَّلة")
                                        if (replyId != null) MessageInfoLine("رد على", replyId.take(16))
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("إغلاق") } }
                            )
                        }
                    }
                }
                    } // إغلاق فرع Message
                } // إغلاق when (تاريخ/رسالة)
            }
            if (isTyping) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.padding(vertical = 4.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
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
        }
    }
    }
}

/**
 * LEGENDARY: عناصر القائمة (تاريخ/رسالة) — فواصل واتساب بلا تداخل
 */
private sealed interface ChatListItem {
    data class DateHeader(val day: String) : ChatListItem
    data class Message(val msg: DecryptedMessage) : ChatListItem
}

@Composable
private fun DateSeparatorChip(day: String) {
    // اليوم/أمس/التاريخ — عزل Bidi + تباين عالٍ
    val label = runCatching {
        val today = java.time.LocalDate.now()
        val d = java.time.LocalDate.parse(day)
        when (d) {
            today -> "اليوم"
            today.minusDays(1) -> "أمس"
            else -> d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("ar")))
        }
    }.getOrDefault(day)
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            shadowElevation = 1.dp) {
            Text(
                text = android.text.BidiFormatter.getInstance().unicodeWrap(label),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * P1-A: استخراج topics رسالة مفكوكة محليًا (بدون قاعدة بيانات).
 * الأولوية: RichMessage.topicId ← hashtags ← regex #topic من النص.
 * يتحمّل الخام UTF-8 القديم (decode=null) عبر regex مباشر.
 */
private val ThreadTopicRegex = Regex("#[\\w\u0600-\u06FF\\-]{2,30}")

private fun DecryptedMessage.threadTopics(): List<String> {
    val rich = runCatching { RichMessage.decode(plaintext) }.getOrNull()
    if (rich != null) {
        val out = LinkedHashSet<String>()
        rich.topicId?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }?.let { out += it }
        rich.hashtags.forEach { h -> h.trim().removePrefix("#").takeIf { it.isNotBlank() }?.let { out += it } }
        ThreadTopicRegex.findAll(rich.text).forEach { out += it.value.removePrefix("#") }
        if (out.isNotEmpty()) return out.toList()
    }
    val raw = runCatching { String(plaintext, Charsets.UTF_8) }.getOrDefault("")
    return ThreadTopicRegex.findAll(raw).map { it.value.removePrefix("#") }.distinct().toList()
}

/**
 * P1-A: شريط chips لفلترة الـ Thread حسب الموضوع.
 * «الكل» (null) + chip لكل topic مستخرج + زر «+» للإنشاء.
 * عرض فقط — الفلترة تتم محليًا في ChatThreadScreen عبر filteredMessages.
 */
@Composable
private fun TopicFilterBar(
    topics: List<String>,
    selectedTopic: String?,
    onSelectTopic: (String?) -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedTopic == null,
            onClick = { onSelectTopic(null) },
            label = { Text("الكل") }
        )
        topics.forEach { topic ->
            FilterChip(
                selected = selectedTopic == topic,
                onClick = { onSelectTopic(if (selectedTopic == topic) null else topic) },
                label = { Text("#$topic", maxLines = 1) }
            )
        }
        AssistChip(
            onClick = onCreateClick,
            label = { Text("موضوع جديد") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = "إنشاء موضوع", modifier = Modifier.size(16.dp))
            }
        )
    }
}

/**
 * شريط الأدوات العلوي لوضع التحديد المتعدد — يعرض عدد العناصر المحددة
 * وأزرار الإجراءات المجمّعة (حذف/إعادة توجيه/تعليم).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onStar: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount محدد", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "إلغاء التحديد", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.CheckCircle, "تحديد الكل", tint = YounesEmerald)
            }
            IconButton(onClick = onStar) {
                Icon(Icons.Default.Star, "تعليم", tint = AqyalGold)
            }
            IconButton(onClick = onForward) {
                Icon(Icons.Default.Forward, "إعادة توجيه", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadTopBar(
    contactName: String,
    contactStatus: String,
    onNavigateBack: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    contactRedId: String = "",
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    TopAppBar(
        modifier = Modifier.sovereignHazeEffect(
            state = hazeState,
            tier = SovereignGlassTier.NavBar,
            isDark = true
        ),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contactName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontFamily = PlexArabicFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(com.red.sovereign.R.string.e2ee_badge_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // G3: معرف المتصل/المرسل — Red ID ظاهر تحت الاسم حيث كان ناقصاً.
                if (contactRedId.isNotBlank()) {
                    Text(
                        text = contactRedId,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = PlexArabicFamily,
                        maxLines = 1
                    )
                }
                Text(
                    text = contactStatus,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontFamily = PlexArabicFamily
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "عودة",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(onClick = onAudioCallClick) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "مكالمة صوتية",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onVideoCallClick) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "مكالمة فيديو",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // زجاج علوي خفيف (tier 8dp): سطح شبه معتم + fallback تلقائي للفاتح
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun MessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    onReplyClick: () -> Unit = {},
    senderName: String = "",
    senderRedId: String = message.senderRedId
) {
    // فقاعات واعية بالثيم: داكن = كحلي/فحمي السيادي، فاتح = حاويات Material.
    // النص والطابع الزمني من colorScheme فيُحققان AA في الوضعين معًا.
    // (كانت ألوان YounesBubble* مثبتة داكنة فتكسر الوضع الفاتح كليًا.)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val backgroundBrush = if (isDark) {
        if (isMine) {
            Brush.linearGradient(
                colors = listOf(YounesBubbleOut, YounesBubbleOutGlow)
            )
        } else {
            Brush.linearGradient(colors = listOf(YounesBubbleIn, YounesBubbleIn))
        }
    } else {
        if (isMine) {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primaryContainer
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }

    val shape = if (isMine) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val messageTime = remember(message.id, message.timestamp) {
        runCatching {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(message.timestamp), ZoneId.systemDefault())
        }.getOrNull()
    }
    val messageText = remember(message.id, message.timestamp) {
        runCatching { String(message.plaintext, Charsets.UTF_8) }.getOrDefault("")
    }

    var showMenu by remember { mutableStateOf(false) }
    val bubbleCtx = androidx.compose.ui.platform.LocalContext.current
    val sysClipboard = bubbleCtx
        .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(backgroundBrush)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showMenu = true }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    val bubbleTextColor = if (isDark) Color.White
                        else if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    val bubbleDimColor = if (isDark) Color.White.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    // G3: معرف المرسل في الفقاعة القديمة — اسم + Red ID للوارد.
                    if (!isMine && (senderName.isNotBlank() || senderRedId.isNotBlank())) {
                        Text(
                            text = senderName.ifBlank { senderRedId },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontFamily = PlexArabicFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (senderName.isNotBlank() && senderRedId.isNotBlank()) {
                            Text(
                                text = senderRedId,
                                color = bubbleDimColor,
                                fontSize = 10.sp,
                                fontFamily = PlexArabicFamily,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = messageText,
                        color = bubbleTextColor,
                        fontSize = 15.sp,
                        fontFamily = PlexArabicFamily,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = messageTime?.format(formatter) ?: "--:--",
                            color = bubbleDimColor,
                            fontSize = 11.sp,
                            fontFamily = PlexArabicFamily
                        )

                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // واتساب: ✓ رمادي (مرسَل) / ✓✓ رمادي (مستلَم) / ✓✓ أزرق (مقروء).
                            // المقروء بـ YounesReadTick (4.75:1 على الصادرة) لا أزرق عشوائي.
                            Text(
                                text = if (message.status == "READ" || message.status == "DELIVERED") "✓✓" else "✓",
                                color = if (message.status == "READ") YounesReadTick else bubbleDimColor,
                                fontSize = 10.sp,
                                fontFamily = PlexArabicFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("رد") },
                    onClick = {
                        showMenu = false
                        onReplyClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("نسخ") },
                    onClick = {
                        showMenu = false
                        val cm = sysClipboard ?: run {
                            android.widget.Toast.makeText(bubbleCtx, "الحافظة غير متاحة", android.widget.Toast.LENGTH_SHORT).show()
                            return@DropdownMenuItem
                        }
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("رسالة", messageText)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Paging3 لسجل المحادثة (2026-09-10): `Pager` حقيقي بدل `Flow<List>` الكامل.
 *
 * كان `ChatHistoryPagingSource` و`chatHistoryPager()` بلا أي استخدام
 * (`collectAsLazyPagingItems` صفر) — هذه العمود يفعّلهما:
 * صفحات 30 عنصرًا من `local_history` (الأحدث أولًا) مع `loadState`
 * كاملة (تحميل/خطأ/إعادة) وتمرير `key = id`.
 *
 * ترتيب العرض: المصدِر يُرجع الأحدث أولًا (DESC)، والقائمة تستخدم
 * `reverseLayout = true` فيكون الأحدث في الأسفل (سلوك واتساب) وتُحمَّل
 * الصفحات الأقدم بالتمرير للأعلى. بدون العكس كان الأحدث يظهر في الأعلى.
 *
 * الاستخدام: `ChatHistoryPagingColumn(conversationId = convId, ...)`
 * بدل تمرير `messages = repository.getLocalHistory(convId).collectAsState()`.
 * المسار القديم `ChatThreadScreen(messages: List<…>)` يبقى للتوافق
 * (RedDashboard يمرر `decrypted` المفكوكة تشفيريًا).
 */
@Composable
fun ChatHistoryPagingColumn(
    conversationId: String,
    modifier: Modifier = Modifier,
    pagingViewModel: ChatHistoryPagingViewModel = viewModel(),
    senderNames: Map<String, String> = emptyMap(),
    onReplyClick: (DecryptedMessage) -> Unit = {},
    reactionsByMessage: Map<String, Map<String, Int>> = emptyMap(),
    onReactionClick: (DecryptedMessage, String) -> Unit = { _, _ -> },
    onReplyStateChanged: (DecryptedMessage?) -> Unit = {}
) {
    val lazyItems = remember(conversationId) { pagingViewModel.pager(conversationId) }
        .collectAsLazyPagingItems()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    // P0-C: المسار الافتراضي — cachedIn(viewModelScope) في الـ ViewModel +
    // كاش مفكوك LRU (200) + تمرير واحد لآخر id (لا عاصفة عند الحذف/التحديث).
    val pagingListState = rememberLazyListState()
    var lastPinnedId by remember { mutableStateOf<String?>(null) }
    val latestPagingId: String? =
        if (lazyItems.itemCount > 0) runCatching { lazyItems[0]?.id }.getOrNull() else null
    LaunchedEffect(latestPagingId) {
        if (latestPagingId != null && latestPagingId != lastPinnedId) {
            lastPinnedId = latestPagingId
            pagingListState.scrollOnce(0)
        }
    }

    when {
        // التحميل الأولي — شاشة كاملة
        lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        // خطأ أولي — مع إعادة المحاولة
        lazyItems.loadState.refresh is LoadState.Error && lazyItems.itemCount == 0 -> {
            val message = (lazyItems.loadState.refresh as? LoadState.Error)?.error?.message
            Column(
                modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("تعذر تحميل السجل", fontWeight = FontWeight.Bold)
                if (!message.isNullOrBlank()) {
                    Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { lazyItems.retry() }) { Text("إعادة المحاولة") }
            }
        }
        // فارغ — لا رسائل بعد
        lazyItems.itemCount == 0 -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد رسائل بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            // حفظ replyTo في مسار Paging: لافتة داخلية + تمرير الاقتباس للفقاعة.
            var pagingReply by remember { mutableStateOf<LocalHistoryEntity?>(null) }
            LaunchedEffect(pagingReply) {
                pagingReply?.let {
                    onReplyStateChanged(
                        DecryptedMessage(
                            id = it.id,
                            conversationId = it.conversationId,
                            senderRedId = it.senderId,
                            plaintext = it.encryptedPlaintext,
                            timestamp = it.createdAt,
                            sequence = 0,
                            type = it.messageType,
                            outgoing = it.outgoing,
                            status = it.status
                        )
                    )
                } ?: onReplyStateChanged(null)
            }
            Column(modifier.fillMaxSize()) {
                pagingReply?.let { pr ->
                    val prText = pr.replyToMessageText
                        ?: remember(pr.id) { pagingViewModel.decryptedText(pr) }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(4.dp).height(32.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "الرد على ${pr.replyToSenderId?.take(8) ?: pr.senderId.take(8)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    prText.take(120),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { pagingReply = null }) {
                                Icon(Icons.Default.Close, "إلغاء الرد", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            LazyColumn(
                state = pagingListState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                // المصدِر DESC (الأحدث أولًا) — العكس يضع الأحدث في الأسفل.
                reverseLayout = true
            ) {
                items(
                    count = lazyItems.itemCount,
                    key = { index -> lazyItems[index]?.id ?: "placeholder-$index" }
                ) { index ->
                    val entity: LocalHistoryEntity? = lazyItems[index]
                    if (entity == null) {
                        // عنصر نائب أثناء التحميل التدريجي (placeholders معطلة، لكن null ممكن عابرًا)
                        Box(
                            Modifier.fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        )
                    } else {
                        // P0-C: كاش مفكوك LRU (200) في الـ ViewModel بدل فك UTF-8 كل تركيب.
                        val decoded = remember(entity.id, entity.createdAt) {
                            pagingViewModel.decryptedText(entity)
                        }
                        val timeText = remember(entity.id, entity.createdAt) {
                            runCatching {
                                LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(entity.createdAt),
                                    ZoneId.systemDefault()
                                ).format(timeFormatter)
                            }.getOrDefault("--:--")
                        }
                        // حفظ replyTo في مسار Paging: فك الغني + حقول الكيان (replyToMessageId/Text/Sender).
                        val pagingRich = remember(entity.id, entity.createdAt) {
                            if (entity.messageType == "RICH_TEXT") runCatching { RichMessage.decode(entity.encryptedPlaintext) }.getOrNull() else null
                        }
                        val pagingReplyId: String? = entity.replyToMessageId ?: pagingRich?.replyTo
                        val pagingReplyText: String? = entity.replyToMessageText
                        val pagingReplySender: String? = entity.replyToSenderId?.take(8)
                        fun pagingAsMessage(): DecryptedMessage = DecryptedMessage(
                            id = entity.id,
                            conversationId = entity.conversationId,
                            senderRedId = entity.senderId,
                            plaintext = entity.encryptedPlaintext,
                            timestamp = entity.createdAt,
                            sequence = 0,
                            type = entity.messageType,
                            outgoing = entity.outgoing,
                            status = entity.status
                        )
                        // shouldMergeWithPrevious + hideSenderHeader: السابق زمنيًا هو index+1 (المصدِر DESC).
                        val pagingPrev: DecryptedMessage? = remember(index, lazyItems.itemCount) {
                            runCatching { lazyItems[index + 1] } .getOrNull()?.let { prev ->
                                DecryptedMessage(
                                    id = prev.id, conversationId = prev.conversationId,
                                    senderRedId = prev.senderId, plaintext = prev.encryptedPlaintext,
                                    timestamp = prev.createdAt, sequence = 0, type = prev.messageType,
                                    outgoing = prev.outgoing, status = prev.status
                                )
                            }
                        }
                        val pagingHideHeader = remember(pagingPrev, entity.id) {
                            shouldMergeWithPrevious(pagingPrev, pagingAsMessage())
                        }
                        var pagingMenu by remember(entity.id) { mutableStateOf(false) }
                        var pagingInfo by remember(entity.id) { mutableStateOf(false) }
                        val pagingCtx = androidx.compose.ui.platform.LocalContext.current
                        val pagingClip = pagingCtx
                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val pagingScope = rememberCoroutineScope()
                        Box {
                            LuxuryChatBubble(
                                message = decoded,
                                isMe = entity.outgoing,
                                time = timeText,
                                status = entity.status,
                                // showMenu في onLongClick: رد/نسخ/تفاعل/معلومات.
                                onLongClick = { pagingMenu = true },
                                onSwipeReply = { pagingReply = entity },
                                messageId = entity.id,
                                senderName = if (entity.outgoing) "" else senderNames[entity.senderId].orEmpty(),
                                senderRedId = if (entity.outgoing) "" else entity.senderId,
                                fontFamily = ChatFontPolicy.familyFor(SettingsRuntime.current.fontFamily),
                                bubbleStyle = SettingsRuntime.current.bubbleStyle,
                                forwardOf = pagingRich?.forwardOf,
                                forwardCount = pagingRich?.forwardCount ?: 0,
                                richMessage = pagingRich,
                                replyToId = pagingReplyId,
                                replyToText = pagingReplyText,
                                replyToSender = pagingReplySender,
                                onReplyClick = pagingReplyId?.let { target ->
                                    {
                                        pagingScope.launch {
                                            val idx = (0 until lazyItems.itemCount).firstOrNull { i ->
                                                runCatching { lazyItems[i]?.id == target }.getOrDefault(false)
                                            }
                                            if (idx != null) runCatching { pagingListState.scrollOnce(idx) }
                                            else onReplyClick(pagingAsMessage())
                                        }
                                    }
                                },
                                reactions = reactionsByMessage[entity.id] ?: emptyMap(),
                                onReactionClick = { emoji -> onReactionClick(pagingAsMessage(), emoji) },
                                isEdited = false,
                                hideSenderHeader = pagingHideHeader
                            )
                            DropdownMenu(
                                expanded = pagingMenu,
                                onDismissRequest = { pagingMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("رد") },
                                    onClick = {
                                        pagingMenu = false
                                        pagingReply = entity
                                        onReplyClick(pagingAsMessage())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("نسخ") },
                                    onClick = {
                                        pagingMenu = false
                                        val cm = pagingClip ?: run {
                                            android.widget.Toast.makeText(pagingCtx, "الحافظة غير متاحة", android.widget.Toast.LENGTH_SHORT).show()
                                            return@DropdownMenuItem
                                        }
                                        cm.setPrimaryClip(
                                            android.content.ClipData.newPlainText("رسالة", decoded)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("تفاعل 👍") },
                                    onClick = {
                                        pagingMenu = false
                                        onReactionClick(pagingAsMessage(), "👍")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("معلومات") },
                                    onClick = {
                                        pagingMenu = false
                                        pagingInfo = true
                                    }
                                )
                            }
                            if (pagingInfo) {
                                AlertDialog(
                                    onDismissRequest = { pagingInfo = false },
                                    title = { Text("معلومات الرسالة", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MessageInfoLine("المعرف", entity.id.take(16))
                                            MessageInfoLine("الوقت", timeText)
                                            MessageInfoLine("الحالة", entity.status)
                                            MessageInfoLine("المرسل", if (entity.outgoing) "أنت" else entity.senderId.take(16))
                                            if (pagingReplyId != null) MessageInfoLine("رد على", pagingReplyId.take(16))
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { pagingInfo = false }) { Text("إغلاق") } }
                                )
                            }
                        }
                    }
                }
                // تذييل التحميل التدريجي (append)
                when (val append = lazyItems.loadState.append) {
                    is LoadState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                    is LoadState.Error -> item {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تعذر تحميل المزيد", fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { lazyItems.retry() }) { Text("إعادة") }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
}

@Composable
private fun MessageInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 56.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
