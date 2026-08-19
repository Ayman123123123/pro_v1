package com.red.sovereign.features.pstn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.PstnState
import com.red.sovereign.features.dinstar.YemenOperator
import com.red.sovereign.ui.components.SovereignOperatorBadge
import com.red.sovereign.ui.components.SovereignStatusBadge
import com.red.sovereign.ui.components.SovereignWaveVisualizer
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients
import kotlinx.coroutines.delay

/**
 * شاشة المكالمة الهاتفية عبر بوابة DINSTAR — التجربة السيادية الفاخرة.
 *
 * المشغّل يُشتقّ من بادئة الرقم عبر [YemenOperator.fromNumber] وهو
 * المصدر الوحيد للحقيقة، فلا يظهر للمستخدم مشغّل يخالف الشريحة التي
 * تمرّ عبرها مكالمته فعلًا.
 */
@Composable
fun PstnCallScreen(
    number: String,
    state: PstnState,
    onHangup: () -> Unit,
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean, String) -> Unit = { _, _ -> }
) {
    var callDuration by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val operator = YemenOperator.fromNumber(number)
    val startedState = state as? PstnState.Started
    val callId = startedState?.callId.orEmpty()
    val isConnected = startedState != null
    val isDialing = state is PstnState.Dialing
    val errorMessage = (state as? PstnState.Error)?.message

    // عدّاد المدّة يبدأ عند تأكيد الخادم بدء المكالمة
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                callDuration++
            }
        } else {
            callDuration = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF070B14), Color(0xFF030712))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SovereignStatusBadge(
                label = "بوابة DINSTAR المركزية",
                glowColor = SovereignColors.GoldNeon,
                textColor = Color.White
            )
        }

        Spacer(Modifier.weight(0.4f))

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

        SovereignOperatorBadge(operator = operator)

        Spacer(Modifier.height(18.dp))

        Text(
            text = when {
                errorMessage != null -> "خطأ: $errorMessage"
                isConnected -> formatCallDuration(callDuration)
                isDialing -> "جارٍ الاتصال بالخادم..."
                else -> "جارٍ توجيه المكالمة عبر الشريحة..."
            },
            color = when {
                errorMessage != null -> SovereignColors.RubyNeon
                isConnected -> SovereignColors.EmeraldNeon
                else -> SovereignColors.GoldLight
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        startedState?.let { started ->
            if (started.dailyLimit > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "مكالمات اليوم: ${started.usedToday} / ${started.dailyLimit}",
                    color = SovereignColors.GoldLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (isConnected) {
            Spacer(Modifier.height(20.dp))
            SovereignWaveVisualizer(
                modifier = Modifier.width(240.dp),
                isSpeaking = !isMuted,
                barColor = SovereignColors.GoldNeon
            )
        }

        Spacer(Modifier.weight(1f))

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
                onClick = {
                    isMuted = !isMuted
                    onMuteToggle(isMuted)
                }
            )
            CallControlButton(
                icon = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                label = "تسجيل",
                isActive = isRecording,
                enabled = callId.isNotEmpty(),
                onClick = {
                    if (callId.isNotEmpty()) {
                        isRecording = !isRecording
                        onRecordToggle(isRecording, callId)
                    }
                }
            )
            CallControlButton(
                icon = if (isSpeaker) {
                    Icons.AutoMirrored.Rounded.VolumeUp
                } else {
                    Icons.AutoMirrored.Rounded.VolumeDown
                },
                label = "مكبر",
                isActive = isSpeaker,
                onClick = {
                    isSpeaker = !isSpeaker
                    onSpeakerToggle(isSpeaker)
                }
            )
        }

        Spacer(Modifier.height(36.dp))

        FloatingActionButton(
            onClick = onHangup,
            containerColor = SovereignColors.RubyNeon,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Rounded.CallEnd,
                contentDescription = "إنهاء",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) {
                        SovereignColors.Gold.copy(alpha = 0.25f)
                    } else {
                        SovereignColors.SurfaceCard
                    }
                )
                .border(
                    width = 1.2.dp,
                    color = if (isActive) {
                        SovereignColors.GoldNeon
                    } else {
                        SovereignColors.GlassBorder
                    },
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) SovereignColors.GoldNeon else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** تنسيق مدّة المكالمة بالثواني إلى دقائق:ثوانٍ. */
private fun formatCallDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
