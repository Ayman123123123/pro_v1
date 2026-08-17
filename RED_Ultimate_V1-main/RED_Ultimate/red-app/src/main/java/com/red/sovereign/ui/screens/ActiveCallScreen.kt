package com.red.sovereign.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.calls.CallRuntime
import com.red.sovereign.calls.CallUiState
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.calls.WebrtcVideo
import com.red.sovereign.ui.components.SovereignAvatarRing
import com.red.sovereign.ui.components.SovereignStatusBadge
import com.red.sovereign.ui.components.SovereignWaveVisualizer
import com.red.sovereign.ui.theme.*

@Composable
fun ActiveCallScreen(modifier: Modifier = Modifier) {
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
        val audio = grants[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!audio) return@rememberLauncherForActivityResult
        val camOk = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (video && !camOk) {
            android.widget.Toast.makeText(context, "الكاميرا غير متاحة — ستستمر المكالمة صوتياً", android.widget.Toast.LENGTH_SHORT).show()
            YounesCallService.accept(context, cameraOn = false, micOn = acceptMic)
        } else {
            YounesCallService.accept(context, cameraOn = acceptCamera && camOk, micOn = acceptMic)
        }
    }
    
    fun requestAccept(cameraOn: Boolean = true, micOn: Boolean = true) {
        acceptCamera = cameraOn
        acceptMic = micOn
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video && cameraOn) add(Manifest.permission.CAMERA)
        }
        permissions.launch(needed.toTypedArray())
    }
    
    BackHandler(enabled = state !is CallUiState.Idle) {
        when (state) {
            is CallUiState.Active, is CallUiState.Connecting, is CallUiState.Reconnecting -> YounesCallService.action(context, YounesCallService.ACTION_END)
            is CallUiState.Incoming -> YounesCallService.action(context, YounesCallService.ACTION_REJECT)
            else -> YounesCallService.action(context, YounesCallService.ACTION_END)
        }
    }

    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Pulsing Animation for connecting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030712),
                        Color(0xFF080E1C),
                        Color(0xFF020409)
                    )
                )
            )
            .clickable { controlsVisible = !controlsVisible }
    ) {
        // Video Remote (Background if active video call)
        if (video && state is CallUiState.Active) {
            CallRuntime.remoteVideo?.let { rVideo ->
                WebrtcVideo(
                    rVideo,
                    CallRuntime.eglContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(vertical = 36.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Info
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible || state !is CallUiState.Active,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SovereignStatusBadge(
                            label = if (video) "مكالمة فيديو E2EE مشفرة 🔒" else "مكالمة صوتية E2EE مشفرة 🔒",
                            glowColor = SovereignColors.EmeraldNeon
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = peer,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontFamily = CairoFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (state is CallUiState.Active && !state.isHeld) {
                            CallTimerText(startedAt = state.startedAt, video = video)
                        } else {
                            val callStateText = when (state) {
                                is CallUiState.Incoming -> if (video) "مكالمة فيديو واردة..." else "مكالمة صوتية واردة..."
                                is CallUiState.Connecting -> state.presenceLabel
                                is CallUiState.Active -> "معلّقة"
                                is CallUiState.ActiveWithIncoming -> "نشطة · ${state.waiting.peer} ينتظر"
                                is CallUiState.Error -> state.message
                                is CallUiState.Busy -> "مشغول"
                                is CallUiState.Declined -> "مرفوضة"
                                is CallUiState.NoAnswer -> "لا يوجد رد"
                                is CallUiState.CallEnded -> "انتهت المكالمة"
                                is CallUiState.Reconnecting -> "انقطع الاتصال... جارٍ استعادته"
                                else -> "جاري التفاوض المشفر..."
                            }
                            Text(
                                text = callStateText,
                                color = SovereignColors.EmeraldNeon,
                                fontSize = 15.sp,
                                fontFamily = TajawalFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Avatar & Visualizer (Audio mode)
                if (!video || state !is CallUiState.Active) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                            if (state is CallUiState.Connecting || state is CallUiState.Incoming || state is CallUiState.Reconnecting) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .background(SovereignColors.Emerald.copy(alpha = alpha))
                                )
                            }
                            SovereignAvatarRing(
                                initial = peer.firstOrNull()?.toString() ?: "?",
                                size = 124.dp,
                                isEncrypted = true,
                                ringColor = SovereignColors.GoldNeon
                            )
                        }

                        if (state is CallUiState.Active) {
                            Spacer(Modifier.height(24.dp))
                            SovereignWaveVisualizer(
                                modifier = Modifier.width(220.dp),
                                isSpeaking = mic,
                                barColor = SovereignColors.EmeraldNeon
                            )
                        }
                    }
                } else if (video && state is CallUiState.Active) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomEnd) {
                        WebrtcVideo(
                            CallRuntime.localVideo,
                            CallRuntime.eglContext,
                            mirror = true,
                            Modifier
                                .size(120.dp, 170.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, SovereignColors.Gold, RoundedCornerShape(18.dp))
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Controls
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                ) {
                    when (state) {
                        is CallUiState.Incoming -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingActionButton(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_REJECT) },
                                    containerColor = SovereignColors.RubyNeon,
                                    contentColor = Color.White,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(imageVector = Icons.Rounded.CallEnd, contentDescription = "رفض", modifier = Modifier.size(36.dp))
                                }
                                FloatingActionButton(
                                    onClick = { requestAccept(true, true) },
                                    containerColor = SovereignColors.EmeraldNeon,
                                    contentColor = Color.Black,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(imageVector = Icons.Rounded.Call, contentDescription = "قبول", modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                        is CallUiState.Error, is CallUiState.Busy, is CallUiState.Declined, is CallUiState.NoAnswer, is CallUiState.CallEnded -> {
                            FloatingActionButton(
                                onClick = { YounesCallService.action(context, YounesCallService.ACTION_END) },
                                containerColor = SovereignColors.SurfaceCard,
                                contentColor = Color.White,
                                modifier = Modifier.size(72.dp).padding(bottom = 24.dp),
                                shape = CircleShape
                            ) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "إغلاق", modifier = Modifier.size(36.dp))
                            }
                        }
                        else -> { // Active, Connecting, Reconnecting
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(SovereignColors.ObsidianDeep.copy(alpha = 0.85f))
                                    .border(1.dp, SovereignColors.GlassBorder, RoundedCornerShape(32.dp))
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CallControlButton(
                                    icon = if (!mic) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                    isActive = !mic,
                                    onClick = { mic = !mic; YounesCallService.action(context, YounesCallService.ACTION_MIC, mic) }
                                )
                                
                                if (video) {
                                    CallControlButton(
                                        icon = if (!camera) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                                        isActive = !camera,
                                        onClick = { camera = !camera; YounesCallService.action(context, YounesCallService.ACTION_CAMERA, camera) }
                                    )
                                    CallControlButton(
                                        icon = Icons.Rounded.Cameraswitch,
                                        isActive = false,
                                        onClick = { YounesCallService.action(context, YounesCallService.ACTION_SWITCH_CAMERA) }
                                    )
                                }
                                
                                FloatingActionButton(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_END) },
                                    containerColor = SovereignColors.RubyNeon,
                                    contentColor = Color.White,
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(imageVector = Icons.Rounded.CallEnd, contentDescription = "إنهاء المكالمة", modifier = Modifier.size(32.dp))
                                }

                                CallControlButton(
                                    icon = if (CallRuntime.speaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                                    isActive = CallRuntime.speaker,
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_SPEAKER, !CallRuntime.speaker) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (isActive) SovereignColors.RubyNeon.copy(alpha = 0.25f) else SovereignColors.SurfaceCard)
            .border(
                1.2.dp,
                if (isActive) SovereignColors.RubyNeon else SovereignColors.GlassBorder,
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) SovereignColors.RubyNeon else Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun CallTimerText(startedAt: Long, video: Boolean) {
    var durationText by remember { mutableStateOf("00:00") }
    LaunchedEffect(startedAt) {
        while (true) {
            val diff = (System.currentTimeMillis() - startedAt) / 1000
            if (diff >= 0) {
                val m = diff / 60
                val s = diff % 60
                durationText = "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(
        text = durationText,
        color = SovereignColors.EmeraldNeon,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TajawalFamily
    )
}
