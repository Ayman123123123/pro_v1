/**
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service.webrtc.links

/**
 * Result type for call link reads.
 */
sealed interface ReadCallLinkResult {
  data class Success(
    val callLinkState: REDCallLinkState
  ) : ReadCallLinkResult

  data class Failure(val status: Short) : ReadCallLinkResult
}
