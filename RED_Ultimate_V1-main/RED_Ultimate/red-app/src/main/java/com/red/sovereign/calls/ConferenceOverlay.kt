package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
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
    val isVideoMode = ConferenceRuntime.isVideoEnabled
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showRaisedHandsSheet by remember { mutableStateOf(false) }
    var isSpeakerFocusMode by remember { mutableStateOf(false) }
    var showInCallChat by remember { mutableStateOf(false) }
    var inCallMessageInput by remember { mutableStateOf("") }

    val activeRoomId = when (state) {
        is ConferenceUiState.Connecting -> state.roomId
        is ConferenceUiState.Active -> state.roomId
        else -> ""
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
                .background(Color(0xFF060D1A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Space Header Info & Share Link
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(if (isVideoMode) Color(0xFF00C98C) else Color(0xFFA78BFA), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isVideoMode) "مؤتمر فيديو" else "مساحة صوتية 🎙️",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "مساحة: ${activeRoomId.take(12)}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Layout Switcher (Grid vs Speaker Focus)
                            IconButton(
                                onClick = { isSpeakerFocusMode = !isSpeakerFocusMode },
                                modifier = Modifier.size(36.dp).background(if (isSpeakerFocusMode) Color(0xFFF5C842) else Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(if (isSpeakerFocusMode) Icons.Default.Person else Icons.Default.GridView, contentDescription = "تغيير العرض", tint = if (isSpeakerFocusMode) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                            }

                            // Copy Link Button
                            IconButton(
                                onClick = {
                                    if (activeRoomId.isNotBlank()) {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("younes://space/$activeRoomId"))
                                        android.widget.Toast.makeText(context, "تم نسخ رابط المساحة 🔗", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة الرابط", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            // Network Health Indicator
                            val stats = ConferenceRuntime.networkStats
                            QualityIndicator(stats)
                        }
                    }

                    // Pinned Message Banner (if available)
                    if (ConferenceRuntime.pinnedMessage.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Pin, contentDescription = null, tint = Color(0xFFF5C842), modifier = Modifier.size(16.dp))
                                Text("رسالة مثبتة: ${ConferenceRuntime.pinnedMessage}", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Main Stage Grid (X-Spaces Avatars or Video Grid)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isVideoMode) {
                        // X-Spaces Audio Stage Layout
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("المتحدثون والمشرفون", color = Color(0xFFF5C842), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            
                            val speakers = participants.filter { it.role in setOf("HOST", "CO_HOST", "SPEAKER") || it.isHost }
                            val listeners = participants.filter { !speakers.contains(it) }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Local User Stage Card
                                item {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E293B))
                                                .border(2.dp, if (!ConferenceRuntime.isMuted) Color(0xFF00C98C) else Color.Transparent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("أنت", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("أنت (${if (ConferenceRuntime.isSpeaker) "متحدث" else "مستمع"})", color = Color.White, fontSize = 12.sp)
                                    }
                                }

                                // Remote Speakers
                                items(speakers) { speaker ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1E293B))
                                                .border(2.dp, if (speaker.isSpeaking) Color(0xFF00C98C) else Color.Transparent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(speaker.userId.take(2).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(speaker.userId.take(10), color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (listeners.isNotEmpty()) {
                                Text("المستمعون (${listeners.size})", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.height(120.dp)
                                ) {
                                    items(listeners) { listenerUser ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF0F172A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(listenerUser.userId.take(2).uppercase(), color = Color.LightGray, fontSize = 12.sp)
                                            }
                                            Text(listenerUser.userId.take(8), color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Video Grid
                        val totalTiles = 1 + remoteVideos.size
                        val columns = if (totalTiles <= 2) 1 else 2
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (localVideo != null) {
                                            ConferenceVideoRenderer(track = localVideo, mirror = true, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("أنت", color = Color.White) }
                                        }
                                    }
                                }
                            }

                            items(participants.filter { it.userId.isNotBlank() }) { participant ->
                                val track = remoteVideos[participant.userId]
                                Card(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (participant.hasVideo && track != null) {
                                            ConferenceVideoRenderer(track = track, mirror = false, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(participant.userId.take(8), color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Reactions Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    listOf("👏", "💯", "🔥", "😂", "❤️").forEach { emoji ->
                        IconButton(
                            onClick = { ConferenceService.sendReaction(context, emoji) },
                            modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Text(emoji, fontSize = 16.sp)
                        }
                    }
                }

                // Bottom Interactive Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showInCallChat = true },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "دردشة الاجتماع", tint = Color.White)
                    }

                    if (ConferenceRuntime.isSpeaker) {
                        IconButton(
                            onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_MIC) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (ConferenceRuntime.isMuted) Color.Red else Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(if (ConferenceRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "الميكروفون", tint = Color.White)
                        }
                    } else {
                        // Listener Raise Hand Button
                        IconButton(
                            onClick = { ConferenceService.raiseHand(context) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFF2196F3), CircleShape)
                        ) {
                            Icon(Icons.Default.Handshake, contentDescription = "طلب التحدث", tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_VIDEO) },
                        modifier = Modifier
                            .size(52.dp)
                            .background(if (!ConferenceRuntime.isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color(0xFF00C98C), CircleShape)
                    ) {
                        Icon(if (!ConferenceRuntime.isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "الكاميرا", tint = Color.White)
                    }

                    IconButton(
                        onClick = { ConferenceService.leave(context) },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "مغادرة", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showInCallChat) {
        AlertDialog(
            onDismissRequest = { showInCallChat = false },
            title = { Text("دردشة الاجتماع 💬") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("دردشة مشفرة حية بداخل القاعة:", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = inCallMessageInput,
                        onValueChange = { inCallMessageInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("اكتب رسالة...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inCallMessageInput.isNotBlank()) {
                        ConferenceService.pinMessage(context, inCallMessageInput.trim())
                        inCallMessageInput = ""
                        showInCallChat = false
                    }
                }) {
                    Text("تثبيت كالرسالة الرئيسية")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInCallChat = false }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun ConferenceInviteSheet(state: ConferenceUiState.Incoming) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF071018))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.video) "دعوة مؤتمر فيديو" else "دعوة مساحة صوتية",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "من ${state.inviter.ifBlank { "مجموعة يونس" }}",
                        color = Color.White.copy(0.7f),
                        fontSize = 15.sp
                    )
                    Text("انضم عندما تريد — لا رنين على كل الأعضاء", color = Color.Gray, fontSize = 13.sp)
                }
                PulseAvatar(letter = state.inviter, pulsing = false)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    EndCallButton("لاحقاً") { ConferenceService.leave(context) }
                    AcceptCallButton("انضمام") {
                        ConferenceService.join(context, state.roomId, state.userId, state.video)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityIndicator(stats: NetworkStats) {
    val color = when (stats.quality) {
        NetworkStats.Quality.EXCELLENT -> Color(0xFF2DDBA4)
        NetworkStats.Quality.GOOD -> Color(0xFF8BC34A)
        NetworkStats.Quality.FAIR -> Color(0xFFFFC107)
        NetworkStats.Quality.POOR -> Color(0xFFE53935)
        NetworkStats.Quality.UNKNOWN -> Color.Gray
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(stats.quality.name, color = Color.White, fontSize = 11.sp)
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
