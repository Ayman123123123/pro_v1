/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore

class CancelRestoreMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(CancelRestoreMediaJob::class)
    const val KEY = "CancelRestoreMediaJob"

    fun enqueue() {
      AppDependencies.jobManager.add(
        CancelRestoreMediaJob(parameters = Parameters.Builder().build())
      )
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    REDStore.backup.userManuallySkippedMediaRestore = true

    ArchiveRestoreProgress.onCancelMediaRestore()

    Log.i(TAG, "Canceling all media restore jobs")
    RestoreAttachmentJob.Queues.ALL.forEach { AppDependencies.jobManager.cancelAllInQueue(it) }

    Log.i(TAG, "Enqueueing check restore media jobs to cleanup")
    RestoreAttachmentJob.Queues.ALL.forEach { AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(it)) }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<CancelRestoreMediaJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CancelRestoreMediaJob {
      return CancelRestoreMediaJob(parameters = parameters)
    }
  }
}
