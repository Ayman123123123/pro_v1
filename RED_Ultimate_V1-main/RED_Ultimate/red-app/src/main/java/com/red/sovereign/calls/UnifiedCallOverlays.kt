package com.red.sovereign.calls

import androidx.compose.runtime.Composable

/**
 * 🎬 Unified Call Overlays — Professional, Integrated, Legendary
 *
 * أولوية عرض واحدة:
 * 1. مكالمة فردية 1:1 (YounesCallOverlay)
 * 2. اجتماع Zoom (ZoomGroupCallOverlay) — حتى 100 مشارك
 * 3. مكالمة جماعية (GroupCallOverlay) — iMO/Zoom style
 * 4. مؤتمر/مساحة صوتية (YounesConferenceOverlay) — X Spaces style
 * 5. بث مباشر (YounesLiveStreamOverlay) — TikTok style
 *
 * Only ONE full overlay shows at a time — prevents stacking at wrong times.
 * أي نوع مصغّر ونشط → شريط MinimizedCallBar الموحّد بدل الواجهة الكاملة.
 * أي وارد ثانٍ → بانر انتظار SecondIncomingBanner بدل إخفائه.
 */
@Composable
fun UnifiedCallOverlays() {
    val callState  = CallRuntime.state
    val groupState = GroupCallRuntime.state
    val confState  = ConferenceRuntime.state
    val liveState  = LiveStreamRuntime.state
    val zoomState  = ZoomRuntime.state

    // شريط مصغّر موحّد: أي نوع نشط ومصغّر → شريط واحد فقط (نفس الأولوية أدناه).
    val anyMinimized =
        (CallRuntime.isMinimized && (callState is CallUiState.Active || callState is CallUiState.ActiveWithIncoming)) ||
            (ZoomRuntime.isMinimized && zoomState is ZoomUiState.Active) ||
            (GroupCallRuntime.isMinimized && groupState is GroupCallUiState.Active) ||
            (ConferenceRuntime.isMinimized && confState is ConferenceUiState.Active) ||
            (LiveStreamRuntime.isMinimized && liveState is LiveStreamUiState.Active)
    if (anyMinimized) {
        MinimizedCallBar()
        SecondIncomingBanner()
        return
    }

    when {
        callState  !is CallUiState.Idle -> YounesCallOverlay()
        zoomState  !is ZoomUiState.Idle
            && zoomState !is ZoomUiState.Ended -> ZoomGroupCallOverlay()
        groupState !is GroupCallUiState.Idle
            && groupState !is GroupCallUiState.Ended -> GroupCallOverlay()
        confState  !is ConferenceUiState.Idle    -> YounesConferenceOverlay()
        liveState  !is LiveStreamUiState.Idle    -> YounesLiveStreamOverlay()
    }

    // بانر انتظار للمكالمة الواردة الثانية بدل إخفائها (عرض فقط).
    SecondIncomingBanner()
}
