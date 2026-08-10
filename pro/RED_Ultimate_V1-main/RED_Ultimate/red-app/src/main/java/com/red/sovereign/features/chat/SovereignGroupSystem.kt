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
fun CreateGroupScreen(onBack: () -> Unit = {}) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(GroupPrivacy.PRIVATE) }
    
    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("إنشاء مجموعة سيادية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("اسم المجموعة") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
            shape = RoundedCornerShape(14.dp),
            enabled = name.isNotBlank()
        ) {
            Text("تأسيس المجموعة", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun SovereignGroupInfoScreen(
    groupName: String,
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
                    Text("مجموعة مشفرة • 8 أعضاء", fontSize = 12.sp, color = Color.Gray)
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
        
        // Members list simplified for now
        if (selectedTab == 0) {
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(8) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(40.dp), shape = CircleShape, color = SovereignColors.SurfaceNavy) {
                            Box(contentAlignment = Alignment.Center) { Text("ع", color = Color.White) }
                        }
                        Text(" عضو يونس $i", modifier = Modifier.padding(start = 12.dp), color = Color.White)
                    }
                }
            }
        }
    }
}
