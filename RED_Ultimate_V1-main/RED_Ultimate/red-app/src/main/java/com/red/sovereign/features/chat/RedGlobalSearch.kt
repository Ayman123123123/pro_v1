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
fun RedGlobalSearch(onBack: () -> Unit = {}) {
    var searchQuery by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("بحث في الرسائل، المجموعات، أو الوسائط...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SovereignColors.Cyan) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SovereignColors.Cyan, unfocusedBorderColor = Color.Gray)
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text("نتائج البحث السيادي", style = MaterialTheme.typography.labelMedium, color = SovereignColors.Cyan)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
            if (searchQuery.length < 3) {
                item { 
                    Text("اكتب 3 أحرف على الأقل للبحث في أرشيفك المشفر.", 
                        modifier = Modifier.padding(16.dp), color = Color.Gray) 
                }
            } else {
                // Future: Integration with RedDatabase FTS
                item { Text("لا توجد نتائج مطابقة لـ '$searchQuery' حالياً.", color = Color.LightGray) }
            }
        }
    }
}
