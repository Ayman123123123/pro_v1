package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شاشة تحكم المضيف — Host Controls Screen (Liquid Glass 2026)
 *
 * متاحة فقط للمضيف (Host) في المكالمات الجماعية. تتيح:
 * - إدارة المشاركين (كتم فردي/كل، طرد، ترقية)
 * - قفل/إلغاء قفل الاجتماع (Lock Meeting)
 * - التحكم في الكاميرات (إيقاف/تشغيل للكل)
 * - إدارة Breakout Rooms
 * - بدء/إيقاف التسجيل
 * - معالجة الاستثناءات ودعم دورة الحياة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostControlsScreen(
    groupCallId: String = "",
    isLocked: Boolean = false,
    onBack: () -> Unit = {},
    onKickParticipant: (String) -> Unit = {},
    onMuteAll: (Boolean) -> Unit = {},
    onToggleLock: (Boolean) -> Unit = {},
    onDisableCamerasAll: () -> Unit = {},
    onEnableCamerasAll: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onOpenBreakoutRooms: () -> Unit = {}
) {
    var isRecording by remember { mutableStateOf(false) }
    var meetingLocked by remember { mutableStateOf(isLocked) }
    var showParticipantMenu by remember { mutableStateOf(false) }
    var selectedParticipant by remember { mutableStateOf<ParticipantInfo?>(null) }
    var showKickDialog by remember { mutableStateOf(false) }

    // المشاركون — يمكن دمجهم مع ZoomRuntime أو عرض نموذج اختباري
    val participants = remember {
        listOf(
            ParticipantInfo("user-001", "أحمد محمد", true, true, true, true),
            ParticipantInfo("user-002", "محمد علي", false, true, false, true),
            ParticipantInfo("user-003", "فاطمة أحمد", false, false, true, false),
            ParticipantInfo("user-004", "خالد عبدالله", false, true, true, true),
            ParticipantInfo("user-005", "نورة سعيد", false, false, false, true),
        )
    }

    val host = participants.firstOrNull { it.isHost } ?: participants.first()
    val others = participants.filter { !it.isHost }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحكم المضيف الصادي", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showParticipantMenu = !showParticipantMenu }) {
                        Icon(Icons.Default.MoreVert, "المزيد", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showParticipantMenu,
                        onDismissRequest = { showParticipantMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (meetingLocked) "إلغاء قفل الاجتماع" else "قفل الاجتماع", color = Color.White) },
                            onClick = {
                                meetingLocked = !meetingLocked
                                onToggleLock(meetingLocked)
                                showParticipantMenu = false
                            },
                            leadingIcon = {
                                Icon(if (meetingLocked) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = AqyalGold)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("فتح غرف الانقسام", color = Color.White) },
                            onClick = {
                                onOpenBreakoutRooms()
                                showParticipantMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Group, null, tint = AqyalGold)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تعطيل الكاميرات للكل", color = Color.Red) },
                            onClick = {
                                onDisableCamerasAll()
                                showParticipantMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VideocamOff, null, tint = Color.Red)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Host info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AqyalGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, "مضيف", tint = Color(0xFF0A0F18), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("أنت المضيف (صلاحيات كاملة)", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text(host.name, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick actions
            Text("إجراءات المضيف السريعة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = if (isRecording) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                    label = if (isRecording) "إيقاف التسجيل" else "تسجيل",
                    color = if (isRecording) Color.Red else AqyalGold,
                    onClick = {
                        runCatching {
                            if (isRecording) onStopRecording() else onStartRecording()
                            isRecording = !isRecording
                        }
                    }
                )
                QuickActionButton(
                    icon = Icons.Default.MicOff,
                    label = "كتم الكل",
                    color = YounesEmerald,
                    onClick = { runCatching { onMuteAll(true) } }
                )
                QuickActionButton(
                    icon = if (meetingLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    label = if (meetingLocked) "إلغاء القفل" else "قفل الاجتماع",
                    color = if (meetingLocked) Color.Red else AqyalGold,
                    onClick = {
                        meetingLocked = !meetingLocked
                        runCatching { onToggleLock(meetingLocked) }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Participants list
            Text("المشاركون (${others.size + 1})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParticipantCard(
                    participant = host,
                    isHost = true,
                    onMenuClick = {}
                )
                others.forEach { participant ->
                    ParticipantCard(
                        participant = participant,
                        isHost = false,
                        onMenuClick = {
                            selectedParticipant = participant
                            showKickDialog = true
                        }
                    )
                }
            }
        }
    }

    // Kick / Manage participant dialog
    if (showKickDialog && selectedParticipant != null) {
        val target = selectedParticipant!!
        AlertDialog(
            onDismissRequest = {
                showKickDialog = false
                selectedParticipant = null
            },
            title = { Text("إدارة المشارك: ${target.name}", fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد طرد هذا المشارك من المكالمة الجماعية؟", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { onKickParticipant(target.id) }
                        showKickDialog = false
                        selectedParticipant = null
                    }
                ) {
                    Text("طرد", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showKickDialog = false
                        selectedParticipant = null
                    }
                ) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

data class ParticipantInfo(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val isMuted: Boolean,
    val isVideoOn: Boolean,
    val isSpeaking: Boolean
)

@Composable
fun ParticipantCard(participant: ParticipantInfo, isHost: Boolean, onMenuClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHost) SovereignColors.SurfaceDarkVariant.copy(alpha = 0.7f) else SovereignColors.SurfaceDarkVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (participant.isSpeaking) YounesEmerald else SovereignColors.SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    if (participant.isSpeaking) {
                        Icon(Icons.Default.Circle, "يتحدث", tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            participant.name.firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(participant.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        if (participant.isHost) {
                            Icon(Icons.Default.AdminPanelSettings, "مضيف", tint = AqyalGold, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (participant.isMuted) {
                            Icon(Icons.Default.MicOff, "مكتم", tint = Color.Red, modifier = Modifier.size(12.dp))
                        }
                        if (!participant.isVideoOn) {
                            Icon(Icons.Default.VideocamOff, "كاميرا مغلقة", tint = Color.Gray, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            if (!isHost) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, "خيارات", tint = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun RowScope.QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
