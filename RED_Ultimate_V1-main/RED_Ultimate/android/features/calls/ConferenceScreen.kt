package com.red.features.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConferenceScreen(
    participants: List<String>,
    activeSpeaker: String?,
    onToggleMic: () -> Unit = {},
    onToggleCamera: () -> Unit = {},
    onEndCall: () -> Unit = {}
) {
    var isMicOn by remember { mutableStateOf(true) }
    var isCameraOn by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(participants) { name ->
                ParticipantTile(name, isActive = name == activeSpeaker)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        isMicOn = !isMicOn
                        onToggleMic()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMicOn) Color.DarkGray else Color.Red
                    )
                ) {
                    Icon(
                        if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Toggle Mic",
                        tint = Color.White
                    )
                }
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = Color.Red,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                }
                FilledIconButton(
                    onClick = {
                        isCameraOn = !isCameraOn
                        onToggleCamera()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isCameraOn) Color.DarkGray else Color.Red
                    )
                ) {
                    Icon(
                        if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Toggle Camera",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ParticipantTile(name: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.DarkGray, RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = if (isActive) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = Color.White)
        if (isActive) {
            Text("Speaking...", color = Color.Green, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
    }
}
