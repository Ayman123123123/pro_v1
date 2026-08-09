package com.red.feature.pstn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PstnCallScreen(
    phoneNumber: String,
    viewModel: PstnViewModel = hiltViewModel(),
    onCallEnded: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)) // Dark grey for PSTN
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = Color(0xFFF57C00).copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(64.dp), tint = Color(0xFFF57C00))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(phoneNumber, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val statusText = when (callState) {
                is PstnCallState.Dialing -> "Dialing via Dumin..."
                is PstnCallState.Ringing -> "Ringing..."
                is PstnCallState.Active -> "Active - ${(callState as PstnCallState.Active).duration}s"
                is PstnCallState.Ended -> "Call Ended"
                else -> "Connecting..."
            }
            
            Text(statusText, fontSize = 18.sp, color = Color(0xFFF57C00), fontWeight = FontWeight.Medium)
        }

        // Hang up button
        FloatingActionButton(
            onClick = { 
                viewModel.hangup()
                onCallEnded()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(72.dp),
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(32.dp))
        }
    }
}
