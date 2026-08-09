/**
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service.webrtc.links

/**
 * Result type for call link creation.
 */
sealed interface CreateCallLinkResult {
  data class Success(
    val credentials: CallLinkCredentials,
    val state: REDCallLinkState
  ) : CreateCallLinkResult

  data class Failure(
    val status: Short
  ) : CreateCallLinkResult
}
