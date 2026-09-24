/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.webrtc.v2

import com.red.sovereign.service.webrtc.PendingParticipantCollection

/**
 * Represents the current state of the pending participants card.
 */
data class PendingParticipantsState(
  val pendingParticipantCollection: PendingParticipantCollection,
  val isInPipMode: Boolean
)
