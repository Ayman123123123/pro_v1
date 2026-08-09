package com.red.sovereign.calls

import androidx.compose.runtime.Composable

/**
 * 🎬 Unified Call Overlays — Professional, Integrated, Legendary
 * Priority: 1-1 Call > Conference > Live Stream
 * Only ONE overlay shows at a time — prevents stacking at wrong times/places
 */
@Composable
fun UnifiedCallOverlays() {
    val callState = CallRuntime.state
    val confState = ConferenceRuntime.state
    val liveState = LiveStreamRuntime.state

    when {
        callState !is CallUiState.Idle -> YounesCallOverlay()
        confState !is ConferenceUiState.Idle -> YounesConferenceOverlay()
        liveState !is LiveStreamUiState.Idle -> YounesLiveStreamOverlay()
    }
}
