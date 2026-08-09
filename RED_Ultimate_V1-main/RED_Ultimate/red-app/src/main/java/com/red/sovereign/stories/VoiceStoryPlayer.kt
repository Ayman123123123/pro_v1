package com.red.sovereign.stories

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.red.sovereign.ui.theme.AqyalCyanGlow

/**
 * ════════════════════════════════════════════════════════════════════════
 *  VoiceStoryPlayer — مشغل قصة صوتية
 *  - ExoPlayer لتشغيل audio
 *  - شريط تقدم متحرك + waveform + play/pause
 *  - تنظيف الموارد عند الـ dispose
 *  - يعيد onFinished callback عند انتهاء التشغيل
 * ════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStoryPlayer(
    mediaUrl: String,
    durationMs: Long = 0L,
    waveform: List<Int> = emptyList(),
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val player = remember(mediaUrl) { buildPlayer(context, mediaUrl, onFinished) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentMs by remember { mutableStateOf(0L) }
    var totalMs by remember { mutableStateOf(durationMs) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)

        // Progress polling
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        val job = coroutineScope.launch {
            while (isActive) {
                currentMs = player.currentPosition.coerceAtLeast(0L)
                if (totalMs <= 0) totalMs = player.duration.coerceAtLeast(0L)
                delay(50)
            }
        }

        onDispose {
            job.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Waveform visualization
        if (waveform.isNotEmpty()) {
            val progress = if (totalMs > 0) (currentMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                val step = size.width / waveform.size.coerceAtLeast(1)
                waveform.forEachIndexed { i, v ->
                    val h = size.height * (v.coerceIn(4, 100) / 100f)
                    val x = i * step + step / 2
                    val played = i.toFloat() / waveform.size <= progress
                    drawLine(
                        color = if (played) AqyalCyanGlow else Color.White.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                        end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2),
                        strokeWidth = 3f
                    )
                }
            }
        }

        // Progress bar
        if (totalMs > 0) {
            LinearProgressIndicator(
                progress = { (currentMs.toFloat() / totalMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = AqyalCyanGlow,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }

        // Time labels + play/pause
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMs(currentMs),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            // Big play/pause button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AqyalCyanGlow)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                formatMs(totalMs),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Quality badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.GraphicEq,
                null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "صوت نقي • E2E مشفر",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

private fun buildPlayer(context: Context, mediaUrl: String, onFinished: () -> Unit): ExoPlayer {
    return ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(mediaUrl))
        prepare()
        playWhenReady = true
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onFinished()
            }
        })
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}
