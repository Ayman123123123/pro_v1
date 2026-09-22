/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId

/**
 * The result of uploading an attachment. Just the additional metadata related to the upload itself.
 */
class AttachmentUploadResult(
  val remoteId: REDServiceAttachmentRemoteId,
  val cdnNumber: Int,
  val key: ByteArray,
  val digest: ByteArray,
  val incrementalDigest: ByteArray?,
  val incrementalDigestChunkSize: Int,
  val dataSize: Long,
  val uploadTimestamp: Long,
  val blurHash: String?
)
