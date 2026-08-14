package com.red.sovereign.ui

import com.red.sovereign.calls.GroupCallService
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.ui.theme.*

// ===============================================================
// Calls Hub — Bento Grid احترافي (WhatsApp/Zoom/iMO Style)
// ===============================================================

@Composable
fun CallsHubLaunchers(
    onNewCall: () -> Unit,
    onGroupCallPicker: () -> Unit,
    onConference: () -> Unit,
    onSpace: () -> Unit,
    onLive: () -> Unit,
    onPstn: () -> Unit,
    onExplore: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // 1. بث مباشر (Live Broadcast) - TikTok Style (Banner)
        CallBentoCard(
            modifier = Modifier.fillMaxWidth().height(115.dp),
            icon = Icons.Rounded.LiveTv,
            title = "بث مباشر تفاعلي",
            subtitle = "مثل تيك توك وتطبيقات البث — إطلاق بثك الخاص أو المشاهدة والتفاعل",
            accentColor = Color(0xFFE53935),
            gradientStart = Color(0xFF2A0A10),
            gradientEnd = Color(0xFF1A0005),
            cornerRadius = 20.dp,
            onClick = onLive,
            trailing = {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("مباشر 🔴", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
        )

        // 2. المكالمات الجماعية والمؤتمرات
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // المكالمات الجماعية (Ad-hoc Group Calls) - Zoom/IMO Style
            CallBentoCard(
                modifier = Modifier.weight(1f).height(130.dp),
                icon = Icons.Rounded.GroupAdd,
                title = "مكالمات جماعية",
                subtitle = "Zoom و IMO\nاختيار أصدقاء للرنين",
                accentColor = Color(0xFF2AABEE),
                gradientStart = Color(0xFF2AABEE).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF2AABEE).copy(alpha = 0.03f),
                cornerRadius = 20.dp,
                onClick = onGroupCallPicker
            )
            
            // المؤتمرات (Conferences / SFU)
            CallBentoCard(
                modifier = Modifier.weight(1f).height(130.dp),
                icon = Icons.Rounded.Videocam,
                title = "مؤتمرات فيديو",
                subtitle = "غرف مرئية مشفرة\nعبر خادم SFU",
                accentColor = Color(0xFFA78BFA),
                gradientStart = Color(0xFFA78BFA).copy(alpha = 0.22f),
                gradientEnd = Color(0xFFA78BFA).copy(alpha = 0.05f),
                cornerRadius = 20.dp,
                onClick = onConference
            )
        }

        // 3. المساحات الصوتية والهاتف اليمني (DINSTAR)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // المساحات الصوتية (Twitter X Spaces)
            CallBentoCard(
                modifier = Modifier.weight(1f).height(130.dp),
                icon = Icons.Rounded.Headset,
                title = "مساحات صوتية",
                subtitle = "مثل Twitter Spaces\nصوت فقط بلا كاميرا",
                accentColor = Color(0xFF00C98C),
                gradientStart = Color(0xFF00C98C).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF00C98C).copy(alpha = 0.03f),
                cornerRadius = 20.dp,
                onClick = onSpace
            )
            
            // الهاتف اليمني (DINSTAR GSM)
            CallBentoCard(
                modifier = Modifier.weight(1f).height(130.dp),
                icon = Icons.Rounded.PhoneInTalk,
                title = "الهاتف اليمني",
                subtitle = "DINSTAR GSM\nاتصال بالشبكات المحلية",
                accentColor = AqyalGold,
                gradientStart = AqyalGold.copy(alpha = 0.20f),
                gradientEnd = AqyalGold.copy(alpha = 0.04f),
                cornerRadius = 20.dp,
                onClick = onPstn
            )
        }

        // 4. مكالمة جديدة E2EE واستكشاف البثوث
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // مكالمة فردية جديدة
            CallBentoCard(
                modifier = Modifier.weight(1f).height(120.dp),
                icon = Icons.Rounded.Phone,
                title = "مكالمة جديدة E2EE",
                subtitle = "صوت أو فيديو مباشر\nلمعرف أو جهة اتصال",
                accentColor = Color(0xFF00E676),
                gradientStart = Color(0xFF00E676).copy(alpha = 0.15f),
                gradientEnd = Color(0xFF00E676).copy(alpha = 0.03f),
                cornerRadius = 20.dp,
                onClick = onNewCall
            )

            // استكشاف البثوث والمساحات
            CallBentoCard(
                modifier = Modifier.weight(1f).height(120.dp),
                icon = Icons.Rounded.Explore,
                title = "استكشاف البثوث",
                subtitle = "البحث في البثوث العامة\nوالمساحات النشطة",
                accentColor = Color(0xFF25F4EE),
                gradientStart = Color(0xFF25F4EE).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF25F4EE).copy(alpha = 0.04f),
                cornerRadius = 20.dp,
                onClick = onExplore
            )
        }
    }
}

// ===============================================================
// Bento Card مكوّن قابل لإعادة الاستخدام
// ===============================================================

