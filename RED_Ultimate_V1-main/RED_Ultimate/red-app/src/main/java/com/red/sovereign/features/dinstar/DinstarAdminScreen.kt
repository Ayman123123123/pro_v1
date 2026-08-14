package com.red.sovereign.features.dinstar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold

@Composable
fun DinstarAdminScreen(viewModel: DinstarViewModel, onBack: () -> Unit) {
    val fleetStatus by viewModel.fleetStatus.collectAsState()

    Column(Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("لوحة الإدارة السيادية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("أسطول بوابات DINSTAR", color = AqyalGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("نظام مراقبة البوابات الموثقة", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (fleetStatus.gateways.isEmpty()) {
                item {
                    Text("لا توجد بوابات متصلة", color = Color.Gray)
                }
            } else {
                items(fleetStatus.gateways) { gw ->
                    GatewayCard(gw)
                }
            }
        }
    }
}

@Composable
private fun GatewayCard(gw: DinstarGatewayStatus) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (gw.isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (gw.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(gw.name.ifBlank { gw.gatewayIp }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(gw.model, color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            val activePorts = gw.ports.filter { it.registrationState.equals("REGISTERED", ignoreCase = true) }
            val inCallPorts = gw.ports.filter { it.callState.equals("ACTIVE", ignoreCase = true) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("المنافذ", "${gw.ports.size}", Icons.Default.SettingsInputComponent)
                StatItem("شرائح مسجلة", "${activePorts.size}", Icons.Default.SimCard)
                StatItem("مكالمات نشطة", "${inCallPorts.size}", Icons.Default.Call)
            }
            
            if (gw.ports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(8.dp))
                Text("تفاصيل المنافذ", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                gw.ports.forEach { port ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("منفذ ${port.index}", color = Color.White, fontSize = 14.sp)
                        Text(port.operatorName, color = AqyalGold, fontSize = 14.sp)
                        Text(
                            if (port.callState.equals("ACTIVE", true)) "مشغول" else "متاح",
                            color = if (port.callState.equals("ACTIVE", true)) Color(0xFFF44336) else Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}
