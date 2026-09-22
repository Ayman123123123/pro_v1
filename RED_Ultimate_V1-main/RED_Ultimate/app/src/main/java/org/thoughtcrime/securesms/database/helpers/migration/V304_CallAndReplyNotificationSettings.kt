package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds per-conversation notification settings for calls and replies when muted.
 */
@Suppress("ClassName")
object V304_CallAndReplyNotificationSettings : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN call_notification_setting INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE recipient ADD COLUMN reply_notification_setting INTEGER DEFAULT 0")
  }
}
