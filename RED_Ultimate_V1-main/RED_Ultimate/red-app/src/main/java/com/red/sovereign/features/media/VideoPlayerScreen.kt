package com.red.sovereign.features.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun VideoPlayerScreen(
    file: java.io.File,
    fileName: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpenWith: ((java.io.File) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var showSave by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
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
            delay(300)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
                    Text(fileName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    onSave?.let { IconButton(onClick = it) { Icon(Icons.Default.Download, "حفظ", tint = Color.White) } }
                    onOpenWith?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Default.OpenInNew, "فتح بـ", tint = Color.White) } }
                    IconButton(onClick = { enterPip(context, player) }) { Icon(Icons.Default.PictureInPictureAlt, "صورة داخل صورة", tint = Color.White) }
                    onDelete?.let { IconButton(onClick = it) { Icon(Icons.Default.Close, "حذف", tint = Color(0xFFFF6E6E)) } }
                }

                Spacer(Modifier.weight(1f))

                Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMediaTime(positionMs), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Slider(
                                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                                onValueChange = { p ->
                                    val target = (p * durationMs).toLong()
                                    player.seekTo(target); positionMs = target
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF00E676),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(formatMediaTime(durationMs), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                                    Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(56.dp)) {
                                    IconButton(onClick = { player.playWhenReady = !player.playWhenReady }) {
                                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }
                                IconButton(onClick = { if (durationMs > 0) player.seekTo(min(durationMs, player.currentPosition + 10_000)) }) {
                                    Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf(0.5f, 1f, 1.5f, 2f).forEach { s ->
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (speed == s) Color(0xFF00E676).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable { speed = s; player.setPlaybackSpeed(s) }
                                    ) {
                                        Text(
                                            "${s}x",
                                            color = if (speed == s) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSave) {
        SaveDestinationDialog(
            fileName = fileName,
            onDismiss = { showSave = false },
            onSave = { location ->
                showSave = false
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val uri = createSaveUri(context, fileName, location) ?: return@launch
                    saveFileToDestination(file, uri, context) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

private fun enterPip(context: Context, player: ExoPlayer) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = context as? Activity ?: return
    runCatching {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }.onFailure {
        Toast.makeText(context, "وضع الصورة-داخل-الصورة غير متاح", Toast.LENGTH_SHORT).show()
    }
}
