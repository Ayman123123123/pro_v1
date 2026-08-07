package com.red.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.red.feature.auth.*
import com.red.feature.chat.*
import com.red.feature.stories.*
import com.red.feature.calls.*
import com.red.feature.pstn.*
import com.red.feature.profile.*
import com.red.MainActivity

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "permissions") {
        // AUTH SYSTEM
        composable("permissions") { PermissionRequestScreen { navController.navigate("welcome") } }
        composable("welcome") { WelcomeScreen({ navController.navigate("register") }, { navController.navigate("login") }) }
        composable("register") { RegisterScreen(onRegistrationSubmitted = { navController.navigate("pending_approval") }) }
        composable("login") { LoginScreen { navController.navigate("main") } }
        composable("pending_approval") { PendingApprovalScreen() }

        // MAIN APP SYSTEM
        composable("main") { MainActivity() } // This loads the BottomNav
        
        // CHAT DETAIL (with ID)
        composable(
            route = "chat_detail/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatDetailScreen(chatId)
        }

        // PSTN CALL SCREEN
        composable(
            route = "pstn_call/{number}",
            arguments = listOf(navArgument("number") { type = NavType.StringType })
        ) { backStackEntry ->
            val number = backStackEntry.arguments?.getString("number") ?: ""
            PstnCallScreen(number, onCallEnded = { navController.popBackStack() })
        }
    }
}
