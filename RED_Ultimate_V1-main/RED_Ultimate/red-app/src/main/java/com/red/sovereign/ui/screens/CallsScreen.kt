package com.red.sovereign.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.calls.*
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.YounesId
import com.red.sovereign.ui.theme.*
import com.red.sovereign.ui.GroupCallPickerDialog
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * CallsScreen - مركز المكالمات المطور بدون PSTN/DINSTAR
 * 
 * - مكالمات خاصة صوت منفصل وفيديو منفصل (أفضل من واتس وتيليجرام وزنجي)
 * - مكالمات مجموعات الدردشة صوت/فيديو كل على حدة
 * - مكالمات جماعية للأصدقاء تشبه زووم/إيمو منفصلة تماماً
 * - بث مباشر أفضل من تيك توك ويوتيوب (تفاعلات فقط)
 * - مؤتمرات ومساحات أفضل من تويتر X
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    ownUserId: String,
    history: CallHistoryViewModel,
    directory: DirectoryViewModel,
    myDisplayName: String = "",
    onExplore: () -> Unit = {}
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("الكل") }
    var query by remember { mutableStateOf("") }
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showGroupCallPicker by remember { mutableStateOf(false) }
    var showConferenceDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val callTarget = pendingCall?.first
        val callVideo = pendingCall?.second
        pendingCall = null
        val audio = grants[Manifest.permission.RECORD_AUDIO] == true
        val cam = callVideo == null || grants[Manifest.permission.CAMERA] == true
        if (audio && cam && callTarget != null) {
            YounesCallService.start(context, callTarget, callVideo ?: false)
        }
    }

    fun launchCall(target: String, video: Boolean) {
        if (!target.matches(Regex(YounesId.PATTERN))) return
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }.toTypedArray()
        val audioOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !video || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioOk && cameraOk) {
            YounesCallService.start(context, target, video)
        } else {
            pendingCall = target to video
            callPermissionLauncher.launch(perms)
        }
    }

    val filteredCalls = remember(history.calls, filter, query) {
        history.calls.filter { call ->
            val matchesFilter = when (filter) {
                "فائتة" -> call.status == "MISSED"
                "صوت" -> call.type == "VOICE" || call.type == "AUDIO"
                "فيديو" -> call.type == "VIDEO"
                "جماعية" -> call.type == "GROUP" || call.type == "CONFERENCE"
                "بث" -> call.type == "LIVE"
                "مساحات" -> call.type == "SPACE" || call.type == "AUDIO_SPACE"
                else -> true
            }
            val q = query.trim()
            val matchesQuery = q.isBlank() || call.peerLabel.contains(q, ignoreCase = true) || call.peerId.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    LaunchedEffect(Unit) {
        history.load()
        directory.refreshPresence()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مركز المكالمات - يونس", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { 
                CallsQuickLaunchers(
                    onPrivateCall = { showNewCallDialog = true },
                    onGroupCall = { showGroupCallPicker = true },
                    onConference = { showConferenceDialog = true },
                    onLive = { showLiveDialog = true },
                    onSpace = { showSpaceDialog = true },
                    onExplore = onExplore
                ) 
            }

            item { 
                OnlineContactsStrip(directory = directory, onCall = { person, video ->
                    launchCall(person.redId, video)
                }) 
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في المكالمات أو جهات الاتصال…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AqyalGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("الكل", "فائتة", "صوت", "فيديو", "جماعية", "بث", "مساحات")) { title ->
                        FilterChip(
                            selected = filter == title,
                            onClick = { filter = title },
                            label = { Text(title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AqyalGold.copy(alpha = 0.2f),
                                selectedLabelColor = AqyalGold
                            )
                        )
                    }
                }
            }

            item { Text("سجل المكالمات", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f)) }

            when {
                history.loading && history.calls.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AqyalGold)
                    }
                }
                history.error != null && history.calls.isEmpty() -> item {
                    CallsEmptyState(Icons.Default.History, "تعذر تحميل السجل", history.error.orEmpty())
                }
                filteredCalls.isEmpty() -> item {
                    CallsEmptyState(Icons.Default.History, "لا توجد مكالمات", "ستظهر هنا المكالمات الصوتية والفيديو والجماعية والبث والمساحات - كلها مشفرة E2EE.")
                }
                else -> items(filteredCalls, key = { it.id }) { call ->
                    CallHistoryRow(call = call, onAudioCall = {
                        launchCall(call.peerId, false)
                    }, onVideoCall = {
                        launchCall(call.peerId, true)
                    })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showNewCallDialog) NewCallDialog(
        onDismiss = { showNewCallDialog = false },
        onCall = { redId, video ->
            showNewCallDialog = false
            launchCall(redId, video)
        }
    )

    if (showGroupCallPicker) GroupCallPickerDialog(
        contacts = directory.contacts,
        onlineIds = directory.onlineIds.toSet(),
        onDismiss = { showGroupCallPicker = false },
        onStartCall = { selectedIds, isVideo ->
            showGroupCallPicker = false
            val selectedNames = selectedIds.map { id -> directory.contacts.find { it.redId == id }?.displayName ?: id }
            GroupCallService.startGroupCall(
                context = context,
                myUserId = ownUserId,
                inviteeIds = selectedIds,
                inviteeNames = selectedNames,
                isVideo = isVideo,
                hostName = myDisplayName
            )
        }
    )

    if (showConferenceDialog) ConferenceJoinDialog(
        onDismiss = { showConferenceDialog = false },
        onJoin = { room ->
            showConferenceDialog = false
            ConferenceService.join(context, room, ownUserId, true, asHost = true)
        }
    )

    if (showLiveDialog) LiveStreamDialog(
        onDismiss = { showLiveDialog = false },
        onStart = { room, title ->
            showLiveDialog = false
            LiveStreamService.start(context, room, ownUserId, true, title)
        },
        onJoin = { room ->
            showLiveDialog = false
            LiveStreamService.start(context, room, ownUserId, false)
        }
    )

    if (showSpaceDialog) SpaceDialog(
        onDismiss = { showSpaceDialog = false },
        onJoin = { room, asHost ->
            showSpaceDialog = false
            ConferenceService.join(context, room, ownUserId, false, asHost = asHost)
        }
    )
}

