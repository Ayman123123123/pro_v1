package com.red.sovereign.features.calls

import com.red.sovereign.features.pstn.PstnViewModel
import javax.inject.Inject

/**
 * RED Call Orchestrator
 * Decides whether to use WebRTC (Internet) or Dinstar (GSM).
 */
class CallOrchestrator @Inject constructor(
    private val voipMaster: RedVoipMaster,
    private val pstnViewModel: PstnViewModel
) {
    fun initiateCall(target: String, isGsm: Boolean) {
        if (isGsm) {
            pstnViewModel.makePstnCall(target) // System B
        } else {
            voipMaster.startSecureCall(target) // System A
        }
    }

    fun startConference(roomId: String) {
        // Mediasoup Multi-user Logic
    }

    fun startLiveStream(streamId: String) {
        // System A: Broadcast Logic
    }
}
