package com.red.sovereign.migrations

import com.red.sovereign.database.REDDatabase.Companion.recipients
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.TextSecurePreferences

/**
 * Added as a way to initialize the story viewed receipts setting.
 */
internal class StoryViewedReceiptsStateMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {
  companion object {
    const val KEY = "StoryViewedReceiptsStateMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!REDStore.story.isViewedReceiptsStateSet()) {
      REDStore.story.viewedReceiptsEnabled = TextSecurePreferences.isReadReceiptsEnabled(context)
      if (REDStore.account.isRegistered) {
        recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<StoryViewedReceiptsStateMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StoryViewedReceiptsStateMigrationJob {
      return StoryViewedReceiptsStateMigrationJob(parameters)
    }
  }
}
