package com.red.sovereign.features.chat

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RedChatDetailScreen(chatId: String) {
    var messageText by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = { RedChatTopBar(chatId) },
        bottomBar = {
            RedMessageInput(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = { /* Logic for guaranteed delivery */ },
                onAttach = { /* File picker for all formats: PDF, APK, ZIP */ }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(listOf("Welcome to RED", "Testing 1080p Call", "File transferred ✓✓")) { msg ->
                RedMessageBubble(
                    content = msg,
                    isMe = msg.contains("✓"),
                    reactions = listOf("❤️", "👍") // ميزة التفاعل مدمجة
                )
            }
        }
    }
}

@Composable
fun RedMessageBubble(content: String, isMe: Boolean, reactions: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) Color(0xFFB71C1C) else Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(content, color = Color.White, modifier = Modifier.padding(12.dp))
        }
        // التفاعلات بالإيموجي (Reactions) أسفل الفقاعة
        Row {
            reactions.forEach { emoji ->
                Text(emoji, fontSize = 12.sp, modifier = Modifier.padding(2.dp))
            }
        }
    }
}
