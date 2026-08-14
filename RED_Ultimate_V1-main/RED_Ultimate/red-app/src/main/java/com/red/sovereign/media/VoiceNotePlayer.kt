package com.red.sovereign.media

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.red.sovereign.media.voice.VoiceColors
import com.red.sovereign.media.voice.VoiceWaveformCanvas
import com.red.sovereign.settings.SettingsRuntime
import kotlinx.coroutines.delay

import androidx.compose.material3.MaterialTheme

/**
 * 🎙️ YOUNES Sovereign — Professional Voice Note Player
 *
 * مشغّل رسائل صوتية احترافي بالكامل:
 *  - ExoPlayer مع AudioAttributes SPEECH
 *  - شريط تقدم تفاعلي (drag-to-seek)
 *  - Waveform مع playhead ملون
 *  - سرعات تشغيل متعددة (0.5× إلى 2×)
 *  - Play/Pause بأزرار كبيرة وواضحة
 *  - Auto-cleanup للموارد
 */
@Composable
fun VoiceNotePlayer(
    uri: Uri,
    waveform: List<Int> = emptyList(),
    durationSeconds: Int = 0,
    isOutgoing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferredSpeed = SettingsRuntime.current.defaultPlaybackSpeed

    var isPlaying by remember(uri) { mutableStateOf(false) }
    var currentPositionMs by remember(uri) { mutableStateOf(0L) }
    var totalDurationMs by remember(uri) { mutableStateOf(durationSeconds * 1000L) }
    var currentSpeed by remember(uri) { mutableStateOf(preferredSpeed) }
    var showSpeedMenu by remember(uri) { mutableStateOf(false) }

    val bubbleBorderColor = if (isOutgoing) VoiceColors.BubbleOutgoingBorder else VoiceColors.BubbleIncomingBorder
    val waveformColor = if (isOutgoing) VoiceColors.WaveformOutgoing else VoiceColors.WaveformIncoming
    val onColor = if (isOutgoing) Color(0xFF001B14) else Color.White
    val surfaceColor = if (isOutgoing) VoiceColors.BubbleOutgoing else VoiceColors.BubbleIncoming
    val primaryColor = waveformColor

    val player = remember(uri) {
        val exo = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setMediaItem(MediaItem.fromUri(uri))
            setPlaybackSpeed(preferredSpeed)
        }
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exo.duration > 0) {
                    totalDurationMs = exo.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    exo.seekTo(0)
                    exo.pause()
                    isPlaying = false
                    currentPositionMs = 0L
                }
            }
        })
        exo.prepare()
        exo
    }

    // Update position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition
            totalDurationMs = if (player.duration > 0) player.duration else totalDurationMs
            delay(100)
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(150),
        label = "playback_progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isOutgoing) 20.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 20.dp
            ))
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
        // Top row: Play button + info + speed
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Large play/pause button
            PlayPauseCircle(
                isPlaying = isPlaying,
                isOutgoing = isOutgoing,
                onClick = {
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "رسالة صوتية",
                    color = onColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDurationMs(currentPositionMs),
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " / ${formatDurationMs(totalDurationMs)}",
                        color = onColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // Speed selector
            Box {
                FilledTonalIconButton(
                    onClick = { showSpeedMenu = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (currentSpeed != 1f) {
                            VoiceColors.PlayedEmerald.copy(alpha = 0.2f)
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Text(
                        text = "${currentSpeed}×",
                        color = onColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}×") },
                            onClick = {
                                currentSpeed = speed
                                player.setPlaybackSpeed(speed)
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

        Spacer(Modifier.height(10.dp))

        // Waveform with seek capability
        if (waveform.isNotEmpty()) {
            VoiceWaveformCanvas(
                samples = waveform,
                color = VoiceColors.WaveformIncoming,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                val newPosition = (newProgress * totalDurationMs).toLong()
                                player.seekTo(newPosition)
                                currentPositionMs = newPosition
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                val newPosition = (newProgress * totalDurationMs).toLong()
                                player.seekTo(newPosition)
                                currentPositionMs = newPosition
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newProgress = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val newPosition = (newProgress * totalDurationMs).toLong()
                            player.seekTo(newPosition)
                            currentPositionMs = newPosition
                        }
                    },
                playheadProgress = animatedProgress,
                isActive = isPlaying
            )
        } else {
            // Fallback progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = primaryColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun PlayPauseCircle(
    isPlaying: Boolean,
    isOutgoing: Boolean = false,
    onClick: () -> Unit
) {
    val gradientColors = if (isOutgoing) listOf(
        Color(0xFF001B14).copy(alpha = 0.2f),
        Color(0xFF001B14).copy(alpha = 0.1f)
    ) else listOf(
        Color.White.copy(alpha = 0.2f),
        Color.White.copy(alpha = 0.1f)
    )
    val iconColor = if (isOutgoing) Color(0xFF001B14) else Color.White
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(colors = gradientColors))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val minutes = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(minutes, secs)
}
