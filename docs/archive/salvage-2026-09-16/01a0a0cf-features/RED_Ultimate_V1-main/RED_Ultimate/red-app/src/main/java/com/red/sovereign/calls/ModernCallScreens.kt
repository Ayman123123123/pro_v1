package com.red.sovereign.calls

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*
import kotlinx.coroutines.delay

/**
 * شاشات مكالمات حديثة - أفضل من واتساب وتيليجرام
 * 
 * كل نوع مكالمة له واجهته المناسبة حسب عمله الأساسي:
 * - فردية: P2P مع رنين ومدة وحالة
 * - جماعية: شبكة أعضاء مع حالة كل عضو
 * - مؤتمر: حتى 100 مع غرف جانبية
 * - بث مباشر: مذيع + مشاهدين + دردشة
 * - مساحة صوتية: أدوار مضيف/متحدث/مستمع
 * - هاتف يمني: لوحة ذهبية مع كشف مشغل
 * - محلي P2P: بلا إنترنت
 */

// شاشة مكالمة فردية حديثة - أفضل من واتساب
@Composable
fun ModernOneToOneCallScreen(
    peerName: String,
    peerId: String,
    isVideo: Boolean,
    isOutgoing: Boolean,
    ringingState: RingingState,
    durationMs: Long = 0,
    isMuted: Boolean = false,
    isSpeaker: Boolean = false,
    isCameraOn: Boolean = true,
    networkQuality: String = "EXCELLENT",
    onMute: () -> Unit = {},
    onSpeaker: () -> Unit = {},
    onCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onEnd: () -> Unit = {},
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(YounesMidnight, YounesVoid, Color(0xFF0A1220))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // هالات ملونة خلفية
        Box(
            Modifier
                .size(400.dp)
                .clip(CircleShape)
                .background(YounesPrimary.copy(alpha = 0.08f))
                .blur(40.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
        )
        Box(
            Modifier
                .size(300.dp)
                .clip(CircleShape)
                .background(YounesCobalt.copy(alpha = 0.06f))
                .blur(30.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 100.dp)
        )
        
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // أعلى: معلومات المتصل وحالة الرنين
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                
                // أفاتار مع نبض
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(if (ringingState == RingingState.RINGING) pulse else 1f)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt)))
                        .border(3.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        peerName.take(1).uppercase().ifBlank { peerId.take(1).uppercase() },
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = YounesOnPrimary
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
                Text(
                    peerName.ifBlank { peerId },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    peerId,
                    fontSize = 12.sp,
                    color = YounesCobalt,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(12.dp))
                
                // حالة الرنين
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (ringingState) {
                        RingingState.CONNECTING -> YounesAccent.copy(alpha = 0.15f)
                        RingingState.RINGING -> YounesPrimary.copy(alpha = 0.15f)
                        RingingState.WAKING_UP -> YounesCobalt.copy(alpha = 0.15f)
                        else -> YounesSurface2
                    }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (ringingState == RingingState.CONNECTING || ringingState == RingingState.WAKING_UP) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = YounesAccent)
                        } else if (ringingState == RingingState.RINGING) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(YounesPrimary)
                            )
                        }
                        Text(
                            when (ringingState) {
                                RingingState.CONNECTING -> "جاري الاتصال..."
                                RingingState.RINGING -> "يرن على جهاز المستلم"
                                RingingState.WAKING_UP -> "جاري إيقاظ الجهاز..."
                                RingingState.NO_ANSWER -> "لا يوجد رد"
                                RingingState.BUSY -> "المستلم مشغول"
                                RingingState.DECLINED -> "تم الرفض"
                            },
                            fontSize = 13.sp,
                            color = when (ringingState) {
                                RingingState.CONNECTING -> YounesAccent
                                RingingState.RINGING -> YounesPrimary
                                else -> YounesMuted
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (durationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatCallDuration(durationMs),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                
                // جودة الشبكة
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NetworkQualityBadge(quality = networkQuality)
                    if (isVideo) {
                        Surface(shape = RoundedCornerShape(12.dp), color = YounesCobalt.copy(alpha = 0.15f)) {
                            Text("فيديو E2EE", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = YounesCobalt)
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(12.dp), color = YounesPrimary.copy(alpha = 0.15f)) {
                            Text("صوت E2EE", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = YounesPrimary)
                        }
                    }
                }
            }
            
            // وسط: فيديو إن وجد
            if (isVideo) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(YounesSurface1),
                    contentAlignment = Alignment.Center
                ) {
                    Text("فيديو ${if (isCameraOn) "نشط" else "متوقف"}", color = YounesMuted)
                }
            }
            
            // أسفل: أزرار التحكم
            if (isOutgoing || durationMs > 0) {
                // مكالمة صادرة أو نشطة
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernCallButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "إلغاء كتم" else "كتم",
                        isActive = isMuted,
                        color = if (isMuted) Color(0xFFF44336) else YounesSurface2,
                        onClick = onMute
                    )
                    ModernCallButton(
                        icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = if (isSpeaker) "سماعة" else "مكبر",
                        isActive = isSpeaker,
                        color = if (isSpeaker) YounesAccent else YounesSurface2,
                        onClick = onSpeaker
                    )
                    if (isVideo) {
                        ModernCallButton(
                            icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = "كاميرا",
                            isActive = !isCameraOn,
                            color = YounesSurface2,
                            onClick = onCamera
                        )
                        ModernCallButton(
                            icon = Icons.Default.Cameraswitch,
                            label = "تقليب",
                            color = YounesSurface2,
                            onClick = onSwitchCamera
                        )
                    }
                    ModernCallButton(
                        icon = Icons.Default.CallEnd,
                        label = "إنهاء",
                        isActive = true,
                        color = Color(0xFFF44336),
                        isLarge = true,
                        onClick = onEnd
                    )
                }
            } else {
                // مكالمة واردة
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ModernCallButton(
                        icon = Icons.Default.CallEnd,
                        label = "رفض",
                        color = YounesSurface2,
                        isLarge = true,
                        onClick = onDecline
                    )
                    ModernCallButton(
                        icon = Icons.Default.Call,
                        label = "قبول",
                        isActive = true,
                        color = YounesPrimary,
                        isLarge = true,
                        onClick = onAccept
                    )
                }
            }
        }
    }
}

