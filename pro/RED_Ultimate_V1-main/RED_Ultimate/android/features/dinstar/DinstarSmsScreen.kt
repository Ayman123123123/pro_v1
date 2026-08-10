package com.red.features.dinstar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.red.core.theme.SovereignColors

/**
 * 📱 YOUNES Dinstar SMS Screen — إرسال واستقبال SMS عبر بوابة GSM
 * 
 * الميزات:
 * - إرسال SMS فردي ومجمّع
 * - اختيار ترميز GSM 7-bit أو UCS2 (Unicode)
 * - اختيار منفذ/منافذ محددة
 * - عرض SMS الواردة
 * - تتبع حالة الإرسال والتسليم
 * - إيقاف مهمة إرسال
 */
@Composable
fun DinstarSmsScreen(
    viewModel: DinstarViewModel,
    onBack: () -> Unit = {}
) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val incomingSms by viewModel.incomingSms.collectAsStateWithLifecycle()
    val smsSendResults by viewModel.smsSendResults.collectAsStateWithLifecycle()
    val smsQueueCount by viewModel.smsQueueCount.collectAsStateWithLifecycle()
    val commandResult by viewModel.commandResult.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var smsText by remember { mutableStateOf("") }
    var recipientsText by remember { mutableStateOf("") }
    var selectedPorts by remember { mutableStateOf(setOf<Int>()) }
    var encoding by remember { mutableStateOf("GSM7BIT") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
    ) {
        // رأس
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = SovereignColors.DinstarGold) }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Sms, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SMS عبر DINSTAR", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("إرسال واستقبال — ${gatewayStatus.registeredCount} شرائح مسجلة", fontSize = 11.sp, color = Color.Gray)
            }
            if (smsQueueCount > 0) {
                Badge(containerColor = SovereignColors.Warning) { Text("$smsQueueCount", color = Color.Black, fontSize = 10.sp) }
            }
        }

        // تبويبات
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SovereignColors.Navy,
            contentColor = SovereignColors.DinstarGold
        ) {
            listOf("إرسال", "واردة", "نتائج").forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) SovereignColors.DinstarGold else Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }

        AnimatedContent(targetState = selectedTab, label = "SmsTab") { tab ->
            when (tab) {
                0 -> SmsSendTab(
                    viewModel = viewModel,
                    smsText = smsText, onSmsTextChange = { smsText = it },
                    recipientsText = recipientsText, onRecipientsChange = { recipientsText = it },
                    selectedPorts = selectedPorts, onPortsChange = { selectedPorts = it },
                    encoding = encoding, onEncodingChange = { encoding = it },
                    gatewayStatus = gatewayStatus
                )
                1 -> SmsIncomingTab(incomingSms)
                2 -> SmsResultsTab(smsSendResults)
            }
        }
    }
}

@Composable
private fun SmsSendTab(
    viewModel: DinstarViewModel,
    smsText: String, onSmsTextChange: (String) -> Unit,
    recipientsText: String, onRecipientsChange: (String) -> Unit,
    selectedPorts: Set<Int>, onPortsChange: (Set<Int>) -> Unit,
    encoding: String, onEncodingChange: (String) -> Unit,
    gatewayStatus: DinstarGatewayStatus
) {
    var isSending by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إرسال SMS جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SovereignColors.DinstarGold)
        }

        // حقل الأرقام
        item {
            OutlinedTextField(
                value = recipientsText, onValueChange = onRecipientsChange,
                label = { Text("الأرقام (مفصولة بفاصلة)") },
                placeholder = { Text("777123456, 777987654") },
                leadingIcon = { Icon(Icons.Rounded.Phone, null, tint = SovereignColors.DinstarGold) },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4
            )
        }

        // حقل النص
        item {
            OutlinedTextField(
                value = smsText, onValueChange = onSmsTextChange,
                label = { Text("محتوى الرسالة") },
                placeholder = { Text("اكتب رسالتك هنا...") },
                leadingIcon = { Icon(Icons.Rounded.Chat, null, tint = SovereignColors.Cyan) },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 8
            )
            val byteCount = smsText.toByteArray(Charsets.UTF_8).size
            val charInfo = if (encoding == "GSM7BIT") "$byteCount بايت (GSM 7-bit: ${160 - byteCount / 7} حرف متبقي)" 
                           else "$byteCount بايت (UCS2: ${70 - byteCount / 2} حرف متبقي)"
            Text(charInfo, fontSize = 10.sp, color = if (byteCount > 140) SovereignColors.Danger else Color.Gray)
        }

        // ترميز
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الترميز:", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = encoding == "GSM7BIT", onClick = { onEncodingChange("GSM7BIT") },
                    label = { Text("GSM 7-bit") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = encoding == "UCS2", onClick = { onEncodingChange("UCS2") },
                    label = { Text("UCS2 (Unicode)") }
                )
                Spacer(Modifier.width(12.dp))
                Text(if (encoding == "GSM7BIT") "ASCII + لاتيني" else "عربي + أي لغة", fontSize = 10.sp, color = Color.Gray)
            }
        }

        // اختيار المنافذ
        item {
            Text("المنافذ (اختياري):", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                gatewayStatus.ports.filter { it.isAvailable }.forEach { port ->
                    val isSelected = port.index in selectedPorts
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPortsChange(if (isSelected) selectedPorts - port.index else selectedPorts + port.index) },
                        label = { Text("P${port.index} ${port.signalPercent}%") },
                        leadingIcon = { Icon(Icons.Rounded.SimCard, null, modifier = Modifier.size(14.dp), tint = Color(port.simType.colorHex)) }
                    )
                }
            }
        }

        // زر إرسال
        item {
            val numbers = recipientsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
            Button(
                onClick = {
                    if (smsText.isNotBlank() && numbers.isNotEmpty()) {
                        isSending = true
                        viewModel.sendSms(
                            text = smsText, numbers = numbers,
                            ports = selectedPorts.toList(), encoding = encoding
                        )
                        isSending = false
                    }
                },
                enabled = smsText.isNotBlank() && numbers.isNotEmpty() && !isSending,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.DinstarGold),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("إرسال إلى ${numbers.size} رقم", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SmsIncomingTab(incomingSms: List<DinstarIncomingSms>) {
    if (incomingSms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Inbox, null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("لا توجد رسائل واردة", color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(incomingSms) { sms ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SimCard, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(sms.number, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(sms.timestamp, fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(sms.text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                        Text("منفذ ${sms.port}", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsResultsTab(results: List<DinstarSmsResult>) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.History, null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("لا توجد نتائج", color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { result ->
                val isSuccess = result.status == "SENT_OK"
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                            null, tint = if (isSuccess) SovereignColors.Success else SovereignColors.Danger, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.number, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("منفذ ${result.port} • ${result.successCount}/${result.count} نجح", fontSize = 10.sp, color = Color.Gray)
                        }
                        Text(result.status, fontSize = 11.sp, color = if (isSuccess) SovereignColors.Success else SovereignColors.Danger)
                    }
                }
            }
        }
    }
}
