package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

@Suppress("ClassName")
object V314_FixMessageRequestAcceptedToRecipient : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE message
      SET to_recipient_id = (SELECT thread.recipient_id FROM thread WHERE thread._id = message.thread_id)
      WHERE (type & 0xF00000000) = 0x600000000
        AND to_recipient_id = from_recipient_id
        AND thread_id IN (SELECT _id FROM thread)
      """
    )
  }
}
