package com.red.sovereign.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.red.sovereign.features.calls.DialPadScreen
import com.red.sovereign.features.chat.ChatDetailScreen
import com.red.sovereign.features.chat.RedChatScreen

/**
 * RED Main Host — central navigation for the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedMainHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "chat_list") {
        composable("chat_list") {
            RedChatScreen(
                onChatClick = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }
        composable("chat/{id}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("id") ?: ""
            ChatDetailScreen(chatId)
        }
        composable("dial_pad") {
            DialPadScreen(onNavigateToCall = { number ->
                navController.navigate("call/$number")
            })
        }
    }
}