// زر مكالمة حديث
@Composable
fun ModernCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    color: Color,
    isLarge: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(if (isLarge) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(color)
        ) {
            Icon(icon, label, tint = if (isActive || color == Color(0xFFF44336) || color == YounesPrimary) Color.White else Color.White, modifier = Modifier.size(if (isLarge) 28.dp else 22.dp))
        }
        Text(label, fontSize = 11.sp, color = YounesMuted, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}

// شارة جودة شبكة
@Composable
fun NetworkQualityBadge(quality: String) {
    val (label, color) = when (quality) {
        "EXCELLENT" -> "ممتازة" to Color(0xFF00C98C)
        "GOOD" -> "جيدة" to Color(0xFF4D9FE8)
        "FAIR" -> "مقبولة" to Color(0xFFF0C674)
        "POOR" -> "ضعيفة" to Color(0xFFF25C5C)
        else -> quality to YounesMuted
    }
    
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

fun formatCallDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

// شاشة مكالمة جماعية حديثة
@Composable
fun ModernGroupCallScreen(
    groupId: String?,
    hostName: String,
    participants: List<GroupCallParticipant>,
    isVideo: Boolean,
    isMuted: Boolean,
    onMute: () -> Unit,
    onEnd: () -> Unit,
    onAddParticipant: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(YounesMidnight)
            .padding(16.dp)
    ) {
        // رأس
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("مكالمة جماعية ${if (isVideo) "فيديو" else "صوتية"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${participants.size} مشارك - ${if (participants.count { it.status == "CONNECTED" } > 0) "${participants.count { it.status == "CONNECTED" }} متصل" else "يرن الجميع"}", color = YounesMuted, fontSize = 12.sp)
            }
            Surface(shape = CircleShape, color = YounesSurface2) {
                IconButton(onClick = onAddParticipant, Modifier.size(36.dp)) {
                    Icon(Icons.Default.PersonAdd, "إضافة", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // شبكة مشاركين
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(if (isVideo) 2 else 3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(participants.size) { idx ->
                val p = participants[idx]
                GroupParticipantCard(participant = p, isVideo = isVideo)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // أدوات
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ModernCallButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "كتم", isActive = isMuted, color = if (isMuted) Color(0xFFF44336) else YounesSurface2, onClick = onMute)
            ModernCallButton(icon = Icons.Default.CallEnd, label = "إنهاء", isActive = true, color = Color(0xFFF44336), isLarge = true, onClick = onEnd)
            ModernCallButton(icon = Icons.Default.Videocam, label = "فيديو", color = YounesSurface2, onClick = {})
        }
    }
}

data class GroupCallParticipant(
    val redId: String,
    val name: String,
    val status: String, // RINGING, CONNECTED, MUTED, DECLINED
    val isVideoOn: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false
)

@Composable
fun GroupParticipantCard(participant: GroupCallParticipant, isVideo: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (participant.isSpeaking) YounesPrimary.copy(alpha = 0.2f) else YounesSurface1
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (participant.isSpeaking) 2.dp else 0.dp,
                if (participant.isSpeaking) YounesPrimary else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt))),
                contentAlignment = Alignment.Center
            ) {
                Text(participant.name.take(1).uppercase(), color = YounesOnPrimary, fontWeight = FontWeight.Bold)
            }
            Text(participant.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                when (participant.status) {
                    "RINGING" -> "يرن..."
                    "CONNECTED" -> if (participant.isMuted) "مكتوم" else "متصل"
                    "DECLINED" -> "رفض"
                    else -> participant.status
                },
                color = when (participant.status) {
                    "RINGING" -> YounesAccent
                    "CONNECTED" -> if (participant.isMuted) YounesMuted else YounesPrimary
                    else -> YounesMuted
                },
                fontSize = 10.sp
            )
            if (isVideo) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(YounesSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (participant.isVideoOn) "فيديو" else "كاميرا متوقفة", fontSize = 10.sp, color = YounesMuted)
                }
            }
        }
    }
}

