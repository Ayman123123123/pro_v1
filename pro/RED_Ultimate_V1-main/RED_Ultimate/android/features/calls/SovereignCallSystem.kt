package com.red.sovereign.features.calls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer
import com.red.core.theme.SovereignColors

/**
 * 📞 YOUNES Sovereign Call System
 * نظام المكالمات الشامل — جميع الأنواع والأشكال
 * VoIP صوتي + فيديو 1080p + مؤتمر + بث مباشر + PSTN خطي + Audio Space
 */

// ━━━━━━━━━━━━ أنواع المكالمات ━━━━━━━━━━━━

enum class CallType(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val description: String
) {
    VOIP_AUDIO("صوتي VoIP", Icons.Rounded.Call, SovereignColors.VoipBlue, "مكالمة صوتية عبر الإنترنت بتشفير طرفي"),
    VOIP_VIDEO("فيديو VoIP", Icons.Rounded.Videocam, Color(0xFF9C27B0), "مكالمة فيديو 1080p بتشفير طرفي"),
    CONFERENCE("مؤتمر", Icons.Rounded.Groups, SovereignColors.Success, "مكالمة جماعية صوتية مع حتى 32 مشارك"),
    LIVE_BROADCAST("بث مباشر", Icons.Rounded.LiveTv, SovereignColors.LiveRed, "بث فيديو مباشر 1-إلى-عدة"),
    PSTN_DINSTAR("خطي اليمني", Icons.Rounded.SimCard, SovereignColors.DinstarGold, "مكالمة عبر بوابة DINSTAR GSM"),
    AUDIO_SPACE("غرفة صوتية", Icons.Rounded.Mic, SovereignColors.SpacePurple, "غرفة صوتية مفتوحة على الهواء")
}

enum class CallDirection(val label: String, val icon: ImageVector) {
    INCOMING("وارد", Icons.Rounded.CallReceived),
    OUTGOING("صادر", Icons.Rounded.CallMade),
    MISSED("فائت", Icons.Rounded.PhoneMissed)
}

enum class CallState { RINGING, CONNECTING, ACTIVE, ON_HOLD, ENDED }

// ━━━━━━━━━━━━ نماذج ━━━━━━━━━━━━

data class SovereignCall(
    val id: String,
    val type: CallType,
    val direction: CallDirection,
    val state: CallState = CallState.ENDED,
    val remoteName: String,
    val remoteAvatar: String? = null,
    val phoneNumber: String? = null, // للـ PSTN
    val duration: Long = 0, // مللي ثانية
    val timestamp: Long = System.currentTimeMillis(),
    val signalStrength: Int? = null, // للـ PSTN
    val port: Int? = null, // منفذ Dinstar
    val participants: List<String> = emptyList(), // للمؤتمر
    val viewerCount: Int = 0, // للبث
    val isRecorded: Boolean = false
)

// ━━━━━━━━━━━━ شاشة المكالمة النشطة المتقدمة ━━━━━━━━━━━━

