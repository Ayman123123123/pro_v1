package com.red.features.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CallEntry(
    val name: String,
    val type: String, // VOIP_AUDIO, VOIP_VIDEO, CONFERENCE, PSTN
    val direction: String, // INCOMING, OUTGOING, MISSED
    val time: String
)

@Composable
fun CallLogScreen() {
    val logs = listOf(
        CallEntry("John Doe", "VOIP_VIDEO", "INCOMING", "10:30 AM"),
        CallEntry("+967777777777", "PSTN", "OUTGOING", "Yesterday"),
        CallEntry("Engineering Group", "CONFERENCE", "MISSED", "Monday")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(logs) { log ->
            CallLogItem(log)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 72.dp), thickness = 0.5.dp)
        }
    }
}

@Composable
fun CallLogItem(log: CallEntry) {
    val iconColor = when (log.type) {
        "VOIP_AUDIO" -> Color(0xFF2196F3) // Blue
        "VOIP_VIDEO" -> Color(0xFF9C27B0) // Purple
        "CONFERENCE" -> Color(0xFF4CAF50) // Green
        "PSTN" -> Color(0xFFF57C00) // Orange
        else -> Color.Gray
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (log.type.contains("VIDEO")) Icons.Default.Videocam else Icons.Default.Call,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.name, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (log.direction == "INCOMING") Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (log.direction == "MISSED") Color.Red else Color.Gray
                )
                Text(log.time, fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}
