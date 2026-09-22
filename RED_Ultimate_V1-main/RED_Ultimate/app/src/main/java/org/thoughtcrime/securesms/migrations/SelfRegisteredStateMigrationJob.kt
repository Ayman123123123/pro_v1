package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient

/**
 * There was a bug where some users had their own recipient entry marked unregistered. This fixes that.
 */
internal class SelfRegisteredStateMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SelfRegisteredStateMigrationJob"

    val TAG = Log.tag(SelfRegisteredStateMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (REDStore.account.isRegistered && REDStore.account.aci != null) {
      val record = REDDatabase.recipients.getRecord(Recipient.self().id)

      if (record.registered != RecipientTable.RegisteredState.REGISTERED) {
        Log.w(TAG, "Inconsistent registered state! Fixing...")
        REDDatabase.recipients.markRegistered(Recipient.self().id, REDStore.account.aci!!)
      } else {
        Log.d(TAG, "Local user is already registered.")
      }
    } else {
      Log.d(TAG, "Not registered. Skipping.")
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SelfRegisteredStateMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SelfRegisteredStateMigrationJob {
      return SelfRegisteredStateMigrationJob(parameters)
    }
  }
}
