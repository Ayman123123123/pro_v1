package com.red.sovereign.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.SovereignColors

data class LiveStreamItem(val hostName: String, val title: String, val viewers: Int, val category: String)
data class SpaceRoomItem(val roomTitle: String, val host: String, val speakersCount: Int, val listenersCount: Int)

@Composable
fun RedExploreScreen(onStartLive: () -> Unit, onStartSpace: () -> Unit) {
    val liveStreams = listOf(
        LiveStreamItem("قناة اليمن التقنية", "شرح ربط وتأمين أجهزة يونس محلياً", 124, "تكنولوجيا"),
        LiveStreamItem("نبض صنعاء", "متابعة حية للأخبار المحلية السيادية", 89, "أخبار")
    )
    val spaces = listOf(
        SpaceRoomItem("مجلس يونس السيادي", "المشرف العام", 3, 42),
        SpaceRoomItem("نقاشات حول التشفير الكمومي", "د. يونس", 5, 67)
    )

    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("الاستكشاف والسيادة", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartLive, colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveRed), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.LiveTv, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("بث", fontSize = 12.sp)
                }
                Button(onClick = onStartSpace, colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.SpacePurple), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Mic, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Space", fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("📡 البث المباشر المحلي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Cyan) }
            items(liveStreams) { live ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(SovereignColors.LiveRed, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(live.hostName, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = SovereignColors.LiveRed.copy(alpha = 0.1f)) {
                                Text("${live.viewers} مشاهد", fontSize = 11.sp, color = SovereignColors.LiveRed, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Text(live.title, fontSize = 15.sp, color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item { Text("🎙️ الغرف الصوتية المفتوحة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Gold) }
            items(spaces) { space ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(space.roomTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("بواسطة: ${space.host}", fontSize = 12.sp, color = Color.Gray)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("👥 $speakersCount متحدث • $listenersCount مستمع", fontSize = 12.sp, color = Color.LightGray)
                            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.SpacePurple), shape = RoundedCornerShape(12.dp)) { Text("دخول", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}
