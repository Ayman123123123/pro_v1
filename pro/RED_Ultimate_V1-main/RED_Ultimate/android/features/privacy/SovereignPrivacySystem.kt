package com.red.sovereign.features.privacy

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.core.theme.SovereignColors

/**
 * 🔒 YOUNES Sovereign Privacy System
 * نظام الخصوصية السيادي — من يستطيع رؤية ماذا
 */

// ━━━━━━━━━━━━ نموذج الخصوصية ━━━━━━━━━━━━

enum class PrivacyLevel(val label: String, val icon: ImageVector, val description: String) {
    EVERYONE("الجميع", Icons.Rounded.Public, "أي شخص يمكنه الرؤية"),
    CONTACTS("جهات الاتصال", Icons.Rounded.Contacts, "فقط من في جهات الاتصال"),
    CONTACTS_EXCEPT("جهات الاتصال باستثناء", Icons.Rounded.Group, "جهات الاتصال مع استثناءات"),
    ONLY_SHARE_WITH("مشاركة مع", Icons.Rounded.People, "فقط أشخاص محددين"),
    NOBODY("لا أحد", Icons.Rounded.Lock, "لن يرى أحد")
}

data class PrivacySettings(
    val lastSeen: PrivacyLevel = PrivacyLevel.EVERYONE,
    val profilePhoto: PrivacyLevel = PrivacyLevel.EVERYONE,
    val about: PrivacyLevel = PrivacyLevel.EVERYONE,
    val status: PrivacyLevel = PrivacyLevel.CONTACTS,
    val readReceipts: PrivacyLevel = PrivacyLevel.EVERYONE,
    val calls: PrivacyLevel = PrivacyLevel.CONTACTS,
    val groups: PrivacyLevel = PrivacyLevel.EVERYONE,
    val liveLocation: PrivacyLevel = PrivacyLevel.NOBODY,
    val onlineStatus: PrivacyLevel = PrivacyLevel.EVERYONE
)

data class StatusPrivacyItem(
    val setting: String,
    val currentLevel: PrivacyLevel,
    val icon: ImageVector,
    val arabicLabel: String
)

// ━━━━━━━━━━━━ شاشة الخصوصية ━━━━━━━━━━━━

@Composable
fun PrivacySettingsScreen(
    settings: PrivacySettings = PrivacySettings(),
    onSettingChange: (String, PrivacyLevel) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val privacyItems = listOf(
        StatusPrivacyItem("lastSeen", settings.lastSeen, Icons.Rounded.Schedule, "آخر ظهور"),
        StatusPrivacyItem("onlineStatus", settings.onlineStatus, Icons.Rounded.Circle, "الحالة المتصلة"),
        StatusPrivacyItem("profilePhoto", settings.profilePhoto, Icons.Rounded.Face, "صورة الملف"),
        StatusPrivacyItem("about", settings.about, Icons.Rounded.Info, "نبذة"),
        StatusPrivacyItem("status", settings.status, Icons.Rounded.EmojiEmotions, "الحالة"),
        StatusPrivacyItem("readReceipts", settings.readReceipts, Icons.Rounded.DoneAll, "إيصالات القراءة"),
        StatusPrivacyItem("calls", settings.calls, Icons.Rounded.Call, "المكالمات"),
        StatusPrivacyItem("groups", settings.groups, Icons.Rounded.Groups, "إضافتي للمجموعات"),
        StatusPrivacyItem("liveLocation", settings.liveLocation, Icons.Rounded.LocationOn, "الموقع المباشر")
    )

    var expandedItem by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
            .padding(16.dp)
    ) {
        // الرأس
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "الخصوصية والأمان",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))

        // رسالة توعية
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SovereignColors.Cyan.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, SovereignColors.Cyan.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Shield, null, tint = SovereignColors.Cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "إعداداتك السيادية محمية بتشفير طرفي. لن يستطيع أي طرف ثالث رؤية بياناتك.",
                    fontSize = 12.sp,
                    color = SovereignColors.Cyan.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // قائمة الإعدادات
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(privacyItems) { item ->
                PrivacySettingCard(
                    item = item,
                    isExpanded = expandedItem == item.setting,
                    onExpand = { expandedItem = if (expandedItem == item.setting) null else item.setting },
                    onSelect = { onSettingChange(item.setting, it) }
                )
            }
        }
    }
}

@Composable
private fun PrivacySettingCard(
    item: StatusPrivacyItem,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onSelect: (PrivacyLevel) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // الصف الرئيسي
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // الأيقونة
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SovereignColors.Cyan.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(12.dp))

                // النص
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.arabicLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(item.currentLevel.label, fontSize = 12.sp, color = Color.Gray)
                }

                // السهم
                Icon(
                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, tint = Color.Gray
                )
            }

            // خيارات الخصوصية
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrivacyLevel.entries.forEach { level ->
                        val isSelected = level == item.currentLevel
                        Surface(
                            onClick = { onSelect(level) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    level.icon, null,
                                    tint = if (isSelected) SovereignColors.Cyan else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        level.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (isSelected) SovereignColors.Cyan else Color.White
                                    )
                                    Text(level.description, fontSize = 11.sp, color = Color.Gray)
                                }
                                if (isSelected) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Cyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ الحالة/Status مع الخصوصية ━━━━━━━━━━━━

enum class StatusType(val label: String, val emoji: String) {
    ONLINE("متصل", "🟢"),
    OFFLINE("غير متصل", "⚪"),
    BUSY("مشغول", "🔴"),
    AWAY("بعيد", "🟡"),
    DO_NOT_DISTURB("لا تزعجني", "⛔"),
    INVISIBLE("مخفي", "👻")
}

data class UserStatus(
    val type: StatusType,
    val customText: String = "",
    val expiryMinutes: Int? = null, // null = دائم
    val visibleTo: PrivacyLevel = PrivacyLevel.EVERYONE
)

@Composable
fun StatusPickerDialog(
    currentStatus: UserStatus = UserStatus(StatusType.ONLINE),
    onStatusChange: (UserStatus) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(currentStatus.type) }
    var customText by remember { mutableStateOf(currentStatus.customText) }
    var selectedPrivacy by remember { mutableStateOf(currentStatus.visibleTo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEmotions, null, tint = SovereignColors.Gold)
                Spacer(Modifier.width(8.dp))
                Text("تحديث حالتك", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // أنواع الحالات
                StatusType.entries.forEach { status ->
                    val isSelected = status == selectedType
                    Surface(
                        onClick = { selectedType = status },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) SovereignColors.Gold.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) SovereignColors.Gold else Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(status.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(status.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // نص مخصص
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("أضف نص حالة... (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(4.dp))

                // من يستطيع رؤيتها
                Text("من يستطيع رؤية حالتك:", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PrivacyLevel.EVERYONE, PrivacyLevel.CONTACTS, PrivacyLevel.NOBODY).forEach { level ->
                        val isSelected = level == selectedPrivacy
                        Surface(
                            onClick = { selectedPrivacy = level },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(level.icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(level.label, fontSize = 11.sp, color = if (isSelected) SovereignColors.Cyan else Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStatusChange(UserStatus(selectedType, customText, visibleTo = selectedPrivacy))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Gold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تحديث", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
        containerColor = SovereignColors.Navy
    )
}
