package com.red.sovereign.migrations

import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.MultiDeviceKeysUpdateJob
import com.red.sovereign.keyvalue.REDStore

/**
 * Migration to sync keys with linked devices.
 */
internal class SyncKeysMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SyncKeysMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (REDStore.account.isLinkedDevice) {
      return
    }

    if (REDStore.account.isMultiDevice) {
      AppDependencies.jobManager.add(MultiDeviceKeysUpdateJob())
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SyncKeysMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncKeysMigrationJob {
      return SyncKeysMigrationJob(parameters)
    }
  }
}
