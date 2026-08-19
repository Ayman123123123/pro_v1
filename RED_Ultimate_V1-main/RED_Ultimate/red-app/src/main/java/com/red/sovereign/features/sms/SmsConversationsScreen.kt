package com.red.sovereign.features.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة المحادثات SMS الاحترافية — قائمة محادثات مع بحث وعدّاد غير مقروء.
 */
@Composable
fun SmsConversationsScreen(vm: SmsViewModel, onOpenChat: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.start() }

    Column(Modifier.fillMaxSize()) {
        // Header
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = AqyalGold, modifier = Modifier.size(34.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("الرسائل النصية", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Text(if (vm.connected) "متصل — تحديث فوري" else "غير متصل — التحديث الدوري", fontSize = 12.sp,
                        color = if (vm.connected) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "تحديث", tint = AqyalGold) }
            }
        }

        // Search
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = vm::onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            placeholder = { Text("ابحث في المحادثات…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        // Content
        if (vm.loading && vm.conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
        } else if (vm.filteredConversations.isEmpty()) {
            EmptyState("لا توجد محادثات", "ابدأ بإرسال رسالة لأي رقم يمني")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.filteredConversations, key = { it.number }) { conv ->
                    ConversationRow(conv) { onOpenChat(conv.number) }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: SmsConversationDto, onClick: () -> Unit) {
    val op = conv.operator?.let { runCatching { com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(conv.number) }.getOrNull() }
    val timeText = conv.lastTime?.let { formatSmsTime(it) } ?: ""
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = YounesEmerald, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conv.number, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    op?.let { Text(it.name, color = it.brandColor, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp)) }
                }
                Text(conv.lastText.orEmpty().take(70), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (conv.unreadCount > 0) {
                        Box(Modifier.clip(CircleShape).background(AqyalGold).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("${conv.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSmsTime(epochSeconds: Long): String {
    val d = Date(epochSeconds * 1000)
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(d)
}

@Composable
private fun EmptyState(title: String, sub: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Chat, null, tint = AqyalGold.copy(alpha = .5f), modifier = Modifier.size(64.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
