/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.messages

import java.util.Optional
import java.util.UUID

/**
 * Represents a received REDServiceAttachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using [REDServiceMessageReceiver.retrieveAttachment]
 *
 * @author Moxie Marlinspike
 */
class REDServiceAttachmentPointer(
  val cdnNumber: Int,
  val remoteId: REDServiceAttachmentRemoteId,
  contentType: String?,
  val key: ByteArray?,
  val size: Optional<Int>,
  val preview: Optional<ByteArray>,
  val width: Int,
  val height: Int,
  val digest: Optional<ByteArray>,
  val incrementalDigest: Optional<ByteArray>,
  val incrementalMacChunkSize: Int,
  val fileName: Optional<String>,
  val voiceNote: Boolean,
  val isBorderless: Boolean,
  val isGif: Boolean,
  val caption: Optional<String>,
  val blurHash: Optional<String>,
  val uploadTimestamp: Long,
  val uuid: UUID?
) : REDServiceAttachment(contentType) {
  override fun isStream() = false
  override fun isPointer() = true
}
