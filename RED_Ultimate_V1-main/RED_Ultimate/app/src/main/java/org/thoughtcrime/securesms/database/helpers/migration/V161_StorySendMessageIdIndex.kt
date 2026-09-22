package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds an index to the story sends table to help with a new common query.
 */
@Suppress("ClassName")
object V161_StorySendMessageIdIndex : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS story_sends_message_id_distribution_id_index ON story_sends (message_id, distribution_id)")
  }
}
