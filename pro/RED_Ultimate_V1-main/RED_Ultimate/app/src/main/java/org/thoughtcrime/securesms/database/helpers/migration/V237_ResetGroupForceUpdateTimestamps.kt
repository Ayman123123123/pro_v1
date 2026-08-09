/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Reset last forced update timestamp for groups to fix a local group state bug.
 */
@Suppress("ClassName")
object V237_ResetGroupForceUpdateTimestamps : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE groups SET last_force_update_timestamp = 0")
  }
}
