package com.red.sovereign.features.chat

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.red.sovereign.core.database.MessageReactionEntity
import com.red.sovereign.core.database.RedDao
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import kotlinx.coroutines.launch

/**
 * 🎭 YOUNES Sovereign — Reactions System
 *
 * ميزات:
 * - اختيار إيموجي سريع (6 إيموجيات افتراضية)
 * - منتقي إيموجي كامل (emoji2-emojipicker)
 * - إيموجيات مخصصة لكل محادثة/مجموعة
 * - ردود فعل سريعة (Quick Reactions) عند الضغط المطول
 * - عرض التفاعلات مجمعة مع عدد لكل إيموجي
 * - E2EE: الإيموجي مخزن محلياً بعد فك التشفير، لا يصل للخادم
 */

// إيموجيات سريعة افتراضية (WhatsApp/Telegram style)
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// فئات الإيموجي للمنتقي الكامل
private val EMOJI_CATEGORIES = mapOf(
    "Smileys" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "☺️", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔"),
    "Gestures" to listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤏", "💪", "🦾", "🦵", "🦿", "🦶", "👂", "👃", "🧠", "🦷", "🦴", "👀", "👁️"),
    "Hearts" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️", "🧿"),
    "Objects" to listOf("🎉", "🎊", "🎈", "🎁", "🎀", "🏆", "🥇", "🥈", "🥉", "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🔮", "🪄", "🎮", "🕹️", "🎰", "🎯", "🎲", "🧩", "🧸", "🪅", "🪆"),
    "Nature" to listOf("🌞", "🌝", "🌚", "🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘", "🌙", "🌎", "🌍", "🌏", "🪐", "☀️", "⭐", "🌟", "🌠", "☁️", "⛅", "🌤️", "🌥️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️"),
    "Flags" to listOf("🏳️", "🏴", "🏁", "🚩", "🏳️‍🌈", "🏳️‍⚧️", "🇸🇦", "🇦🇪", "🇶🇦", "🇰🇼", "🇧🇭", "🇴🇲", "🇯🇴", "🇱🇧", "🇵🇸", "🇪🇬", "🇲🇦", "🇹🇳", "🇩🇿", "🇱🇾")
)

/**
 * شاشة منتقي الإيموجي الكامل
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullEmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(EMOJI_CATEGORIES.keys.first()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Text("اختر إيموجي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (!showSearch) {
                IconButton(onClick = { showSearch = true }) {
                    Icon(Icons.Default.Search, "بحث", tint = AqyalGold, modifier = Modifier.size(24.dp))
                }
            } else {
                IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                    Icon(Icons.Default.Close, "إخفاء البحث", tint = AqyalGold, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Search field
        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("ابحث عن إيموجي...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AqyalGold) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AqyalGold,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Category tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(EMOJI_CATEGORIES.keys.toList()) { category ->
                val isSelected = category == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = { Text(category, style = MaterialTheme.typography.labelMedium, color = if (isSelected) Color.White else Color.Gray) },
                    colors = FilterChipDefaults.colors(
                        selectedContainerColor = AqyalGold.copy(alpha = 0.2f),
                        unselectedContainerColor = Color(0xFF1E293B)
                    )
                )
            }
        }

        // Emoji grid
        val emojis = EMOJI_CATEGORIES[selectedCategory] ?: emptyList()
        val filteredEmojis = if (searchQuery.isNotBlank()) {
            emojis.filter { it.contains(searchQuery, ignoreCase = true) }
        } else emojis

        androidx.compose.foundation.lazy.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredEmojis, key = { it }) { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onEmojiSelected(emoji) }
                        .background(if (emoji.isNotBlank()) Color(0xFF1E293B) else Color.Transparent, RoundedCornerShape(8.dp))
                        .wrapContentSize(Alignment.Center)
                )
            }
        }
    }
}

/**
 * شريط ردود الفعل السريعة (Quick Reactions) - يظهر عند الضغط المطول على رسالة
 */
