package com.red.sovereign.features.pstn

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalCellularAlt
import 'androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar'
import 'androidx.compose.material.icons.rounded.PhoneInTalk'
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.features.dinstar.DinstarGatewayStatus
import com.red.features.dinstar.DinstarPort

@Composable
fun DinstarDashboardUI(
    status: DinstarGatewayStatus?,
    selectedSlot: Int,
    onSlotSelected: (Int) -> Unit
) {
    if (status == null) {
        // Loading state or unavailable
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "بوابة Dinstar (مباشر)",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (status.isOnline) "متصل" else "غير متصل",
                color = if (status.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontSize = 12.sp
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(140.dp) // fits 2 rows of 4
        ) {
            items(status.ports) { port ->
                SimPortCard(
                    port = port,
                    isSelected = selectedSlot == port.index,
                    onClick = {
                        if (port.isAvailable || port.callState == "IDLE") {
                            onSlotSelected(port.index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SimPortCard(
    port: DinstarPort,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val brandColor = Color(port.simType.colorHex)
    val isRegistered = port.registrationState == "REGISTERED"
    val isBusy = port.callState != "IDLE"
    
    val bgColor = if (isSelected) brandColor.copy(alpha = 0.2f) else Color(0xFF1E1E1E)
    val borderColor = if (isSelected) brandColor else Color.Transparent

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = true, onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top: Port Number & Status Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "P${port.index}",
                    color = if (isRegistered) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                when {
                    isBusy -> {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = "Busy",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    isRegistered -> {
                        Icon(
                            imageVector = Icons.Rounded.SignalCellularAlt,
                            contentDescription = "Signal",
                            tint = if (port.signalPercent > 30) Color(0xFF4CAF50) else Color(0xFFFFC107),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.SignalCellularConnectedNoInternet0Bar,
                            contentDescription = "No Signal",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            
            // Middle: Operator Name
            Text(
                text = if (isRegistered) port.operatorName.take(5) else "---",
                color = if (isRegistered) brandColor else Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
            
            // Bottom: Signal Percent
            Text(
                text = if (isRegistered) "${port.signalPercent}%" else "Off",
                color = if (isRegistered) Color.White.copy(alpha = 0.7f) else Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}
