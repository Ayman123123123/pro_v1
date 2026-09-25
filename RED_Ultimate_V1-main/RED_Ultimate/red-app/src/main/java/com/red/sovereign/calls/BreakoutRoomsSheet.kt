package com.red.sovereign.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesPrimary

@Composable
fun BreakoutRoomsSheet(
    meetingId: String,
    isHost: Boolean,
    onDismiss: () -> Unit,
    onCreateRooms: (Int, Boolean) -> Unit,
    onAssignMember: (String, String) -> Unit,
    onMoveMember: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onBroadcastMessage: (String) -> Unit,
    onCloseAllRooms: () -> Unit,
    onTimerAction: (String, Boolean) -> Unit,
) {
    val rooms = ZoomRuntime.breakoutRooms
    val state = ZoomRuntime.state
    val joinedMembers = when (state) {
        is ZoomUiState.Active -> state.members.filter { it.status == ZoomMemberStatus.JOINED }
        is ZoomUiState.Ringing -> state.members.filter { it.status == ZoomMemberStatus.JOINED }
        else -> emptyList()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var roomCount by remember { mutableStateOf(3) }
    var autoAssign by remember { mutableStateOf(true) }
    var broadcastMessage by remember { mutableStateOf("") }
    var selectedRoomForTimer by remember { mutableStateOf<String?>(null) }
    var timerMinutes by remember { mutableStateOf(5) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("غرف الانقسام", color = scheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isHost && rooms.isNotEmpty()) {
                    Button(
                        onClick = { onCloseAllRooms() },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.error.copy(alpha = 0.2f)),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Close, "إغلاق الكل", tint = scheme.error, modifier = Modifier.size(16.dp))
                            Text("إغلاق الكل", color = scheme.error, fontSize = 12.sp)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .clickable { onDismiss() }
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp)
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { if (isHost) showCreateDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "غرف الانقسام", tint = YounesPrimary, modifier = Modifier.size(48.dp))
                    Text(
                        if (isHost) "أنشئ غرف انقسام لتقسيم المشاركين" else "لا توجد غرف انقسام نشطة",
                        color = if (isHost) YounesPrimary else scheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (isHost) {
                        Text("اضغط لإنشاء الغرف", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rooms, key = { it.id }) { room ->
                    BreakoutRoomCard(
                        room = room,
                        allMembers = joinedMembers,
                        isHost = isHost,
                        onAssignMember = onAssignMember,
                        onMoveMember = onMoveMember,
                        onDeleteRoom = onDeleteRoom,
                        onTimerAction = onTimerAction,
                        selectedRoomForTimer = selectedRoomForTimer,
                        onSelectTimer = { selectedRoomForTimer = it },
                        timerMinutes = timerMinutes,
                        onTimerMinutesChange = { timerMinutes = it }
                    )
                }
            }
        }

        if (isHost) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = broadcastMessage,
                        onValueChange = { broadcastMessage = it },
                        placeholder = { Text("رسالة بث لجميع الغرف…", color = scheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = scheme.outline.copy(alpha = 0.5f),
                            focusedBorderColor = YounesPrimary,
                            focusedLabelColor = YounesPrimary,
                            unfocusedTextColor = scheme.onSurface,
                            focusedTextColor = scheme.onSurface,
                            unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                            focusedPlaceholderColor = scheme.onSurfaceVariant
                        )
                    )
                    Button(
                        onClick = {
                            // الغرف: قصّ الفراغات وحدّ الطول — رسالة البث كانت تُرسل خاماً بلا حد.
                            val msg = broadcastMessage.trim().take(200)
                            if (msg.isNotBlank()) {
                                onBroadcastMessage(msg)
                                broadcastMessage = ""
                            }
                        },
                        enabled = broadcastMessage.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = YounesPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("بث", color = Color(0xFF06090F), fontWeight = FontWeight.Bold)
                    }
                }

                if (rooms.isEmpty() || rooms.size < 10) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        // تباين: حاوية/محتوى من الثيم بدل زمرد على زمرد شفاف (tone-on-tone).
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primaryContainer,
                            contentColor = scheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, "إضافة", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (rooms.isEmpty()) "إنشاء غرف الانقسام" else "إضافة غرف إضافية",
                                color = scheme.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء غرف الانقسام", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("عدد الغرف:", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { roomCount = (roomCount - 1).coerceAtLeast(2) }) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص", tint = YounesPrimary)
                            }
                            Text("$roomCount", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                            // الغرف: سقف 10 يطابق بوابة الإضافة (rooms.size < 10) — كان 50 بلا سقف واقعي.
                            IconButton(onClick = { roomCount = (roomCount + 1).coerceAtMost(10) }) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة", tint = YounesPrimary)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تعيين تلقائي للمشاركين", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Switch(
                            checked = autoAssign,
                            onCheckedChange = { autoAssign = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = YounesPrimary, checkedTrackColor = YounesPrimary.copy(alpha = 0.5f))
                        )
                    }
                    Text(
                        if (autoAssign) "سيتم توزيع المشاركين بالتساوي على الغرف" else "ستبقى الغرف فارغة — أضف المشاركين يدوياً",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateRooms(roomCount, autoAssign)
                    showCreateDialog = false
                }) {
                    Text("إنشاء", color = YounesPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun BreakoutRoomCard(
    room: ZoomBreakoutRoom,
    allMembers: List<ZoomMember>,
    isHost: Boolean,
    onAssignMember: (String, String) -> Unit,
    onMoveMember: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onTimerAction: (String, Boolean) -> Unit,
    selectedRoomForTimer: String?,
    onSelectTimer: (String?) -> Unit,
    timerMinutes: Int,
    onTimerMinutesChange: (Int) -> Unit
) {
    val assignedMembers = allMembers.filter { it.userId in room.participantIds }
    val unassignedMembers = allMembers.filter { it.userId !in room.participantIds }
    val isTimerSelected = selectedRoomForTimer == room.id
    val cardScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTimerSelected) YounesPrimary.copy(alpha = 0.12f) else cardScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isTimerSelected) BorderStroke(1.5.dp, YounesPrimary) else BorderStroke(1.dp, cardScheme.outline.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(YounesPrimary, Color(0xFF0D47A1))))
                    ) {
                        Text(
                            room.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Column {
                        Text(room.name, color = cardScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${assignedMembers.size} / ${room.participantIds.size + unassignedMembers.size} مشارك",
                            color = cardScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isHost) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!isTimerSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cardScheme.surface)
                                    .clickable { onSelectTimer(room.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = "مؤقت", tint = YounesPrimary, modifier = Modifier.size(14.dp))
                                    Text("مؤقت", color = YounesPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cardScheme.surface)
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onTimerMinutesChange((timerMinutes - 1).coerceAtLeast(1)) }) {
                                            Icon(Icons.Default.Remove, contentDescription = "إنقاص المؤقت", tint = YounesPrimary, modifier = Modifier.size(14.dp))
                                        }
                                        Text("$timerMinutes د", color = cardScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp).padding(horizontal = 8.dp))
                                        IconButton(onClick = { onTimerMinutesChange((timerMinutes + 1).coerceAtMost(120)) }) {
                                            Icon(Icons.Default.Add, contentDescription = "زيادة المؤقت", tint = YounesPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Button(
                                    onClick = {
                                        onTimerAction(room.id, true)
                                        onSelectTimer(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = YounesPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("ابدأ", color = Color(0xFF06090F), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                                Button(
                                    onClick = {
                                        onTimerAction(room.id, false)
                                        onSelectTimer(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = cardScheme.error.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("إلغاء", color = cardScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(cardScheme.error.copy(alpha = 0.15f))
                                .clickable { onDeleteRoom(room.id) }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الغرفة", tint = cardScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (assignedMembers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    assignedMembers.forEach { member ->
                        BreakoutMemberRow(
                            member = member,
                            isAssigned = true,
                            isHost = isHost,
                            onMove = { onMoveMember(room.id, member.userId) },
                            onRemove = { onMoveMember("", member.userId) }
                        )
                    }
                }
            }

            if (isHost && unassignedMembers.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("غير معينين — اضغط + لتعيين", color = cardScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    ) {
                        items(unassignedMembers, key = { it.userId }) { member ->
                            BreakoutMemberRow(
                                member = member,
                                isAssigned = false,
                                isHost = isHost,
                                onMove = { onAssignMember(room.id, member.userId) },
                                // الغرف: صف غير معيّن لا يحذف الغرفة — لا عملية هنا (زر الإزالة مخفي أصلاً).
                                onRemove = { }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakoutMemberRow(
    member: ZoomMember,
    isAssigned: Boolean,
    isHost: Boolean,
    onMove: () -> Unit,
    onRemove: () -> Unit
) {
    val rowScheme = MaterialTheme.colorScheme
    // تباين: ألوان الثيم التكيفية بدل أخضر/كهرماني ثابتين يختفيان على الثيم الداكن.
    val statusColor = when (member.status) {
        ZoomMemberStatus.JOINED -> if (member.isMuted) rowScheme.tertiary else rowScheme.primary
        ZoomMemberStatus.RINGING -> rowScheme.secondary
        else -> rowScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAssigned) rowScheme.surface.copy(alpha = 0.6f) else rowScheme.surface.copy(alpha = 0.35f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))))
            ) {
                Text(
                    member.displayName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Column {
                Text(member.displayName, color = rowScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Text(
                        when (member.status) {
                            ZoomMemberStatus.JOINED -> if (member.isMuted) "متصل · مكتوم" else "متصل"
                            ZoomMemberStatus.RINGING -> "يرن…"
                            ZoomMemberStatus.LEFT -> "غادر"
                            ZoomMemberStatus.NO_ANSWER -> "لم يرد"
                            else -> member.status.name
                        },
                        color = rowScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (member.hasVideo) Icon(Icons.Default.Videocam, contentDescription = "فيديو", tint = YounesPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }

        if (isHost) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isAssigned) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "إزالة من الغرفة", tint = rowScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onMove) {
                    Icon(if (isAssigned) Icons.Default.SwapHoriz else Icons.Default.AddCircle, contentDescription = if (isAssigned) "نقل" else "تعيين", tint = YounesPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
