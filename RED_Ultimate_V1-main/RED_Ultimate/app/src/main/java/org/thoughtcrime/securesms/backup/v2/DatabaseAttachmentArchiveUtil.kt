/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import android.text.TextUtils
import org.signal.core.models.backup.MediaName
import org.signal.core.util.Base64
import org.signal.core.util.Base64.decodeBase64
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.Util
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.attachments.InvalidAttachmentException
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.RemoteConfig
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId
import java.io.IOException
import java.util.Optional

object DatabaseAttachmentArchiveUtil {
  @JvmStatic
  fun requireMediaName(attachment: DatabaseAttachment): MediaName {
    require(hadIntegrityCheckPerformed(attachment)) { "${attachment.attachmentId} has not had its integrity check performed yet. TransferState: ${attachment.transferState}, ArchiveTransferState: ${attachment.archiveTransferState}" }
    return MediaName.fromPlaintextHashAndRemoteKey(attachment.dataHash!!.decodeBase64OrThrow(), attachment.remoteKey!!.decodeBase64OrThrow())
  }

  /**
   * For java, since it struggles with value classes.
   */
  @JvmStatic
  fun requireMediaNameAsString(attachment: DatabaseAttachment): String {
    require(hadIntegrityCheckPerformed(attachment)) { "${attachment.attachmentId} has not had its integrity check performed yet. TransferState: ${attachment.transferState}, ArchiveTransferState: ${attachment.archiveTransferState}" }
    return MediaName.fromPlaintextHashAndRemoteKey(attachment.dataHash!!.decodeBase64OrThrow(), attachment.remoteKey!!.decodeBase64OrThrow()).name
  }

  @JvmStatic
  fun getMediaName(attachment: DatabaseAttachment): MediaName? {
    return if (hadIntegrityCheckPerformed(attachment)) {
      val plaintextHash = attachment.dataHash.decodeBase64()
      val remoteKey = attachment.remoteKey?.decodeBase64()

      if (plaintextHash != null && remoteKey != null) {
        MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey)
      } else {
        null
      }
    } else {
      null
    }
  }

  @JvmStatic
  fun requireThumbnailMediaName(attachment: DatabaseAttachment): MediaName {
    require(hadIntegrityCheckPerformed(attachment)) { "${attachment.attachmentId} has not had its integrity check performed yet. TransferState: ${attachment.transferState}, ArchiveTransferState: ${attachment.archiveTransferState}" }
    return MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(attachment.dataHash!!.decodeBase64OrThrow(), attachment.remoteKey!!.decodeBase64OrThrow())
  }

  /**
   * Returns whether an integrity check has been performed at some point by checking against its transfer state
   */
  fun hadIntegrityCheckPerformed(attachment: DatabaseAttachment): Boolean {
    if (attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED) {
      return true
    }

    return when (attachment.transferState) {
      AttachmentTable.TRANSFER_PROGRESS_DONE,
      AttachmentTable.TRANSFER_NEEDS_RESTORE,
      AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS,
      AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> true

      else -> false
    }
  }
}

fun DatabaseAttachment.requireMediaName(): MediaName {
  return DatabaseAttachmentArchiveUtil.requireMediaName(this)
}

fun DatabaseAttachment.getMediaName(): MediaName? {
  return DatabaseAttachmentArchiveUtil.getMediaName(this)
}

fun DatabaseAttachment.requireThumbnailMediaName(): MediaName {
  return DatabaseAttachmentArchiveUtil.requireThumbnailMediaName(this)
}

fun DatabaseAttachment.hadIntegrityCheckPerformed(): Boolean {
  return DatabaseAttachmentArchiveUtil.hadIntegrityCheckPerformed(this)
}

/**
 * Creates a [REDServiceAttachmentPointer] for the archived attachment of the given [DatabaseAttachment].
 */
