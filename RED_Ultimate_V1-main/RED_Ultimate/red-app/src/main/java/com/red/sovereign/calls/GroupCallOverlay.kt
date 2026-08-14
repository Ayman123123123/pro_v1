package com.red.sovereign.calls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * 👥 مكالمة جماعية احترافية — نمط Zoom / iMO
 * - دعم Speaker View (المتحدث يأخذ الشاشة).
 * - دعم Gallery View (شبكة الأعضاء).
 * - أدوات تحكم المضيف (Host Controls).
 */
@Composable
fun GroupCallOverlay() {
    val state = GroupCallRuntime.state
    if (state is GroupCallUiState.Idle || state is GroupCallUiState.Ended) return

    val context = LocalContext.current

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
                .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF02080C))))
        ) {
            when (state) {
                is GroupCallUiState.IncomingGroup -> GroupCallIncomingPanel(state, context)
                is GroupCallUiState.Ringing       -> GroupCallRingingPanel(state, context)
                is GroupCallUiState.Active         -> GroupCallActivePanel(state, context)
                else -> {}
            }
        }
    }
}

// ─────────────── واجهة المكالمة النشطة ───────────────────────────────────────

@Composable
private fun GroupCallActivePanel(state: GroupCallUiState.Active, context: android.content.Context) {
    val remoteVideos = GroupCallRuntime.remoteVideos
    val localVideo = GroupCallRuntime.localVideo
    val activeMembers = state.members.filter { it.status == GroupCallMemberStatus.JOINED }
    
    // Zoom style toggle for Speaker vs Gallery
    var isSpeakerView by remember { mutableStateOf(activeMembers.isNotEmpty()) }
    var showHostControls by remember { mutableStateOf(false) }
    
    // For speaker view, we'll just pick the first remote member as the "speaker" for now,
    // or fallback to local if nobody else is there.
    val mainSpeaker = activeMembers.firstOrNull() 
    
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ─── Header bar ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00C98C)))
                Text("مكالمة جماعية (${activeMembers.size + 1})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            
            // View Toggle Button (Speaker vs Gallery)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.1f))
                        .clickable { isSpeakerView = !isSpeakerView }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSpeakerView) Icons.Default.GridView else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(if (isSpeakerView) "توسيع" else "مفرد", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ─── Video Area ───
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            if (isSpeakerView && mainSpeaker != null) {
                // Speaker View Layout
                Column(Modifier.fillMaxSize()) {
                    // Big Speaker
                    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                        GroupCallVideoTile(
                            label = mainSpeaker.displayName,
                            track = remoteVideos[mainSpeaker.userId],
                            isMuted = mainSpeaker.isMuted,
                            isMirror = false,
                            eglContext = GroupCallRuntime.eglContext,
                            fillBounds = true
                        )
                    }
                    // Small Gallery below
                    Row(
                        modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Local
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))) {
                            GroupCallVideoTile(
                                label = "أنت",
                                track = localVideo,
                                isMuted = GroupCallRuntime.isMuted,
                                isMirror = true,
                                eglContext = GroupCallRuntime.eglContext,
                                fillBounds = true
                            )
                        }
                        // Other remotes
                        activeMembers.filter { it.userId != mainSpeaker.userId }.take(3).forEach { member ->
                            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))) {
                                GroupCallVideoTile(
                                    label = member.displayName,
                                    track = remoteVideos[member.userId],
                                    isMuted = member.isMuted,
                                    isMirror = false,
                                    eglContext = GroupCallRuntime.eglContext,
                                    fillBounds = true
                                )
                            }
                        }
                    }
                }
            } else {
                // Gallery View Layout
                val totalTiles = 1 + activeMembers.size
                val columns = when {
                    totalTiles <= 1 -> 1
                    totalTiles <= 4 -> 2
                    else            -> 3
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        GroupCallVideoTile(
                            label = "أنت", track = localVideo, isMuted = GroupCallRuntime.isMuted,
                            isMirror = true, eglContext = GroupCallRuntime.eglContext, fillBounds = false
                        )
                    }
                    items(activeMembers) { member ->
                        GroupCallVideoTile(
                            label = member.displayName, track = remoteVideos[member.userId], isMuted = member.isMuted,
                            isMirror = false, eglContext = GroupCallRuntime.eglContext, fillBounds = false
                        )
                    }
                }
            }
        }

        // ─── Control bar ───
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Host Controls Strip
            if (GroupCallRuntime.isHost) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF2D2611))))
                        .border(1.dp, Color(0xFFF5C842).copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = Color(0xFFF5C842), modifier = Modifier.size(16.dp))
                        Text("أدوات المضيف", color = Color(0xFFF5C842), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("كتم الكل", color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { /* action */ })
                        Text("إنهاء للجميع", color = Color(0xFFE53935), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { GroupCallService.end(context) })
                    }
                }
            }

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (GroupCallRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (GroupCallRuntime.isMuted) "إلغاء الكتم" else "كتم",
                    color = if (GroupCallRuntime.isMuted) Color(0xFFE53935) else Color.White.copy(0.2f),
                    onClick = { GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_MIC) }
                )
                if (state.isVideo) {
                    CallControlButton(
                        icon = if (GroupCallRuntime.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = if (GroupCallRuntime.isVideoEnabled) "إيقاف الكاميرا" else "تشغيل الكاميرا",
                        color = if (!GroupCallRuntime.isVideoEnabled) Color(0xFFE53935) else Color.White.copy(0.2f),
                        onClick = { GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_VIDEO) }
                    )
                    CallControlButton(
                        icon = Icons.Default.Cameraswitch,
                        label = "تبديل",
                        color = Color.White.copy(0.2f),
                        onClick = { GroupCallService.action(context, GroupCallService.ACTION_SWITCH_CAMERA) }
                    )
                }
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "مغادرة",
                    color = Color(0xFFE53935),
                    size = 64,
                    onClick = { GroupCallService.end(context) }
                )
            }
        }
    }
}

