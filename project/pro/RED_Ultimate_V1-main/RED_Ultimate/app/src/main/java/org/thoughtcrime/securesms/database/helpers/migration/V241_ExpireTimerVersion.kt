/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Migration for new field tracking expiration timer version.
 */
object V241_ExpireTimerVersion : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN expire_timer_version INTEGER DEFAULT 1 NOT NULL;")
    db.execSQL("ALTER TABLE recipient ADD COLUMN message_expiration_time_version INTEGER DEFAULT 1 NOT NULL;")
    db.execSQL(
      """
      UPDATE recipient
      SET message_expiration_time_version = 2
      WHERE message_expiration_time > 0
      """
    )
  }
}
