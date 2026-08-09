package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
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
fun YounesLiveStreamOverlay() {
    val state = LiveStreamRuntime.state
    if (state is LiveStreamUiState.Idle) return

    val context = LocalContext.current
    val localVideo = LiveStreamRuntime.localVideo
    val remoteVideo = LiveStreamRuntime.remoteVideo

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
            val isBroadcaster = when (state) {
                is LiveStreamUiState.Connecting -> state.isBroadcaster
                is LiveStreamUiState.Active -> state.isBroadcaster
                else -> false
            }

            // Video Stream View
            if (isBroadcaster && localVideo != null) {
                LiveStreamVideoRenderer(
                    track = localVideo,
                    mirror = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!isBroadcaster && remoteVideo != null) {
                LiveStreamVideoRenderer(
                    track = remoteVideo,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF5C842))
                }
            }

            // Top Overlay Panel (Info)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "مباشر",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val streamTitle = when (state) {
                        is LiveStreamUiState.Connecting -> "جارٍ بدء البث..."
                        is LiveStreamUiState.Active -> "البث: ${state.streamId}"
                        is LiveStreamUiState.Error -> "خطأ: ${state.message}"
                        else -> ""
                    }
                    Text(
                        text = streamTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val stats = LiveStreamRuntime.networkStats
                    Text("جودة الشبكة: ${stats.quality.name} · ${stats.rttMs}ms", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            // Bottom Overlay Panel (Controls)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBroadcaster) {
                    IconButton(
                        onClick = { LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_MIC) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (LiveStreamRuntime.isMuted) Color.Red else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(28.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (LiveStreamRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "كتم/تفعيل الميكروفون",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_VIDEO) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (LiveStreamRuntime.localVideo?.enabled() == true) Color.White.copy(alpha = 0.2f) else Color.Red,
                                RoundedCornerShape(28.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "كتم/تفعيل الكاميرا",
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = { LiveStreamService.stop(context) },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, RoundedCornerShape(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "إيقاف البث",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStreamVideoRenderer(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val egl = LiveStreamRuntime.eglContext ?: return
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
