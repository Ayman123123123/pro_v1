package com.red.sovereign.features.lan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * P2-LAN — شاشة أقران نفس الواي فاي + مكالمة P2P.
 *
 * - بطاقة حالة الشبكة (IP/الشبكة الفرعية + تحذير عزل العملاء).
 * - قائمة الأقران المكتشفين عبر NSD مع شارة تحقق.
 * - حوار وارد (قبول صوت/فيديو/رفض) + أدوات المكالمة (كتم/كاميرا/إنهاء).
 * - عرض الفيديو عبر SurfaceViewRenderer عند توفر المسارات.
 */
@Composable
fun LanPeersScreen(
    myRedId: String,
    myName: String,
    contactRedIds: Set<String> = emptySet(),
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember(myRedId) {
        LanCallManager(
            appContext = context.applicationContext,
            myRedId = myRedId,
            myName = myName.ifBlank { "مستخدم RED" },
            contactRedIds = { contactRedIds }
        )
    }
    val peers by manager.peers.collectAsState()
    val state by manager.state.collectAsState()
    val incoming by manager.incoming.collectAsState()
    val remoteVideo by manager.remoteVideo.collectAsState()
    val lastError by manager.lastError.collectAsState()
    var videoCall by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    val net = remember(discovering) {
        runCatching { LanNet.currentWifiNet(context.applicationContext) }.getOrNull()
    }

    DisposableEffect(manager) {
        onDispose { manager.shutdown() }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "مكالمات الشبكة المحلية",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("إغلاق") }
        }

        // بطاقة الشبكة
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (net != null) Color(0xFF0B3B2E) else Color(0xFF3B1414)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (net != null) "متصل: ${net.ip}  (‎${net.prefix24}.0/24‎)"
                    else "لست على شبكة محلية — انضم لنفس الواي فاي",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "مكالمات P2P مشفرة (DTLS-SRTP) بلا إنترنت ولا خادم" +
                        if (net != null && !net.transportWifi) " — عبر كابل/مشاركة" else "",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
                if (!discovering) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { manager.startDiscovery(); discovering = true },
                        enabled = net != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C98C)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("بدء اكتشاف الأجهزة القريبة", color = Color.Black, fontWeight = FontWeight.Bold) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("يبحث عن أجهزة RED القريبة…", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        if (lastError != null) {
            Text(
                lanErrorText(lastError!!),
                color = Color(0xFFFF9E9E),
                fontSize = 12.sp
            )
            // احتياطي عزل العملاء: هوسبوت الهاتف يسمح جهاز-لجهاز — زر مباشر للإعدادات
            if (lastError == "LAN_ISOLATED") {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent("android.settings.TETHER_SETTINGS").apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                ) { Text("فتح إعدادات نقطة الاتصال (حل بديل)", fontSize = 12.sp) }
            }
        }

        // حالة المكالمة النشطة
        if (state == LanCallManager.LanCallState.CALLING) {
            Text("يتصل… بانتظار رد النظير", color = Color.White, fontSize = 13.sp)
        }
        if (state == LanCallManager.LanCallState.IN_CALL) {
            InCallPanel(
                video = videoCall,
                muted = muted,
                remoteVideo = remoteVideo,
                eglProvider = { manager.eglContext() },
                localTrackProvider = { manager.localVideo() },
                onToggleMute = {
                    muted = !muted
                    manager.setMicrophoneEnabled(!muted)
                },
                onEnd = { manager.endCall() }
            )
        }

        // قائمة الأقران
        Text("الأجهزة القريبة (${peers.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (discovering && peers.isEmpty()) {
            Text(
                "لا أجهزة بعد — تأكد أن الطرف الآخر فتح نفس الشاشة وبدأ الاكتشاف.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(peers, key = { it.redId }) { peer ->
                PeerRow(
                    peer = peer,
                    busy = state == LanCallManager.LanCallState.CALLING ||
                        state == LanCallManager.LanCallState.IN_CALL,
                    onVoice = { videoCall = false; manager.call(peer, video = false) },
                    onVideo = { videoCall = true; manager.call(peer, video = true) }
                )
            }
        }
    }

    // حوار وارد
    val inc = incoming
    if (inc != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("مكالمة واردة عبر الواي فاي") },
            text = {
                Column {
                    Text("${inc.from.name} (${inc.from.redId.take(8)}…)")
                    Text(if (inc.media == "video") "مكالمة فيديو" else "مكالمة صوتية", fontSize = 12.sp, color = Color.Gray)
                    if (!inc.from.verified) Text(
                        "غير موجود في جهات اتصالك — تحقق من هويته قبل القبول.",
                        fontSize = 12.sp,
                        color = Color(0xFFE0B551)
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { videoCall = false; manager.accept(video = false) }) { Text("قبول صوت") }
                    TextButton(onClick = { videoCall = true; manager.accept(video = true) }) { Text("قبول فيديو") }
                }
            },
            dismissButton = {
                TextButton(onClick = { manager.decline() }) { Text("رفض") }
            }
        )
    }

    LaunchedEffect(state) {
        if (state == LanCallManager.LanCallState.IDLE) {
            muted = false
        }
    }
}

