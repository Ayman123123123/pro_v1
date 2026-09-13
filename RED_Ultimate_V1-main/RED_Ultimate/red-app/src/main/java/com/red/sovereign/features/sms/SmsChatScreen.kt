package com.red.sovereign.features.sms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.red.sovereign.R
import com.red.sovereign.ui.screens.scrollOnce
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import java.util.Locale

/**
 * 📨 شاشة دردشة SMS — فقاعات IN/OUT، علامات تسليم، عدّاد مقاطع صحيح، حذف بضغط طويل.
 *
 * التوحيد (2026-09-05): الضغط الطويل كان معلَّقاً بلا مستدعٍ (كان النص يقول
 * «اضغط مطولاً للحذف» بينما لا `combinedClickable` على الفقاعة أبداً)، وعدّاد
 * المقاطع كان يستخدم قسمة صحيحة فيُظهر «1» لـ161 حرفاً. الآن يستخدم
 * [smsSegmentCount] الصحيح (GSM‑7: 160/153، UCS2: 70/67).
 */
@Composable
fun SmsChatScreen(vm: SmsViewModel, onBack: () -> Unit) {
    val number = vm.chatNumber ?: run { LaunchedEffect(Unit) { onBack() }; return }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showDelete by remember { mutableStateOf<SmsMessageDto?>(null) }
    // تفاصيل الرسالة عند النقر (كان onClick فارغًا) — عرض المحتوى والوقت والحالة.
    var selectedInfo by remember { mutableStateOf<SmsMessageDto?>(null) }

    // المفتاح معرف آخر رسالة لا الحجم + تمرير موحد — يمنع عاصفة التمرير عند التحديث.
    LaunchedEffect(vm.chatMessages.lastOrNull()?.id) {
        // scrollOnce + runCatching داخلياً: bare animateScrollToItem crashed on
        // rapid delete/refresh when lastIndex went stale mid-animation.
        if (vm.chatMessages.isNotEmpty()) listState.scrollOnce(vm.chatMessages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(color = SovereignColors.ObsidianDeep) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(number, fontWeight = FontWeight.Bold, color = AqyalGold)
                    val op = com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)
                    Text(
                        op?.name ?: (if (vm.connected) stringResource(R.string.status_connected_live) else stringResource(R.string.status_disconnected_periodic)),
                        fontSize = 11.sp,
                        color = op?.brandColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.refresh() }, enabled = !vm.loading) {
                    if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AqyalGold)
                    else Icon(Icons.Default.Refresh, "تحديث", tint = AqyalGold)
                }
            }
        }

        vm.error?.let { err ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(err, Modifier.padding(10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.chatMessages, key = { it.id }) { msg ->
                MessageBubble(msg, onLongPress = { showDelete = msg }, onClick = { selectedInfo = msg })
            }
        }

        Surface(color = SovereignColors.SurfaceCard) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_SMS_CHARS) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب رسالة…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val parts = smsSegmentCount(text)
                    Text(
                        if (parts <= 1) "${text.length} حرف" else "$parts مقاطع · ${text.length} حرف",
                        fontSize = 11.sp,
                        color = if (parts >= 2) AqyalGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { if (text.isNotBlank()) { vm.send(text); text = "" } },
                        enabled = text.isNotBlank() && !vm.sending,
                        colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)
                    ) {
                        if (vm.sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إرسال")
                        }
                    }
                }
            }
        }
    }

    showDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("حذف الرسالة؟") },
            text = { Text("ستُحذف من الخادم نهائياً ولا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMessage(target.id); showDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = null }) { Text("إلغاء") } }
        )
    }

    // حوار التفاصيل عند النقر — يعرض النص الكامل والوقت والحالة والاتجاه.
    selectedInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { selectedInfo = null },
            title = { Text("معلومات الرسالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(info.content, fontSize = 15.sp)
                    HorizontalDivider()
                    Text("الوقت: ${formatTime(info.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("الاتجاه: ${if (info.direction == "OUT") "صادرة" else "واردة"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("الحالة: ${info.status}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { selectedInfo = null }) { Text("إغلاق") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: SmsMessageDto, onLongPress: () -> Unit, onClick: () -> Unit) {
    val isOut = msg.direction == "OUT"
    val bubbleColor = if (isOut) AqyalGold.copy(alpha = .18f) else SovereignColors.SurfaceCard
    val bubbleShape = if (isOut) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                // النقر يفتح التفاصيل، والضغط الطويل يعرض حذف — لا onClick فارغ.
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(msg.content, fontSize = 15.sp)
                Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(msg.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isOut) {
                        Spacer(Modifier.width(4.dp))
                        DeliveryStatus(msg.status)
                    }
                }
            }
        }
    }
}

/** أيقونة + نص عربي لحالة التسليم — لا رموز إنجليزية خام أمام المستخدم. */
@Composable
private fun DeliveryStatus(status: String) {
    val (icon, tint, label) = when (status.uppercase(Locale.ROOT)) {
        "DELIVERED" -> Triple(Icons.Default.DoneAll, YounesEmerald, "وصلت")
        "SENT" -> Triple(Icons.Default.Check, YounesEmerald, "أُرسلت")
        "FAILED" -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, "فشل")
        "PENDING" -> Triple(Icons.Default.Schedule, Color.Gray, "جارٍ")
        else -> Triple(Icons.Default.Check, Color.Gray, status)
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
    Spacer(Modifier.width(2.dp))
    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tint)
}

/**
 * حد المؤلِّف — الخادم يحدّ 1500 بايت؛ 918 حرفاً عربياً تبقى دونه بأمان.
 * ملاحظة: [smsSegmentCount] و[formatTime] موحدان في SmsFormat.kt —
 * لا تُعِد تعريفهما هنا حتى لا يتفرّع تقديران للتكلفة.
 */
private const val MAX_SMS_CHARS = 918
