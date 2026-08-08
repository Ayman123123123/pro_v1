package com.red.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.red.core.theme.REDTheme
import com.red.features.chat.RedChatListScreen
import com.red.features.calls.RedCallLogScreen
import com.red.features.explore.RedExploreScreen
import com.red.features.pstn.PstnDialerScreen
import com.red.features.profile.RedSettingsScreen

data class NavTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun RedMainDashboard() {
    REDTheme {
        com.red.core.theme.SovereignBackground {
            var selectedTab by remember { mutableStateOf(0) }
            
            val tabs = listOf(
                NavTab("المحادثات", Icons.Default.ChatBubbleOutline, Icons.Default.ChatBubble),
                NavTab("المكالمات", Icons.Default.PhoneOutlined, Icons.Default.Phone),
                NavTab("لوحة الاتصال", Icons.Default.Dialpad, Icons.Default.Dialpad),
                NavTab("الاستكشاف", Icons.Default.ExploreOutlined, Icons.Default.Explore),
                NavTab("الإعدادات", Icons.Default.SettingsOutlined, Icons.Default.Settings)
            )

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isDinstarTab = index == 2 // Dialpad / Dinstar tab gets special gold tint
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(tab.title) },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isDinstarTab) Color(0xFFF4B400) else LocalContentColor.current
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (isDinstarTab) Color(0xFFF4B400) else MaterialTheme.colorScheme.primary,
                                    indicatorColor = if (isDinstarTab) Color(0xFFF4B400).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.container
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (selectedTab) {
                        0 -> RedChatListScreen(
                            onChatClick = { chat -> /* TODO: navigate to chat detail */ },
                            onDinstarDial = { number -> selectedTab = 2 }
                        )
                        1 -> RedCallLogScreen()
                        2 -> PstnDialerScreen() // Dual Engine: VoIP vs Dinstar Yemeni Line
                        3 -> RedExploreScreen(
                            onStartLive = { /* TODO: launch live broadcast */ },
                            onStartSpace = { /* TODO: create audio space */ }
                        )
                        4 -> RedSettingsScreen(
                            onManageDinstar = { /* TODO: open Dinstar admin panel */ },
                            onLogout = { /* TODO: clear tokens and restart */ }
                        )
                    }
                }
            }
        }
    }
}
