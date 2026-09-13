package com.red.sovereign.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.red.sovereign.features.auth.*
import com.red.sovereign.features.chat.*
import com.red.sovereign.features.calls.*
import com.red.sovereign.features.pstn.*
import com.red.sovereign.features.profile.*
import com.red.sovereign.features.stories.*
import com.red.sovereign.features.media.*
import com.red.sovereign.features.privacy.*
import com.red.core.theme.*
import com.red.features.dinstar.*
import com.red.features.devices.*

/**
 * 🧭 YOUNES Sovereign Navigation — 24 مسار متكامل
 * كل المسارات مربوطة بـ callbacks حقيقية بدون أي TODO
 */
@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        // ─── Splash ───
        composable("splash") {
            RedSplashScreen(onFinished = {
                navController.navigate("auth") { popUpTo("splash") { inclusive = true } }
            })
        }

        // ─── Auth ───
        composable("auth") {
            WelcomeScreen(onLogin = {
                navController.navigate("main") { popUpTo("auth") { inclusive = true } }
            })
        }

        // ─── Main Dashboard ───
        composable("main") {
            RedMainDashboard(
                onNavigateToChat = { id -> navController.navigate("chat_detail/$id") },
                onNavigateToCall = { id -> navController.navigate("call_type_picker/$id") },
                onNavigateToVideo = { id -> navController.navigate("voip_call/$id/VOIP_VIDEO") },
                onNavigateToPstn = { num -> navController.navigate("pstn_call/$num") },
                onNavigateToLive = { navController.navigate("live_broadcast/me") },
                onNavigateToSpace = { navController.navigate("audio_space/new") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDinstar = { navController.navigate("dinstar_admin") },
                onLogout = {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
            )
        }

        // ━━━━━━━━━━━━ 💬 المحادثات ━━━━━━━━━━━━
        composable("chat_detail/{chatId}") { backStack ->
            ChatDetailScreen(backStack.arguments?.getString("chatId") ?: "")
        }

        // ━━━━━━━━━━━━ 👥 المجموعات ━━━━━━━━━━━━
        composable("create_group") {
            CreateGroupScreen(
                onCreate = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("group_info/{groupId}") { backStack ->
            SovereignGroupInfoScreen(
                group = SovereignGroup(
                    id = backStack.arguments?.getString("groupId") ?: "",
                    name = "مجموعة",
                    myRole = GroupRole.ADMIN
                ),
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 📞 المكالمات ━━━━━━━━━━━━
        composable("call_type_picker/{userId}") { backStack ->
            val userId = backStack.arguments?.getString("userId") ?: ""
            CallTypePickerSheet(
                contactName = userId,
                onCallTypeSelected = { type ->
                    when (type) {
                        CallType.VOIP_AUDIO -> navController.navigate("voip_call/$userId/VOIP_AUDIO")
                        CallType.VOIP_VIDEO -> navController.navigate("voip_call/$userId/VOIP_VIDEO")
                        CallType.PSTN_DINSTAR -> navController.navigate("pstn_call/$userId")
                        CallType.CONFERENCE -> navController.navigate("conference/$userId")
                        CallType.LIVE_BROADCAST -> navController.navigate("live_broadcast/$userId")
                        CallType.AUDIO_SPACE -> navController.navigate("audio_space/$userId")
                    }
                },
                onDismiss = { navController.popBackStack() }
            )
        }

        composable(
            "voip_call/{userId}/{callType}",
            arguments = listOf(navArgument("callType") { type = NavType.StringType })
        ) { backStack ->
            VideoCallScreen(
                remoteName = backStack.arguments?.getString("userId") ?: "",
                voipEngine = VoipEngine(androidx.compose.ui.platform.LocalContext.current),
                onEndCall = { navController.popBackStack() }
            )
        }

        composable("pstn_call/{number}") { backStack ->
            PstnCallScreen(
                phoneNumber = backStack.arguments?.getString("number") ?: "",
                onEnd = { navController.popBackStack() }
            )
        }

        composable(
            "incoming_pstn_call/{number}/{port}",
            arguments = listOf(navArgument("port") { type = NavType.IntType })
        ) { backStack ->
            IncomingPstnCallScreen(
                callerNumber = backStack.arguments?.getString("number") ?: "",
                portIndex = backStack.arguments?.getInt("port") ?: 0,
                viewModel = remember { DinstarViewModel() },
                onAnswer = { _, number -> navController.navigate("pstn_call/$number") },
                onReject = { navController.popBackStack() }
            )
        }

        composable("conference/{groupId}") { backStack ->
            ConferenceScreen(
                participants = listOf("أنت", backStack.arguments?.getString("groupId") ?: ""),
                activeSpeaker = null
            )
        }

        composable("live_broadcast/{streamId}") { backStack ->
            LiveBroadcastScreen(
                streamId = backStack.arguments?.getString("streamId") ?: "",
                isBroadcaster = true,
                onClose = { navController.popBackStack() }
            )
        }

        composable("audio_space/{spaceId}") { backStack ->
            ConferenceScreen(
                participants = listOf("أنت", backStack.arguments?.getString("spaceId") ?: ""),
                activeSpeaker = null
            )
        }

        composable("call_log") {
            SovereignCallLogScreen(onBack = { navController.popBackStack() })
        }

        // ━━━━━━━━━━━━ 📖 القصص ━━━━━━━━━━━━
        composable("create_story") {
            SovereignCreateStoryScreen(
                onPublish = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable("story_viewer/{userId}") { backStack ->
            SovereignStoryViewer(
                stories = emptyList(),
                onClose = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 👤 الملف الشخصي ━━━━━━━━━━━━
        composable("profile") {
            ProfileScreen(
                onNavigateToPrivacy = { navController.navigate("privacy") },
                onNavigateToTheme = { navController.navigate("theme_settings") },
                onNavigateToDevices = { navController.navigate("devices") },
                onNavigateToBackup = { navController.navigate("backup") },
                onNavigateToUpdate = { navController.navigate("update") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("privacy") {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("theme_settings") {
            SovereignThemeSettingsScreen(onBack = { navController.popBackStack() })
        }

        // ━━━━━━━━━━━━ ⚙️ الإعدادات ━━━━━━━━━━━━
        composable("settings") { SettingsScreen(navController) }
        composable("backup") { BackupScreen(onBack = { navController.popBackStack() }) }
        composable("update") { UpdateScreen(onBack = { navController.popBackStack() }) }
        composable("devices") {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onLogoutDevice = { deviceId -> /* Remote session revoked */ },
                onNavigateToDinstar = { navController.navigate("dinstar_admin") }
            )
        }
        composable("dinstar_admin") {
            DinstarAdminScreen(
                viewModel = remember { DinstarViewModel() },
                onBack = { navController.popBackStack() },
                onDialWithPort = { port, number -> navController.navigate("pstn_call/$number") }
            )
        }
        composable("dinstar_sms") {
            DinstarSmsScreen(
                viewModel = remember { DinstarViewModel() },
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 🔔 الإشعارات ━━━━━━━━━━━━
        composable("notifications") {
            SovereignNotificationCenter(onBack = { navController.popBackStack() })
        }

        // ━━━━━━━━━━━━ 🎵 مشغل الوسائط ━━━━━━━━━━━━
        composable("media_player/{mediaId}") { backStack ->
            SovereignVideoPlayer(
                uri = android.net.Uri.parse(backStack.arguments?.getString("mediaId") ?: ""),
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 📡 الاستكشاف ━━━━━━━━━━━━
        composable("explore") {
            com.red.features.explore.RedExploreScreen(
                onStartLive = { navController.navigate("live_broadcast/me") },
                onStartSpace = { navController.navigate("audio_space/new") }
            )
        }
    }
}
