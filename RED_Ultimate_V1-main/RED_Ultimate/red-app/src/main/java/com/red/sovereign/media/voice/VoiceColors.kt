package com.red.sovereign.media.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.*
import com.red.sovereign.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 🎨 YOUNES Sovereign — Voice UI Color Palette
 * ألوان احترافية متسقة مع باقي التطبيق
 */
object VoiceColors {
    // Primary action colors
    val RecordingRed = Color(0xFFFF3B5C)        // Active recording
    val RecordingRedGlow = Color(0xFFFF6B85)    // Recording with glow
    val LockGold = Color(0xFFFFB347)            // Locked state
    val LockGoldDark = Color(0xFFC4843D)        // Locked state dark

    // Playback colors
    val PlayedEmerald = Color(0xFF00E6A0)       // Played portion
    val UnplayedNavy = Color(0xFF1A2F4A)        // Unplayed portion
    val PlayheadGold = Color(0xFFE8B84A)        // Playhead

    // Waveform colors
    val WaveformActive = Color(0xFF00E6A0)      // Active recording waveform
    val WaveformIncoming = Color(0xFF35CBE0)    // Incoming message waveform
    val WaveformOutgoing = Color(0xFF00382A)    // Outgoing message waveform
    val WaveformLocked = Color(0xFFFFB347)      // Locked waveform

    // Surface colors
    val BubbleIncoming = Color(0xFF1A2F4A)
    val BubbleIncomingBorder = Color(0xFF2A4A7A)
    val BubbleOutgoing = Color(0xFF00C896)
    val BubbleOutgoingBorder = Color(0xFF00E6A0)

    // State colors
    val CancelRed = Color(0xFFFF6B6B)
    val SuccessEmerald = Color(0xFF10B981)
    val WarningGold = Color(0xFFFFB347)
    val InfoCyan = Color(0xFF35CBE0)
}

/**
 * 🎙️ YOUNES Sovereign — Voice Waveform Renderer
 * رسم احترافي للموجة الصوتية مع animations
 */
