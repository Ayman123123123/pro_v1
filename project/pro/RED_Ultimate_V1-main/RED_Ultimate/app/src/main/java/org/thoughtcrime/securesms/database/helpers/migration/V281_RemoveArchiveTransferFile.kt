/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * We used to decrypt archive files in two distinct steps, and therefore needed a secondary transfer file.
 * Now, we're able to do all of the decrypt in one stream, so we no longer need the intermediary transfer file.
 */
object V281_RemoveArchiveTransferFile : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_transfer_file")
  }
}
