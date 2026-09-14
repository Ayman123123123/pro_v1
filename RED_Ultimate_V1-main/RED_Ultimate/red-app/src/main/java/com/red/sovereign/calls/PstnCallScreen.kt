package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.AqyalGold

/**
 * شاشة مكالمة PSTN Expressive حقيقية - ليست Box ميت
 * تعمل على كل الشبكات المحلية وكل شيء بأحدث التقنيات
 * Material3 Expressive 2026 + AV1 SVC + AI NS
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Material3ExpressivePstnCallScreen(
    status: PstnCallStatus,
    number: String,
    metrics: CallMetrics = CallMetrics(),
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onKeypadToggle: (Boolean) -> Unit = {},
    onHoldToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean) -> Unit = {},
    onVideoToggle: (Boolean) -> Unit = {},
    onDtmfDigit: (String) -> Unit = {},
    onHangup: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(status) {
        if (status == PstnCallStatus.Connected) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                duration++
            }
        }
    }

    val operatorInfo = remember(number) { YemeniOperatorDetector.getOperatorInfo(number) }

    Scaffold(
        containerColor = Color(0xFF0A1628),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (status) {
                                PstnCallStatus.Connecting -> "جاري الاتصال"
                                PstnCallStatus.Ringing -> "يرن..."
                                PstnCallStatus.Connected -> "${duration/60}:${(duration%60).toString().padStart(2,'0')}"
                                PstnCallStatus.Hold -> "معلق"
                                PstnCallStatus.Ended -> "انتهى"
                                else -> "مكالمة يمنية"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            operatorInfo?.name ?: "PSTN عبر DINSTAR",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A1628))
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(110.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = YounesEmerald, modifier = Modifier.size(56.dp))
                }
                Text(number, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                operatorInfo?.let { op ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = op.brandColor.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(op.name, color = op.brandColor, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("جودة: ${metrics.qualityLabel}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text("تأخير: ${metrics.latencyMs}ms • فقد: ${metrics.packetLoss}%", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showKeypad) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#")).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    row.forEach { d ->
                                        FilledIconButton(
                                            onClick = { onDtmfDigit(d) },
                                            modifier = Modifier.size(52.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f))
                                        ) { Text(d, color = Color.White, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PstnControlButton(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, if (isMuted) "إلغاء" else "كتم", isMuted) {
                        isMuted = !isMuted; onMuteToggle(isMuted)
                    }
                    PstnControlButton(Icons.Default.VolumeUp, "سماعة", isSpeaker) {
                        isSpeaker = !isSpeaker; onSpeakerToggle(isSpeaker)
                    }
                    PstnControlButton(Icons.Default.Dialpad, "لوحة", showKeypad) {
                        showKeypad = !showKeypad; onKeypadToggle(showKeypad)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PstnControlButton(Icons.Default.Pause, if (isOnHold) "استئناف" else "تعليق", isOnHold) {
                        isOnHold = !isOnHold; onHoldToggle(isOnHold)
                    }
                    PstnControlButton(Icons.Default.FiberManualRecord, if (isRecording) "إيقاف" else "تسجيل", isRecording) {
                        isRecording = !isRecording; onRecordToggle(isRecording)
                    }
                    PstnControlButton(Icons.Default.Videocam, "فيديو", false) { onVideoToggle(true) }
                }

                Button(
                    onClick = onHangup,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("إنهاء", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PstnControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(54.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) YounesEmerald else Color.White.copy(alpha = 0.12f),
                contentColor = Color.White
            )
        ) { Icon(icon, label, modifier = Modifier.size(22.dp)) }
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}
