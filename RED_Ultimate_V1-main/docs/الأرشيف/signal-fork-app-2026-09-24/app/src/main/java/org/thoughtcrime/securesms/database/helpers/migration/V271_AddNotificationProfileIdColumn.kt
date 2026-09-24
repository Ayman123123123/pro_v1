/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import com.red.sovereign.database.SQLiteDatabase
import java.util.UUID

/**
 * Add notification_profile_id column to Notification Profiles to support backups.
 */
@Suppress("ClassName")
object V271_AddNotificationProfileIdColumn : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE notification_profile ADD COLUMN notification_profile_id TEXT DEFAULT NULL")

    db.rawQuery("SELECT _id FROM notification_profile")
      .readToList { it.requireLong("_id") }
      .forEach { id ->
        db.execSQL("UPDATE notification_profile SET notification_profile_id = '${UUID.randomUUID()}' WHERE _id = $id")
      }
  }
}
