package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds support for storing the systemNickname from storage service.
 */
@Suppress("ClassName")
object V180_RecipientNicknameMigration : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN system_nickname TEXT DEFAULT NULL")
  }
}