@Throws(InvalidAttachmentException::class)
fun DatabaseAttachment.createArchiveAttachmentPointer(useArchiveCdn: Boolean): REDServiceAttachmentPointer {
  if (remoteKey.isNullOrBlank()) {
    throw InvalidAttachmentException("empty encrypted key")
  }

  if (remoteDigest == null && dataHash == null) {
    throw InvalidAttachmentException("no integrity check available")
  }

  return try {
    val (remoteId, cdnNumber) = if (useArchiveCdn) {
      val mediaRootBackupKey = REDStore.backup.mediaRootBackupKey
      val mediaCdnPath = BackupRepository.getArchivedMediaCdnPath().successOrThrow()

      val id = REDServiceAttachmentRemoteId.Backup(
        mediaCdnPath = mediaCdnPath,
        mediaId = this.requireMediaName().toMediaId(mediaRootBackupKey).encode()
      )

      id to (archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn)
    } else {
      if (remoteLocation.isNullOrEmpty()) {
        throw InvalidAttachmentException("empty content id")
      }

      REDServiceAttachmentRemoteId.from(remoteLocation, cdn.cdnNumber) to cdn.cdnNumber
    }

    val key = Base64.decode(remoteKey)

    REDServiceAttachmentPointer(
      cdnNumber = cdnNumber,
      remoteId = remoteId,
      contentType = null,
      key = key,
      size = Optional.of(Util.toIntExact(size)),
      preview = Optional.empty(),
      width = 0,
      height = 0,
      digest = Optional.ofNullable(remoteDigest),
      incrementalDigest = Optional.ofNullable(getIncrementalDigest()),
      incrementalMacChunkSize = incrementalMacChunkSize,
      fileName = Optional.ofNullable(fileName),
      voiceNote = voiceNote,
      isBorderless = borderless,
      isGif = videoGif,
      caption = Optional.empty(),
      blurHash = Optional.ofNullable(blurHash).map { it.hash },
      uploadTimestamp = uploadTimestamp,
      uuid = uuid
    )
  } catch (e: IOException) {
    throw InvalidAttachmentException(e)
  } catch (e: ArithmeticException) {
    throw InvalidAttachmentException(e)
  }
}

/**
 * Creates a [REDServiceAttachmentPointer] for an archived thumbnail of the given [DatabaseAttachment].
 */
@Throws(InvalidAttachmentException::class)
fun DatabaseAttachment.createArchiveThumbnailPointer(): REDServiceAttachmentPointer {
  if (TextUtils.isEmpty(remoteKey)) {
    throw InvalidAttachmentException("empty encrypted key")
  }

  val mediaRootBackupKey = REDStore.backup.mediaRootBackupKey
  val mediaCdnPath = BackupRepository.getArchivedMediaCdnPath().successOrThrow()
  return try {
    val key = mediaRootBackupKey.deriveThumbnailTransitKey(requireThumbnailMediaName())
    val mediaId = mediaRootBackupKey.deriveMediaId(requireThumbnailMediaName()).encode()
    REDServiceAttachmentPointer(
      cdnNumber = archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn,
      remoteId = REDServiceAttachmentRemoteId.Backup(
        mediaCdnPath = mediaCdnPath,
        mediaId = mediaId
      ),
      contentType = null,
      key = key,
      size = Optional.empty(),
      preview = Optional.empty(),
      width = 0,
      height = 0,
      digest = Optional.empty(),
      incrementalDigest = Optional.empty(),
      incrementalMacChunkSize = incrementalMacChunkSize,
      fileName = Optional.empty(),
      voiceNote = voiceNote,
      isBorderless = borderless,
      isGif = videoGif,
      caption = Optional.empty(),
      blurHash = Optional.ofNullable(blurHash).map { it.hash },
      uploadTimestamp = uploadTimestamp,
      uuid = uuid
    )
  } catch (e: IOException) {
    throw InvalidAttachmentException(e)
  } catch (e: ArithmeticException) {
    throw InvalidAttachmentException(e)
  }
}
