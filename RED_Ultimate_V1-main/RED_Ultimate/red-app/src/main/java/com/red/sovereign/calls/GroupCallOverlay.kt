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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * 👥 مكالمات المجموعات — واتساب النقي
 * زرّان منفصلان في ترويسة المجموعة: 📞 صوت و 🎥 فيديو، ترن الجميع حتى 32 مشاركاً.
 * مستقل تماماً عن المؤتمرات/المساحات/Zoom (لكل منها مساره الخاص).
 *
 * واتساب مرجع التصميم:
 * - Incoming: واجهة كاملة مع أفاتار المجموعة، اسم + عدد الأعضاء، زرّا قبول/رفض كبسولات (pill)
 * - Ringing: شبكة 3 أعمدة لحالات الأعضاء (يرن/انضم/رفض/لم يرد/مشغول) مع نبض
 * - Voice Active: شبكة دوائر للأفاتار (2-3 لكل صف)، المتحدث مضاء بحلقة خضراء نابضة، جزيرة تحكم عائمة
 * - Video Active: شبكة فيديو تكيّفية + تسليط المتحدث + جزيرة تحكم عائمة
 */
@Composable
fun GroupCallOverlay() {
    val state = GroupCallRuntime.state
    if (state is GroupCallUiState.Idle || state is GroupCallUiState.Ended) return
    // ◀️ الرجوع أثناء مكالمة جماعية لا يخرج من التطبيق — يستهلك الحدث (المكالمة تُنهى عبر زر الإنهاء فقط)
    BackHandler { /* consume */ }

    // 📱 وضع مصغّر — نافذة عائمة صغيرة (مثل واتساب)
    if (GroupCallRuntime.isMinimized && state is GroupCallUiState.Active) {
        MinimizedGroupCallBar(state)
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
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF02070E))))
        ) {
            when (state) {
                is GroupCallUiState.IncomingGroup -> WhatsAppIncomingPanel(state)
                is GroupCallUiState.Ringing       -> WhatsAppRingingPanel(state)
                is GroupCallUiState.Active        -> WhatsAppActivePanel(state)
                else -> {}
            }
        }
    }
}

// ── شريط مصغّر — نافذة عائمة قابلة للنقر للعودة ───────────────────────

@Composable
private fun MinimizedGroupCallBar(state: GroupCallUiState.Active) {
    val context = LocalContext.current
    val joinedCount = state.members.count { it.status == GroupCallMemberStatus.JOINED } + 1
    androidx.compose.ui.window.Popup(
        alignment = Alignment.BottomEnd,
        offset = androidx.compose.ui.unit.IntOffset(x = 24, y = -160)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, YounesEmerald.copy(0.5f)),
            shadowElevation = 8.dp,
            modifier = Modifier.width(180.dp).clickable { GroupCallRuntime.isMinimized = false }
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00C98C)))
                    Text(if (state.isVideo) "فيديو جماعي" else "صوت جماعي", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$joinedCount مشاركون", color = Color.White.copy(0.7f), fontSize = 10.sp)
                    WhatsAppElapsedTimer(state.startedAt)
                }
                if (state.isVideo && GroupCallRuntime.localVideo != null && GroupCallRuntime.eglContext != null) {
                    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
                    val track = GroupCallRuntime.localVideo!!
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(GroupCallRuntime.eglContext, null)
                                setMirror(true)
                                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                setEnableHardwareScaler(true)
                                renderer = this
                                track.addSink(this)
                            }
                        },
                        update = { view -> track.addSink(view) },
                        modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(10.dp))
                    )
                    DisposableEffect(track, renderer) {
                        onDispose { renderer?.let { track.removeSink(it); it.release() } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f))
                            .clickable { GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_MIC) }.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (GroupCallRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            null, tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFE53935))
                            .clickable { GroupCallService.end(context); GroupCallRuntime.isMinimized = false }.padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// Active — يوجّه للصوت أو الفيديو
// ════════════════════════════════════════════════════════════════════

