package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

@Suppress("ClassName")
object V316_AddVerifiedGroupNameHashMigration : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE groups ADD COLUMN verified_name_hash BLOB DEFAULT NULL")
  }
}
