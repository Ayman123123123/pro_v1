package com.red.sovereign.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesVoid
import com.red.sovereign.calls.Material3ExpressivePstnCallScreen
import com.red.sovereign.calls.Material3ExpressiveIncomingPstnCallScreen

/**
 * مكالمة فردية عبر الإنترنت — سلوك واتساب/تلجرام:
 * رنين + قبول/رفض، صوت = صورة ونبض، فيديو = شاشة كاملة + نافذة صغيرة.
 * لا لوحة DTMF (تلك للهواتف PSTN).
 */
@Composable
fun YounesCallOverlay() {
    val state = CallRuntime.state
    if (state is CallUiState.Idle) return
    val context = LocalContext.current
    val mode = when (state) {
        is CallUiState.Incoming -> state.mode
        is CallUiState.Connecting -> state.mode
        is CallUiState.Active -> state.mode
        is CallUiState.ActiveWithIncoming -> state.active.mode
        is CallUiState.CallEnded -> state.mode
        is CallUiState.Reconnecting -> state.mode
        else -> "VOICE"
    }
    val peer = when (state) {
        is CallUiState.Incoming -> state.peer
        is CallUiState.Connecting -> state.peer
        is CallUiState.Active -> state.peer
        is CallUiState.ActiveWithIncoming -> state.active.peer
        is CallUiState.CallEnded -> state.peer
        is CallUiState.Busy -> state.peer
        is CallUiState.Declined -> state.peer
        is CallUiState.NoAnswer -> state.peer
        is CallUiState.Reconnecting -> state.peer
        else -> ""
    }
    val callId = when (state) {
        is CallUiState.Incoming -> state.callId
        is CallUiState.Connecting -> state.callId
        is CallUiState.Active -> state.callId
        is CallUiState.ActiveWithIncoming -> state.active.callId
        is CallUiState.CallEnded -> state.callId
        is CallUiState.Reconnecting -> state.callId
        else -> ""
    }
    val isPstnCall = mode == "PSTN" || mode == "DINSTAR" || callId.startsWith("pstn-") || callId.startsWith("dinstar-")
    val video = mode == "VIDEO"
    var acceptCamera by remember { mutableStateOf(true) }
    var acceptMic by remember { mutableStateOf(true) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!audio) return@rememberLauncherForActivityResult
        val camOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (video && !camOk) {
            // رفض الكاميرا ≠ رفض المكالمة — نكمل صوتياً ونخبر المستخدم (لا نجمّد زر القبول)
            android.widget.Toast.makeText(context, "الكاميرا غير متاحة — ستستمر المكالمة صوتياً", android.widget.Toast.LENGTH_SHORT).show()
            YounesCallService.accept(context, cameraOn = false, micOn = acceptMic)
        } else {
            YounesCallService.accept(context, cameraOn = acceptCamera && camOk, micOn = acceptMic)
        }
    }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH)
    }
    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }
    var showRecordConsent by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }

    fun requestAccept(cameraOn: Boolean = true, micOn: Boolean = true) {
        acceptCamera = cameraOn
        acceptMic = micOn
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video && cameraOn) add(Manifest.permission.CAMERA)
        }
        permissions.launch(needed.toTypedArray())
    }

    // حوار تأكيد التسجيل — موافقة صريحة قبل بدء التسجيل (لا تُفترض)
    if (showRecordConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRecordConsent = false },
            title = { Text("تسجيل المكالمة", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيتم تسجيل هذه المكالمة على جهازك فقط (صوتك عبر الميكروفون) مع تشفير AES-GCM.\n" +
                        "أكّد أن الطرف الآخر موافق على التسجيل قبل البدء."
                )
            },
            confirmButton = {
                TextButton({
                    showRecordConsent = false
                    context.startService(
                        android.content.Intent(context, YounesCallService::class.java)
                            .setAction(YounesCallService.ACTION_START_RECORDING)
                            .putExtra(YounesCallService.EXTRA_CONSENT, true)
                    )
                }) { Text("موافق — ابدأ التسجيل", color = YounesEmerald) }
            },
            dismissButton = {
                TextButton({ showRecordConsent = false }) { Text("إلغاء") }
            }
        )
    }

    // لوحة الأرقام DTMF — تعمل فعلياً عبر توليد نغمات على قناة المكالمة
    if (showKeypad) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeypad = false },
            title = { Text("لوحة الأرقام", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("1 2 3", "4 5 6", "7 8 9", "* 0 #").forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.split(" ").forEach { digit ->
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { YounesCallService.dtmf(context, digit[0]) },
                                    modifier = Modifier.size(64.dp, 52.dp)
                                ) {
                                    Text(digit, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showKeypad = false }) { Text("إغلاق") } }
        )
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF101B2B), Color(0xFF03070C))))
        ) {
            // Minimize Button
            if (state is CallUiState.Active || state is CallUiState.Connecting || state is CallUiState.Reconnecting) {
                androidx.compose.material3.IconButton(
                    onClick = { CallRuntime.isMinimized = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 16.dp).statusBarsPadding()
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                        contentDescription = "تصغير",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            if (video && state !is CallUiState.Incoming && state !is CallUiState.Error) {
                WebrtcVideo(CallRuntime.remoteVideo, CallRuntime.eglContext, mirror = false, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(bottom = 60.dp)
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                CallHeader(peer, state, video)

                // شارة صريحة عند فشل الكاميرا — مكالمة صوتية + زر إعادة محاولة
                if (CallRuntime.cameraNotice) {
                    Surface(
                        color = Color(0xFFB45309).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تعذر فتح الكاميرا — المكالمة صوتية", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { YounesCallService.action(context, YounesCallService.ACTION_CAMERA, true) }) {
                                Text("إعادة المحاولة", color = Color(0xFFFFC107), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                when (state) {
                    // PSTN/DINSTAR calls use Material 3 Expressive screens
                    is CallUiState.Incoming -> if (isPstnCall) {
                        Material3ExpressiveIncomingPstnCallScreen(
                            callerNumber = peer,
                            callerName = null, // Could be enhanced with contact lookup
                            callId = callId,
                            onAccept = { YounesCallService.action(context, YounesCallService.ACTION_ACCEPT) },
                            onReject = { YounesCallService.action(context, YounesCallService.ACTION_REJECT) },
                            onDeclineWithMessage = { msg -> /* TODO: send decline message */ },
                            onAcceptVideo = { YounesCallService.action(context, YounesCallService.ACTION_ACCEPT_VIDEO) },
                            context = context
                        )
                    } else {
                        IncomingBody(peer, video, onAccept = { requestAccept(true, true) }, onAcceptPrivate = { requestAccept(false, true) }, onReject = {
                            YounesCallService.action(context, YounesCallService.ACTION_REJECT)
                        })
                    }
                    is CallUiState.Connecting -> if (isPstnCall) {
                        Material3ExpressivePstnCallScreen(
                            status = PstnCallStatus.BRIDGING,
                            metrics = CallMetrics(),
                            onMuteToggle = { YounesCallService.action(context, YounesCallService.ACTION_MIC, it) },
                            onSpeakerToggle = { YounesCallService.action(context, YounesCallService.ACTION_SPEAKER, it) },
                            onKeypadToggle = { /* show keypad */ },
                            onHoldToggle = { YounesCallService.action(context, if (it) YounesCallService.ACTION_HOLD else YounesCallService.ACTION_RESUME) },
                            onRecordToggle = { /* record toggle */ },
                            onVideoToggle = { /* video toggle */ },
                            onHangup = { YounesCallService.action(context, YounesCallService.ACTION_END) }
                        )
                    } else {
                        ConnectingBody(peer, video)
                    }
                    is CallUiState.Error -> ErrorBody(state.message) {
                        YounesCallService.action(context, YounesCallService.ACTION_END)
                    }
                    is CallUiState.Busy -> ErrorBody("المشترك مشغول بمكالمة أخرى") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.Declined -> ErrorBody("تم رفض المكالمة") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.NoAnswer -> ErrorBody("لم يتم الرد") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.CallEnded -> if (isPstnCall) {
                        Material3ExpressivePstnCallScreen(
                            status = PstnCallStatus.ENDED,
                            metrics = CallMetrics(),
                            onHangup = { /* handled */ },
                            onBack = { /* handled by overlay */ }
                        )
                    } else {
                        CallEndedBody(state) {
                            if (state.canRedial) YounesCallService.start(context, state.peer, state.mode == "VIDEO")
                        }
                    }
                    is CallUiState.Reconnecting -> if (isPstnCall) {
                        Material3ExpressivePstnCallScreen(
                            status = PstnCallStatus.BRIDGING,
                            metrics = CallMetrics(),
                            onHangup = { YounesCallService.action(context, YounesCallService.ACTION_END) }
                        )
                    } else {
                        ReconnectingBody(peer)
                    }
                    else -> {
                        if (video) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomEnd) {
                                WebrtcVideo(
                                    CallRuntime.localVideo,
                                    CallRuntime.eglContext,
                                    mirror = true,
                                    Modifier.size(120.dp, 170.dp).clip(RoundedCornerShape(16.dp))
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                PulseAvatar(size = 140.dp, letter = peer, pulsing = state is CallUiState.Active && (state as? CallUiState.Active)?.isHeld != true)
                            }
                        }
                    }
                }

                if (state is CallUiState.ActiveWithIncoming) {
                    WaitingBanner(state.waiting.peer, onAccept = {
                        YounesCallService.action(context, YounesCallService.ACTION_ACCEPT_SECOND)
                    }, onReject = {
                        YounesCallService.action(context, YounesCallService.ACTION_REJECT_SECOND)
                    })
                }

                // أزرار التحكم تبقى متاحة في كل الحالات النشطة — بما فيها Reconnecting،
                // حتى لا تعلق المكالمة بلا زر إنهاء عند انقطاع الشبكة
                if (state is CallUiState.Active || state is CallUiState.ActiveWithIncoming ||
                    state is CallUiState.Connecting || state is CallUiState.Reconnecting
                ) {
                    var controlsVisible by remember { mutableStateOf(true) }
                    
                    Box(modifier = Modifier.fillMaxWidth().clickable { controlsVisible = !controlsVisible }) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = controlsVisible,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                        ) {
                            ActiveControls(
                                video = video,
                                held = (state as? CallUiState.Active)?.isHeld == true,
                                mic = mic,
                                camera = camera,
                                isRecording = CallRuntime.isRecording,
                                onRecord = {
                                    if (CallRuntime.isRecording) {
                                        YounesCallService.action(context, YounesCallService.ACTION_STOP_RECORDING)
                                    } else {
                                        showRecordConsent = true
                                    }
                                },
                                onKeypad = { showKeypad = true },
                                onMic = { mic = !mic; YounesCallService.action(context, YounesCallService.ACTION_MIC, mic) },
                                onSpeaker = { YounesCallService.action(context, YounesCallService.ACTION_SPEAKER, !CallRuntime.speaker) },
                                onHold = {
                                    val heldNow = (CallRuntime.state as? CallUiState.Active)?.isHeld == true
                                    YounesCallService.action(context, if (heldNow) YounesCallService.ACTION_RESUME else YounesCallService.ACTION_HOLD)
                                },
                                onBluetooth = {
                                    if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH)
                                    } else bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                },
                                onCamera = { camera = !camera; YounesCallService.action(context, YounesCallService.ACTION_CAMERA, camera) },
                                onFlip = { YounesCallService.action(context, YounesCallService.ACTION_SWITCH_CAMERA) },
                                onEnd = { YounesCallService.action(context, YounesCallService.ACTION_END) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHeader(peer: String, state: CallUiState, video: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(peer.ifBlank { "يونس" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val subtitle = when (state) {
            is CallUiState.Incoming -> if (video) "مكالمة فيديو واردة" else "مكالمة صوتية واردة"
            is CallUiState.Connecting -> state.presenceLabel
            is CallUiState.Active -> if (state.isHeld) "معلّقة" else if (video) "مكالمة فيديو" else "مكالمة صوتية"
            is CallUiState.ActiveWithIncoming -> "نشطة · ${state.waiting.peer} ينتظر"
            is CallUiState.Error -> state.message
            is CallUiState.Busy -> "مشغول"
            is CallUiState.Declined -> "مرفوضة"
            is CallUiState.NoAnswer -> "لا يوجد رد"
            is CallUiState.CallEnded -> "انتهت المكالمة"
            is CallUiState.Reconnecting -> "انقطع الاتصال... جارٍ استعادته"
            else -> ""
        }
        Text(subtitle, color = Color.White.copy(0.72f), fontSize = 14.sp)
        val active = when (state) {
            is CallUiState.Active -> state
            is CallUiState.ActiveWithIncoming -> state.active
            else -> null
        }
        if (active != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CallElapsedTimer(active.startedAt)
                NetworkQualityBars(CallRuntime.networkStats)
            }
        }
        // مؤشر التسجيل — نقطة حمراء نابضة + "جارٍ التسجيل"
        if (CallRuntime.isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33E53935))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE53935))
                )
                Text("جارٍ التسجيل", color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        EncryptedBadge()
    }
}

@Composable
private fun IncomingBody(peer: String, video: Boolean, onAccept: () -> Unit, onAcceptPrivate: () -> Unit, onReject: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PulseAvatar(letter = peer, pulsing = true)
        Text(if (video) "يرن فيديو يونس" else "يرن صوت يونس", color = Color.White.copy(0.6f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
            EndCallButton("رفض", onReject)
            AcceptCallButton("قبول", onAccept)
        }
        if (video) {
            TextButton(onAcceptPrivate) {
                Text("قبول بدون كاميرا", color = YounesEmerald, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ConnectingBody(peer: String, video: Boolean) {
    val state = CallRuntime.state as? CallUiState.Connecting
    val presenceIcon = when (state?.presenceState) {
        CallPresenceMonitor.PresenceState.RINGING -> "🔔"
        CallPresenceMonitor.PresenceState.WAKING_UP -> "⚡"
        CallPresenceMonitor.PresenceState.NO_ANSWER -> "🔇"
        else -> "📡"
    }
    val presenceSubtext = when (state?.presenceState) {
        CallPresenceMonitor.PresenceState.RINGING -> "وصلت المكالمة — في انتظار الرد"
        CallPresenceMonitor.PresenceState.WAKING_UP -> "جارٍ تنبيه الجهاز عبر مسار احتياطي"
        CallPresenceMonitor.PresenceState.NO_ANSWER -> "لم يرد الطرف الآخر خلال المهلة"
        else -> "انتظر حتى يرد الطرف الآخر"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!video) PulseAvatar(letter = peer, pulsing = state?.presenceState != CallPresenceMonitor.PresenceState.WAKING_UP)
        // مؤشر حالة تسليم المكالمة
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(presenceIcon, fontSize = 18.sp)
            Text(presenceSubtext, color = Color.White.copy(0.65f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorBody(message: String, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, color = Color.White, fontSize = 16.sp)
        EndCallButton("إغلاق", onClose)
    }
}

@Composable
private fun CallEndedBody(state: CallUiState.CallEnded, onRedial: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val durationFormatted = String.format("%02d:%02d", (state.durationMs / 1000) / 60, (state.durationMs / 1000) % 60)
        Text("المدة: $durationFormatted", color = Color.White.copy(0.8f), fontSize = 16.sp)
        if (state.canRedial) {
            CallRoundButton(Icons.Default.PhoneInTalk, "اتصل مجدداً", onRedial, YounesEmerald)
        }
    }
}

@Composable
private fun ReconnectingBody(peer: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.material3.CircularProgressIndicator(color = YounesEmerald)
        Text("جارٍ إعادة الاتصال بـ $peer", color = Color.White.copy(0.8f), fontSize = 14.sp)
    }
}

@Composable
private fun WaitingBanner(peer: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3D2E00))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("مكالمة ثانية من $peer", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onAccept) { Text("تعليق وقبول", color = YounesEmerald) }
            TextButton(onReject) { Text("رفض", color = Color.White) }
        }
    }
}

@Composable
private fun ActiveControls(
    video: Boolean,
    held: Boolean,
    mic: Boolean,
    camera: Boolean,
    isRecording: Boolean,
    onRecord: () -> Unit,
    onKeypad: () -> Unit,
    onMic: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onBluetooth: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onEnd: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        CallRoundButton(if (mic) Icons.Default.Mic else Icons.Default.MicOff, "كتم", onMic, if (mic) Color.White.copy(0.14f) else Color(0x33E53935))
        CallRoundButton(Icons.Default.SpeakerPhone, "سماعة", onSpeaker)
        CallRoundButton(
            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            if (isRecording) "إيقاف التسجيل" else "تسجيل",
            onRecord,
            if (isRecording) Color(0xFFB71C1C) else Color(0x33E53935)
        )
        CallRoundButton(Icons.Default.Dialpad, "أرقام", onKeypad)
        if (video) {
            CallRoundButton(if (camera) Icons.Default.Videocam else Icons.Default.VideocamOff, "كاميرا", onCamera)
            CallRoundButton(Icons.Default.Cameraswitch, "تدوير", onFlip)
        } else {
            CallRoundButton(if (held) Icons.Default.PlayArrow else Icons.Default.Pause, if (held) "استئناف" else "تعليق", onHold)
            CallRoundButton(Icons.Default.Bluetooth, "بلوتوث", onBluetooth)
        }
        EndCallButton(onClick = onEnd)
    }
}
