package com.red.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun RedChatBubble(message: String, isMe: Boolean) {
    val gradient = if (isMe) {
        Brush.linearGradient(listOf(Color(0xFFD32F2F), Color(0xFFB71C1C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF262626), Color(0xFF1A1A1A)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp, 
                topEnd = 20.dp, 
                bottomStart = if (isMe) 20.dp else 4.dp, 
                bottomEnd = if (isMe) 4.dp else 20.dp
            ),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(gradient)
                    .padding(14.dp)
            ) {
                Text(text = message, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
