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
import com.red.sovereign.features.media.*
import com.red.sovereign.features.privacy.*
import com.red.core.theme.*

/**
 * 🧭 YOUNES Sovereign Navigation — كل المسارات مفعلة
 */
@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        // ─── Splash → Auth → Main ───
        composable("splash") {
            RedSplashScreen(onFinished = { navController.navigate("auth") { popUpTo("splash") { inclusive = true } } })
        }

        // ─── Authentication ───
        composable("auth") {
            WelcomeScreen(onLogin = { navController.navigate("main") { popUpTo("auth") { inclusive = true } } })
        }

        // ─── Main Dashboard ───
        composable("main") {
            RedDashboard(
                onNavigateToChat = { id -> navController.navigate("chat_detail/$id") },
                onNavigateToCall = { id -> navController.navigate("call_type_picker/$id") },
                onNavigateToPhone = { num -> navController.navigate("pstn_call/$num") },
                onNavigateToSettings = { navController.navigate("settings") }
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

        // ━━━━━━━━━━━━ 📞 المكالمات — جميع الأنواع ━━━━━━━━━━━━
        composable("call_type_picker/{userId}") { backStack ->
            // Bottom sheet لاختيار نوع المكالمة
            val userId = backStack.arguments?.getString("userId") ?: ""
            CallTypePickerSheet(
                contactName = userId,
                onCallTypeSelected = { type ->
                    when (type) {
                        CallType.VOIP_AUDIO, CallType.VOIP_VIDEO ->
                            navController.navigate("voip_call/$userId/${type.name}")
                        CallType.PSTN_DINSTAR ->
                            navController.navigate("pstn_call/$userId")
                        CallType.CONFERENCE ->
                            navController.navigate("conference/$userId")
                        CallType.LIVE_BROADCAST ->
                            navController.navigate("live_broadcast/$userId")
                        CallType.AUDIO_SPACE ->
                            navController.navigate("audio_space/$userId")
                    }
                },
                onDismiss = { navController.popBackStack() }
            )
        }

        composable("voip_call/{userId}/{callType}") { backStack ->
            val callTypeStr = backStack.arguments?.getString("callType") ?: "VOIP_AUDIO"
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
            // Audio Space — غرفة صوتية
            ConferenceScreen(
                participants = listOf("أنت", backStack.arguments?.getString("spaceId") ?: ""),
                activeSpeaker = null
            )
        }

        // ─── سجل المكالمات الموحد ───
        composable("call_log") {
            SovereignCallLogScreen(
                onBack = { navController.popBackStack() }
            )
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
                stories = emptyList(), // TODO: load from ViewModel
                onClose = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 👤 الملف الشخصي ━━━━━━━━━━━━
        composable("profile") {
            ProfileScreen(
                onNavigateToPrivacy = { navController.navigate("privacy") },
                onNavigateToTheme = { navController.navigate("theme_settings") },
                onNavigateToDevices = { navController.navigate("devices") },
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 🔒 الخصوصية ━━━━━━━━━━━━
        composable("privacy") {
            PrivacySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 🎨 المظهر ━━━━━━━━━━━━
        composable("theme_settings") {
            SovereignThemeSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ ⚙️ الإعدادات ━━━━━━━━━━━━
        composable("settings") { SettingsScreen(navController) }
        composable("backup") { BackupScreen() }
        composable("update") { UpdateScreen() }
        composable("devices") { /* TODO: DevicesScreen */ }

        // ━━━━━━━━━━━━ 🔔 الإشعارات ━━━━━━━━━━━━
        composable("notifications") {
            SovereignNotificationCenter(
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 🎵 مشغل الوسائط ━━━━━━━━━━━━
        composable("media_player/{mediaId}") { backStack ->
            // مشغل الوسائط المتقدم
            SovereignVideoPlayer(
                uri = android.net.Uri.parse(backStack.arguments?.getString("mediaId") ?: ""),
                onBack = { navController.popBackStack() }
            )
        }

        // ━━━━━━━━━━━━ 📡 البث والاستكشاف ━━━━━━━━━━━━
        composable("explore") {
            com.red.features.explore.RedExploreScreen(
                onStartLive = { navController.navigate("live_broadcast/me") },
                onStartSpace = { navController.navigate("audio_space/new") }
            )
        }
    }
}