@Composable
fun VoiceWaveformCanvas(
    samples: List<Int>,
    color: Color = VoiceColors.WaveformActive,
    modifier: Modifier = Modifier,
    showAnimation: Boolean = false,
    playheadProgress: Float = -1f, // -1 = no playhead, 0..1 = position
    isActive: Boolean = false
) {
    val animatedSamples by animateIntAsState(
        targetValue = samples.size,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "waveform_samples"
    )

    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.85f,
        animationSpec = tween(300),
        label = "waveform_alpha"
    )

    // Continuous animation for active recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_pulse"
    )

    Canvas(modifier = modifier) {
        val displaySamples = samples.takeLast(animatedSamples).ifEmpty {
            List(48) { 8 }
        }
        val step = size.width / displaySamples.size.coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxBarHeight = size.height * (if (isActive) 0.95f * pulse else 0.85f)

        displaySamples.forEachIndexed { index, value ->
            val normalized = (value.coerceIn(4, 100) / 100f)
            val barHeight = (maxBarHeight * normalized).coerceAtLeast(3f)
            val x = step * index + step / 2

            // Determine if this sample is before the playhead
            val isPlayed = playheadProgress >= 0f && index.toFloat() / displaySamples.size <= playheadProgress
            val sampleColor = when {
                playheadProgress >= 0f && isPlayed -> VoiceColors.PlayedEmerald
                else -> color
            }

            val alpha = if (playheadProgress < 0f) activeAlpha else {
                if (isPlayed) 1f else 0.45f
            }

            drawLine(
                color = sampleColor.copy(alpha = alpha),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = (step * 0.55f).coerceIn(2f, 8f),
                cap = StrokeCap.Round
            )
        }

        // Draw playhead if active
        if (playheadProgress >= 0f) {
            val playheadX = size.width * playheadProgress
            drawLine(
                color = VoiceColors.PlayheadGold,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * ⏱️ YOUNES Sovereign — Voice Timer Display
 * عرض احترافي للوقت مع تنسيق ذكي
 */
@Composable
fun VoiceTimerDisplay(
    seconds: Int,
    isActive: Boolean = false,
    isPaused: Boolean = false,
    maxSeconds: Int = 600,
    color: Color = VoiceColors.RecordingRed,
    modifier: Modifier = Modifier
) {
    val text = formatDurationSmart(seconds, maxSeconds)
    val alpha by animateFloatAsState(
        targetValue = if (isActive && !isPaused) 1f else 0.7f,
        animationSpec = tween(400),
        label = "timer_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Pulse dot for active recording
        if (isActive && !isPaused) {
            val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_pulse"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color)
            )
        } else if (isPaused) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = text,
            color = color.copy(alpha = alpha),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )

        // Max duration warning
        if (seconds > maxSeconds * 0.8) {
            val warningColor = if (seconds >= maxSeconds - 10) {
                VoiceColors.CancelRed
            } else {
                VoiceColors.WarningGold
            }
            Text(
                text = "•",
                color = warningColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDurationSmart(seconds: Int, maxSeconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    val maxMinutes = maxSeconds / 60
    val maxSecs = maxSeconds % 60
    return "%d:%02d / %d:%02d".format(minutes, secs, maxMinutes, maxSecs)
}

/**
 * 🔴 YOUNES Sovereign — Pulsing Recording Indicator
 * مؤشر احترافي يدور حول زر التسجيل
 */
@Composable
fun PulsingRecordingIndicator(
    color: Color = VoiceColors.RecordingRed,
    size: Dp = 56.dp,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale2"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha2"
    )

    Box(modifier = modifier.size(size * 2), contentAlignment = Alignment.Center) {
        if (isActive) {
            // Outer rings
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale1)
                    .border(
                        width = 2.dp,
                        color = color.copy(alpha = alpha1),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale2)
                    .border(
                        width = 2.dp,
                        color = color.copy(alpha = alpha2),
                        shape = CircleShape
                    )
            )
        }
        // Core circle
        Box(
            modifier = Modifier
                .size(size)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.9f),
                            color
                        )
                    )
                )
        )
    }
}

/**
 * 🎙️ YOUNES Sovereign — Voice Record Button
 * زر تسجيل احترافي مع press-to-record + drag-to-cancel
 */
