package com.red.sovereign.features.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.core.database.StarredMessageEntity
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة الرسائل المُعلَّمة (Starred/Bookmarked Messages).
 *
 * تعرض جميع الرسائل التي علّمها المستخدم بعلامة نجمة، مرتبة من الأحدث إلى الأقدم.
 * يمكن للمستخدم إلغاء تعليمة أي رسالة أو حذفها محلياً.
 *
 * الميزة تتفوق على واتساب: لا يوجد "الرسائل المُعلَّمة" في واتساب.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    repository: LocalRepository,
    onBack: () -> Unit,
    onMessageClick: (messageId: String, conversationId: String) -> Unit = { _, _ -> }
) {
    val starredMessages by repository.getAllStarredMessages().collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = AqyalGold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الرسائل المُعلَّمة", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("${starredMessages.size}", color = AqyalCyanGlow, fontSize = 13.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "عودة", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (starredMessages.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("لا توجد رسائل مُعلَّمة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("علّم الرسائل بالضغط على النجمة في قائمة الإجراءات", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(starredMessages, key = { it.messageId }) { starred ->
                    StarredMessageItem(
                        starred = starred,
                        timeFormat = timeFormat,
                        onClick = { onMessageClick(starred.messageId, starred.conversationId) },
                        onUnstar = {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.unstarMessage(starred.messageId)
                            }
                        },
                        onDelete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                repository.deleteLocalMessage(starred.messageId)
                                repository.unstarMessage(starred.messageId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredMessageItem(
    starred: StarredMessageEntity,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onUnstar: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, null, tint = AqyalGold, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = starred.senderId.take(16),
                        fontSize = 12.sp,
                        color = AqyalCyanGlow,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = timeFormat.format(Date(starred.starredAt)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onUnstar, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Star, "إلغاء التعليمة", tint = AqyalGold, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = starred.messageText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "محادثة: ${starred.conversationId.take(16)}...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
