package com.red.sovereign.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import kotlinx.coroutines.isActive
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.calls.CallRuntime
import com.red.sovereign.calls.CallUiState
import com.red.sovereign.calls.PictureInPictureManager
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.calls.WebrtcVideo
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.ui.components.SovereignAvatarRing
import com.red.sovereign.ui.components.SovereignCallAcceptButton
import com.red.sovereign.ui.components.SovereignCallControlButton
import com.red.sovereign.ui.components.SovereignCallEndButton
import com.red.sovereign.ui.components.rememberSovereignHaze
import com.red.sovereign.ui.components.sovereignHazeEffect
import com.red.sovereign.ui.components.sovereignHazeSource
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
    // اسم وصورة الطرف الآخر من الدليل — لا المعرف الخام.
    val directory: DirectoryViewModel = viewModel()
    fun resolveName(redId: String): String =
        directory.contacts.firstOrNull { it.redId == redId }?.displayName?.takeIf { it.isNotBlank() } ?: redId
    val peerName = resolveName(peer)
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
    
    var mic by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }

    // رجوع متدرج: إن كانت عناصر التحكم مخفية تُظهرها أولاً، وإلا يُصغّر
    // إلى PiP/شريط مصغّر مع بقاء ForegroundService — لا يُنهي المكالمة
    // ولا يحبس المستخدم (الإنهاء الصريح لزر الإنهاء فقط).
    BackHandler(enabled = state !is CallUiState.Idle) {
        if (!controlsVisible) {
            controlsVisible = true
        } else {
            val activity = context as? android.app.Activity
            CallRuntime.isMinimized = true
            if (activity == null || !PictureInPictureManager.isSupported() || !PictureInPictureManager.enterPip(activity)) {
                // بلا دعم PiP نبقى على الشاشة مصغّرة منطقيًا دون قطع الاتصال.
            }
        }
    }

    // Pulsing halo only exists while ringing/connecting — the InfiniteTransition
    // is created inside PulsingHalo so it is fully disposed in Active/Ended
    // states instead of running forever on every call screen.
    val isPulsing = state is CallUiState.Connecting ||
        state is CallUiState.Incoming ||
        state is CallUiState.Reconnecting
    // زجاج مثلج حقيقي لشريط التحكم — المصدر: خلفية المكالمة تحته.
    val hazeState = rememberSovereignHaze()

    Box(
        modifier = modifier
            .fillMaxSize()
            .sovereignHazeSource(hazeState)
            // أساس مرفوع 0A0F18 (لا 020409 الساحق) + شبكية كوبالت/بنفسج ≤12%.
            // شاشة المكالمة تبقى داكنة عمدًا في الوضعين (مثل واتساب) لكن
            // بلا سحق OLED: كل نص أبيض ≥13:1 على الأساس المرفوع.
            .background(
                Brush.verticalGradient(
                    listOf(YounesDeep, YounesVoid, YounesMidnight)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "إظهار أو إخفاء عناصر التحكم",
                onClick = { controlsVisible = !controlsVisible }
            )
    ) {
        // هالة الشبكية فوق الأساس
        Box(
            Modifier.fillMaxSize()
                .background(SovereignGradients.meshCall)
        )
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
                            text = peerName,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontFamily = PlexArabicFamily,
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
                                is CallUiState.ActiveWithIncoming -> "نشطة · ${resolveName(state.waiting.peer)} ينتظر"
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
                                fontFamily = PlexArabicFamily,
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
                            if (isPulsing) {
                                PulsingHalo()
                            }
                            SovereignAvatarRing(
                                initial = peerName.firstOrNull()?.toString() ?: "?",
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

                // مكالمة واردة ثانية أثناء نشطة: تعليق/قبول (تبديل)، رفض،
                // وتعليق/استئناف الحالية — كانت نصًا فقط بلا أي زر.
                if (state is CallUiState.ActiveWithIncoming) {
                    val waiting = state.waiting
                    val waitingName = resolveName(waiting.peer)
                    val held = state.active.isHeld
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2E00).copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("مكالمة ثانية من $waitingName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (waitingName != waiting.peer) {
                                Text(waiting.peer, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_ACCEPT_SECOND) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = YounesPrimary, contentColor = Color.Black)
                                ) { Text("تعليق وقبول", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                OutlinedButton(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_REJECT_SECOND) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("رفض", fontSize = 13.sp) }
                                TextButton(
                                    onClick = {
                                        YounesCallService.action(
                                            context,
                                            if (held) YounesCallService.ACTION_RESUME else YounesCallService.ACTION_HOLD
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (held) "استئناف الحالية" else "تعليق الحالية", color = Color.White, fontSize = 13.sp) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                                // نظام موحّد: إنهاء/رفض 64dp روبي، قبول 72dp زمرد.
                                SovereignCallEndButton(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_REJECT) },
                                    contentDescription = "رفض المكالمة"
                                )
                                SovereignCallAcceptButton(
                                    onClick = { requestAccept(true, true) },
                                    contentDescription = "قبول المكالمة"
                                )
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
                                    .sovereignHazeEffect(
                                        state = hazeState,
                                        tier = SovereignGlassTier.Sheet,
                                        isDark = true
                                    )
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(SovereignColors.ObsidianDeep.copy(alpha = 0.85f))
                                    .border(1.dp, SovereignColors.GlassBorder, RoundedCornerShape(32.dp))
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CallControlButton(
                                    icon = if (!mic) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                    contentDescription = if (!mic) "إلغاء كتم الميكروفون" else "كتم الميكروفون",
                                    isActive = !mic,
                                    onClick = { mic = !mic; YounesCallService.action(context, YounesCallService.ACTION_MIC, mic) }
                                )
                                
                                if (video) {
                                    CallControlButton(
                                        icon = if (!camera) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                                        contentDescription = if (!camera) "تشغيل الكاميرا" else "إيقاف الكاميرا",
                                        isActive = !camera,
                                        onClick = { camera = !camera; YounesCallService.action(context, YounesCallService.ACTION_CAMERA, camera) }
                                    )
                                    CallControlButton(
                                        icon = Icons.Rounded.Cameraswitch,
                                        contentDescription = "تبديل الكاميرا",
                                        isActive = false,
                                        onClick = { YounesCallService.action(context, YounesCallService.ACTION_SWITCH_CAMERA) }
                                    )
                                }
                                
                                SovereignCallEndButton(
                                    onClick = { YounesCallService.action(context, YounesCallService.ACTION_END) },
                                    contentDescription = "إنهاء المكالمة"
                                )

                                CallControlButton(
                                    icon = if (CallRuntime.speaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                                    contentDescription = if (CallRuntime.speaker) "إيقاف مكبر الصوت" else "تشغيل مكبر الصوت",
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
    onClick: () -> Unit,
    contentDescription: String = "زر تحكم",
    modifier: Modifier = Modifier
) {
    // يفوَّض للنظام الموحّد — حجم/تموّج/تسمية واحدة عبر كل الشاشات.
    SovereignCallControlButton(
        icon = icon,
        contentDescription = contentDescription,
        isActive = isActive,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun PulsingHalo() {
    // بوابة reduceMotion: لا InfiniteTransition إطلاقًا عند تفضيل تقليل الحركة.
    if (AppThemeState.reduceMotion) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(SovereignColors.Emerald.copy(alpha = 0.12f))
        )
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            // 2000ms هالة بطيئة فاخرة (كان 1400ms) — نبضة واحدة كحد أقصى
            animation = tween(SovereignPulseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(SovereignPulseDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .clip(CircleShape)
            .background(SovereignColors.Emerald.copy(alpha = alpha))
    )
}

@Composable
fun CallTimerText(startedAt: Long, video: Boolean) {
    var durationText by remember { mutableStateOf("00:00") }
    LaunchedEffect(startedAt) {
        // Cancelled on dispose (LaunchedEffect) + explicit isActive guard so
        // the loop can never outlive the call screen.
        while (isActive) {
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
        fontFamily = PlexArabicFamily
    )
}
