package com.red.sovereign.features.chat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.red.sovereign.core.MessageStore
import com.red.sovereign.core.LocalMessage
import com.red.sovereign.core.RedConnectionService
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class RecurrenceRule(val label: String, val cronExpression: String?) {
    NONE("مرة واحدة", null),
    DAILY("يومياً", "0 0 * * *"),
    WEEKLY("أسبوعياً", "0 0 * * 0"),
    MONTHLY("شهرياً", "0 0 1 * *"),
    YEARLY("سنوياً", "0 0 1 1 *"),
    CUSTOM("مخصص", null);

    companion object {
        fun fromCron(cron: String?): RecurrenceRule {
            return values().firstOrNull { it.cronExpression == cron } ?: NONE
        }
    }
}

data class ScheduledMessage(
    val id: String,
    val recipientId: String,
    val recipientName: String,
    val messageText: String,
    val scheduledTime: Long,
    val createdAt: Long,
    val status: String = "PENDING",
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val cronExpression: String? = null,
    val timezone: String = "UTC",
    val nextRunTime: Long? = null,
    val runCount: Int = 0,
    val maxRuns: Int? = null,
    val endDate: Long? = null
) {
    val isRecurring: Boolean
        get() = recurrence != RecurrenceRule.NONE
    
    val isActive: Boolean
        get() = status == "PENDING" && (endDate == null || endDate!! > System.currentTimeMillis())
}

/**
 * P0-D: حمولة الرسالة المجدولة — JSON بدل ترميز `a|b|c|d`.
 * الترميز القديم كان يكسر أي رسالة تحتوي `|` (انقسام خاطئ وضياع النص).
 * القراءة تدعم القديم للترحيل (انظر decodeScheduledPayload) لكن الكتابة JSON فقط.
 */
@Serializable
private data class ScheduledPayload(
    val recipientId: String,
    val recipientName: String,
    val messageText: String,
    val scheduledTime: Long,
    val recurrence: String = "NONE",
    val cronExpression: String? = null,
    val timezone: String = "UTC",
    val nextRunTime: Long? = null,
    val runCount: Int = 0,
    val maxRuns: Int? = null,
    val endDate: Long? = null
)

private val ScheduledJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private const val KEY_MESSAGE_ID = "message_id"
private const val KEY_RECIPIENT_ID = "recipient_id"
private const val KEY_MESSAGE_TEXT = "message_text"
private const val KEY_CONVERSATION_ID = "conversation_id"

/** يفك حمولة مخزنة: JSON أولاً، ثم legacy `a|b|c|d` (بحد 4 للحفاظ على `|` داخل النص). */
private fun decodeScheduledPayload(text: String): ScheduledPayload? {
    runCatching { return ScheduledJson.decodeFromString(ScheduledPayload.serializer(), text) }.getOrNull()?.let { return it }
    // ترحيل legacy فقط — لا تكتب به مجدداً.
    return runCatching {
        val parts = text.split("|", limit = 4)
        if (parts.size >= 4) ScheduledPayload(
            recipientId = parts[0],
            recipientName = parts[1],
            messageText = parts[2],
            scheduledTime = parts[3].toLongOrNull() ?: 0L
        ) else null
    }.getOrNull()
}

