/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.network.api.AttachmentUploadResult
import com.red.sovereign.attachments.Cdn
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId
import kotlin.random.Random

object AttachmentTableTestUtil {

  fun createUploadResult(attachmentId: AttachmentId, uploadTimestamp: Long = System.currentTimeMillis()): AttachmentUploadResult {
    val databaseAttachment = REDDatabase.attachments.getAttachment(attachmentId)!!

    return AttachmentUploadResult(
      remoteId = REDServiceAttachmentRemoteId.V4("somewhere-${Random.nextLong()}"),
      cdnNumber = Cdn.CDN_3.cdnNumber,
      key = databaseAttachment.remoteKey?.let { Base64.decode(it) } ?: Util.getSecretBytes(64),
      digest = Random.nextBytes(32),
      incrementalDigest = Random.nextBytes(16),
      incrementalDigestChunkSize = 5,
      uploadTimestamp = uploadTimestamp,
      dataSize = databaseAttachment.size,
      blurHash = databaseAttachment.blurHash?.hash
    )
  }
}
