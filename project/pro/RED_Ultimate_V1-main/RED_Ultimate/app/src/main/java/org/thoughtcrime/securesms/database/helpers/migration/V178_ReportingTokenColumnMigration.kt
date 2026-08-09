package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * This adds a column to the Recipients table to store a spam reporting token.
 */
@Suppress("ClassName")
object V178_ReportingTokenColumnMigration : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE recipient ADD COLUMN reporting_token BLOB DEFAULT NULL")
  }
}
