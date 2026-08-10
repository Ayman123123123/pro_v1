package com.red.sovereign.features.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LiveBroadcastScreen(
    streamId: String,
    isBroadcaster: Boolean,
    onClose: () -> Unit,
    viewModel: LiveBroadcastViewModel = hiltViewModel()
) {
    val viewerCount by viewModel.viewerCount.collectAsState()
    val reactions by viewModel.reactions.collectAsState()

    LaunchedEffect(streamId) {
        if (isBroadcaster) viewModel.startLive(streamId) else viewModel.joinLive(streamId)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Fullscreen Video Placeholder (WebRTC Consumer)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (isBroadcaster) "You are LIVE (1080p)" else "Watching RED Stream", color = Color.White)
        }

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color.Red, shape = CircleShape) {
                Text("LIVE", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("👁️ $viewerCount", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }

        // Reactions Overlay
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            reactions.takeLast(5).forEach { reaction ->
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(24.dp))
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (!isBroadcaster) {
                Button(
                    onClick = { viewModel.sendHeart(streamId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Color.Red)
                    Text(" Reaction", color = Color.White)
                }
            }
        }
    }
}
