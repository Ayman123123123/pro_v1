package com.red.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RedGlobalSearch() {
    var searchQuery by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search messages, groups, or media...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Search Results", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        
        // Results list logic...
        LazyColumn {
            item { Text("Enter 3 or more characters to search RED history.", modifier = Modifier.padding(16.dp), color = androidx.compose.ui.graphics.Color.Gray) }
        }
    }
}