@Composable
private fun PeerRow(
    peer: LanPeer,
    busy: Boolean,
    onVoice: () -> Unit,
    onVideo: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C29)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (peer.verified) Color(0xFF00C98C) else Color(0xFF4D9FE8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    peer.name.take(1).uppercase(),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(peer.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (peer.verified) {
                        Spacer(Modifier.width(6.dp))
                        Text("موثق", color = Color(0xFF00C98C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("${peer.redId.take(12)}… • ${peer.host}", color = Color.Gray, fontSize = 11.sp)
            }
            TextButton(onClick = onVoice, enabled = !busy) { Text("صوت") }
            TextButton(onClick = onVideo, enabled = !busy) { Text("فيديو") }
        }
    }
}

@Composable
private fun InCallPanel(
    video: Boolean,
    muted: Boolean,
    remoteVideo: org.webrtc.VideoTrack?,
    eglProvider: () -> org.webrtc.EglBase.Context?,
    localTrackProvider: () -> org.webrtc.VideoTrack?,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B3B2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("مكالمة LAN نشطة — P2P مشفرة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (video) {
                val egl = eglProvider()
                if (egl != null) {
                    var viewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                    val track = remoteVideo ?: localTrackProvider()
                    Surface(
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                        color = Color.Black
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    init(egl, null)
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                    setMirror(false)
                                    viewRef = this
                                }
                            },
                            update = { view -> view.setMirror(false) },
                            onRelease = { it.release() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DisposableEffect(track, viewRef) {
                        if (track != null && viewRef != null) track.addSink(viewRef)
                        onDispose { if (track != null && viewRef != null) track.removeSink(viewRef) }
                    }
                } else {
                    Text("يُجهز الفيديو…", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f)
                        .background(
                            if (muted) Color(0xFFF25C5C) else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) { Text(if (muted) "إلغاء الكتم" else "كتم", color = Color.White) }
                TextButton(
                    onClick = onEnd,
                    modifier = Modifier.weight(1f)
                        .background(Color(0xFFF25C5C), RoundedCornerShape(12.dp))
                ) { Text("إنهاء", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun lanErrorText(code: String): String = when (code) {
    "LAN_ISOLATED" -> "تعذر الوصول للنظير: الراوتر يفعّل عزل العملاء (AP isolation) — عطّله أو استخدم هوسبوت الهاتف."
    "LAN_NO_ANSWER" -> "لا رد من النظير (انتهت مهلة الرنين)."
    "LAN_TIMEOUT" -> "مهلة اتصال TCP — تحقق أنكما على نفس الشبكة."
    "LAN_PORT_BUSY" -> "منفذ الإشارة مشغول — أعد فتح الشاشة."
    "LAN_PC_FAILED" -> "تعثر الاتصال المباشر (ICE) — أعد المحاولة."
    else -> code
}
