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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.red.core.theme.SovereignColors

/**
 * 👥 YOUNES Sovereign Group System
 * نظام المجموعات المتقدم — إنشاء + إدارة + صلاحيات + خصوصية
 */

// ━━━━━━━━━━━━ النماذج ━━━━━━━━━━━━

enum class GroupRole(val label: String, val icon: ImageVector, val color: Color) {
    OWNER("المالك", Icons.Rounded.VpnKey, SovereignColors.Gold),
    ADMIN("المشرف", Icons.Rounded.Shield, SovereignColors.Cyan),
    MODERATOR("المراقب", Icons.Rounded.Badge, SovereignColors.SpacePurple),
    MEMBER("عضو", Icons.Rounded.Person, Color.Gray)
}

enum class GroupPrivacy(val label: String, val icon: ImageVector, val desc: String) {
    PUBLIC("عامة", Icons.Rounded.Public, "أي شخص يمكنه الانضمام والرؤية"),
    PRIVATE("خاصة", Icons.Rounded.Lock, "الانضمام بالدعوة فقط — المحتوى مرئي للأعضاء"),
    SECRET("سرية", Icons.Rounded.VisibilityOff, "لن تظهر في البحث — الدعوة فقط من المالك")
}

enum class GroupFeature(val label: String) {
    MESSAGES("الرسائل"),
    MEDIA("الوسائط"),
    VOICE_NOTES("الرسائل الصوتية"),
    POLLS("الاستطلاعات"),
    CALLS("المكالمات الجماعية"),
    LIVE("البث المباشر"),
    LINKS("الروابط"),
    FILES("الملفات")
}

data class SovereignGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val privacy: GroupPrivacy = GroupPrivacy.PRIVATE,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val myRole: GroupRole = GroupRole.MEMBER,
    val enabledFeatures: Set<GroupFeature> = GroupFeature.entries.toSet(),
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: GroupRole,
    val isOnline: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis(),
    val customTitle: String? = null
)

// ━━━━━━━━━━━━ شاشة إنشاء مجموعة ━━━━━━━━━━━━

