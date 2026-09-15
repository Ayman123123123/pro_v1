package com.red.sovereign.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.random.Random

/**
 * 🎬 بث مباشر احترافي — نمط TikTok بالكامل!
 * مؤثرات بصرية مذهلة، تدرجات لونية، شات شفاف يتلاشى تدريجياً، وأمواج من القلوب.
 */

/** الهدايا معطلة بقرار المنتج — الشيفرة (محفظة/إشارة GIFT) تبقى سليمة خلف هذا العلم. */
private const val GIFTS_ENABLED = false

@Composable
fun YounesLiveStreamOverlay() {
    val state = LiveStreamRuntime.state
    if (state is LiveStreamUiState.Idle) return

    val context = LocalContext.current
    val localVideo = LiveStreamRuntime.localVideo
    val remoteVideo = LiveStreamRuntime.remoteVideo
    var chatText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<LiveChatMessage?>(null) }
    var showRaisedHandsSheet by remember { mutableStateOf(false) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showHostControls by remember { mutableStateOf(false) }
    var inviteRedId by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val hapticView = androidx.compose.ui.platform.LocalView.current
    fun tapHaptic(kind: Int = android.view.HapticFeedbackConstants.VIRTUAL_KEY) {
        runCatching { hapticView.performHapticFeedback(kind) }
    }
    // بوابة الصلاحيات داخل البث — إعادة طلب الكاميرا/الميكروفون دون مغادرة البث
    var permissionTick by remember { mutableStateOf(0) }
    val mediaPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTick++
        LiveStreamService.retryMedia(context)
    }
    fun hasCam(): Boolean {
        permissionTick.let {}
        return androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun hasMic(): Boolean {
        permissionTick.let {}
        return androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun requestMediaPermissions() {
        mediaPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
    }
    // اسم المرسِل الحقيقي للدردشة والهدايا: جهات الاتصال ثم اسم
    // المستخدم ثم المعرف — لا "أنا" الثابتة التي كانت تظهر للجميع.
    val directory: DirectoryViewModel = viewModel()
    val tokens = remember(context) { TokenStore(context.applicationContext) }
    val myRedId = tokens.redId.orEmpty()
    val myName = directory.contacts.firstOrNull { it.redId == myRedId }?.displayName
        ?: tokens.username?.takeIf { it.isNotBlank() }
        ?: myRedId

    if (state is LiveStreamUiState.Incoming) {
        LiveIncomingCard(state)
        return
    }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    val isBcast = when (state) {
        is LiveStreamUiState.Connecting -> state.isBroadcaster
        is LiveStreamUiState.Active -> state.isBroadcaster
        else -> false
    }

    // LEGENDARY FIX (خطأ صامت): حالة Error كانت تُبنى ولا تُعرض أبداً ثم تُغلق بعد 3s —
    // الآن بطاقة عربية صريحة بأزرار فعل (إعادة/صوت فقط/مغادرة/نسخ التفاصيل).
    if (state is LiveStreamUiState.Error) {
        val arabic = when {
            state.message.contains("TIMEOUT") -> "تعذر الاتصال بالبث خلال 15 ثانية — تحقق من الشبكة"
            state.message.contains("PERMISSION") -> "إذن الكاميرا/الميكروفون مطلوب للبث"
            state.message.contains("REGISTRATION") -> "تعذر تسجيل البث في الخادم"
            else -> "تعذر بدء البث (${state.message.take(60)})"
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("تعذر البث المباشر") },
            text = { androidx.compose.material3.Text(arabic) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    LiveStreamService.retryMedia(context)
                }) { androidx.compose.material3.Text("إعادة المحاولة") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = {
                        LiveStreamService.setQuality(context, LiveQuality.AUDIO_ONLY)
                    }) { androidx.compose.material3.Text("صوت فقط") }
                    androidx.compose.material3.TextButton(onClick = {
                        LiveStreamService.stop(context)
                    }) { androidx.compose.material3.Text("مغادرة") }
                }
            }
        )
        return
    }

    Dialog(
        // LEGENDARY FIX (عالق مثبت على الجهاز): زر الرجوع كان محبوساً تماماً
        // (dismissOnBackPress=false + onDismiss={}) في بث أسود بلا مخرج —
        // الآن يفتح تأكيد مغادرة بدل الحبس (المذيع يُحذَّر أن المغادرة تنهي البث).
        onDismissRequest = { showLeaveConfirm = true },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // ضغطة مزدوجة = ❤️ مثل TikTok (تفاعل مجاني — بدون هدايا)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            tapHaptic(android.view.HapticFeedbackConstants.LONG_PRESS)
                            LiveStreamService.sendReaction(context, listOf("❤️", "🔥", "😂", "✨", "👏").random())
                        }
                    )
                }
        ) {
            val isBroadcaster = when (state) {
                is LiveStreamUiState.Connecting -> state.isBroadcaster
                is LiveStreamUiState.Active -> state.isBroadcaster
                else -> false
            }

            // ─── 1. خلفية الفيديو الرئيسية ملء الشاشة (صوت فقط = غلاف صوتي) ───
            if (LiveStreamRuntime.isAudioOnly) {
                Box(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(110.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(SovereignColors.LiveContainer, SovereignColors.Cyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎙", fontSize = 48.sp)
                        }
                        Text("بث صوتي مباشر", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("🎞 الجودة: صوت فقط — وفّر البيانات", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else if (isBroadcaster && localVideo != null) {
                LiveStreamVideoRenderer(track = localVideo, mirror = true, modifier = Modifier.fillMaxSize())
            } else if (!isBroadcaster && remoteVideo != null) {
                LiveStreamVideoRenderer(track = remoteVideo, mirror = false, modifier = Modifier.fillMaxSize())
            } else {
                // بطاقة انتظار قابلة للفعل — لا spinner صامت ولا شاشة سوداء
                val camErr = LiveStreamRuntime.cameraError
                val audErr = LiveStreamRuntime.audioError
                val connecting = state is LiveStreamUiState.Connecting
                Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(0.86f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.06f))
                            .padding(20.dp)
                    ) {
                        if (camErr == null && audErr == null) {
                            CircularProgressIndicator(color = SovereignColors.LiveContainer)
                            Text(
                                if (connecting && !isBroadcaster) "بانتظار فيديو المذيع... تحقق من الشبكة"
                                else "جارٍ معالجة البث الفائق...",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        if (camErr == "PERMISSION" && isBroadcaster) {
                            Text("📷 إذن الكاميرا مرفوض", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("البث يعمل صوت فقط الآن. امنح الإذن لتشغيل الفيديو.", color = Color.Gray, fontSize = 13.sp)
                            Button(
                                onClick = { requestMediaPermissions() },
                                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("منح إذن الكاميرا والميكروفون") }
                        } else if (camErr == "UNAVAILABLE" && isBroadcaster) {
                            Text("📷 الكاميرا غير متاحة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("قد تكون مشغولة بتطبيق آخر أو غير مدعومة — أنت تبث صوت فقط.", color = Color.Gray, fontSize = 13.sp)
                            Button(
                                onClick = { LiveStreamService.retryMedia(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إعادة محاولة الكاميرا", color = Color.Black) }
                        }
                        if (audErr == "PERMISSION" && isBroadcaster) {
                            Text("🎙 إذن الميكروفون مرفوض — صوتك لا يصل!", color = Color(0xFFFF9800), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { requestMediaPermissions() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("منح إذن الميكروفون") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { LiveStreamService.retryMedia(context) },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("🔄 إعادة المحاولة", color = Color.White, fontSize = 12.sp) }
                            if (!isBroadcaster) {
                                OutlinedButton(
                                    onClick = { LiveStreamService.setQuality(context, LiveQuality.AUDIO_ONLY) },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("🎞 صوت فقط", color = Color.White, fontSize = 12.sp) }
                            }
                            TextButton(onClick = { LiveStreamService.stop(context) }) { Text("مغادرة", color = Color.Gray) }
                        }
                        if (!hasCam() && isBroadcaster && camErr == null) {
                            TextButton(onClick = { requestMediaPermissions() }) { Text("طلب صلاحيات الكاميرا/الصوت", color = SovereignColors.Cyan, fontSize = 12.sp) }
                        }
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
                        Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SovereignColors.LiveContainer, SovereignColors.Cyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(hostName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column {
                        Text(hostName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("مضيف 👑", color = Color(0xFFF5C842), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Stats Cluster (Viewers clickable, Close)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Viewer Count — قابل للنقر لعرض قائمة المشاهدين (عين)
                    Box(
                        Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .clickable { showViewersSheet = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("${LiveStreamRuntime.viewerCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(12.dp))
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

            // ─── 3b. شبكة المضيفين المشاركين حتى 4 (TikTok Multi-guest) ───
            // coHostVideos الجديدة + fallback لـ coHostVideo القديم للتوافق.
            val coHosts = remember(LiveStreamRuntime.coHostVideos, LiveStreamRuntime.coHostVideo) {
                val m = LiveStreamRuntime.coHostVideos.toList().take(4)
                if (m.isNotEmpty()) m else LiveStreamRuntime.coHostVideo?.let { listOf("cohost" to it) } ?: emptyList()
            }
            if (coHosts.isNotEmpty()) {
                if (coHosts.size == 1) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 88.dp, end = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("مضيف مشارك", color = Color(0xFFF5C842), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        LiveStreamVideoRenderer(
                            track = coHosts[0].second,
                            mirror = false,
                            modifier = Modifier.size(110.dp, 150.dp).clip(RoundedCornerShape(10.dp))
                        )
                        if (isBroadcaster) {
                            Text(
                                "إزالة",
                                color = SovereignColors.LiveContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { LiveStreamService.removeCoHost(context, coHosts[0].first) }.padding(4.dp)
                            )
                        }
                    }
                } else {
                    // شبكة 2x2 للمضيفين المتعددين
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 88.dp, end = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("الضيوف (${coHosts.size}/4)", color = Color(0xFFF5C842), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        for (row in coHosts.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for ((uid, track) in row) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        LiveStreamVideoRenderer(
                                            track = track,
                                            mirror = false,
                                            modifier = Modifier.size(84.dp, 110.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                        if (isBroadcaster) {
                                            Text(
                                                "✕",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier
                                                    .background(SovereignColors.LiveContainer, CircleShape)
                                                    .clickable { LiveStreamService.removeCoHost(context, uid) }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                }
            }
        }
    }

    // LEGENDARY: تأكيد مغادرة بزر الرجوع (كان محبوساً بلا مخرج في بث أسود)
    if (showLeaveConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { androidx.compose.material3.Text(if (isBcast) "إنهاء البث؟" else "مغادرة البث؟") },
            text = {
                androidx.compose.material3.Text(
                    if (isBcast) "المغادرة تُنهي البث لجميع المشاهدين."
                    else "يمكنك العودة من قائمة البثوث النشطة."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLeaveConfirm = false
                    LiveStreamService.stop(context)
                }) { androidx.compose.material3.Text(if (isBcast) "إنهاء" else "مغادرة") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLeaveConfirm = false }) {
                    androidx.compose.material3.Text("بقاء")
                }
            }
        )
    }
}
                    }
                }
            }

            // ─── 3c. التعليق المثبّت + مؤشرات البث الأسطورية ───
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LiveStreamRuntime.pinnedMessage?.let { pinned ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5C842).copy(alpha = 0.92f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📌", fontSize = 13.sp)
                        Text(
                            "${pinned.senderName}: ${pinned.text}",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isBroadcaster) {
                            Text(
                                "✕",
                                color = Color.Black.copy(0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { LiveStreamService.unpinMessage(context) }.padding(2.dp)
                            )
                        }
                    }
                }
                // شريط حالة: المدة + الجودة + الوضع البطيء + التسجيل
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (LiveStreamRuntime.streamStartTime > 0L) {
                        var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(1000)
                                nowTick = System.currentTimeMillis()
                            }
                        }
                        val secs = ((nowTick - LiveStreamRuntime.streamStartTime) / 1000).coerceAtLeast(0)
                        val mm = "%02d:%02d".format(secs / 60, secs % 60)
                        Box(Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("⏱ $mm", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))
                            .clickable { showQualitySheet = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("🎞 ${LiveStreamRuntime.quality.label}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (LiveStreamRuntime.slowModeSec > 0) {
                        Box(Modifier.background(Color(0xFFFF9800).copy(0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("🐢 بطيء ${LiveStreamRuntime.slowModeSec}ث", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (LiveStreamRuntime.isRecording) {
                        Box(Modifier.background(Color.Red.copy(0.9f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("● REC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // زر الإحصائيات
                    Box(
                        Modifier.background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))
                            .clickable { LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_STATS) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("📊", fontSize = 11.sp)
                    }
                }
                // لوحة الإحصائيات (bitrate/rtt/المشاهدين) — من networkStats الموجودة
                if (LiveStreamRuntime.showStats) {
                    val ns = LiveStreamRuntime.networkStats
                    val kbps = if (ns.availableBitrateKbps > 0) ns.availableBitrateKbps else ns.bandwidthKbps
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(0.65f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "👁 ${LiveStreamRuntime.viewerCount} • 📶 ${kbps}kbps • ⏱ ${ns.rttMs}ms • ❌ ${"%.1f".format(ns.packetLossPercent)}%",
                            color = SovereignColors.Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
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
                        // scrollToItem (not animate) + runCatching: chat shrinks on
                        // moderation/kick and animate throws IndexOutOfBounds.
                        runCatching { listState.scrollToItem(messages.lastIndex) }
                    }
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val quoted = remember(msg.replyToId, messages) {
                            msg.replyToId?.let { rid -> messages.firstOrNull { it.id == rid } }
                        }
                        Row(
                            Modifier.background(Brush.horizontalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent)), RoundedCornerShape(16.dp))
                                .clickable { replyTo = msg }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                quoted?.let { q ->
                                    Text(
                                        "↩ ${q.senderName}: ${q.text.take(60)}",
                                        color = SovereignColors.Cyan,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${msg.senderName}: ",
                                        color = Color(0xFFC0C0C0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = msg.text,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                            // تثبيت وحذف سريع للمذيع
                            if (isBroadcaster) {
                                Text(
                                    "📌",
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        LiveStreamService.pinMessage(context, msg.id, msg.senderName, msg.text)
                                    }.padding(2.dp)
                                )
                                Text(
                                    "🗑",
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        LiveStreamService.deleteChat(context, msg.id)
                                    }.padding(2.dp)
                                )
                            }
                        }
                    }
                }

                // Fading effect for top of chat
                Box(
                    Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
                )
            }

            // ─── 6. شريط الأدوات السفلي (مع شريط الرد) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // شريط الرد — يظهر عند النقر على رسالة (TikTok reply)
                replyTo?.let { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SovereignColors.Cyan.copy(0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "↩ الرد على ${r.senderName}: ${r.text.take(50)}",
                            color = SovereignColors.Cyan,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("✕", color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { replyTo = null }.padding(2.dp))
                    }
                }
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
                        placeholder = { Text(if (replyTo != null) "رد..." else "أضف تعليقاً...", color = Color.White.copy(0.7f), fontSize = 13.sp) },
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
                                LiveStreamService.sendChat(context, chatText.trim(), myName, replyTo?.id)
                                chatText = ""
                                replyTo = null
                            }
                        })
                    )

                    // Action Icons (Right side) — منظمة: دعوة + مشاركة + إدارة أسطورية
                    if (isBroadcaster) {
                        ActionIcon(Icons.Default.PersonAdd, "دعوة") { showInviteSheet = true }
                        ActionIcon(
                            Icons.Default.Cameraswitch,
                            if (LiveStreamRuntime.localVideo == null) "إعادة محاولة الكاميرا" else "تبديل الكاميرا"
                        ) {
                            if (LiveStreamRuntime.localVideo == null) LiveStreamService.retryMedia(context)
                            else LiveStreamService.action(context, LiveStreamService.ACTION_SWITCH_CAMERA)
                        }
                        ActionIcon(
                            icon = if (LiveStreamRuntime.isMuted || LiveStreamRuntime.audioError != null) Icons.Default.MicOff else Icons.Default.Mic,
                            desc = "صوت",
                            color = if (LiveStreamRuntime.isMuted || LiveStreamRuntime.audioError != null) SovereignColors.LiveContainer else Color.White
                        ) {
                            LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_MIC)
                        }
                        // زر الأيادي المرفوعة مع شارة العدد (كان ميتاً showRaisedHandsSheet)
                        Box(contentAlignment = Alignment.Center) {
                            ActionIcon(Icons.Default.PanTool, "طلبات الصعود (${LiveStreamRuntime.raisedHands.size})") {
                                showRaisedHandsSheet = true
                            }
                            if (LiveStreamRuntime.raisedHands.isNotEmpty()) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(18.dp)
                                        .background(Color.Red, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${LiveStreamRuntime.raisedHands.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        // لوحة المذيع (جودة/بطيء/تسجيل/تثبيت)
                        ActionIcon(Icons.Default.Settings, "تحكم المذيع") { showHostControls = true }
                    } else {
                        ActionIcon(Icons.Default.PersonAdd, "دعوة") { showInviteSheet = true }
                        // طلب صعود للمشاهد (Raise Hand) — كان بلا زر
                        val alreadyRaised = LiveStreamRuntime.raisedHands.any { it.userId == myRedId } || LiveStreamRuntime.isCoHost
                        ActionIcon(
                            Icons.Default.PanTool,
                            if (LiveStreamRuntime.isCoHost) "مضيف مشارك" else "طلب صعود",
                            color = if (alreadyRaised) Color(0xFFF5C842) else Color.White
                        ) {
                            if (LiveStreamRuntime.isCoHost) {
                                LiveStreamService.action(context, LiveStreamService.ACTION_LEAVE_COHOST)
                            } else if (!alreadyRaised) {
                                LiveStreamService.raiseHand(context, myName)
                                android.widget.Toast.makeText(context, "تم إرسال طلب الصعود للمذيع ✋", android.widget.Toast.LENGTH_SHORT).show()
                            }
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
                            .background(Brush.linearGradient(listOf(SovereignColors.LiveContainer, Color(0xFFFF0055))))
                            .clickable {
                                tapHaptic()
                                LiveStreamService.sendReaction(context, listOf("❤️", "🔥", "😂", "✨").random())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "إعجاب", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // شيت المشاهدين — عند الضغط على العين (مع طرد/كتم للمذيع)
            if (showViewersSheet) {
                ViewersSheet(
                    viewerCount = LiveStreamRuntime.viewerCount,
                    viewerIds = LiveStreamRuntime.viewerIds,
                    resolveName = { id ->
                        directory.contacts.firstOrNull { it.redId == id }?.displayName
                            ?: LiveStreamRuntime.viewerNames[id]
                            ?: "زائر ${id.takeLast(4)}"
                    },
                    isBroadcaster = isBroadcaster,
                    onDismiss = { showViewersSheet = false },
                    onInvite = { showViewersSheet = false; showInviteSheet = true },
                    onKick = { id -> LiveStreamService.kickViewer(context, id) },
                    onMute = { id -> LiveStreamService.muteViewer(context, id, true) },
                    onPinChat = { msg -> LiveStreamService.pinMessage(context, msg.id, msg.senderName, msg.text) }
                )
            }
            // شيت الأيادي المرفوعة — كان معلناً ولا يُستخدم (إصلاح P0)
            if (showRaisedHandsSheet) {
                RaisedHandsSheet(
                    hands = LiveStreamRuntime.raisedHands,
                    onDismiss = { showRaisedHandsSheet = false },
                    onApprove = { id ->
                        LiveStreamService.approveCoHost(context, id)
                        showRaisedHandsSheet = false
                    },
                    onReject = { id -> LiveStreamService.rejectCoHost(context, id) }
                )
            }
            // شيت الجودة — تبديل 360p-1080p + صوت فقط (إصلاح البطء)
            if (showQualitySheet) {
                QualitySheet(
                    current = LiveStreamRuntime.quality,
                    onDismiss = { showQualitySheet = false },
                    onSelect = { q ->
                        LiveStreamService.setQuality(context, q)
                        showQualitySheet = false
                    }
                )
            }
            // لوحة المذيع الأسطورية — بطيء/تسجيل/تثبيت/كلمات محظورة
            if (showHostControls && isBroadcaster) {
                var blockedWords by remember { mutableStateOf("") }
                HostControlsSheet(
                    slowModeSec = LiveStreamRuntime.slowModeSec,
                    isRecording = LiveStreamRuntime.isRecording,
                    isMuted = LiveStreamRuntime.isMuted,
                    blockedWords = blockedWords,
                    onBlockedWordsChange = { blockedWords = it },
                    onSaveWords = {
                        val sid = when (state) {
                            is LiveStreamUiState.Connecting -> state.streamId
                            is LiveStreamUiState.Active -> state.streamId
                            else -> ""
                        }
                        if (sid.isNotBlank()) {
                            val ctx = context
                            val words = blockedWords
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val api = com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(ctx))
                                    val body = org.json.JSONObject()
                                        .put("action", "WORDS")
                                        .put("value", words)
                                        .toString()
                                    api.request("POST", "/api/livestream/$sid/moderate", body)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(ctx, "تم حفظ الكلمات المحظورة", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(ctx, "تعذر الحفظ", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onSlowMode = { sec -> LiveStreamService.setSlowMode(context, sec) },
                    onToggleMic = { LiveStreamService.action(context, LiveStreamService.ACTION_TOGGLE_MIC) },
                    onToggleRecording = {
                        if (LiveStreamRuntime.isRecording) LiveStreamService.action(context, LiveStreamService.ACTION_STOP_RECORDING)
                        else {
                            val i = android.content.Intent(context, LiveStreamService::class.java).apply {
                                action = LiveStreamService.ACTION_START_RECORDING
                                putExtra(YounesCallService.EXTRA_CONSENT, true)
                            }
                            androidx.core.content.ContextCompat.startForegroundService(context, i)
                        }
                    },
                    onUnpin = { LiveStreamService.unpinMessage(context) },
                    onDismiss = { showHostControls = false }
                )
            }
            // شيت الدعوة من داخل البث — أصدقاء أو رابط
            if (showInviteSheet) {
                InviteFromLiveSheet(
                    streamId = when (state) {
                        is LiveStreamUiState.Connecting -> state.streamId
                        is LiveStreamUiState.Active -> state.streamId
                        else -> ""
                    },
                    inviteRedId = inviteRedId,
                    onRedIdChange = { inviteRedId = it },
                    onDismiss = { showInviteSheet = false; inviteRedId = "" },
                    onInviteFriend = {
                        val sid = when (state) {
                            is LiveStreamUiState.Connecting -> state.streamId
                            is LiveStreamUiState.Active -> state.streamId
                            else -> ""
                        }
                        if (inviteRedId.isNotBlank() && sid.isNotBlank()) {
                            // دعوة عبر الخادم — استدعاء API مباشر
                            val ctx = context
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val api = com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(ctx))
                                    val body = org.json.JSONObject().put("friendIds", org.json.JSONArray().put(inviteRedId.trim())).toString()
                                    api.request("POST", "/api/livestream/$sid/invite", body)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(ctx, "تم إرسال الدعوة إلى $inviteRedId", android.widget.Toast.LENGTH_SHORT).show()
                                        showInviteSheet = false; inviteRedId = ""
                                    }
                                } catch (_: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(ctx, "تعذر إرسال الدعوة", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onCopyLink = {
                        val sid = when (state) {
                            is LiveStreamUiState.Connecting -> state.streamId
                            is LiveStreamUiState.Active -> state.streamId
                            else -> ""
                        }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("younes://livestream/$sid"))
                        android.widget.Toast.makeText(context, "تم نسخ رابط البث: younes://livestream/$sid", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewersSheet(
    viewerCount: Int,
    viewerIds: List<String>,
    resolveName: (String) -> String,
    isBroadcaster: Boolean,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onKick: (String) -> Unit = {},
    onMute: (String) -> Unit = {},
    onPinChat: (LiveChatMessage) -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                // استهلاك النقر دون إعلان "زر معطل" لقارئ الشاشة:
                // clickable(enabled=false) تنشر semantics زر معطل؛ pointerInput يبتلع
                // النقر بصمت فلا يصل لطبقة الإغلاق الخارجية ولا يُعلن كزر.
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).pointerInput(Unit) { detectTapGestures { } },
                color = Color(0xFF0F172A)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("المشاهدون ($viewerCount)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).background(Color.White.copy(0.1f), CircleShape)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (viewerIds.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (viewerCount == 0) "لا يوجد مشاهدون بعد — ادعُ أصدقاءك!"
                                else "انضم $viewerCount مشاهِد — تظهر الأسماء فور تفاعلهم أو إضافتهم كأصدقاء",
                                color = Color.Gray, fontSize = 13.sp
                            )
                        }
                    } else {
                        // قائمة حقيقية: الاسم من جهات الاتصال ثم من رسائل
                        // الشات (VIEWER_JOINED يحمل المعرف فقط) ثم مقتطع المعرف.
                        // بحث المشاهدين — مفيد للبثوث الكبيرة.
                        var viewerQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = viewerQuery,
                            onValueChange = { viewerQuery = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            placeholder = { Text("بحث عن مشاهد...", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(0.06f),
                                unfocusedContainerColor = Color.White.copy(0.04f),
                                focusedBorderColor = SovereignColors.LiveContainer,
                                unfocusedBorderColor = Color.White.copy(0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        val shownIds = remember(viewerIds, viewerQuery) {
                            if (viewerQuery.isBlank()) viewerIds
                            else viewerIds.filter {
                                it.contains(viewerQuery, ignoreCase = true) ||
                                    resolveName(it).contains(viewerQuery, ignoreCase = true)
                            }
                        }
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(shownIds, key = { it }) { id ->
                                val name = resolveName(id)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(SovereignColors.LiveContainer, SovereignColors.Cyan))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(id, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isBroadcaster) {
                                        // إدارة المذيع: كتم + طرد (كان عرض فقط بأيقونة Person)
                                        Text(
                                            "🔇",
                                            fontSize = 16.sp,
                                            modifier = Modifier.clickable { onMute(id) }.padding(4.dp)
                                        )
                                        Text(
                                            "⛔",
                                            fontSize = 16.sp,
                                            modifier = Modifier.clickable { onKick(id) }.padding(4.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (viewerCount > viewerIds.size) {
                            Text(
                                "+${viewerCount - viewerIds.size} مشاهِد آخر لم تصل بياناته بعد",
                                color = Color.Gray, fontSize = 12.sp
                            )
                        }
                    }
                    Button(onClick = onInvite, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveContainer), shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("دعوة أصدقاء / نسخ الرابط", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RaisedHandsSheet(
    hands: List<RaisedHandUser>,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).pointerInput(Unit) { detectTapGestures { } },
                color = Color(0xFF0F172A)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("✋ طلبات الصعود (${hands.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).background(Color.White.copy(0.1f), CircleShape)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (hands.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد طلبات — سيظهر هنا من يرفع يده للصعود كمضيف مشارك", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(hands, key = { it.userId }) { h ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier.size(42.dp).clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Color(0xFFF5C842), SovereignColors.LiveContainer))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(h.userName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(h.userName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(h.userId, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = { onApprove(h.userId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("قبول", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    TextButton(onClick = { onReject(h.userId) }) { Text("رفض", color = Color.Gray, fontSize = 12.sp) }
                                }
                            }
                        }
                        Text("الحد الأقصى 4 مضيفين مشاركين في نفس الوقت", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySheet(
    current: LiveQuality,
    onDismiss: () -> Unit,
    onSelect: (LiveQuality) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).pointerInput(Unit) { detectTapGestures { } },
                color = Color(0xFF0F172A)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🎞 جودة المشاهدة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("التلقائي يختار الأفضل حسب شبكتك. اختر جودة منخفضة لتوفير البيانات وتقليل التقطيع.", color = Color.Gray, fontSize = 12.sp)
                    LiveQuality.entries.forEach { q ->
                        val selected = q == current
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selected) SovereignColors.LiveContainer.copy(0.2f) else Color.White.copy(0.06f))
                                .clickable { onSelect(q) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(q.label, color = if (selected) SovereignColors.LiveContainer else Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                            if (selected) Text("✓", color = SovereignColors.LiveContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostControlsSheet(
    slowModeSec: Int,
    isRecording: Boolean,
    isMuted: Boolean,
    blockedWords: String,
    onBlockedWordsChange: (String) -> Unit,
    onSaveWords: () -> Unit,
    onSlowMode: (Int) -> Unit,
    onToggleMic: () -> Unit,
    onToggleRecording: () -> Unit,
    onUnpin: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).pointerInput(Unit) { detectTapGestures { } },
                color = Color(0xFF0F172A)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⚙ تحكم المذيع الأسطوري", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("🐢 الوضع البطيء للشات (منع الإغراق)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 10, 30).forEach { sec ->
                            val sel = slowModeSec == sec
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) SovereignColors.LiveContainer else Color.White.copy(0.08f))
                                    .clickable { onSlowMode(sec) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (sec == 0) "إيقاف" else "${sec}ث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onToggleMic,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isMuted) Color.Red.copy(0.3f) else Color.White.copy(0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (isMuted) "🔇 إلغاء الكتم" else "🎙 كتم نفسي", color = Color.White, fontSize = 12.sp) }
                        Button(
                            onClick = onToggleRecording,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color.White.copy(0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (isRecording) "⏹ إيقاف REC" else "● بدء REC", color = Color.White, fontSize = 12.sp) }
                    }
                    OutlinedButton(
                        onClick = onUnpin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("📌 إلغاء تثبيت التعليق", color = Color.Gray) }
                    HorizontalDivider(color = Color.White.copy(0.08f))
                    Text("🚫 الكلمات المحظورة (افصل بفاصلة)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = blockedWords,
                        onValueChange = onBlockedWordsChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: spam, إعلان, xxx", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White.copy(0.06f), unfocusedContainerColor = Color.White.copy(0.04f), focusedBorderColor = SovereignColors.LiveContainer, unfocusedBorderColor = Color.White.copy(0.1f), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = onSaveWords,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("حفظ الكلمات المحظورة", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun InviteFromLiveSheet(
    streamId: String,
    inviteRedId: String,
    onRedIdChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onInviteFriend: () -> Unit,
    onCopyLink: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
            Surface(
                // نفس نمط ابتلاع النقر الصامت — انظر ViewersSheet أعلاه.
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).pointerInput(Unit) { detectTapGestures { } },
                color = Color(0xFF0F172A)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("دعوة إلى البث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("شارك الرابط أو ادعُ صديقاً عبر معرّف يونس", color = Color.Gray, fontSize = 12.sp)
                    // رابط
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).clickable { onCopyLink() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("younes://livestream/$streamId", color = SovereignColors.Cyan, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ContentCopy, null, tint = SovereignColors.Cyan, modifier = Modifier.size(18.dp))
                    }
                    Button(onClick = onCopyLink, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Share, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نسخ الرابط", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    // مشاركة نظامية TikTok-style (واتساب/تيليجرام/...) — كانت نسخ فقط
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "شاهد البث المباشر 🔴\nyounes://livestream/$streamId")
                                }
                                context.startActivity(android.content.Intent.createChooser(send, "مشاركة البث"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, tint = SovereignColors.Cyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة عبر النظام", color = SovereignColors.Cyan)
                    }
                    HorizontalDivider(color = Color.White.copy(0.08f))
                    Text("دعوة صديق عبر معرّف يونس", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = inviteRedId,
                        onValueChange = onRedIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: 12345", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White.copy(0.06f), unfocusedContainerColor = Color.White.copy(0.04f), focusedBorderColor = SovereignColors.LiveContainer, unfocusedBorderColor = Color.White.copy(0.1f), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = onInviteFriend,
                        enabled = inviteRedId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveContainer, disabledContainerColor = Color.Gray.copy(0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال دعوة", color = Color.White, fontWeight = FontWeight.Bold)
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
    // FIX: إصلاح الشاشة السوداء عند egl null + fallback + حماية init(null)
    val eglLive = LiveStreamRuntime.eglContext
    val egl = eglLive
        ?: com.red.sovereign.calls.GroupCallRuntime.eglContext
        ?: WebRtcBootstrap.eglContext
    if (egl == null) {
        Box(modifier.background(Color(0xFF02080C)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White, modifier = androidx.compose.ui.Modifier.size(32.dp))
        }
        return
    }
    if (track == null) {
        Box(modifier.background(Color(0xFF0F172A)))
        return
    }

    var viewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(egl, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(true)
                viewRef = this
            }
        },
        update = { view ->
            view.setMirror(mirror)
        },
        modifier = modifier
    )

    DisposableEffect(track, viewRef) {
        if (track != null && viewRef != null) {
            track.addSink(viewRef)
        }
        onDispose {
            if (track != null && viewRef != null) {
                track.removeSink(viewRef)
            }
        }
    }

    DisposableEffect(viewRef) {
        onDispose {
            viewRef?.release()
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
                    // ابتلاع صامت للنقر — لا clickable(enabled=false) كي لا يُعلن زراً معطلاً.
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SovereignColors.Cyan, SovereignColors.LiveContainer))), contentAlignment = Alignment.Center) {
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
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveContainer)
                    ) {
                        Text("مشاهدة")
                    }
                }
            }
        }
    }
}
