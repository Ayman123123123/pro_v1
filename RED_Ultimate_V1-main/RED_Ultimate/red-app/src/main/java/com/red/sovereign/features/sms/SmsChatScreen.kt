package com.red.sovereign.features.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة الدردشة SMS — فقاعات، علامات تسليم، عدّاد أحرف، حذف طويل.
 */
@Composable
fun SmsChatScreen(vm: SmsViewModel, onBack: () -> Unit) {
    val number = vm.chatNumber ?: run { LaunchedEffect(Unit) { onBack() }; return }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showDelete by remember { mutableStateOf<SmsMessageDto?>(null) }

    // تحديث الرسائل عند الوصول
    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = SovereignColors.ObsidianDeep) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(number, fontWeight = FontWeight.Bold, color = AqyalGold)
                    val op = com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)
                    Text(op?.name ?: (if (vm.connected) "متصل" else "غير متصل"), fontSize = 11.sp,
                        color = op?.brandColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.ErrorOutline, "تحديث", tint = AqyalGold) }
            }
        }

        // Messages
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.chatMessages, key = { it.id }) { msg ->
                MessageBubble(msg, onDelete = { showDelete = msg })
            }
        }

        // Composer
        Surface(color = SovereignColors.SurfaceCard) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 918) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب رسالة…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${smsParts(text)}/${text.length}", fontSize = 11.sp,
                        color = if (smsParts(text) >= 2) AqyalGold else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (text.isNotBlank()) { vm.send(text.trim()); text = "" }
                    }, enabled = text.isNotBlank() && !vm.sending,
                        colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) {
                        if (vm.sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("إرسال") }
                    }
                }
            }
        }
    }

    if (showDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("حذف الرسالة؟") },
            text = { Text("ستُحذف من جهاز الخادم نهائيًا.") },
            confirmButton = {
                TextButton(onClick = { showDelete?.let { vm.deleteMessage(it.id) }; showDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun MessageBubble(msg: SmsMessageDto, onDelete: () -> Unit) {
    val isOut = msg.direction == "OUT"
    val bubbleColor = if (isOut) SovereignColors.SurfaceCard else SovereignColors.Emerald.copy(alpha = .20f)
    val bubbleShape = if (isOut) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isOut) Alignment.End else Alignment.Start) {
        Box(Modifier.widthIn(max = 300.dp).clip(bubbleShape).background(bubbleColor).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column {
                Text(msg.content, fontSize = 15.sp)
                Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(msg.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isOut) DeliveryTick(msg.status)
                }
            }
        }
        // حذف طويل
        if (isOut) {
            Text("اضغط مطولًا للحذف", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DeliveryTick(status: String) {
    val (icon, tint) = when (status.uppercase(Locale.ROOT)) {
        "DELIVERED" -> Icons.Default.DoneAll to YounesEmerald
        "SENT" -> Icons.Default.Check to YounesEmerald
        "FAILED" -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        else -> Icons.Default.Check to Color.Gray
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
}

private fun smsParts(text: String): Int {
    val length = text.length
    val isUnicode = text.any { it.code > 127 }
    return if (isUnicode) (length / 70).coerceAtLeast(1) else (length / 160).coerceAtLeast(1)
}

private fun formatTime(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
