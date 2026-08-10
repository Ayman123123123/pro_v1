package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.chat.LuxuryChatBubble

@Composable
fun ChatDetailScreen(chatId: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("RED Squad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("typing...", fontSize = 12.sp, color = Color.Red)
                    }
                },
                actions = {
                    IconButton(onClick = { /* VoIP Call */ }) { Icon(Icons.Default.Call, null, tint = Color.White) }
                    IconButton(onClick = { /* Video Call */ }) { Icon(Icons.Default.Videocam, null, tint = Color.White) }
                }
            )
        },
        bottomBar = {
            ChatInputBar()
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(listOf("Hello Team!", "System B is now live.", "AV1 Codec testing...")) { msg ->
                LuxuryChatBubble(message = msg, isMe = msg.contains("System"), status = "READ")
            }
        }
    }
}

@Composable
fun ChatInputBar() {
    Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.Default.Add, null, tint = Color.Red) }
            TextField(
                value = "", onValueChange = {}, 
                placeholder = { Text("RED Encrypted Message") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )
            IconButton(onClick = {}) { Icon(Icons.Default.Mic, null, tint = Color.Red) }
            IconButton(onClick = {}) { Icon(Icons.Default.Send, null, tint = Color.Red) }
        }
    }
}