@Composable
fun VoiceRecordButton(
    state: VoiceMessageState,
    isLocked: Boolean,
    hasPermission: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLockRequest: () -> Unit,
    onCancel: () -> Unit,
    onUpdateCancelProgress: (Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val isRecording = state is VoiceMessageState.Recording
    val isPreview = state is VoiceMessageState.Preview
    val isSending = state is VoiceMessageState.Sending

    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> if (isLocked) VoiceColors.LockGold else VoiceColors.RecordingRed
            isPreview -> VoiceColors.SuccessEmerald
            isSending -> VoiceColors.WarningGold
            else -> Color(0xFF00C896) // Sovereign Emerald
        },
        animationSpec = tween(300),
        label = "button_color"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_scale"
    )

    val inner = size * 0.87f
    Box(
        modifier = modifier
            .size(size)
            .scale(buttonScale)
            .pointerInput(state, hasPermission) {
                if (!hasPermission) {
                    detectTapGestures(onTap = { onClick() })
                    return@pointerInput
                }
                
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                        
                        if (isRecording) {
                            // If it's already recording (e.g. locked), a tap stops/previews
                            down.consume()
                            val up = awaitPointerEvent().changes.firstOrNull { !it.pressed }
                            if (up != null) {
                                onClick()
                            }
                            continue
                        }
                        
                        if (isPreview || isSending) {
                            down.consume()
                            val up = awaitPointerEvent().changes.firstOrNull { !it.pressed }
                            if (up != null) {
                                onClick()
                            }
                            continue
                        }

                        // Idle state -> Start recording immediately on press down
                        down.consume()
                        onPress()

                        var isCancelled = false
                        var totalDragY = 0f
                        var totalDragX = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull() ?: break
                            if (pos.isConsumed) continue
                            
                            if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Move) {
                                val dragAmount = pos.position - pos.previousPosition
                                totalDragY += dragAmount.y
                                totalDragX += dragAmount.x
                                
                                // Negative Y is upward (lock), Negative X is leftward (cancel)
                                // Standard: Swipe UP to lock, Swipe LEFT to cancel.
                                val lockProgress = (-totalDragY / 120f).coerceIn(0f, 1f)
                                val cancelProgressValue = (-totalDragX / 120f).coerceIn(0f, 1f)
                                
                                // Only update cancel progress if they are dragging left
                                if (cancelProgressValue > 0.1f) {
                                    onUpdateCancelProgress(cancelProgressValue)
                                    if (cancelProgressValue > 0.6f) {
                                        isCancelled = true
                                        onCancel()
                                        break
                                    }
                                } else if (lockProgress > 0.4f && !isLocked) {
                                    onLockRequest()
                                    // Once locked, we break the press-hold cycle
                                    break
                                }
                            } else if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                                if (!isCancelled && !isLocked) {
                                    onRelease()
                                }
                                break
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        PulsingRecordingIndicator(
            color = buttonColor,
            size = inner,
            isActive = isRecording && !isLocked
        )

        // Inner icon
        Box(
            modifier = Modifier
                .size(inner)
                .clip(CircleShape)
                .background(buttonColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isSending -> Icons.Rounded.HourglassEmpty
                    isPreview -> Icons.Rounded.Send
                    isRecording -> if (isLocked) Icons.Rounded.Lock else Icons.Rounded.Stop
                    else -> Icons.Rounded.Mic
                },
                contentDescription = when {
                    isSending -> "جارٍ الإرسال"
                    isPreview -> "إرسال"
                    isRecording -> if (isLocked) "مُقفل" else "إيقاف"
                    else -> "تسجيل"
                },
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * 📤 YOUNES Sovereign — Voice Preview Actions
 * أزرار المعاينة (إرسال / حذف)
 */
@Composable
fun VoicePreviewActions(
    isSending: Boolean,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onDiscard,
            enabled = !isSending,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = VoiceColors.CancelRed
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = VoiceColors.CancelRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "حذف",
                color = VoiceColors.CancelRed,
                fontSize = 14.sp
            )
        }

        Button(
            onClick = onSend,
            enabled = !isSending,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoiceColors.SuccessEmerald
            )
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("إرسال", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/**
 * 🔒 YOUNES Sovereign — Lock Indicator (when recording is locked)
 */
@Composable
fun VoiceLockIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lock_shine")
    val shineX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_x"
    )

    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        VoiceColors.LockGoldDark,
                        VoiceColors.LockGold,
                        VoiceColors.LockGoldDark
                    ),
                    startX = shineX * 200f
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "مُقفل — حرر يديك",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * ↩️ YOUNES Sovereign — Drag-to-Cancel Progress Bar
 * شريط السحب للإلغاء
 */
@Composable
fun VoiceCancelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cancel_progress"
    )

    val color by animateColorAsState(
        targetValue = when {
            progress >= 0.6f -> VoiceColors.CancelRed
            progress >= 0.3f -> VoiceColors.WarningGold
            else -> VoiceColors.InfoCyan
        },
        animationSpec = tween(200),
        label = "cancel_color"
    )

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (progress >= 0.6f) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (progress >= 0.6f) "حرر للإلغاء" else "↩️ اسحب للأعلى للقفل • للأسفل للإلغاء",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = VoiceColors.UnplayedNavy.copy(alpha = 0.5f)
        )
    }
}
