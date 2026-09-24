package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

@Suppress("ClassName")
object V321_AddScheduledMessageIndex : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS message_scheduled_non_story_index ON message (scheduled_date) WHERE story_type = 0 AND parent_story_id <= 0 AND scheduled_date != -1")
  }
}
