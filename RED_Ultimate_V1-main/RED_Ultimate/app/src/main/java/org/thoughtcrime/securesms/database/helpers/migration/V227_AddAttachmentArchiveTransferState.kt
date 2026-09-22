/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds a new column to track the status of transferring attachments to the archive service.
 */
object V227_AddAttachmentArchiveTransferState : REDDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN archive_transfer_state INTEGER DEFAULT 0")
  }
}
