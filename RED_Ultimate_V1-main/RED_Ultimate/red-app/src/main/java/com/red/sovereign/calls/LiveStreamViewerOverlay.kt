package com.red.sovereign.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.red.sovereign.ui.theme.AqyalGold
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.random.Random

/**
 * 🎬 بث مباشر احترافي — نمط TikTok بالكامل!
 * مؤثرات بصرية مذهلة، تدرجات لونية، شات شفاف يتلاشى تدريجياً، وأمواج من القلوب.
 */
@Composable
fun YounesLiveStreamOverlay() {
    val state = LiveStreamRuntime.state
    if (state is LiveStreamUiState.Idle) return

    val context = LocalContext.current
    val localVideo = LiveStreamRuntime.localVideo
    val remoteVideo = LiveStreamRuntime.remoteVideo
    var chatText by remember { mutableStateOf("") }
    var showRaisedHandsSheet by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

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
                .background(Color.Black)
        ) {
            val isBroadcaster = when (state) {
                is LiveStreamUiState.Connecting -> state.isBroadcaster
                is LiveStreamUiState.Active -> state.isBroadcaster
                else -> false
            }

            // ─── 1. خلفية الفيديو الرئيسية ملء الشاشة ───
            if (isBroadcaster && localVideo != null) {
                LiveStreamVideoRenderer(track = localVideo, mirror = true, modifier = Modifier.fillMaxSize())
            } else if (!isBroadcaster && remoteVideo != null) {
                LiveStreamVideoRenderer(track = remoteVideo, mirror = false, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = Color(0xFFF91850)) // TikTok Pink
                        Text("جارٍ معالجة البث الفائق...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ─── 2. تدرجات علوية وسفلية (Vignette) لقراءة النصوص بوضوح ───
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
            )

            // ─── 3. شريط المعلومات العلوي (Host & Stats) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Host Capsule
                val hostName = when (state) {
                    is LiveStreamUiState.Connecting -> "يتم الاتصال..."
                    is LiveStreamUiState.Active -> if (isBroadcaster) "أنت (البث الخاص بك)" else "البث المباشر"
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .padding(end = 12.dp, start = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Host Avatar
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF91850), Color(0xFF25F4EE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(hostName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column {
                        Text(hostName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("مضيف 👑", color = Color(0xFFF5C842), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Stats Cluster (Viewers, Close)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Viewer Count (TikTok style transparent badge)
                    Box(
                        Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("${LiveStreamRuntime.viewerCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = { LiveStreamService.stop(context) },
                        modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ─── 4. أنيميشن القلوب (Reactions floating up) ───
            FloatingReactions(
                reactions = LiveStreamRuntime.reactions,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 140.dp, end = 20.dp)
                    .width(60.dp)
                    .height(300.dp)
            )

            // ─── 5. الشات المباشر (TikTok Style: Fading top, rapid scrolling) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(240.dp)
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 80.dp)
            ) {
                val listState = rememberLazyListState()
                val messages = LiveStreamRuntime.chatMessages

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        Row(
                            Modifier.background(Brush.horizontalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent)), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${msg.senderName}: ",
                                color = Color(0xFFC0C0C0), // Level color
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = msg.text,
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Fading effect for top of chat
                Box(
                    Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
                )
            }

            // ─── 6. شريط الأدوات السفلي ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Comment Input Field
                    OutlinedTextField(
                        value = chatText,
                        onValueChange = { chatText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        placeholder = { Text("أضف تعليقاً...", color = Color.White.copy(0.7f), fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(22.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (chatText.isNotBlank()) {
                                LiveStreamService.sendChat(context, chatText.trim(), "أنا")
                                chatText = ""
                            }
                        })
                    )

                    // Action Icons (Right side)
                    if (isBroadcaster) {
                        ActionIcon(Icons.Default.Cameraswitch, "تبديل الكاميرا") {
                            LiveStreamService.action(context, LiveStreamService.ACTION_SWITCH_CAMERA)
                        }
                        ActionIcon(
                            icon = if (LiveStreamRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            desc = "صوت",
                            color = if (LiveStreamRuntime.isMuted) Color(0xFFF91850) else Color.White
                        ) {
                            LiveStreamRuntime.isMuted = !LiveStreamRuntime.isMuted // Mock toggle
                        }
                    } else {
                        // Viewer tools
                        ActionIcon(Icons.Default.CardGiftcard, "هدايا", tint = Color(0xFFFFD700)) {
                            // TODO: Show gifts panel
                        }
                        ActionIcon(Icons.Default.Share, "مشاركة") {
                            val activeStreamId = when (state) {
                                is LiveStreamUiState.Connecting -> state.streamId
                                is LiveStreamUiState.Active -> state.streamId
                                else -> ""
                            }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("younes://livestream/$activeStreamId"))
                            android.widget.Toast.makeText(context, "تم نسخ رابط البث", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Floating Reaction Button (Bottom Right)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFF91850), Color(0xFFFF0055))))
                            .clickable { LiveStreamService.sendReaction(context, listOf("❤️", "🔥", "😂", "✨").random()) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "إعجاب", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Color.Unspecified, color: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = if (tint != Color.Unspecified) tint else color, modifier = Modifier.size(20.dp))
    }
}

// ─── Floating Reactions Animation System ───
@Composable
private fun FloatingReactions(reactions: List<LiveStreamReaction>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        reactions.takeLast(15).forEach { reaction ->
            key(reaction.id) {
                FloatingHeart(reaction.emoji)
            }
        }
    }
}

@Composable
private fun FloatingHeart(emoji: String) {
    var isVisible by remember { mutableStateOf(false) }
    
    // Randomize path
    val startX = remember { Random.nextInt(-20, 20).toFloat() }
    val endX = remember { Random.nextInt(-60, 60).toFloat() }
    
    val translateY by animateFloatAsState(
        targetValue = if (isVisible) -400f else 0f,
        animationSpec = tween(durationMillis = 2500, easing = LinearOutSlowInEasing)
    )
    
    val translateX by animateFloatAsState(
        targetValue = if (isVisible) endX else startX,
        animationSpec = tween(durationMillis = 2500, easing = FastOutLinearInEasing)
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 2500, easing = CubicBezierEasing(0.8f, 0f, 1f, 1f))
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    if (alpha > 0.05f) {
        Text(
            text = emoji,
            fontSize = 32.sp,
            modifier = Modifier
                .graphicsLayer(
                    translationY = translateY,
                    translationX = translateX,
                    alpha = alpha
                )
                .padding(4.dp)
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
                .background(Color.Black.copy(0.7f))
                .clickable { LiveStreamService.stop(context) },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                    .padding(24.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF25F4EE), Color(0xFFF91850)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LiveTv, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${state.broadcasterName.ifBlank { "أحد الأصدقاء" }} بدأ بثاً",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("انضم لمشاهدة البث المباشر والتفاعل", color = Color.Gray, fontSize = 14.sp)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { LiveStreamService.stop(context) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))
                    ) {
                        Text("لاحقاً", color = Color.White)
                    }
                    Button(
                        onClick = { LiveStreamService.start(context, state.streamId, state.userId, false) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF91850))
                    ) {
                        Text("مشاهدة")
                    }
                }
            }
        }
    }
}
