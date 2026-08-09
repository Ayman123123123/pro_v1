package com.red.sovereign.features.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

@Composable
fun StoryViewerScreen(
    userId: String,
    onClose: () -> Unit,
    viewModel: StoryViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stories by viewModel.stories.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val progress by viewModel.progress.collectAsState()
    
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(userId) { viewModel.loadStories(userId) }
    
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    if (stories.isEmpty()) return
    val currentStory = stories[currentIndex]

    // Update Player when story is Video
    LaunchedEffect(currentIndex) {
        if (currentStory.type == "VIDEO") {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentStory.mediaUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
        }
        viewModel.onStoryVisible(currentStory.id)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                if (offset.x < size.width / 3) viewModel.prevStory() else viewModel.nextStory()
            }
        }
    ) {
        // Content
        when (currentStory.type) {
            "IMAGE" -> AsyncImage(
                model = currentStory.mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            "TEXT" -> Box(
                modifier = Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(currentStory.backgroundColor ?: "#D32F2F"))),
                contentAlignment = Alignment.Center
            ) {
                Text(currentStory.caption ?: "", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            "VIDEO" -> AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlays: Progress Bars
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                LinearProgressIndicator(
                    progress = { if (index == currentIndex) progress else if (index < currentIndex) 1f else 0f },
                    modifier = Modifier.weight(1f).height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Header: User Info
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("يونس", color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
        }

        // Footer: Reply or Views
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth()) {
            // Logic for "Eye icon" if my story, or "Reply bar" if contact's
        }
    }
}
