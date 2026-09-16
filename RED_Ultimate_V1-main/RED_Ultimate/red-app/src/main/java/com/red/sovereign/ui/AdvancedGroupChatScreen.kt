package com.red.sovereign.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.red.sovereign.agents.RedAgentRegistry

data class GroupMessage(
    val id: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val isPinned: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val threadCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedGroupChatScreen(
    groupName: String,
    onBack: () -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    var selectedSubChannel by remember { mutableStateOf("General") }
    var showAiDashboard by remember { mutableStateOf(false) }

    val subChannels = listOf("General", "Announcements", "Voice Rooms", "AI Agents", "Media Vault", "Polls")
    val messages = remember {
        mutableStateListOf(
            GroupMessage("1", "Sovereign Admin", "Welcome to RED Ultimate Group! Powered by 20 AI Agents.", "10:00 AM", true, mapOf("❤️" to 12, "🚀" to 8), 3),
            GroupMessage("2", "AI Master Coordinator", "All 20 agents are active and monitoring group security & translations.", "10:02 AM", false, mapOf("🤖" to 5), 1),
            GroupMessage("3", "Younes (Lead)", "Let's test the new sub-channels and encrypted voice rooms.", "10:05 AM", false, mapOf("👍" to 4), 0)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = groupName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = "Sub-channel: $selectedSubChannel • 20 AI Agents Active", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiDashboard = !showAiDashboard }) {
                        Icon(Icons.Default.SmartToy, contentDescription = "AI Agents Hub")
                    }
                    IconButton(onClick = { /* Open Voice Room */ }) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = "Voice Room")
                    }
                    IconButton(onClick = { /* Group Settings */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Sub-channel selector bar
                ScrollableTabRow(
                    selectedTabIndex = subChannels.indexOf(selectedSubChannel),
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    subChannels.forEach { channel ->
                        Tab(
                            selected = selectedSubChannel == channel,
                            onClick = { selectedSubChannel = channel },
                            text = { Text(channel, fontSize = 12.sp) }
                        )
                    }
                }

                // Message input bar superior to WhatsApp
                Surface(
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Attach Media / Vault */ }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { /* Encrypted Poll */ }) {
                            Icon(Icons.Default.Poll, contentDescription = "Poll", tint = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Message ($selectedSubChannel)... or type /summarize") },
                            modifier = Modifier.weight(1.0f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    messages.add(
                                        GroupMessage(
                                            id = System.currentTimeMillis().toString(),
                                            senderName = "You",
                                            content = messageInput,
                                            timestamp = "Just now"
                                        )
                                    )
                                    messageInput = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.senderName == "You")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = msg.senderName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = msg.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = msg.content, fontSize = 15.sp)

                            if (msg.isPinned) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("📌 Pinned Message", fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }

            // AI Agents Hub Overlay Drawer / Dialog
            if (showAiDashboard) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(320.dp).align(Alignment.CenterEnd),
                    tonalElevation = 16.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("20 AI Agents Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(onClick = { showAiDashboard = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(RedAgentRegistry.getAllAgents()) { agent ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(text = agent.agentName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = agent.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "Status: ACTIVE 🟢", fontSize = 10.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
