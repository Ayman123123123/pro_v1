package com.red.sovereign.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.features.sms.SmsViewModel
import com.red.sovereign.features.sms.formatSmsTime
import com.red.sovereign.features.sms.smsSegmentCount
import com.red.sovereign.sms.SmsConversation
import com.red.sovereign.sms.SmsMessageDto
import com.red.sovereign.ui.screens.scrollOnce
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesRose
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * 📨 شاشات رسائل الهاتف اليمني (SMS عبر DINSTAR).
 *
 * شاشتان في ملف واحد لثبات الهوية مع بقية الشاشات:
 * - [SmsConversationsScreen] قائمة المحادثات + غير المقروء + تحديث يدوي يجلب
 *   وارد الجهاز فوراً.
 * - محادثة [SmsThreadScreen] فقاعات IN/OUT مع حالة التسليم (PENDING/SENT/
 *   DELIVERED/FAILED) وشريط إرسال بعدّاد المقاطع.
 *
 * الإرسال يستهلك نفس الحصة اليومية المشروعة للمكالمات (منفذ المستخدم الدائم
 * يُستخدم تلقائياً في الخادم)، ولا يعرض هذا الملف أرقام منافذ للاختيار.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConversationsScreen(vm: SmsViewModel, onBack: () -> Unit, onCallNumber: (String) -> Unit = {}) {
    // بدء الاستماع الحي والاستقصاء عند الدخول، وإيقافهما كلياً عند مغادرة الشاشة
    // التوحيد (2026-09-10): يستخدم SmsViewModel الموحد (features.sms) — start/stop.
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }
    // زر الرجوع داخل الـThread يغلق المحادثة أولاً (لا يخرج من الشاشة) —
    // يوازي زر IconButton في SmsThreadScreen، والأسماء الحالية chatNumber/closeChat
    // بعد توحيد 2026-09-10 (openNumber/closeThread سابقاً).
    BackHandler(enabled = vm.chatNumber != null) { vm.closeChat() }
    var pendingDelete by remember { mutableStateOf<SmsConversation?>(null) }

    if (vm.chatNumber != null) {
        SmsThreadScreen(vm = vm, onBack = { vm.closeChat() })
        return
    }

    Column(Modifier.fillMaxSize()) {
        // ── شريط العنوان ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
            }
            Column(Modifier.weight(1f)) {
                Text("رسائل الهاتف اليمني", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("SMS عبر شرائح DINSTAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            val unread = vm.totalUnread
            if (unread > 0) {
                Box(
                    Modifier.size(26.dp).background(YounesRose, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$unread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            IconButton(onClick = { vm.refresh() }, enabled = !vm.loading) {
                if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "تحديث وجلب الوارد")
            }
        }

        vm.error?.let { err ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = YounesRose.copy(alpha = .12f))
            ) {
                Text(err, Modifier.padding(12.dp), color = YounesRose, fontSize = 12.sp)
            }
        }

        if (!vm.loading && vm.conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(62.dp))
                    Text("لا رسائل بعد", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "ستظهر هنا رسائلك الواردة والصادرة عبر شرائح DINSTAR —\nجرّب زر التحديث لجلب الوارد الآن",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.conversations, key = { it.number }) { convo ->
                    SmsConversationRow(
                        convo = convo,
                        onClick = { vm.openChat(convo.number) },
                        onLongClick = { pendingDelete = convo },
                        onCall = { onCallNumber(convo.number) }
                    )
                }
            }
        }
    }

    pendingDelete?.let { convo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المحادثة؟") },
            text = { Text("سيُحذف سجل الرسائل مع \"${convo.number}\" من الخادم. لا يمكن التراجع.") },
            confirmButton = {
                TextButton({ vm.deleteConversation(convo.number); pendingDelete = null }) {
                    Text("حذف", color = YounesRose)
                }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SmsConversationRow(convo: SmsConversation, onClick: () -> Unit, onLongClick: () -> Unit, onCall: () -> Unit) {
    val op = YemeniOperatorDetector.getOperatorInfo(convo.number)
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (convo.unreadCount > 0)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AqyalGold.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text("${convo.number.takeLast(2)}", color = AqyalGold, fontWeight = FontWeight.Black) }

            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(convo.number, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (op != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).background(op.brandColor, CircleShape))
                        Spacer(Modifier.width(3.dp))
                        Text(op.name, color = op.brandColor, fontSize = 10.sp)
                    }
                }
                Text(
                    convo.lastText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(formatSmsTime(convo.lastTime), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                if (convo.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(20.dp).background(YounesEmerald, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${convo.unreadCount}", color = Color(0xFF002117), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(onClick = onCall) {
                Icon(Icons.Default.Phone, "اتصال بهذا الرقم", tint = YounesEmerald, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun SmsThreadScreen(vm: SmsViewModel, onBack: () -> Unit) {
    val number = vm.chatNumber ?: return
    var draft by remember(number) { mutableStateOf("") }
    val listState = rememberLazyListState()

    // تمرير لأسفل عند وصول رسالة جديدة — المفتاح معرف آخر رسالة لا الحجم
    // + scrollOnce موحد (animate كان يرمي IndexOutOfBounds عند تقلص القائمة).
    LaunchedEffect(vm.chatMessages.lastOrNull()?.id) {
        if (vm.chatMessages.isNotEmpty()) listState.scrollOnce(vm.chatMessages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Column(Modifier.weight(1f)) {
                Text(number, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(op.brandColor, CircleShape))
                        Text("  ${op.name} (${op.technology})", color = op.brandColor, fontSize = 10.sp)
                    }
                }
            }
        }

        vm.error?.let { err ->
            Text(
                err,
                Modifier.padding(horizontal = 14.dp),
                color = YounesRose, fontSize = 12.sp
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (vm.loading && vm.chatMessages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = AqyalGold)
                    }
                }
            }
            items(vm.chatMessages, key = { it.id }) { msg ->
                SmsBubble(msg)
            }
        }

        // ── شريط الإرسال ──
        Card(Modifier.fillMaxWidth().padding(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(620) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالة…", fontSize = 13.sp) },
                    maxLines = 4,
                    supportingText = {
                        val parts = smsSegmentCount(draft)
                        Text(
                            "${draft.length} حرف · ~$parts مقطع",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                FilledIconButton(
                    onClick = {
                        vm.send(draft)
                        draft = ""
                    },
                    enabled = !vm.sending && draft.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (vm.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AqyalGold)
                    else Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = AqyalGold)
                }
            }
        }
    }
}

@Composable
private fun SmsBubble(msg: SmsMessageDto) {
    val isOut = msg.direction == "OUT"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOut) AqyalGold.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isOut) 16.dp else 4.dp,
                bottomEnd = if (isOut) 4.dp else 16.dp
            )
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.content, fontSize = 14.sp)
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatSmsTime(msg.createdAt),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOut) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when (msg.status) {
                                "PENDING" -> "⏳ جارٍ الإرسال"
                                "SENT" -> "✓ أُرسلت"
                                "DELIVERED" -> "✓✓ وصلت"
                                "FAILED" -> "✗ فشل الإرسال"
                                else -> msg.status
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (msg.status) {
                                "DELIVERED" -> YounesEmerald
                                "FAILED" -> YounesRose
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * تنسيق الوقت وعدّاد المقاطع موحدان في features.sms.SmsFormat —
 * [formatSmsTime] ذكي بنفس اليوم، و[smsSegmentCount] بقاعدة 160/153 و70/67.
 * لا تُعِد تعريفهما هنا.
 */
