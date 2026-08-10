package com.red.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.red.feature.auth.*

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "permissions") {
        composable("permissions") {
            PermissionRequestScreen(onAllPermissionsGranted = {
                navController.navigate("welcome") {
                    popUpTo("permissions") { inclusive = true }
                }
            })
        }
        composable("welcome") {
            WelcomeScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }
        composable("register") {
            RegisterScreen(onRegistrationSubmitted = {
                navController.navigate("pending_approval")
            })
        }
        composable("login") {
            LoginScreen(onLoginSuccess = {
                // Redirect to Main App (Chat List) - to be implemented in Step 4
                navController.navigate("chats") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("pending_approval") {
            PendingApprovalScreen()
        }
        composable("rejected") {
            RejectedScreen()
        }
        composable("banned") {
            BannedScreen()
        }
        composable("chats") {
            // Placeholder for Step 4
        }
    }
}
