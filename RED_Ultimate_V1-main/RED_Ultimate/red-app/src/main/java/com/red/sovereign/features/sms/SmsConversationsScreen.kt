package com.red.sovereign.features.sms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.red.sovereign.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesRose

/**
 * ملاحظة توحيد (2026-09-10): [smsSegmentCount]/[formatSmsTime] في SmsFormat.kt —
 * لا تُعِد تعريفهما هنا.
 */

/**
 * 📨 شاشة محادثات SMS الموحّدة — قائمة + بحث + غير مقروء + محادثة جديدة.
 *
 * التوحيد (2026-09-05) جمع أفضل ما في النسختين المتنافستين:
 * - من `features.sms`: البحث، حوار «محادثة جديدة» بتحقق يمني صارم مطابق
 *   لقواعد الخادم، وشريط الشريحة المربوطة 1:1.
 * - من `sms`: شارة إجمالي غير المقروء، عرض الأخطاء المُوطَّنة، زر الاتصال
 *   بالرقم مباشرة، حذف المحادثة بضغط طويل، وإيقاف الاستقصاء عند مغادرة
 *   الشاشة عبر [DisposableEffect] (كان الاستقصاء يبقى حياً في الخلفية).
 *
 * الشريحة 1:1 — المستخدم يرى رقمه الكامل غير مقنع في جهازه فقط.
 */
