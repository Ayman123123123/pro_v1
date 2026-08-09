package com.red.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchScreen(conversationId: String) {
    var query by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { 
                query = it
                // Simulate search logic
                if (it.length > 2) searchResults.add("Result for '$it'")
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search messages...") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        
        LazyColumn {
            items(searchResults) { result ->
                ListItem(headlineContent = { Text(result) })
            }
        }
    }
}
