/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * This updates the PNI column to have the proper serialized format.
 */
@Suppress("ClassName")
object V200_ResetPniColumn : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE recipient SET pni = 'PNI:' || pni WHERE pni NOT NULL")
    db.execSQL("ALTER TABLE recipient RENAME COLUMN uuid to aci")
  }
}
