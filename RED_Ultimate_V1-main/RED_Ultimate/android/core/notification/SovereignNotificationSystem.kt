package com.red.sovereign.core.notification

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.core.theme.SovereignColors

/**
 * 🔔 YOUNES Sovereign Notification System
 * نظام الإشعارات السيادي — أنواع متعددة + تصنيف + إجراءات
 */

// ━━━━━━━━━━━━ أنواع الإشعارات ━━━━━━━━━━━━

enum class NotificationType(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val channel: String
) {
    // الرسائل
    NEW_MESSAGE("رسالة جديدة", Icons.Rounded.ChatBubble, SovereignColors.VoipBlue, "messages"),
    GROUP_MESSAGE("رسالة مجموعة", Icons.Rounded.Groups, SovereignColors.Success, "messages"),
    MENTION("إشارة إليك", Icons.Rounded.AlternateEmail, SovereignColors.Cyan, "messages"),

    // المكالمات
    INCOMING_CALL("مكالمة واردة", Icons.Rounded.Call, SovereignColors.VoipBlue, "calls"),
    MISSED_CALL("مكالمة فائتة", Icons.Rounded.PhoneMissed, SovereignColors.Danger, "calls"),
    PSTN_CALL("مكالمة خطية", Icons.Rounded.SimCard, SovereignColors.DinstarGold, "calls"),

    // القصص
    STORY_VIEW("مشاهدة قصتك", Icons.Rounded.Visibility, SovereignColors.Cyan, "stories"),
    STORY_REPLY("رد على قصتك", Icons.Rounded.Reply, SovereignColors.VoipBlue, "stories"),

    // المجموعات
    GROUP_INVITE("دعوة مجموعة", Icons.Rounded.GroupAdd, SovereignColors.Success, "groups"),
    GROUP_UPDATE("تحديث مجموعة", Icons.Rounded.Info, Color.Gray, "groups"),
    ROLE_CHANGE("تغيير دورك", Icons.Rounded.Shield, SovereignColors.Gold, "groups"),

    // البث
    LIVE_STARTED("بث مباشر بدأ", Icons.Rounded.LiveTv, SovereignColors.LiveRed, "live"),
    SPACE_STARTED("غرفة صوتية بدأت", Icons.Rounded.Mic, SovereignColors.SpacePurple, "live"),

    // النظام
    SECURITY_ALERT("تنبيه أمني", Icons.Rounded.Security, SovereignColors.Danger, "security"),
    DEVICE_NEW("جهاز جديد", Icons.Rounded.Devices, SovereignColors.Warning, "security"),
    UPDATE_AVAILABLE("تحديث متاح", Icons.Rounded.SystemUpdate, SovereignColors.Cyan, "system"),

    // Dinstar
    DINSTAR_STATUS("حالة Dinstar", Icons.Rounded.Router, SovereignColors.DinstarGold, "dinstar"),
    DINSTAR_ALERT("تنبيه Dinstar", Icons.Rounded.Warning, SovereignColors.Danger, "dinstar")
}

enum class NotificationPriority { URGENT, HIGH, NORMAL, LOW }

data class SovereignNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val isRead: Boolean = false,
    val actionLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val groupId: String? = null, // لتجميع إشعارات متعددة
    val threadId: String? = null // لفتح المحادثة/المكالمة
)

// ━━━━━━━━━━━━ مركز الإشعارات ━━━━━━━━━━━━

@Composable
fun SovereignNotificationCenter(
    notifications: List<SovereignNotification> = emptyList(),
    onNotificationClick: (SovereignNotification) -> Unit = {},
    onActionClick: (SovereignNotification, String) -> Unit = { _, _ -> },
    onDismiss: (SovereignNotification) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }
    val unreadCount = notifications.count { !it.isRead }

    Column(
        modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian)
    ) {
        // الرأس
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("الإشعارات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (unreadCount > 0) {
                    Text("$unreadCount غير مقروء", fontSize = 12.sp, color = SovereignColors.Cyan)
                }
            }
            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Text("قراءة الكل", color = SovereignColors.Cyan, fontSize = 13.sp)
                }
            }
        }

        // فلاتر سريعة
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("الكل") }
            )
            listOf(
                NotificationType.NEW_MESSAGE to "الرسائل",
                NotificationType.INCOMING_CALL to "المكالمات",
                NotificationType.GROUP_INVITE to "المجموعات",
                NotificationType.SECURITY_ALERT to "الأمان"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = selectedFilter == type,
                    onClick = { selectedFilter = if (selectedFilter == type) null else type },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = type.color.copy(alpha = 0.15f),
                        selectedLabelColor = type.color
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // قائمة الإشعارات
        val filtered = if (selectedFilter != null) {
            notifications.filter { it.type == selectedFilter || it.type.channel == selectedFilter?.channel }
        } else notifications

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("لا توجد إشعارات", color = Color.Gray, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { notification ->
                    SovereignNotificationItem(
                        notification = notification,
                        onClick = { onNotificationClick(notification) },
                        onAction = { action -> onActionClick(notification, action) },
                        onDismiss = { onDismiss(notification) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SovereignNotificationItem(
    notification: SovereignNotification,
    onClick: () -> Unit,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bgAlpha = if (notification.isRead) 0.03f else 0.08f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = notification.type.color.copy(alpha = bgAlpha)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // أيقونة النوع
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(notification.type.color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(notification.type.icon, null, tint = notification.type.color, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            // المحتوى
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notification.title,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!notification.isRead) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).background(notification.type.color, CircleShape))
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    notification.body,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // إجراءات
                if (notification.actionLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onAction(notification.actionLabel) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(notification.actionLabel, color = notification.type.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        notification.secondaryActionLabel?.let { secondary ->
                            TextButton(
                                onClick = { onAction(secondary) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(secondary, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // الوقت + حذف
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTimeAgo(notification.timestamp), fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ بناء الإشعارات — Builder Pattern ━━━━━━━━━━━━

class SovereignNotificationBuilder {
    private var notification = SovereignNotification(
        id = "n_${System.currentTimeMillis()}"
    )

    fun type(type: NotificationType) = apply { notification = notification.copy(type = type) }
    fun title(title: String) = apply { notification = notification.copy(title = title) }
    fun body(body: String) = apply { notification = notification.copy(body = body) }
    fun sender(name: String, avatar: String? = null) = apply { notification = notification.copy(senderName = name, senderAvatar = avatar) }
    fun priority(priority: NotificationPriority) = apply { notification = notification.copy(priority = priority) }
    fun action(label: String, secondary: String? = null) = apply { notification = notification.copy(actionLabel = label, secondaryActionLabel = secondary) }
    fun threadId(id: String) = apply { notification = notification.copy(threadId = id) }

    fun build() = notification
}

// ─── مساعد الوقت ───
private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "الآن"
        diff < 3600_000 -> "${diff / 60_000}د"
        diff < 86400_000 -> "${diff / 3600_000}س"
        else -> "${diff / 86400_000}ي"
    }
}
