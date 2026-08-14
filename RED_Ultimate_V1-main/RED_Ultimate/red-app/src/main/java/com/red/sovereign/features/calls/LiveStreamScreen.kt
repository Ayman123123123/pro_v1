package com.red.sovereign.features.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.red.sovereign.calls.LiveStreamRuntime
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.calls.LiveStreamUiState
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * 🔴 Sovereign Live Stream Screen — Immersive TikTok/Instagram style UI
 */
@Composable
fun LiveStreamScreen() {
    val state = LiveStreamRuntime.state
    if (state is LiveStreamUiState.Idle) return

    val context = LocalContext.current
    val localVideo = LiveStreamRuntime.localVideo
    val remoteVideo = LiveStreamRuntime.remoteVideo
    var chatText by remember { mutableStateOf("") }
    
    val isBroadcaster = when (state) {
        is LiveStreamUiState.Connecting -> state.isBroadcaster
        is LiveStreamUiState.Active -> state.isBroadcaster
        else -> false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF02080C))
    ) {
        // Video Stream
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF5C842))
            }
        }

        // Gradients for readable text over video
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
        )

        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Live Pulse
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE53935).copy(alpha = alpha), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("البث السيادي", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("👁️ ${LiveStreamRuntime.viewerCount}", color = Color.White)
            }
        }

        // Floating Chat Overlay
        val listState = rememberLazyListState()
        val messages = LiveStreamRuntime.chatMessages
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 80.dp)
                .fillMaxWidth(0.7f)
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "${msg.senderName}: ${msg.text}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Action Bar (Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatText,
                onValueChange = { chatText = it },
                modifier = Modifier.weight(1f).height(48.dp),
                placeholder = { Text("أضف تعليقًا...", color = Color.Gray) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(0.5f),
                    unfocusedContainerColor = Color.Black.copy(0.5f),
                    focusedBorderColor = Color(0xFFF5C842),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (chatText.isNotBlank()) {
                        LiveStreamService.sendChat(context, chatText.trim(), "أنا")
                        chatText = ""
                    }
                })
            )
            
            if (isBroadcaster) {
                IconButton(
                    onClick = { LiveStreamService.action(context, LiveStreamService.ACTION_SWITCH_CAMERA) },
                    modifier = Modifier.size(48.dp).background(Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Cameraswitch, null, tint = Color.White)
                }
            } else {
                IconButton(
                    onClick = { LiveStreamService.sendReaction(context, "❤️") },
                    modifier = Modifier.size(48.dp).background(Color(0xFFE53935), CircleShape)
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Color.White)
                }
            }
            
            IconButton(
                onClick = { LiveStreamService.stop(context) },
                modifier = Modifier.size(48.dp).background(Color.Red, CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun LiveStreamVideoRenderer(track: VideoTrack, mirror: Boolean, modifier: Modifier) {
    val egl = LiveStreamRuntime.eglContext ?: return
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
