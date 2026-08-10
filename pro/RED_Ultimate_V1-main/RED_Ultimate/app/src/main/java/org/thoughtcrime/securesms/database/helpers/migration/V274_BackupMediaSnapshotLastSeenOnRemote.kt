/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Added a column to the backup media snapshot table to keep track of the last time we saw an object on the CDN.
 */
object V274_BackupMediaSnapshotLastSeenOnRemote : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE backup_media_snapshot ADD COLUMN last_seen_on_remote_timestamp INTEGER DEFAULT 0")
  }
}
