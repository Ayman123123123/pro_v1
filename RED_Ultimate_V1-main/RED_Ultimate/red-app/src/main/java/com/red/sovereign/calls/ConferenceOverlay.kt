package com.red.sovereign.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
    if (state is ConferenceUiState.Incoming) {
        ConferenceInviteSheet(state)
        return
    }

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
    var showRecordConsent by remember { mutableStateOf(false) }

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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(activeRoomId))
                                    android.widget.Toast.makeText(context, "تم نسخ معرف الغرفة", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة المعرف", tint = Color.White)
                            }
                            if (ConferenceRuntime.participants.any { it.raisedHand }) {
                                IconButton(
                                    onClick = { showRaisedHandsSheet = true }
                                ) {
                                    BadgedBox(badge = { Badge { Text(ConferenceRuntime.participants.count { it.raisedHand }.toString()) } }) {
                                        Icon(Icons.Default.Handshake, contentDescription = "الأيدي المرفوعة", tint = Color(0xFFF5C842))
                                    }
                                }
                            }
                        }
                    }

                    // Pinned Note in Meeting/Space
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

                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseScale"
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Local User Stage Card
                                item {
                                    val isLocalSpeaking = ConferenceRuntime.isSpeaker && !ConferenceRuntime.isMuted
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLocalSpeaking) {
                                                Box(
                                                    Modifier
                                                        .size(76.dp * pulseScale)
                                                        .clip(CircleShape)
                                                        .background(Color(0x3300C98C))
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                                            listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))
                                                        )
                                                    )
                                                    .border(
                                                        2.dp,
                                                        if (isLocalSpeaking) Color(0xFF00C98C) else Color(0x33FFFFFF),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("أنت", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text("أنت (${if (ConferenceRuntime.isSpeaker) "متحدث" else "مستمع"})", color = Color.White, fontSize = 12.sp)
                                    }
                                }

                                // Remote Speakers
                                items(speakers) { speaker ->
                                    val isSpeaking = speaker.isSpeaking
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSpeaking) {
                                                Box(
                                                    Modifier
                                                        .size(76.dp * pulseScale)
                                                        .clip(CircleShape)
                                                        .background(Color(0x3300C98C))
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                                            listOf(Color(0xFF1E293B), Color(0xFF090D16))
                                                        )
                                                    )
                                                    .border(
                                                        2.dp,
                                                        if (isSpeaking) Color(0xFF00C98C) else if (speaker.isHost) Color(0xFFF5C842) else Color(0x33FFFFFF),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(speaker.userId.take(2).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(speaker.userId.take(10), color = if (speaker.isHost) Color(0xFFF5C842) else Color.White, fontSize = 12.sp, fontWeight = if (speaker.isHost) FontWeight.Bold else FontWeight.Normal)
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

                    if (isVideoMode) {
                        IconButton(
                            onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_VIDEO) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (!ConferenceRuntime.isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color(0xFF00C98C), CircleShape)
                        ) {
                            Icon(if (!ConferenceRuntime.isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "الكاميرا", tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = {
                            if (ConferenceRuntime.isRecording) {
                                ConferenceService.action(context, ConferenceService.ACTION_STOP_RECORDING)
                            } else {
                                showRecordConsent = true
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(if (ConferenceRuntime.isRecording) Color(0xFFB71C1C) else Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            if (ConferenceRuntime.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = "تسجيل",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { ConferenceService.leave(context) },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "مغادرة", tint = Color.White)
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

    // موافقة صريحة قبل أي تسجيل — لا يُفترض أبداً (خصوصية الطرفين)
    if (showRecordConsent) {
        AlertDialog(
            onDismissRequest = { showRecordConsent = false },
            title = { Text("تسجيل المؤتمر", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيُسجَّل صوتك عبر الميكروفون محلياً على جهازك بتشفير AES-GCM.\n" +
                        "أكّد أن جميع المشاركين موافقون على التسجيل قبل البدء."
                )
            },
            confirmButton = {
                TextButton({
                    showRecordConsent = false
                    ConferenceService.action(context, ConferenceService.ACTION_START_RECORDING, consent = true)
                }) { Text("موافق — ابدأ التسجيل", color = Color(0xFF00C98C)) }
            },
            dismissButton = {
                TextButton({ showRecordConsent = false }) { Text("إلغاء") }
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
                        ConferenceService.join(context, state.roomId, state.userId, state.video, asHost = false)
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
