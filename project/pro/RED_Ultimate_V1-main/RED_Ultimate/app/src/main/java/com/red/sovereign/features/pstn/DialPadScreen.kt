package com.red.sovereign.features.pstn

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.calls.YemeniOperatorDetector

@Composable
fun DialPadScreen(onNavigateToCall: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Operator Badge
        if (number.length >= 2) {
            Badge(containerColor = opInfo.brandColor) {
                Text(opInfo.name, color = Color.White)
            }
        }
        
        Text(number, fontSize = 48.sp, modifier = Modifier.padding(vertical = 32.dp), color = Color.White)

        // Keypad (Simplified for code brevity, same 3x4 logic)
        Spacer(Modifier.weight(1f))

        FloatingActionButton(
            onClick = { onNavigateToCall(number) },
            containerColor = Color(0xFFF57C00), // Orange for GSM
            modifier = Modifier.size(72.dp).padding(bottom = 32.dp)
        ) {
            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
