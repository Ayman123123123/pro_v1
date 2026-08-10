package com.red.sovereign.features.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CreateStoryScreen(
    onFinished: () -> Unit,
    viewModel: StoryViewModel = hiltViewModel()
) {
    var mode by remember { mutableStateOf("TEXT") } // TEXT, IMAGE, VIDEO
    var textInput by remember { mutableStateOf("") }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.createStory("IMAGE", uri)
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is StoryUiState.Success) onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(if (mode == "TEXT") Color(0xFFD32F2F) else Color.Black)) {
        if (mode == "TEXT") {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                placeholder = { Text("What's on your mind?") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )
        }

        // Bottom Selection Bar
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(onClick = { mode = "TEXT" }) { Icon(Icons.Default.TextFields, null, tint = Color.White) }
            IconButton(onClick = { galleryLauncher.launch("image/*") }) { Icon(Icons.Default.Image, null, tint = Color.White) }
        }

        // Send Button
        FloatingActionButton(
            onClick = { if (textInput.isNotBlank()) viewModel.createStory("TEXT", text = textInput, bg = "#D32F2F") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Send, null)
        }
    }
}
