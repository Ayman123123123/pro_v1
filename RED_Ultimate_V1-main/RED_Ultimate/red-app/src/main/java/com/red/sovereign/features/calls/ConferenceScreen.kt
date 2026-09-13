package com.red.sovereign.features.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.ConferenceUiState
import kotlinx.coroutines.delay
import org.webrtc.VideoTrack
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon

/**
 * 🌐 Sovereign Conference Screen — MediaSFU Grid Integration
 */
@Composable
fun ConferenceScreen() {
    val state = ConferenceRuntime.state
    if (state is ConferenceUiState.Idle) return

    val context = LocalContext.current
    val participants = ConferenceRuntime.participants
    val localVideo = ConferenceRuntime.localVideo
    val remoteVideos = ConferenceRuntime.remoteVideos
    
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060D1A))
    ) {
        // MediaSFU Advanced Grid
        MediaSfuGrid(
            localVideo = localVideo,
            remoteVideos = remoteVideos,
            participants = participants
        )

        // Top Header
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00C98C), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("مؤتمر آمن 🔒", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    text = "المشاركين: ${participants.size + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_MIC) },
                    modifier = Modifier.size(56.dp).background(if (ConferenceRuntime.isMuted) Color.Red else Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(if (ConferenceRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = Color.White)
                }

                IconButton(
                    onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_VIDEO) },
                    modifier = Modifier.size(56.dp).background(if (!ConferenceRuntime.isVideoEnabled) Color.White.copy(0.2f) else Color(0xFF00C98C), CircleShape)
                ) {
                    Icon(if (!ConferenceRuntime.isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                }

                FloatingActionButton(
                    onClick = { ConferenceService.leave(context) },
                    containerColor = Color.Red,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Invisible touch area to toggle controls
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
        )
    }
}

@Composable
fun MediaSfuGrid(
    localVideo: VideoTrack?,
    remoteVideos: Map<String, VideoTrack>,
    participants: List<com.red.sovereign.calls.ConferenceParticipant>
) {
    val totalTiles = 1 + remoteVideos.size
    
    // Adaptive MediaSFU algorithm for column count
    val columns = when {
        totalTiles == 1 -> 1
        totalTiles <= 4 -> 2
        totalTiles <= 9 -> 3
        else -> 4
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Local Video Tile
        item {
            MediaSfuTile(track = localVideo, mirror = true, label = "أنت")
        }

        // Remote Videos
        items(participants.filter { it.userId.isNotBlank() && remoteVideos.containsKey(it.userId) }) { participant ->
            MediaSfuTile(
                track = remoteVideos[participant.userId],
                mirror = false,
                label = participant.userId.take(8)
            )
        }
    }
}

@Composable
fun MediaSfuTile(track: VideoTrack?, mirror: Boolean, label: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (track != null) 3f/4f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (track != null) {
                ConferenceVideoRenderer(track = track, mirror = mirror, modifier = Modifier.fillMaxSize())
            } else {
                Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            
            // Name tag overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(label, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConferenceVideoRenderer(track: VideoTrack, mirror: Boolean, modifier: Modifier) {
    val egl = ConferenceRuntime.eglContext ?: return
    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(egl, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                renderer = this
                track.addSink(this)
            }
        },
        update = { view ->
            track.addSink(view)
        },
        modifier = modifier
    )
    DisposableEffect(track, renderer) {
        onDispose {
            renderer?.let {
                track.removeSink(it)
                it.release()
            }
        }
    }
}
