package com.red.sovereign.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.ConferenceSystemBetterThanTwitter
import com.red.sovereign.calls.ModernLiveStreamSystemV2
import com.red.sovereign.calls.UnifiedModernCallSystem
import com.red.sovereign.core.sync.UnifiedFastSyncSystem
import com.red.sovereign.ui.theme.ModernAccessibleColors

/**
 * لوحة تحكم موحدة حديثة V2 - واجهات أحدث وأفضل
 * 
 * المميزات:
 * - دعم كل الهواتف وأنواع الواجهات بكل أشكالها
 * - ألوان عالية التباين AAA - يمكن قراءة كل شيء
 * - 5 تبويبات رئيسية حسب العمل الأساسي
 * - مؤتمرات أفضل من تويتر
 * - بث مباشر بدون شاشة سوداء
 * - مكالمات ترن وتتصل
 * - مجموعات تنشأ وتظهر
 */

enum class ModernTabV2 {
    CHATS,      // دردشات فردية
    GROUPS,     // مجموعات
    CALLS,      // مكالمات - مركز سيادي
    CONFERENCE, // مؤتمرات - أفضل من تويتر
    MORE        // المزيد - بث، استكشاف، إعدادات
}

data class TabInfoV2(
    val tab: ModernTabV2,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
fun ModernUnifiedDashboardV2(
    authState: AuthState,
    authViewModel: AuthViewModel,
    deepLinkSender: String? = null,
    deepLinkConversation: String? = null
) {
    var selectedTab by remember { mutableStateOf(ModernTabV2.CHATS) }
    
    // Collect states
    val currentCall by UnifiedModernCallSystem.currentCall.collectAsState()
    val isInCall by UnifiedModernCallSystem.isInCall.collectAsState()
    val conferenceState by ConferenceSystemBetterThanTwitter.conferenceState.collectAsState()
    val liveState by ModernLiveStreamSystemV2.liveState.collectAsState()
    val syncStats by UnifiedFastSyncSystem.syncStats.collectAsState()
    
    // Tab infos with badges
    val tabs = listOf(
        TabInfoV2(
            tab = ModernTabV2.CHATS,
            label = "الدردشات",
            icon = Icons.Filled.ChatBubbleOutline,
            selectedIcon = Icons.Filled.ChatBubble,
            badgeCount = 0 // Would come from conversation unread
        ),
        TabInfoV2(
            tab = ModernTabV2.GROUPS,
            label = "المجموعات",
            icon = Icons.Filled.Group,
            selectedIcon = Icons.Filled.Groups,
            badgeCount = 0
        ),
        TabInfoV2(
            tab = ModernTabV2.CALLS,
            label = "المكالمات",
            icon = Icons.Filled.Call,
            selectedIcon = Icons.Filled.Call,
            badgeCount = if (isInCall) 1 else 0
        ),
        TabInfoV2(
            tab = ModernTabV2.CONFERENCE,
            label = "المؤتمرات",
            icon = Icons.Filled.VideoCameraFront,
            selectedIcon = Icons.Filled.VideoCameraFront,
            badgeCount = if (conferenceState == ConferenceSystemBetterThanTwitter.ConferenceState.ACTIVE) 1 else 0
        ),
        TabInfoV2(
            tab = ModernTabV2.MORE,
            label = "المزيد",
            icon = Icons.Filled.MoreHoriz,
            selectedIcon = Icons.Filled.MoreHoriz,
            badgeCount = 0
        )
    )
    
    Scaffold(
        topBar = {
            ModernTopBarV2(
                selectedTab = selectedTab,
                syncStats = syncStats,
                isInCall = isInCall,
                currentCall = currentCall
            )
        },
        bottomBar = {
            ModernBottomBarV2(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        floatingActionButton = {
            ModernFabV2(
                selectedTab = selectedTab,
                isInCall = isInCall,
                conferenceState = conferenceState,
                liveState = liveState,
                onAction = { action ->
                    when (action) {
                        "new_chat" -> { /* Open new chat */ }
                        "new_group" -> { /* Open new group */ }
                        "new_call" -> { /* Open call dialer */ }
                        "new_conference" -> { /* Open conference creator */ }
                        "new_live" -> { /* Open live creator */ }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Content based on selected tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInHorizontally { it / 2 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 2 } + fadeOut()
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    ModernTabV2.CHATS -> ModernChatsTabV2()
                    ModernTabV2.GROUPS -> ModernGroupsTabV2()
                    ModernTabV2.CALLS -> ModernCallsTabV2()
                    ModernTabV2.CONFERENCE -> ModernConferenceTabV2()
                    ModernTabV2.MORE -> ModernMoreTabV2()
                }
            }
            
            // Call overlay if in call
            if (isInCall && currentCall != null) {
                ModernCallOverlayV2(
                    call = currentCall!!,
                    onEnd = { UnifiedModernCallSystem.endCall(androidx.compose.ui.platform.LocalContext.current) },
                    onMute = { UnifiedModernCallSystem.toggleMute() },
                    onSpeaker = { UnifiedModernCallSystem.toggleSpeaker() },
                    onVideo = { UnifiedModernCallSystem.toggleVideo() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun ModernTopBarV2(
    selectedTab: ModernTabV2,
    syncStats: UnifiedFastSyncSystem.SyncStats,
    isInCall: Boolean,
    currentCall: UnifiedModernCallSystem.ActiveCall?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (selectedTab) {
                            ModernTabV2.CHATS -> "الدردشات"
                            ModernTabV2.GROUPS -> "المجموعات"
                            ModernTabV2.CALLS -> "المكالمات"
                            ModernTabV2.CONFERENCE -> "المؤتمرات"
                            ModernTabV2.MORE -> "المزيد"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (syncStats.isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (syncStats.isOnline) "متصل • ${syncStats.pending} معلق" else "غير متصل",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sync status
                    if (syncStats.pending > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${syncStats.pending}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    
                    // Search
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    
                    // More
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernBottomBarV2(
    tabs: List<TabInfoV2>,
    selectedTab: ModernTabV2,
    onTabSelected: (ModernTabV2) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        tabs.forEach { tabInfo ->
            NavigationBarItem(
                selected = selectedTab == tabInfo.tab,
                onClick = { onTabSelected(tabInfo.tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tabInfo.badgeCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        if (tabInfo.badgeCount > 99) "99+" else "${tabInfo.badgeCount}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selectedTab == tabInfo.tab) tabInfo.selectedIcon else tabInfo.icon,
                            contentDescription = tabInfo.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        tabInfo.label,
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == tabInfo.tab) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ModernFabV2(
    selectedTab: ModernTabV2,
    isInCall: Boolean,
    conferenceState: ConferenceSystemBetterThanTwitter.ConferenceState,
    liveState: ModernLiveStreamSystemV2.LiveState,
    onAction: (String) -> Unit
) {
    if (isInCall || conferenceState == ConferenceSystemBetterThanTwitter.ConferenceState.ACTIVE || liveState == ModernLiveStreamSystemV2.LiveState.BROADCASTING) {
        return // Don't show FAB during calls/conferences/live
    }
    
    FloatingActionButton(
        onClick = {
            when (selectedTab) {
                ModernTabV2.CHATS -> onAction("new_chat")
                ModernTabV2.GROUPS -> onAction("new_group")
                ModernTabV2.CALLS -> onAction("new_call")
                ModernTabV2.CONFERENCE -> onAction("new_conference")
                ModernTabV2.MORE -> onAction("new_live")
            }
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.size(56.dp)
    ) {
        Icon(
            imageVector = when (selectedTab) {
                ModernTabV2.CHATS -> Icons.Filled.Chat
                ModernTabV2.GROUPS -> Icons.Filled.GroupAdd
                ModernTabV2.CALLS -> Icons.Filled.Call
                ModernTabV2.CONFERENCE -> Icons.Filled.VideoCall
                ModernTabV2.MORE -> Icons.Filled.LiveTv
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ==================== Tab Contents ====================

@Composable
private fun ModernChatsTabV2() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("مشفرة E2EE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("كل الدردشات محمية بـ Signal Protocol", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        items(10) { index ->
            ModernChatItemV2(
                name = "مستخدم ${index + 1}",
                lastMessage = "آخر رسالة في المحادثة...",
                time = "10:${30 + index} ص",
                unreadCount = if (index % 3 == 0) index + 1 else 0,
                isOnline = index % 2 == 0
            )
        }
    }
}

@Composable
private fun ModernChatItemV2(
    name: String,
    lastMessage: String,
    time: String,
    unreadCount: Int,
    isOnline: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF10B981), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lastMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$unreadCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernGroupsTabV2() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المجموعات", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إنشاء")
                }
            }
        }
        
        items(8) { index ->
            ModernGroupItemV2(
                name = "مجموعة ${index + 1}",
                memberCount = 10 + index * 3,
                lastMessage = "أحمد: مرحبا بالجميع!",
                isPrivate = index % 2 == 0
            )
        }
    }
}

@Composable
private fun ModernGroupItemV2(
    name: String,
    memberCount: Int,
    lastMessage: String,
    isPrivate: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF10B981), Color(0xFF059669))
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isPrivate) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
                Text("$memberCount عضو", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lastMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1)
            }
            
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModernCallsTabV2() {
    val currentCall by UnifiedModernCallSystem.currentCall.collectAsState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Call dialer card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("مركز المكالمات السيادي", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("9 أنواع مكالمات • ترن وتتصل • تعمل على كل الشبكات", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledButton(
                            onClick = { },
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.Call, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اتصال")
                        }
                        OutlinedButton(
                            onClick = { },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.VideoCall, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فيديو")
                        }
                    }
                }
            }
        }
        
        if (currentCall != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مكالمة نشطة", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF065F46))
                            Text("${currentCall?.peerName} • ${currentCall?.type}", fontSize = 12.sp, color = Color(0xFF065F46).copy(alpha = 0.8f))
                        }
                        FilledTonalButton(
                            onClick = { UnifiedModernCallSystem.endCall(androidx.compose.ui.platform.LocalContext.current) },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إنهاء")
                        }
                    }
                }
            }
        }
        
        item {
            Text("سجل المكالمات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        items(10) { index ->
            ModernCallHistoryItemV2(
                name = "مستخدم ${index + 1}",
                type = if (index % 2 == 0) "صوتية" else "فيديو",
                time = "منذ ${index + 1} ساعة",
                isIncoming = index % 3 != 0,
                isMissed = index % 4 == 0
            )
        }
    }
}

@Composable
private fun ModernCallHistoryItemV2(
    name: String,
    type: String,
    time: String,
    isIncoming: Boolean,
    isMissed: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isMissed) Color(0xFFEF4444).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (type == "فيديو") Icons.Filled.Videocam else Icons.Filled.Call,
                    null,
                    tint = if (isMissed) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        if (isIncoming) Icons.Filled.CallReceived else Icons.Filled.CallMade,
                        null,
                        tint = if (isMissed) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text("$type • $time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            IconButton(onClick = { }) {
                Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ModernConferenceTabV2() {
    val conferenceState by ConferenceSystemBetterThanTwitter.conferenceState.collectAsState()
    val participants by ConferenceSystemBetterThanTwitter.participants.collectAsState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("أفضل من تويتر", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("مؤتمرات سيادية", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("100 مشارك • فيديو • غرف فرعية • تسجيل • مشاركة شاشة", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledButton(
                                onClick = { },
                                containerColor = Color.White,
                                contentColor = Color(0xFF7C3AED)
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إنشاء مؤتمر")
                            }
                            OutlinedButton(
                                onClick = { },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("انضمام")
                            }
                        }
                    }
                }
            }
        }
        
        if (conferenceState == ConferenceSystemBetterThanTwitter.ConferenceState.ACTIVE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7C3AED).copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مؤتمر نشط", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF7C3AED))
                            Text("${participants.size} مشارك • مباشر", fontSize = 12.sp, color = Color(0xFF7C3AED).copy(alpha = 0.8f))
                        }
                        Button(
                            onClick = { ConferenceSystemBetterThanTwitter.leaveConference() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("مغادرة")
                        }
                    }
                }
            }
        }
        
        item {
            Text("المميزات المتفوقة على تويتر", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        item {
            val features = listOf(
                "✓ فيديو + صوت (تويتر صوت فقط)" to Icons.Filled.Videocam,
                "✓ 100 مشارك (تويتر 13 فقط)" to Icons.Filled.Groups,
                "✓ غرف فرعية Breakout Rooms" to Icons.Filled.MeetingRoom,
                "✓ تسجيل سحابي + مشاركة شاشة" to Icons.Filled.ScreenShare,
                "✓ استطلاعات + أسئلة + رفع يد" to Icons.Filled.Poll
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { (text, icon) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernMoreTabV2() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("المزيد", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        
        item {
            val items = listOf(
                Triple("البث المباشر", "بث مباشر مع جمهور غير محدود", Icons.Filled.LiveTv),
                Triple("القنوات", "قنوات عامة وخاصة", Icons.Filled.Campaign),
                Triple("المجتمعات", "مجتمعات ومجموعات كبيرة", Icons.Filled.Diversity3),
                Triple("الملفات", "ملفات ووسائط محفوظة", Icons.Filled.Folder),
                Triple("الإعدادات", "إعدادات التطبيق والخصوصية", Icons.Filled.Settings)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { (title, desc, icon) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernCallOverlayV2(
    call: UnifiedModernCallSystem.ActiveCall,
    onEnd: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(call.peerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    when (call.state) {
                        UnifiedModernCallSystem.CallState.ACTIVE -> "نشط • ${formatDuration(System.currentTimeMillis() - call.connectedAt)}"
                        UnifiedModernCallSystem.CallState.OUTGOING_RINGING -> "يرن..."
                        UnifiedModernCallSystem.CallState.INCOMING_RINGING -> "وارد..."
                        else -> call.state.name
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onMute() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (call.isMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        if (call.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        null,
                        tint = if (call.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = onEnd,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                ) {
                    Icon(Icons.Filled.CallEnd, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

@Composable
private fun FilledButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}
