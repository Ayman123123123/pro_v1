package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun YounesConferenceOverlay() {
    val state = ConferenceRuntime.state
    if (state is ConferenceUiState.Idle) return

    val context = LocalContext.current
    val participants = ConferenceRuntime.participants
    val localVideo = ConferenceRuntime.localVideo
    val remoteVideos = ConferenceRuntime.remoteVideos

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF02080C))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Room Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    val roomText = when (state) {
                        is ConferenceUiState.Connecting -> "جارٍ الاتصال بالغرفة..."
                        is ConferenceUiState.Active -> "مؤتمر يونس نشط"
                        is ConferenceUiState.Error -> "خطأ: ${state.message}"
                        else -> ""
                    }
                    Text(
                        text = roomText,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "عدد المشاركين: ${participants.size + 1}",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }

                // Grid of Videos
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val totalTiles = 1 + remoteVideos.size
                    val columns = if (totalTiles <= 2) 1 else 2
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Local Participant Card
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (ConferenceRuntime.isVideoEnabled && localVideo != null) {
                                        ConferenceVideoRenderer(
                                            track = localVideo,
                                            mirror = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("أنت (بدون فيديو)", color = Color.White)
                                        }
                                    }
                                    Text(
                                        text = "أنت",
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Remote Participants Cards
                        items(participants.filter { it.userId.isNotBlank() }) { participant ->
                            val track = remoteVideos[participant.userId]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (participant.hasVideo && track != null) {
                                        ConferenceVideoRenderer(
                                            track = track,
                                            mirror = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(participant.userId.take(8), color = Color.White)
                                        }
                                    }
                                    Text(
                                        text = participant.userId.take(12),
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_MIC) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (ConferenceRuntime.isMuted) Color.Red else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(28.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (ConferenceRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "كتم/تفعيل الميكروفون",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_VIDEO) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (!ConferenceRuntime.isVideoEnabled) Color.Red else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(28.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (!ConferenceRuntime.isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "كتم/تفعيل الكاميرا",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { ConferenceService.leave(context) },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red, RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "إنهاء المكالمة",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConferenceVideoRenderer(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val egl = ConferenceRuntime.eglContext ?: return
    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(egl, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                renderer = this
                track?.addSink(this)
            }
        },
        update = { view ->
            track?.addSink(view)
        },
        modifier = modifier
    )
    DisposableEffect(track, renderer) {
        onDispose {
            renderer?.let {
                track?.removeSink(it)
                it.release()
            }
        }
    }
}
