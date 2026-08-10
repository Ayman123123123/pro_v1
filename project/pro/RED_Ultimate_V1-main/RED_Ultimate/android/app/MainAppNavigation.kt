package com.red.sovereign.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.red.sovereign.features.auth.*
import com.red.sovereign.features.chat.*
import com.red.sovereign.features.calls.*
import com.red.sovereign.features.pstn.*
import com.red.sovereign.features.profile.*
import com.red.sovereign.features.stories.*

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auth") {
        // Authentication Flow
        composable("auth") { WelcomeScreen(onLogin = { navController.navigate("main") }) }
        
        // Main Application Dashboard (Tabs)
        composable("main") {
            RedDashboard(
                onNavigateToChat = { id -> navController.navigate("chat_detail/$id") },
                onNavigateToCall = { id -> navController.navigate("voip_call/$id") },
                onNavigateToPhone = { num -> navController.navigate("pstn_call/$num") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        // Feature Screens
        composable("chat_detail/{chatId}") { backStack ->
            ChatDetailScreen(backStack.arguments?.getString("chatId") ?: "")
        }

        composable("voip_call/{userId}") { backStack ->
            VideoCallScreen(remoteName = backStack.arguments?.getString("userId") ?: "") {
                navController.popBackStack()
            }
        }

        composable("pstn_call/{number}") { backStack ->
            PstnCallScreen(phoneNumber = backStack.arguments?.getString("number") ?: "") {
                navController.popBackStack()
            }
        }

        composable("settings") { SettingsScreen(navController) }
        
        composable("dumin_settings") { 
            // Dumin hardware config screen
        }

        composable("create_story") {
            CreateStoryScreen(onFinished = { navController.popBackStack() })
        }
    }
}
