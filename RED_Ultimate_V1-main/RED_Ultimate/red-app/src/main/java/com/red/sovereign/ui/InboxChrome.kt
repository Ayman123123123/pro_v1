package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.contacts.PublicRedProfile
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

/**
 * شريط حضور الأصدقاء — في قائمة الدردشات فقط.
 * الضغط يفتح المحادثة كاملة. الضغط الطويل يفتح ورقة الصديق.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FriendsPresenceRail(
    people: List<PublicRedProfile>,
    isOnline: (String) -> Boolean,
    onOpen: (PublicRedProfile) -> Unit,
    onLongPress: (PublicRedProfile) -> Unit,
    onAdd: () -> Unit,
) {
    if (people.isEmpty()) return
    val onlineCount = people.count { isOnline(it.redId) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الأصدقاء", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (onlineCount > 0) {
                Text(
                    "  $onlineCount متصل",
                    color = YounesEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(Modifier.weight(1f))
            Text(
                "إضافة",
                color = YounesEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdd).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "add-friend") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).clickable(onClick = onAdd),
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "إضافة صديق", tint = YounesEmerald) }
                    Text("جديد", fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(people, key = { it.redId }) { person ->
                val online = isOnline(person.redId)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(68.dp).combinedClickable(
                        onClick = { onOpen(person) },
                        onLongClick = { onLongPress(person) },
                    ),
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .then(
                                    if (online) Modifier.border(2.dp, YounesEmerald, CircleShape)
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                person.displayName.take(1).ifBlank { "?" },
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = if (online) YounesEmerald else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (online) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00C98C))
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                    Text(
                        person.displayName,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = if (online) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
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
