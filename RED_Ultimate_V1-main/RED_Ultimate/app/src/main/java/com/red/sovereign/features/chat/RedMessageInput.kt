package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RedMessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRecordVoice: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Attachment Button
        IconButton(
            onClick = onAttach,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Attach", tint = Color.Gray)
        }

        // Text Input Field
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .defaultMinSize(minHeight = 24.dp)
        ) {
            if (text.isEmpty()) {
                Text("Message...", color = Color.Gray, fontSize = 16.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Send or Record Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank()) Color(0xFFB71C1C) else Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = if (text.isNotBlank()) onSend else onRecordVoice
            ) {
                Icon(
                    imageVector = if (text.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = if (text.isNotBlank()) "Send" else "Record Voice",
                    tint = if (text.isNotBlank()) Color.White else Color.Gray
                )
            }
        }
    }
}
