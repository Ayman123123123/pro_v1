package com.red.sovereign.migrations

import com.red.sovereign.database.REDDatabase
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.CheckKeyTransparencyJob
import com.red.sovereign.keyvalue.REDStore

/**
 * Clears all existing key transparency data
 */
internal class ResetKeyTransparencyMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "ResetKeyTransparencyMigrationJob"
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    REDStore.account.distinguishedHead = null
    REDStore.misc.nextKeyTransparencyTime = 0
    REDDatabase.recipients.clearAllKeyTransparencyData()
    CheckKeyTransparencyJob.enqueueIfNecessary(addDelay = false)
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ResetKeyTransparencyMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ResetKeyTransparencyMigrationJob {
      return ResetKeyTransparencyMigrationJob(parameters)
    }
  }
}