@Composable
private fun CallBentoCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    gradientStart: Color,
    gradientEnd: Color,
    cornerRadius: Dp,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(cornerRadius), ambientColor = accentColor.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                trailing?.invoke()
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.White.copy(0.58f), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun CallChip(icon: ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ===============================================================
// Group Call Picker — اختيار الأصدقاء (iMO/Zoom Style)
// ===============================================================

@Composable
fun GroupCallPickerDialog(
    contacts: List<PublicRedProfile>,
    onlineIds: Set<String>,
    onDismiss: () -> Unit,
    onStartCall: (selectedIds: List<String>, isVideo: Boolean) -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    var isVideo by remember { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E293B).copy(alpha = 0.95f), Color(0xFF0F172A).copy(alpha = 0.98f))))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Column {
                    Text("مكالمة جماعية", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("${selected.size} مختار · ${contacts.size} صديق", color = Color.White.copy(0.6f), fontSize = 13.sp)
                }

                // Toggle صوت/فيديو
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.05f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(true to "📹 فيديو", false to "🎙 صوت").forEach { (video, label) ->
                        val isActive = isVideo == video
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (isActive) Color(0xFF00C98C) else Color.Transparent)
                                .clickable { isVideo = video }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isActive) Color.White else Color.White.copy(0.5f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))

                // Contact List
                val sorted = contacts.sortedByDescending { it.redId in onlineIds }
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sorted, key = { it.redId }) { contact ->
                        val isOnline = contact.redId in onlineIds
                        val isSelected = contact.redId in selected
                        val bgColor by animateColorAsState(
                            if (isSelected) Color(0xFF00C98C).copy(0.15f) else Color.White.copy(0.03f),
                            label = "contact_bg"
                        )
                        val borderColor by animateColorAsState(
                            if (isSelected) Color(0xFF00C98C).copy(0.5f) else Color.Transparent,
                            label = "contact_border"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(bgColor).border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .clickable { if (isSelected) selected.remove(contact.redId) else selected.add(contact.redId) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF00C98C).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                    Text(contact.displayName.take(1).uppercase(), color = Color(0xFF00C98C), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                if (isOnline) Box(Modifier.size(12.dp).clip(CircleShape).border(2.dp, bgColor, CircleShape).background(Color(0xFF00C896)))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(contact.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                                Text(if (isOnline) "متصل الآن" else "@${contact.username}", color = if (isOnline) Color(0xFF00C98C) else Color.White.copy(0.5f), fontSize = 12.sp)
                            }
                            // Custom Checkbox
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(if (isSelected) Color(0xFF00C98C) else Color.White.copy(0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (contacts.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.PersonOff, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
                                    Text("لا توجد جهات اتصال بعد", color = Color.White.copy(0.5f))
                                    Text("أضف أصدقاء من تبويب الدردشات", color = Color.White.copy(0.4f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.White.copy(0.6f)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (selected.isNotEmpty()) onStartCall(selected.toList(), isVideo) },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C98C), disabledContainerColor = Color(0xFF00C98C).copy(0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Phone, null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("بدء ${if (isVideo) "فيديو" else "صوت"} (${selected.size})", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ===============================================================
// Conference Hub Dialog — مؤتمرات ومساحات صوتية
// ===============================================================

@Composable
fun ConferenceHubDialog(
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onJoinExisting: (roomId: String) -> Unit
) {
    var isJoining by remember { mutableStateOf(false) }
    var roomIdInput by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.7f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))))
                    .border(1.dp, Color(0xFFA78BFA).copy(0.3f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFA78BFA).copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Headset, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("المؤتمرات والمساحات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("نمط Twitter/X Spaces و Zoom", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.7f))
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))

                if (!isJoining) {
                    // Option 1: Create New
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFA78BFA).copy(0.2f), Color(0xFFA78BFA).copy(0.05f))))
                            .border(1.dp, Color(0xFFA78BFA).copy(0.4f), RoundedCornerShape(16.dp))
                            .clickable {
                                onDismiss()
                                onCreateNew()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFA78BFA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("إنشاء جلسة / مساحة جديدة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("مساحة صوتية أو مؤتمر فيديو، دعوة أصدقاء وتخصيص كامل", color = Color.White.copy(0.65f), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }

                    // Option 2: Join Existing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.04f))
                            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                            .clickable { isJoining = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MeetingRoom, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("الانضمام إلى جلسة قائمة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("أدخل رمز أو معرّف الغرفة للدخول الفوري", color = Color.White.copy(0.65f), fontSize = 11.sp)
                        }
                    }
                } else {
                    // Join Form
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("أدخل معرّف الغرفة أو رابط المؤتمر:", color = Color.White.copy(0.8f), fontSize = 13.sp)
                        OutlinedTextField(
                            value = roomIdInput,
                            onValueChange = { roomIdInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("مثال: room-123 أو majlis-01", color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA78BFA),
                                unfocusedBorderColor = Color.White.copy(0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { isJoining = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("رجوع", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    if (roomIdInput.isNotBlank()) {
                                        onJoinExisting(roomIdInput.trim())
                                        onDismiss()
                                    }
                                },
                                enabled = roomIdInput.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA78BFA)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("انضمام", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===============================================================
// Live Stream Hub Dialog — مركز البث المباشر (TikTok Style)
// ===============================================================

@Composable
fun LiveStreamHubDialog(
    onDismiss: () -> Unit,
    onStartBroadcasting: (title: String, isPrivate: Boolean, password: String) -> Unit,
    onWatchStream: (streamId: String) -> Unit
) {
    var mode by remember { mutableStateOf<Int>(0) } // 0: choice, 1: create, 2: watch
    var streamTitle by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var streamPassword by remember { mutableStateOf("") }
    var watchStreamId by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.75f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E0A14), Color(0xFF0F0A1A))))
                    .border(1.dp, Color(0xFFF91850).copy(0.35f), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFF91850), Color(0xFF25F4EE)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("مركز البث المباشر 🔴", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("نمط TikTok — بث تفاعلي وإعجابات حية", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.7f))
                    }
                }

                HorizontalDivider(color = Color.White.copy(0.1f))

                when (mode) {
                    0 -> {
                        // Choice 1: Broadcast
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFF91850).copy(0.25f), Color(0xFFF91850).copy(0.05f))))
                                .border(1.dp, Color(0xFFF91850).copy(0.5f), RoundedCornerShape(16.dp))
                                .clickable { mode = 1 }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF91850)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("بدء بث مباشر جديد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("بث صوت وفيديو لجمهورك مع تفاعل وشات فوري", color = Color.White.copy(0.65f), fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }

                        // Choice 2: Watch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(0.04f))
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                                .clickable { mode = 2 }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF25F4EE), modifier = Modifier.size(26.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("مشاهدة بث مباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("أدخل معرف البث للانضمام والمشاهدة", color = Color.White.copy(0.65f), fontSize = 11.sp)
                            }
                        }
                    }
                    1 -> {
                        // Create Stream Form
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("عنوان البث:", color = Color.White.copy(0.8f), fontSize = 13.sp)
                            OutlinedTextField(
                                value = streamTitle,
                                onValueChange = { streamTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("مثال: بث سيادي مباشر", color = Color.Gray) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFF91850),
                                    unfocusedBorderColor = Color.White.copy(0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(0.04f))
                                    .clickable { isPrivate = !isPrivate }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("بث خاص بكلمة سر 🔒", color = Color.White, fontSize = 13.sp)
                                Checkbox(
                                    checked = isPrivate,
                                    onCheckedChange = { isPrivate = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF91850))
                                )
                            }

                            if (isPrivate) {
                                OutlinedTextField(
                                    value = streamPassword,
                                    onValueChange = { streamPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("كلمة السر الخاصة بالبث", color = Color.Gray) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFF91850),
                                        unfocusedBorderColor = Color.White.copy(0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { mode = 0 },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("رجوع", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        onStartBroadcasting(streamTitle.ifBlank { "بث مباشر يونس" }, isPrivate, streamPassword)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF91850)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إطلاق البث 🔴", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    2 -> {
                        // Watch Stream Form
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("أدخل معرّف البث المباشر:", color = Color.White.copy(0.8f), fontSize = 13.sp)
                            OutlinedTextField(
                                value = watchStreamId,
                                onValueChange = { watchStreamId = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("مثال: stream-xyz أو رابط البث", color = Color.Gray) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF25F4EE),
                                    unfocusedBorderColor = Color.White.copy(0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { mode = 0 },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("رجوع", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        if (watchStreamId.isNotBlank()) {
                                            onWatchStream(watchStreamId.trim())
                                            onDismiss()
                                        }
                                    },
                                    enabled = watchStreamId.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25F4EE)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("مشاهدة", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===============================================================
// Permission Helper — طلب الصلاحية قبل المكالمة
// ===============================================================

@Composable
fun rememberCallPermissionLauncher(
    needCamera: Boolean = false,
    onGranted: () -> Unit,
    onDenied: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val perms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (needCamera) add(Manifest.permission.CAMERA)
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) onGranted() else onDenied() }
    return {
        val allGranted = perms.all { perm -> ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED }
        if (allGranted) onGranted() else launcher.launch(perms.toTypedArray())
    }
}

// ===============================================================
// Glyph helper
// ===============================================================

fun callTypeGlyph(type: String, route: String): Pair<ImageVector, Color> = when {
    route == "DINSTAR"      -> Icons.Rounded.PhoneInTalk  to AqyalGold
    type  == "LIVE"         -> Icons.Rounded.LiveTv        to Color(0xFFE53935)
    type  == "SPACE"        -> Icons.Rounded.Headset       to Color(0xFFA78BFA)
    type  == "GROUP"        -> Icons.Rounded.Groups        to AqyalCyanGlow
    type  == "CONFERENCE"   -> Icons.Rounded.MeetingRoom   to Color(0xFF2AABEE)
    type  == "VIDEO"        -> Icons.Rounded.Videocam      to YounesEmerald
    else                    -> Icons.Rounded.Phone         to YounesEmerald
}
