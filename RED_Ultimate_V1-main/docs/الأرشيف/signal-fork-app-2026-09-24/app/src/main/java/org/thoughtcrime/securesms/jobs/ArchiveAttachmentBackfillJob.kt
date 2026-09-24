/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.backup.ArchiveUploadProgress
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import kotlin.time.Duration.Companion.days

/**
 * When run, this will find all of the attachments that need to be uploaded to the archive tier and enqueue [UploadAttachmentToArchiveJob]s for them.
 */
class ArchiveAttachmentBackfillJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    private val TAG = Log.tag(ArchiveAttachmentBackfillJob::class.java)

    const val KEY = "ArchiveAttachmentBackfillJob"
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue(ArchiveCommitAttachmentDeletesJob.ARCHIVE_ATTACHMENT_QUEUE)
      .setMaxInstancesForQueue(2)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!REDStore.backup.backsUpMedia) {
      Log.w(TAG, "This user doesn't back up media! Skipping. Tier: ${REDStore.backup.backupTier}")
      return Result.success()
    }

    REDDatabase.attachments.createRemoteKeyForAttachmentsThatNeedArchiveUpload()

    val jobs = REDDatabase.attachments.getAttachmentsThatNeedArchiveUpload()
      .map { attachmentId -> UploadAttachmentToArchiveJob(attachmentId) }

    ArchiveUploadProgress.onAttachmentSectionStarted(REDDatabase.attachments.getPendingArchiveUploadBytes())

    if (!isCanceled) {
      Log.i(TAG, "Adding ${jobs.size} jobs to backfill attachments.", true)
      AppDependencies.jobManager.addAll(jobs)
    } else {
      Log.w(TAG, "Job was canceled. Not enqueuing backfill.", true)
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ArchiveAttachmentBackfillJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveAttachmentBackfillJob {
      return ArchiveAttachmentBackfillJob(parameters)
    }
  }
}
