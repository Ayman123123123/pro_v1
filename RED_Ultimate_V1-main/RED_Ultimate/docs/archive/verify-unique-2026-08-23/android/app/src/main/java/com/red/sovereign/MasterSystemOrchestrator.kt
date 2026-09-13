package com.red.sovereign

import android.util.Log
import com.red.sovereign.core.delivery.RedDeliveryEngine
import com.red.sovereign.features.calls.RedVoipMaster
import com.red.sovereign.features.pstn.PstnViewModel
import com.red.features.dinstar.DinstarViewModel
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
    private val identityManager: IdentityManager,
    private val dinstarViewModel: DinstarViewModel
) {
    companion object { private const val TAG = "RED.Orchestrator" }

    @Volatile private var isRunning = false

    fun startSovereignSystem() {
        if (isRunning) {
            Log.w(TAG, "System already running, ignoring duplicate start")
            return
        }

        if (approvalManager.isUserApproved()) {
            Log.i(TAG, "Initializing sovereign systems for user ${identityManager.getUserName()}")

            // System C: Guaranteed Messaging
            runCatching { deliveryEngine.initialize() }
                .onFailure { Log.e(TAG, "Delivery engine init failed", it) }

            // System A: VoIP SFU (1080p AV1/Opus)
            runCatching { voipMaster.prepare() }
                .onFailure { Log.e(TAG, "VoIP master prepare failed", it) }

            // System B: PSTN Isolated (DINSTAR UC2000-VE-8G)
            runCatching { pstnViewModel.syncGatewayStatus() }
                .onFailure { Log.e(TAG, "PSTN gateway sync failed", it) }

            // Dinstar Gateway Discovery & Live Monitoring
            runCatching { dinstarViewModel.discoverGateway() }
                .onFailure { Log.e(TAG, "Dinstar gateway discovery failed", it) }

            isRunning = true
            Log.i(TAG, "All sovereign systems initialized successfully")
        } else {
            Log.w(TAG, "User not approved — showing pending UI")
            approvalManager.showPendingUI()
        }
    }

    fun stopSovereignSystem() {
        if (!isRunning) return
        Log.i(TAG, "Shutting down sovereign systems")
        dinstarViewModel.stopLiveMonitoring()
        isRunning = false
    }

    fun isSystemRunning(): Boolean = isRunning
}
