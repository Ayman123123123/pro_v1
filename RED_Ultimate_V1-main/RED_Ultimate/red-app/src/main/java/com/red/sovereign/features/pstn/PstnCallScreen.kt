package com.red.sovereign.features.pstn

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.PstnState

@Composable
fun PstnCallScreen(
    number: String,
    state: PstnState,
    onHangup: () -> Unit,
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean, String) -> Unit = { _, _ -> },
    viewModel: AuthViewModel? = null
) {
    Box {}
}
