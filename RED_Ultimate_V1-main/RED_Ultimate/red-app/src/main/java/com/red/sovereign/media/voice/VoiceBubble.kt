package com.red.sovereign.media.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.VoiceManifest
import com.red.sovereign.media.voice.VoiceColors

/**
 * 💬 YOUNES Sovereign — Voice Message Bubble
 * فقاعة رسالة صوتية احترافية بالكامل
 *
 * الميزات:
 *  - تشغيل/إيقاف بنبضة
 *  - waveform interactive (الضغط للتنقل)
 *  - شريط تقدم ملون
 *  - سرعة تشغيل قابلة للتغيير
 *  - تنزيل/تشفير indicator
 */
@Composable
fun VoiceMessageBubble(
    manifest: VoiceManifest,
    isOutgoing: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDownload: () -> Unit,
    onWaveformTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playheadProgress by remember { mutableStateOf(0f) }
    var currentSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val bubbleColor = if (isOutgoing) VoiceColors.BubbleOutgoing else VoiceColors.BubbleIncoming
    val bubbleBorderColor = if (isOutgoing) VoiceColors.BubbleOutgoingBorder else VoiceColors.BubbleIncomingBorder
    val waveformColor = if (isOutgoing) VoiceColors.WaveformOutgoing else VoiceColors.WaveformIncoming
    val onColor = if (isOutgoing) Color(0xFF001B14) else Color.White

    Box(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isOutgoing) 20.dp else 4.dp,
                    bottomEnd = if (isOutgoing) 4.dp else 20.dp
                )
            )
            .background(
                if (isOutgoing) {
                    Brush.linearGradient(
                        colors = listOf(
                            VoiceColors.BubbleOutgoing,
                            VoiceColors.BubbleOutgoing.copy(alpha = 0.85f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            VoiceColors.BubbleIncoming,
                            VoiceColors.BubbleIncoming.copy(alpha = 0.95f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = bubbleBorderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play/Pause button
                PlayPauseButton(
                    isPlaying = isPlaying,
                    isOutgoing = isOutgoing,
                    enabled = isDownloaded,
                    onClick = {
                        if (isDownloaded) {
                            isPlaying = !isPlaying
                            onPlayPause()
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "رسالة صوتية",
                        color = onColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${formatDuration(manifest.durationSeconds)} · ${formatBytes(manifest.size)}",
                        color = onColor.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                // Speed control
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${currentSpeed}×",
                            color = onColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}×") },
                                onClick = {
                                    currentSpeed = speed
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                },
                                leadingIcon = if (currentSpeed == speed) {
                                    { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Waveform
            VoiceWaveformCanvas(
                samples = manifest.waveform,
                color = waveformColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clickable(enabled = isDownloaded) {
                        // Toggle play on tap
                        isPlaying = !isPlaying
                        onPlayPause()
                    },
                playheadProgress = if (isPlaying) playheadProgress else -1f,
                isActive = false
            )

            // Bottom row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "تم التنزيل",
                        tint = onColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "مشفّر ومحفوظ",
                        color = onColor.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                } else {
                    FilledTonalButton(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isOutgoing) {
                                Color.Black.copy(alpha = 0.15f)
                            } else {
                                VoiceColors.PlayedEmerald.copy(alpha = 0.2f)
                            }
                        )
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                color = onColor,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "جارٍ التنزيل…",
                                color = onColor,
                                fontSize = 12.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "تنزيل وتشغيل",
                                color = onColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isOutgoing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (isOutgoing) VoiceColors.WaveformOutgoing else VoiceColors.PlayedEmerald
    val iconColor = Color.White

    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0.8f))
                )
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
