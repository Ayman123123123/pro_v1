package com.red.sovereign.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.red.sovereign.core.database.OutboxMessageEntity
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.core.outbox.OutboxRetryWorker
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة قائمة الانتظار دون اتصال — Sprint P0 ربط فقط (صفر تخزين جديد).
 *
 * تعرض [com.red.sovereign.core.database.OutboxDao.observePending] الحيّ
 * (PENDING/FAILED من جدول outbox_messages الجاهز) مع زرّي إعادة/حذف لكل
 * رسالة عبر نفس DAO والعامل [OutboxRetryWorker] — بلا Snackbar وهمي ولا
 * بيانات جامدة. الحمولة مشفرة فلا تُفك ولا تُعرض، يُعرض حجمها فقط.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineQueueScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val dao = remember(appContext) { RedDatabase.getInstance(appContext).outboxDao() }
    // التدفق الحيّ — أي enqueue/retry/delete من أي مكان ينعكس فوراً هنا.
    val pending by dao.observePending().collectAsState(initial = emptyList())
    var deadCount by remember { mutableStateOf<Int?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun refreshDead() {
        scope.launch {
            deadCount = runCatching { dao.countDeadLetter() }.getOrNull()
        }
    }
    LaunchedEffect(Unit) { refreshDead() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قائمة الانتظار دون اتصال") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.ObsidianDeep
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${pending.size} رسالة معلّقة · ${deadCount ?: "…"} ميتة (Dead Letter)",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "إعادة الجدولة تمر عبر OutboxRetryWorker (مقيّد بالشبكة CONNECTED) — الحذف يسقط الرسالة من Outbox فقط.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        OutboxRetryWorker.schedule(appContext)
                        scope.launch {
                            snackbarHostState.showSnackbar("Outbox: ${pending.size} معلّقة — أُعيدت الجدولة")
                        }
                        actionMsg = "أُعيدت الجدولة عبر العامل — ${pending.size} معلّقة"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = pending.isNotEmpty()
                ) { Text("إعادة المحاولة الآن") }
                OutlinedButton(onClick = { refreshDead() }, modifier = Modifier.weight(1f)) {
                    Text("تحديث العدادات")
                }
            }
            actionMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = YounesEmerald)
            }
            if (pending.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("لا رسائل معلّقة — صندوق الصادر فارغ.", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "تُحفظ الرسائل هنا تلقائياً عند انقطاع الشبكة وتُرسل عند عودتها.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(pending, key = { it.id }) { msg ->
                        OutboxRow(
                            msg = msg,
                            busy = busyId == msg.id,
                            onRetry = {
                                busyId = msg.id
                                scope.launch {
                                    runCatching {
                                        dao.scheduleRetry(msg.id, System.currentTimeMillis(), "manual_retry")
                                        OutboxRetryWorker.schedule(appContext)
                                        actionMsg = "أُعيدت جدولة ${msg.id.take(8)} عبر OutboxRetryWorker"
                                    }.onFailure { actionMsg = "فشل: ${it.message?.take(100)}" }
                                    busyId = null
                                }
                            },
                            onDelete = {
                                busyId = msg.id
                                scope.launch {
                                    runCatching {
                                        dao.delete(msg.id)
                                        actionMsg = "حُذفت ${msg.id.take(8)} من Outbox"
                                    }.onFailure { actionMsg = "فشل: ${it.message?.take(100)}" }
                                    busyId = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboxRow(
    msg: OutboxMessageEntity,
    busy: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${msg.type}${msg.mediaType?.let { " · $it" } ?: ""} · أولوية ${priorityLabelAr(msg.priority)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "محادثة ${msg.conversationId.take(16)} · ${msg.payload.size} بايت مشفّر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    msg.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (msg.status == "FAILED") MaterialTheme.colorScheme.error else AqyalGold
                )
            }
            Text(
                "محاولات ${msg.retryCount} · ${msg.lastError?.take(80) ?: "بانتظار الشبكة"} · ${formatTimeAr(msg.nextAttemptAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "جارٍ…" else "إعادة")
                }
                TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun priorityLabelAr(priority: Int): String = when (priority) {
    OutboxMessageEntity.PRIORITY_HIGH -> "عالية"
    OutboxMessageEntity.PRIORITY_LOW -> "منخفضة"
    else -> "عادية"
}

private fun formatTimeAr(epochMs: Long): String = runCatching {
    SimpleDateFormat("HH:mm dd/MM", Locale("ar")).format(Date(epochMs))
}.getOrDefault("—")
