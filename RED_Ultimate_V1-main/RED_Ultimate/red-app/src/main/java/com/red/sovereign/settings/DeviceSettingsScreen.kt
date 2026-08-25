package com.red.sovereign.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive Device Settings Screen
 * Comprehensive settings organized by category
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    onBack: () -> Unit,
    tokenStore: TokenStore,
    snackbarHostState: SnackbarHostState,
    onPstnConfigClick: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.ObsidianDeep
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            // Account & Identity
            SettingsSectionItem(
                title = "Account & Identity",
                icon = Icons.Filled.Person,
                color = AqyalGold
            )
            SettingsItem(
                title = "Profile & Identity",
                subtitle = "Manage your Yunus ID, display name, avatar",
                icon = Icons.Filled.Person,
                onClick = { /* navigate to profile */ }
            )
            SettingsItem(
                title = "Device Identity",
                subtitle = "Device certificates, keys, and attestation",
                icon = Icons.Filled.Fingerprint,
                onClick = { /* navigate to device identity */ }
            )
            SettingsItem(
                title = "Recovery Codes",
                subtitle = "Backup and manage account recovery codes",
                icon = Icons.Filled.Key,
                onClick = { /* navigate to recovery */ }
            )
            SettingsItem(
                title = "Active Sessions",
                subtitle = "View and revoke active login sessions",
                icon = Icons.Filled.MoreVert,
                onClick = { /* navigate to sessions */ }
            )

            // PSTN / DINSTAR
            SettingsSectionItem(
                title = "PSTN / DINSTAR",
                icon = Icons.Filled.Call,
                color = YounesEmerald
            )
            SettingsItem(
                title = "PSTN Configuration",
                subtitle = "DINSTAR gateway, SIM status, port mapping",
                icon = Icons.Filled.NetworkCell,
                onClick = { 
                    // Navigate to PSTN Config Screen
                    // This requires a navigation controller - for now we'll use a callback approach
                    onPstnConfigClick?.invoke()
                }
            )
            SettingsItem(
                title = "Call Limits & Quotas",
                subtitle = "Daily/minute limits, per-number restrictions",
                icon = Icons.Filled.Call,
                onClick = { /* navigate to limits */ }
            )
            SettingsItem(
                title = "Call Recording",
                subtitle = "Auto-record, storage, retention policy",
                icon = Icons.Filled.Mic,
                onClick = { /* navigate to recording */ }
            )
            SettingsItem(
                title = "Call Forwarding & Voicemail",
                subtitle = "Conditional forwarding, voicemail settings",
                icon = Icons.Filled.CallReceived,
                onClick = { /* navigate to forwarding */ }
            )

            // Network & Connectivity
            SettingsSectionItem(
                title = "Network & Connectivity",
                icon = Icons.Filled.Wifi,
                color = AqyalGold
            )
            SettingsItem(
                title = "Server Endpoint",
                subtitle = ServerEndpoint.url(),
                icon = Icons.Filled.NetworkCell,
                isReadOnly = true
            )
            SettingsItem(
                title = "Connection Mode",
                subtitle = "Auto / WiFi only / Mobile data / VPN only",
                icon = Icons.Filled.Wifi,
                onClick = { /* show mode dialog */ }
            )
            SettingsItem(
                title = "TURN/STUN Configuration",
                subtitle = "Relay servers for WebRTC media",
                icon = Icons.Filled.SettingsInputComponent,
                onClick = { /* navigate to TURN config */ }
            )
            SettingsItem(
                title = "Offline Queue",
                subtitle = "Queue messages/calls when offline",
                icon = Icons.Filled.Sync,
                onClick = { /* navigate to offline queue */ }
            )

            // Security & Privacy
            SettingsSectionItem(
                title = "Security & Privacy",
                icon = Icons.Filled.Security,
                color = Color(0xFFF44336)
            )
            SettingsItem(
                title = "Encryption Keys",
                subtitle = "Manage device encryption keys, key rotation",
                icon = Icons.Filled.Lock,
                onClick = { /* navigate to keys */ }
            )
            SettingsItem(
                title = "Read Receipts & Typing",
                subtitle = "Control read receipts, typing indicators",
                icon = Icons.Filled.Description,
                onClick = { /* navigate to receipts */ }
            )
            SettingsItem(
                title = "Link Previews",
                subtitle = "Generate link previews in chats",
                icon = Icons.Filled.Info,
                onClick = { /* navigate to link previews */ }
            )
            SettingsItem(
                title = "Screen Security",
                subtitle = "Block screenshots, secure input, incognito keyboard",
                icon = Icons.Filled.VisibilityOff,
                onClick = { /* navigate to screen security */ }
            )
            SettingsItem(
                title = "App Lock",
                subtitle = "Biometric/PIN lock for app entry",
                icon = Icons.Filled.Fingerprint,
                onClick = { /* navigate to app lock */ }
            )
            SettingsItem(
                title = "Data Export & Deletion",
                subtitle = "Export all data or delete account",
                icon = Icons.Filled.Delete,
                isDestructive = true,
                onClick = { /* navigate to data management */ }
            )

            // Media & Storage
            SettingsSectionItem(
                title = "Media & Storage",
                icon = Icons.Filled.Storage,
                color = Color(0xFF9C27B0)
            )
            SettingsItem(
                title = "Media Auto-Download",
                subtitle = "Photos, videos, documents - WiFi / Mobile / Never",
                icon = Icons.Filled.Download,
                onClick = { /* navigate to auto-download */ }
            )
            SettingsItem(
                title = "Storage Usage",
                subtitle = "Cache, media, database - clear cache",
                icon = Icons.Filled.Storage,
                onClick = { /* navigate to storage */ }
            )
            SettingsItem(
                title = "Media Quality",
                subtitle = "Photo/video quality, compression settings",
                icon = Icons.Filled.Image,
                onClick = { /* navigate to media quality */ }
            )

            // Notifications
            SettingsSectionItem(
                title = "Notifications",
                icon = Icons.Filled.Notifications,
                color = Color(0xFF673AB7)
            )
            SettingsItem(
                title = "Message Notifications",
                subtitle = "Sound, vibration, LED, priority, categories",
                icon = Icons.Filled.Notifications,
                onClick = { /* navigate to message notifications */ }
            )
            SettingsItem(
                title = "Call Notifications",
                subtitle = "Incoming call screen, vibration, ringtone",
                icon = Icons.Filled.Call,
                onClick = { /* navigate to call notifications */ }
            )
            SettingsItem(
                title = "Group & Channel Notifications",
                subtitle = "Mentions, replies, admin alerts",
                icon = Icons.Filled.Group,
                onClick = { /* navigate to group notifications */ }
            )
            SettingsItem(
                title = "Do Not Disturb",
                subtitle = "Schedule, exceptions, priority only",
                icon = Icons.Filled.DoNotDisturb,
                onClick = { /* navigate to DND */ }
            )

            // Appearance
            SettingsSectionItem(
                title = "Appearance",
                icon = Icons.Filled.BrightnessAuto,
                color = Color(0xFF00BCD4)
            )
            SettingsItem(
                title = "Theme",
                subtitle = "System / Light / Dark / AMOLED / Custom",
                icon = Icons.Filled.BrightnessAuto,
                onClick = { /* navigate to theme */ }
            )
            SettingsItem(
                title = "Accent Color",
                subtitle = "Primary accent color for UI elements",
                icon = Icons.Filled.Palette,
                onClick = { /* navigate to accent */ }
            )
            SettingsItem(
                title = "Font Size & Style",
                subtitle = "Scaling, font family, weight",
                icon = Icons.Filled.TextFields,
                onClick = { /* navigate to font */ }
            )
            SettingsItem(
                title = "Chat Bubbles",
                subtitle = "Style, corners, tails, compression",
                icon = Icons.Filled.Chat,
                onClick = { /* navigate to chat bubbles */ }
            )
            SettingsItem(
                title = "Animations & Motion",
                subtitle = "Reduce motion, transition speed",
                icon = Icons.Filled.Animation,
                onClick = { /* navigate to animations */ }
            )

            // Call Settings
            SettingsSectionItem(
                title = "Call Settings",
                icon = Icons.Filled.Call,
                color = YounesEmerald
            )
            SettingsItem(
                title = "Ringtone & Vibration",
                subtitle = "Incoming call ringtone, vibration pattern",
                icon = Icons.Filled.Vibration,
                onClick = { /* navigate to ringtone */ }
            )
            SettingsItem(
                title = "Speaker & Audio",
                subtitle = "Auto-speaker, noise cancellation, HD voice",
                icon = Icons.Filled.VolumeUp,
                onClick = { /* navigate to audio */ }
            )
            SettingsItem(
                title = "Call Recording",
                subtitle = "Auto-record, announcement, format",
                icon = Icons.Filled.Mic,
                onClick = { /* navigate to recording */ }
            )
            SettingsItem(
                title = "Call History",
                subtitle = "Retention, export, sync with cloud",
                icon = Icons.Filled.History,
                onClick = { /* navigate to history */ }
            )
            SettingsItem(
                title = "Emergency SOS",
                subtitle = "Quick dial, location sharing, contacts",
                icon = Icons.Filled.Emergency,
                isDestructive = true,
                onClick = { /* navigate to SOS */ }
            )

            // Advanced / Developer
            SettingsSectionItem(
                title = "Advanced",
                icon = Icons.Filled.Construction,
                color = Color(0xFF795548)
            )
            SettingsItem(
                title = "Debug Logging",
                subtitle = "Enable verbose logs, logcat export",
                icon = Icons.Filled.BugReport,
                onClick = { /* toggle debug */ }
            )
            SettingsItem(
                title = "Network Diagnostics",
                subtitle = "Ping, traceroute, DNS, WebRTC stats",
                icon = Icons.Filled.NetworkCheck,
                onClick = { /* navigate to diagnostics */ }
            )
            SettingsItem(
                title = "WebRTC Debug",
                subtitle = "ICE candidates, SDP, stats, internals",
                icon = Icons.Filled.DeveloperMode,
                onClick = { /* navigate to WebRTC debug */ }
            )
            SettingsItem(
                title = "Feature Flags",
                subtitle = "Experimental features, A/B tests",
                icon = Icons.Filled.Flag,
                onClick = { /* navigate to flags */ }
            )
            SettingsItem(
                title = "Backup & Restore",
                subtitle = "Settings, keys, local data backup",
                icon = Icons.Filled.Backup,
                onClick = { /* navigate to backup */ }
            )
            SettingsItem(
                title = "About",
                subtitle = "Version, build, licenses, legal",
                icon = Icons.Filled.Info,
                onClick = { /* navigate to about */ }
            )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionItem(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isReadOnly: Boolean = false,
    isDestructive: Boolean = false,
    trailingIcon: ImageVector = Icons.Filled.ArrowForward,
    onClick: () -> Unit = {}
) {
    val titleColor = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReadOnly, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDestructive) Color(0xFFF44336).copy(alpha = 0.15f)
                    else AqyalGold.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isDestructive) Color(0xFFF44336) else AqyalGold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isReadOnly) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
