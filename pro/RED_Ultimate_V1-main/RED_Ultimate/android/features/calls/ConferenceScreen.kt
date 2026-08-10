package com.red.features.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConferenceScreen(participants: List<String>, activeSpeaker: String?) {
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
        
        // Control Bar
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(32.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                // Icons for Mic, Video, End
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
