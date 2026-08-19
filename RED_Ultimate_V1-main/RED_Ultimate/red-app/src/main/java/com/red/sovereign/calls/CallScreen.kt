package com.red.sovereign.calls

import android.content.Context
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive PSTN Call Screen
 * Shows clear connection stages: WebSocket SIP → TURN → Asterisk → GSM
 */
@Composable
fun Material3ExpressivePstnCallScreen(
    status: PstnCallStatus,
    metrics: CallMetrics = CallMetrics(),
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onKeypadToggle: () -> Unit = {},
    onHoldToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean) -> Unit = {},
    onVideoToggle: (Boolean) -> Unit = {},
    onHangup: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isHeld by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var dialedDigits by remember { mutableStateOf("") }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var callStartTime by remember { mutableLongStateOf(0L) }
    var connectionStage by remember { mutableStateOf(ConnectionStage.BRIDGING) }
    var animatedProgress by remember { mutableStateOf(0f) }
    var pulseAnimation by remember { mutableStateOf(1f) }

    // Connection stages with clear labels
    enum class ConnectionStage(val label: String, val description: String, val icon: ImageVector, val color: Color) {
        BRIDGING("Connecting to SIP Server", "Establishing WebSocket SIP connection to Asterisk", Icons.Filled.CloudQueue, AqyalGold),
        REGISTERING("Registering SIP Account", "Authenticating with SIP credentials", Icons.Filled.VerifiedUser, AqyalGold),
        INVITING("Placing Call", "Sending SIP INVITE via Asterisk", Icons.Filled.CallMade, AqyalGold),
        TURN_CONNECTING("Connecting TURN Server", "Establishing media relay via TURN/STUN", Icons.Filled.Router, YounesEmerald),
        RINGING("Ringing", "Call is ringing on remote side", Icons.Filled.PhoneInTalk, AqyalGold),
        CONNECTED("Connected", "Media path established - call active", Icons.Filled.Call, YounesEmerald)
    }

    // Determine current stage from status
    val currentStage = when (status) {
        PstnCallStatus.REGISTERING -> ConnectionStage.REGISTERING
        PstnCallStatus.BRIDGING -> ConnectionStage.BRIDGING
        PstnCallStatus.INVITING -> ConnectionStage.INVITING
        PstnCallStatus.RINGING -> ConnectionStage.RINGING
        PstnCallStatus.ACTIVE -> ConnectionStage.CONNECTED
        else -> ConnectionStage.BRIDGING
    }

    // Pulse animation for connecting states
    LaunchedEffect(connectionStage) {
        if (connectionStage != ConnectionStage.CONNECTED) {
            while (true) {
                delay(1000)
                pulseAnimation = if (pulseAnimation == 1f) 0.7f else 1f
            }
        }
    }

    // Call timer
    LaunchedEffect(status) {
        when (status) {
            PstnCallStatus.ACTIVE -> {
                if (callStartTime == 0L) callStartTime = System.currentTimeMillis()
            }
            PstnCallStatus.IDLE, PstnCallStatus.ENDED -> {
                callStartTime = 0L
                elapsedMs = 0L
            }
        }
    }

    LaunchedEffect(status, callStartTime) {
        if (status == PstnCallStatus.ACTIVE && callStartTime > 0) {
            while (true) {
                elapsedMs = System.currentTimeMillis() - callStartTime
                delay(1000)
            }
        }
    }

    LaunchedEffect(status) {
        if (status == PstnCallStatus.IDLE) onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SovereignColors.ObsidianDeep
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar with connection stage indicator
            ConnectionStageHeader(stage = currentStage, pulseAnimation = pulseAnimation)

            Spacer(modifier = Modifier.height(16.dp))

            // Main content - Avatar + Status + Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar with pulse animation
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { scaleX = pulseAnimation; scaleY = pulseAnimation }
                        .clip(CircleShape)
                        .background(currentStage.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentStage != ConnectionStage.CONNECTED) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(60.dp),
                            color = currentStage.color,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = currentStage.icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = currentStage.color
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stage label
                Text(
                    text = currentStage.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Stage description
                Text(
                    text = currentStage.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Call timer when active
                if (status == PstnCallStatus.ACTIVE) {
                    Text(
                        text = formatDuration(elapsedMs),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Call quality indicator when active
            if (status == PstnCallStatus.ACTIVE) {
                CallQualityCard(metrics = metrics)
            }

            // Daily limit progress
            if (metrics.dailyLimit > 0) {
                DailyLimitCard(metrics = metrics)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Control buttons
            if (status != PstnCallStatus.IDLE && status != PstnCallStatus.ENDED) {
                CallControlPanel(
                    isMuted = isMuted,
                    isSpeaker = isSpeaker,
                    isHeld = isHeld,
                    isRecording = isRecording,
                    isVideoEnabled = isVideoEnabled,
                    showKeypad = showKeypad,
                    dialedDigits = dialedDigits,
                    status = status,
                    onMuteToggle = { isMuted = it; onMuteToggle(it) },
                    onSpeakerToggle = { isSpeaker = it; onSpeakerToggle(it) },
                    onHoldToggle = { isHeld = it; onHoldToggle(it) },
                    onRecordToggle = { isRecording = it; onRecordToggle(it) },
                    onVideoToggle = { isVideoEnabled = it; onVideoToggle(it) },
                    onKeypadToggle = { showKeypad = !showKeypad },
                    onDigitPress = { dialedDigits += it },
                    onDigitDelete = { if (dialedDigits.isNotEmpty()) dialedDigits = dialedDigits.dropLast(1) },
                    onHangup = onHangup
                )
            } else {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun ConnectionStageHeader(
    stage: Material3ExpressivePstnCallScreen.ConnectionStage,
    pulseAnimation: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = stage.color.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer { this.alpha = pulseAnimation },
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = stage.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = stage.color
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = stage.color
                )
            }
        }
    }
}

@Composable
private fun CallQualityCard(metrics: CallMetrics) {
    val (quality, qualityColor) = when {
        metrics.jitterMs < 30 && metrics.packetLossPercent < 1f && metrics.roundTripMs < 150 ->
            "Excellent" to YounesEmerald
        metrics.jitterMs < 50 && metrics.packetLossPercent < 3f && metrics.roundTripMs < 300 ->
            "Good" to AqyalGold
        metrics.jitterMs < 80 && metrics.packetLossPercent < 5f ->
            "Fair" to Color(0xFFFF9800)
        else ->
            "Poor" to Color(0xFFF44336)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = SovereignColors.SurfaceCard
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SignalCellular4Bar,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = qualityColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Call Quality: $quality",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = qualityColor
                )
                Text(
                    text = "Jitter ${"%.0f".format(metrics.jitterMs)}ms  •  Loss ${"%.1f".format(metrics.packetLossPercent)}%  •  RTT ${"%.0f".format(metrics.roundTripMs)}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyLimitCard(metrics: CallMetrics) {
    val progress = (metrics.usedToday.toFloat() / metrics.dailyLimit).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = SovereignColors.SurfaceCard
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Daily PSTN Limit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${metrics.usedToday} / ${metrics.dailyLimit}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AqyalGold,
                trackColor = SovereignColors.ObsidianDeep.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun CallControlPanel(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isHeld: Boolean,
    isRecording: Boolean,
    isVideoEnabled: Boolean,
    showKeypad: Boolean,
    dialedDigits: String,
    status: PstnCallStatus,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onHoldToggle: (Boolean) -> Unit,
    onRecordToggle: (Boolean) -> Unit,
    onVideoToggle: (Boolean) -> Unit,
    onKeypadToggle: () -> Unit,
    onDigitPress: (String) -> Unit,
    onDigitDelete: () -> Unit,
    onHangup: () -> Unit
) {
    // Keypad overlay
    if (showKeypad) {
        KeypadOverlay(
            dialedDigits = dialedDigits,
            onDigitPress = onDigitPress,
            onDigitDelete = onDigitDelete,
            onClose = { /* handled by parent */ }
        )
    }

    // Main control buttons
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Primary row: Mute, Speaker, Hold, Record
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveCallButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                activeColor = Color(0xFFF44336),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onMuteToggle(!isMuted) }
            )

            ExpressiveCallButton(
                icon = if (isSpeaker) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                label = if (isSpeaker) "Earpiece" else "Speaker",
                isActive = isSpeaker,
                activeColor = AqyalGold,
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onSpeakerToggle(!isSpeaker) }
            )

            ExpressiveCallButton(
                icon = Icons.Filled.Pause,
                label = if (isHeld) "Resume" else "Hold",
                isActive = isHeld,
                activeColor = Color(0xFFFF9800),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onHoldToggle(!isHeld) }
            )

            ExpressiveCallButton(
                icon = if (isRecording) Icons.Filled.PlayArrow else Icons.Filled.Mic,
                label = if (isRecording) "Stop Rec" else "Record",
                isActive = isRecording,
                activeColor = Color(0xFFF44336),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onRecordToggle(!isRecording) }
            )
        )

        // Secondary row: Keypad, Video, Hangup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveCallButton(
                icon = Icons.Filled.Keyboard,
                label = if (showKeypad) "Hide Keypad" else "Keypad",
                isActive = showKeypad,
                activeColor = AqyalGold,
                enabled = true,
                onClick = onKeypadToggle
            )

            ExpressiveCallButton(
                icon = if (isVideoEnabled) Icons.Filled.VideocamOff else Icons.Filled.VideoCall,
                label = if (isVideoEnabled) "Video Off" else "Video",
                isActive = isVideoEnabled,
                activeColor = YounesEmerald,
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { /* video toggle */ }
            )

            // Hangup button - prominent
            ExpressiveCallButton(
                icon = Icons.Filled.CallEnd,
                label = "End Call",
                isActive = true,
                activeColor = Color(0xFFF44336),
                isDestructive = true,
                enabled = true,
                onClick = onHangup
            )
        }
    }
}

