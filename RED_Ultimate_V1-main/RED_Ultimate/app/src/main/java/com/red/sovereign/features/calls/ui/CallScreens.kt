package com.red.sovereign.features.calls.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.calls.VoipState
import com.red.sovereign.features.calls.CallUiState

// ─── Color Palette ─────────────────────────────────────────────────────────
private val RedDark = Color(0xFF1A0A0A)
private val RedAccent = Color(0xFFE53935)
private val GreenAccent = Color(0xFF4CAF50)
private val DarkGray = Color(0xFF212121)
private val MidGray = Color(0xFF424242)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFBDBDBD)

// ─── IncomingCallScreen ────────────────────────────────────────────────────

/**
 * شاشة الرنين الواردة.
 * تُعرض عند استقبال OFFER من شخص آخر.
 * تحتوي على نبضة متحركة حول الصورة الرمزية، وزرَّي رد ورفض.
 */
@Composable
fun IncomingCallScreen(
    uiState: CallUiState,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onAnswerVideo: () -> Unit = {}
) {
    val isVideoCall = uiState.session?.isVideo == true
    val callerName = uiState.remoteDisplayName.ifBlank { uiState.remoteUserId }

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse1"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = 200, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse2"
    )
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.0f,
        animationSpec = infiniteRepeatable(tween(900, delayMillis = 400, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D0D0D), Color(0xFF1C1C1C), Color(0xFF0D0D0D)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.weight(1f))

            // Call type label
            Text(
                text = if (isVideoCall) "📹 مكالمة فيديو واردة" else "📞 مكالمة صوتية واردة",
                color = TextSecondary,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(24.dp))

            // Avatar with pulse rings
            Box(contentAlignment = Alignment.Center) {
                // Pulse rings (outermost to innermost)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulse3)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulse2)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulse1)
                        .clip(CircleShape)
                        .background(GreenAccent.copy(alpha = 0.18f))
                )
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF3E3E3E), Color(0xFF1A1A1A))
                            )
                        )
                        .border(2.dp, GreenAccent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        color = TextPrimary,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Caller Name
            Text(
                text = callerName,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // RED ID
            Text(
                text = "@${uiState.remoteUserId}",
                color = RedAccent.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            Spacer(Modifier.weight(1f))

            // Action Buttons
            if (isVideoCall) {
                // Video call: three buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reject
                    CallActionButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "رفض",
                        backgroundColor = RedAccent,
                        size = 72.dp,
                        onClick = onReject
                    )
                    // Answer Audio
                    CallActionButton(
                        icon = Icons.Rounded.Call,
                        label = "صوت فقط",
                        backgroundColor = MidGray,
                        size = 64.dp,
                        onClick = onAnswer
                    )
                    // Answer Video
                    CallActionButton(
                        icon = Icons.Rounded.Videocam,
                        label = "رد بالفيديو",
                        backgroundColor = GreenAccent,
                        size = 72.dp,
                        onClick = onAnswerVideo
                    )
                }
            } else {
                // Audio call: two buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallActionButton(
                        icon = Icons.Rounded.CallEnd,
                        label = "رفض",
                        backgroundColor = RedAccent,
                        size = 76.dp,
                        onClick = onReject
                    )
                    CallActionButton(
                        icon = Icons.Rounded.Call,
                        label = "رد",
                        backgroundColor = GreenAccent,
                        size = 76.dp,
                        onClick = onAnswer
                    )
                }
            }

            // Quick decline messages
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            ) {
                listOf("مشغول الآن", "سأتصل لاحقاً", "لحظة من فضلك").forEach { msg ->
                    QuickReplyChip(text = msg, onClick = onReject)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─── ActiveCallScreen ──────────────────────────────────────────────────────

/**
 * شاشة المكالمة النشطة.
 * تدعم الصوت فقط والفيديو، مع جميع أزرار التحكم.
 */
@Composable
fun ActiveCallScreen(
    uiState: CallUiState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHoldCall: () -> Unit,
    localVideoView: @Composable (() -> Unit)? = null,
    remoteVideoView: @Composable (() -> Unit)? = null
) {
    val isVideo = uiState.session?.isVideo == true && uiState.isVideoEnabled
    val remoteName = uiState.remoteDisplayName.ifBlank { uiState.remoteUserId }
    val durationFormatted = formatDuration(uiState.durationSeconds)
    val isOnHold = uiState.isOnHold
    val callState = uiState.callState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isVideo) Color.Black else Color(0xFF0D0D0D))
    ) {
        // ── Remote Video (full screen when video call) ─────────────
        if (isVideo && remoteVideoView != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                remoteVideoView()
            }
        }

        // ── Audio Call Background ──────────────────────────────────
        if (!isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D0D0D), Color(0xFF1A1A1A), Color(0xFF0D0D0D))
                        )
                    )
            )
        }

        // ── Main Content ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // Status chip
            Surface(
                color = when (callState) {
                    VoipState.CONNECTING -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    VoipState.ACTIVE -> GreenAccent.copy(alpha = 0.15f)
                    VoipState.ON_HOLD -> Color(0xFF9E9E9E).copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (callState) {
                        VoipState.RINGING_OUTGOING -> "جاري الاتصال..."
                        VoipState.CONNECTING -> "⟳ جاري التوصيل"
                        VoipState.ACTIVE -> if (isOnHold) "⏸ إيقاف مؤقت" else "● اتصال مباشر"
                        else -> ""
                    },
                    color = when (callState) {
                        VoipState.CONNECTING -> Color(0xFFFF9800)
                        VoipState.ACTIVE -> if (isOnHold) TextSecondary else GreenAccent
                        else -> TextSecondary
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            if (!isVideo) {
                // Audio: Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF3E3E3E), Color(0xFF1A1A1A)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = remoteName.take(1).uppercase(),
                        color = TextPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Name
            Text(
                text = remoteName,
                color = TextPrimary,
                fontSize = if (isVideo) 20.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Duration
            Text(
                text = durationFormatted,
                color = if (callState == VoipState.ACTIVE) GreenAccent else TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            // Error banner
            uiState.errorMessage?.let { err ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = RedAccent.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = err,
                        color = RedAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Local video PiP (video calls) ─────────────────────
            if (isVideo && localVideoView != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(width = 100.dp, height = 140.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                        .padding(bottom = 8.dp)
                ) {
                    localVideoView()
                }
            }

            // ── Control Bar ───────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                // Row 1: Mute, Speaker, Video, Camera switch
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallControlButton(
                        icon = if (uiState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = if (uiState.isMuted) "كتم" else "ميكروفون",
                        active = uiState.isMuted,
                        activeColor = RedAccent.copy(alpha = 0.3f),
                        onClick = onToggleMute
                    )
                    SmallControlButton(
                        icon = if (uiState.isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        label = "مكبر",
                        active = uiState.isSpeakerOn,
                        activeColor = Color(0xFF1E88E5).copy(alpha = 0.3f),
                        onClick = onToggleSpeaker
                    )
                    if (uiState.session?.isVideo == true) {
                        SmallControlButton(
                            icon = if (uiState.isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            label = "كاميرا",
                            active = !uiState.isVideoEnabled,
                            activeColor = RedAccent.copy(alpha = 0.3f),
                            onClick = onToggleVideo
                        )
                        SmallControlButton(
                            icon = Icons.Rounded.FlipCameraAndroid,
                            label = "تبديل",
                            active = false,
                            onClick = onSwitchCamera
                        )
                    }
                    SmallControlButton(
                        icon = if (isOnHold) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                        label = if (isOnHold) "استئناف" else "إيقاف",
                        active = isOnHold,
                        activeColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                        onClick = onHoldCall
                    )
                }

                Spacer(Modifier.height(32.dp))

                // End call FAB
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = RedAccent,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "إنهاء المكالمة",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("إنهاء", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ─── Helper Composables ────────────────────────────────────────────────────

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = backgroundColor,
            modifier = Modifier.size(size)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SmallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = Color.White.copy(alpha = 0.15f),
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.White else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp
        )
    }
}

// ─── Utils ─────────────────────────────────────────────────────────────────

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