@Composable
fun CreateGroupScreen(
    onCreate: (SovereignGroup) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(GroupPrivacy.PRIVATE) }
    var enabledFeatures by remember { mutableStateOf(GroupFeature.entries.toSet()) }
    var step by remember { mutableStateOf(0) } // 0=info, 1=privacy, 2=features

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
            .padding(16.dp)
    ) {
        // الرأس
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (step > 0) step-- else onBack() }) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when (step) {
                    0 -> "إنشاء مجموعة — المعلومات"
                    1 -> "إنشاء مجموعة — الخصوصية"
                    else -> "إنشاء مجموعة — المميزات"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(24.dp))

        // مؤشر الخطوات
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) { i ->
                val isActive = i <= step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isActive) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f))
                )
                if (i < 2) Spacer(Modifier.width(4.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> GroupInfoStep(
                name = name, onNameChange = { name = it },
                description = description, onDescChange = { description = it }
            )
            1 -> GroupPrivacyStep(
                privacy = privacy, onPrivacyChange = { privacy = it }
            )
            2 -> GroupFeaturesStep(
                features = enabledFeatures, onFeaturesChange = { enabledFeatures = it }
            )
        }

        Spacer(Modifier.weight(1f))

        // زر التالي/الإنشاء
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    if (step < 2) step++
                    else {
                        onCreate(SovereignGroup(
                            id = "grp_${System.currentTimeMillis()}",
                            name = name,
                            description = description,
                            privacy = privacy,
                            enabledFeatures = enabledFeatures,
                            myRole = GroupRole.OWNER
                        ))
                    }
                },
                enabled = name.isNotBlank() || step > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (step < 2) "التالي" else "إنشاء المجموعة", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(if (step < 2) Icons.Rounded.ArrowForward else Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun GroupInfoStep(
    name: String, onNameChange: (String) -> Unit,
    description: String, onDescChange: (String) -> Unit
) {
    // أيقونة المجموعة
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = SovereignColors.SurfaceNavy
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GroupAdd, null, tint = SovereignColors.Cyan, modifier = Modifier.size(40.dp))
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = name, onValueChange = onNameChange,
        label = { Text("اسم المجموعة") },
        placeholder = { Text("مثال: فريق التطوير السيادي") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = description, onValueChange = onDescChange,
        label = { Text("الوصف (اختياري)") },
        placeholder = { Text("ما هي هذه المجموعة؟") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2, maxLines = 4,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun GroupPrivacyStep(
    privacy: GroupPrivacy, onPrivacyChange: (GroupPrivacy) -> Unit
) {
    Text("من يستطيع رؤية هذه المجموعة؟", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(16.dp))

    GroupPrivacy.entries.forEach { level ->
        val isSelected = level == privacy
        Surface(
            onClick = { onPrivacyChange(level) },
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.12f) else SovereignColors.SurfaceNavy,
            border = BorderStroke(1.5.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(
                        if (isSelected) SovereignColors.Cyan.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(level.icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(level.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (isSelected) SovereignColors.Cyan else Color.White)
                    Text(level.desc, fontSize = 12.sp, color = Color.Gray)
                }
                if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Cyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun GroupFeaturesStep(
    features: Set<GroupFeature>, onFeaturesChange: (Set<GroupFeature>) -> Unit
) {
    Text("المميزات المفعلة في المجموعة", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Text("يمكنك تقييد المميزات لاحقاً من إعدادات المجموعة", fontSize = 12.sp, color = Color.Gray)
    Spacer(Modifier.height(16.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(GroupFeature.entries.toList()) { feature ->
            val isEnabled = feature in features
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SovereignColors.SurfaceNavy
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(feature.label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            onFeaturesChange(if (checked) features + feature else features - feature)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = SovereignColors.Cyan)
                    )
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ شاشة معلومات المجموعة المتقدمة ━━━━━━━━━━━━

@Composable
fun SovereignGroupInfoScreen(
    group: SovereignGroup,
    members: List<GroupMember> = emptyList(),
    onBack: () -> Unit = {},
    onMemberAction: (GroupMember, GroupRole) -> Unit = { _, _ -> },
    onToggleMute: () -> Unit = {},
    onLeave: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الأعضاء", "الوسائط", "الروابط", "الإعدادات")

    Column(
        modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian)
    ) {
        // الرأس مع صورة المجموعة
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(SovereignColors.Navy),
            contentAlignment = Alignment.BottomStart
        ) {
            // زر الرجوع
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = SovereignColors.SurfaceNavy
                ) {
                    group.avatarUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } ?: Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Groups, null, tint = SovereignColors.Cyan, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(group.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(group.privacy.icon, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${group.privacy.label} • ${group.memberCount} عضو • ${group.onlineCount} متصل", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // التبويبات
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SovereignColors.SurfaceNavy
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                )
            }
        }

        // المحتوى
        when (selectedTab) {
            0 -> GroupMembersList(members, group.myRole, onMemberAction)
            3 -> GroupSettingsTab(group, onToggleMute, onLeave, onDelete)
        }
    }
}

@Composable
private fun GroupMembersList(
    members: List<GroupMember>,
    myRole: GroupRole,
    onMemberAction: (GroupMember, GroupRole) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // الأعضاء حسب الدور
        val (owners, admins, mods, regular) = members.partition { it.role == GroupRole.OWNER }
            .let { (o, rest) ->
                val (a, rest2) = rest.partition { it.role == GroupRole.ADMIN }
                val (m, r) = rest2.partition { it.role == GroupRole.MODERATOR }
                Tuple4(o, a, m, r)
            }

        if (owners.isNotEmpty()) {
            item { SectionHeader("المالك") }
            items(owners) { MemberRow(it, myRole, onMemberAction) }
        }
        if (admins.isNotEmpty()) {
            item { SectionHeader("المشرفون") }
            items(admins) { MemberRow(it, myRole, onMemberAction) }
        }
        if (mods.isNotEmpty()) {
            item { SectionHeader("المراقبون") }
            items(mods) { MemberRow(it, myRole, onMemberAction) }
        }
        if (regular.isNotEmpty()) {
            item { SectionHeader("الأعضاء") }
            items(regular) { MemberRow(it, myRole, onMemberAction) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Cyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
}

@Composable
private fun MemberRow(
    member: GroupMember,
    myRole: GroupRole,
    onAction: (GroupMember, GroupRole) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // الأفاتار
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
            Box(contentAlignment = Alignment.Center) {
                if (member.isOnline) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(10.dp).background(SovereignColors.Success, CircleShape))
                }
                Text(member.name.take(1), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            member.customTitle?.let {
                Text(it, fontSize = 11.sp, color = member.role.color)
            }
        }
        // شارة الدور
        Icon(member.role.icon, null, tint = member.role.color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun GroupSettingsTab(
    group: SovereignGroup,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingRow("كتم الإشعارات", Icons.Rounded.NotificationsOff, group.isMuted) { onToggleMute() }
        }
        item {
            SettingRow("تثبيت المجموعة", Icons.Rounded.PushPin, group.isPinned) { /* toggle */ }
        }
        item {
            OutlinedButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.Warning)
            ) {
                Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("مغادرة المجموعة")
            }
        }
        if (group.myRole == GroupRole.OWNER) {
            item {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.Danger)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حذف المجموعة")
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, icon: ImageVector, isChecked: Boolean, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = SovereignColors.SurfaceNavy) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.White)
            Switch(checked = isChecked, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = SovereignColors.Cyan))
        }
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
