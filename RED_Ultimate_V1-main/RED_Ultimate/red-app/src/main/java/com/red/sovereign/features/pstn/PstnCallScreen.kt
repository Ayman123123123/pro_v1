package com.red.sovereign.features.pstn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import kotlinx.coroutines.delay
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.features.dinstar.YemenOperator
import com.red.sovereign.ui.components.SovereignOperatorBadge
import com.red.sovereign.ui.components.SovereignStatusBadge
import com.red.sovereign.ui.components.SovereignWaveVisualizer
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients
import com.red.sovereign.auth.PstnState
import com.red.sovereign.auth.AuthViewModel

/**
 * 🇾🇪 DINSTAR GSM & Yemeni PSTN Calling Screen — Ultimate Luxury Experience
 */
@Composable
fun PstnCallScreen(
    number: String,
    state: PstnState,
    onHangup: () -> Unit,
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean, String) -> Unit = { _, _ -> },
    viewModel: AuthViewModel? = null
) {
    var callDuration by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var showDialpad by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val operator = YemenOperator.fromNumber(number)
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)
    val startedState = state as? PstnState.Started
    val callId = startedState?.callId ?: ""
    val isAnswered = startedState?.answered == true
    val isRinging = state is PstnState.Ringing || (startedState?.ringing == true)
    val isBridging = state is PstnState.Bridging || state is PstnState.Registering

    // Call Timer
    LaunchedEffect(isAnswered) {
        if (isAnswered) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF070B14),
                        Color(0xFF030712)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SovereignStatusBadge(
                label = "بوابة DINSTAR المركزية 📶",
                glowColor = SovereignColors.GoldNeon,
                textColor = Color.White
            )
        }

        Spacer(Modifier.weight(0.4f))

        // Profile / Operator Avatar
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(SovereignColors.SurfaceCard)
                .border(2.dp, SovereignGradients.dinstar, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PhoneInTalk,
                contentDescription = null,
                tint = SovereignColors.GoldNeon,
                modifier = Modifier.size(52.dp)
            )
        }
        
        Spacer(Modifier.height(20.dp))
        
        Text(
            text = formatPhoneNumber(number),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(10.dp))

        // المشغل اليمني التلقائي
        SovereignOperatorBadge(operator = operator)

        Spacer(Modifier.height(18.dp))

        Text(
            text = when {
                isAnswered -> formatDuration(callDuration)
                isRinging -> "جاري رنين الهاتف..."
                isBridging -> "جاري الاتصال بالخادم..."
                state is PstnState.Error -> "خطأ: ${(state as PstnState.Error).message}"
                else -> "جاري توجيه المكالمة عبر الشريحة..."
            },
            color = when {
                isAnswered -> SovereignColors.EmeraldNeon
                state is PstnState.Error -> SovereignColors.RubyNeon
                else -> SovereignColors.GoldLight
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        // عرض العداد اليومي عند توفر القيم
        startedState?.let { st ->
            if (st.dailyLimit > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "مكالمات اليوم: ${st.usedToday} / ${st.dailyLimit}",
                    color = SovereignColors.GoldLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isAnswered) {
            Spacer(Modifier.height(20.dp))
            SovereignWaveVisualizer(
                modifier = Modifier.width(240.dp),
                isSpeaking = !isMuted,
                barColor = SovereignColors.GoldNeon
            )
        }

        Spacer(Modifier.weight(1f))

        // Call Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = "كتم",
                isActive = isMuted,
                onClick = { isMuted = !isMuted; viewModel?.togglePstnMute(isMuted) ?: onMuteToggle(isMuted) }
            )
            CallControlButton(
                icon = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                label = "تسجيل",
                isActive = isRecording,
                onClick = { 
                    if (callId.isNotEmpty()) {
                        isRecording = !isRecording
                        onRecordToggle(isRecording, callId)
                    }
                }
            )
            CallControlButton(
                icon = if (isSpeaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                label = "مكبر",
                isActive = isSpeaker,
                onClick = { isSpeaker = !isSpeaker; viewModel?.togglePstnSpeaker(isSpeaker) ?: onSpeakerToggle(isSpeaker) }
            )
        }

        Spacer(Modifier.height(36.dp))

        // Hangup Button
        FloatingActionButton(
            onClick = onHangup,
            containerColor = SovereignColors.RubyNeon,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (isActive) SovereignColors.Gold.copy(alpha = 0.25f) else SovereignColors.SurfaceCard)
                .border(
                    1.2.dp,
                    if (isActive) SovereignColors.GoldNeon else SovereignColors.GlassBorder,
                    CircleShape
                )
        ) {
            Icon(
                icon,
                label,
                tint = if (isActive) SovereignColors.GoldNeon else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun formatPhoneNumber(number: String): String {
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> number
    }
}
