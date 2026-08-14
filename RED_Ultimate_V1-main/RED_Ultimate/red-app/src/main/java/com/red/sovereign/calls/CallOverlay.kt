package com.red.sovereign.calls

import android.Manifest
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val video = mode == "VIDEO"
    var acceptCamera by remember { mutableStateOf(true) }
    var acceptMic by remember { mutableStateOf(true) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = !video || grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audio && camOk) YounesCallService.accept(context, cameraOn = acceptCamera, micOn = acceptMic)
    }
    val bluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH)
    }
    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }

    fun requestAccept(cameraOn: Boolean = true, micOn: Boolean = true) {
        acceptCamera = cameraOn
        acceptMic = micOn
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video && cameraOn) add(Manifest.permission.CAMERA)
        }
        permissions.launch(needed.toTypedArray())
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

                when (state) {
                    is CallUiState.Incoming -> IncomingBody(peer, video, onAccept = { requestAccept(true, true) }, onAcceptPrivate = { requestAccept(false, true) }, onReject = {
                        YounesCallService.action(context, YounesCallService.ACTION_REJECT)
                    })
                    is CallUiState.Connecting -> ConnectingBody(peer, video)
                    is CallUiState.Error -> ErrorBody(state.message) {
                        YounesCallService.action(context, YounesCallService.ACTION_END)
                    }
                    is CallUiState.Busy -> ErrorBody("المشترك مشغول بمكالمة أخرى") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.Declined -> ErrorBody("تم رفض المكالمة") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.NoAnswer -> ErrorBody("لم يتم الرد") { YounesCallService.action(context, YounesCallService.ACTION_END) }
                    is CallUiState.CallEnded -> CallEndedBody(state) {
                        if (state.canRedial) YounesCallService.start(context, state.peer, state.mode == "VIDEO")
                    }
                    is CallUiState.Reconnecting -> ReconnectingBody(peer)
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

                if (state is CallUiState.Active || state is CallUiState.ActiveWithIncoming || state is CallUiState.Connecting) {
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
            is CallUiState.Connecting -> if (video) "جارٍ الاتصال بالفيديو…" else "جارٍ الاتصال…"
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!video) PulseAvatar(letter = peer, pulsing = true)
        Text("انتظر حتى يرد الطرف الآخر", color = Color.White.copy(0.65f), fontSize = 14.sp)
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
