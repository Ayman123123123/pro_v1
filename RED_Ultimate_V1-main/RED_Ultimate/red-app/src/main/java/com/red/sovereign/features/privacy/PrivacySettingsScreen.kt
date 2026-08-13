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
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.remember
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
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🔒 YOUNES Sovereign Privacy System
 */

enum class PrivacyLevel(val label: String, val icon: ImageVector, val description: String) {
    EVERYONE("الجميع", Icons.Rounded.Public, "أي شخص يمكنه الرؤية"),
    CONTACTS("جهات الاتصال", Icons.Rounded.Contacts, "فقط من في جهات الاتصال"),
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

data class StatusPrivacyItem(val setting: String, val currentLevel: PrivacyLevel, val icon: ImageVector, val arabicLabel: String)

@Composable
fun PrivacySettingsScreen(
    settings: PrivacySettings = PrivacySettings(),
    onSettingChange: (String, PrivacyLevel) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val settingsVm: com.red.sovereign.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val liveSettings = remember(settingsVm.state, settings) {
        settings.copy(
            lastSeen = visibilityOf(settingsVm.state.lastSeenVisibility, settings.lastSeen),
            profilePhoto = visibilityOf(settingsVm.state.profilePhotoVisibility, settings.profilePhoto),
            about = visibilityOf(settingsVm.state.aboutVisibility, settings.about),
            readReceipts = if (settingsVm.state.readReceipts) PrivacyLevel.EVERYONE else PrivacyLevel.NOBODY,
            calls = visibilityOf(settingsVm.state.whoCanCall, settings.calls),
            groups = visibilityOf(settingsVm.state.whoCanAddToGroups, settings.groups),
            onlineStatus = if (settingsVm.state.hideLastSeen) PrivacyLevel.NOBODY else PrivacyLevel.EVERYONE
        )
    }
    val persist: (String, PrivacyLevel) -> Unit = { key, level ->
        when (key) {
            "lastSeen" -> settingsVm.setLastSeenVisibility(level.name)
            "onlineStatus" -> settingsVm.setHideLastSeen(level == PrivacyLevel.NOBODY)
            "profilePhoto" -> settingsVm.setProfilePhotoVisibility(level.name)
            "readReceipts" -> settingsVm.setReadReceipts(level != PrivacyLevel.NOBODY)
            "calls" -> settingsVm.setWhoCanCall(level.name)
            "groups" -> settingsVm.setWhoCanAddToGroups(level.name)
            "about" -> settingsVm.setAboutVisibility(level.name)
        }
        onSettingChange(key, level)
    }
    val privacyItems = listOf(
        StatusPrivacyItem("lastSeen", liveSettings.lastSeen, Icons.Rounded.Schedule, "آخر ظهور"),
        StatusPrivacyItem("onlineStatus", liveSettings.onlineStatus, Icons.Rounded.Circle, "الحالة المتصلة"),
        StatusPrivacyItem("profilePhoto", liveSettings.profilePhoto, Icons.Rounded.Face, "صورة الملف الشخصي"),
        StatusPrivacyItem("readReceipts", liveSettings.readReceipts, Icons.Rounded.DoneAll, "إيصالات القراءة"),
        StatusPrivacyItem("calls", liveSettings.calls, Icons.Rounded.Call, "المكالمات الواردة"),
        StatusPrivacyItem("groups", liveSettings.groups, Icons.Rounded.Groups, "من يضيفني للمجموعات")
    )

    var expandedItem by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("الخصوصية والسيادة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.Cyan.copy(alpha = 0.08f), border = BorderStroke(1.dp, SovereignColors.Cyan.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = SovereignColors.Cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("إعداداتك السيادية لا تغادر هذا الجهاز إلا مشفرة بمفاتيحك الخاصة.", fontSize = 12.sp, color = SovereignColors.Cyan)
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(privacyItems) { item ->
                PrivacySettingCard(item, isExpanded = expandedItem == item.setting, onExpand = { expandedItem = if (expandedItem == item.setting) null else item.setting }, onSelect = { persist(item.setting, it) })
            }
        }
    }
}

private fun visibilityOf(stored: String, fallback: PrivacyLevel): PrivacyLevel =
    PrivacyLevel.entries.firstOrNull { it.name == stored } ?: fallback

@Composable
private fun PrivacySettingCard(item: StatusPrivacyItem, isExpanded: Boolean, onExpand: () -> Unit, onSelect: (PrivacyLevel) -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(SovereignColors.Cyan.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(item.arabicLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White); Text(item.currentLevel.label, fontSize = 12.sp, color = Color.Gray) }
                Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Color.Gray)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyLevel.entries.forEach { level ->
                        val isSelected = level == item.currentLevel
                        Surface(onClick = { onSelect(level) }, shape = RoundedCornerShape(10.dp), color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else Color.Transparent, border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(level.icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(level.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, color = if (isSelected) SovereignColors.Cyan else Color.White); Text(level.description, fontSize = 11.sp, color = Color.Gray) }
                                if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Cyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
