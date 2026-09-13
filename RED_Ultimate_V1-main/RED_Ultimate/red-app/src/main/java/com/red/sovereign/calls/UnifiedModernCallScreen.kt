package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.components.SovereignGlassCard
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesPrimary

/**
 * شاشة المكالمة الموحدة — Liquid Glass 2026
 *
 * تدمج أفضل ما في واتساب (بساطة) وزوم (شبكة) بتصميم زجاجي سائل:
 * - خلفية متدرجة مع عمق
 * - بطاقة زجاجية عائمة للمتحدث
 * - شريط تحكم زجاجي سفلي
 * - مؤشر جودة حي + خلفية افتراضية
 * - تحترم reduceMotion و highContrast
 */
@Composable
fun ModernLiquidGlassCallScreen(
    isVideo: Boolean = false,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    onToggleMic: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleBackground: () -> Unit = {},
    // LEGENDARY: حقول واتساب+ — الاسم/الحالة/المدة (كانت ثابتة "مكالمة يونس" بلا مؤقت)
    peerName: String = "مكالمة يونس السيادية",
    peerRedId: String = "",
    callStateText: String = "",
    durationText: String = "",
    isSpeakerOn: Boolean = false,
    isIncoming: Boolean = false,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = onEndCall,
    onSwitchCamera: () -> Unit = {},
    onAddParticipant: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onToggleKeypad: () -> Unit = {}
) {
    if (isVideo) {
        LegendaryVideoCallScreen(
            peerName = peerName, peerRedId = peerRedId, durationText = durationText,
            callStateText = callStateText, isMuted = isMuted, isVideoEnabled = isVideoEnabled,
            isSpeakerOn = isSpeakerOn, onToggleMic = onToggleMic, onToggleVideo = onToggleVideo,
            onToggleSpeaker = onToggleSpeaker, onEndCall = onEndCall, onSwitchCamera = onSwitchCamera,
            onAddParticipant = onAddParticipant, onOpenChat = onOpenChat, onMinimize = onMinimize,
            onToggleBackground = onToggleBackground,
            // P0: تمرير القبول للفيديو (كانت واردة الفيديو بلا قبول)
            isIncoming = isIncoming, onAccept = onAccept, onReject = onReject
        )
    } else {
        LegendaryVoiceCallScreen(
            peerName = peerName, peerRedId = peerRedId, durationText = durationText,
            callStateText = callStateText, isMuted = isMuted, isSpeakerOn = isSpeakerOn,
            isIncoming = isIncoming, onToggleMic = onToggleMic, onToggleSpeaker = onToggleSpeaker,
            onEndCall = onEndCall, onAccept = onAccept, onReject = onReject,
            onAddParticipant = onAddParticipant, onOpenChat = onOpenChat,
            onMinimize = onMinimize, onToggleKeypad = onToggleKeypad, onUpgradeToVideo = onToggleVideo
        )
    }
}

