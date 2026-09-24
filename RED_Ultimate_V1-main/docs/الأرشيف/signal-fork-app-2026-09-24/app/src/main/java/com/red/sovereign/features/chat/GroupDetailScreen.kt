package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  GROUP MEMBER DATA
// ════════════════════════════════════════════════════════════

data class GroupMember(
    val id: String,
    val name: String,
    val role: String = "MEMBER",   // OWNER | ADMIN | MEMBER
    val isOnline: Boolean = false,
    val avatarColor: Color = SurfaceElevated,
    val phone: String = ""
)

// ════════════════════════════════════════════════════════════
//  GROUP DETAIL SCREEN — تفاصيل المجموعة
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit
) {
    var showAddMember by remember { mutableStateOf(false) }
    var selectedTab   by remember { mutableStateOf(0) }
    val tabs = listOf("الأعضاء", "الوسائط", "الروابط")

    val members = remember {
        listOf(
            GroupMember("1", "أنت",      "OWNER", true,  RedPrimary,   "+967 7XX XXX"),
            GroupMember("2", "أيمن",     "ADMIN", true,  BlueAccent,   "+967 7XX XXX"),
            GroupMember("3", "علي",      "MEMBER",false, GreenAccent,  "+967 7XX XXX"),
            GroupMember("4", "سارة",     "ADMIN", true,  Color(0xFFE91E63), "+967 7XX XXX"),
            GroupMember("5", "خالد",     "MEMBER",false, AqyalCyan,    "+967 7XX XXX"),
            GroupMember("6", "نورة",     "MEMBER",true,  GoldenAccent, "+967 7XX XXX"),
            GroupMember("7", "محمد",     "MEMBER",false, PurpleAccent, "+967 7XX XXX"),
            GroupMember("8", "فاطمة",    "MEMBER",true,  OrangeAccent, "+967 7XX XXX"),
        )
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("معلومات المجموعة", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "رجوع", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Edit */ }) {
                        Icon(Icons.Rounded.Edit, "تعديل", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Hero Card ──────────────────────────────────────────
            item {
                GroupHeroCard(members = members)
            }

            // ── Quick Actions ──────────────────────────────────────
            item {
                GroupQuickActions()
            }

            // ── Description ───────────────────────────────────────
            item {
                GroupInfoCard()
            }

            // ── Tab Row ───────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor   = SurfaceDark,
                        contentColor     = RedPrimary,
                        indicator        = { tabPositions ->
                            if (selectedTab < tabPositions.size)
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color    = RedPrimary,
                                    height   = 2.dp
                                )
                        },
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick  = { selectedTab = index },
                                text     = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // ── Members tab ───────────────────────────────────────
            if (selectedTab == 0) {
                item {
                    // Add Member
                    MemberActionRow(
                        icon    = Icons.Rounded.PersonAdd,
                        label   = "إضافة أعضاء",
                        tint    = GreenAccent,
                        onClick = { showAddMember = true }
                    )
                }
                item {
                    MemberActionRow(
                        icon    = Icons.Rounded.Link,
                        label   = "رابط دعوة المجموعة",
                        tint    = BlueAccent,
                        onClick = { /* Copy link */ }
                    )
                }

                items(members) { member ->
                    GroupMemberRow(member = member, isOwner = member.role == "OWNER")
                }
            }

            // ── Media tab ─────────────────────────────────────────
            if (selectedTab == 1) {
                item {
                    GroupMediaGrid()
                }
            }

            // ── Danger Zone ───────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    DangerButton(label = "مغادرة المجموعة",   icon = Icons.Rounded.ExitToApp)
                    DangerButton(label = "حذف المجموعة",      icon = Icons.Rounded.DeleteForever)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  HERO CARD
// ════════════════════════════════════════════════════════════

@Composable
private fun GroupHeroCard(members: List<GroupMember>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SurfaceDark, BgPrimary)))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Group Avatar with gradient border
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(GradientRedPrimary))
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(98.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(RedDeep, SurfaceDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("RD", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("مطوري RED", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Group, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${members.size} عضو", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(TextTertiary))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.Lock, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("مجموعة مغلقة", color = TextSecondary, fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Online member avatars stack
            Row(horizontalArrangement = Arrangement.Center) {
                members.filter { it.isOnline }.take(5).forEachIndexed { i, m ->
                    Box(
                        modifier = Modifier
                            .offset(x = (-(i * 12)).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, BgPrimary, CircleShape)
                            .background(m.avatarColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(m.name.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text("${members.count { it.isOnline }} متصل الآن", color = OnlineDot, fontSize = 13.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  QUICK ACTIONS
// ════════════════════════════════════════════════════════════

@Composable
private fun GroupQuickActions() {
    val actions = listOf(
        Triple(Icons.Rounded.Call,              "مكالمة صوتية", GreenAccent),
        Triple(Icons.Rounded.Videocam,          "مكالمة فيديو", BlueAccent),
        Triple(Icons.Rounded.NotificationsOff,  "كتم",          OrangeAccent),
        Triple(Icons.Rounded.Search,            "بحث",          AqyalCyan),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { (icon, label, tint) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  INFO CARD
// ════════════════════════════════════════════════════════════

@Composable
private fun GroupInfoCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Column {
            Text("الوصف", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "مجموعة مطوري RED Ultimate — نتشارك الأفكار والتحديثات والمستجدات حول التطبيق. 🔴",
                color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarToday, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("تم الإنشاء في 1 يناير 2026", color = TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  MEMBER ROW
// ════════════════════════════════════════════════════════════

@Composable
private fun MemberActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 74.dp))
}

@Composable
private fun GroupMemberRow(member: GroupMember, isOwner: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(member.avatarColor, member.avatarColor.copy(0.6f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(member.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (member.isOnline) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(OnlineDot).border(2.dp, BgPrimary, CircleShape))
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(member.phone, color = TextTertiary, fontSize = 13.sp)
        }

        // Role badge
        when (member.role) {
            "OWNER" -> RoleBadge("مالك",  RedPrimary)
            "ADMIN" -> RoleBadge("مشرف", BlueAccent)
        }
    }
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 74.dp))
}

@Composable
private fun RoleBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ════════════════════════════════════════════════════════════
//  MEDIA GRID
// ════════════════════════════════════════════════════════════

@Composable
private fun GroupMediaGrid() {
    val colors = listOf(BlueAccent, PurpleAccent, GreenAccent, OrangeAccent, AqyalCyan, RedPrimary, GoldenAccent, BlueAccent, PurpleAccent)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        val rows = colors.chunked(3)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(color.copy(0.6f), color)))
                    ) {
                        Icon(
                            Icons.Rounded.Image, null,
                            tint = Color.White.copy(0.4f),
                            modifier = Modifier.size(32.dp).align(Alignment.Center)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  DANGER BUTTON
// ════════════════════════════════════════════════════════════

@Composable
private fun DangerButton(label: String, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RedPrimary.copy(alpha = 0.08f))
            .clickable { }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = RedPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  HELPER: tabIndicatorOffset
// ════════════════════════════════════════════════════════════

private fun Modifier.tabIndicatorOffset(tabPosition: TabPosition): Modifier =
    this.fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = tabPosition.left)
        .width(tabPosition.width)
