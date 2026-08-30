package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
 * شاشة تحكم المضيف — Host Controls Screen
 *
 * متاحة فقط للمضيف (Host) في المكالمات الجماعية. تتيح:
 * - إدارة المشاركين (كتم، طرد، ترقية)
 * - التحكم في الإعدادات الجماعية (كاميرا، ميكروفون)
 * - إدارة Breakout Rooms
 * - بدء/إيقاف التسجيل
 * - مشاركة الشاشة
 */
@Composable
fun HostControlsScreen(
    groupCallId: String = "",
    onBack: () -> Unit = {},
    onKickParticipant: (String) -> Unit = {},
    onMuteAll: (Boolean) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {}
) {
    var isRecording by remember { mutableStateOf(false) }
    var showParticipantMenu by remember { mutableStateOf(false) }
    var selectedParticipant by remember { mutableStateOf<String?>(null) }

    // Mock participants — في التطبيق الفعلي يتم جلبها من GroupCallService
    val participants = remember {
        listOf(
            ParticipantInfo("user-001", "أحمد محمد", true, true, true, true),
            ParticipantInfo("user-002", "محمد علي", false, true, false, true),
            ParticipantInfo("user-003", "فاطمة أحمد", false, false, true, false),
            ParticipantInfo("user-004", "خالد عبدالله", false, true, true, true),
            ParticipantInfo("user-005", "نورة سعيد", false, false, false, true),
        )
    }

    val host = participants.first { it.isHost }
    val others = participants.filter { !it.isHost }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحكم المضيف", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
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
                            text = { Text("إدارة المشاركين", color = Color.White) },
                            onClick = { showParticipantMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("إعدادات المجموعة", color = Color.White) },
                            onClick = { showParticipantMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("خروج المضيف", color = Color.Red) },
                            onClick = { showParticipantMenu = false }
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

            // Host info
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
                        Text("أنت المضيف", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text(host.name, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick actions
            Text("إجراءات سريعة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        if (isRecording) onStopRecording() else onStartRecording()
                        isRecording = !isRecording
                    }
                )
                QuickActionButton(
                    icon = Icons.Default.Mic,
                    label = "كتم الكل",
                    color = YounesEmerald,
                    onClick = { onMuteAll(true) }
                )
                QuickActionButton(
                    icon = Icons.Default.Videocam,
                    label = "كاميرا الكل",
                    color = AqyalGold,
                    onClick = { /* enable camera for all */ }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Participants list
            Text("المشاركين (${participants.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Host first
                item {
                    ParticipantCard(
                        participant = host,
                        isHost = true,
                        onMenuClick = { /* host menu */ }
                    )
                }
                // Others
                items(others) { participant ->
                    ParticipantCard(
                        participant = participant,
                        isHost = false,
                        onMenuClick = { selectedParticipant = participant.id }
                    )
                }
            }
        }
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
                // Avatar
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
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, "المزيد", tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun QuickActionButton(
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
