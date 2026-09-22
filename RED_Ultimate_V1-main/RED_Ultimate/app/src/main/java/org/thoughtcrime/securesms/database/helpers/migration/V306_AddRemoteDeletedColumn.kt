package com.red.sovereign.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import com.red.sovereign.database.SQLiteDatabase

/**
 * Because of an OOM in [V302_AddDeletedByColumn], we could not drop the remote_deleted column for everyone.
 * This adds it back for the people who dropped it.
 */
@Suppress("ClassName")
object V306_AddRemoteDeletedColumn : REDDatabaseMigration {

  private val TAG = Log.tag(V306_AddRemoteDeletedColumn::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqlUtil.columnExists(db, "message", "remote_deleted")) {
      Log.i(TAG, "Already have remote deleted column!")
      return
    }

    db.execSQL("ALTER TABLE message ADD COLUMN remote_deleted INTEGER DEFAULT 0")
  }
}
