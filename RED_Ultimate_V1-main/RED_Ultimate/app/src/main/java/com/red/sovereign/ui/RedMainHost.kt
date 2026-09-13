package com.red.sovereign.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.red.sovereign.features.calls.ui.ConferenceScreen
import com.red.sovereign.features.calls.ui.RedCallScreen
import com.red.sovereign.features.chat.ChatListScreen
import com.red.sovereign.features.chat.GroupCreateScreen
import com.red.sovereign.features.chat.GroupDetailScreen
import com.red.sovereign.features.chat.RedChatDetailScreen
import com.red.sovereign.features.live.GoLiveScreen
import com.red.sovereign.features.live.LiveDiscoverScreen
import com.red.sovereign.features.live.LiveStreamScreen
import com.red.sovereign.features.pstn.DialPadScreen
import com.red.sovereign.features.settings.SettingsScreen
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  Bottom Navigation Items
// ════════════════════════════════════════════════════════════

sealed class RedNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Chats    : RedNavItem("chats",   "المحادثات", Icons.Filled.Chat,       Icons.Outlined.ChatBubbleOutline)
    object Calls    : RedNavItem("calls",   "المكالمات", Icons.Filled.Call,       Icons.Outlined.Phone)
    object Live     : RedNavItem("live",    "مباشر",     Icons.Filled.LiveTv,     Icons.Outlined.LiveTv)
    object Spaces   : RedNavItem("spaces",  "فضاءات",    Icons.Filled.Mic,        Icons.Outlined.Mic)
    object Settings : RedNavItem("settings","الإعدادات", Icons.Filled.Settings,   Icons.Outlined.Settings)
}

private val bottomNavItems = listOf(
    RedNavItem.Chats,
    RedNavItem.Calls,
    RedNavItem.Live,
    RedNavItem.Spaces,
    RedNavItem.Settings
)

// ════════════════════════════════════════════════════════════
//  Main Host
// ════════════════════════════════════════════════════════════

@Composable
fun RedMainHost(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Routes where bottom bar should be hidden
    val hideBottomBar = currentRoute?.startsWith("chat/") == true
        || currentRoute?.startsWith("call/") == true
        || currentRoute?.startsWith("live_stream/") == true
        || currentRoute == "go_live"
        || currentRoute?.startsWith("conference") == true

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit  = slideOutVertically(targetOffsetY  = { it }) + fadeOut()
            ) {
                RedBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate   = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = RedNavItem.Chats.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            // ── Chats ──────────────────────────────────────────
            composable(RedNavItem.Chats.route) {
                ChatListScreen(
                    onChatClick    = { chatId -> navController.navigate("chat/$chatId") },
                    onNewChatClick = { navController.navigate("new_group") }
                )
            }
            composable(
                route = "chat/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { back ->
                val chatId = back.arguments?.getString("id") ?: ""
                RedChatDetailScreen(
                    chatId       = chatId,
                    onBack       = { navController.popBackStack() },
                    onAudioCall  = { navController.navigate("call/$it?video=false") },
                    onVideoCall  = { navController.navigate("call/$it?video=true") }
                )
            }
            composable("new_group") {
                GroupCreateScreen(onNext = { _ -> navController.navigate(RedNavItem.Chats.route) })
            }
            composable(
                route = "group/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { back ->
                val gid = back.arguments?.getString("id") ?: ""
                GroupDetailScreen(groupId = gid, onBack = { navController.popBackStack() })
            }

            // ── Calls ──────────────────────────────────────────
            composable(RedNavItem.Calls.route) {
                DialPadScreen(onNavigateToCall = { number -> navController.navigate("call/$number?video=false") })
            }
            composable(
                route = "call/{target}?video={isVideo}",
                arguments = listOf(
                    navArgument("target")  { type = NavType.StringType },
                    navArgument("isVideo") { type = NavType.BoolType; defaultValue = false }
                )
            ) { back ->
                val target  = back.arguments?.getString("target")  ?: ""
                val isVideo = back.arguments?.getBoolean("isVideo") ?: false
                RedCallScreen(
                    remoteId   = target,
                    isVideo    = isVideo,
                    onEndCall  = { navController.popBackStack() }
                )
            }

            // ── Live ───────────────────────────────────────────
            composable(RedNavItem.Live.route) {
                LiveDiscoverScreen(
                    onStreamClick = { streamId -> navController.navigate("live_stream/$streamId") },
                    onGoLive      = { navController.navigate("go_live") }
                )
            }
            composable(
                route = "live_stream/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { back ->
                val id = back.arguments?.getString("id") ?: ""
                LiveStreamScreen(streamId = id, onBack = { navController.popBackStack() })
            }
            composable("go_live") {
                GoLiveScreen(onBack = { navController.popBackStack() })
            }

            // ── Spaces ─────────────────────────────────────────
            composable(RedNavItem.Spaces.route) {
                ConferenceScreen(onBack = { navController.popBackStack() })
            }

            // ── Settings ───────────────────────────────────────
            composable(RedNavItem.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  Bottom Nav Bar UI
// ════════════════════════════════════════════════════════════

@Composable
private fun RedBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, BgPrimary.copy(alpha = 0.95f), BgPrimary)
                )
            )
    ) {
        NavigationBar(
            modifier         = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            containerColor   = SurfaceDark.copy(alpha = 0.97f),
            tonalElevation   = 0.dp,
            windowInsets     = WindowInsets(0)
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route

                NavigationBarItem(
                    selected = selected,
                    onClick  = { onNavigate(item.route) },
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            // Active Indicator Glow
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(RedPrimary.copy(alpha = 0.12f))
                                )
                            }

                            // Live tab pulsing dot
                            if (item is RedNavItem.Live && !selected) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 10.dp, y = (-10).dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(RedGlow)
                                )
                            }

                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                modifier = Modifier.size(if (selected) 26.dp else 24.dp),
                                tint = if (selected) RedPrimary else TextSecondary
                            )
                        }
                    },
                    label = {
                        Text(
                            text       = item.label,
                            fontSize   = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color      = if (selected) RedPrimary else TextSecondary
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor       = RedPrimary,
                        unselectedIconColor     = TextSecondary,
                        selectedTextColor       = RedPrimary,
                        unselectedTextColor     = TextSecondary,
                        indicatorColor          = Color.Transparent
                    )
                )
            }
        }
    }
}