@Composable
private fun CallsQuickLaunchers(
    onPrivateCall: () -> Unit,
    onGroupCall: () -> Unit,
    onConference: () -> Unit,
    onLive: () -> Unit,
    onSpace: () -> Unit,
    onExplore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("إطلاق سريع - أفضل من كل المنافسين", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(listOf(
                Triple("صوت", Icons.Rounded.Call, Brush.horizontalGradient(listOf(SovereignColors.VoipBlue, SovereignColors.Cyan))) to onPrivateCall,
                Triple("فيديو", Icons.Rounded.Videocam, Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6)))) to onPrivateCall,
                Triple("مجموعة", Icons.Rounded.Groups, Brush.horizontalGradient(listOf(SovereignColors.Success, Color(0xFF059669)))) to onGroupCall,
                Triple("مؤتمر", Icons.Rounded.VideoCall, Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6)))) to onConference,
                Triple("بث", Icons.Rounded.LiveTv, SovereignGradients.live) to onLive,
                Triple("مساحة", Icons.Rounded.RecordVoiceOver, SovereignGradients.space) to onSpace,
                Triple("استكشاف", Icons.Rounded.Explore, SovereignGradients.royal) to onExplore
            )) { (data, action) ->
                val (label, icon, brush) = data
                QuickActionCard(label, icon, brush, action)
            }
        }
    }
}

@Composable
private fun QuickActionCard(label: String, icon: ImageVector, brush: Brush, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SovereignColors.SurfaceNavy)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OnlineContactsStrip(directory: DirectoryViewModel, onCall: (PublicRedProfile, Boolean) -> Unit) {
    val online = remember(directory.contacts, directory.onlineIds) {
        directory.contacts.filter { it.redId in directory.onlineIds }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("متصل الآن - مكالمات P2P فورية", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
            if (online.isNotEmpty()) {
                Text("${online.size}", fontSize = 12.sp, color = SovereignColors.Success)
            }
        }
        if (online.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SovereignColors.SurfaceNavy).padding(16.dp), contentAlignment = Alignment.Center) {
                Text("لا يوجد جهات اتصال متصلة حالياً", color = YounesMuted, fontSize = 13.sp)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(online, key = { it.redId }) { person ->
                    OnlineContactChip(person, onCall)
                }
            }
        }
    }
}

