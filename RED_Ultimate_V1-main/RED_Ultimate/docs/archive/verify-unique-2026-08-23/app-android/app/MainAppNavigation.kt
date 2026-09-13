package com.red.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.red.features.auth.*
import com.red.features.chat.*
import com.red.features.calls.*
import com.red.features.pstn.*

/**
 * The Master Controller: One App, Three Systems, One Vision.
 */
@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auth_flow") {
        
        // 1. Auth & Admin Approval (The Gatekeeper)
        composable("auth_flow") { 
            // Ensures user is APPROVED before seeing anything else
            AuthFlowHandler(
                onApproved = { navController.navigate("main_dashboard") },
                onRejected = { /* Show Rejected */ }
            )
        }

        // 2. The Integrated Dashboard (Tabs: Chat, Stories, Calls, PSTN, Settings)
        composable("main_dashboard") {
            MainDashboard(
                onChatSelected = { id -> navController.navigate("chat/$id") },
                onPstnDial = { num -> navController.navigate("pstn/$num") },
                onVoipCall = { id -> navController.navigate("voip/$id") }
            )
        }

        // 3. System A: VoIP 4K (Mediasoup/WebRTC)
        composable("voip/{userId}") { backStack ->
            val userId = backStack.arguments?.getString("userId") ?: ""
            VoipCallScreen(userId) 
        }

        // 4. System B: PSTN (Dumin/GSM - SEPARATE)
        composable("pstn/{number}") { backStack ->
            val number = backStack.arguments?.getString("number") ?: ""
            PstnDialerScreen(number)
        }
    }
}
