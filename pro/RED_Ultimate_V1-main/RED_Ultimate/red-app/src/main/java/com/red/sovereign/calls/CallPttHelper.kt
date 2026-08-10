package com.red.sovereign.calls

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Push-to-Talk (PTT) helper composable.
 * Useful for group calls where you want to speak only when needed.
 * During the press, mic is unmuted; on release, mic is muted.
 */
@Composable
fun PushToTalkButton(
    onPttStart: () -> Unit,
    onPttEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val color = if (isPressed) Color(0xFF00C98C) else Color.White.copy(alpha = 0.2f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPttStart()
                        try { tryAwaitRelease() } finally {
                            isPressed = false
                            onPttEnd()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPressed) "جارٍ الإرسال… ارفع لإيقاف" else "اضغط مطولاً للتحدث (PTT)",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
