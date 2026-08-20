package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class DashboardSection(val label: String, val icon: ImageVector) {
    CHATS("الدردشات", Icons.Default.ChatBubble),
    HOME("الرئيسية", Icons.Default.Home),
    CALLS("المكالمات", Icons.Default.Call),
    GROUPS("المجموعات", Icons.Default.Groups),
    MORE("المزيد", Icons.Default.MoreHoriz)
}

/** شريط تنقل اللوحة؛ لا يحمل حالة الأعمال ويبلغ المنسق بالاختيار فقط. */
@Composable
internal fun DashboardBottomNavigation(
    selectedSection: DashboardSection,
    onSectionSelected: (DashboardSection) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    ) {
        DashboardSection.entries.forEach { item ->
            val itemLabel = item.label
            val isSelected = selectedSection == item
            val onClick = remember(item) { { onSectionSelected(item) } }
            NavigationBarItem(
                selected = isSelected,
                onClick = onClick,
                alwaysShowLabel = true,
                icon = { Icon(item.icon, itemLabel) },
                label = {
                    Text(
                        itemLabel,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .22f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}
