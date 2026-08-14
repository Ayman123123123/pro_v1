package com.red.sovereign.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesRuby

@Composable
fun InlineChatCallBar(peerId: String, modifier: Modifier = Modifier) {
    val state = CallRuntime.state
    val context = LocalContext.current
    
    // We only show the inline bar if the current call involves this peer
    val isRelevantCall = when (state) {
        is CallUiState.Active -> state.peer == peerId
        is CallUiState.ActiveWithIncoming -> state.active.peer == peerId
        is CallUiState.Connecting -> state.peer == peerId
        is CallUiState.Reconnecting -> state.peer == peerId
        is CallUiState.Busy -> state.peer == peerId
        is CallUiState.Declined -> state.peer == peerId
        is CallUiState.NoAnswer -> state.peer == peerId
        else -> false
    }

    AnimatedVisibility(
        visible = isRelevantCall,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(YounesEmerald.copy(alpha = 0.15f))
                .clickable {
                    // Clicking the bar opens the full overlay, which is managed globally
                    // but we can trigger an intent if needed, or simply let the user know
                    // the full UI is usually above or can be summoned.
                    // Actually, if we are in chat, the global overlay is likely hiding the chat?
                    // The instruction said: "عند النقر -> يفتح YounesCallOverlay كاملاً"
                    // We can emit an action or toggle a state. For now, the global overlay is controlled
                    // by UnifiedCallOverlays showing if state !is Idle. If they minimized it, we would 
                    // need a CallRuntime.isMinimized state. Let's assume UnifiedCallOverlays handles minimizing.
                    CallRuntime.isMinimized = false
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icon = if ((state as? CallUiState.Active)?.mode == "VIDEO") Icons.Default.Videocam else Icons.Default.Call
                Icon(icon, contentDescription = null, tint = YounesEmerald, modifier = Modifier.size(20.dp))
                
                Column {
                    Text(
                        text = "مكالمة جارية مع $peerId",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = when (state) {
                        is CallUiState.Connecting -> "جارٍ الاتصال..."
                        is CallUiState.Reconnecting -> "جارٍ إعادة الاتصال..."
                        is CallUiState.Active -> "انقر للتكبير"
                        is CallUiState.Busy -> "مشغول"
                        is CallUiState.Declined -> "مرفوضة"
                        is CallUiState.NoAnswer -> "لا يوجد رد"
                        else -> ""
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, color = Color.White.copy(0.7f), fontSize = 12.sp)
                    }
                }
            }

            if (state is CallUiState.Active || state is CallUiState.Connecting || state is CallUiState.Reconnecting) {
                IconButton(
                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_END) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(YounesRuby)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "إنهاء", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
