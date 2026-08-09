package com.red.sovereign.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("RED Settings") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Profile Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("profile_edit") }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(modifier = Modifier.size(64.dp).clip(CircleShape), color = Color.DarkGray) {
                        // Avatar placeholder
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("RED Admin", style = MaterialTheme.typography.titleLarge)
                        Text("Sovereign ID: @red_01", color = Color.Gray)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Edit, null, tint = Color.Red)
                }
            }

            // System C: Chat & Privacy
            item { SettingHeader("MESSAGING & PRIVACY (SYSTEM C)") }
            item { SettingItem("Privacy & Security", Icons.Default.Lock) { navController.navigate("privacy") } }
            item { SettingItem("Chat Backgrounds", Icons.Default.Wallpaper) { } }

            // System A: VoIP & Media
            item { SettingHeader("VOIP & HD MEDIA (SYSTEM A)") }
            item { SettingItem("Call Quality (4K/AV1)", Icons.Default.HighQuality) { } }
            item { SettingItem("Storage Usage", Icons.Default.CloudQueue) { navController.navigate("storage_usage") } }

            // System B: PSTN Hardware
            item { SettingHeader("PSTN GSM GATEWAY (SYSTEM B)") }
            item { 
                SettingItem("Dumin Config", Icons.Default.SettingsPhone, tint = Color(0xFFF57C00)) { 
                    navController.navigate("dumin_settings") 
                } 
            }

            // Global Actions
            item { Spacer(Modifier.height(32.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("Logout", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Logout, null, tint = Color.Red) },
                    modifier = Modifier.clickable { /* Logout */ }
                )
            }
        }
    }
}

@Composable
fun SettingHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Color.Red
    )
}

@Composable
fun SettingItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint) },
        modifier = Modifier.clickable { onClick() }
    )
}
