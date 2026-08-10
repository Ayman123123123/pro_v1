package com.red.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

data class LiveStreamItem(
    val hostName: String,
    val title: String,
    val viewers: Int,
    val category: String
)

data class SpaceRoomItem(
    val roomTitle: String,
    val host: String,
    val speakersCount: Int,
    val listenersCount: Int
)

@Composable
fun RedExploreScreen(
    onStartLive: () -> Unit,
    onStartSpace: () -> Unit
) {
    val liveStreams = listOf(
        LiveStreamItem("قناة اليمن التقنية", "شرح ربط وتأمين جهاز DINSTAR UC2000 محلياً", 1240, "تكنولوجيا"),
        LiveStreamItem("شبكة أخبار صنعاء", "التغطية المباشرة للأحداث السياسية والاقتصادية", 3400, "أخبار")
    )

    val spaces = listOf(
        SpaceRoomItem("مجلس مهندسي الاتصالات اليمنيين", "أمجد باردوني", 4, 156),
        SpaceRoomItem("نقاشات السيادة التقنية والسيرفرات المحلية", "سعيد اليمني", 6, 230)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الاستكشاف والبث",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartLive,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("بث", fontSize = 12.sp)
                }

                Button(
                    onClick = onStartSpace,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Space", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Streams Section
            item {
                Text(
                    text = "📡 البثوث المباشرة الجارية",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(liveStreams) { live ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.Red, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(live.hostName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Red.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "${live.viewers} مشاهد",
                                    fontSize = 11.sp,
                                    color = Color.Red,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(live.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Spaces Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🎙️ الغرف الصوتية (Spaces)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(spaces) { space ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(space.roomTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("المضيف: ${space.host}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👥 متحدثون: ${space.speakersCount} • مستمعون: ${space.listenersCount}", fontSize = 12.sp, color = Color.Gray)
                            Button(
                                onClick = { /* Join Space */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text("انضمام", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
