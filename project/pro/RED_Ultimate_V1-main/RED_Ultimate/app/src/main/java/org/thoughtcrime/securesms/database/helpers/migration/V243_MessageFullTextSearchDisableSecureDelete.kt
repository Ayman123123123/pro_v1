/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase
import com.red.sovereign.database.helpers.migration.V240_MessageFullTextSearchSecureDelete.FTS_TABLE_NAME

/**
 * This undoes [V240_MessageFullTextSearchSecureDelete] by disabling secure-delete on our FTS table.
 * Unfortunately the performance overhead was too high. Thankfully, our old approach, while more
 * manual, provides the same safety guarantees, while also allowing us to optimize bulk deletes.
 */
object V243_MessageFullTextSearchDisableSecureDelete : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("""INSERT INTO $FTS_TABLE_NAME ($FTS_TABLE_NAME, rank) VALUES('secure-delete', 0);""")
  }
}
