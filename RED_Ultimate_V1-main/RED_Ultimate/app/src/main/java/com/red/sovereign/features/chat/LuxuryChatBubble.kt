package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LuxuryChatBubble(message: String, isMe: Boolean, status: String) {
    val gradient = if (isMe) {
        Brush.linearGradient(listOf(Color(0xFFD32F2F), Color(0xFFB71C1C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF1E1E1E), Color(0xFF121212)))
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (isMe) 20.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 20.dp
            ),
            color = Color.Transparent,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.background(gradient).padding(12.dp)) {
                Text(text = message, color = Color.White, fontSize = 16.sp)
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text("12:45 PM", fontSize = 10.sp, color = Color.White.copy(0.6f))
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Text(if (status == "READ") "✓✓" else "✓", color = if (status == "READ") Color.Cyan else Color.White)
                    }
                }
            }
        }
    }
}
