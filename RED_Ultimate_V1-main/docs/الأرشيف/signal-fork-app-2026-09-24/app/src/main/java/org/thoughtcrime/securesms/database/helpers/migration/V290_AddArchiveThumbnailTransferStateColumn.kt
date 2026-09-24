/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * We need to keep track of a transfer state for thumbnails too.
 */
@Suppress("ClassName")
object V290_AddArchiveThumbnailTransferStateColumn : REDDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_thumbnail_transfer_state INTEGER DEFAULT 0;")
  }
}
