package com.red.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.red.core.theme.REDTheme
import com.red.features.chat.RedChatListScreen
import com.red.features.calls.RedCallLogScreen
import com.red.features.explore.RedExploreScreen
import com.red.features.pstn.PstnDialerScreen
import com.red.features.profile.RedSettingsScreen
import com.red.features.dinstar.DinstarTab
import com.red.features.dinstar.DinstarViewModel

/**
 * 🏛️ YOUNES Main Dashboard — 5 تبويبات متكاملة
 * كل التبويبات مربوطة بـ Navigation callbacks حقيقية
 */
data class NavTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val badge: Int = 0 // عدد التنبيهات
)

@Composable
fun RedMainDashboard(
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToCall: (String) -> Unit = {},
    onNavigateToVideo: (String) -> Unit = {},
    onNavigateToPstn: (String) -> Unit = {},
    onNavigateToLive: () -> Unit = {},
    onNavigateToSpace: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDinstar: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    REDTheme {
        com.red.core.theme.SovereignBackground {
            var selectedTab by remember { mutableStateOf(0) }
            var unreadChats by remember { mutableStateOf(3) }
            var missedCalls by remember { mutableStateOf(1) }
            val dinstarViewModel = remember { DinstarViewModel() }

            val tabs = listOf(
                NavTab("المحادثات", Icons.Default.ChatBubbleOutline, Icons.Default.ChatBubble, unreadChats),
                NavTab("المكالمات", Icons.Default.PhoneOutlined, Icons.Default.Phone, missedCalls),
                NavTab("لوحة الاتصال", Icons.Default.Dialpad, Icons.Default.Dialpad),
                NavTab("الاستكشاف", Icons.Default.ExploreOutlined, Icons.Default.Explore),
                NavTab("المزيد", Icons.Default.MoreHoriz, Icons.Default.MoreVert)
            )

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isDinstarTab = index == 2
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(tab.title, maxLines = 1) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab.badge > 0) {
                                                Badge(containerColor = if (isDinstarTab) Color(0xFFF4B400) else MaterialTheme.colorScheme.primary) {
                                                    Text(tab.badge.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selectedTab == index) tab.selectedIcon else tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isDinstarTab) Color(0xFFF4B400) else LocalContentColor.current
                                        )
                                    }
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
                            onChatClick = { chat -> onNavigateToChat(chat.id) },
                            onDinstarDial = { number -> onNavigateToPstn(number) }
                        )
                        1 -> RedCallLogScreen()
                        2 -> DinstarTab(
                            viewModel = dinstarViewModel,
                            onDialViaPort = { port, number -> onNavigateToPstn(number) },
                            onNavigateToCdr = { onNavigateToDinstar() },
                            onNavigateToSms = { onNavigateToDinstar() }
                        )
                        3 -> RedExploreScreen(
                            onStartLive = onNavigateToLive,
                            onStartSpace = onNavigateToSpace
                        )
                        4 -> RedSettingsScreen(
                            onManageDinstar = onNavigateToDinstar,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    }
}
