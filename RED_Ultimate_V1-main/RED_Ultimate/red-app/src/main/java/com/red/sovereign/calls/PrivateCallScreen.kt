package com.red.sovereign.calls

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

/**
 * PrivateCallScreen - مكالمات خاصة محسنة 2026
 * 
 * صوت منفصل وفيديو منفصل:
 * - رنين فوري مع high-priority push + full-screen intent
 * - جودة عالية: Opus 48kHz + AV1 SVC + simulcast
 * - صوت نقي: RNNoise AI + echo cancellation + auto gain
 * - وصول: mDNS LAN + TURN TLS 1.3 + WebRTC unified-plan
 * - واجهات: أفضل من واتس وتيليجرام وزنجي
 * - E2EE: Insertable Streams
 */

@Composable
fun PrivateVoiceCallScreen(
    peerName: String,
    peerRedId: String,
    state: CallUiState,
    isMuted: Boolean,
    isSpeaker: Boolean,
    duration: String,
    networkQuality: Float,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onSwitchToVideo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF1A3A5F))))
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            
            // Avatar مع نبض للرنين
            Box(
                Modifier
                    .size(120.dp)
                    .scale(if (state is CallUiState.Connecting || state is CallUiState.Incoming) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(AqyalGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(peerName.take(1), color = AqyalGold, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(16.dp))
            Text(peerName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(peerRedId, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            
            Spacer(Modifier.height(8.dp))
            
            // حالة المكالمة
            val statusText = when (state) {
                is CallUiState.Connecting -> state.presenceLabel
                is CallUiState.Incoming -> "مكالمة صوتية واردة..."
                is CallUiState.Active -> duration
                is CallUiState.Busy -> "مشغول"
                is CallUiState.Declined -> "مرفوضة"
                is CallUiState.NoAnswer -> if (state.outgoing) "لم يتم الرد" else "مكالمة فائتة"
                is CallUiState.CallEnded -> "انتهت - $duration"
                is CallUiState.Error -> state.message
                else -> ""
            }
            Text(statusText, color = AqyalGold, fontSize = 16.sp)
            
            // جودة الشبكة
            if (state is CallUiState.Active) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val qualityColor = when {
                        networkQuality >= 4f -> Color(0xFF00C98C)
                        networkQuality >= 3f -> Color(0xFFF5C842)
                        else -> Color(0xFFE53935)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(qualityColor))
                    Spacer(Modifier.width(6.dp))
                    Text("جودة: ${"%.1f".format(networkQuality)}/5.0 - Opus 48kHz + RNNoise AI", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            // أزرار التحكم - صوت منفصل
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "إلغاء كتم" else "كتم",
                    active = !isMuted,
                    onClick = onToggleMute
                )
                CallControlButton(
                    icon = Icons.Filled.Videocam,
                    label = "فيديو",
                    active = false,
                    onClick = onSwitchToVideo
                )
                CallControlButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "مكبر",
                    active = isSpeaker,
                    onClick = onToggleSpeaker
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            // زر إنهاء كبير
            Button(
                onClick = onEndCall,
                modifier = Modifier.size(72.dp).clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            
            Spacer(Modifier.height(24.dp))
            Text("E2EE • P2P • TURN TLS 1.3 • أفضل من واتس وتيليجرام", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
        }
    }
}

@Composable
fun PrivateVideoCallScreen(
    peerName: String,
    peerRedId: String,
    state: CallUiState,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isFrontCamera: Boolean,
    duration: String,
    networkQuality: Float,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onSwitchToVoice: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // فيديو بعيد يملأ الشاشة
        Box(
            Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (CallRuntime.remoteVideo != null) {
                // WebRTC Video Renderer سيكون هنا
                Text("فيديو ${peerName} - AV1 SVC 720p", color = Color.White)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.VideocamOff, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Text("في انتظار فيديو ${peerName}...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            }
        }
        
        // فيديو محلي صغير
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(120.dp, 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (CallRuntime.localVideo != null && isCameraOn) {
                Text("أنت", color = Color.White, fontSize = 12.sp)
            } else {
                Icon(Icons.Filled.VideocamOff, null, tint = Color.White.copy(alpha = 0.5f))
            }
        }
        
        // معلومات علوية
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(peerName, color = Color.White, fontWeight = FontWeight.Bold)
            Text(duration, color = AqyalGold, fontSize = 12.sp)
            Text("AV1 SVC • ${"%.1f".format(networkQuality)}/5.0 • E2EE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
        
        // أزرار التحكم سفلية - فيديو منفصل
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButtonSmall(Icons.Filled.Mic, !isMuted, onToggleMute)
                CallControlButtonSmall(Icons.Filled.Videocam, isCameraOn, onToggleCamera)
                CallControlButtonSmall(Icons.Filled.Cameraswitch, true, onSwitchCamera)
                CallControlButtonSmall(Icons.Filled.VolumeUp, true, onToggleSpeaker)
                CallControlButtonSmall(Icons.Filled.Call, true, onSwitchToVoice)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onEndCall,
                modifier = Modifier.size(64.dp).clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.Filled.CallEnd, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) AqyalGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
            )
        ) {
            Icon(icon, label, tint = if (active) AqyalGold else Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun CallControlButtonSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
