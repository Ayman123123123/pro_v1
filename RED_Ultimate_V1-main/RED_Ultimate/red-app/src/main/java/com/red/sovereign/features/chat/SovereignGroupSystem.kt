package com.red.sovereign.features.chat

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
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 👥 YOUNES Sovereign Group System
 * نظام المجموعات المتقدم السيادي
 */

enum class GroupRole(val label: String, val icon: ImageVector, val color: Color) {
    OWNER("المالك السيادي", Icons.Rounded.VpnKey, SovereignColors.Gold),
    ADMIN("مشرف", Icons.Rounded.Shield, SovereignColors.Cyan),
    MEMBER("عضو", Icons.Rounded.Person, Color.Gray)
}

enum class GroupPrivacy(val label: String, val icon: ImageVector, val desc: String) {
    PUBLIC("عامة", Icons.Rounded.Public, "متاحة للجميع محلياً"),
    PRIVATE("خاصة", Icons.Rounded.Lock, "بالدعوة فقط — تشفير كامل"),
    SECRET("سرية", Icons.Rounded.VisibilityOff, "مخفية — دعوة المالك فقط")
}

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit = {},
    friends: List<PublicRedProfile> = emptyList(),
    onCreate: (name: String, privacy: String, memberRedIds: List<String>) -> Unit = { _, _, _ -> }
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(GroupPrivacy.PRIVATE) }
    val selectedMembers = remember { androidx.compose.runtime.mutableStateMapOf<String, PublicRedProfile>() }
    var memberSearch by remember { mutableStateOf("") }

    val filteredFriends = remember(memberSearch, friends) {
        val q = memberSearch.trim()
        if (q.isEmpty()) friends
        else friends.filter { it.displayName.contains(q, ignoreCase = true) || it.username.contains(q, ignoreCase = true) || it.redId.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Column {
                Text("إنشاء مجموعة سيادية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("اختر الأصدقاء الذين تريد إضافتهم", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("اسم المجموعة") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("الوصف (اختياري)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(16.dp))
        Text("خصوصية المجموعة", color = SovereignColors.Cyan, fontWeight = FontWeight.Bold)
        GroupPrivacy.entries.forEach { level ->
            val isSelected = level == privacy
            Surface(
                onClick = { privacy = level },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.1f) else SovereignColors.SurfaceNavy,
                border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(level.icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(level.label, color = if (isSelected) SovereignColors.Cyan else Color.White, fontWeight = FontWeight.Bold)
                        Text(level.desc, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("إضافة أعضاء (${selectedMembers.size} مختار)", color = SovereignColors.Cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (selectedMembers.isNotEmpty()) {
                TextButton({ selectedMembers.clear() }) { Text("إلغاء التحديد", color = Color.Gray, fontSize = 12.sp) }
            }
        }
        OutlinedTextField(
            value = memberSearch, onValueChange = { memberSearch = it },
            label = { Text("ابحث عن صديق…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(8.dp))
        if (friends.isEmpty()) {
            Text("لا يوجد أصدقاء بعد — أضف أصدقاء أولاً لتتمكن من إضافتهم للمجموعة", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filteredFriends, key = { it.redId }) { friend ->
                    val checked = selectedMembers.containsKey(friend.redId)
                    Surface(
                        onClick = { if (checked) selectedMembers.remove(friend.redId) else selectedMembers[friend.redId] = friend },
                        shape = RoundedCornerShape(10.dp),
                        color = if (checked) SovereignColors.Cyan.copy(alpha = 0.12f) else SovereignColors.SurfaceNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(36.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
                                Box(contentAlignment = Alignment.Center) { Text(friend.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(friend.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("@${friend.username}", color = SovereignColors.Cyan, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { if (it) selectedMembers[friend.redId] = friend else selectedMembers.remove(friend.redId) },
                                colors = CheckboxDefaults.colors(checkedColor = SovereignColors.Cyan)
                            )
                        }
                    }
                }
                if (filteredFriends.isEmpty()) {
                    item { Text("لا توجد نتائج مطابقة", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onCreate(name.trim(), privacy.name, selectedMembers.keys.toList()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
            shape = RoundedCornerShape(14.dp),
            enabled = name.isNotBlank()
        ) {
            Text("تأسيس المجموعة (${selectedMembers.size} عضو)", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun SovereignGroupInfoScreen(
    groupName: String,
    memberCount: Int = 0,
    onBack: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian)) {
        Box(Modifier.fillMaxWidth().height(200.dp).background(SovereignColors.Navy), contentAlignment = Alignment.BottomStart) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(64.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, tint = SovereignColors.Cyan, modifier = Modifier.size(32.dp)) }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(groupName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("مجموعة مشفرة • ${memberCount.coerceAtLeast(1)} أعضاء", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        
        val tabs = listOf("الأعضاء", "الوسائط", "الإعدادات")
        var selectedTab by remember { mutableIntStateOf(0) }
        TabRow(selectedTabIndex = selectedTab, containerColor = SovereignColors.SurfaceNavy) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 13.sp) })
            }
        }
        
        // قائمة الأعضاء الحقيقية تُدار من GroupViewModel عبر إدارة المجموعة.
        if (selectedTab == 0) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Groups, null, tint = SovereignColors.Cyan, modifier = Modifier.size(48.dp))
                Text("${memberCount.coerceAtLeast(1)} عضو", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("ادارة الأعضاء والأدوار تتم من شاشة إدارة المجموعة", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
