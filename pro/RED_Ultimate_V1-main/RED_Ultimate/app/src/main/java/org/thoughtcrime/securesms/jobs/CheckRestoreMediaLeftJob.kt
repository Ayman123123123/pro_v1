/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.backup.DeletionState
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.service.BackupMediaRestoreService
import kotlin.time.Duration.Companion.seconds

/**
 * Intended to be enqueued after the various media restore jobs to check progress to completion. When this job
 * runs it expects all media to be restored and will re-enqueue a new instance if not. Re-enqueue is likely to happen
 * when one of the restore queues finishes before the other(s).
 */
class CheckRestoreMediaLeftJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY = "CheckRestoreMediaLeftJob"

    private val TAG = Log.tag(CheckRestoreMediaLeftJob::class)
    private val FINALIZE_LOCK = Any()
    private const val STALLED_RECOVERY_QUEUE = "CheckRestoreMediaLeftJob::StalledRecovery"

    fun enqueueStalledRecoveryCheck() {
      AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(STALLED_RECOVERY_QUEUE))
    }
  }

  constructor(queue: String) : this(
    Parameters.Builder()
      .setQueue(queue)
      .setLifespan(Parameters.IMMORTAL)
      .setGlobalPriority(Parameters.PRIORITY_LOW)
      .setMaxAttempts(2)
      .build()
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray? = null

  override fun run(): Result {
    val remainingAttachmentSize = REDDatabase.attachments.getRemainingRestorableAttachmentSize()

    if (remainingAttachmentSize == 0L) {
      Log.d(TAG, "Media restore complete: there are no remaining restorable attachments.")
      onMediaRestoreComplete()
    } else if (runAttempt == 0) {
      Log.w(TAG, "Still have remaining data to restore, will retry before checking job queues, queue: ${parameters.queue} estimated remaining: $remainingAttachmentSize")
      return Result.retry(30.seconds.inWholeMilliseconds)
    } else {
      handleRemainingAfterMaxAttempts(remainingAttachmentSize)
    }

    return Result.success()
  }

  /**
   * Reached the retry limit while attachments still appear restorable. Figure out whether there is anything left actively working on the
   * restore before deciding the restore is finished.
   */
  private fun handleRemainingAfterMaxAttempts(remainingAttachmentSize: Long) {
    synchronized(FINALIZE_LOCK) {
      val otherCheckJobs = AppDependencies.jobManager.find { it.factoryKey == KEY && it.id != id }
      if (otherCheckJobs.isNotEmpty()) {
        Log.w(TAG, "Max retries reached but ${otherCheckJobs.size} other check job(s) remain, deferring to them. queue: ${parameters.queue} estimated remaining: $remainingAttachmentSize")
        return
      }

      val orphanedCount = REDDatabase.attachments.markRestorableAttachmentsWithoutMessageAsFailed()
      if (orphanedCount > 0) {
        Log.w(TAG, "$orphanedCount orphaned restorable attachments marked failed")
      }
      val restoreQueues = AppDependencies.jobManager
        .find { it.factoryKey == RestoreAttachmentJob.KEY || it.factoryKey == RestoreLocalAttachmentJob.KEY }
        .mapNotNull { it.queueKey }
        .toSet()

      if (restoreQueues.isNotEmpty()) {
        Log.w(TAG, "Max retries reached but restore jobs remain in ${restoreQueues.size} queue(s), re-enqueueing check jobs. estimated remaining: $remainingAttachmentSize")
        AppDependencies.jobManager.addAll(restoreQueues.map { CheckRestoreMediaLeftJob(it) })
        return
      }

      Log.w(TAG, "Max retries reached and no restore jobs remain, treating restore as complete. estimated remaining: $remainingAttachmentSize")
      onMediaRestoreComplete()
    }
  }

  private fun onMediaRestoreComplete() {
    ArchiveRestoreProgress.allMediaRestored()
    BackupMediaRestoreService.stop(context)

    if (REDStore.backup.deletionState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      REDStore.backup.deletionState = DeletionState.MEDIA_DOWNLOAD_FINISHED
    }

    if (REDStore.backup.localRestoreReconcilePending) {
      if (REDStore.backup.backsUpMedia) {
        Log.i(TAG, "Local restore complete. Reconciling restored media against the archive CDN before uploading. (Flag cleared by the reconciliation job.)")
        ArchiveAttachmentReconciliationJob.enqueueReconcileFirstForLocalRestore()
      } else {
        REDStore.backup.localRestoreReconcilePending = false
      }
    }

    if (!REDStore.backup.backsUpMedia) {
      REDDatabase.attachments.markQuotesThatNeedReconstruction()
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<CheckRestoreMediaLeftJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CheckRestoreMediaLeftJob {
      return CheckRestoreMediaLeftJob(parameters)
    }
  }
}
