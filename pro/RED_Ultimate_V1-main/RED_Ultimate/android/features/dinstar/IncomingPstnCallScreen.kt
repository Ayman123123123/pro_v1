package com.red.features.dinstar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.red.core.theme.SovereignColors
import kotlinx.coroutines.delay

/**
 * 📞 YOUNES Incoming PSTN Call Screen — شاشة مكالمة واردة عبر DINSTAR
 *
 * تظهر عند استقبال مكالمة PSTN واردة:
 * - Dinstar WebSocket → DINSTAR_CDR/PORT_STATUS event → ViewModel → هذه الشاشة
 * - تعرض: رقم المتصل + المشغل + منفذ SIM + الإشارة
 * - أزرار: قبول (أخضر) + رفض (أحمر)
 * - مؤقت رنين (30ث تلقائي الرفض)
 *
 * التدفق:
 * 1. WebSocket يستقبل PORT_STATUS event: callState=RINGING
 * 2. ViewModel يُصدر DinstarEvent.CallStateChanged
 * 3. Navigation ينتقل لشاشة IncomingPstnCallScreen
 * 4. المستخدم يضغط قبول أو رفض
 * 5. إذا قبول ← Asterisk AMI Answer ← PstnCallScreen (ACTIVE)
 * 6. إذا رفض ← Asterisk AMI Hangup ← الرجوع
 */
@Composable
fun IncomingPstnCallScreen(
    callerNumber: String,
    callerName: String? = null,
    portIndex: Int,
    signalPercent: Int = 0,
    viewModel: DinstarViewModel,
    onAnswer: (portIndex: Int, callerNumber: String) -> Unit,
    onReject: () -> Unit
) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val port = gatewayStatus.ports.find { it.index == portIndex }
    val operator = YemenOperator.fromNumber(callerNumber)
    val operatorColor = Color(operator.colorHex)

    // مؤقت الرنين — رفض تلقائي بعد 30 ثانية
    var ringDuration by remember { mutableIntStateOf(0) }
    val autoRejectSeconds = 30
    LaunchedEffect(Unit) {
        while (ringDuration < autoRejectSeconds) {
            delay(1000)
            ringDuration++
        }
        // رفض تلقائي
        onReject()
    }

    // نبض الرنين
    val pulseInfinite = rememberInfiniteTransition()
    val ringPulse by pulseInfinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "RingPulse"
    )
    val ringScale by pulseInfinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "RingScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SovereignColors.Obsidian,
                        operatorColor.copy(alpha = 0.04f),
                        SovereignColors.Obsidian
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // شارة DINSTAR واردة
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SovereignColors.DinstarGold.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, SovereignColors.DinstarGold.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SimCard, null,
                        tint = SovereignColors.DinstarGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "مكالمة واردة — DINSTAR Gateway",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignColors.DinstarGold
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // أيقونة المتصل مع نبض الرنين
            Box(
                modifier = Modifier
                    .size(140.dp * ringScale)
                    .clip(CircleShape)
                    .background(operatorColor.copy(alpha = ringPulse * 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PhoneInTalk,
                    null,
                    tint = operatorColor.copy(alpha = ringPulse),
                    modifier = Modifier.size(64.dp)
                )
            }

            // حلقات الرنين المتوسعة
            Box(
                modifier = Modifier
                    .size(180.dp * ringScale)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(2.dp, operatorColor.copy(alpha = ringPulse * 0.3f), CircleShape)
            )

            Spacer(Modifier.height(32.dp))

            // اسم المتصل (إن وجد)
            if (!callerName.isNullOrBlank()) {
                Text(
                    callerName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
            }

            // الرقم
            Text(
                callerNumber,
                fontSize = if (callerName.isNullOrBlank()) 28.sp else 20.sp,
                fontWeight = if (callerName.isNullOrBlank()) FontWeight.Bold else FontWeight.Medium,
                color = if (callerName.isNullOrBlank()) Color.White else Color.Gray,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(12.dp))

            // شارة المشغل
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = operatorColor.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CellTower, null,
                        tint = operatorColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        operator.arabicName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = operatorColor
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // معلومات المنفذ
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SovereignColors.SurfaceNavy,
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SimCard, null,
                        tint = operatorColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "منفذ ${portIndex}",
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(16.dp))
                    Icon(
                        Icons.Rounded.SignalCellularAlt, null,
                        tint = SovereignColors.DinstarGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${port?.signalPercent ?: signalPercent}%",
                        fontSize = 12.sp,
                        color = SovereignColors.DinstarGold
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        port?.operatorName ?: operator.arabicName,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // مؤقت الرنين
            val remainingSeconds = autoRejectSeconds - ringDuration
            Text(
                "يرن... (${remainingSeconds}ث)",
                fontSize = 14.sp,
                color = SovereignColors.Warning.copy(alpha = 0.8f)
            )
        }

        // ═══ أزرار القبول والرفض ═══
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // زر الرفض (أحمر)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onReject,
                    containerColor = SovereignColors.Danger,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Rounded.CallEnd, null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("رفض", color = SovereignColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // زر القبول (أخضر)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { onAnswer(portIndex, callerNumber) },
                    containerColor = SovereignColors.Success,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Rounded.Call, null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("قبول", color = SovereignColors.Success, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
