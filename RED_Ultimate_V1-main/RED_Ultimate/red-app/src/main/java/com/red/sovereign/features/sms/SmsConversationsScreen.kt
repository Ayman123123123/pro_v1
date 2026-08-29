package com.red.sovereign.features.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة المحادثات SMS الاحترافية — قائمة محادثات مع بحث وعدّاد غير مقروء.
 *
 * زر + يفتح حوار "محادثة جديدة" مع تحقق يمني صارم (نفس قواعد الخادم):
 * تطبيع الرقم + رفض الأنماط التسلسلية الوهمية قبل أي إنفاق.
 * الشريحة 1:1 — المستخدم يرى رقمه الكامل غير مقنع في جهازه فقط.
 */
@Composable
fun SmsConversationsScreen(vm: SmsViewModel, onOpenChat: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.start() }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatNumber by remember { mutableStateOf("") }
    var newChatError by remember { mutableStateOf<String?>(null) }
    // رقم الشريحة المربوطة 1:1 — كامل لصاحبه في جهازه فقط (ليس اختياراً)
    val context = LocalContext.current
    val boundNumber = remember { TokenStore(context).pstnNumber?.takeIf { it.isNotBlank() } }
    val boundPort = remember { TokenStore(context).pstnPortIndex }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Message, null, tint = AqyalGold, modifier = Modifier.size(34.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("الرسائل النصية", fontWeight = FontWeight.Bold, color = AqyalGold)
                        Text(if (vm.connected) "متصل — تحديث فوري" else "غير متصل — التحديث الدوري", fontSize = 12.sp,
                            color = if (vm.connected) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "تحديث", tint = AqyalGold) }
                }
            }

            // شريط الشريحة المربوطة — كامل غير مقنع، يراه صاحبه فقط
            // لو غير مربوط يظهر تنبيه بدل إخفاء صامت (وإلا يظن المستخدم أن الإرسال يعمل)
            if (boundNumber != null) {
                val op = runCatching { YemeniOperatorDetector.getOperatorInfo(boundNumber) }.getOrNull()
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = YounesEmerald.copy(alpha = .10f))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, null, tint = YounesEmerald, modifier = Modifier.size(16.dp))
                        Text(" ترسل من  $boundNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = YounesEmerald,
                            modifier = Modifier.padding(start = 6.dp))
                        if (boundPort != null) Text(" · SIM ${boundPort + 1}", fontSize = 11.sp, color = YounesEmerald.copy(alpha = .7f),
                            modifier = Modifier.padding(start = 4.dp))
                        op?.let { Text(" · ${it.name}", fontSize = 11.sp, color = it.brandColor, modifier = Modifier.padding(start = 4.dp)) }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("لا توجد شريحة مربوطة — اطلب من الإدارة ربط شريحتك", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                placeholder = { Text("ابحث في المحادثات…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Content
            if (vm.loading && vm.conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            } else if (vm.filteredConversations.isEmpty()) {
                EmptyState("لا توجد محادثات", "ابدأ بإرسال رسالة لأي رقم يمني")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.filteredConversations, key = { it.number }) { conv ->
                        ConversationRow(conv) { onOpenChat(conv.number) }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = AqyalGold
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New Chat", tint = Color.White)
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
                    // normalized الآن 9 خانات يمنية صافية
                    onOpenChat(normalized)
                        showNewChatDialog = false
                        newChatNumber = ""
                    }
                }) { Text("مراسلة") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false; newChatError = null }) { Text("إلغاء") }
            }
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
    // بادئات يمنية معروفة: 71 سبأفون، 73 يو، 77/78 يمن موبايل، 70 واي
    val prefix3 = local.take(3)
    val ok = prefix3 in setOf("700","701","702","703","704","705","706","707","708","709",
        "710","711","712","713","714","715","716","717","718","719",
        "770","771","772","773","774","775","776","777","778","779","780","781","782","783","784","785","786","787","788","789") || local.length >= 9
    if (!ok) return "بادئة غير معروفة"
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

@Composable
private fun ConversationRow(conv: SmsConversationDto, onClick: () -> Unit) {
    val op = conv.operator?.let { runCatching { com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(conv.number) }.getOrNull() }
    val timeText = conv.lastTime?.let { formatSmsTime(it) } ?: ""
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = YounesEmerald, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conv.number, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    op?.let { Text(it.name, color = it.brandColor, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp)) }
                }
                Text(conv.lastText.orEmpty().take(70), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (conv.unreadCount > 0) {
                        Box(Modifier.clip(CircleShape).background(AqyalGold).padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("${conv.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSmsTime(epochSeconds: Long): String {
    val d = Date(epochSeconds * 1000)
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(d)
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
