package com.red.sovereign.core

import com.red.sovereign.core.auth.IdentityManager
import com.red.sovereign.features.calls.RedVoipMaster
import com.red.sovereign.features.pstn.PstnViewModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RED Master Feature Set — aggregates all system capabilities.
 */
@Singleton
data class MasterFeatureSet @Inject constructor(
    val voip: RedVoipMaster,           // System A: 1080p AV1
    val identity: IdentityManager      // Auth: Admin Approval
) {
    val systemA: String get() = "1080p WebRTC"
    val systemB: String get() = "GSM Gateway (Dinstar UC2000)"
    val systemC: String get() = "E2E Messaging with Guaranteed Delivery"
}
