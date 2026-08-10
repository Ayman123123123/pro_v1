package com.red.feature.chat

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.red.core.delivery.MessageEntity
import com.red.core.delivery.MessageStatus

@Composable
fun ChatDetailScreen(
    conversationId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var textState by remember { mutableStateOf("") }
    val messages by viewModel.getMessages(conversationId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engineer Team") },
                actions = {
                    IconButton(onClick = { /* VoIP Call */ }) { Icon(Icons.Default.Call, null) }
                    IconButton(onClick = { /* Video Call */ }) { Icon(Icons.Default.Videocam, null) }
                }
            )
        },
        bottomBar = {
            ChatInput(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    if (textState.isNotBlank()) {
                        viewModel.sendMessage(conversationId, textState)
                        textState = ""
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity) {
    val isMe = msg.senderId == "me"
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(color, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(msg.payload, color = textColor, fontSize = 16.sp)
            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "12:00 PM", // Mock time
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    DeliveryStatusIcon(msg.status)
                }
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: MessageStatus) {
    val icon = when (status) {
        MessageStatus.SENDING -> Icons.Default.Schedule
        MessageStatus.SENT -> Icons.Default.Check
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.Error
    }
    val tint = if (status == MessageStatus.READ) Color(0xFF00B0FF) else Color.Gray
    Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint)
}

@Composable
fun ChatInput(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Attach */ }) { Icon(Icons.Default.Add, null) }
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
