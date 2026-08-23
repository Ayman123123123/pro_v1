package com.red.sovereign.calls

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.calls.YemeniOperatorDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive Incoming PSTN Call Screen
 * Full-screen incoming call with swipe actions, haptic feedback, and clear caller info
 */
@Composable
fun Material3ExpressiveIncomingPstnCallScreen(
    callerNumber: String,
    callerName: String? = null,
    callId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDeclineWithMessage: (String) -> Unit = {},
    onAcceptVideo: () -> Unit = {},
    context: Context
) {
    var pulseAnimation by remember { mutableStateOf(1f) }
    var swipeProgress by remember { mutableStateOf(0f) }
    var showMessageOptions by remember { mutableStateOf(false) }
    var ringAnimation by remember { mutableStateOf(0f) }

    // Caller info
    val operatorInfo = YemeniOperatorDetector.getOperatorInfo(callerNumber)
    val displayName = callerName?.ifBlank { null } ?: operatorInfo?.name ?: "Unknown Caller"
    val operatorColor = operatorInfo?.brandColor ?: AqyalGold

    // Start ring animation and vibration
    LaunchedEffect(Unit) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        while (true) {
            // Pulse animation for ring
            ringAnimation = 1f
            delay(200)
            ringAnimation = 0f
            delay(200)
            ringAnimation = 1f
            delay(200)
            ringAnimation = 0f
            delay(200)

            // Vibrate pattern
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 500, 500), -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 500, 500), -1)
            }

            delay(1500)
        }
    }

    // Pulse animation for avatar
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            pulseAnimation = if (pulseAnimation == 1f) 0.9f else 1f
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SovereignColors.ObsidianDeep
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background caller avatar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = pulseAnimation; scaleY = pulseAnimation }
            ) {
                // Caller avatar
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer { scaleX = 1f + (0.05f * pulseAnimation); scaleY = 1f + (0.05f * pulseAnimation) }
                        .clip(CircleShape)
                        .background(operatorColor.copy(alpha = 0.15f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallReceived,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = operatorColor
                    )
                }
            }

            // Incoming call label with pulse
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .graphicsLayer { this.alpha = 0.7f + (0.3f * ringAnimation) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Incoming PSTN Call",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = AqyalGold
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = callerNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Operator badge
            operatorInfo?.let { op ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 220.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = op.brandColor.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SignalCellularAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = op.brandColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${op.name} • GSM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = op.brandColor
                            )
                        }
                    }
                }
            }

            // Swipe actions area - bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Instructions
                Text(
                    text = "Swipe up to answer  •  Swipe down to decline",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline button (red)
                    SwipeActionButton(
                        icon = Icons.Filled.CallEnd,
                        label = "Decline",
                        color = Color(0xFFF44336),
                        onClick = onReject
                    )

                    // Accept button (green) - prominent
                    SwipeActionButton(
                        icon = Icons.Filled.Call,
                        label = "Accept",
                        color = YounesEmerald,
                        isPrimary = true,
                        onClick = onAccept
                    )

                    // Accept with video
                    SwipeActionButton(
                        icon = Icons.Filled.VideoCall,
                        label = "Video",
                        color = AqyalGold,
                        onClick = onAcceptVideo
                    )
                }
            }

            // Message options
            if (showMessageOptions) {
                DeclineMessageSheet(
                    onSend = { msg ->
                        onDeclineWithMessage(msg)
                        showMessageOptions = false
                    },
                    onDismiss = { showMessageOptions = false }
                )
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isPrimary) color else color.copy(alpha = 0.15f)
    val tintColor = if (isPrimary) Color.White else color
    val size = if (isPrimary) 84.dp else 72.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isPrimary) 84.dp else 72.dp)
                .clip(CircleShape)
                .background(if (isPrimary) color else color.copy(alpha = 0.15f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (isPrimary) 32.dp else 28.dp),
                tint = if (isPrimary) Color.White else color
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal,
            color = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DeclineMessageSheet(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val messages = listOf(
        "I'll call you back",
        "I'm in a meeting",
        "Can't talk right now",
        "Send me a text instead",
        "I'm driving"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = SovereignColors.SurfaceCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Reply with Message",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        messages.forEach { msg ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = SovereignColors.ObsidianDeep
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clickable { onSend(msg) }
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}