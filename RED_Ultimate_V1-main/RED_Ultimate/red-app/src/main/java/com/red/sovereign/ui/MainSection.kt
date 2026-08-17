package com.red.sovereign.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainSection(val label: String, val icon: ImageVector) {
    CHATS("الدردشات", Icons.Default.ChatBubble),
    GROUPS("المجموعات", Icons.Default.Groups),
    CALLS("المكالمات", Icons.Default.Call),
    HOME("الرئيسية", Icons.Default.Home),
    MORE("المزيد", Icons.Default.MoreHoriz)
}
