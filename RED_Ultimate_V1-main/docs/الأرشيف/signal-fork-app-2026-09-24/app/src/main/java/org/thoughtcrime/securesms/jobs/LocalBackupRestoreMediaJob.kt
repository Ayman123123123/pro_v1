/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.jobs

import android.net.Uri
import org.signal.core.util.logging.Log
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.backup.v2.local.ArchiveFileSystem
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.protos.LocalBackupRestoreMediaJobData
import com.red.sovereign.keyvalue.REDStore
import java.io.File

/**
 * Scans the local backup files directory and enqueues individual [RestoreLocalAttachmentJob]s for each restorable attachment.
 */
class LocalBackupRestoreMediaJob private constructor(
  parameters: Parameters,
  private val backupDirectoryUri: Uri
) : Job(parameters) {

  companion object {
    const val KEY = "LocalBackupRestoreMediaJob"
    private val TAG = Log.tag(LocalBackupRestoreMediaJob::class)

    fun create(backupDirectoryUri: Uri): LocalBackupRestoreMediaJob {
      return LocalBackupRestoreMediaJob(
        Parameters.Builder()
          .setLifespan(Parameters.IMMORTAL)
          .setMaxAttempts(1)
          .build(),
        backupDirectoryUri = backupDirectoryUri
      )
    }
  }

  override fun serialize(): ByteArray {
    return LocalBackupRestoreMediaJobData(
      backupDirectoryUri = backupDirectoryUri.toString()
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val archiveFileSystem = when (backupDirectoryUri.scheme) {
      "content" -> ArchiveFileSystem.openForRestore(context, backupDirectoryUri) ?: run {
        Log.w(TAG, "Unable to open backup directory: $backupDirectoryUri")
        REDStore.backup.localRestoreDirectoryError = true
        return Result.failure()
      }
      else -> ArchiveFileSystem.fromFile(context, File(backupDirectoryUri.path!!))
    }

    val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
    RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)
    return Result.success()
  }

  override fun onFailure() {
    ArchiveRestoreProgress.allMediaRestored()
    // forceUpdate in case restoreState was already NONE and allMediaRestored() skipped its update()
    ArchiveRestoreProgress.forceUpdate()
  }

  class Factory : Job.Factory<LocalBackupRestoreMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LocalBackupRestoreMediaJob {
      val data = LocalBackupRestoreMediaJobData.ADAPTER.decode(serializedData!!)
      return LocalBackupRestoreMediaJob(
        parameters = parameters,
        backupDirectoryUri = Uri.parse(data.backupDirectoryUri)
      )
    }
  }
}
