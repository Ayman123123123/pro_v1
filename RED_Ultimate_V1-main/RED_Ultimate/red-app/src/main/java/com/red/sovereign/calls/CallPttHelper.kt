package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Push-to-Talk (PTT) helper composable for RED Sovereign Call System.
 * Useful for walkie-talkie group calls where users speak only when holding the button.
 * During press, microphone is unmuted; on release, microphone is muted.
 * Includes haptic feedback, accessibility semantics, and robust error/cancellation handling.
 */
@Composable
fun PushToTalkButton(
    onPttStart: () -> Unit,
    onPttEnd: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF00C98C),
    inactiveColor: Color = Color.White.copy(alpha = 0.2f),
    activeText: String = "جارٍ الإرسال… ارفع لإيقاف",
    inactiveText: String = "اضغط مطولاً للتحدث (PTT)"
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val color = if (isPressed) activeColor else inactiveColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(color)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        runCatching { onPttStart() }
                        try {
                            tryAwaitRelease()
                        } catch (e: Exception) {
                            // Handle potential pointer cancellation safely
                        } finally {
                            isPressed = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            runCatching { onPttEnd() }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPressed) activeText else inactiveText,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
