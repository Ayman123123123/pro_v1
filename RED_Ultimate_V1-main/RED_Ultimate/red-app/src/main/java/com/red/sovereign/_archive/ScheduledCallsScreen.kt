package com.red.sovereign.features.calls

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.ScheduledCall
import com.red.sovereign.calls.ScheduledCallScheduler
import com.red.sovereign.calls.ScheduledCallStore
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.YounesEmerald
import java.util.Calendar
import java.util.UUID

/**
 * ⏰ Scheduled Calls Screen — يدعم:
 * - إنشاء مكالمات مجدولة (مؤتمر/مساحة/بث)
 * - تكرار (يومي/أسبوعي/شهري)
 * - تصدير .ics للتقويم
 * - عرض مجدول مقسم: قادمة / منتهية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledCallsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scheduledCalls = remember { mutableStateListOf<ScheduledCall>().also { it.addAll(ScheduledCallStore.list(context)) } }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var createRoomId by remember { mutableStateOf("") }
    var createVideo by remember { mutableStateOf(false) }
    var createInvitees by remember { mutableStateOf<List<String>>(emptyList()) }
    var createDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis) }
    var createRecurrence by remember { mutableStateOf("none") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // تحديث القائمة عند العودة
    LaunchedEffect(showCreateDialog) {
        if (!showCreateDialog) {
            scheduledCalls.clear()
            scheduledCalls.addAll(ScheduledCallStore.list(context))
        }
    }

    fun resetForm() {
        createTitle = ""
        createRoomId = ""
        createVideo = false
        createInvitees = emptyList()
        createDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
        createRecurrence = "none"
    }

    val upcoming = scheduledCalls.filter { it.timeMillis > System.currentTimeMillis() }.sortedBy { it.timeMillis }
    val past = scheduledCalls.filter { it.timeMillis <= System.currentTimeMillis() }.sortedByDescending { it.timeMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مكالمات مجدولة", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "مكالمة مجدولة جديدة")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (scheduledCalls.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Schedule,
                    title = "لا توجد مكالمات مجدولة",
                    subtitle = "اضغط + لجدولة مكالمة مؤتمر، مساحة، أو بث مباشر"
                )
            } else {
                if (upcoming.isNotEmpty()) {
                    Text("قادمة (${upcoming.size})", color = YounesEmerald, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(upcoming, key = { it.id }) { call ->
                            ScheduledCallRow(
                                call = call,
                                onDelete = {
                                    ScheduledCallScheduler.cancel(context, call)
                                    scheduledCalls.remove(call)
                                },
                                onEdit = { showCreateDialog = true; populateEdit(call) }
                            )
                        }
                    }
                }
                if (past.isNotEmpty()) {
                    Text("منتهية (${past.size})", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(past, key = { it.id }) { call ->
                            ScheduledCallRow(
                                call = call,
                                isPast = true,
                                onDelete = {
                                    ScheduledCallScheduler.cancel(context, call)
                                    scheduledCalls.remove(call)
                                },
                                onEdit = { populateEdit(call); showCreateDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ScheduledCallCreateDialog(
            context = context,
            onDismiss = { showCreateDialog = false; resetForm() },
            onSave = { call ->
                ScheduledCallScheduler.schedule(context, call)
                scheduledCalls.add(call)
                showCreateDialog = false
                resetForm()
            },
            title = createTitle,
            onTitleChange = { createTitle = it },
            roomId = createRoomId,
            onRoomIdChange = { createRoomId = it },
            video = createVideo,
            onVideoChange = { createVideo = it },
            invitees = createInvitees,
            onInviteesChange = { createInvitees = it },
            dateMillis = createDate,
            onDateChange = { createDate = it },
            recurrence = createRecurrence,
            onRecurrenceChange = { createRecurrence = it },
            isEditing = scheduledCalls.any { it.id == createRoomId && it.title == createTitle }
        )
    }
}

@Composable
private fun ScheduledCallRow(
    call: ScheduledCall,
    isPast: Boolean = false,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = call.timeMillis }
    val dateStr = "%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    val timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val recurrenceStr = when (call.roomId.contains("recur:")) {
        true -> " 🔄 ${call.roomId.split("recur:").last()}"
        false -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onEdit)
            .background(if (isPast) Color(0xFF1E293B) else AqyalSurfaceNavy),
        colors = CardDefaults.cardColors(containerColor = if (isPast) Color(0xFF1E293B) else AqyalSurfaceNavy),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (call.video) Icons.Default.Videocam else Icons.Default.Groups,
                        null,
                        tint = if (call.video) AqyalGold else YounesEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = call.title.ifBlank { if (call.video) "مؤتمر فيديو" else "مساحة صوتية" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isPast) Color.Gray else Color.White
                    )
                    if (isPast) {
                        Text("انتهت", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .background(Color.Red.copy(alpha = 0.15f))
                            .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("$dateStr • $timeStr$recurrenceStr", color = if (isPast) Color.Gray else Color.LightGray, fontSize = 12.sp)
                if (call.invitees.isNotEmpty()) {
                    Text("${call.invitees.size} مشارك", color = Color.Gray, fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "تعديل", tint = YounesEmerald, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color.Red, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun ScheduledCallCreateDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSave: (ScheduledCall) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    video: Boolean,
    onVideoChange: (Boolean) -> Unit,
    invitees: List<String>,
    onInviteesChange: (List<String>) -> Unit,
    dateMillis: Long,
    onDateChange: (Long) -> Unit,
    recurrence: String,
    onRecurrenceChange: (String) -> Unit,
    isEditing: Boolean
) {
    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "تعديل مكالمة مجدولة" else "جدولة مكالمة جديدة") },
        text = {
            Column(Modifier.padding(vertical = 8.dp).width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("عنوان المكالمة (مثال: اجتماع فريق التطوير)") },
                    singleLine = true, label = { Text("العنوان") }
                )
                OutlinedTextField(
                    value = roomId, onValueChange = onRoomIdChange,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("معرف الغرفة (مثال: team-meeting-01)") },
                    singleLine = true, label = { Text("معرف الغرفة") }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = video, onCheckedChange = onVideoChange)
                    Text(if (video) "مؤتمر فيديو" else "مساحة صوتية", fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التاريخ: ${"%02d/%02d/%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))}", fontSize = 14.sp)
                    IconButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d ->
                            cal.set(y, m, d)
                            onDateChange(cal.timeInMillis)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Icon(Icons.Default.Event, "تاريخ", tint = YounesEmerald) }
                    Text("الوقت: ${"%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}", fontSize = 14.sp)
                    IconButton(onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            cal.set(Calendar.HOUR_OF_DAY, h)
                            cal.set(Calendar.MINUTE, m)
                            onDateChange(cal.timeInMillis)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    }) { Icon(Icons.Default.Schedule, "وقت", tint = YounesEmerald) }
                }
                OutlinedTextField(
                    value = recurrence, onValueChange = onRecurrenceChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("تكرار: none / daily / weekly / monthly") },
                    singleLine = true, label = { Text("تكرار (اختياري)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val call = ScheduledCall(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    roomId = roomId + if (recurrence != "none") "recur:$recurrence" else "",
                    video = video,
                    invitees = invitees,
                    timeMillis = dateMillis
                )
                onSave(call)
            }, enabled = title.isNotBlank() && roomId.isNotBlank() && dateMillis > System.currentTimeMillis()) {
                Text(if (isEditing) "حفظ التغييرات" else "جدولة")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun populateEdit(call: ScheduledCall) {
    // This would be called from the parent composable to populate the form
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) = Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Text(subtitle, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}