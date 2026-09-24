package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.jobs.RefreshOwnProfileJob
import com.red.sovereign.keyvalue.REDStore

/**
 * Reuploads user's attributes followed by a download of their profile and a reset of their KT data
 */
internal class KeyTransparencyUsernameMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "KeyTransparencyUsernameMigrationJob"

    private val TAG: String = tag(KeyTransparencyUsernameMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    Log.i(TAG, "Resetting KT data and refreshing attributes")
    REDStore.account.distinguishedHead = null
    REDStore.misc.nextKeyTransparencyTime = 0
    REDDatabase.recipients.clearAllKeyTransparencyData()

    AppDependencies.jobManager.startChain(RefreshAttributesJob())
      .then(RefreshOwnProfileJob())
      .enqueue()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<KeyTransparencyUsernameMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): KeyTransparencyUsernameMigrationJob {
      return KeyTransparencyUsernameMigrationJob(parameters)
    }
  }
}
