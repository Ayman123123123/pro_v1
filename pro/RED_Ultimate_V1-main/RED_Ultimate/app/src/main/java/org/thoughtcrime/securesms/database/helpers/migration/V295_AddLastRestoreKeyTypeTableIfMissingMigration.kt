/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Creates the LastResortKeyTuple table if missed getting created on backup or device transfer import.
 */
@Suppress("ClassName")
object V295_AddLastRestoreKeyTypeTableIfMissingMigration : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS last_resort_key_tuple (
        _id INTEGER PRIMARY KEY,
        kyber_prekey_id INTEGER NOT NULL REFERENCES kyber_prekey (_id) ON DELETE CASCADE,
        signed_key_id INTEGER NOT NULL,
        public_key BLOB NOT NULL,
        UNIQUE(kyber_prekey_id, signed_key_id, public_key)
      )
      """
    )
  }
}
