package com.red.sovereign.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Scan QR */ }) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "QR", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Profile Bento Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141414))
                        .clickable { /* Edit Profile */ }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB71C1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AY", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ayman", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("+967 77X XXX XXX", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Online | RED Sovereign", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                        
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                // General Settings Card
                SettingsCard {
                    SettingsRow(Icons.Outlined.Lock, "Privacy & Security", "Passcode, Two-step verification")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Outlined.ChatBubbleOutline, "Chats", "Theme, Wallpapers, Chat History")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Outlined.Notifications, "Notifications", "Message, Group & Call tones")
                }
            }

            item {
                // Data & Storage Card
                SettingsCard {
                    SettingsRow(Icons.Outlined.Storage, "Data and Storage", "Network usage, Auto-download")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Default.Language, "App Language", "English")
                }
            }

            item {
                // Theme Picker Card
                val currentTheme = com.red.sovereign.ui.theme.AppThemeState.currentPreset
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141414))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme", tint = Color(0xFFB71C1C), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("App Theme", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val themes = listOf(
                        com.red.sovereign.ui.theme.AppThemePreset.WHATSAPP_DARK   to Triple("واتساب", Color(0xFF00A884), Color(0xFF0B141A)),
                        com.red.sovereign.ui.theme.AppThemePreset.TELEGRAM_DARK   to Triple("تلجرام", Color(0xFF2AABEE), Color(0xFF0E1621)),
                        com.red.sovereign.ui.theme.AppThemePreset.SOVEREIGN       to Triple("سيادي",  Color(0xFF00C896), Color(0xFF0A1628)),
                        com.red.sovereign.ui.theme.AppThemePreset.OLED_BLACK      to Triple("أوليد",  Color(0xFF00E676), Color(0xFF000000))
                    )
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        themes.forEach { (preset, info) ->
                            val (label, accent, bg) = info
                            val isSelected = currentTheme == preset
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bg)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) accent else Color(0xFF333333),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { com.red.sovereign.ui.theme.AppThemeState.currentPreset = preset }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                // Server Connection Card
                var showServerDialog by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showServerDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = "Server Connection", tint = Color(0xFFB71C1C), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Server Connection", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Current: ${com.red.sovereign.core.ServerEndpoint.url()}", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
                
                if (showServerDialog) {
                    var serverIp by remember { mutableStateOf(com.red.sovereign.core.ServerEndpoint.url()) }
                    AlertDialog(
                        onDismissRequest = { showServerDialog = false },
                        containerColor = Color(0xFF141414),
                        title = { Text("Server Connection IP", color = Color.White) },
                        text = {
                            OutlinedTextField(
                                value = serverIp,
                                onValueChange = { serverIp = it },
                                label = { Text("Enter Server IP (e.g. http://192.168.1.10:8088)", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFB71C1C)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (serverIp.isNotBlank()) {
                                    runCatching {
                                        com.red.sovereign.core.ServerEndpoint.update(context, serverIp)
                                        showServerDialog = false
                                    }
                                }
                            }) { Text("Save", color = Color(0xFFB71C1C)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerDialog = false }) { Text("Cancel", color = Color.Gray) }
                        }
                    )
                }
            }

            item {
                // Help Card
                SettingsCard {
                    SettingsRow(Icons.Outlined.HelpOutline, "Help", "FAQ, Contact us, Privacy Policy")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Default.Info, "About RED", "Version 1.0 (Ultimate)")
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "from\nRED TEAM",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF141414))
    ) {
        content()
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle Click */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFFB71C1C), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
