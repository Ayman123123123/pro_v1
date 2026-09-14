package com.red.sovereign.calls

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

/**
 * ImprovedSpacesScreen - مساحات صوتية أفضل من تويتر X 2026
 * 
 * مواصفات أفضل من X Spaces:
 * - X: 13 متحدث (مضيف + 2 مساعد + 10 متحدثين) + غير محدود مستمعين
 * - RED: 13 متحدث + غير محدود مستمعين + جودة أعلى + تفاعلات أفضل + moderation متقدم
 * - تقنيات: Opus stereo 48kHz + RNNoise AI + SFU + breakout
 * - ميزات: تسجيل اختياري + تذاكر + live captions + reactions + مشاركة DM/link
 */

@Composable
fun ImprovedSpacesScreen(
    spaceTitle: String,
    hostName: String,
    speakers: List<SpaceParticipant>,
    listeners: List<SpaceParticipant>,
    raisedHands: List<SpaceParticipant>,
    isHost: Boolean,
    isSpeaker: Boolean,
    isMuted: Boolean,
    isRecording: Boolean,
    listenerCount: Int,
    onToggleMute: () -> Unit,
    onRaiseHand: () -> Unit,
    onLowerHand: () -> Unit,
    onApproveSpeaker: (String) -> Unit,
    onRemoveSpeaker: (String) -> Unit,
    onMuteParticipant: (String) -> Unit,
    onLeaveSpace: () -> Unit,
    onSendReaction: (String) -> Unit,
    onStartRecording: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0A1628))))
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header - أفضل من X
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A4A).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HeadsetMic, null, tint = Color(0xFF9C27B0), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(spaceTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (isRecording) {
                            Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(4.dp)) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                    Spacer(Modifier.width(4.dp))
                                    Text("REC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("يستضيف $hostName", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.People, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        Text("$listenerCount مستمع • ${speakers.size} متحدث • ${raisedHands.size} يرفع يده", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Text("🎧 Opus Stereo 48kHz • RNNoise AI • SFU • تفاعلات • moderation • أفضل من X", color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp)
                }
            }
            
            LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // المتحدثون - 13 max مثل X
                item {
                    Text("المتحدثون (${speakers.size}/13) - يتحدثون الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                items(speakers.chunked(4)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { speaker ->
                            SpaceParticipantAvatar(speaker, isSpeaker = true, isHost = speaker.id == "host", onMute = if (isHost) ({ onMuteParticipant(speaker.id) }) else null, onRemove = if (isHost) ({ onRemoveSpeaker(speaker.id) }) else null)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                
                // رافعو الأيدي
                if (raisedHands.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PanTool, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("يرفعون أيديهم (${raisedHands.size})", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    items(raisedHands.chunked(3)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { hand ->
                                SpaceParticipantAvatar(hand, isSpeaker = false, showHand = true, onApprove = if (isHost) ({ onApproveSpeaker(hand.id) }) else null)
                            }
                        }
                    }
                }
                
                // المستمعون - غير محدود
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("المستمعون ($listenerCount) - غير محدود", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                items(listeners.chunked(5)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { listener ->
                            SpaceListenerAvatar(listener)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            
            // Bottom controls - أفضل من X
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // Reactions bar - تفاعلات فقط بدون هدايا
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("❤️", "😂", "👏", "🔥", "🎉", "💯").forEach { emoji ->
                            FilledTonalIconButton(onClick = { onSendReaction(emoji) }, modifier = Modifier.size(40.dp)) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        if (isSpeaker || isHost) {
                            FilledIconButton(
                                onClick = onToggleMute,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, null, tint = Color.White)
                            }
                        } else {
                            FilledTonalIconButton(onClick = onRaiseHand, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.PanTool, null)
                            }
                        }
                        if (isHost) {
                            IconButton(onClick = onStartRecording) {
                                Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, null, tint = if (isRecording) Color(0xFFE53935) else Color.White)
                            }
                        }
                        IconButton(onClick = { /* share */ }) {
                            Icon(Icons.Filled.Share, null, tint = Color.White)
                        }
                        Button(
                            onClick = onLeaveSpace,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Filled.ExitToApp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("مغادرة", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceParticipantAvatar(
    participant: SpaceParticipant,
    isSpeaker: Boolean,
    isHost: Boolean = false,
    showHand: Boolean = false,
    onMute: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onApprove: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
        Box {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (isHost) Color(0xFFFFD700).copy(alpha = 0.2f) else if (isSpeaker) Color(0xFF9C27B0).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(participant.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (participant.isMuted && isSpeaker) {
                Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MicOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
            if (showHand) {
                Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) {
                    Text("✋", fontSize = 10.sp)
                }
            }
            if (isHost) {
                Box(Modifier.align(Alignment.TopStart).size(16.dp).clip(CircleShape).background(Color(0xFFFFD700)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Star, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(participant.name, color = Color.White, fontSize = 10.sp, maxLines = 1)
        if (onApprove != null) {
            Button(onClick = onApprove, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("موافقة", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SpaceListenerAvatar(participant: SpaceParticipant) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(participant.name.take(1), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(participant.name, color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
    }
}

data class SpaceParticipant(
    val id: String,
    val name: String,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false
)