// شاشة بث مباشر حديثة
@Composable
fun ModernLiveStreamScreen(
    title: String,
    broadcasterName: String,
    viewerCount: Int,
    isBroadcaster: Boolean,
    isPrivate: Boolean,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(YounesMidnight)) {
        // فيديو البث
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(YounesSurface1),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔴", fontSize = 32.sp)
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("بواسطة $broadcasterName", color = YounesMuted, fontSize = 12.sp)
            }
            // شارة مباشر
            Surface(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF44336)
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Text("مباشر", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            // عداد مشاهدين
            Surface(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Visibility, "مشاهدون", tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("$viewerCount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (isPrivate) {
                Surface(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = YounesAccent.copy(alpha = 0.9f)
                ) {
                    Text("🔒 خاص بكلمة سر", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = YounesOnPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // دردشة وهدايا
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(YounesSurface1.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            Text("دردشة البث المباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("مرحباً بالجميع في البث! 🎉", color = YounesMuted, fontSize = 12.sp)
            
            Spacer(Modifier.height(12.dp))
            
            if (isBroadcaster) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ModernCallButton(icon = Icons.Default.Mic, label = "ميك", color = YounesSurface2, onClick = onToggleMic)
                    ModernCallButton(icon = Icons.Default.Videocam, label = "كاميرا", color = YounesSurface2, onClick = onToggleCamera)
                    ModernCallButton(icon = Icons.Default.CallEnd, label = "إنهاء البث", isActive = true, color = Color(0xFFF44336), onClick = onEnd)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = YounesPrimary)) {
                        Text("إرسال هدية 🎁")
                    }
                    OutlinedButton(onClick = onEnd) {
                        Text("مغادرة")
                    }
                }
            }
        }
    }
}
