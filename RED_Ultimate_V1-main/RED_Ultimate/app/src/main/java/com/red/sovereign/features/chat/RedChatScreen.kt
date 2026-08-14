package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedChatDetailScreen(
    chatId: String,
    onBack: () -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    
    // Mock Data
    val messages = listOf(
        Message("1", "Hello there!", "10:30 AM", isMe = false, "READ"),
        Message("2", "Hi! How are you?", "10:31 AM", isMe = true, "READ"),
        Message("3", "Are we still meeting today?", "10:35 AM", isMe = false, "READ"),
        Message("4", "Yes, let me send you the files first.", "10:36 AM", isMe = true, "SENT")
    )

    Scaffold(
        containerColor = Color(0xFF0A0A0A), // Deep dark mode
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Name and Status
                        Column {
                            Text("Ayman", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Online", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onAudioCall(chatId) }) {
                        Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = Color.White)
                    }
                    IconButton(onClick = { onVideoCall(chatId) }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                    }
                    IconButton(onClick = { /* More Options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF141414)
                )
            )
        },
        bottomBar = {
            RedMessageInput(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = { 
                    /* TODO: Send message */ 
                    messageText = ""
                },
                onAttach = { /* Open modern attach sheet */ },
                onRecordVoice = { /* Start voice recording */ }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            items(messages) { msg ->
                LuxuryChatBubble(
                    message = msg.content,
                    isMe = msg.isMe,
                    time = msg.time,
                    status = msg.status
                )
            }
        }
    }
}

data class Message(
    val id: String,
    val content: String,
    val time: String,
    val isMe: Boolean,
    val status: String // "SENT", "DELIVERED", "READ"
)
