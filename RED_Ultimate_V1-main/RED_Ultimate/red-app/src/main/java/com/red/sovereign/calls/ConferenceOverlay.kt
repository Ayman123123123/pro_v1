package com.red.sovereign.calls

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.red.sovereign.ui.theme.SovereignColors
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
    // غرفة الانتظار: ننتظر موافقة المضيف — لا وسائط ولا قائمة مشاركين بعد.
    if (state is ConferenceUiState.WaitingApproval) {
        val waitingContext = LocalContext.current
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("بانتظار موافقة المضيف…", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("فعّلت المضيف غرفة انتظار لهذه المساحة — سيصلك إشعار فور قبولك.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp))
                    androidx.compose.material3.TextButton(onClick = { ConferenceService.leave(waitingContext) }) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
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
    var selectedParticipantForAction by remember { mutableStateOf<ConferenceParticipant?>(null) }
    var showHostActionMenu by remember { mutableStateOf(false) }
    var hostActionAnchor by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val activeRoomId = when (state) {
        is ConferenceUiState.Connecting -> state.roomId
        is ConferenceUiState.Active -> state.roomId
        else -> ""
    }

    val scheme = MaterialTheme.colorScheme

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
                .background(scheme.background)
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
                // Top Header Info & Controls
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
                                    .background(if (isVideoMode) scheme.primary else Color(0xFF6750A4), RoundedCornerShape(6.dp))
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
                                color = scheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isSpeakerFocusMode = !isSpeakerFocusMode }
                            ) {
                                Icon(
                                    if (isSpeakerFocusMode) Icons.Default.GridView else Icons.Default.Star,
                                    contentDescription = "تبديل وضع العرض (شبكة / تركيز)",
                                    tint = if (isSpeakerFocusMode) scheme.primary else scheme.onBackground
                                )
                            }
                            if (ConferenceRuntime.selfRole == "HOST") {
                                IconButton(
                                    onClick = { ConferenceService.toggleLock(context) }
                                ) {
                                    Icon(
                                        if (ConferenceRuntime.isRoomLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (ConferenceRuntime.isRoomLocked) "فتح الغرفة" else "قفل الغرفة",
                                        tint = if (ConferenceRuntime.isRoomLocked) Color(0xFFE54343) else scheme.onBackground
                                    )
                                }
                                IconButton(
                                    onClick = { ConferenceService.muteAll(context) }
                                ) {
                                    Icon(
                                        Icons.Default.MicOff,
                                        contentDescription = "كتم الجميع",
                                        tint = scheme.onBackground
                                    )
                                }
                            }
                            // غرفة الانتظار: مضيف ومضيف مشارك — زر يفتح لوحة المنتظرين
                            if (ConferenceRuntime.selfRole == "HOST" || ConferenceRuntime.selfRole == "CO_HOST") {
                                IconButton(onClick = { showLobbySheet = true }) {
                                    BadgedBox(badge = {
                                        if (ConferenceRuntime.waitingUsers.isNotEmpty()) {
                                            Badge { Text(ConferenceRuntime.waitingUsers.size.toString()) }
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.HourglassTop,
                                            contentDescription = "غرفة الانتظار",
                                            tint = if (ConferenceRuntime.lobbyEnabled) Color(0xFF14C79A) else scheme.onBackground
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(activeRoomId))
                                    android.widget.Toast.makeText(context, "تم نسخ معرف الغرفة", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة المعرف", tint = scheme.onBackground)
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
                                .background(scheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Pin, contentDescription = null, tint = Color(0xFFB8860B), modifier = Modifier.size(16.dp))
                                Text("رسالة مثبتة: ${ConferenceRuntime.pinnedMessage}", color = scheme.onSurface, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Main Stage Grid / Spotlight View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val pinnedId = ConferenceRuntime.pinnedParticipantId
                    val speakingPeers = ConferenceRuntime.speakingPeers

                    if (!isVideoMode) {
                        // Audio Space Stage Layout
                        val speakingRing = Color(0xFF7C5CFF)
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("المتحدثون والمشرفون", color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                if (pinnedId != null) {
                                    TextButton(onClick = { ConferenceService.pinParticipant(context, null) }) {
                                        Text("إلغاء التثبيت ⭐️", fontSize = 11.sp)
                                    }
                                }
                            }

                            val speakers = participants.filter { it.role in setOf("HOST", "CO_HOST", "SPEAKER") || it.isHost }
                            val listeners = participants.filter { !speakers.contains(it) }

                            val anyoneSpeaking = remember(speakers, participants, speakingPeers) {
                                speakingPeers.isNotEmpty() || speakers.any { it.isSpeaking } || ConferenceRuntime.isSpeaker
                            }
                            val pulseScale: Float = if (anyoneSpeaking) ConferencePulseScale() else 1f

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Local User Stage Card
                                item {
                                    val isLocalSpeaking = ConferenceRuntime.isSpeaker && !ConferenceRuntime.isMuted
                                    val isPinned = pinnedId == "local" || pinnedId == ConferenceRuntime.myUserId
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clickable {
                                            ConferenceService.pinParticipant(context, ConferenceRuntime.myUserId)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.size(76.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLocalSpeaking) {
                                                Box(
                                                    Modifier
                                                        .size(76.dp * pulseScale)
                                                        .clip(CircleShape)
                                                        .background(speakingRing.copy(alpha = 0.18f))
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(CircleShape)
                                                    .background(scheme.surfaceVariant)
                                                    .border(
                                                        if (isPinned) 3.dp else 2.dp,
                                                        if (isPinned) Color(0xFFB8860B) else if (isLocalSpeaking) speakingRing else scheme.outline,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("أنت", color = scheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text("أنت (${if (ConferenceRuntime.isSpeaker) "متحدث" else "مستمع"})", color = scheme.onSurface, fontSize = 12.sp)
                                    }
                                }

                                // Remote Speakers
                                items(speakers, key = { it.userId }) { speaker ->
                                    val isSpeaking = speaker.userId in speakingPeers || (speakingPeers.isEmpty() && speaker.isSpeaking)
                                    val isPinned = pinnedId == speaker.userId
                                    val isHostOrCoHost = ConferenceRuntime.participants.any { it.userId == ConferenceRuntime.myUserId && it.role in setOf("HOST", "CO_HOST") }
                                    var anchorCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { anchorCoords = it }
                                            .clickable {
                                                if (isHostOrCoHost && speaker.userId != ConferenceRuntime.myUserId) {
                                                    selectedParticipantForAction = speaker
                                                    hostActionAnchor = anchorCoords
                                                    showHostActionMenu = true
                                                } else {
                                                    ConferenceService.pinParticipant(context, speaker.userId)
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.size(76.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSpeaking) {
                                                Box(
                                                    Modifier
                                                        .size(76.dp * pulseScale)
                                                        .clip(CircleShape)
                                                        .background(speakingRing.copy(alpha = 0.18f))
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(CircleShape)
                                                    .background(scheme.surfaceVariant)
                                                    .border(
                                                        if (isPinned) 3.dp else 2.dp,
                                                        if (isPinned) Color(0xFFB8860B) else if (isSpeaking) speakingRing else if (speaker.isHost) Color(0xFFB8860B) else scheme.outline,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(speaker.userId.take(2).uppercase(), color = scheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(speaker.userId.take(10), color = if (speaker.isHost) Color(0xFFB8860B) else scheme.onSurface, fontSize = 12.sp, fontWeight = if (speaker.isHost) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            if (listeners.isNotEmpty()) {
                                Text("المستمعون (${listeners.size})", color = scheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.height(120.dp)
                                ) {
                                    items(listeners, key = { it.userId }) { listenerUser ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(CircleShape).background(scheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(listenerUser.userId.take(2).uppercase(), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                            }
                                            Text(listenerUser.userId.take(8), color = scheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Video Conference Grid or Speaker Focus Spotlight Mode
                        if (isSpeakerFocusMode && (pinnedId != null || participants.isNotEmpty())) {
                            val spotlightUserId = pinnedId ?: speakingPeers.firstOrNull() ?: participants.firstOrNull()?.userId ?: ""
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Spotlight / Pinned Big Tile
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val spotlightTrack = if (spotlightUserId == ConferenceRuntime.myUserId || spotlightUserId.isBlank()) localVideo else remoteVideos[spotlightUserId]
                                        if (spotlightTrack != null) {
                                            ConferenceVideoRenderer(track = spotlightTrack, mirror = spotlightUserId == ConferenceRuntime.myUserId, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(if (spotlightUserId.isBlank()) "البث الرئيسي" else spotlightUserId, color = scheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(10.dp)
                                                .background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("⭐️ عرض مميز", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Filmstrip of other participants
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Local thumb if not spotlighted
                                    if (spotlightUserId != ConferenceRuntime.myUserId) {
                                        Card(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { ConferenceService.pinParticipant(context, ConferenceRuntime.myUserId) },
                                            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                if (localVideo != null) ConferenceVideoRenderer(track = localVideo, mirror = true, modifier = Modifier.fillMaxSize())
                                                else Text("أنت", color = scheme.onSurface, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    // Remotes thumbs
                                    participants.forEach { p ->
                                        if (p.userId != spotlightUserId && p.userId.isNotBlank()) {
                                            Card(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { ConferenceService.pinParticipant(context, p.userId) },
                                                colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                            ) {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    val tr = remoteVideos[p.userId]
                                                    if (tr != null) ConferenceVideoRenderer(track = tr, mirror = false, modifier = Modifier.fillMaxSize())
                                                    else Text(p.userId.take(8), color = scheme.onSurface, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard Video Grid
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { ConferenceService.pinParticipant(context, ConferenceRuntime.myUserId) },
                                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (localVideo != null) {
                                                ConferenceVideoRenderer(track = localVideo, mirror = true, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("أنت", color = scheme.onSurface) }
                                            }
                                        }
                                    }
                                }

                                // DoD: سقف البلاطات 12 — الفائض عدّاد (+N) بدل renderers بلا حد.
                                val allTiles = participants.filter { it.userId.isNotBlank() }
                                val visibleTiles = allTiles.take(ConferenceRuntime.MAX_VIDEO_TILES)
                                val overflowTiles = allTiles.size - visibleTiles.size
                                items(visibleTiles, key = { it.userId }) { participant ->
                                    val track = remoteVideos[participant.userId]
                                    val isPresenting = participant.userId == ConferenceRuntime.remoteScreenSharePeerId && ConferenceRuntime.remoteScreenSharePeerId.isNotBlank()
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { ConferenceService.pinParticipant(context, participant.userId) },
                                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (participant.hasVideo && track != null) {
                                                ConferenceVideoRenderer(track = track, mirror = false, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(participant.userId.take(8), color = scheme.onSurface) }
                                            }
                                            if (isPresenting) {
                                                Text(
                                                    "🖥 يشارك الشاشة",
                                                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                        .padding(6.dp)
                                                        .background(Color(0xFF00C98C).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                // DoD: عدّاد الفائض فوق سقف البلاطات.
                                if (overflowTiles > 0) {
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(12.dp)),
                                            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    "+$overflowTiles",
                                                    color = scheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold
                                                )
                                            }
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
                            modifier = Modifier.size(36.dp).background(scheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Text(emoji, fontSize = 16.sp)
                        }
                    }
                }

                // Bottom Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surface.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leave Call Button
                    IconButton(
                        onClick = { ConferenceService.leave(context) },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFE54343), CircleShape)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "مغادرة", tint = Color.White)
                    }

                    if (ConferenceRuntime.isSpeaker) {
                        // Microphone Toggle
                        IconButton(
                            onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_MIC) },
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    if (ConferenceRuntime.isMuted) scheme.surfaceVariant else Color(0xFF6750A4),
                                    CircleShape
                                )
                                .border(1.dp, if (ConferenceRuntime.isMuted) scheme.outline else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                if (ConferenceRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "الميكروفون",
                                tint = if (ConferenceRuntime.isMuted) scheme.onSurfaceVariant else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        // Raise Hand Button
                        IconButton(
                            onClick = { ConferenceService.raiseHand(context) },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFFFB020), CircleShape)
                        ) {
                            Icon(Icons.Default.Handshake, contentDescription = "طلب التحدث", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    if (isVideoMode) {
                        // Video Camera Toggle
                        IconButton(
                            onClick = { ConferenceService.action(context, ConferenceService.ACTION_TOGGLE_VIDEO) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (!ConferenceRuntime.isVideoEnabled) scheme.surfaceVariant else scheme.primary,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (!ConferenceRuntime.isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "الكاميرا",
                                tint = if (!ConferenceRuntime.isVideoEnabled) scheme.onSurfaceVariant else Color.White
                            )
                        }

                        // Screen Share Toggle Button
                        val screenShareLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                                result.data?.let { ConferenceService.startScreenShare(context, it) } ?: android.util.Log.w("ConferenceOverlay", "screen share grant null; skip")
                            }
                        }
                        IconButton(
                            onClick = {
                                if (ConferenceRuntime.isScreenSharing) {
                                    ConferenceService.stopScreenShare(context)
                                } else {
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? android.media.projection.MediaProjectionManager
                                    if (projectionManager != null) {
                                        runCatching { screenShareLauncher.launch(projectionManager.createScreenCaptureIntent()) }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (ConferenceRuntime.isScreenSharing) Color(0xFF00C98C) else scheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (ConferenceRuntime.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                                contentDescription = "مشاركة الشاشة",
                                tint = if (ConferenceRuntime.isScreenSharing) Color.White else scheme.onSurfaceVariant
                            )
                        }
                    }

                    // Recording Toggle
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
                            .background(
                                if (ConferenceRuntime.isRecording) Color(0xFFE54343).copy(alpha = 0.15f) else scheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (ConferenceRuntime.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = "تسجيل",
                            tint = if (ConferenceRuntime.isRecording) Color(0xFFE54343) else scheme.onSurfaceVariant
                        )
                    }

                    // Chat Toggle
                    IconButton(
                        onClick = { showInCallChat = true },
                        modifier = Modifier
                            .size(52.dp)
                            .background(scheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "دردشة الاجتماع", tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Host / Moderator Action Menu
    if (showHostActionMenu) {
        selectedParticipantForAction?.let { target ->
            DropdownMenu(
                expanded = showHostActionMenu,
                onDismissRequest = { showHostActionMenu = false; selectedParticipantForAction = null; hostActionAnchor = null },
                modifier = Modifier.width(220.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("تثبيت البث / Spotlight", color = scheme.primary) },
                    onClick = {
                        ConferenceService.pinParticipant(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("منح حق التحدث", color = scheme.onSurface) },
                    onClick = {
                        ConferenceService.approveSpeaker(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("إلغاء حق التحدث", color = scheme.onSurface) },
                    onClick = {
                        ConferenceService.demoteListener(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("منح مضيف مشارك", color = scheme.onSurface) },
                    onClick = {
                        ConferenceService.grantCoHost(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("إلغاء مضيف مشارك", color = scheme.onSurface) },
                    onClick = {
                        ConferenceService.revokeCoHost(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("كتم صوت", color = scheme.onSurface) },
                    onClick = {
                        ConferenceService.muteUser(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("طرد من القاعة", color = Color(0xFFE54343)) },
                    onClick = {
                        ConferenceService.kickUser(context, target.userId)
                        showHostActionMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("إلغاء", color = scheme.onSurfaceVariant) },
                    onClick = { showHostActionMenu = false }
                )
            }
        }
    }

    // غرفة الانتظار: لوحة المضيف — تفعيل اللوبي + قائمة المنتظرين (قبول/رفض/قبول الكل)
    if (showLobbySheet) {
        AlertDialog(
            onDismissRequest = { showLobbySheet = false },
            title = { Text("غرفة الانتظار ⏳", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("طلب موافقتك قبل دخول أي مشارك", fontSize = 13.sp)
                        androidx.compose.material3.Switch(
                            checked = ConferenceRuntime.lobbyEnabled,
                            onCheckedChange = { enabled -> ConferenceService.setLobby(context, enabled) }
                        )
                    }
                    if (ConferenceRuntime.waitingUsers.isEmpty()) {
                        Text("لا يوجد منتظرون حالياً.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConferenceRuntime.waitingUsers.forEach { waitingId ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(waitingId, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { ConferenceService.approveWaiting(context, waitingId) }) {
                                            Text("قبول ✓", color = Color(0xFF14C79A))
                                        }
                                        TextButton(onClick = { ConferenceService.denyWaiting(context, waitingId) }) {
                                            Text("رفض ✕", color = Color(0xFFE54343))
                                        }
                                    }
                                }
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { ConferenceService.approveAllWaiting(context) },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("قبول الكل (${ConferenceRuntime.waitingUsers.size})", color = scheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ showLobbySheet = false }) { Text("إغلاق") }
            }
        )
    }

    // Raised Hands Sheet / Dialog
    if (showRaisedHandsSheet) {
        AlertDialog(
            onDismissRequest = { showRaisedHandsSheet = false },
            title = { Text("الأيدي المرفوعة ✋", fontWeight = FontWeight.Bold) },
            text = {
                val raised = participants.filter { it.raisedHand }
                if (raised.isEmpty()) {
                    Text("لا توجد أيدي مرفوعة حالياً.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        raised.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.userId, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            ConferenceService.approveSpeaker(context, p.userId)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("قبول متحدث", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ showRaisedHandsSheet = false }) { Text("إغلاق") }
            }
        )
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
                }) { Text("موافق — ابدأ التسجيل", color = scheme.primary) }
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
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
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
                        color = scheme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "من ${state.inviter.ifBlank { "مجموعة يونس" }}",
                        color = scheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                    Text("انضم عندما تريد — لا رنين على كل الأعضاء", color = scheme.onSurfaceVariant, fontSize = 13.sp)
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
private fun ConferencePulseScale(): Float {
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
    return pulseScale
}

@Composable
private fun ConferenceVideoRenderer(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val egl = ConferenceRuntime.eglContext ?: return
    if (track == null) return
    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    androidx.compose.runtime.key(track) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(egl, null)
                    setMirror(mirror)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                    renderer = this
                    track.addSink(this)
                }
            },
            update = { view -> if (renderer == view) track.addSink(view) },
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
}