@Composable
private fun GroupCallVideoTile(
    label: String,
    track: VideoTrack?,
    isMuted: Boolean,
    isMirror: Boolean,
    eglContext: org.webrtc.EglBase.Context?,
    fillBounds: Boolean
) {
    val modifier = if (fillBounds) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(0.75f)
    
    Card(
        modifier = modifier.border(if (isMuted) 0.dp else 2.dp, if (isMuted) Color.Transparent else Color(0xFF00C98C), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (track != null && eglContext != null) {
                var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglContext, null)
                            setMirror(isMirror)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            renderer = this
                            track.addSink(this)
                        }
                    },
                    update = { view -> track.addSink(view) },
                    modifier = Modifier.fillMaxSize()
                )
                DisposableEffect(track, renderer) {
                    onDispose { renderer?.let { track.removeSink(it); it.release() } }
                }
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF060D1A)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label.take(2).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // Name + Mute badge overlay
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMuted) {
                    Box(Modifier.background(Color(0xFFE53935), RoundedCornerShape(4.dp)).padding(2.dp)) {
                        Icon(Icons.Default.MicOff, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─────────────── واجهة الدعوة الواردة ────────────────────────────────────────

@Composable
private fun GroupCallIncomingPanel(state: GroupCallUiState.IncomingGroup, context: android.content.Context) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.background(if (state.isVideo) Color(0xFF1B3A2A) else Color(0xFF1B1B40), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (state.isVideo) "📹 مكالمة فيديو جماعية" else "📞 مكالمة صوتية جماعية",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(state.hostName.ifBlank { state.hostId }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (state.otherMembers.isNotEmpty()) {
                Text("+ ${state.otherMembers.size} آخرون", color = Color.White.copy(0.6f), fontSize = 14.sp)
            }
            Text("يدعوك للانضمام...", color = Color(0xFF00C98C), fontSize = 14.sp)
        }

        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(Color(0x2200C98C)))
            Box(Modifier.size(110.dp).scale(pulse * 0.9f).clip(CircleShape).background(Color(0x3300C98C)))
            Box(
                Modifier.size(90.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
                    .border(2.dp, Color(0xFF00C98C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(state.hostName.take(2).uppercase().ifBlank { "?" },
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
            CallControlButton(Icons.Default.CallEnd, "رفض", Color(0xFFE53935), 70) { GroupCallService.decline(context, state.groupCallId) }
            CallControlButton(if (state.isVideo) Icons.Default.Videocam else Icons.Default.Mic, "قبول", Color(0xFF00C98C), 70) { 
                GroupCallService.accept(context, state.groupCallId, "", state.isVideo) 
            }
        }
    }
}

// ─────────────── واجهة الانتظار (المضيف يرن) ─────────────────────────────────

@Composable
private fun GroupCallRingingPanel(state: GroupCallUiState.Ringing, context: android.content.Context) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.isVideo) "📹 مكالمة فيديو جماعية" else "📞 مكالمة صوتية جماعية",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("جاري الاتصال بـ ${state.members.size} أشخاص...", color = Color.White.copy(0.6f), fontSize = 13.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).padding(vertical = 20.dp)
        ) {
            items(state.members) { member ->
                MemberStatusTile(member)
            }
        }

        CallControlButton(Icons.Default.CallEnd, "إلغاء", Color(0xFFE53935), 70) { GroupCallService.end(context) }
    }
}

@Composable
private fun MemberStatusTile(member: GroupCallMember) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_${member.userId}")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )
    val (borderColor, statusText, statusColor) = when (member.status) {
        GroupCallMemberStatus.RINGING   -> Triple(Color(0xFFFFC107), "يرن...",    Color(0xFFFFC107))
        GroupCallMemberStatus.JOINED    -> Triple(Color(0xFF00C98C), "انضم ✓",   Color(0xFF00C98C))
        GroupCallMemberStatus.DECLINED  -> Triple(Color(0xFFE53935), "رفض",      Color(0xFFE53935))
        GroupCallMemberStatus.NO_ANSWER -> Triple(Color(0xFF9E9E9E), "لم يرد",   Color(0xFF9E9E9E))
        GroupCallMemberStatus.LEFT      -> Triple(Color(0xFF9E9E9E), "غادر",     Color(0xFF9E9E9E))
    }
    val scale = if (member.status == GroupCallMemberStatus.RINGING) pulse else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(72.dp).scale(scale).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
            .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(member.displayName.take(2).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(member.displayName.take(10), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    size: Int = 52,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(size.dp).background(color, CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
        }
        Text(label, color = Color.White.copy(0.7f), fontSize = 10.sp)
    }
}
