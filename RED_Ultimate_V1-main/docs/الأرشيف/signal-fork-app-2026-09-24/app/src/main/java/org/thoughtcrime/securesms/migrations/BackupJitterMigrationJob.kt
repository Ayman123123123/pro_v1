/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.SettingsValues
import com.red.sovereign.keyvalue.REDStore
import java.util.Random

internal class BackupJitterMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    const val KEY = "BackupJitterMigrationJob"
    val TAG = Log.tag(BackupJitterMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val hour = REDStore.settings.backupHour
    val minute = REDStore.settings.backupMinute
    if (hour == SettingsValues.BACKUP_DEFAULT_HOUR && minute == SettingsValues.BACKUP_DEFAULT_MINUTE) {
      val rand = Random()
      val newHour = rand.nextInt(3) + 1 // between 1AM - 3AM
      val newMinute = rand.nextInt(12) * 5 // 5 minute intervals up to +55 minutes
      REDStore.settings.setBackupSchedule(newHour, newMinute)
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackupJitterMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupJitterMigrationJob {
      return BackupJitterMigrationJob(parameters)
    }
  }
}