@Composable
fun SovereignActiveCallScreen(
    call: SovereignCall,
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    isVideoOn: Boolean = true,
    callDuration: String = "00:00",
    voipEngine: VoipEngine? = null,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onHold: () -> Unit = {},
    onAddCall: () -> Unit = {},
    onTransfer: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onBluetooth: () -> Unit = {},
    onRecord: () -> Unit = {}
) {
    val pulseInfinite = rememberInfiniteTransition()
    val pulseAlpha by pulseInfinite.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "CallPulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (call.type) {
            CallType.VOIP_VIDEO -> {
                // فيديو بعيد + معاينة محلية
                if (voipEngine != null) {
                    AndroidView(
                        factory = { SurfaceViewRenderer(it).apply { init(voipEngine.getEglContext(), null) } },
                        modifier = Modifier.fillMaxSize()
                    )
                    // المعاينة المحلية
                    Surface(
                        modifier = Modifier.size(120.dp, 180.dp).align(Alignment.TopEnd).padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.DarkGray
                    ) {
                        AndroidView(
                            factory = { SurfaceViewRenderer(it).apply { init(voipEngine.getEglContext(), null); setMirror(true) } }
                        )
                    }
                } else {
                    // عنصر نائب
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(100.dp),
                                shape = CircleShape,
                                color = call.type.color.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(call.remoteName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            CallType.LIVE_BROADCAST -> {
                // بث مباشر
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = SovereignColors.LiveRed, shape = CircleShape) {
                            Text("LIVE", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(call.remoteName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("👁️ ${call.viewerCount} مشاهد", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            CallType.CONFERENCE, CallType.AUDIO_SPACE -> {
                // مؤتمر/غرفة صوتية — شبكة المشاركين
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(call.participants.ifEmpty { listOf(call.remoteName) }) { name ->
                        ParticipantCard(name, isActive = name == call.remoteName)
                    }
                }
            }
            else -> {
                // صوتي/PSTN — واجهة أنيقة
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // شارة النوع
                    Surface(
                        color = call.type.color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(call.type.icon, null, tint = call.type.color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(call.type.label, color = call.type.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // الأفاتار مع نبض
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulseAlpha)
                            .clip(CircleShape)
                            .background(call.type.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }

                    Spacer(Modifier.height(20.dp))

                    // الاسم
                    Text(call.remoteName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

                    // الرقم (PSTN)
                    call.phoneNumber?.let {
                        Text(it, color = Color.Gray, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    // حالة المكالمة
                    val stateText = when (call.state) {
                        CallState.RINGING -> "رنين..."
                        CallState.CONNECTING -> "جاري الاتصال..."
                        CallState.ACTIVE -> callDuration
                        CallState.ON_HOLD -> "معلقة"
                        CallState.ENDED -> "انتهت"
                    }
                    Text(stateText, color = Color.Gray, fontSize = 18.sp)

                    // إشارة Dinstar
                    call.signalStrength?.let { signal ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SignalCellularAlt, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("الإشارة: $signal%", color = SovereignColors.DinstarGold, fontSize = 12.sp)
                            call.port?.let { p -> Text(" • منفذ $p", color = Color.Gray, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        // ─── أزرار التحكم الموحدة ───
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // الصف الأول (للمكالمات المتقدمة)
            if (call.type in listOf(CallType.VOIP_VIDEO, CallType.CONFERENCE, CallType.PSTN_DINSTAR)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
                ) {
                    CallControlButton(Icons.Rounded.AddIcCall, "إضافة", onAddCall)
                    CallControlButton(Icons.Rounded.PhoneForwarded, "تحويل", onTransfer)
                    if (call.type == CallType.VOIP_VIDEO) {
                        CallControlButton(Icons.Rounded.FlipCameraAndroid, "قلب", onSwitchCamera)
                    }
                    CallControlButton(Icons.Rounded.FiberManualRecord, "تسجيل", onRecord, tint = if (call.isRecorded) SovereignColors.Danger else Color.White)
                }
            }

            // الصف الرئيسي
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
            ) {
                // كتم
                CallControlButton(
                    icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (isMuted) "مكتوم" else "كتم",
                    onClick = onToggleMute,
                    tint = if (isMuted) SovereignColors.Danger else Color.White,
                    bgColor = if (isMuted) SovereignColors.Danger.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                )

                // إنهاء المكالمة
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = SovereignColors.Danger,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }

                // مكبر الصوت / فيديو
                if (call.type == CallType.VOIP_VIDEO) {
                    CallControlButton(
                        icon = if (isVideoOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = "فيديو",
                        onClick = onToggleVideo,
                        tint = if (!isVideoOn) SovereignColors.Danger else Color.White
                    )
                } else {
                    CallControlButton(
                        icon = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        label = "مكبر",
                        onClick = onToggleSpeaker,
                        tint = if (isSpeakerOn) SovereignColors.Cyan else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    bgColor: Color = Color.White.copy(alpha = 0.1f)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp).background(bgColor, CircleShape)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun ParticipantCard(name: String, isActive: Boolean) {
    val border = if (isActive) BorderStroke(2.dp, SovereignColors.Success) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SovereignColors.SurfaceNavy)
            .border(border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(SovereignColors.Success, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("متحدث", color = SovereignColors.Success, fontSize = 10.sp)
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ سجل المكالمات الموحد المتقدم ━━━━━━━━━━━━

@Composable
fun SovereignCallLogScreen(
    calls: List<SovereignCall> = emptyList(),
    onCallBack: (SovereignCall) -> Unit = {},
    onCallDetails: (SovereignCall) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf<CallType?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)
    ) {
        // الرأس
        Text("سجل المكالمات السيادي", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(12.dp))

        // فلاتر النوع
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("الكل") }
                )
            }
            items(CallType.entries.toList()) { type ->
                FilterChip(
                    selected = selectedFilter == type,
                    onClick = { selectedFilter = if (selectedFilter == type) null else type },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, modifier = Modifier.size(14.dp), tint = type.color)
                            Spacer(Modifier.width(4.dp))
                            Text(type.label, fontSize = 11.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = type.color.copy(alpha = 0.15f),
                        selectedLabelColor = type.color
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // القائمة
        val filtered = if (selectedFilter != null) calls.filter { it.type == selectedFilter } else calls

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered) { call ->
                SovereignCallLogItem(
                    call = call,
                    onCallBack = { onCallBack(call) },
                    onDetails = { onCallDetails(call) }
                )
            }
        }
    }
}

@Composable
private fun SovereignCallLogItem(
    call: SovereignCall,
    onCallBack: () -> Unit,
    onDetails: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDetails)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة النوع
            Box(
                modifier = Modifier.size(48.dp).background(call.type.color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(call.type.icon, null, tint = call.type.color, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(14.dp))

            // المعلومات
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(call.remoteName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))

                    // شارة النوع
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = call.type.color.copy(alpha = 0.15f)
                    ) {
                        Text(call.type.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = call.type.color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        call.direction.icon, null,
                        modifier = Modifier.size(12.dp),
                        tint = if (call.direction == CallDirection.MISSED) SovereignColors.Danger else Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    val durationText = if (call.duration > 0) formatCallDuration(call.duration) else "فائت"
                    Text(durationText, fontSize = 12.sp, color = Color.Gray)
                    call.signalStrength?.let { s ->
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.SignalCellularAlt, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(12.dp))
                        Text("$s%", fontSize = 10.sp, color = SovereignColors.DinstarGold)
                    }
                }
            }

            // زر إعادة الاتصال
            IconButton(
                onClick = onCallBack,
                modifier = Modifier.size(40.dp).background(call.type.color.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(call.type.icon, null, tint = call.type.color, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ━━━━━━━━━━━━ واجهة اختيار نوع المكالمة ━━━━━━━━━━━━

@Composable
fun CallTypePickerSheet(
    contactName: String,
    phoneNumber: String? = null,
    onCallTypeSelected: (CallType) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SovereignColors.Navy, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(24.dp)
    ) {
        // المقبض
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))

        Text("اتصال بـ $contactName", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        CallType.entries.forEach { type ->
            Surface(
                onClick = { onCallTypeSelected(type) },
                shape = RoundedCornerShape(12.dp),
                color = type.color.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, type.color.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(type.color.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(type.icon, null, tint = type.color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(type.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Text(type.description, fontSize = 11.sp, color = Color.Gray)
                    }
                    Icon(Icons.Rounded.ArrowForward, null, tint = type.color, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatCallDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        else -> "%d:%02d".format(minutes, seconds % 60)
    }
}
