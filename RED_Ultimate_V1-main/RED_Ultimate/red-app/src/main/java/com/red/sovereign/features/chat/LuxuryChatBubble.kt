package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * فقاعة دردشة "Luxury" — تصميم عصري وحصري للمنصة السيادية.
 * يتميز بزوايا ناعمة وتدرج لوني يعكس حالة الرسالة.
 */
@Composable
fun LuxuryChatBubble(
    message: String,
    isMe: Boolean,
    time: String,
    status: String,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val timeColor = textColor.copy(alpha = 0.7f)

    // Modern shape: smooth rounded corners, with a sharper edge indicating direction
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMe) 18.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 18.dp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(bubbleColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() }
                    )
                }
                .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message,
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    fontSize = 11.sp,
                    color = timeColor
                )
                if (isMe) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (status == "READ" || status == "DELIVERED") "✓✓" else "✓",
                        color = if (status == "READ") Color(0xFF00E6A0) else timeColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
