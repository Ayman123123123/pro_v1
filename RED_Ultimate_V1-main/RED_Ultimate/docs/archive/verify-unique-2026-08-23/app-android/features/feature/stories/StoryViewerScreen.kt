package com.red.feature.stories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun StoryViewerScreen(stories: List<String>, onAllStoriesViewed: () -> Unit) {
    var currentIndex by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(currentIndex) {
        progress = 0f
        val duration = 5000L // 5 seconds per story
        val step = 0.01f
        val delayTime = duration / 100
        
        while (progress < 1f) {
            delay(delayTime)
            progress += step
        }
        
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            onAllStoriesViewed()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Story Content
        AsyncImage(
            model = stories[currentIndex],
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Progress Bars
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                val barProgress = when {
                    index < currentIndex -> 1f
                    index == currentIndex -> progress
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { barProgress },
                    modifier = Modifier.weight(1f).height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Close Button
        TextButton(
            onClick = onAllStoriesViewed,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp)
        ) {
            Text("Close", color = Color.White)
        }
    }
}
