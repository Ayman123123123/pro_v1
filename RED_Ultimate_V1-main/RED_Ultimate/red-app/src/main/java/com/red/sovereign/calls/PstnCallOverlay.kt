package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * طبقة مكالمة PSTN العائمة - حقيقية Material3 Expressive 2026
 * تظهر فوق كل الشاشات أثناء مكالمة PSTN نشطة
 */
@Composable
fun YounesPstnCallOverlay() {
    val state = PstnCallRuntime.state
    if (state is PstnCallState.Idle || state is PstnCallState.Ended) return

    val number = when (state) {
        is PstnCallState.Preparing -> state.number
        is PstnCallState.Ringing -> state.number
        is PstnCallState.Active -> state.number
        is PstnCallState.Failed -> state.number
        else -> ""
    }

    val statusText = when (state) {
        is PstnCallState.Preparing -> "جاري الاتصال..."
        is PstnCallState.Ringing -> "يرن..."
        is PstnCallState.Active -> "متصل - ${formatCallDuration(System.currentTimeMillis() - state.startedAt)}"
        is PstnCallState.Failed -> "فشل: ${state.message}"
        else -> ""
    }

    val operatorInfo = remember(number) { YemeniOperatorDetector.getOperatorInfo(number) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneInTalk, null, tint = YounesEmerald, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(statusText, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                operatorInfo?.let { Text(it.name, color = it.brandColor, fontSize = 10.sp) }
            }
            IconButton(onClick = { PstnCallRuntime.muted = !PstnCallRuntime.muted }) {
                Icon(if (PstnCallRuntime.muted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = if (PstnCallRuntime.muted) Color(0xFFE53935) else Color.White)
            }
            IconButton(onClick = { /* Expand to full screen */ }) {
                Icon(Icons.Default.OpenInFull, null, tint = Color.White)
            }
            FilledIconButton(onClick = { PstnCallRuntime.state = PstnCallState.Idle }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White)
            }
        }
    }
}

private fun formatCallDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
