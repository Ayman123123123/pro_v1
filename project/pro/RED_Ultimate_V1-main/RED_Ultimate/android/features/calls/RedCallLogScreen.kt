package com.red.features.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RedCallEntry(
    val name: String,
    val type: String, // VOIP_AUDIO, VOIP_VIDEO, DINSTAR_PSTN
    val direction: String, // INCOMING, OUTGOING, MISSED
    val time: String,
    val details: String = ""
)

@Composable
fun RedCallLogScreen() {
    val logs = listOf(
        RedCallEntry("سعيد اليمني", "DINSTAR_PSTN", "OUTGOING", "منذ 5 دقائق", "+967 77 123 4567"),
        RedCallEntry("محمد أحمد (زنجي VoIP)", "VOIP_AUDIO", "INCOMING", "منذ ساعة", "مكالمة عبر الإنترنت"),
        RedCallEntry("مؤتمر العمل الجماعي", "CONFERENCE", "MISSED", "أمس", "غرفة صوتية"),
        RedCallEntry("أمجد باردوني", "DINSTAR_PSTN", "INCOMING", "منذ يومين", "+967 73 987 6543")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "سجل المكالمات الموحد",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                RedCallLogItem(log)
            }
        }
    }
}

@Composable
fun RedCallLogItem(log: RedCallEntry) {
    val isDinstar = log.type == "DINSTAR_PSTN"
    val badgeColor = if (isDinstar) Color(0xFFF4B400) else Color(0xFF1E88E5) // Gold for Dinstar, Blue for VoIP
    val icon = when {
        log.type.contains("VIDEO") -> Icons.Default.Videocam
        isDinstar -> Icons.Default.SimCard
        else -> Icons.Default.Call
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(badgeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isDinstar) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF4B400).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "خطي اليمني",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF4B400),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (log.direction == "INCOMING") Icons.Default.CallReceived else Icons.Default.CallMade,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (log.direction == "MISSED") Color.Red else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${log.details} • ${log.time}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Quick Call Back Button
            IconButton(
                onClick = { /* Quick callback */ },
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isDinstar) Icons.Default.PhoneForwarded else Icons.Default.Call,
                    contentDescription = "إعادة الاتصال",
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
