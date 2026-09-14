package com.red.sovereign.features.dinstar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شاشة SMS عبر البوابة - حقيقية كاملة Material3 Expressive 2026
 * - إرسال مجمّع
 * - الوارد المخزن
 * - نتائج الإرسال + طابور
 * - كشف المشغل اليمني
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinstarSmsScreen(viewModel: DinstarViewModel, onBack: () -> Unit = {}) {
    val incomingSms by viewModel.incomingSms.collectAsState()
    val sendResults by viewModel.smsSendResults.collectAsState()
    val queueCount by viewModel.smsQueueCount.collectAsState()
    val commandResult by viewModel.commandResult.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var smsText by remember { mutableStateOf("") }
    var numbersText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212))) {
        if (onBack != { }) {
            TopAppBar(
                title = { Text("SMS عبر البوابة", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        }

        TabRow(selectedTabIndex = tab, containerColor = Color(0xFF1E1E1E), contentColor = AqyalGold) {
            listOf("إرسال", "واردة", "السجل").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }) {
                    Text(title, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, color = if (tab == i) AqyalGold else Color.Gray, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }

        when (tab) {
            0 -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إرسال SMS مجمّع", color = AqyalGold, fontWeight = FontWeight.Bold)
                        Text("في الطابور: $queueCount", color = Color.Gray, fontSize = 12.sp)
                        OutlinedTextField(value = numbersText, onValueChange = { numbersText = it }, label = { Text("أرقام مفصولة بفاصلة أو سطر") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(value = smsText, onValueChange = { if (it.length <= 918) smsText = it }, label = { Text("نص الرسالة (حتى 918 حرف)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Text("${gatewaySegmentCount(smsText, \"AUTO\")} جزء • ${smsText.length} حرف", fontSize = 11.sp, color = Color.Gray)
                        Button(onClick = {
                            val numbers = numbersText.split(",", "\n", " ").map { it.trim() }.filter { it.isNotBlank() }
                            if (numbers.isNotEmpty() && smsText.isNotBlank()) {
                                viewModel.sendSms(smsText, numbers)
                                smsText = ""
                            }
                        }, enabled = smsText.isNotBlank() && numbersText.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("إرسال عبر DINSTAR")
                        }
                        commandResult?.let {
                            when (it) {
                                is DinstarCommandResult.Loading -> CircularProgressIndicator(Modifier.size(20.dp), color = AqyalGold)
                                is DinstarCommandResult.Success -> Text(it.message, color = YounesEmerald, fontSize = 12.sp)
                                is DinstarCommandResult.Error -> Text(it.message, color = Color(0xFFF44336), fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (sendResults.isNotEmpty()) {
                    Text("آخر النتائج", color = Color.White, fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(sendResults.take(10)) { result ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("حالة: ${result.status}", color = if (result.status == \"ACCEPTED\" || result.status == \"SENT\") YounesEmerald else Color.Gray, fontSize = 13.sp)
                                    Text("أرقام: ${result.numbers.joinToString(\", \")}", color = Color.White, fontSize = 12.sp)
                                    result.taskId?.let { Text("مهمة: $it", color = Color.Gray, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LaunchedEffect(Unit) { viewModel.fetchIncomingSms() }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    if (incomingSms.isEmpty()) {
                        item { Text("لا توجد رسائل واردة", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                    } else {
                        items(incomingSms) { sms ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Sms, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(sms.number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("منفذ ${sms.port}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    Text(sms.text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                    Text(java.text.SimpleDateFormat(\"dd/MM HH:mm\", java.util.Locale.getDefault()).format(java.util.Date(sms.receivedAt)), color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    if (sendResults.isEmpty()) {
                        item { Text("لا يوجد سجل إرسال", color = Color.Gray) }
                    } else {
                        items(sendResults) { result ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (result.status == \"ACCEPTED\") Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (result.status == \"ACCEPTED\") YounesEmerald else Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(result.numbers.joinToString(\", \"), color = Color.White, fontSize = 12.sp)
                                        Text(result.status, color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun gatewaySegmentCount(text: String, encoding: String): Int {
    if (text.isEmpty()) return 0
    val ucs2 = when (encoding.uppercase()) {
        \"UCS2\" -> true
        \"GSM7BIT\" -> false
        else -> text.any { it.code > 0x7F }
    }
    val single = if (ucs2) 70 else 160
    val multi = if (ucs2) 67 else 153
    return if (text.length <= single) 1 else 1 + (text.length - single + multi - 1) / multi
}
