package com.red.sovereign.features.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 📞 YOUNES Sovereign Call System — Ultimate Integration
 */

enum class CallType(val label: String, val icon: ImageVector, val color: Color, val description: String) {
    VOIP_AUDIO("صوتي يونس", Icons.Rounded.Call, SovereignColors.VoipBlue, "تشفير طرفي كامل"),
    VOIP_VIDEO("فيديو يونس", Icons.Rounded.Videocam, Color(0xFF9C27B0), "دقة 1080p سيادية"),
    CONFERENCE("مؤتمر يونس", Icons.Rounded.Groups, SovereignColors.Success, "حتى 32 مشارك"),
    LIVE_BROADCAST("بث مباشر", Icons.Rounded.LiveTv, SovereignColors.LiveRed, "بث سيادي عام"),
    PSTN_DINSTAR("خطي يمني", Icons.Rounded.SimCard, SovereignColors.DinstarGold, "عبر بوابة DINSTAR")
}

data class SovereignCall(
    val id: String,
    val type: CallType,
    val remoteName: String,
    val state: String = "ACTIVE",
    val duration: Long = 0,
    val signal: Int? = null
)

@Composable
fun SovereignActiveCallScreen(
    call: SovereignCall,
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = call.type.color.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(call.type.icon, null, tint = call.type.color, modifier = Modifier.size(16.dp))
                    Text(" ${call.type.label}", color = call.type.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(140.dp).clip(CircleShape).background(call.type.color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(call.remoteName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(call.state, color = Color.Gray, fontSize = 16.sp)
            if (isMuted) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text("🔇 الميكروفون مكتوم", color = Color.Red, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                }
            }
        }

        // Controls — الآن فعالة وليست فارغة
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(56.dp).background(
                    if (isMuted) Color.Red.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
            ) {
                Icon(
                    if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    null,
                    tint = if (isMuted) Color.Red else Color.White
                )
            }
            FloatingActionButton(onClick = onEndCall, containerColor = SovereignColors.Danger, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = onToggleSpeaker,
                modifier = Modifier.size(56.dp).background(
                    if (isSpeakerOn) SovereignColors.Cyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Rounded.VolumeUp,
                    null,
                    tint = if (isSpeakerOn) SovereignColors.Cyan else Color.White
                )
            }
        }
    }
}
