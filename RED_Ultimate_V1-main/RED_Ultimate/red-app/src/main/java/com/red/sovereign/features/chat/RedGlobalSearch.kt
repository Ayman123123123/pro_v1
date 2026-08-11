package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🔍 YOUNES Global Search — البحث السيادي الشامل
 */
@Composable
fun RedGlobalSearch(
    onBack: () -> Unit = {},
    onOpenConversation: (senderRedId: String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 🔍 يبحث في قاعدة بيانات الرسائل الفعلية (Room) التي تُخزَّن فيها الرسائل
    // المفكوكة محلياً — وليس في red_messages.db المنفصلة غير المحدثة.
    val repository = remember { com.red.sovereign.core.database.LocalRepository(context) }
    var results by remember { mutableStateOf(emptyList<com.red.sovereign.core.database.LocalHistoryEntity>()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            isSearching = true
            // 🔍 بحث محلي فقط على الجهاز — الخادم لا يرى النص
            results = repository.searchAll(searchQuery.trim())
            isSearching = false
        } else {
            results = emptyList()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("بحث مشفر في رسائلك — FTS5 محلي") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SovereignColors.Cyan) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SovereignColors.Cyan, unfocusedBorderColor = Color.Gray),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("نتائج البحث السيادي", style = MaterialTheme.typography.labelMedium, color = SovereignColors.Cyan)
            if (isSearching) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SovereignColors.Cyan)
            else if (searchQuery.length >= 2) Text("(${results.size})", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp).weight(1f)) {
            if (searchQuery.trim().length < 2) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("اكتب حرفين على الأقل", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("البحث يتم على جهازك فقط — الخادم لا يرى النص", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (results.isEmpty() && !isSearching) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Text("لا توجد نتائج لـ '$searchQuery' — جرب كلمة أخرى", color = Color.LightGray, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(results, key = { it.id }) { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(msg.senderId) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                com.red.sovereign.core.RichMessage.decode(msg.encryptedPlaintext)?.text?.take(80)
                                    ?: msg.encryptedPlaintext.toString(Charsets.UTF_8).take(80),
                                maxLines = 2,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${msg.senderId.take(12)} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(msg.createdAt))}",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
