/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.signal.core.util.Base64

/**
 * Acts as credentials for various archive operations.
 */
class ArchiveCredentialPresentation(
  val presentation: ByteArray,
  val signedPresentation: ByteArray
) {
  fun toHeaders(): MutableMap<String, String> {
    return mutableMapOf(
      "X-RED-ZK-Auth" to Base64.encodeWithPadding(presentation),
      "X-RED-ZK-Auth-Signature" to Base64.encodeWithPadding(signedPresentation)
    )
  }
}
