package com.red.sovereign.settings

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
import com.red.sovereign.core.UnifiedNetworkManager
import com.red.sovereign.core.UnifiedNetworkManager.DiscoveredServer
import com.red.sovereign.core.UnifiedNetworkManager.NetworkState
import com.red.sovereign.ui.theme.*

@Composable
fun ModernNetworkSettingsScreen(
    onBack: () -> Unit = {}
) {
    val networkStates by UnifiedNetworkManager.networkStates.collectAsState()
    val discoveredServers by UnifiedNetworkManager.discoveredServersFlow.collectAsState()
    val scanProgress by UnifiedNetworkManager.scanProgress.collectAsState()
    val isScanning by UnifiedNetworkManager.isScanning.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ModernNetworkHeader(onBack = onBack)
        }
        
        item {
            NetworkQualityOverview(networkStates = networkStates)
        }
        
        item {
            ActiveNetworksSection(networkStates = networkStates)
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "خوادم RED المكتشفة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                FilledTonalButton(
                    onClick = { UnifiedNetworkManager.scanForServers() },
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${(scanProgress * 100).toInt()}%")
                    } else {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فحص الشبكة")
                    }
                }
            }
        }
        
        if (discoveredServers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "لا توجد خوادم مكتشفة",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "تأكد من اتصال الخادم على نفس الشبكة المحلية",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(discoveredServers.values.toList()) { server ->
                ModernDiscoveredServerCard(server = server)
            }
        }
        
        item {
            NetworkCapabilitiesCard()
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModernNetworkHeader(onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(SovereignRedPrimary, SovereignRedCobalt)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "إدارة الشبكات",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "تعمل على كل الشبكات المحلية وكل الشبكات",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Hub, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun NetworkQualityOverview(networkStates: List<NetworkState>) {
    val bestNetwork = networkStates.maxByOrNull { it.quality.ordinal }
    val totalActive = networkStates.count { it.isConnected }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NetworkStatItem(
                icon = Icons.Filled.Wifi,
                label = "شبكات نشطة",
                value = "$totalActive",
                color = SovereignRedPrimary
            )
            NetworkStatItem(
                icon = Icons.Filled.SignalCellularAlt,
                label = "أفضل جودة",
                value = bestNetwork?.quality?.name ?: "NONE",
                color = SovereignRedEmerald
            )
            NetworkStatItem(
                icon = Icons.Filled.Speed,
                label = "P2P LAN",
                value = if (networkStates.any { it.type.name == "WIFI" && it.isConnected }) "متاح" else "غير متاح",
                color = SovereignRedGold
            )
        }
    }
}

@Composable
private fun NetworkStatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActiveNetworksSection(networkStates: List<NetworkState>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الشبكات النشطة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            if (networkStates.isEmpty()) {
                Text(
                    "لا توجد شبكات نشطة - تحقق من الاتصال",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                networkStates.forEach { state ->
                    ModernNetworkStateRow(state = state)
                }
            }
        }
    }
}

@Composable
private fun ModernNetworkStateRow(state: NetworkState) {
    val typeIcon = when (state.type) {
        UnifiedNetworkManager.NetworkType.WIFI -> Icons.Filled.Wifi
        UnifiedNetworkManager.NetworkType.ETHERNET -> Icons.Filled.Lan
        UnifiedNetworkManager.NetworkType.MOBILE -> Icons.Filled.SignalCellularAlt
        UnifiedNetworkManager.NetworkType.VPN -> Icons.Filled.VpnLock
        UnifiedNetworkManager.NetworkType.BLUETOOTH -> Icons.Filled.Bluetooth
        else -> Icons.Filled.Hub
    }
    
    val qualityColor = when (state.quality) {
        UnifiedNetworkManager.NetworkQuality.EXCELLENT -> SovereignRedEmerald
        UnifiedNetworkManager.NetworkQuality.GOOD -> Color(0xFF4CAF50)
        UnifiedNetworkManager.NetworkQuality.FAIR -> SovereignRedGold
        UnifiedNetworkManager.NetworkQuality.POOR -> Color(0xFFFF9800)
        UnifiedNetworkManager.NetworkQuality.NONE -> MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(qualityColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(typeIcon, null, tint = qualityColor, modifier = Modifier.size(20.dp))
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.type.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (state.isConnected) SovereignRedEmerald else MaterialTheme.colorScheme.error, CircleShape)
                )
            }
            Text(
                "${state.ipAddress ?: "No IP"} • ${state.quality} • ${state.bandwidth}kbps",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Box(
            modifier = Modifier
                .background(qualityColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(state.quality.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = qualityColor)
        }
    }
}

@Composable
private fun ModernDiscoveredServerCard(server: DiscoveredServer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    .background(
                        Brush.linearGradient(listOf(SovereignRedPrimary, SovereignRedCobalt)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Dns, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.host, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    if (server.isYounesVerified) {
                        Icon(Icons.Filled.Verified, null, tint = SovereignRedEmerald, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "${server.url} • ${server.discoveryMethod} • ${server.latencyMs}ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (server.isYounesVerified) SovereignRedEmerald.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (server.isYounesVerified) "✓ YOUNES" else "غير موثق",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (server.isYounesVerified) SovereignRedEmerald else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${server.ipAddress}:${server.port}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(onClick = { /* connect */ }) {
                Icon(Icons.Filled.Link, null, tint = SovereignRedPrimary)
            }
        }
    }
}

@Composable
private fun NetworkCapabilitiesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SovereignRedPrimary.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = SovereignRedGold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("مميزات RED المتفوقة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            val features = listOf(
                "✓ يعمل على كل الشبكات المحلية: WiFi, Ethernet, USB, VPN, Hotspot" to Icons.Filled.Wifi,
                "✓ مكالمات P2P مباشرة بلا إنترنت على نفس الشبكة" to Icons.Filled.Call,
                "✓ اكتشاف تلقائي عبر mDNS و IP scanning" to Icons.Filled.Search,
                "✓ تبديل تلقائي عند فشل الشبكة" to Icons.Filled.SwapHoriz,
                "✓ أفضل من واتساب وتيليجرام - ألوان سيادية وأمان" to Icons.Filled.Security
            )
            
            features.forEach { (text, icon) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, tint = SovereignRedPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