@Composable
fun QuickReactionsBar(
    messageId: String,
    conversationId: String,
    currentUserId: String,
    repository: LocalRepository,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit
) {
    val existingReactions = repository.getReactionsForMessage(messageId)
    val myReaction = existingReactions.firstOrNull { it.senderId == currentUserId }?.emoji

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(SovereignColors.SurfaceNavy.copy(alpha = 0.95f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                val isSelected = myReaction == emoji
                val count = existingReactions.count { it.emoji == emoji }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            onReactionSelected(emoji)
                            onDismiss()
                        }
                        .background(
                            if (isSelected) AqyalGold.copy(alpha = 0.2f) else Color.Transparent,
                            CircleShape
                        )
                        .border(if (isSelected) BorderStroke(2.dp, AqyalGold) else BorderStroke(0.dp, Color.Transparent), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                    if (count > 0) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) AqyalGold else Color.Gray,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = -4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // زر "المزيد" لفتح المنتقي الكامل
            IconButton(onClick = { /* TODO: فتح FullEmojiPicker */ }) {
                Icon(Icons.Default.MoreHoriz, "المزيد", tint = AqyalGold, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * عرض التفاعلات على الرسالة (Reaction Summary)
 */
@Composable
fun ReactionSummary(
    reactions: List<MessageReactionEntity>,
    currentUserId: String,
    onReactionClick: (String) -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    if (reactions.isEmpty()) return

    // تجميع التفاعلات حسب الإيموجي
    val grouped = reactions.groupBy { it.emoji }
        .map { (emoji, list) ->
            val senders = list.map { it.senderId }.distinct()
            val myReaction = senders.contains(currentUserId)
            ReactionGroup(emoji = emoji, count = senders.size, myReaction = myReaction, senders = senders.take(3))
        }
        .sortedByDescending { it.count }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onLongClick = onLongClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        grouped.forEach { group ->
            ReactionChip(
                group = group,
                onClick = { onReactionClick(group.emoji) }
            )
        }
    }
}

data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val myReaction: Boolean,
    val senders: List<String>
)

@Composable
fun ReactionChip(
    group: ReactionGroup,
    onClick: () -> Unit
) {
    val bgColor = if (group.myReaction) AqyalGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (group.myReaction) AqyalGold else Color.Transparent
    val textColor = if (group.myReaction) AqyalGold else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp).height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(group.emoji, fontSize = 14.sp)
            if (group.count > 1) {
                Text(group.count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}

/**
 * ViewModel لإدارة التفاعلات
 */
class ReactionsViewModel(
    private val repository: LocalRepository,
    private val conversationId: String,
    private val currentUserId: String
) {
    private val _reactions = MutableStateFlow<List<MessageReactionEntity>>(emptyList())
    val reactions: StateFlow<List<MessageReactionEntity>> = _reactions

    fun loadReactions(messageId: String) {
        _reactions.value = repository.getReactionsForMessage(messageId)
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val existing = _reactions.value.firstOrNull { it.messageId == messageId && it.senderId == currentUserId }

        if (existing != null) {
            if (existing.emoji == emoji) {
                // إزالة التفاعل
                repository.deleteReaction(messageId, currentUserId)
                _reactions.value = _reactions.value.filter { it.messageId != messageId || it.senderId != currentUserId }
            } else {
                // استبدال الإيموجي
                repository.deleteReaction(messageId, currentUserId)
                val newReaction = MessageReactionEntity(
                    messageId = messageId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    emoji = emoji,
                    timestamp = System.currentTimeMillis()
                )
                repository.upsertReaction(newReaction)
                _reactions.value = _reactions.value.filter { it.messageId != messageId || it.senderId != currentUserId } + newReaction
            }
        } else {
            // إضافة تفاعل جديد
            val newReaction = MessageReactionEntity(
                messageId = messageId,
                conversationId = conversationId,
                senderId = currentUserId,
                emoji = emoji,
                timestamp = System.currentTimeMillis()
            )
            repository.upsertReaction(newReaction)
            _reactions.value = _reactions.value + newReaction
        }
    }
}
