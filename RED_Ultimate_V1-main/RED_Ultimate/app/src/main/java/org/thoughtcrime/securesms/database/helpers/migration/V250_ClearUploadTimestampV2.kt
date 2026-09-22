/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Turns out we need to run [V247_ClearUploadTimestamp] again, because there was another situation where we had mismatching transit data across duplicates.
 */
@Suppress("ClassName")
object V250_ClearUploadTimestampV2 : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE attachment SET upload_timestamp = 1 WHERE upload_timestamp > 0")
  }
}
