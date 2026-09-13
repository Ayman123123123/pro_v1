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
import kotlinx.coroutines.isActive
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
    /** النغمات التي قُبلت للإرسال فعلًا — تُعرض للتأكيد البصري. */
    var dtmfSent by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val operator = YemenOperator.fromNumber(number)
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)
    val startedState = state as? PstnState.Started
    val callId = startedState?.callId ?: ""
    val isAnswered = startedState?.answered == true
    val isRinging = state is PstnState.Ringing || (startedState?.ringing == true)
    val isBridging = state is PstnState.Bridging || state is PstnState.Registering
    // وسائط مبكرة: الصوت يتدفّق من الشبكة والمكالمة لم تُجَب. لوحة الأرقام
    // يجب أن تعمل هنا تحديدًا لأن قوائم المزوّد تُشغَّل قبل الرد.
    val isEarlyMedia = state is PstnState.EarlyMedia
    // ضوابط الصوت وDTMF تعمل في الحالتين: مكالمة مُجابة أو صوت شبكة.
    val mediaLive = isAnswered || isEarlyMedia

    // Call Timer — cancelled on dispose via isActive.
    LaunchedEffect(isAnswered) {
        if (isAnswered) {
            while (isActive) {
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
                contentDescription = "أيقونة المكالمة",
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
                isEarlyMedia -> "صوت الشبكة — ردّ المزوّد"
                isRinging -> "جاري رنين الهاتف..."
                isBridging -> "جاري الاتصال بالخادم..."
                state is PstnState.Error -> "خطأ: ${(state as PstnState.Error).message}"
                else -> "جاري توجيه المكالمة عبر الشريحة..."
            },
            color = when {
                isAnswered -> SovereignColors.EmeraldNeon
                isEarlyMedia -> SovereignColors.EmeraldNeon
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

        if (mediaLive) {
            Spacer(Modifier.height(20.dp))
            SovereignWaveVisualizer(
                modifier = Modifier.width(240.dp),
                isSpeaking = !isMuted,
                barColor = SovereignColors.GoldNeon
            )
        }

        Spacer(Modifier.weight(1f))

        // ─── لوحة نغمات DTMF ────────────────────────────────────────────
        // كانت `showDialpad` معرَّفة ولا يقرؤها شيء: لا زرّ يفتحها ولا لوحة
        // تُرسم. فلم تكن هناك أي وسيلة للتفاعل مع قوائم مزوّد الخدمة من
        // شاشة المكالمة الحيّة — وهي أكثر ما يحتاجه المستخدم فعلًا
        // (استعلام رصيد، تعبئة، خدمة العملاء).
        if (showDialpad && mediaLive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dtmfSent.isNotEmpty()) {
                    Text(
                        text = dtmfSent,
                        color = SovereignColors.GoldNeon,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            IconButton(
                                onClick = {
                                    // الإرسال الحقيقي إلى الشبكة؛ لا نضيف
                                    // الرقم للعرض إلا إن قُبل فعلًا، حتى لا
                                    // يظن المستخدم أنه أُرسل وهو لم يُرسَل.
                                    val ok = viewModel?.sendPstnDtmf(digit) ?: false
                                    if (ok) dtmfSent += digit
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(SovereignColors.SurfaceCard)
                                    .border(1.dp, SovereignColors.GlassBorder, CircleShape)
                            ) {
                                Text(
                                    digit,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

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
                icon = Icons.Rounded.Dialpad,
                label = "أرقام",
                isActive = showDialpad,
                onClick = { showDialpad = !showDialpad }
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
