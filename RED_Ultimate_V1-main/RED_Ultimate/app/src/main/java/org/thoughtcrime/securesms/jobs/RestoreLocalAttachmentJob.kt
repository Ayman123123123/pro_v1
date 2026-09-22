/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.jobs

import android.net.Uri
import org.signal.core.models.backup.MediaName
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.StreamUtil
import org.signal.core.util.androidx.DocumentFileInfo
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.backup.v2.local.ArchiveFileSystem
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.AttachmentTable.LocalRestorableAttachment
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.protos.RestoreLocalAttachmentJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.MmsException
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.StreamSupplier
import java.io.IOException

/**
 * Restore attachment from local backup storage.
 */
class RestoreLocalAttachmentJob private constructor(
  parameters: Parameters,
  private val attachmentId: AttachmentId,
  private val messageId: Long,
  private val restoreUri: Uri,
  private val size: Long
) : Job(parameters) {

  companion object {
    const val KEY = "RestoreLocalAttachmentJob"
    val TAG = Log.tag(RestoreLocalAttachmentJob::class.java)
    private const val CONCURRENT_QUEUES = 2

    fun enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo: Map<String, DocumentFileInfo>) {
      val jobManager = AppDependencies.jobManager

      REDStore.backup.localRestoreReconcilePending = true

      val orphanedCount = REDDatabase.attachments.markRestorableAttachmentsWithoutMessageAsFailed()
      if (orphanedCount > 0) {
        Log.w(TAG, "Failed $orphanedCount orphaned restorable attachment(s) with no backing message before enqueueing restores.")
      }

      do {
        val possibleRestorableAttachments: List<LocalRestorableAttachment> = REDDatabase.attachments.getRestorableLocalAttachments(500)
        val notRestorableAttachments = ArrayList<AttachmentId>(possibleRestorableAttachments.size)
        val restoreAttachmentJobs: MutableList<Job> = ArrayList(possibleRestorableAttachments.size)

        possibleRestorableAttachments
          .forEachIndexed { index, attachment ->
            val fileInfo = if (attachment.plaintextHash != null && attachment.localBackupKey != null) {
              mediaNameToFileInfo[MediaName.forLocalBackupFilename(attachment.plaintextHash, attachment.localBackupKey.key).name]
            } else {
              null
            }

            if (fileInfo != null) {
              restoreAttachmentJobs += RestoreLocalAttachmentJob(queueName(index), attachment, fileInfo)
            } else {
              notRestorableAttachments += attachment.attachmentId
            }
          }

        // Mark not restorable attachments as failed
        REDDatabase.attachments.setRestoreTransferState(notRestorableAttachments, AttachmentTable.TRANSFER_PROGRESS_FAILED)

        // Intentionally enqueues one at a time for safer attachment transfer state management
        Log.d(TAG, "Adding ${restoreAttachmentJobs.size} restore local attachment jobs")
        restoreAttachmentJobs.forEach { jobManager.add(it) }
      } while (possibleRestorableAttachments.isNotEmpty())

      ArchiveRestoreProgress.onRestoringMedia()

      val checkDoneJobs = (0 until CONCURRENT_QUEUES)
        .map {
          CheckRestoreMediaLeftJob(queueName(it))
        }

      AppDependencies.jobManager.addAll(checkDoneJobs)
    }

    private fun queueName(index: Int): String {
      return "RestoreLocalAttachmentJob_${index % CONCURRENT_QUEUES}"
    }
  }

  private constructor(queue: String, attachment: LocalRestorableAttachment, info: DocumentFileInfo) : this(
    Parameters.Builder()
      .setQueue(queue)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(3)
      .build(),
    attachmentId = attachment.attachmentId,
    messageId = attachment.mmsId,
    restoreUri = info.documentFile.uri,
    size = info.size
  )

  override fun serialize(): ByteArray {
    return RestoreLocalAttachmentJobData(
      attachmentId = attachmentId.id,
      messageId = messageId,
      fileUri = restoreUri.toString(),
      fileSize = size
    ).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    REDDatabase.attachments.setRestoreTransferState(attachmentId, AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS)
  }

  override fun run(): Result {
    Log.i(TAG, "onRun() messageId: $messageId  attachmentId: $attachmentId")

    val attachment = REDDatabase.attachments.getAttachmentWithMetadata(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.")
      return Result.failure()
    }

    if (attachment.dataHash == null || attachment.metadata?.localBackupKey == null) {
      Log.w(TAG, "Attachment no longer has a plaintext hash or local backup key")
      return Result.failure()
    }

    if (attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
      Log.w(TAG, "Attachment does not need to be restored.")
      return Result.success()
    }

    val streamSupplier = StreamSupplier {
      ArchiveFileSystem.openInputStream(context, restoreUri) ?: throw IOException("Unable to open stream for $restoreUri")
    }

    try {
      val iv = ByteArray(16)
      streamSupplier.openStream().use { StreamUtil.readFully(it, iv) }
      AttachmentCipherInputStream.createForAttachment(
        streamSupplier = streamSupplier,
        streamLength = size,
        plaintextLength = attachment.size,
        combinedKeyMaterial = attachment.metadata.localBackupKey.key,
        integrityCheck = IntegrityCheck.forPlaintextHash(
          plaintextHash = Base64.decode(attachment.dataHash)
        ),
        incrementalDigest = null,
        incrementalMacChunkSize = 0
      ).use { input ->
        REDDatabase
          .attachments
          .finalizeAttachmentAfterDownload(
            mmsId = attachment.mmsId,
            attachmentId = attachment.attachmentId,
            inputStream = input,
            archiveRestore = true
          )
      }
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "Experienced an InvalidMessageException while trying to read attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "Detected an invalid mac. Treating as a permanent failure.")
        markPermanentlyFailed(messageId, attachmentId)
      }
      return Result.failure()
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to store attachment.", e)
      return Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, "Experienced an exception while trying to read attachment.", e)
      return Result.retry(defaultBackoff())
    } catch (e: SecurityException) {
      Log.w(TAG, "Lost access to the backup directory. Unable to restore attachment.", e)
      REDStore.backup.localRestoreDirectoryError = true
      return Result.failure()
    }

    return Result.success()
  }

  override fun onFailure() {
    markFailed(messageId, attachmentId)
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  private fun markFailed(messageId: Long, attachmentId: AttachmentId) {
    REDDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)
  }

  private fun markPermanentlyFailed(messageId: Long, attachmentId: AttachmentId) {
    REDDatabase.attachments.setTransferProgressPermanentFailure(attachmentId, messageId)
  }

  class Factory : Job.Factory<RestoreLocalAttachmentJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreLocalAttachmentJob {
      val data = RestoreLocalAttachmentJobData.ADAPTER.decode(serializedData!!)
      return RestoreLocalAttachmentJob(
        parameters = parameters,
        attachmentId = AttachmentId(data.attachmentId),
        messageId = data.messageId,
        restoreUri = Uri.parse(data.fileUri),
        size = data.fileSize
      )
    }
  }
}
