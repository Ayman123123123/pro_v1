package com.red.sovereign.features.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
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
        if (ConferenceRuntime.isVideoEnabled) {
            // MediaSFU Advanced Grid
            MediaSfuGrid(
                localVideo = localVideo,
                remoteVideos = remoteVideos,
                participants = participants
            )
        } else {
            // Twitter Spaces / Audio Conference Stage
            TwitterSpacesStage(participants)
        }

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

@Composable
fun TwitterSpacesStage(participants: List<com.red.sovereign.calls.ConferenceParticipant>) {
    val speakers = participants.filter { it.role in setOf("HOST", "CO_HOST", "SPEAKER") || it.isHost }
    val listeners = participants.filter { !speakers.contains(it) }
    val speakingPeers = ConferenceRuntime.speakingPeers
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(top = 80.dp, start = 16.dp, end = 16.dp)) {
        Text("المتحدثون", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                SpaceAvatarTile(label = "أنت", isSpeaking = ConferenceRuntime.isSpeaker && !ConferenceRuntime.isMuted, isHost = ConferenceRuntime.selfRole == "HOST")
            }
            items(speakers, key = { it.userId }) { speaker ->
                val isSpeaking = speaker.userId in speakingPeers || (speakingPeers.isEmpty() && speaker.isSpeaking)
                SpaceAvatarTile(label = speaker.userId.take(8), isSpeaking = isSpeaking, isHost = speaker.isHost || speaker.role == "HOST", onClick = { ConferenceService.pinParticipant(context, speaker.userId) })
            }
        }
        
        if (listeners.isNotEmpty()) {
            Text("المستمعون", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(listeners, key = { it.userId }) { listener ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1E293B)), contentAlignment = Alignment.Center) {
                            Text(listener.userId.take(2).uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(listener.userId.take(8), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceAvatarTile(label: String, isSpeaking: Boolean, isHost: Boolean, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick?.invoke() }) {
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            if (isSpeaking) {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(600),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                Box(modifier = Modifier.size(80.dp * scale).clip(CircleShape).background(Color(0xFF7C5CFF).copy(alpha = 0.3f)))
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(
                        if (isHost) 2.dp else if (isSpeaking) 2.dp else 0.dp,
                        if (isHost) Color(0xFFB8860B) else if (isSpeaking) Color(0xFF7C5CFF) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(label.take(2).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (isHost) FontWeight.Bold else FontWeight.Normal)
            if (isHost) {
                Icon(Icons.Default.Star, contentDescription = "Host", tint = Color(0xFFB8860B), modifier = Modifier.size(12.dp))
            }
        }
    }
}
