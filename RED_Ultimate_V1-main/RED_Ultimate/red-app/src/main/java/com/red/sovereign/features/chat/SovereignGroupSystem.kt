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
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupMember
import com.red.sovereign.groups.GroupViewModel
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

private fun roleOf(member: GroupMember): GroupRole {
    return when (member.role.uppercase()) {
        "OWNER" -> GroupRole.OWNER
        "ADMIN" -> GroupRole.ADMIN
        else -> GroupRole.MEMBER
    }
}

@Composable
fun SovereignGroupInfoScreen(
    group: Group?,
    groups: GroupViewModel,
    friends: List<PublicRedProfile> = emptyList(),
    onBack: () -> Unit = {}
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showAddMembers by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tabs = listOf("الأعضاء", "الإعدادات")
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian)) {
        // رأس المجموعة
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(SovereignColors.Navy, SovereignColors.Obsidian))),
            contentAlignment = Alignment.BottomStart
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // أيقونة/صورة المجموعة
                group?.let { g ->
                    androidx.compose.runtime.LaunchedEffect(g.avatarUrl) { groups.loadAvatar(g) }
                    val avatarImg = groups.avatars[g.id]
                    if (avatarImg != null) {
                        androidx.compose.foundation.Image(avatarImg, g.name, Modifier.size(68.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        Surface(Modifier.size(68.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
                            Box(contentAlignment = Alignment.Center) { Text(g.name.take(1), color = SovereignColors.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                } ?: Surface(Modifier.size(68.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, tint = SovereignColors.Cyan, modifier = Modifier.size(36.dp)) }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(group?.name ?: "المجموعة", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(group?.description?.takeIf(String::isNotBlank) ?: "مجموعة مشفرة", fontSize = 13.sp, color = Color.Gray, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = SovereignColors.Success, modifier = Modifier.size(14.dp))
                        Text(" ${group?.members?.size ?: 0} أعضاء • تشفير Sender Keys", fontSize = 12.sp, color = SovereignColors.Cyan)
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = SovereignColors.SurfaceNavy) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 13.sp) })
            }
        }

        when (selectedTab) {
            0 -> {
                // زر إضافة أعضاء
                if (group != null && friends.isNotEmpty()) {
                    Button(
                        onClick = { showAddMembers = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                        shape = RoundedCornerShape(12.dp)
                    ) { Icon(Icons.Rounded.PersonAdd, null, tint = Color.Black); Text(" إضافة أعضاء من أصدقائك", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(group?.members ?: emptyList(), key = { it.redId }) { member ->
                        val role = roleOf(member)
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.SurfaceNavy) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(42.dp), shape = CircleShape, color = SovereignColors.Navy) {
                                    Box(contentAlignment = Alignment.Center) { Text(member.username.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(member.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(member.redId, color = SovereignColors.Cyan, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = role.color.copy(alpha = 0.18f)) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(role.icon, null, tint = role.color, modifier = Modifier.size(14.dp))
                                        Text(" ${role.label}", fontSize = 11.sp, color = role.color, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // الإعدادات
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group != null) {
                        item {
                            InfoRow(Icons.Rounded.Schedule, "رسائل مؤقتة", "أرسل رسائل تختفي بعد وقت — من شاشة المحادثة") { }
                        }
                        item {
                            InfoRow(Icons.Rounded.VolumeOff, "كتم الإشعارات", "إيقاف تنبيهات هذه المجموعة") { }
                        }
                        item {
                            InfoRow(Icons.Rounded.Shield, "رمز أمان المجموعة", "تأكد من تطابق رمز الأمان مع الأعضاء") { }
                        }
                        item {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.Danger.copy(alpha = 0.12f)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.ExitToApp, null, tint = SovereignColors.Danger)
                                    Text(" مغادرة المجموعة", color = SovereignColors.Danger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { confirmLeave = true })
                                }
                            }
                        }
                        item {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.Danger.copy(alpha = 0.12f)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DeleteForever, null, tint = SovereignColors.Danger)
                                    Text(" حذف المجموعة نهائياً", color = SovereignColors.Danger, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { confirmDelete = true })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // نافذة إضافة أعضاء
    if (showAddMembers && group != null) {
        val selectedAdd = remember { androidx.compose.runtime.mutableStateMapOf<String, PublicRedProfile>() }
        AlertDialog(
            onDismissRequest = { showAddMembers = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("إضافة أعضاء", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val existing = group.members.map { it.redId }.toSet()
                    val addable = friends.filter { it.redId !in existing }
                    if (addable.isEmpty()) item { Text("كل أصدقائك بالفعل في المجموعة", color = Color.Gray) }
                    items(addable, key = { it.redId }) { friend ->
                        val checked = selectedAdd.containsKey(friend.redId)
                        Surface(onClick = { if (checked) selectedAdd.remove(friend.redId) else selectedAdd[friend.redId] = friend }, shape = RoundedCornerShape(10.dp), color = if (checked) SovereignColors.Cyan.copy(alpha = 0.12f) else SovereignColors.Navy) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(friend.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                Checkbox(checked = checked, onCheckedChange = { if (it) selectedAdd[friend.redId] = friend else selectedAdd.remove(friend.redId) }, colors = CheckboxDefaults.colors(checkedColor = SovereignColors.Cyan))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    selectedAdd.values.forEach { friend -> groups.addMember(group, friend.redId) {} }
                    showAddMembers = false
                }) { Text("إضافة (${selectedAdd.size})", color = SovereignColors.Cyan) }
            },
            dismissButton = { TextButton({ showAddMembers = false }) { Text("إلغاء", color = Color.Gray) } }
        )
    }

    // تأكيد المغادرة
    if (confirmLeave && group != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("مغادرة المجموعة؟", color = Color.White) },
            text = { Text("لن تستقبل رسائل المجموعة بعد الآن.", color = Color.Gray) },
            confirmButton = { TextButton({ groups.leave(group) { onBack() }; confirmLeave = false }) { Text("مغادرة", color = SovereignColors.Danger) } },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("إلغاء", color = Color.Gray) } }
        )
    }

    // تأكيد الحذف
    if (confirmDelete && group != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("حذف المجموعة نهائياً؟", color = Color.White) },
            text = { Text("سيُحذف للمجموعة لدى الجميع. لا يمكن التراجع.", color = Color.Gray) },
            confirmButton = { TextButton({ groups.deleteGroup(group) { onBack() }; confirmDelete = false }) { Text("حذف", color = SovereignColors.Danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("إلغاء", color = Color.Gray) } }
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.SurfaceNavy) {
        Row(Modifier.padding(12.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
