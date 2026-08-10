package com.red

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.red.feature.chat.ChatListScreen
import com.red.feature.stories.StoryListScreen
import com.red.feature.calls.CallLogScreen
import com.red.feature.pstn.DialPadScreen
import com.red.feature.profile.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            REDTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Chats,
        Screen.Stories,
        Screen.Calls,
        Screen.Phone,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Chats.route, Modifier.padding(innerPadding)) {
            composable(Screen.Chats.route) { ChatListScreen(navController) }
            composable(Screen.Stories.route) { StoryListScreen() }
            composable(Screen.Calls.route) { CallLogScreen() }
            composable(Screen.Phone.route) { DialPadScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Chats : Screen("chats", "Chats", Icons.Default.Chat)
    object Stories : Screen("stories", "Status", Icons.Default.History)
    object Calls : Screen("calls", "Calls", Icons.Default.Call)
    object Phone : Screen("phone", "Phone", Icons.Default.Dialpad)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun REDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF2196F3),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            background = androidx.compose.ui.graphics.Color(0xFF121212)
        ),
        content = content
    )
}
