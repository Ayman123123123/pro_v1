package com.red.sovereign.calls

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box

@Composable
fun Material3ExpressiveIncomingPstnCallScreen(
    callerNumber: String,
    callerName: String? = null,
    callId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDeclineWithMessage: (String) -> Unit = {},
    onAcceptVideo: () -> Unit = {},
    context: Context
) {
    Box {}
}
