/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service.webrtc

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableCompat
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.service.webrtc.state.WebRtcServiceState

/**
 * Active call data to be returned from calls to isInCallQuery.
 */
@Parcelize
data class ActiveCallData(
  val recipientId: RecipientId
) : Parcelable {

  companion object {
    private const val KEY = "ACTIVE_CALL_DATA"

    @JvmStatic
    fun fromCallState(webRtcServiceState: WebRtcServiceState): ActiveCallData {
      return ActiveCallData(
        webRtcServiceState.callInfoState.callRecipient.id
      )
    }

    @JvmStatic
    fun fromBundle(bundle: Bundle): ActiveCallData {
      return bundle.getParcelableCompat(KEY, ActiveCallData::class.java)!!
    }
  }

  fun toBundle(): Bundle = bundleOf(KEY to this)
}