private fun encodeScheduledPayload(payload: ScheduledPayload): ByteArray =
    ScheduledJson.encodeToString(ScheduledPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

private fun toPayload(message: ScheduledMessage): ScheduledPayload = ScheduledPayload(
    recipientId = message.recipientId,
    recipientName = message.recipientName,
    messageText = message.messageText,
    scheduledTime = message.scheduledTime,
    recurrence = message.recurrence.name,
    cronExpression = message.cronExpression,
    timezone = message.timezone,
    nextRunTime = message.nextRunTime,
    runCount = message.runCount,
    maxRuns = message.maxRuns,
    endDate = message.endDate
)

private fun fromPayload(id: String, payload: ScheduledPayload, createdAt: Long, status: String): ScheduledMessage = ScheduledMessage(
    id = id,
    recipientId = payload.recipientId,
    recipientName = payload.recipientName,
    messageText = payload.messageText,
    scheduledTime = payload.scheduledTime,
    createdAt = createdAt,
    status = status,
    recurrence = RecurrenceRule.valueOf(payload.recurrence),
    cronExpression = payload.cronExpression,
    timezone = payload.timezone,
    nextRunTime = payload.nextRunTime,
    runCount = payload.runCount,
    maxRuns = payload.maxRuns,
    endDate = payload.endDate
)

private fun scheduledMessageToPayload(msg: ScheduledMessage): ScheduledPayload = ScheduledPayload(
    recipientId = msg.recipientId,
    recipientName = msg.recipientName,
    messageText = msg.messageText,
    scheduledTime = msg.scheduledTime,
    recurrence = msg.recurrence.name,
    cronExpression = msg.cronExpression,
    timezone = msg.timezone,
    nextRunTime = msg.nextRunTime,
    runCount = msg.runCount,
    maxRuns = msg.maxRuns,
    endDate = msg.endDate
)

class ScheduledMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val recipientId = inputData.getString(KEY_RECIPIENT_ID) ?: return Result.failure()
        val messageText = inputData.getString(KEY_MESSAGE_TEXT) ?: return Result.failure()
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()

        return try {
            val messageStore = MessageStore(applicationContext)
            // عدم تكرار الإرسال: المنبه الدقيق + WorkManager قد يطلقان معاً.
            // إن وُسمت المجدولة SENT مسبقاً فلا إعادة إرسال.
            val existing = runCatching { messageStore.getLocalHistoryEntry(messageId) }.getOrNull()
            if (existing?.status == "SENT") return Result.success()

            // P0-D: إرسال فعلي عبر RedConnectionService (E2EE + طابور + إعادة وصل)،
            // لا حفظ محلي فقط. clientId=messageId يفعّل الحفظ المتفائل داخل الخدمة
            // (نفس المعرف سلكياً فلا تكرار).
            RedConnectionService.sendText(applicationContext, recipientId, conversationId, messageText, messageId)
            // وسم المجدولة مرسلة (best-effort) لتظهر "مرسلة" في القائمة.
            runCatching { messageStore.updateStatus(messageId, "SENT") }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}

/**
 * مستقبل المنبه الدقيق للرسائل المجدولة.
 * يُشغَّل عبر AlarmManager.setExactAndAllowWhileIdle عند الموعد، فيُدخل عمل
 * WorkManager فورياً (نفس المدخلات) — WorkManager هو fallback الموثوق في Doze.
 * مسجل في AndroidManifest داخل application مع SCHEDULE_EXACT_ALARM؛
 * بلا الإذن يسقط الكود تلقائياً إلى WorkManager غير الدقيق (آمن).
 */
class ScheduledMessageAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(KEY_MESSAGE_ID) ?: return
        val recipientId = intent.getStringExtra(KEY_RECIPIENT_ID) ?: return
        val messageText = intent.getStringExtra(KEY_MESSAGE_TEXT) ?: return
        val conversationId = intent.getStringExtra(KEY_CONVERSATION_ID) ?: return
        val input = workDataOf(
            KEY_MESSAGE_ID to messageId,
            KEY_RECIPIENT_ID to recipientId,
            KEY_MESSAGE_TEXT to messageText,
            KEY_CONVERSATION_ID to conversationId
        )
        val immediate = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("scheduled_message_$messageId")
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "scheduled_message_${messageId}_alarm",
                ExistingWorkPolicy.KEEP,
                immediate
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    onNavigateBack: () -> Unit,
    onEditMessage: (ScheduledMessage) -> Unit,
    onDeleteMessage: (ScheduledMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scheduledMessages by remember { mutableStateOf<List<ScheduledMessage>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ScheduledMessage?>(null) }

    LaunchedEffect(Unit) {
        scheduledMessages = loadScheduledMessages(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "الرسائل المجدولة",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, "عودة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Rounded.Add, "إضافة رسالة مجدولة")
            }
        }
    ) { paddingValues ->
        if (scheduledMessages.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "لا توجد رسائل مجدولة",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "اضغط + لإضافة رسالة مجدولة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scheduledMessages, key = { it.id }) { message ->
                    ScheduledMessageCard(
                        message = message,
                        onEdit = { editingMessage = message },
                        onDelete = {
                            scope.launch {
                                deleteScheduledMessage(context, message.id)
                                scheduledMessages = loadScheduledMessages(context)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduledMessageDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { recipientId, recipientName, messageText, scheduledTime ->
                scope.launch {
                    val newMessage = createScheduledMessage(
                        context, recipientId, recipientName, messageText, scheduledTime
                    )
                    scheduledMessages = loadScheduledMessages(context)
                    scheduleWorkManager(context, newMessage)
                }
                showAddDialog = false
            }
        )
    }

    editingMessage?.let { message ->
        EditScheduledMessageDialog(
            message = message,
            onDismiss = { editingMessage = null },
            onConfirm = { updatedMessage ->
                scope.launch {
                    updateScheduledMessage(context, updatedMessage)
                    scheduledMessages = loadScheduledMessages(context)
                }
                editingMessage = null
            }
        )
    }
}

@Composable
fun ScheduledMessageCard(
    message: ScheduledMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val isPast = message.scheduledTime < System.currentTimeMillis()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.recipientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = message.recipientId,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "تعديل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message.messageText,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dateFormat.format(Date(message.scheduledTime)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (isPast) "مرسلة" else "قيد الانتظار",
                    fontSize = 12.sp,
                    color = if (isPast) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduledMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: (recipientId: String, recipientName: String, messageText: String, scheduledTime: Long) -> Unit
) {
    var recipientId by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis() + 3600000) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة رسالة مجدولة") },
        text = {
            Column {
                OutlinedTextField(
                    value = recipientId,
                    onValueChange = { recipientId = it },
                    label = { Text("معرف المستلم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("اسم المستلم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("الرسالة") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("التاريخ")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الوقت")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                Text(
                    text = "الموعد: ${dateFormat.format(Date(selectedDate))}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientId.isNotBlank() && messageText.isNotBlank()) {
                        onConfirm(recipientId, recipientName.ifBlank { recipientId }, messageText, selectedDate)
                    }
                },
                enabled = recipientId.isNotBlank() && messageText.isNotBlank()
            ) {
                Text("جدولة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = 12,
            initialMinute = 0
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("اختر الوقت") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = selectedDate
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedDate = calendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduledMessageDialog(
    message: ScheduledMessage,
    onDismiss: () -> Unit,
    onConfirm: (ScheduledMessage) -> Unit
) {
    var messageText by remember { mutableStateOf(message.messageText) }
    var selectedDate by remember { mutableLongStateOf(message.scheduledTime) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الرسالة المجدولة") },
        text = {
            Column {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("الرسالة") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("التاريخ")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الوقت")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                Text(
                    text = "الموعد: ${dateFormat.format(Date(selectedDate))}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onConfirm(message.copy(
                            messageText = messageText,
                            scheduledTime = selectedDate
                        ))
                    }
                },
                enabled = messageText.isNotBlank()
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = Calendar.getInstance().apply { timeInMillis = selectedDate }.get(Calendar.HOUR_OF_DAY),
            initialMinute = Calendar.getInstance().apply { timeInMillis = selectedDate }.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("اختر الوقت") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = selectedDate
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedDate = calendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private suspend fun loadScheduledMessages(context: Context): List<ScheduledMessage> {
    return try {
        val messageStore = MessageStore(context)
        val messages = messageStore.localHistory("scheduled_messages", 100)
        messages.mapNotNull { localMessage ->
            try {
                val text = String(localMessage.plaintext, Charsets.UTF_8)
                // P0-D: JSON أولاً + ترحيل legacy `a|b|c|d`.
                val payload = decodeScheduledPayload(text) ?: return@mapNotNull null
                fromPayload(localMessage.id, payload, localMessage.timestamp, localMessage.status)
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private suspend fun createScheduledMessage(
    context: Context,
    recipientId: String,
    recipientName: String,
    messageText: String,
    scheduledTime: Long,
    recurrence: RecurrenceRule = RecurrenceRule.NONE,
    cronExpression: String? = null,
    timezone: String = "UTC",
    maxRuns: Int? = null,
    endDate: Long? = null
): ScheduledMessage {
    val id = "scheduled_${System.currentTimeMillis()}_${Random().nextInt(10000)}"
    // P0-D: JSON بدل `a|b|c|d` — آمن مع `|` والأسطر واليونيكود.
    val plaintext = encodeScheduledPayload(toPayload(ScheduledMessage(
        id = id,
        recipientId = recipientId,
        recipientName = recipientName,
        messageText = messageText,
        scheduledTime = scheduledTime,
        createdAt = System.currentTimeMillis(),
        status = "PENDING",
        recurrence = recurrence,
        cronExpression = cronExpression,
        timezone = timezone,
        maxRuns = maxRuns,
        endDate = endDate
    )))

    val messageStore = MessageStore(context)
    val localMessage = LocalMessage(
        id = id,
        conversationId = "scheduled_messages",
        senderId = "local_user",
        plaintext = plaintext,
        type = "SCHEDULED",
        timestamp = System.currentTimeMillis(),
        outgoing = true,
        status = "PENDING"
    )
    messageStore.saveDecrypted(localMessage)

    return ScheduledMessage(
        id = id,
        recipientId = recipientId,
        recipientName = recipientName,
        messageText = messageText,
        scheduledTime = scheduledTime,
        createdAt = System.currentTimeMillis(),
        status = "PENDING",
        recurrence = recurrence,
        cronExpression = cronExpression,
        timezone = timezone,
        maxRuns = maxRuns,
        endDate = endDate
    )
}

private suspend fun updateScheduledMessage(context: Context, message: ScheduledMessage) {
    // P0-D: JSON فقط.
    val plaintext = encodeScheduledPayload(toPayload(message))

    val messageStore = MessageStore(context)
    messageStore.updateLocalHistoryText(message.id, plaintext)
    // أعد جدولة الإرسال بعد التعديل (إلغاء القديم + جدولة جديدة).
    scheduleWorkManager(context, message)
}

private suspend fun deleteScheduledMessage(context: Context, messageId: String) {
    cancelScheduledWork(context, messageId)
    val messageStore = MessageStore(context)
    messageStore.deleteLocalMessage(messageId)
}

/** يلغي WorkManager + منبه Alarm لنفس الرسالة (يُستدعى عند الحذف/التعديل). */
private fun cancelScheduledWork(context: Context, messageId: String) {
    runCatching { WorkManager.getInstance(context).cancelUniqueWork("scheduled_message_$messageId") }
    runCatching { WorkManager.getInstance(context).cancelUniqueWork("scheduled_message_${messageId}_alarm") }
    runCatching {
        val am = context.getSystemService(AlarmManager::class.java) ?: return@runCatching
        val pi = PendingIntent.getBroadcast(
            context,
            messageId.hashCode(),
            Intent(context, ScheduledMessageAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        pi.cancel()
    }
}

private fun scheduleWorkManager(context: Context, message: ScheduledMessage) {
    val delay = message.scheduledTime - System.currentTimeMillis()
    if (delay <= 0) return

    val inputData = workDataOf(
        KEY_MESSAGE_ID to message.id,
        KEY_RECIPIENT_ID to message.recipientId,
        KEY_MESSAGE_TEXT to message.messageText,
        KEY_CONVERSATION_ID to message.recipientId
    )

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // المسار الأساسي الموثوق (fallback في Doze): WorkManager مؤجل.
    val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
        .setInputData(inputData)
        .setConstraints(constraints)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .addTag("scheduled_message_${message.id}")
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "scheduled_message_${message.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

    // مسار الدقة: ExactAlarm عند الموعد → يوقظ ScheduledMessageAlarmReceiver
    // الذي يُدخل عملاً فورياً. الفشل (بلا إذن/بلا تسجيل manifest) آمن — يبقى WorkManager.
    runCatching {
        val am = context.getSystemService(AlarmManager::class.java) ?: return@runCatching
        val alarmIntent = Intent(context, ScheduledMessageAlarmReceiver::class.java)
            .putExtra(KEY_MESSAGE_ID, message.id)
            .putExtra(KEY_RECIPIENT_ID, message.recipientId)
            .putExtra(KEY_MESSAGE_TEXT, message.messageText)
            .putExtra(KEY_CONVERSATION_ID, message.recipientId)
        val pi = PendingIntent.getBroadcast(
            context,
            message.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = message.scheduledTime
        try {
            // يتطلب SCHEDULE_EXACT_ALARM على API 31+ — يرمي SecurityException بلا إذن.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // محاولة دقيقة؛ عند الرفض نسقط للغير دقيق أدناه.
                runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
                    .onFailure { am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (se: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
