package com.red.sovereign.calls

import androidx.compose.runtime.Composable

/**
 * 🎬 Unified Call Overlays — Professional, Integrated, Legendary
 * Priority: 1-1 Call > PSTN Call > Conference > Live Stream
 * Only ONE overlay shows at a time — prevents stacking at wrong times/places
 *
 * مكالمة PSTN تلي المكالمة المشفَّرة في الأولوية: الأخيرة تحمل وسائط
 * حيّة داخل التطبيق، أما الأولى فيمرّ صوتها عبر شبكة المشغّل، فإخفاؤها
 * لحظةً أقلّ ضرراً من إخفاء مكالمة قائمة.
 */
@Composable
fun UnifiedCallOverlays() {
    val callState = CallRuntime.state
    val pstnStatus = CallRuntime.pstnStatus
    val confState = ConferenceRuntime.state
    val liveState = LiveStreamRuntime.state

    when {
        callState !is CallUiState.Idle -> YounesCallOverlay()
        pstnStatus != PstnCallStatus.IDLE -> YounesPstnCallOverlay()
        confState !is ConferenceUiState.Idle -> YounesConferenceOverlay()
        liveState !is LiveStreamUiState.Idle -> YounesLiveStreamOverlay()
    }
}
