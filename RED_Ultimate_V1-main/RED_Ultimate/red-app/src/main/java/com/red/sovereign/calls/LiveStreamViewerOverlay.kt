package com.red.sovereign.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
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
    var chatText by remember { mutableStateOf("") }
    var showRaisedHandsSheet by remember { mutableStateOf(false) }

    if (state is LiveStreamUiState.Incoming) {
        LiveIncomingCard(state)
        return
    }

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Color(0xFFF5C842))
                        Text("جارٍ فتح البث المباشر المباشر عبر SFU...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // Gradient Overlays for readable text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Top Header: Live Badge, Viewer Count, Network Quality
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE53935), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "مباشر 🔴",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val streamTitle = when (state) {
                        is LiveStreamUiState.Connecting -> "جارٍ بدء البث..."
                        is LiveStreamUiState.Active -> "البث السيادي"
                        is LiveStreamUiState.Error -> "خطأ"
                        else -> ""
                    }
                    Text(
                        text = streamTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Viewer Count Badge
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "👁️ ${LiveStreamRuntime.viewerCount}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Network Quality Health
                    val stats = LiveStreamRuntime.networkStats
                    val qualityColor = when (stats.quality) {
                        NetworkStats.Quality.EXCELLENT -> Color(0xFF00C98C)
                        NetworkStats.Quality.GOOD -> Color(0xFF2196F3)
                        NetworkStats.Quality.FAIR -> Color(0xFFFF9800)
                        else -> Color(0xFFE53935)
                    }
                    Box(
                        modifier = Modifier
                            .background(qualityColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${stats.rttMs}ms • ${stats.quality.name}",
                            color = qualityColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Floating Reactions Column (Hearts Burst on Right)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LiveStreamRuntime.reactions.takeLast(8).forEach { reaction ->
                    Text(
                        text = reaction.emoji,
                        fontSize = 28.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Floating Live Chat Overlay (Bottom Left)
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(200.dp)
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 80.dp)
            ) {
                val listState = rememberLazyListState()
                val messages = LiveStreamRuntime.chatMessages
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${msg.senderName}:",
                                    color = Color(0xFFF5C842),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Input & Interactive Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Chat Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatText,
                        onValueChange = { chatText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        placeholder = { Text("اكتب تعليقاً حياً...", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                            focusedBorderColor = Color(0xFFF5C842),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (chatText.isNotBlank()) {
                                LiveStreamService.sendChat(context, chatText.trim(), "أنا")
                                chatText = ""
                            }
                        })
                    )

                    IconButton(
                        onClick = {
                            if (chatText.isNotBlank()) {
                                LiveStreamService.sendChat(context, chatText.trim(), "أنا")
                                chatText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFF5C842), CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }

                    // Share Stream Link Button
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val activeStreamId = when (state) {
                        is LiveStreamUiState.Connecting -> state.streamId
                        is LiveStreamUiState.Active -> state.streamId
                        else -> ""
                    }
                    IconButton(
                        onClick = {
                            if (activeStreamId.isNotBlank()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("younes://livestream/$activeStreamId"))
                                android.widget.Toast.makeText(context, "تم نسخ رابط البث المباشر 🔗", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة رابط البث", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Broadcaster Camera Flip Button
                    if (isBroadcaster) {
                        IconButton(
                            onClick = { LiveStreamService.action(context, LiveStreamService.ACTION_SWITCH_CAMERA) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "تبديل الكاميرا", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Broadcaster Recording Button
                        IconButton(
                            onClick = {
                                if (LiveStreamRuntime.isRecording) {
                                    LiveStreamService.action(context, LiveStreamService.ACTION_STOP_RECORDING)
                                } else {
                                    LiveStreamService.action(context, LiveStreamService.ACTION_START_RECORDING)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (LiveStreamRuntime.isRecording) Color.Red else Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "تسجيل البث", tint = if (LiveStreamRuntime.isRecording) Color.White else Color.Red, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        // Viewer Audio-Only Toggle Button
                        IconButton(
                            onClick = { LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_AUDIO_ONLY) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (LiveStreamRuntime.isAudioOnly) Color(0xFFF5C842) else Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Headset, contentDescription = "وضع الصوت فقط", tint = if (LiveStreamRuntime.isAudioOnly) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Reaction Heart Button
                    IconButton(
                        onClick = { LiveStreamService.sendReaction(context, "❤️") },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "تفاعل", tint = Color.White, modifier = Modifier.size(22.dp))
                    }

                    // Raised Hand Button for Viewers
                    if (!isBroadcaster) {
                        IconButton(
                            onClick = { LiveStreamService.raiseHand(context, "مشاهد") },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF2196F3), CircleShape)
                        ) {
                            Icon(Icons.Default.Handshake, contentDescription = "طلب انضمام للمسرح", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Broadcaster Hand Requests Manager
                    if (isBroadcaster && LiveStreamRuntime.raisedHands.isNotEmpty()) {
                        IconButton(
                            onClick = { showRaisedHandsSheet = true },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFFF9800), CircleShape)
                        ) {
                            Text("${LiveStreamRuntime.raisedHands.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // End Stream Button
                    IconButton(
                        onClick = { LiveStreamService.stop(context) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "إيقاف البث", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showRaisedHandsSheet) {
        AlertDialog(
            onDismissRequest = { showRaisedHandsSheet = false },
            title = { Text("طلبات الانضمام للمسرح (${LiveStreamRuntime.raisedHands.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveStreamRuntime.raisedHands.forEach { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.userName, fontWeight = FontWeight.SemiBold)
                            Button(onClick = {
                                LiveStreamService.approveCoHost(context, user.userId)
                                showRaisedHandsSheet = false
                            }) {
                                Text("الموافقة")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRaisedHandsSheet = false }) { Text("إغلاق") }
            }
        )
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

/** بطاقة «بدأ البث» — مشاهدة أو تجاهل، بلا رنة هاتف. */
@Composable
private fun LiveIncomingCard(state: LiveStreamUiState.Incoming) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC02080C))
                .clickable { LiveStreamService.stop(context) },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0D1B2A))
                    .padding(22.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.background(Color(0xFFE53935), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("مباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    "${state.broadcasterName.ifBlank { "مستخدم يونس" }} بدأ بثاً",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("شاهد عندما تريد — بدون رنين", color = Color.Gray, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { LiveStreamService.stop(context) }) { Text("تجاهل", color = Color.White) }
                    Button(onClick = { LiveStreamService.start(context, state.streamId, state.userId, false) }) {
                        Text("مشاهدة")
                    }
                }
            }
        }
    }
}
