package com.red.sovereign.features.pstn

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
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.PstnState
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.AqyalGold

/**
 * شاشة مكالمة PSTN نشطة حقيقية - ليست Box ميت
 * تصميم فاخر Material3 Expressive 2026
 * - حالة المكالمة (رنين، متصل، جاري...)
 * - أزرار تحكم (كتم، سماعة، تسجيل، لوحة مفاتيح)
 * - مؤقت مدة المكالمة
 * - كشف المشغل اليمني
 * - إنهاء المكالمة
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val operatorInfo = remember(number) { YemeniOperatorDetector.getOperatorInfo(number) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(state) {
        if (state is PstnState.Started) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        }
    }

    val statusText = when (state) {
        is PstnState.Dialing -> "جاري الاتصال..."
        is PstnState.Ringing -> "يرن..."
        is PstnState.Registering -> "تسجيل في البوابة..."
        is PstnState.Bridging -> "تجهيز الاتصال..."
        is PstnState.EarlyMedia -> "صوت مبكر..."
        is PstnState.Started -> formatDuration(callDuration)
        is PstnState.Error -> state.message
        else -> "متصل"
    }

    val statusColor = when (state) {
        is PstnState.Error -> Color(0xFFE53935)
        is PstnState.Started -> YounesEmerald
        is PstnState.Ringing -> AqyalGold
        else -> Color.White.copy(alpha = 0.7f)
    }

    Scaffold(
        containerColor = Color(0xFF0A1628),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مكالمة يمنية", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(statusText, color = statusColor, fontSize = 12.sp)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = YounesEmerald, modifier = Modifier.size(64.dp))
                }

                Text(formatPhoneNumber(number), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)

                operatorInfo?.let { op ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = op.brandColor.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(op.brandColor))
                            Spacer(Modifier.width(8.dp))
                            Text(op.name, color = op.brandColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(statusText, color = statusColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                }

                if (state is PstnState.Started) {
                    Text(formatDuration(callDuration), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "إلغاء كتم" else "كتم",
                        active = isMuted,
                        onClick = {
                            isMuted = !isMuted
                            onMuteToggle(isMuted)
                        }
                    )
                    CallControlButton(
                        icon = Icons.Default.VolumeUp,
                        label = "سماعة",
                        active = isSpeaker,
                        onClick = {
                            isSpeaker = !isSpeaker
                            onSpeakerToggle(isSpeaker)
                        }
                    )
                    CallControlButton(
                        icon = Icons.Default.FiberManualRecord,
                        label = if (isRecording) "إيقاف" else "تسجيل",
                        active = isRecording,
                        onClick = {
                            isRecording = !isRecording
                            onRecordToggle(isRecording, number)
                        }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControlButton(
                        icon = Icons.Default.Dialpad,
                        label = "لوحة",
                        active = showKeypad,
                        onClick = { showKeypad = !showKeypad }
                    )
                    CallControlButton(
                        icon = Icons.Default.Pause,
                        label = "تعليق",
                        onClick = { viewModel?.togglePstnHold() }
                    )
                    CallControlButton(
                        icon = Icons.Default.PersonAdd,
                        label = "إضافة",
                        onClick = {}
                    )
                }

                if (showKeypad) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("*", "0", "#")
                            ).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    row.forEach { digit ->
                                        FilledIconButton(
                                            onClick = { viewModel?.sendPstnDtmf(digit) },
                                            modifier = Modifier.size(56.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                        ) {
                                            Text(digit, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onHangup,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("إنهاء المكالمة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) YounesEmerald else Color.White.copy(alpha = 0.15f),
                contentColor = if (active) Color.White else Color.White.copy(alpha = 0.8f)
            )
        ) {
            Icon(icon, label, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
