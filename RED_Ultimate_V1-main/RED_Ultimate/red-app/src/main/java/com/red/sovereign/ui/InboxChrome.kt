package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.ChatFolder
import com.red.sovereign.core.InboxFilter
import com.red.sovereign.ui.theme.YounesEmerald

data class ConversationActionState(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val archived: Boolean,
    val muted: Boolean,
    val favorite: Boolean,
    val locked: Boolean,
    val unread: Boolean
)

@Composable
fun InboxFilterBar(
    selected: InboxFilter,
    customFolders: List<ChatFolder>,
    selectedFolderId: String?,
    unreadCount: Int,
    archivedCount: Int,
    onFilter: (InboxFilter) -> Unit,
    onFolder: (String?) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InboxFilter.entries.forEach { filter ->
            val count = when (filter) {
                InboxFilter.UNREAD -> unreadCount
                InboxFilter.ARCHIVED -> archivedCount
                else -> 0
            }
            val label = if (count > 0 && filter != InboxFilter.ALL) "${filter.label} $count" else filter.label
            FilterChip(
                selected = selected == filter && selectedFolderId == null,
                onClick = { onFolder(null); onFilter(filter) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YounesEmerald.copy(alpha = 0.22f),
                    selectedLabelColor = YounesEmerald
                )
            )
        }
        customFolders.forEach { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onFolder(folder.id); onFilter(InboxFilter.ALL) },
                label = { Text(folder.name, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun InboxSearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("بحث في الدردشات…") },
        leadingIcon = { Icon(Icons.Default.Search, null) }
    )
}

@Composable
fun SavedMessagesRow(onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Bookmark, "رسائلي", tint = YounesEmerald) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("رسائلي", fontWeight = FontWeight.SemiBold)
                Text("ملاحظات مشفّرة على هذا الجهاز", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ArchiveEntryRow(count: Int, onOpen: () -> Unit) {
    if (count <= 0) return
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Archive, null, tint = YounesEmerald)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("المؤرشفة", fontWeight = FontWeight.SemiBold)
                Text("$count محادثة مخفية عن القائمة الرئيسية", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionSheet(
    state: ConversationActionState,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMute: (Long) -> Unit,
    onUnmute: () -> Unit,
    onFavorite: () -> Unit,
    onLock: () -> Unit,
    onUnread: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("تنظيم سريع كما في واتساب وتلجرام — محلياً ومشفّراً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            ActionRow(Icons.Default.PushPin, if (state.pinned) "إلغاء التثبيت" else "تثبيت أعلى القائمة", "الوصول السريع للمحادثة المهمة", onPin)
            ActionRow(if (state.favorite) Icons.Default.Star else Icons.Default.StarBorder, if (state.favorite) "إزالة من المفضلة" else "إضافة للمفضلة", "فلتر المفضلة في تبويب الدردشات", onFavorite)
            ActionRow(if (state.archived) Icons.Default.Unarchive else Icons.Default.Archive, if (state.archived) "إلغاء الأرشفة" else "أرشفة", "إخفاء دون حذف السجل المشفر", onArchive)
            ActionRow(Icons.Default.MarkChatUnread, if (state.unread) "تعيين كمقروء" else "تعيين كغير مقروء", "للتذكير بالرجوع لاحقاً", onUnread)
            ActionRow(if (state.locked) Icons.Default.LockOpen else Icons.Default.Lock, if (state.locked) "إلغاء قفل المحادثة" else "قفل المحادثة", "بصمة الجهاز قبل فتح الرسائل", onLock)
            if (state.muted) {
                ActionRow(Icons.Default.NotificationsOff, "إلغاء الكتم", "إعادة تنبيهات هذه المحادثة", onUnmute)
            } else {
                Text("كتم الإشعارات", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onMute(8L * 60 * 60 * 1000) }, Modifier.weight(1f)) { Text("8 ساعات", fontSize = 12.sp) }
                    OutlinedButton({ onMute(7L * 24 * 60 * 60 * 1000) }, Modifier.weight(1f)) { Text("أسبوع", fontSize = 12.sp) }
                    OutlinedButton({ onMute(Long.MAX_VALUE / 2) }, Modifier.weight(1f)) { Text("دائماً", fontSize = 12.sp) }
                }
            }
            ActionRow(Icons.Default.Delete, "حذف المحادثة", "يحذف السجل المحلي فقط من هذا الجهاز", onDelete)
            TextButton(onDismiss, Modifier.align(Alignment.CenterHorizontally)) { Text("إغلاق") }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(YounesEmerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = YounesEmerald, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LockedChatGate(title: String, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Lock, null, tint = YounesEmerald, modifier = Modifier.size(48.dp))
        Text("محادثة مقفلة", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("أكد هويتك لعرض الرسائل المشفّرة.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        androidx.compose.material3.Button(onUnlock) { Text("فتح بالبصمة") }
    }
}
