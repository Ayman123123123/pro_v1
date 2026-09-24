/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * We were unnecessarily holding on to some attachment download data for viewed view-once messages that we don't need to hold onto.
 */
object V283_ViewOnceRemoteDataCleanup : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE 
        attachment
      SET 
        remote_key = NULL,
        remote_digest = NULL,
        remote_incremental_digest = NULL,
        remote_incremental_digest_chunk_size = 0,
        thumbnail_file = NULL,
        thumbnail_random = NULL,
        archive_transfer_state = 0
      WHERE 
        data_file IS NULL AND
        content_type = 'application/x-signal-view-once'
      """
    )
  }
}
