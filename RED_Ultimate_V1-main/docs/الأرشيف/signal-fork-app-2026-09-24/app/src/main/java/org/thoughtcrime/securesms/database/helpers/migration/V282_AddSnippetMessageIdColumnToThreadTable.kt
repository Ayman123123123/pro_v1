/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * In order to make sure the snippet URI is not overwritten by the wrong message attachment, we want to
 * track the snippet message id in the thread table.
 */
@Suppress("ClassName")
object V282_AddSnippetMessageIdColumnToThreadTable : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE thread ADD COLUMN snippet_message_id INTEGER DEFAULT 0")
  }
}
