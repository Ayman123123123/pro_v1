package com.red.sovereign.calls

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box

@Composable
fun Material3ExpressivePstnCallScreen(
    status: PstnCallStatus,
    number: String,
    metrics: CallMetrics = CallMetrics(),
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onKeypadToggle: (Boolean) -> Unit = {},
    onHoldToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean) -> Unit = {},
    onVideoToggle: (Boolean) -> Unit = {},
    onDtmfDigit: (String) -> Unit = {},
    onHangup: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Box {}
}
