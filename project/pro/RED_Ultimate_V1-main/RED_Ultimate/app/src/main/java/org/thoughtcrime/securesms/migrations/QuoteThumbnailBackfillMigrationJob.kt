/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.update
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.AttachmentTable.Companion.DATA_FILE
import com.red.sovereign.database.AttachmentTable.Companion.QUOTE
import com.red.sovereign.database.AttachmentTable.Companion.QUOTE_PENDING_TRANSCODE
import com.red.sovereign.database.AttachmentTable.Companion.TABLE_NAME
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.QuoteThumbnailBackfillJob
import com.red.sovereign.keyvalue.REDStore
import java.lang.Exception

/**
 * Kicks off the quote attachment thumbnail generation process by marking quote attachments
 * for processing and enqueueing a [QuoteThumbnailBackfillJob].
 */
internal class QuoteThumbnailBackfillMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(QuoteThumbnailBackfillMigrationJob::class.java)
    const val KEY = "QuoteThumbnailBackfillMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val markedCount = REDDatabase.attachments.migrationMarkQuoteAttachmentsForThumbnailProcessing()
    REDStore.misc.startedQuoteThumbnailMigration = true

    Log.i(TAG, "Marked $markedCount quote attachments for thumbnail processing")

    if (markedCount > 0) {
      AppDependencies.jobManager.add(QuoteThumbnailBackfillJob())
    } else {
      Log.i(TAG, "No quote attachments to process.")
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun AttachmentTable.migrationMarkQuoteAttachmentsForThumbnailProcessing(): Int {
    return writableDatabase
      .update(TABLE_NAME)
      .values(QUOTE to QUOTE_PENDING_TRANSCODE)
      .where("$QUOTE != 0 AND $DATA_FILE NOT NULL")
      .run()
  }

  class Factory : Job.Factory<QuoteThumbnailBackfillMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): QuoteThumbnailBackfillMigrationJob {
      return QuoteThumbnailBackfillMigrationJob(parameters)
    }
  }
}
