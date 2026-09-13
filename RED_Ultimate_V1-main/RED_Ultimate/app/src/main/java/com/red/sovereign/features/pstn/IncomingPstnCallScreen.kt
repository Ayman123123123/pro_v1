package com.red.sovereign.features.pstn

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val GreenAccept = Color(0xFF43A047)
private val RedDecline = Color(0xFFE53935)
private val GoldGsm = Color(0xFFF57C00)

/**
 * شاشة مكالمة PSTN واردة (وحدة app).
 *
 * مكيّفة من الأرشيف التاريخي (android/features/dinstar/IncomingPstnCallScreen.kt)
 * على كاشف [YemeniOperatorDetector] المصلح (نفس الحزمة — بلا استيراد خارجي).
 *
 * - رقم المتصل + المزود + لون/أيقونة المزود
 * - أزرار: رد (أخضر بنبض) / رفض (أحمر) / صامت
 * - نبض (pulse) على زر الرد والدائرة
 * - شارة "مكالمة هاتفية واردة" مع أيقونة GSM
 * - رفض تلقائي بعد 30 ثانية
 *
 * ملاحظة Full-screen intent: تُعرض هذه الشاشة من Activity بخاصية
 * showWhenLocked/turnScreenOn (يُضبط في Manifest/EmergencyCallActivity)،
 * وهذا الملف يقدّم الـ UI فقط.
 */
@Composable
fun IncomingPstnCallScreen(
    callerNumber: String,
    callerName: String? = null,
    portIndex: Int = 0,
    signalPercent: Int = 0,
    gatewayHost: String = "",
    callId: String = "",
    onAnswer: (portIndex: Int, callerNumber: String) -> Unit = { _, _ -> },
    onReject: () -> Unit = {},
    onToggleMute: (Boolean) -> Unit = {}
) {
    val opInfo = YemeniOperatorDetector.getOperatorInfo(callerNumber)
    val operatorColor = opInfo.brandColor
    var isMuted by remember { mutableStateOf(false) }

    // مؤقت الرنين — رفض تلقائي بعد 30 ثانية
    var ringDuration by remember { mutableIntStateOf(0) }
    val autoRejectSeconds = 30
    LaunchedEffect(Unit) {
        while (ringDuration < autoRejectSeconds) {
            delay(1000)
            ringDuration++
        }
        onReject()
    }

    // نبض الرنين على زر الرد والدائرة
    val pulse = rememberInfiniteTransition(label = "incoming-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    val remainingSeconds = autoRejectSeconds - ringDuration

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D0D), operatorColor.copy(alpha = 0.08f), Color(0xFF0D0D0D))))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // شارة "مكالمة هاتفية واردة" مع أيقونة GSM
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoldGsm.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, GoldGsm.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.SimCard, null, tint = GoldGsm, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "مكالمة هاتفية واردة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldGsm
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // دائرة المتصل النابضة مع أول حرف للمزود
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(operatorColor.copy(alpha = 0.18f * pulseAlpha + 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = opInfo.iconLetter.ifBlank { "?" },
                    color = operatorColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            if (!callerName.isNullOrBlank()) {
                Text(callerName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                formatIncomingNumber(callerNumber),
                fontSize = if (callerName.isNullOrBlank()) 28.sp else 20.sp,
                fontWeight = if (callerName.isNullOrBlank()) FontWeight.Bold else FontWeight.Medium,
                color = if (callerName.isNullOrBlank()) Color.White else Color(0xFFBDBDBD)
            )

            Spacer(Modifier.height(12.dp))

            // شارة المزود
            Surface(shape = RoundedCornerShape(8.dp), color = operatorColor.copy(alpha = 0.14f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CellTower, null, tint = operatorColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(opInfo.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = operatorColor)
                    if (opInfo.technology.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            opInfo.technology,
                            fontSize = 11.sp,
                            color = operatorColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // معلومات المنفذ + الإشارة
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1A1A1A),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.SimCard, null, tint = operatorColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("منفذ ${portIndex + 1}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(16.dp))
                    Icon(Icons.Rounded.SignalCellularAlt, null, tint = GoldGsm, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$signalPercent%", fontSize = 12.sp, color = GoldGsm)
                    if (gatewayHost.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        Text(gatewayHost, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("يرن... (${remainingSeconds}ث)", fontSize = 14.sp, color = Color(0xFFFFB300).copy(alpha = 0.85f))
        }

        // أزرار الرد/الرفض/الصامت
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onReject,
                    containerColor = RedDecline,
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, "رفض", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("رفض", color = RedDecline, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        onToggleMute(isMuted)
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    Icon(
                        if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        "صامت",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("صامت", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { onAnswer(portIndex, callerNumber) },
                    containerColor = GreenAccept,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(68.dp)
                        .scale(pulseScale)
                ) {
                    Icon(
                        Icons.Rounded.Call, "رد",
                        tint = Color.White.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("رد", color = GreenAccept, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // أيقونة GSM علوية
        Icon(
            Icons.Rounded.PhoneInTalk, null,
            tint = GoldGsm.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(20.dp)
        )
    }
}

private fun formatIncomingNumber(number: String): String {
    if (number.isEmpty()) return "رقم غير معروف"
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 1..3 -> number
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> "${number.take(3)} ${number.substring(3, 6)} ${number.drop(6)}"
    }
}
