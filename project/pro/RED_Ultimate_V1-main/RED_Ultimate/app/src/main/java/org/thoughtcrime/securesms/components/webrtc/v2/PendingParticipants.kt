/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.components.webrtc.PendingParticipantsView
import com.red.sovereign.service.webrtc.PendingParticipantCollection

/**
 * Re-implementation of PendingParticipantsView in compose.
 */
@Composable
fun PendingParticipants(
  pendingParticipantsState: PendingParticipantsState,
  pendingParticipantsListener: PendingParticipantsListener,
  modifier: Modifier = Modifier
) {
  if (pendingParticipantsState.isInPipMode) {
    return
  }

  var hasDisplayedContent by remember { mutableStateOf(false) }

  if (hasDisplayedContent || pendingParticipantsState.pendingParticipantCollection.getUnresolvedPendingParticipants().isNotEmpty()) {
    hasDisplayedContent = true

    AndroidView(
      ::PendingParticipantsView,
      modifier = modifier
    ) { view ->
      view.listener = pendingParticipantsListener
      view.applyState(pendingParticipantsState.pendingParticipantCollection)
    }
  }
}

@DayNightPreviews
@Composable
fun PendingParticipantsPreview() {
  Previews.Preview {
    PendingParticipants(
      pendingParticipantsState = PendingParticipantsState(
        pendingParticipantCollection = PendingParticipantCollection(),
        isInPipMode = false
      ),
      pendingParticipantsListener = PendingParticipantsListener.Empty
    )
  }
}
