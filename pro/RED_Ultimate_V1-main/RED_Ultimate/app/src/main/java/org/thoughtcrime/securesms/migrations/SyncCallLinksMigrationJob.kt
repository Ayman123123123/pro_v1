/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper

/**
 * Marks all call links as needing to be synced by storage service.
 */
internal class SyncCallLinksMigrationJob @JvmOverloads constructor(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SyncCallLinksMigrationJob"

    private val TAG = Log.tag(SyncCallLinksMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (REDStore.account.aci == null) {
      Log.w(TAG, "Self not available yet.")
      return
    }

    val callLinkRecipients = REDDatabase.callLinks.getAll().map { it.recipientId }.filter {
      try {
        Recipient.resolved(it)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Unable to resolve recipient: $it")
        false
      }
    }

    REDDatabase.recipients.markNeedsSync(callLinkRecipients)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SyncCallLinksMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncCallLinksMigrationJob {
      return SyncCallLinksMigrationJob(parameters)
    }
  }
}
