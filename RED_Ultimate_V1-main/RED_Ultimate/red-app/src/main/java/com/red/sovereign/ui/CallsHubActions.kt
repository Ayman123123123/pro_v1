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
    onGroupCallPicker: () -> Unit,
    onConference: () -> Unit,
    onLive: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1. بث مباشر (Live Broadcast) - TikTok Style
        CallBentoCard(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            icon = Icons.Rounded.LiveTv,
            title = "بث مباشر",
            subtitle = "مثل تيك توك وتطبيقات البث — مشاهدة أو إنشاء بث",
            accentColor = Color(0xFFE53935),
            gradientStart = Color(0xFF2A0A10),
            gradientEnd = Color(0xFF1A0005),
            cornerRadius = 22.dp,
            onClick = onLive,
            trailing = {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("مباشر", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 2. المكالمات الجماعية (Ad-hoc Group Calls) - Zoom/IMO Style
            CallBentoCard(
                modifier = Modifier.weight(1f).height(140.dp),
                icon = Icons.Rounded.GroupAdd,
                title = "مكالمات جماعية",
                subtitle = "مثل Zoom و IMO\nتختار من قائمة الأصدقاء",
                accentColor = Color(0xFF2AABEE),
                gradientStart = Color(0xFF2AABEE).copy(alpha = 0.18f),
                gradientEnd = Color(0xFF2AABEE).copy(alpha = 0.03f),
                cornerRadius = 22.dp,
                onClick = onGroupCallPicker
            )
            
            // 3. المؤتمرات (Conferences / Spaces) - Twitter X Spaces Style
            CallBentoCard(
                modifier = Modifier.weight(1f).height(140.dp),
                icon = Icons.Rounded.Headset,
                title = "مؤتمرات / مساحات",
                subtitle = "مثل مساحات تويتر (X)\nغرف صوتية ومرئية",
                accentColor = Color(0xFFA78BFA),
                gradientStart = Color(0xFFA78BFA).copy(alpha = 0.22f),
                gradientEnd = Color(0xFFA78BFA).copy(alpha = 0.05f),
                cornerRadius = 22.dp,
                onClick = onConference
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
