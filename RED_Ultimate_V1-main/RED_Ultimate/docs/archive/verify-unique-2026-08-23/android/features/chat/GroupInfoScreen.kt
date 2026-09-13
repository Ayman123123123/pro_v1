package com.red.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GroupInfoScreen(groupId: String) {
    val members = listOf(
        GroupMember("1", "Admin User", "OWNER"),
        GroupMember("2", "Engineer 1", "ADMIN"),
        GroupMember("3", "Client A", "MEMBER")
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Group Info") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                GroupHeaderSection("Engineering Team")
            }
            items(members) { member ->
                ListItem(
                    headlineContent = { Text(member.name) },
                    supportingContent = { Text(member.role) },
                    trailingContent = {
                        if (member.role != "MEMBER") Icon(Icons.Default.Shield, null, tint = Color.Blue)
                    }
                )
            }
        }
    }
}

data class GroupMember(val id: String, val name: String, val role: String)

@Composable
fun GroupHeaderSection(name: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(100.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text(name.take(1), style = MaterialTheme.typography.headlineLarge) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.headlineMedium)
    }
}