/** LEGENDARY صوت — يتفوق على واتساب: نبض أفاتار + مؤقت + E2EE + شبكة 6 أزرار + قبول/رفض */
@Composable
fun LegendaryVoiceCallScreen(
    peerName: String,
    peerRedId: String = "",
    durationText: String = "",
    callStateText: String = "",
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    isIncoming: Boolean = false,
    onToggleMic: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
    onAddParticipant: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onToggleKeypad: () -> Unit = {},
    onUpgradeToVideo: () -> Unit = {}
) {
    val quality by CallQualityManager.stats.collectAsState()
    // نبض الأفاتار أثناء الرنين/الحديث
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1200), androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "avatar_pulse"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A0F18), Color(0xFF131C29), Color(0xFF0A0F18)))
        ).padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            // الرأس: جودة + تصغير + تشفير
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = when (quality.quality) {
                    NetworkQuality.EXCELLENT -> Color(0xFF14C79A); NetworkQuality.GOOD -> Color(0xFF4D9FE8)
                    NetworkQuality.FAIR -> Color(0xFFE0B551); else -> Color(0xFFF25C5C)
                }.copy(alpha = 0.18f), shape = RoundedCornerShape(20.dp)) {
                    Text("● ${CallQualityManager.labelFor(quality.quality)}  ${quality.bitrateKbps}kbps",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                androidx.compose.material3.IconButton(onClick = onMinimize,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                    androidx.compose.material3.Icon(Icons.Default.Videocam, null, tint = Color.White)
                }
            }
            // الوسط: أفاتار نابض + اسم + RED-ID + مؤقت + E2EE
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((120 * pulse).dp).clip(CircleShape).background(YounesPrimary.copy(alpha = 0.18f)))
                    Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.radialGradient(listOf(YounesPrimary, SovereignColors.Cyan))),
                        contentAlignment = Alignment.Center) {
                        Text(peerName.firstOrNull()?.toString() ?: "ي", color = Color(0xFF06090F), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(peerName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (peerRedId.isNotBlank()) Text(peerRedId, color = Color(0xFF9FB0C2), fontSize = 13.sp)
                Text(
                    when {
                        durationText.isNotBlank() -> durationText
                        callStateText.isNotBlank() -> callStateText
                        isIncoming -> "مكالمة واردة…"
                        else -> "جارٍ الاتصال…"
                    },
                    color = Color(0xFF9FB0C2), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Surface(color = Color(0xFF14C79A).copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text("🔒 مشفّر E2EE", color = Color(0xFF14C79A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            // الشبكة: 6 أزرار + إنهاء (أكثر من واتساب بزرين)
            SovereignGlassCard(cornerRadius = 28.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CallCircleButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "كتم",
                            active = isMuted, activeColor = Color(0xFFF25C5C), onClick = onToggleMic)
                        CallCircleButton(icon = Icons.AutoMirrored.Filled.VolumeUp, label = "مكبر",
                            active = isSpeakerOn, onClick = onToggleSpeaker)
                        CallCircleButton(icon = Icons.Default.Videocam, label = "فيديو", onClick = onUpgradeToVideo)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CallCircleButton(icon = Icons.Default.PersonAdd, label = "إضافة", onClick = onAddParticipant)
                        CallCircleButton(icon = Icons.Default.ChatBubble, label = "دردشة", onClick = onOpenChat)
                        CallCircleButton(icon = Icons.Default.Dialpad, label = "لوحة", onClick = onToggleKeypad)
                    }
                    if (isIncoming && durationText.isBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            androidx.compose.material3.IconButton(onClick = onReject,
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE03131))) {
                                Icon(Icons.Default.CallEnd, "رفض", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            androidx.compose.material3.IconButton(onClick = onAccept,
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF14C79A))) {
                                Icon(Icons.Default.Mic, "قبول", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else {
                        androidx.compose.material3.IconButton(onClick = onEndCall,
                            modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0xFFE03131))) {
                            Icon(Icons.Default.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** LEGENDARY فيديو — يتفوق على واتساب: فيديو بعيد ملء + PiP ذاتي + زجاج سفلي 8 أزرار */
@Composable
fun LegendaryVideoCallScreen(
    peerName: String,
    peerRedId: String = "",
    durationText: String = "",
    callStateText: String = "",
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    isSpeakerOn: Boolean = false,
    onToggleMic: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onAddParticipant: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onToggleBackground: () -> Unit = {},
    // P0: قبول/رفض للفيديو الوارد (كانت واردة الفيديو بلا قبول عبر UnifiedModern)
    isIncoming: Boolean = false,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = onEndCall
) {
    val quality by CallQualityManager.stats.collectAsState()
    val bg = VirtualBackgroundManager.config
    // LEGENDARY: فيديو حقيقي من WebRTC (كان أفاتاراً ثابتاً — الفيديو يصل ولا يُعرض!)
    val remoteVideo = CallRuntime.remoteVideo
    val localVideo = CallRuntime.localVideo
    val egl = CallRuntime.eglContext
    Box(Modifier.fillMaxSize().background(Color(0xFF06090F))) {
        // طبقة الفيديو البعيد الحقيقي — ملء الشاشة
        if (remoteVideo != null && egl != null && isVideoEnabled) {
            WebrtcVideo(track = remoteVideo, egl = egl, mirror = false, modifier = Modifier.fillMaxSize())
            // تظليل علوي/سفلي لقراءة الأزرار فوق الفيديو
            Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        } else {
            // احتياط: أفاتار عند انقطاع/إيقاف الفيديو
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0F18), Color(0xFF1B2635)))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(110.dp).clip(CircleShape).background(Brush.radialGradient(listOf(YounesPrimary, SovereignColors.Cyan))), contentAlignment = Alignment.Center) {
                        Text(peerName.firstOrNull()?.toString() ?: "ي", color = Color(0xFF06090F), fontSize = 44.sp, fontWeight = FontWeight.Black)
                    }
                    Text(peerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (durationText.isNotBlank()) durationText else callStateText.ifBlank { "جارٍ الاتصال…" }, color = Color(0xFF9FB0C2), fontSize = 13.sp)
                }
            }
        }
        // PiP الذاتي الحقيقي أعلى اليسار (كان صندوق "أنت" ثابتاً)
        if (localVideo != null && egl != null) {
            Surface(Modifier.align(Alignment.TopStart).padding(16.dp).size(width = 110.dp, height = 150.dp).clip(RoundedCornerShape(16.dp)),
                color = Color(0xFF1B2635), shadowElevation = 8.dp) {
                WebrtcVideo(track = localVideo, egl = egl, mirror = true, modifier = Modifier.fillMaxSize())
            }
        } else {
            Surface(Modifier.align(Alignment.TopStart).padding(16.dp).size(width = 110.dp, height = 150.dp).clip(RoundedCornerShape(16.dp)),
                color = Color(0xFF1B2635), shadowElevation = 8.dp) {
                Box(contentAlignment = Alignment.Center) {
                    Text("أنت", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        // الشريط العلوي: جودة + تشفير + تصغير
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(20.dp)) {
                Text("● ${CallQualityManager.labelFor(quality.quality)}  🔒 E2EE",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            androidx.compose.material3.IconButton(onClick = onMinimize,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                Icon(Icons.Default.Videocam, null, tint = Color.White)
            }
        }
        // الشريط السفلي الزجاجي: 8 أزرار (واتساب 4 فقط)
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)),
            color = SovereignColors.GlassBgLiquid, shadowElevation = 16.dp, tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (bg.effect) {
                    VirtualBgEffect.NONE -> "بدون خلفية • انقر للضبابي"; VirtualBgEffect.BLUR -> "ضبابي • انقر للكثيف"
                    VirtualBgEffect.BLUR_HEAVY -> "ضبابي كثيف"; VirtualBgEffect.SOLID -> "لون ثابت"; VirtualBgEffect.IMAGE -> "صورة"
                }, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CallCircleButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "كتم", active = isMuted, activeColor = Color(0xFFF25C5C), small = true, onClick = onToggleMic)
                    CallCircleButton(icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, label = "كاميرا", active = !isVideoEnabled, activeColor = Color(0xFFF25C5C), small = true, onClick = onToggleVideo)
                    CallCircleButton(icon = Icons.AutoMirrored.Filled.VolumeUp, label = "مكبر", active = isSpeakerOn, small = true, onClick = onToggleSpeaker)
                    CallCircleButton(icon = Icons.Default.SwitchCamera, label = "قلب", small = true, onClick = onSwitchCamera)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CallCircleButton(icon = Icons.Default.PersonAdd, label = "إضافة", small = true, onClick = onAddParticipant)
                    CallCircleButton(icon = Icons.Default.ChatBubble, label = "دردشة", small = true, onClick = onOpenChat)
                    CallCircleButton(icon = Icons.Default.BlurOn, label = "خلفية", small = true, onClick = onToggleBackground)
                    androidx.compose.material3.IconButton(onClick = onEndCall,
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFE03131))) {
                        Icon(Icons.Default.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                // P0: صف قبول/رفض للفيديو الوارد (كان زر الإنهاء فقط — لا قبول)
                if (isIncoming && durationText.isBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = onReject,
                            modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFE03131))) {
                            Icon(Icons.Default.CallEnd, "رفض", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        androidx.compose.material3.IconButton(onClick = onAccept,
                            modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF14C79A))) {
                            Icon(Icons.Default.Videocam, "قبول", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    activeColor: Color = Color(0xFF1B2635),
    small: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.IconButton(onClick = onClick,
            modifier = Modifier.size(if (small) 48.dp else 56.dp).clip(CircleShape)
                .background(if (active) activeColor else Color(0xFF1B2635))) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (small) 22.dp else 24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}

// ── التوافق الخلفي: النسخة القديمة ذات 8 بارامترات تستدعي الجديدة ──
@Composable
private fun LegacyModernLiquidGlassCallScreen(
    isVideo: Boolean = false,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    onToggleMic: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleBackground: () -> Unit = {}
) {
    // تفويض للشاشات الأسطورية بدل تكرار 160 سطراً (كانت نسخة ثابتة بلا اسم/مؤقت)
    ModernLiquidGlassCallScreen(
        isVideo = isVideo, isMuted = isMuted, isVideoEnabled = isVideoEnabled,
        onToggleMic = onToggleMic, onToggleVideo = onToggleVideo,
        onToggleSpeaker = onToggleSpeaker, onEndCall = onEndCall,
        onToggleBackground = onToggleBackground
    )
}
