/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Ensure archive_transfer_state is clear if an attachment is missing a remote_key.
 */
@Suppress("ClassName")
object V287_FixInvalidArchiveState : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE attachment SET archive_cdn = null, archive_transfer_state = 0 WHERE remote_key IS NULL AND archive_transfer_state = 3")
  }
}
