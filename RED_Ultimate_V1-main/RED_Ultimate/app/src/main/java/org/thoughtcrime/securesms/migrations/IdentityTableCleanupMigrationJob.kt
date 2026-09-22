package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import com.red.sovereign.database.IdentityTable
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.AccountConsistencyWorkerJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient

/**
 * Migration to help cleanup some inconsistent state for ourself in the identity table.
 */
internal class IdentityTableCleanupMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "IdentityTableCleanupMigrationJob"

    val TAG = Log.tag(IdentityTableCleanupMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (REDStore.account.aci == null || REDStore.account.pni == null) {
      Log.i(TAG, "ACI/PNI are unset, skipping.")
      return
    }

    if (!REDStore.account.hasAciIdentityKey()) {
      Log.i(TAG, "No ACI identity set yet, skipping.")
      return
    }

    if (!REDStore.account.hasPniIdentityKey()) {
      Log.i(TAG, "No PNI identity set yet, skipping.")
      return
    }

    AppDependencies.protocolStore.aci().identities().saveIdentityWithoutSideEffects(
      Recipient.self().id,
      REDStore.account.aci!!,
      REDStore.account.aciIdentityKey.publicKey,
      IdentityTable.VerifiedStatus.VERIFIED,
      true,
      System.currentTimeMillis(),
      true
    )

    AppDependencies.protocolStore.pni().identities().saveIdentityWithoutSideEffects(
      Recipient.self().id,
      REDStore.account.pni!!,
      REDStore.account.pniIdentityKey.publicKey,
      IdentityTable.VerifiedStatus.VERIFIED,
      true,
      System.currentTimeMillis(),
      true
    )

    AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<IdentityTableCleanupMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): IdentityTableCleanupMigrationJob {
      return IdentityTableCleanupMigrationJob(parameters)
    }
  }
}
