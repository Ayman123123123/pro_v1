/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Sets the 'secure-delete' flag on the message_fts table.
 * https://www.sqlite.org/fts5.html#the_secure_delete_configuration_option
 */
@Suppress("ClassName")
object V240_MessageFullTextSearchSecureDelete : REDDatabaseMigration {

  const val FTS_TABLE_NAME = "message_fts"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("""INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) VALUES('secure-delete', 1);""")
  }
}
