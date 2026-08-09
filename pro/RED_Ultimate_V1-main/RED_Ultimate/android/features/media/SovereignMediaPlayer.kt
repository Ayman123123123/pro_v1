package com.red.sovereign.features.media

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.red.core.theme.SovereignColors

/**
 * 🎵 YOUNES Sovereign Media Player
 * مشغل وسائط متقدم: صوت + فيديو + تحكم كامل + سرعة + PiP
 */

// ━━━━━━━━━━━━ مشغل الصوت المتقدم ━━━━━━━━━━━━

@Composable
fun SovereignAudioPlayer(
    title: String,
    artist: String = "",
    durationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float = 1f,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onSkipNext: (() -> Unit)? = null,
    onSkipPrev: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SovereignColors.SurfaceNavy,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // العنوان والفنان
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // أيقونة الرسالة الصوتية
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(SovereignColors.Cyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint = SovereignColors.Cyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    if (artist.isNotEmpty()) {
                        Text(
                            artist,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // زر السرعة
                IconButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                    Text(
                        "${playbackSpeed}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignColors.Gold
                    )
                }
            }

            // قائمة السرعات
            AnimatedVisibility(visible = showSpeedMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    speeds.forEach { speed ->
                        val isSelected = speed == playbackSpeed
                        Surface(
                            onClick = { onSpeedChange(speed); showSpeedMenu = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "${speed}x",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SovereignColors.Cyan else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // شريط التقدم
            var sliderPosition by remember { mutableFloatStateOf(progress) }
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it; onSeek(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = SovereignColors.Cyan,
                    activeTrackColor = SovereignColors.Cyan,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                )
            )

            // الوقت
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration((progress * durationMs).toLong()), fontSize = 11.sp, color = Color.Gray)
                Text(formatDuration(durationMs), fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            // أزرار التحكم
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // السابق
                if (onSkipPrev != null) {
                    IconButton(onClick = onSkipPrev) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                // تشغيل/إيقاف
                FloatingActionButton(
                    onClick = onPlayPause,
                    containerColor = SovereignColors.Cyan,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // التالي
                if (onSkipNext != null) {
                    IconButton(onClick = onSkipNext) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ مشغل الفيديو المتقدم ━━━━━━━━━━━━

@Composable
fun SovereignVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    onPipRequest: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        // الفيديو
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // أزرار التحكم المتحركة
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            ) {
                // رجوع 10 ثواني
                IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }) {
                    Icon(Icons.Rounded.FastRewind, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // تشغيل/إيقاف
                FloatingActionButton(
                    onClick = {
                        isPlaying = !isPlaying
                        exoPlayer.playWhenReady = isPlaying
                    },
                    containerColor = SovereignColors.Cyan.copy(alpha = 0.9f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // تقديم 10 ثواني
                IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                    Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        // الشريط العلوي
        AnimatedVisibility(visible = showControls, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))

                // سرعة التشغيل
                Text(
                    "${playbackSpeed}x",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovereignColors.Gold,
                    modifier = Modifier.clickable {
                        playbackSpeed = when (playbackSpeed) {
                            1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f
                        }
                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                    }
                )

                Spacer(Modifier.width(8.dp))

                // PiP
                if (onPipRequest != null) {
                    IconButton(onClick = onPipRequest) {
                        Icon(Icons.Rounded.PictureInPicture, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // ملء الشاشة
                if (onFullscreenToggle != null) {
                    IconButton(onClick = onFullscreenToggle) {
                        Icon(
                            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            null, tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ فقاعة الرسالة الصوتية ━━━━━━━━━━━━

@Composable
fun VoiceNotePlayer(
    durationMs: Long,
    positionMs: Long = 0,
    isPlaying: Boolean = false,
    isMe: Boolean = false,
    onPlayPause: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val accentColor = if (isMe) SovereignColors.Cyan else SovereignColors.Gold

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.widthIn(max = 260.dp)
    ) {
        // زر التشغيل
        IconButton(onClick = onPlayPause) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
        }

        // الموجة الصوتية + شريط التقدم
        Column(modifier = Modifier.weight(1f)) {
            // شريط التقدم المخصص (موجة صوتية مبسطة)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(if (isPlaying) positionMs else 0),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Text(
                    formatDuration(durationMs),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ─── مساعد تنسيق الوقت ───
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
