package com.red.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage

data class Conversation(
    val id: String,
    val name: String,
    val lastMsg: String,
    val time: String,
    val unreadCount: Int,
    val avatarUrl: String?
)

@Composable
fun ChatListScreen(navController: NavController) {
    val mockChats = listOf(
        Conversation("1", "Engineer Team", "The 4K VoIP is ready.", "10:45 AM", 3, null),
        Conversation("2", "Admin", "User registration approved.", "Yesterday", 0, null),
        Conversation("3", "Dumin Gateway", "SIM 1 Status: Active", "Monday", 0, null)
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(title = { Text("Chats", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* New Chat Logic */ }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(mockChats) { chat ->
                ConversationItem(chat) {
                    navController.navigate("chat_detail/${chat.id}")
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 72.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
fun ConversationItem(chat: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Surface(
            modifier = Modifier.size(56.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (chat.avatarUrl != null) {
                AsyncImage(model = chat.avatarUrl, contentDescription = null)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(chat.name.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chat.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(chat.time, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chat.lastMsg, color = Color.Gray, maxLines = 1, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (chat.unreadCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(chat.unreadCount.toString(), color = Color.White)
                    }
                }
            }
        }
    }
}
