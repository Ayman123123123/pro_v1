package com.red.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RedChatRoom(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val isGroup: Boolean,
    val yemeniNumber: String? = null // For Dinstar PSTN quick dial if available
)

@Composable
fun RedChatListScreen(
    onChatClick: (RedChatRoom) -> Unit,
    onDinstarDial: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Private, 2: Groups
    val chats = listOf(
        RedChatRoom("1", "أمجد باردوني", "أهلاً بك في منظومة RED السيادية", "10:45 AM", 2, false, "+967739876543"),
        RedChatRoom("2", "مجموعه مطوري اليمن", "تم ربط جهاز DINSTAR بنجاح 🔴", "09:30 AM", 5, true),
        RedChatRoom("3", "سعيد اليمني", "سأقوم بالاتصال بك عبر الخط اليمني قريباً", "أمس", 0, false, "+967771234567"),
        RedChatRoom("4", "غرفة العمليات المركزية", "تحديث الأمان الميداني مكتمل", "الأثنين", 1, true)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المحادثات السيادية",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* New Chat */ }) {
                Icon(Icons.Default.EditSquare, contentDescription = "محادثة جديدة", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs (All, Private, Groups)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            indicator = {},
            divider = {}
        ) {
            val tabs = listOf("الكل", "الخاصة", "المجموعات")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat List
        val filteredChats = when (selectedTab) {
            1 -> chats.filter { !it.isGroup }
            2 -> chats.filter { it.isGroup }
            else -> chats
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredChats) { chat ->
                RedChatRoomItem(
                    chat = chat,
                    onClick = { onChatClick(chat) },
                    onDinstarCall = { chat.yemeniNumber?.let { onDinstarDial(it) } }
                )
            }
        }
    }
}

@Composable
fun RedChatRoomItem(
    chat: RedChatRoom,
    onClick: () -> Unit,
    onDinstarCall: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar / Group Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(if (chat.isGroup) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF1E88E5).copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (chat.isGroup) Color(0xFF4CAF50) else Color(0xFF1E88E5),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chat.lastMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Time & Badges & Quick Dinstar Call Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = chat.time,
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // If Dinstar Yemeni number is available, show gold SIM call button
                    if (!chat.isGroup && chat.yemeniNumber != null) {
                        IconButton(
                            onClick = onDinstarCall,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFF4B400).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = "اتصال خطي يمني",
                                tint = Color(0xFFF4B400),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFF1E88E5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
