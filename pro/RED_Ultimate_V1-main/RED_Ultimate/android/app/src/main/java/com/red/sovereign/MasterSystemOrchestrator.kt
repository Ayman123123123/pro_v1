package com.red.sovereign

import com.red.sovereign.core.delivery.RedDeliveryEngine
import com.red.sovereign.features.calls.RedVoipMaster
import com.red.sovereign.features.pstn.PstnViewModel
import com.red.sovereign.core.auth.ApprovalManager
import com.red.sovereign.core.auth.IdentityManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RED Master System Orchestrator
 * Ensures 1080p VoIP, Isolated PSTN, and Guaranteed Messaging are live.
 */
@Singleton
class MasterSystemOrchestrator @Inject constructor(
    private val deliveryEngine: RedDeliveryEngine,
    private val voipMaster: RedVoipMaster,
    private val pstnViewModel: PstnViewModel,
    private val approvalManager: ApprovalManager,
    private val identityManager: IdentityManager
) {
    fun startSovereignSystem() {
        if (approvalManager.isUserApproved()) {
            println("🔴 RED: Initializing all systems for user ${identityManager.getUserName()}")
            
            // System C: Messaging
            deliveryEngine.initialize()
            
            // System A: VoIP SFU
            voipMaster.prepare()
            
            // System B: PSTN Isolated
            pstnViewModel.syncGatewayStatus()
        } else {
            approvalManager.showPendingUI()
        }
    }
}
