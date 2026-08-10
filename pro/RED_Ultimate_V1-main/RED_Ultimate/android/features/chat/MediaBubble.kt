package com.red.features.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun MediaBubble(type: String, url: String, fileName: String? = null) {
    Column(modifier = Modifier.widthIn(max = 240.dp).padding(4.dp)) {
        when (type) {
            "IMAGE" -> {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.height(180.dp).fillMaxWidth())
            }
            "VIDEO" -> {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.height(180.dp).fillMaxWidth())
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            "FILE" -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.Description, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(fileName ?: "Document", maxLines = 1)
                }
            }
        }
    }
}
