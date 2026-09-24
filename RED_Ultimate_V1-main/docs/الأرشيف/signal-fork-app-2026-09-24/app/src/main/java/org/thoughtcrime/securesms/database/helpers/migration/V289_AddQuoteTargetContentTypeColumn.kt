/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds the quote_target_content_type column to attachments and migrates existing quote attachments
 * to populate this field with their current content_type.
 */
@Suppress("ClassName")
object V289_AddQuoteTargetContentTypeColumn : REDDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN quote_target_content_type TEXT DEFAULT NULL;")
    db.execSQL("UPDATE attachment SET quote_target_content_type = content_type WHERE quote != 0;")
  }
}
