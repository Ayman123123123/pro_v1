package com.red.features.dinstar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.red.core.theme.SovereignColors
import kotlinx.coroutines.delay

/**
 * 📞 YOUNES PSTN Call Screen — شاشة مكالمة خطية عبر DINSTAR
 * 
 * التدفق:
 * 1. المستخدم يُدخل الرقم في PstnDialerScreen
 * 2. يضغط "اتصال" ← ViewModel يختار أفضل منفذ
 * 3. الطلب يذهب للباكند: POST /api/pstn/dial
 * 4. الباكند ← Asterisk AMI ← PJSIP ← DINSTAR SIM Port
 * 5. هذه الشاشة تعرض حالة المكالمة الحية
 */
@Composable
fun PstnCallScreen(
    phoneNumber: String,
    selectedPort: Int? = null,
    viewModel: DinstarViewModel,
    onEnd: () -> Unit
) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val optimalPort = selectedPort?.let { p -> gatewayStatus.ports.find { it.index == p } }
        ?: viewModel.selectOptimalPort(phoneNumber)

    var callState by remember { mutableStateOf("CONNECTING") }
    var callDuration by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    // مؤقت المكالمة
    LaunchedEffect(callState) {
        if (callState == "ACTIVE") {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    // محاكاة تقدم المكالمة (في الإنتاج: WebSocket من الباكند)
    LaunchedEffect(Unit) {
        delay(2000)
        if (callState == "CONNECTING") callState = "RINGING"
        delay(3000)
        if (callState == "RINGING") callState = "ACTIVE"
    }

    val operator = YemenOperator.fromNumber(phoneNumber)
    val operatorColor = Color(operator.colorHex)

    val pulseInfinite = rememberInfiniteTransition()
    val pulseAlpha by pulseInfinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "CallPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SovereignColors.Obsidian,
                        operatorColor.copy(alpha = 0.05f),
                        SovereignColors.Obsidian
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // شارة DINSTAR
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SovereignColors.DinstarGold.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, SovereignColors.DinstarGold.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.SimCard, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("خطي اليمني — DINSTAR Gateway", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SovereignColors.DinstarGold)
                }
            }

            Spacer(Modifier.height(40.dp))

            // أيقونة الاتصال مع نبض
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(operatorColor.copy(alpha = pulseAlpha * 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PhoneInTalk,
                    null,
                    tint = operatorColor,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // اسم المشغل
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = operatorColor.copy(alpha = 0.12f)
            ) {
                Text(
                    operator.arabicName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = operatorColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // الرقم
            Text(
                phoneNumber,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            // حالة المكالمة
            val stateText = when (callState) {
                "CONNECTING" -> "جاري الاتصال..."
                "RINGING" -> "يرن..."
                "ACTIVE" -> formatDuration(callDuration)
                "ENDED" -> "انتهت المكالمة"
                else -> callState
            }
            val stateColor = when (callState) {
                "CONNECTING" -> SovereignColors.Warning
                "RINGING" -> SovereignColors.Cyan
                "ACTIVE" -> SovereignColors.Success
                "ENDED" -> Color.Gray
                else -> Color.Gray
            }
            Text(stateText, fontSize = 18.sp, color = stateColor, fontWeight = FontWeight.Medium)

            // معلومات المنفذ
            if (optimalPort != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SovereignColors.SurfaceNavy
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.SimCard, null, tint = operatorColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("منفذ ${optimalPort.index}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(16.dp))
                        Icon(Icons.Rounded.SignalCellularAlt, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${optimalPort.signalPercent}%", fontSize = 12.sp, color = SovereignColors.DinstarGold)
                        Spacer(Modifier.width(16.dp))
                        Text(optimalPort.operatorName, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // ═══ أزرار التحكم ═══
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // الصف العلوي
            if (callState == "ACTIVE") {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp)
                ) {
                    PstnCallControlButton(
                        icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = if (isMuted) "مكتوم" else "كتم",
                        onClick = { isMuted = !isMuted },
                        tint = if (isMuted) SovereignColors.Danger else Color.White
                    )
                    PstnCallControlButton(
                        icon = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        label = "مكبر",
                        onClick = { isSpeakerOn = !isSpeakerOn },
                        tint = if (isSpeakerOn) SovereignColors.Cyan else Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // زر إنهاء المكالمة
            FloatingActionButton(
                onClick = {
                    callState = "ENDED"
                    onEnd()
                },
                containerColor = SovereignColors.Danger,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun PstnCallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

private fun formatDuration(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%02d:%02d".format(min, sec)
}
