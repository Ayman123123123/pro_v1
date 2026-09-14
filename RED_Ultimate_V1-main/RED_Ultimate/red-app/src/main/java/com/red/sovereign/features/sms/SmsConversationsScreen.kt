package com.red.sovereign.features.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.red.sovereign.ui.theme.YounesEmerald
import java.text.SimpleDateFormat
import java.util.*

/**
 * شاشة محادثات SMS - حقيقية Material3 Expressive 2026
 * - قائمة المحادثات مع آخر رسالة
 * - بحث
 * - اتصال مباشر بالرقم
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConversationsScreen(
    vm: SmsViewModel,
    onOpenChat: (String) -> Unit,
    onCallNumber: (String) -> Unit = {}
) {
    val conversations by vm.conversations.collectAsState()
    val connected by vm.connectedState.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A1628))) {
        TopAppBar(
            title = {
                Column {
                    Text("رسائل SMS", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (connected) "متصل • ${conversations.size} محادثة" else "غير متصل", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            },
            actions = {
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A1628))
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("بحث في المحادثات...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f))
        )

        val filtered = remember(conversations, query) {
            if (query.isBlank()) conversations else conversations.filter { it.number.contains(query) || it.lastText.contains(query, true) }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Sms, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text("لا توجد محادثات", color = Color.Gray)
                    Text("ستظهر هنا رسائل SMS عبر DINSTAR", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.number }) { conv ->
                    val opInfo = remember(conv.number) { com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(conv.number) }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenChat(conv.number) },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = YounesEmerald)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(conv.number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    opInfo?.let {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(it.brandColor))
                                    }
                                }
                                Text(conv.lastText.take(50), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
                                Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(conv.lastTime)), color = Color.Gray, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onCallNumber(conv.number) }) {
                                Icon(Icons.Default.Call, null, tint = YounesEmerald)
                            }
                        }
                    }
                }
            }
        }
    }
}
