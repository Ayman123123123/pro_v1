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
 * 📞 YOUNES PSTN Call Screen V2 — Real-time WebSocket
 * حالة المكالمة حية من DinstarWebSocketBridge
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
    var isOnHold by remember { mutableStateOf(false) }
    var liveSignal by remember { mutableIntStateOf(optimalPort?.signalPercent ?: 0) }

    // ─── استماع لأحداث WebSocket ───
    LaunchedEffect(Unit) {
        viewModel.dinstarEvents.collect { event ->
            when (event) {
                is DinstarEvent.CallStateChanged -> {
                    when {
                        event.newState == "RINGING" && event.port == (optimalPort?.index ?: -1) ->
                            callState = "RINGING"
                        event.newState == "ACTIVE" && event.oldState != "ACTIVE" && event.port == (optimalPort?.index ?: -1) ->
                            callState = "ACTIVE"
                        event.newState == "IDLE" && event.oldState == "ACTIVE" && event.port == (optimalPort?.index ?: -1) -> {
                            callState = "ENDED"
                            delay(2000)
                            onEnd()
                        }
                    }
                }
                is DinstarEvent.CircuitBreakerOpen -> { callState = "ENDED" }
                else -> {}
            }
        }
    }

    LaunchedEffect(gatewayStatus.ports) {
        optimalPort?.let { port ->
            gatewayStatus.ports.find { it.index == port.index }?.let { livePort ->
                liveSignal = livePort.signalPercent
            }
        }
    }

    LaunchedEffect(callState) {
        if (callState == "ACTIVE") {
            while (true) { delay(1000); callDuration++ }
        }
    }

    val wsConnected by viewModel.connectionState.collectAsStateWithLifecycle()
    LaunchedEffect(wsConnected) {
        if (wsConnected != BackendConnectionState.CONNECTED) {
            delay(2000)
            if (callState == "CONNECTING") callState = "RINGING"
            delay(3000)
            if (callState == "RINGING") callState = "ACTIVE"
        }
    }

    val operator = YemenOperator.fromNumber(phoneNumber)
    val operatorColor = Color(operator.colorHex)

    val pulseInfinite = rememberInfiniteTransition()
    val pulseAlpha by pulseInfinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "CallPulse"
    )
    val ringPulse by pulseInfinite.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "RingPulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(SovereignColors.Obsidian, operatorColor.copy(alpha = 0.05f), SovereignColors.Obsidian))
    )) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))

            // شارة DINSTAR + WebSocket
            Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.DinstarGold.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, SovereignColors.DinstarGold.copy(alpha = 0.3f))) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SimCard, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("خطي اليمني — DINSTAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SovereignColors.DinstarGold)
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.size(6.dp).background(
                        when (wsConnected) { BackendConnectionState.CONNECTED -> SovereignColors.Success; BackendConnectionState.CONNECTING -> SovereignColors.Warning; else -> SovereignColors.Danger }, CircleShape
                    ))
                    Spacer(Modifier.width(4.dp))
                    Text(when (wsConnected) { BackendConnectionState.CONNECTED -> "حي"; BackendConnectionState.CONNECTING -> "..."; else -> "غير متصل" }, fontSize = 10.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(40.dp))

            val currentPulse = if (callState == "RINGING") ringPulse else pulseAlpha
            Box(modifier = Modifier.size(128.dp).clip(CircleShape).background(operatorColor.copy(alpha = currentPulse * 0.15f)), contentAlignment = Alignment.Center) {
                Icon(when (callState) { "RINGING" -> Icons.Rounded.PhoneInTalk; "ACTIVE" -> Icons.Rounded.PhoneInTalk; "ENDED" -> Icons.Rounded.CallEnd; else -> Icons.Rounded.PhoneForwarded }, null, tint = operatorColor.copy(alpha = currentPulse), modifier = Modifier.size(56.dp))
            }

            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = operatorColor.copy(alpha = 0.12f)) {
                Text(operator.arabicName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = operatorColor, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(phoneNumber, fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))

            val stateText = when (callState) { "CONNECTING" -> "جاري الاتصال..."; "RINGING" -> "يرن..."; "ACTIVE" -> formatDuration(callDuration); "ENDED" -> "انتهت المكالمة"; else -> callState }
            val stateColor = when (callState) { "CONNECTING" -> SovereignColors.Warning; "RINGING" -> SovereignColors.Cyan; "ACTIVE" -> SovereignColors.Success; "ENDED" -> Color.Gray; else -> Color.Gray }
            Text(stateText, fontSize = 18.sp, color = stateColor, fontWeight = FontWeight.Medium)

            if (callState == "ACTIVE") {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.width(120.dp).height(2.dp).clip(RoundedCornerShape(1.dp)), color = SovereignColors.Success, trackColor = Color.Gray.copy(alpha = 0.2f))
            }

            if (optimalPort != null) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = SovereignColors.SurfaceNavy) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SimCard, null, tint = operatorColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp)); Text("منفذ ${optimalPort.index}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(16.dp)); Icon(Icons.Rounded.SignalCellularAlt, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        val signalColor = when { liveSignal >= 60 -> SovereignColors.Success; liveSignal >= 30 -> SovereignColors.Warning; else -> SovereignColors.Danger }
                        Text("$liveSignal%", fontSize = 12.sp, color = signalColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp)); Text(optimalPort.operatorName, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            if (isOnHold) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = SovereignColors.Warning.copy(alpha = 0.15f)) {
                    Text("⏸ مكالمة معلقة", fontSize = 13.sp, color = SovereignColors.Warning, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (callState == "ACTIVE") {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
                    PstnCallControlButton(icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, label = if (isMuted) "مكتوم" else "كتم", onClick = { isMuted = !isMuted }, tint = if (isMuted) SovereignColors.Danger else Color.White)
                    PstnCallControlButton(icon = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown, label = "مكبر", onClick = { isSpeakerOn = !isSpeakerOn }, tint = if (isSpeakerOn) SovereignColors.Cyan else Color.White)
                    PstnCallControlButton(icon = if (isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, label = if (isOnHold) "استئناف" else "تعليق", onClick = { isOnHold = !isOnHold }, tint = if (isOnHold) SovereignColors.Warning else Color.White)
                    PstnCallControlButton(icon = Icons.Rounded.PhoneForwarded, label = "تحويل", onClick = { }, tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            FloatingActionButton(onClick = { callState = "ENDED"; onEnd() }, containerColor = SovereignColors.Danger, shape = CircleShape, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun PstnCallControlButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(2.dp)); Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
