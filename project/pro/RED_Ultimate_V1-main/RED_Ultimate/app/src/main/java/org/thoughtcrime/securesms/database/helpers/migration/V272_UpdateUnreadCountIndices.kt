/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Updates a partial index for some performance-critical queries around unread counts.
 */
@Suppress("ClassName")
object V272_UpdateUnreadCountIndices : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX message_thread_unread_count_index")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_thread_unread_count_index ON message (thread_id) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date = -1 AND original_message_id IS NULL AND read = 0")
  }
}
