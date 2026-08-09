package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🖼️ YOUNES Sovereign Media Bubble
 */
@Composable
fun MediaBubble(
    type: String,
    url: String,
    fileName: String? = null,
    isMe: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(modifier = Modifier.widthIn(max = 260.dp).padding(4.dp)) {
        when (type) {
            "IMAGE" -> {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
                )
            }
            "VIDEO" -> {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            "FILE" -> {
                Surface(shape = RoundedCornerShape(12.dp), color = if (isMe) SovereignColors.Cyan.copy(alpha = 0.1f) else SovereignColors.SurfaceNavy) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = SovereignColors.Cyan)
                        Text(fileName ?: "ملف سيادي", modifier = Modifier.padding(start = 12.dp), color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