@Composable
fun SmsConversationsScreen(
    vm: SmsViewModel,
    onOpenChat: (String) -> Unit,
    onCallNumber: (String) -> Unit = {}
) {
    // بدء المقبس الحي والاستقصاء عند الدخول، وإيقافهما كلياً عند المغادرة.
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatNumber by remember { mutableStateOf("") }
    var newChatError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SmsConversationDto?>(null) }

    val context = LocalContext.current
    // مفاتيح TokenStore: تُعاد قراءة الشريحة المربوطة كلما تغيّرت القيم المخزنة
    // (ربط/فك/تبديل حساب) بدل تجميد أول قراءة إلى الأبد بـ remember بلا مفاتيح.
    val tokenStore = remember(context) { TokenStore(context) }
    val boundNumber = remember(tokenStore.pstnNumber) { tokenStore.pstnNumber?.takeIf { it.isNotBlank() } }
    val boundPort = remember(tokenStore.pstnPortIndex) { tokenStore.pstnPortIndex }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── الترويسة: الحالة الحيّة + إجمالي غير المقروء + تحديث يدوي ──
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Message, null, tint = AqyalGold, modifier = Modifier.size(34.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("الرسائل النصية", fontWeight = FontWeight.Bold, color = AqyalGold)
                        Text(
                            if (vm.connected) stringResource(R.string.status_connected_live) else stringResource(R.string.status_disconnected_periodic),
                            fontSize = 12.sp,
                            color = if (vm.connected) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val unread = vm.totalUnread
                    if (unread > 0) {
                        Box(
                            Modifier.size(26.dp).background(YounesRose, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("$unread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { vm.refresh() }, enabled = !vm.loading) {
                        if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AqyalGold)
                        else Icon(Icons.Default.Refresh, "تحديث وجلب الوارد", tint = AqyalGold)
                    }
                }
            }

            // ── شريط الشريحة المربوطة — كامل غير مقنع، يراه صاحبه فقط ──
            // لو غير مربوط يظهر تنبيه بدل إخفاء صامت (وإلا يظن المستخدم أن الإرسال يعمل)
            if (boundNumber != null) {
                val op = runCatching { YemeniOperatorDetector.getOperatorInfo(boundNumber) }.getOrNull()
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = YounesEmerald.copy(alpha = .10f))
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = YounesEmerald, modifier = Modifier.size(16.dp))
                        Text(
                            " ترسل من  $boundNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = YounesEmerald, modifier = Modifier.padding(start = 6.dp)
                        )
                        if (boundPort != null) Text(
                            " · SIM ${boundPort + 1}", fontSize = 11.sp,
                            color = YounesEmerald.copy(alpha = .7f), modifier = Modifier.padding(start = 4.dp)
                        )
                        op?.let {
                            Text(" · ${it.name}", fontSize = 11.sp, color = it.brandColor, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            } else {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "لا توجد شريحة مربوطة — اطلب من الإدارة ربط شريحتك", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── الأخطاء المُوطَّنة عربياً ──
            vm.error?.let { err ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = YounesRose.copy(alpha = .12f))
                ) {
                    Text(err, Modifier.padding(12.dp), color = YounesRose, fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                placeholder = { Text("ابحث في المحادثات…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            if (vm.loading && vm.conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            } else if (vm.filteredConversations.isEmpty()) {
                EmptyState(
                    if (vm.searchQuery.isBlank()) "لا توجد محادثات" else "لا نتائج للبحث",
                    if (vm.searchQuery.isBlank()) "ابدأ بإرسال رسالة لأي رقم يمني — أو اضغط تحديث لجلب الوارد"
                    else "جرّب رقماً أو كلمة أخرى"
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vm.filteredConversations, key = { it.number }) { conv ->
                        ConversationRow(
                            conv = conv,
                            onClick = { onOpenChat(conv.number) },
                            onLongClick = { pendingDelete = conv },
                            onCall = { onCallNumber(conv.number) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = AqyalGold
        ) {
            Icon(Icons.Filled.Add, contentDescription = "محادثة جديدة", tint = Color.White)
        }
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false; newChatError = null },
            title = { Text("محادثة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newChatNumber,
                        onValueChange = { newChatNumber = it; newChatError = null },
                        label = { Text("رقم الهاتف — 777123456 أو 967777123456") },
                        placeholder = { Text("مثال: 777123456") },
                        singleLine = true,
                        isError = newChatError != null,
                        supportingText = newChatError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                    if (boundNumber != null) {
                        Text("ستُرسل من  $boundNumber", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeYemeniInput(newChatNumber)
                    val err = validateYemeniNumber(normalized)
                    if (err != null) { newChatError = err; return@TextButton }
                    onOpenChat(normalized)
                    showNewChatDialog = false
                    newChatNumber = ""
                }) { Text("مراسلة") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false; newChatError = null }) { Text("إلغاء") }
            }
        )
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المحادثة؟") },
            text = { Text("سيُحذف كامل سجل الرسائل مع \"${conv.number}\" من الخادم. لا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteConversation(conv.number); pendingDelete = null }) {
                    Text("حذف", color = YounesRose)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}

// ── تحقق يمني محلي — نفس قواعد الخادم بلا استدعاء شبكة ──
private fun normalizeYemeniInput(raw: String): String {
    val c = raw.filter { it.isDigit() || it == '+' }
    return when {
        c.startsWith("+967") -> c.removePrefix("+967")
        c.startsWith("00967") -> c.removePrefix("00967")
        c.startsWith("967") -> c.removePrefix("967")
        c.startsWith("0") -> c.removePrefix("0")
        else -> c
    }.filter { it.isDigit() }
}

private fun validateYemeniNumber(local: String): String? {
    if (local.isBlank()) return "أدخل رقم الهاتف"
    if (local.length < 9) return "الرقم قصير — 9 خانات يمنية (777123456)"
    if (local.length > 12) return "الرقم طويل جداً"
    if (!local.matches(Regex("^[0-9]{9,12}$"))) return "أرقام فقط"
    if (looksLikePlaceholder(local)) return "رقم وهمي/تسلسلي — أدخل رقماً حقيقياً"
    // البادئة تُحسم عبر كاشف المشغلين الموحّد بدل قائمة مكرّرة تتعارض معه.
    if (YemeniOperatorDetector.getOperatorInfo(local) == null) return "بادئة غير معروفة — 70/71/73/77/78"
    return null
}

// نفس منطق الخادم NumberLearningService — تسلسل متتالٍ ≥6 أو خانة واحدة مكررة
private fun looksLikePlaceholder(number: String): Boolean {
    val d = number.filter { it.isDigit() }.let { raw ->
        when {
            raw.startsWith("00967") -> raw.drop(5)
            raw.startsWith("967") && raw.length > 9 -> raw.drop(3)
            else -> raw
        }
    }
    if (d.length < 9) return true
    if (d.all { it == d[0] }) return true
    var longest = 1; var asc = 1; var desc = 1
    for (i in 1 until d.length) {
        val delta = d[i] - d[i - 1]
        asc = if (delta == 1) asc + 1 else 1
        desc = if (delta == -1) desc + 1 else 1
        longest = maxOf(longest, asc, desc)
    }
    return longest >= 6
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: SmsConversationDto,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCall: () -> Unit
) {
    val op = runCatching { YemeniOperatorDetector.getOperatorInfo(conv.number) }.getOrNull()
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (conv.unreadCount > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .9f)
            else SovereignColors.SurfaceCard
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = .18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = YounesEmerald, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conv.number, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    op?.let {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).background(it.brandColor, CircleShape))
                        Spacer(Modifier.width(3.dp))
                        Text(it.name, color = it.brandColor, fontSize = 10.sp)
                    }
                }
                Text(
                    conv.lastText.take(70), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatSmsTime(conv.lastTime), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (conv.unreadCount > 0) {
                        Box(Modifier.clip(CircleShape).background(AqyalGold).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("${conv.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
private fun EmptyState(title: String, sub: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Chat, null, tint = AqyalGold.copy(alpha = .5f), modifier = Modifier.size(64.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
