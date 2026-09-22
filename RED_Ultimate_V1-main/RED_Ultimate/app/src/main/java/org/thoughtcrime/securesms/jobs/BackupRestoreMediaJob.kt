/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import com.red.sovereign.R
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.NotPushRegisteredException
import com.red.sovereign.service.BackupMediaRestoreService
import kotlin.time.Duration.Companion.days

/**
 * Job that is responsible for enqueueing attachment download
 * jobs upon restore.
 */
class BackupRestoreMediaJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(BackupRestoreMediaJob::class.java)

    const val KEY = "BackupRestoreMediaJob"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setMaxInstancesForFactory(2)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onAdded() {
    ArchiveRestoreProgress.onStartMediaRestore()
  }

  override fun onRun() {
    if (!REDStore.account.isRegistered) {
      Log.e(TAG, "Not registered, cannot restore!")
      throw NotPushRegisteredException()
    }

    val jobManager = AppDependencies.jobManager
    val batchSize = 500
    val restoreTime = System.currentTimeMillis()

    val orphanedCount = REDDatabase.attachments.markRestorableAttachmentsWithoutMessageAsFailed()
    if (orphanedCount > 0) {
      Log.w(TAG, "$orphanedCount orphaned restorable attachments marked failed")
    }

    val stalledCount = REDDatabase.attachments.resetRestorableAttachmentsInProgressToNeedsRestore()
    if (stalledCount > 0) {
      Log.w(TAG, "$stalledCount attachments were stuck mid-restore; reset to needs-restore so they can be re-enqueued")
    }

    do {
      val restoreThumbnailJobs: MutableList<RestoreAttachmentThumbnailJob> = mutableListOf()
      val restoreFullAttachmentJobs: MutableList<RestoreAttachmentJob> = mutableListOf()

      val restoreThumbnailOnlyAttachmentsIds: MutableList<AttachmentId> = mutableListOf()
      val notRestorable: MutableList<AttachmentId> = mutableListOf()

      val last30DaysAttachments = REDDatabase.attachments.getLast30DaysOfRestorableAttachments(batchSize)
      val remainingSize = batchSize - last30DaysAttachments.size

      val remaining = if (remainingSize > 0) {
        REDDatabase.attachments.getOlderRestorableAttachments(batchSize = remainingSize)
      } else {
        listOf()
      }

      val attachmentBatch = last30DaysAttachments + remaining
      val messageIds = attachmentBatch.map { it.mmsId }.toSet()
      val messageMap = REDDatabase.messages.getMessages(messageIds).associate { it.id to (it as MmsMessageRecord) }

      for (attachment in attachmentBatch) {
        val isWallpaper = attachment.mmsId == AttachmentTable.WALLPAPER_MESSAGE_ID

        val message = messageMap[attachment.mmsId]
        if (message == null && !isWallpaper) {
          Log.w(TAG, "Unable to find message for ${attachment.attachmentId}, mmsId: ${attachment.mmsId}")
          notRestorable += attachment.attachmentId
          continue
        }

        if (isWallpaper || shouldRestoreFullSize(message!!, restoreTime, REDStore.backup.optimizeStorage)) {
          restoreFullAttachmentJobs += RestoreAttachmentJob.forInitialRestore(
            messageId = attachment.mmsId,
            attachmentId = attachment.attachmentId,
            stickerPackId = attachment.stickerPackId,
            queueHash = attachment.plaintextHash?.contentHashCode() ?: attachment.remoteKey?.contentHashCode()
          )
        } else {
          restoreThumbnailJobs += RestoreAttachmentThumbnailJob(
            messageId = attachment.mmsId,
            attachmentId = attachment.attachmentId,
            highPriority = false
          )

          restoreThumbnailOnlyAttachmentsIds += attachment.attachmentId
        }
      }

      REDDatabase.rawDatabase.withinTransaction {
        // Mark not restorable thumbnails and attachments as failed
        REDDatabase.attachments.setThumbnailRestoreState(notRestorable, AttachmentTable.ThumbnailRestoreState.PERMANENT_FAILURE)
        REDDatabase.attachments.setRestoreTransferState(notRestorable, AttachmentTable.TRANSFER_PROGRESS_FAILED)

        // Set thumbnail only attachments as offloaded
        REDDatabase.attachments.setRestoreTransferState(restoreThumbnailOnlyAttachmentsIds, AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
      }

      ArchiveRestoreProgress.onProcessStart()

      // Intentionally enqueues one at a time for safer attachment transfer state management
      restoreThumbnailJobs.forEach { jobManager.add(it) }
      restoreFullAttachmentJobs.forEach { jobManager.add(it) }
    } while (restoreThumbnailJobs.isNotEmpty() || restoreFullAttachmentJobs.isNotEmpty() || notRestorable.isNotEmpty())

    BackupMediaRestoreService.start(context, context.getString(R.string.BackupStatus__restoring_media))
    ArchiveRestoreProgress.onRestoringMedia()

    RestoreAttachmentJob.Queues.INITIAL_RESTORE.forEach { queue ->
      jobManager.add(CheckRestoreMediaLeftJob(queue))
    }
  }

  private fun shouldRestoreFullSize(message: MmsMessageRecord, restoreTime: Long, optimizeStorage: Boolean): Boolean {
    return !optimizeStorage || ((restoreTime - message.dateReceived) < 30.days.inWholeMilliseconds)
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupRestoreMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupRestoreMediaJob {
      return BackupRestoreMediaJob(parameters)
    }
  }
}
