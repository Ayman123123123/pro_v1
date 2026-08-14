package com.red.sovereign.calls
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.LocalPhone

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesMuted
import com.red.sovereign.ui.theme.YounesRose
import com.red.sovereign.ui.theme.YounesVoid
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallElapsedTimer(startedAt: Long, color: Color = Color.White) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - startedAt).coerceAtLeast(0L)) / 1000
    Text("%02d:%02d".format(elapsed / 60, elapsed % 60), color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun PulseAvatar(size: Dp = 128.dp, letter: String, pulsing: Boolean = true) {
    val pulse = rememberInfiniteTransition(label = "ring")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "scale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.35f)) {
        if (pulsing) {
            Box(Modifier.size(size * 1.28f).scale(scale).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.16f)))
            Box(Modifier.size(size * 1.12f).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.22f)))
        }
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(0xFF17324A)),
            contentAlignment = Alignment.Center
        ) {
            if (letter.isBlank()) {
                androidx.compose.foundation.Image(
                    painterResource(com.red.sovereign.R.drawable.younes_icon_master),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(letter.take(1).uppercase(), color = Color.White, fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CallRoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color = Color.White.copy(alpha = 0.14f),
    tint: Color = Color.White,
    size: Dp = 58.dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(container).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.42f))
        }
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
    }
}

@Composable
fun EndCallButton(label: String = "إنهاء", onClick: () -> Unit) {
    CallRoundButton(
        icon = androidx.compose.material.icons.Icons.Filled.CallEnd,
        label = label,
        onClick = onClick,
        container = YounesRose,
        tint = Color.White,
        size = 68.dp
    )
}

@Composable
fun AcceptCallButton(label: String = "قبول", onClick: () -> Unit) {
    CallRoundButton(
        icon = androidx.compose.material.icons.Icons.Filled.LocalPhone,
        label = label,
        onClick = onClick,
        container = YounesEmerald,
        tint = Color.White,
        size = 68.dp
    )
}

@Composable
fun EncryptedBadge(text: String = "مشفّرة طرفياً") {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp).size(7.dp).clip(CircleShape).background(YounesEmerald))
        Text(text, color = YounesMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
    }
}

@Composable
fun NetworkQualityBars(stats: NetworkStats) {
    val (bars, color, label) = when (stats.quality) {
        NetworkStats.Quality.EXCELLENT -> Triple(4, Color(0xFF2DDBA4), "ممتازة")
        NetworkStats.Quality.GOOD -> Triple(3, Color(0xFF8BC34A), "جيدة")
        NetworkStats.Quality.FAIR -> Triple(2, Color(0xFFFFC107), "متوسطة")
        NetworkStats.Quality.POOR -> Triple(1, YounesRose, "ضعيفة")
        NetworkStats.Quality.UNKNOWN -> Triple(0, Color.Gray, "—")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { i ->
            val on = i < bars
            Box(
                Modifier.size(width = 3.dp, height = (5 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (on) color else Color.White.copy(alpha = 0.22f))
            )
        }
        Text("$label ${if (stats.rttMs > 0) "· ${stats.rttMs}ms" else ""}".trim(), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
fun WebrtcVideo(track: VideoTrack?, egl: EglBase.Context?, mirror: Boolean, modifier: Modifier) {
    if (egl == null) {
        Box(modifier.background(YounesVoid))
        return
    }
    
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(egl, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                rendererRef = this
            }
        },
        update = { view -> 
            view.setMirror(mirror)
        },
        onRelease = { view ->
            view.release()
        },
        modifier = modifier
    )
    
    DisposableEffect(track, rendererRef) {
        val view = rendererRef
        if (track != null && view != null) {
            track.addSink(view)
        }
        onDispose {
            if (track != null && view != null) {
                track.removeSink(view)
            }
        }
    }
}