@Composable
private fun OnlineContactChip(person: PublicRedProfile, onCall: (PublicRedProfile, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(AqyalGold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(person.displayName.take(1), color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(SovereignColors.Success).border(2.dp, SovereignColors.Navy, CircleShape))
        }
        Spacer(Modifier.height(4.dp))
        Text(person.displayName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onCall(person, false) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Call, "اتصال صوتي", tint = SovereignColors.VoipBlue, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onCall(person, true) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Videocam, "اتصال فيديو", tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CallHistoryRow(call: CallHistoryItem, onAudioCall: () -> Unit, onVideoCall: () -> Unit) {
    val statusColor = when (call.status) {
        "MISSED" -> SovereignColors.Danger
        "REJECTED", "FAILED" -> YounesMuted
        else -> SovereignColors.Success
    }
    val directionIcon = when (call.direction) {
        "OUTGOING" -> Icons.AutoMirrored.Filled.CallMade
        "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived
        else -> Icons.AutoMirrored.Filled.CallMissed
    }
    val typeIcon = when (call.type) {
        "VIDEO" -> Icons.Rounded.Videocam
        "GROUP", "CONFERENCE" -> Icons.Rounded.Groups
        "LIVE" -> Icons.Rounded.LiveTv
        "SPACE", "AUDIO_SPACE" -> Icons.Rounded.RecordVoiceOver
        else -> Icons.Rounded.Call
    }
    val duration = call.computedDurationSeconds().formatCallDuration()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(AqyalGold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(typeIcon, null, tint = AqyalGold)
            }
            Column(Modifier.weight(1f)) {
                Text(call.peerLabel.takeIf { it.isNotBlank() } ?: call.peerId, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(directionIcon, null, modifier = Modifier.size(14.dp), tint = statusColor)
                    Text(callStatusLabel(call.status), fontSize = 12.sp, color = statusColor)
                    if (duration.isNotBlank()) {
                        Text("• $duration", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Text(formatCallTime(call.startedAt), fontSize = 11.sp, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onAudioCall, modifier = Modifier.size(40.dp).background(SovereignColors.VoipBlue.copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.Rounded.Call, null, tint = SovereignColors.VoipBlue, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onVideoCall, modifier = Modifier.size(40.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.15f), CircleShape)) {
                    Icon(Icons.Rounded.Videocam, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CallsEmptyState(icon: ImageVector, title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun NewCallDialog(onDismiss: () -> Unit, onCall: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var redId by remember { mutableStateOf("") }
    var video by remember { mutableStateOf(false) }
    val pattern = Regex(YounesId.PATTERN)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مكالمة جديدة عبر يونس - P2P مشفر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("أدخل معرّف يونس للاتصال به مباشرة (صوت منفصل وفيديو منفصل):", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = redId,
                    onValueChange = { redId = YounesId.normalizeInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(YounesId.PLACEHOLDER) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = video, onCheckedChange = { video = it })
                    Text("مكالمة فيديو - جودة عالية AV1 SVC")
                }
                Text("رنين فوري، E2EE، أفضل من واتس وتيليجرام", fontSize = 10.sp, color = AqyalGold)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCall(redId, video) },
                enabled = redId.matches(pattern)
            ) { Text(if (video) "اتصال فيديو" else "اتصال صوتي") }
        },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ConferenceJoinDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var room by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الانضمام إلى مؤتمر - أفضل من زووم") },
        text = {
            OutlinedTextField(
                value = room,
                onValueChange = { room = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("معرف الغرفة") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onJoin(room.trim()) }, enabled = room.trim().isNotBlank()) { Text("انضمام") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun LiveStreamDialog(
    onDismiss: () -> Unit,
    onStart: (String, String) -> Unit,
    onJoin: (String) -> Unit
) {
    var room by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var broadcaster by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مركز البث المباشر - أفضل من تيك توك ويوتيوب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = room, onValueChange = { room = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("معرف البث") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = broadcaster, onCheckedChange = { broadcaster = it }); Text("أنا المنتج") }
                if (broadcaster) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("عنوان البث") }, singleLine = true)
                }
                Text("WebRTC <500ms للمتفاعلين + LL-HLS للجمهور - تفاعلات فقط بدون هدايا", fontSize = 10.sp, color = AqyalGold)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (broadcaster) onStart(room.trim().ifBlank { "stream_${UUID.randomUUID().toString().take(8)}" }, title.trim().ifBlank { "بث مباشر" })
                else onJoin(room.trim())
            }, enabled = broadcaster || room.trim().isNotBlank()) { Text(if (broadcaster) "بدء بث" else "انضمام") }
        },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SpaceDialog(onDismiss: () -> Unit, onJoin: (String, Boolean) -> Unit) {
    var room by remember { mutableStateOf("") }
    var asHost by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مساحة صوتية - أفضل من تويتر X") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = room, onValueChange = { room = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("معرف المساحة (اختياري)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = asHost, onCheckedChange = { asHost = it }); Text("الانضمام كمضيف") }
                Text("13 متحدث + مستمعين لا نهائي + تفاعلات + تسجيل اختياري", fontSize = 10.sp, color = AqyalGold)
            }
        },
        confirmButton = { Button(onClick = { onJoin(room.trim().ifBlank { "space-${System.currentTimeMillis() % 100000}" }, asHost || room.isBlank()) }) { Text("دخول مساحة") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

private fun callStatusLabel(status: String): String = when (status) {
    "ANSWERED" -> "تم الرد"
    "MISSED" -> "فائتة"
    "REJECTED" -> "مرفوضة"
    "FAILED" -> "فاشلة"
    else -> status
}

private fun formatCallTime(value: String?): String {
    val ts = parseCallTimestamp(value) ?: return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "الآن"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} د"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} س"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale("ar")).format(Date(ts))
        else -> SimpleDateFormat("dd/MM/yyyy", Locale("ar")).format(Date(ts))
    }
}