@Composable
private fun WhatsAppActivePanel(state: GroupCallUiState.Active) {
    val context = LocalContext.current
    var showRecordConsent by remember { mutableStateOf(false) }
    var isSpeakerView by remember { mutableStateOf(state.members.any { it.status == GroupCallMemberStatus.JOINED }) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        WhatsAppActiveHeader(state)

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            if (state.isVideo) {
                WhatsAppVideoGrid(state, isSpeakerView, onToggleView = { isSpeakerView = !isSpeakerView })
            } else {
                WhatsAppVoiceGrid(state)
            }
        }

        WhatsAppControlIsland(
            state = state,
            isSpeakerView = isSpeakerView,
            onToggleSpeakerView = { isSpeakerView = !isSpeakerView },
            onRecordClick = {
                if (GroupCallRuntime.isRecording) GroupCallService.action(context, GroupCallService.ACTION_STOP_RECORDING)
                else showRecordConsent = true
            }
        )
    }

    if (showRecordConsent) {
        AlertDialog(
            onDismissRequest = { showRecordConsent = false },
            title = { Text("تسجيل مكالمة المجموعة", fontWeight = FontWeight.Bold) },
            text = { Text("سيُسجَّل صوتك محلياً بتشفير AES-GCM. أكّد موافقة جميع الأعضاء قبل البدء.") },
            confirmButton = {
                TextButton({
                    showRecordConsent = false
                    context.startService(
                        android.content.Intent(context, GroupCallService::class.java)
                            .setAction(GroupCallService.ACTION_START_RECORDING)
                            .putExtra(YounesCallService.EXTRA_CONSENT, true)
                    )
                }) { Text("موافق — ابدأ", color = YounesEmerald) }
            },
            dismissButton = { TextButton({ showRecordConsent = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun WhatsAppActiveHeader(state: GroupCallUiState.Active) {
    val groupName = GroupCallRuntime.activeGroupName.ifBlank { "مجموعة يونس" }
    val joinedCount = state.members.count { it.status == GroupCallMemberStatus.JOINED } + 1
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(YounesEmerald))
            Column {
                Text(groupName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.isVideo) "مكالمة فيديو جماعية" else "مكالمة صوتية جماعية",
                        color = Color.White.copy(0.7f), fontSize = 12.sp
                    )
                    WhatsAppElapsedTimer(state.startedAt)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // عدد المشاركين
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("$joinedCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            // شارة التشفير
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(YounesEmerald.copy(0.18f)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("🔒 E2EE", color = YounesEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WhatsAppElapsedTimer(startedAt: Long) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val secs = (elapsed / 1000) % 60
    val mins = (elapsed / 1000) / 60
    Text(String.format("%02d:%02d", mins, secs), color = Color.White.copy(0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

// ── Voice Grid — دوائر واتساب ─────────────────────────────────────

@Composable
private fun WhatsAppVoiceGrid(state: GroupCallUiState.Active) {
    val joined = state.members.filter { it.status == GroupCallMemberStatus.JOINED }
    // واتساب: المتحدث الحالي مضاء — نحاكي بأول غير مكتوم كـ active speaker
    val speakerId = joined.firstOrNull { !it.isMuted }?.userId

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // أنت أولاً
        item {
            WhatsAppAvatarTile(
                label = "أنت",
                initial = "أنت".take(2),
                isMuted = GroupCallRuntime.isMuted,
                isSpeaking = !GroupCallRuntime.isMuted && speakerId == null,
                isSelf = true
            )
        }
        items(joined) { member ->
            WhatsAppAvatarTile(
                label = member.displayName,
                initial = member.displayName.take(2).uppercase(),
                isMuted = member.isMuted,
                isSpeaking = member.userId == speakerId,
                isSelf = false
            )
        }
    }
}

@Composable
private fun WhatsAppAvatarTile(
    label: String,
    initial: String,
    isMuted: Boolean,
    isSpeaking: Boolean,
    isSelf: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "speak_$label")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = if (isSpeaking) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            if (isSpeaking) {
                Box(Modifier.size(96.dp).scale(pulse).clip(CircleShape).background(YounesEmerald.copy(0.18f)))
                Box(Modifier.size(86.dp).scale(pulse * 0.96f).clip(CircleShape).background(YounesEmerald.copy(0.12f)))
            }
            Box(
                Modifier.size(74.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
                    .border(if (isSpeaking) 2.dp else 1.dp, if (isSpeaking) YounesEmerald else Color.White.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initial.uppercase(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (isMuted) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Color(0xFFE53935))
                        .border(2.dp, Color(0xFF02070E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MicOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(label.take(14), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium)
        Text(if (isMuted) "مكتوم" else if (isSpeaking) "يتحدث..." else "متصل", color = if (isSpeaking) YounesEmerald else Color.White.copy(0.55f), fontSize = 10.sp)
    }
}

// ── Video Grid — شبكة واتساب + تسليط المتحدث ───────────────────────

@Composable
private fun WhatsAppVideoGrid(
    state: GroupCallUiState.Active,
    isSpeakerView: Boolean,
    onToggleView: () -> Unit
) {
    val remoteVideos = GroupCallRuntime.remoteVideos
    val localVideo = GroupCallRuntime.localVideo
    val joined = state.members.filter { it.status == GroupCallMemberStatus.JOINED }
    val speaker = joined.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        if (isSpeakerView && speaker != null) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                    GroupCallVideoTile(
                        label = speaker.displayName,
                        track = remoteVideos[speaker.userId],
                        isMuted = speaker.isMuted,
                        isMirror = false,
                        eglContext = GroupCallRuntime.eglContext,
                        fillBounds = true
                    )
                    // شارة المتحدث
                    Box(
                        Modifier.align(Alignment.TopStart).padding(10.dp)
                            .clip(RoundedCornerShape(8.dp)).background(YounesEmerald).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("يتحدث", color = Color(0xFF002118), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))) {
                        GroupCallVideoTile("أنت", localVideo, GroupCallRuntime.isMuted, true, GroupCallRuntime.eglContext, true)
                    }
                    joined.filter { it.userId != speaker.userId }.take(3).forEach { m ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))) {
                            GroupCallVideoTile(m.displayName, remoteVideos[m.userId], m.isMuted, false, GroupCallRuntime.eglContext, true)
                        }
                    }
                }
            }
        } else {
            val total = 1 + joined.size
            val cols = when {
                total <= 1 -> 1
                total <= 4 -> 2
                total <= 9 -> 3
                else -> 4
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    GroupCallVideoTile("أنت", localVideo, GroupCallRuntime.isMuted, true, GroupCallRuntime.eglContext, false)
                }
                items(joined) { m ->
                    GroupCallVideoTile(m.displayName, remoteVideos[m.userId], m.isMuted, false, GroupCallRuntime.eglContext, false)
                }
            }
        }

        // زر التبديل Spotlight/Gallery عائم صغير
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(0.45f))
                .clickable { onToggleView() }.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isSpeakerView) Icons.Default.GridView else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(if (isSpeakerView) "الشبكة" else "المتحدث", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

// ── جزيرة التحكم العائمة — واتساب ───────────────────────────────────

@Composable
private fun WhatsAppControlIsland(
    state: GroupCallUiState.Active,
    isSpeakerView: Boolean,
    onToggleSpeakerView: () -> Unit,
    onRecordClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // شريط المضيف المصغر — واتساب: لا أدوات مضيف معقدة، فقط إنهاء للجميع إن كان المضيف
        if (GroupCallRuntime.isHost && state.members.any { it.status == GroupCallMemberStatus.JOINED }) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B)).border(1.dp, YounesEmerald.copy(0.2f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = YounesEmerald, modifier = Modifier.size(14.dp))
                    Text("المضيف", color = YounesEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("كتم الكل", color = Color.White.copy(0.85f), fontSize = 11.sp, modifier = Modifier.clickable { GroupCallService.muteAll(context) })
            }
        }

        // الجزيرة الرئيسية — pill عائم ضبابي
        Box(
            Modifier.clip(RoundedCornerShape(32.dp)).background(Color(0xFF1A2332).copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(32.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IslandButton(
                    icon = if (GroupCallRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    tint = Color.White,
                    bg = if (GroupCallRuntime.isMuted) Color(0xFFE53935) else Color.White.copy(0.14f),
                    label = if (GroupCallRuntime.isMuted) "إلغاء الكتم" else "كتم"
                ) { GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_MIC) }

                // 📱 زر التصغير — واتساب: تصفح أثناء المكالمة
                IslandButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    tint = Color.White,
                    bg = Color.White.copy(0.14f),
                    label = "تصغير"
                ) { GroupCallRuntime.isMinimized = true }

                if (state.isVideo) {
                    IslandButton(
                        icon = if (GroupCallRuntime.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        bg = if (!GroupCallRuntime.isVideoEnabled) Color(0xFFE53935) else Color.White.copy(0.14f)
                    ) { GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_VIDEO) }

                    IslandButton(icon = Icons.Default.Cameraswitch, bg = Color.White.copy(0.14f)) {
                        GroupCallService.action(context, GroupCallService.ACTION_SWITCH_CAMERA)
                    }
                } else {
                    // صوت: زر السماعة — تبديل بين الأذن والسماعة الخارجية مثل واتساب
                    IslandButton(icon = Icons.Default.VolumeUp, bg = Color.White.copy(0.14f)) {
                        GroupCallService.action(context, GroupCallService.ACTION_TOGGLE_SPEAKER)
                    }
                }

                IslandButton(
                    icon = if (GroupCallRuntime.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    bg = if (GroupCallRuntime.isRecording) Color(0xFFE53935) else Color.White.copy(0.14f),
                    tint = if (GroupCallRuntime.isRecording) Color.White else Color(0xFFE53935)
                ) { onRecordClick() }

                // زر الإنهاء الكبير الأحمر — واتساب: 56dp
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE53935))
                        .clickable { GroupCallService.end(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Text(if (state.isVideo) "مكالمة فيديو جماعية · مشفّرة" else "مكالمة صوتية جماعية · مشفّرة", color = Color.White.copy(0.45f), fontSize = 10.sp)
    }
}

@Composable
private fun IslandButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    tint: Color = Color.White,
    label: String? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ── Incoming — واتساب pill ───────────────────────────────────────────

@Composable
private fun WhatsAppIncomingPanel(state: GroupCallUiState.IncomingGroup) {
    val context = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "ring")
    val pulse by infinite.animateFloat(
        initialValue = 0.95f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val groupName = GroupCallRuntime.activeGroupName.ifBlank { "مجموعة يونس" }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(YounesEmerald.copy(0.15f))
                    .border(1.dp, YounesEmerald.copy(0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(if (state.isVideo) "📹 مكالمة فيديو جماعية واردة" else "📞 مكالمة صوتية جماعية واردة", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(groupName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.hostName.ifBlank { state.hostId }.let { "$it يدعوك" }, color = Color.White.copy(0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (state.otherMembers.isNotEmpty()) {
                Text("+ ${state.otherMembers.size} آخرون في المكالمة", color = Color.White.copy(0.55f), fontSize = 13.sp)
            }
            Text("المكالمة مشفّرة E2EE", color = Color.White.copy(0.4f), fontSize = 11.sp)
        }

        // أفاتار نابض مع دوائر متحدة
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(160.dp).scale(pulse).clip(CircleShape).background(YounesEmerald.copy(0.10f)))
            Box(Modifier.size(130.dp).scale(pulse * 0.97f).clip(CircleShape).background(YounesEmerald.copy(0.14f)))
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
                    .border(2.dp, YounesEmerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(groupName.take(2).uppercase().ifBlank { "؟" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            if (state.isVideo) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(32.dp).clip(CircleShape).background(YounesEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, null, tint = Color(0xFF002118), modifier = Modifier.size(16.dp))
                }
            }
        }

        // أزرار pill واتساب — كبسولات كبيرة
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            // قبول
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(YounesEmerald)
                    .clickable { GroupCallService.accept(context, state.groupCallId, "", state.isVideo) },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color(0xFF002118), modifier = Modifier.size(20.dp))
                    Text(if (state.isVideo) "قبول فيديو" else "قبول صوت", color = Color(0xFF002118), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.10f))
                    .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(28.dp))
                    .clickable { GroupCallService.decline(context, state.groupCallId) },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("رفض", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("يمكنك الانضمام لاحقاً من داخل المجموعة حتى بعد الرفض", color = Color.White.copy(0.45f), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ── Ringing — واتساب grid ───────────────────────────────────────────

@Composable
private fun WhatsAppRingingPanel(state: GroupCallUiState.Ringing) {
    val context = LocalContext.current
    val groupName = GroupCallRuntime.activeGroupName.ifBlank { "المجموعة" }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F2A1D)).padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (state.isVideo) "📹 مكالمة فيديو جماعية" else "📞 مكالمة صوتية جماعية", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("ترن $groupName", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("جاري الاتصال بـ ${state.members.size} أشخاص — أول من يقبل يبدأ المكالمة", color = Color.White.copy(0.6f), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).padding(vertical = 18.dp)
        ) {
            items(state.members) { member ->
                WhatsAppMemberTile(member)
            }
        }

        // جزيرة إلغاء واتساب — زر أحمر كبير
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE53935))
                .clickable { GroupCallService.end(context) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text("إلغاء", color = Color.White.copy(0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WhatsAppMemberTile(member: GroupCallMember) {
    val infinite = rememberInfiniteTransition(label = "ring_${member.userId}")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = if (member.status == GroupCallMemberStatus.RINGING) 1.10f else 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )
    val (border, label, col) = when (member.status) {
        GroupCallMemberStatus.RINGING   -> Triple(YounesEmerald, "يرن...", Color(0xFFFFC107))
        GroupCallMemberStatus.JOINED    -> Triple(YounesEmerald, "انضم ✓", YounesEmerald)
        GroupCallMemberStatus.DECLINED  -> Triple(Color(0xFFE53935), "رفض", Color(0xFFE53935))
        GroupCallMemberStatus.NO_ANSWER -> Triple(Color(0xFF6B7280), "لم يرد", Color(0xFF6B7280))
        GroupCallMemberStatus.LEFT      -> Triple(Color(0xFF6B7280), "غادر", Color(0xFF6B7280))
        GroupCallMemberStatus.BUSY      -> Triple(Color(0xFFFB8C00), "مشغول", Color(0xFFFB8C00))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(68.dp).scale(pulse).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
            .border(2.dp, border, CircleShape),
            contentAlignment = Alignment.Center) {
            Text(member.displayName.take(2).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(member.displayName.take(12), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(col.copy(0.15f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(label, color = col, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Video Tile (مشترك) — مرتب ومنظم مثل واتساب ───────────────────────────
// عند وجود فيديو: يملأ البطاقة مع الحفاظ على النسبة (FILL) + حواف 12dp
// عند عدمه: دائرة أفاتار منظمة في المنتصف (64dp) مع اسم واضح، وليس نصاً ممدداً

@Composable
private fun GroupCallVideoTile(
    label: String,
    track: VideoTrack?,
    isMuted: Boolean,
    isMirror: Boolean,
    eglContext: org.webrtc.EglBase.Context?,
    fillBounds: Boolean
) {
    val modifier = if (fillBounds) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(0.85f)
    androidx.compose.material3.Card(
        modifier = modifier
            .border(
                width = if (isMuted) 0.dp else 1.dp,
                color = if (isMuted) Color.Transparent else YounesEmerald.copy(0.45f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (track != null && eglContext != null && track.enabled()) {
                var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
                // اكتمال الصوت: تأكد من تمكين المسار الصوتي المرافق
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglContext, null)
                            setMirror(isMirror)
                            // واتساب: ملء البطاقة دون تشويه — قص الحواف الزائدة
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                            renderer = this
                            track.addSink(this)
                        }
                    },
                    update = { view -> track.addSink(view) },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
                DisposableEffect(track, renderer) {
                    onDispose { renderer?.let { track.removeSink(it); it.release() } }
                }
            } else {
                // صورة/أفاتار مرتبة في المنتصف — دائرة 72dp مع تدرج + أحرف أولى واضحة
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF162544), Color(0xFF040A14)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFF23406A), Color(0xFF0F1E36))))
                                .border(2.dp, Color.White.copy(0.10f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label.take(2).uppercase().ifBlank { "؟" },
                                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            label.take(16), color = Color.White.copy(0.9f),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (track != null && !track.enabled()) {
                            // الكاميرا متوقفة مؤقتاً — شارة صغيرة
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("الكاميرا متوقفة", color = Color.White.copy(0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            // شريط الاسم + كتم مرتب أسفل البطاقة — تدرج أسود شفاف مثل واتساب
            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.72f))))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMuted) {
                    Box(Modifier.background(Color(0xFFE53935), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MicOff, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Text("مكتوم", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(YounesEmerald))
                }
                Text(label.take(14), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            // مؤشر فيديو نشط أعلى اليسار عند وجود مسار
            if (track != null && track.enabled() && eglContext != null) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFF00C98C).copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("● LIVE", color = Color(0xFF002118), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
