package com.red.sovereign.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.red.sovereign.core.delivery.MasterDeliveryEngine

@Composable
fun ChatDetailScreen(chatId: String, viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = { /* User Avatar, Name, Online Status, Call Icons */ },
        bottomBar = {
            ChatInputBar(onSend = { text -> viewModel.sendMessage(chatId, text) })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages) { msg ->
                RedChatBubble(msg.content, msg.isMe, msg.status)
            }
        }
    }
}
