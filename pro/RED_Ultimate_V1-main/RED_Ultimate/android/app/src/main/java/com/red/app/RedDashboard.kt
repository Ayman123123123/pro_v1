package com.red.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.red.core.theme.REDTheme

/**
 * RED Sovereign UI
 * The most advanced interface in 2026.
 */
@Composable
fun RedDashboard() {
    REDTheme {
        var selectedTab by remember { mutableStateOf(0) }
        
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val tabs = listOf("Chats", "Status", "Calls", "Phone", "Settings")
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(title) },
                            icon = { /* Icons implemented in features */ }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> ChatListScreen()
                    1 -> StatusListScreen()
                    2 -> CallLogScreen()
                    3 -> PstnDialerScreen() // System B: Dumin
                    4 -> SettingsScreen()
                }
            }
        }
    }
}
