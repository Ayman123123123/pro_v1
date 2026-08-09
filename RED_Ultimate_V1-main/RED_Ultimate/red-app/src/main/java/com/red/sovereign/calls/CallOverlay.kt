package com.red.sovereign.calls

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun YounesCallOverlay() {
    val state = CallRuntime.state
    if (state is CallUiState.Idle) return
    val context = LocalContext.current
    val mode = when (state) { is CallUiState.Incoming -> state.mode; is CallUiState.Connecting -> state.mode; is CallUiState.Active -> state.mode; else -> "VOICE" }
    val peer = when (state) { is CallUiState.Incoming -> state.peer; is CallUiState.Connecting -> state.peer; is CallUiState.Active -> state.peer; else -> "" }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audio = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = mode != "VIDEO" || grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audio && camera) YounesCallService.action(context, YounesCallService.ACTION_ACCEPT)
    }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH) }
    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF02080C))) {
            if (mode == "VIDEO") RemoteVideoRenderer(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(peer, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(when (state) { is CallUiState.Incoming -> "مكالمة واردة"; is CallUiState.Connecting -> "جارٍ الاتصال الآمن…"; is CallUiState.Active -> "مكالمة يونس مشفرة"; is CallUiState.Error -> state.message + " (سيُغلق تلقائياً)"; else -> "" }, color = Color.White.copy(alpha = .75f))
                }
                if (mode == "VIDEO") Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) { LocalVideoRenderer(Modifier.size(120.dp, 170.dp)) }
                if (state is CallUiState.Error) {
                    Button({ YounesCallService.action(context, YounesCallService.ACTION_END) }) { Text("إغلاق") }
                } else if (state is CallUiState.Incoming) Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    FilledIconButton({ YounesCallService.action(context, YounesCallService.ACTION_REJECT) }, Modifier.size(68.dp)) { Icon(Icons.Default.CallEnd, "رفض", tint = Color.Red) }
                    FilledIconButton({ callPermissions.launch(if (mode == "VIDEO") arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)) }, Modifier.size(68.dp)) { Icon(Icons.Default.Call, "قبول", tint = Color(0xFF2DDBA4)) }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControl(if (mic) Icons.Default.Mic else Icons.Default.MicOff, "الميكروفون") { mic = !mic; YounesCallService.action(context, YounesCallService.ACTION_MIC, mic) }
                    CallControl(Icons.Default.SpeakerPhone, "مكبر الصوت") { YounesCallService.action(context, YounesCallService.ACTION_SPEAKER, !CallRuntime.speaker) }
                    CallControl(Icons.Default.Bluetooth, "Bluetooth") {
                        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) YounesCallService.action(context, YounesCallService.ACTION_BLUETOOTH)
                        else bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (mode == "VIDEO") CallControl(if (camera) Icons.Default.Videocam else Icons.Default.VideocamOff, "الكاميرا") { camera = !camera; YounesCallService.action(context, YounesCallService.ACTION_CAMERA, camera) }
                    if (mode == "VIDEO") CallControl(Icons.Default.Cameraswitch, "تبديل الكاميرا") { YounesCallService.action(context, YounesCallService.ACTION_SWITCH_CAMERA) }
                    FilledIconButton({ YounesCallService.action(context, YounesCallService.ACTION_END) }, Modifier.size(62.dp)) { Icon(Icons.Default.CallEnd, "إنهاء", tint = Color.Red) }
                }
            }
        }
    }
}

@Composable private fun CallControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) = FilledIconButton(action, Modifier.size(52.dp)) { Icon(icon, label) }

@Composable private fun RemoteVideoRenderer(modifier: Modifier) = VideoRenderer(CallRuntime.remoteVideo, mirror = false, modifier)
@Composable private fun LocalVideoRenderer(modifier: Modifier) = VideoRenderer(CallRuntime.localVideo, mirror = true, modifier)

@Composable
private fun VideoRenderer(track: org.webrtc.VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val egl = CallRuntime.eglContext ?: return
    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    AndroidView(factory = { context -> SurfaceViewRenderer(context).apply { init(egl, null); setMirror(mirror); setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL); renderer = this; track?.addSink(this) } }, update = { view -> track?.addSink(view) }, modifier = modifier)
    DisposableEffect(track, renderer) { onDispose { renderer?.let { track?.removeSink(it); it.release() } } }
}
