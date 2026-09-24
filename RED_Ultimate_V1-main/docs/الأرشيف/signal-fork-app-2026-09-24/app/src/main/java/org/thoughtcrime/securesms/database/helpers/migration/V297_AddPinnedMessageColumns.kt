package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds the columns and indexes necessary for pinned messages
 */
@Suppress("ClassName")
object V297_AddPinnedMessageColumns : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message ADD COLUMN pinned_until INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE message ADD COLUMN pinning_message_id INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE message ADD COLUMN pinned_at INTEGER DEFAULT 0")

    db.execSQL("CREATE INDEX message_pinned_until_index ON message (pinned_until)")
    db.execSQL("CREATE INDEX message_pinned_at_index ON message (pinned_at)")
  }
}
