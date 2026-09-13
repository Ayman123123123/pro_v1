package com.red.sovereign.features.media

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🎵 YOUNES Sovereign Media Player — Ultimate Suite
 */

@Composable
fun SovereignVideoPlayer(
    uri: Uri,
    onBack: () -> Unit = {}
) {
    // G3: مفتاح uri يمنع إعادة استخدام مشغل قديم لملف جديد، وسياق
    // التطبيق يمنع تسرب Activity، والإيقاف عند ON_PAUSE يمنع استمرار
    // الصوت بعد مغادرة الشاشة (تسرب ExoPlayer).
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(appContext).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycle, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exoPlayer.play()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { player = exoPlayer } }, modifier = Modifier.fillMaxSize())
        
        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
        }
    }
}

@Composable
fun SovereignAudioPlayer(
    title: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SovereignColors.SurfaceNavy,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(SovereignColors.Cyan.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = SovereignColors.Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("رسالة صوتية سيادية", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = SovereignColors.Cyan, modifier = Modifier.size(32.dp))
            }
        }
    }
}
