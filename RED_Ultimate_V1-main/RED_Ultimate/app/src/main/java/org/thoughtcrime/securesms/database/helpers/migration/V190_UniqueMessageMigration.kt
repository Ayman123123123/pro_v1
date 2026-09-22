package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * This migration used to do what [V191_UniqueMessageMigrationV2] does. However, due to bugs, the migration was abandoned.
 * We now re-do the migration in V191.
 */
@Suppress("ClassName")
object V190_UniqueMessageMigration : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
