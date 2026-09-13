package com.red.sovereign.features.pstn

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box

@Composable
fun DialPadScreen(
    onDismiss: () -> Unit = {},
    onNavigateToWebRtcCall: (String) -> Unit,
    onNavigateToPstnCall: (String) -> Unit
) {
    Box {}
}

fun formatPhoneNumber(number: String): String = number
