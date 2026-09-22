/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds a deletion timestamp to the call links table, which is required for storage service syncing.
 */
object V245_DeletionTimestampOnCallLinks : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE call_link ADD COLUMN deletion_timestamp INTEGER DEFAULT 0 NOT NULL;")
  }
}
