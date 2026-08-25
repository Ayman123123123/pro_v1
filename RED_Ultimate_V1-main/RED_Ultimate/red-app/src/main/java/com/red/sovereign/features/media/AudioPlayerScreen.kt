package com.red.sovereign.features.media

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

@Composable
fun AudioPlayerScreen(
    file: File,
    fileName: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpenWith: ((File) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showSave by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    val waveform = remember(file.absolutePath) { buildStableWaveform(file, 64) }

    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(file.absolutePath.toUri()))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) durationMs = player.duration
                if (state == Player.STATE_ENDED) { positionMs = 0L }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) positionMs = player.currentPosition
            delay(200)
        }
    }

    val playedColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
            Text(fileName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            onSave?.let { IconButton(onClick = it) { Icon(Icons.Default.Download, "حفظ") } }
            onOpenWith?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Default.OpenInNew, "فتح بـ") } }
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Default.Close, "حذف", tint = Color(0xFFD32F2F)) } }
        }

        Spacer(Modifier.height(48.dp))

        WaveformView(
            amplitudes = waveform,
            progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            playedColor = playedColor,
            idleColor = idleColor,
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatMediaTime(positionMs), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { p ->
                    val target = (p * durationMs).toLong()
                    player.seekTo(target); positionMs = target
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(formatMediaTime(durationMs), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                Icon(Icons.Default.Replay10, null, Modifier.size(30.dp))
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp)) {
                IconButton(onClick = { player.playWhenReady = !player.playWhenReady }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            IconButton(onClick = { if (durationMs > 0) player.seekTo(min(durationMs, player.currentPosition + 10_000)) }) {
                Icon(Icons.Default.Forward10, null, Modifier.size(30.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (speed == s) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { speed = s; player.setPlaybackSpeed(s) }
                ) {
                    Text("${s}x", fontSize = 11.sp, fontWeight = if (speed == s) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }

        Text(
            "تشغيل محلي آمن — الملف مفكوك التشفير في ذاكرة مؤقتة خاصة",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp)
        )
    }

    if (showSave) {
        SaveDestinationDialog(
            fileName = fileName,
            onDismiss = { showSave = false },
            onSave = { location ->
                showSave = false
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = createSaveUri(context, fileName, location) ?: return@launch
                    saveFileToDestination(file, uri, context) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun WaveformView(
    amplitudes: List<Float>,
    progress: Float,
    playedColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val count = amplitudes.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val amp = amplitudes[i].coerceIn(0.08f, 1f)
            val h = size.height * amp * 0.85f
            val x = i * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = if (i.toFloat() / count <= progress) playedColor else idleColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * موجة مستقرة لكل ملف: تُشتق من SHA-256 للملف نفسه فتبدو طبيعية وتتكرر بدقة،
 * بدون كلفة فك ترميز PCM الثقيلة على خيط الواجهة.
 */
fun buildStableWaveform(file: File, samples: Int): List<Float> = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    val hash = digest.digest()
    List(samples) { i ->
        val b = hash[i % hash.size].toInt() and 0xFF
        0.15f + (b / 255f) * 0.85f
    }
}.getOrDefault(List(samples) { 0.35f })
