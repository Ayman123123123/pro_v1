package com.red.sovereign.calls

import androidx.compose.runtime.Composable

/**
 * 🎬 Unified Call Overlays — Professional, Integrated, Legendary
 *
 * أولوية العرض:
 * 1. مكالمة فردية 1:1 (YounesCallOverlay)
 * 2. مكالمة جماعية (GroupCallOverlay) — iMO/Zoom style
 * 3. مؤتمر/مساحة صوتية (YounesConferenceOverlay) — X Spaces style
 * 4. بث مباشر (YounesLiveStreamOverlay) — TikTok style
 *
 * Only ONE overlay shows at a time — prevents stacking at wrong times.
 */
@Composable
fun UnifiedCallOverlays() {
    val callState  = CallRuntime.state
    val groupState = GroupCallRuntime.state
    val confState  = ConferenceRuntime.state
    val liveState  = LiveStreamRuntime.state

    when {
        callState  !is CallUiState.Idle && !CallRuntime.isMinimized -> YounesCallOverlay()
        groupState !is GroupCallUiState.Idle

            && groupState !is GroupCallUiState.Ended -> GroupCallOverlay()
        confState  !is ConferenceUiState.Idle    -> YounesConferenceOverlay()
        liveState  !is LiveStreamUiState.Idle    -> YounesLiveStreamOverlay()
    }
}

