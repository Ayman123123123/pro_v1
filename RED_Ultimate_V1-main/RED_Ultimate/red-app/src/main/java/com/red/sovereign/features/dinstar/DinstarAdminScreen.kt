package com.red.sovereign.features.dinstar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.red.sovereign.ui.theme.CairoFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinstarAdminScreen(viewModel: DinstarViewModel, onBack: () -> Unit) {
    val fleetStatus by viewModel.fleetStatus.collectAsState()
    val commandResult by viewModel.commandResult.collectAsState()

    var selectedPortForUssd by remember { mutableStateOf<DinstarPort?>(null) }
    var ussdInputCode by remember { mutableStateOf("*555#") }

    Column(
        Modifier
            .fillMaxSize()
            .background(SovereignColors.ObsidianDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SovereignColors.SurfaceCard)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "إدارة بوابات DINSTAR",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = CairoFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "مراقبة وتحكم في أسطول الـ GSM وPSTN",
                        color = SovereignColors.GoldNeon,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = { viewModel.refreshStatus() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SovereignColors.SurfaceCard)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = SovereignColors.GoldNeon)
            }
        }

        // Command Result Banner
        commandResult?.let { res ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (res) {
                            is DinstarCommandResult.Loading -> SovereignColors.SurfaceCard
                            is DinstarCommandResult.Success -> SovereignColors.EmeraldNeon.copy(alpha = 0.18f)
                            is DinstarCommandResult.Error -> SovereignColors.RubyNeon.copy(alpha = 0.18f)
                        }
                    )
                    .border(
                        1.dp,
                        when (res) {
                            is DinstarCommandResult.Loading -> SovereignColors.GlassBorder
                            is DinstarCommandResult.Success -> SovereignColors.EmeraldNeon.copy(alpha = 0.4f)
                            is DinstarCommandResult.Error -> SovereignColors.RubyNeon.copy(alpha = 0.4f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (res) {
                            is DinstarCommandResult.Loading -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SovereignColors.GoldNeon,
                                strokeWidth = 2.dp
                            )
                            is DinstarCommandResult.Success -> Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = SovereignColors.EmeraldNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            is DinstarCommandResult.Error -> Icon(
                                Icons.Default.Error,
                                null,
                                tint = SovereignColors.RubyNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = when (res) {
                                is DinstarCommandResult.Loading -> "جارٍ تنفيذ الأمر على البوابة..."
                                is DinstarCommandResult.Success -> res.message
                                is DinstarCommandResult.Error -> res.message
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearCommandResult() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (fleetStatus.gateways.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Router, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Text("لا توجد بوابات متصلة حالياً بالخادم", color = Color.Gray, fontSize = 14.sp)
                            Text("تأكد من تشغيل البوابة وشبكة الإدارة", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(fleetStatus.gateways) { gw ->
                    GatewayCard(
                        gw = gw,
                        onResetPort = { portIdx -> viewModel.resetPort(portIdx) },
                        onOpenUssd = { port -> selectedPortForUssd = port }
                    )
                }
            }
        }
    }

    // USSD Dialog
    selectedPortForUssd?.let { port ->
        Dialog(onDismissRequest = { selectedPortForUssd = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SovereignColors.ObsidianDeep,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SovereignColors.GoldNeon.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "استعلام USSD (منفذ ${port.index} - ${port.operatorName})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { selectedPortForUssd = null }) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                        }
                    }

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { ussdInputCode = "*555#" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.GoldNeon)
                        ) {
                            Text("*555# رصيد", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { ussdInputCode = "*123#" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.GoldNeon)
                        ) {
                            Text("*123# باقات", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = ussdInputCode,
                        onValueChange = { ussdInputCode = it },
                        label = { Text("رمز USSD", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SovereignColors.GoldNeon,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.sendUssd(port.index, ussdInputCode.trim())
                            selectedPortForUssd = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Gold)
                    ) {
                        Text("إرسال الأمر للبوابة", color = SovereignColors.ObsidianDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayCard(
    gw: DinstarGatewayStatus,
    onResetPort: (Int) -> Unit,
    onOpenUssd: (DinstarPort) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SovereignColors.SurfaceCard)
            .border(1.dp, SovereignColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (gw.isOnline) SovereignColors.EmeraldNeon else SovereignColors.RubyNeon)
                    )
                    Text(
                        text = gw.name.ifBlank { gw.gatewayIp },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = gw.model.ifBlank { "DINSTAR UC2000" },
                    color = SovereignColors.GoldNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Stats row
            val activePorts = gw.ports.filter { it.registrationState.equals("REGISTERED", ignoreCase = true) }
            val inCallPorts = gw.ports.filter { it.callState.equals("ACTIVE", ignoreCase = true) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SovereignColors.ObsidianDeep.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("المنافذ", "${gw.ports.size}", Icons.Default.SettingsInputComponent)
                StatItem("المسجلة", "${activePorts.size}", Icons.Default.SimCard)
                StatItem("المكالمات", "${inCallPorts.size}", Icons.Default.Call)
            }

            // Ports List
            if (gw.ports.isNotEmpty()) {
                Text(
                    text = "المنافذ والشرائح",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                gw.ports.forEach { port ->
                    PortRowItem(
                        port = port,
                        onReset = { onResetPort(port.index) },
                        onUssd = { onOpenUssd(port) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortRowItem(
    port: DinstarPort,
    onReset: () -> Unit,
    onUssd: () -> Unit
) {
    val isRegistered = port.registrationState.equals("REGISTERED", true)
    val isInCall = port.callState.equals("ACTIVE", true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SovereignColors.ObsidianDeep.copy(alpha = 0.4f))
            .border(0.8.dp, SovereignColors.GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Port index & operator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRegistered) SovereignColors.EmeraldNeon.copy(alpha = 0.2f) else Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${port.index}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Column {
                    Text(
                        text = port.operatorName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isInCall) "🔴 في مكالمة" else if (isRegistered) "🟢 متاح" else "⚪ غير مسجل",
                        color = if (isInCall) SovereignColors.RubyNeon else if (isRegistered) SovereignColors.EmeraldNeon else Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            // Signal + Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                port.signalPercent?.let { sig ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.SignalCellularAlt, null, tint = SovereignColors.GoldNeon, modifier = Modifier.size(14.dp))
                        Text("$sig%", color = Color.White, fontSize = 10.sp)
                    }
                }

                IconButton(
                    onClick = onUssd,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Dialpad, "USSD", tint = SovereignColors.GoldNeon, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, "إعادة تشغيل المنفذ", tint = SovereignColors.RubyNeon, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = SovereignColors.GoldNeon, modifier = Modifier.size(16.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Color.Gray, fontSize = 10.sp)
    }
}
