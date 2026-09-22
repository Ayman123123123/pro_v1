package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.AccountConsistencyWorkerJob
import com.red.sovereign.keyvalue.REDStore

/**
 * Migration to help address some account consistency issues that resulted under very specific situation post-device-transfer.
 */
internal class AccountConsistencyMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "AccountConsistencyMigrationJob"

    val TAG = Log.tag(AccountConsistencyMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!REDStore.account.hasAciIdentityKey()) {
      Log.i(TAG, "No identity set yet, skipping.")
      return
    }

    if (!REDStore.account.isRegistered || REDStore.account.aci == null) {
      Log.i(TAG, "Not yet registered, skipping.")
      return
    }

    AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<AccountConsistencyMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AccountConsistencyMigrationJob {
      return AccountConsistencyMigrationJob(parameters)
    }
  }
}