@Composable
private fun ExpressiveCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bgColor = if (isDestructive) {
        if (enabled) Color(0xFFF44336) else Color(0xFFF44336).copy(alpha = 0.4f)
    } else if (isActive) {
        if (enabled) activeColor else activeColor.copy(alpha = 0.4f)
    } else {
        if (enabled) SovereignColors.SurfaceCard else SovereignColors.SurfaceCard.copy(alpha = 0.4f)
    }

    val tintColor = if (isDestructive || isActive) Color.White else MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bgColor),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tintColor
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun KeypadOverlay(
    dialedDigits: String,
    onDigitPress: (String) -> Unit,
    onDigitDelete: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display dialed digits
            Text(
                text = if (dialedDigits.isEmpty()) "Enter DTMF" else dialedDigits,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#"),
                    listOf("⌫")
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { key ->
                            val isDelete = key == "⌫"
                            Text(
                                text = if (isDelete) "" else key,
                                modifier = Modifier
                                    .size(if (isDelete) 72.dp else 64.dp)
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(
                                        if (isDelete) Color(0xFFF44336).copy(alpha = 0.8f)
                                        else Color.White.copy(alpha = 0.15f)
                                    )
                                    .clickable {
                                        if (isDelete) onDigitDelete() else onDigitPress(key)
                                    }
                                    .padding(16.dp),
                                fontSize = if (isDelete) 20.sp else 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Keypad",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }.padding(horizontal = 16.dp, vertical = 24.dp)
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}