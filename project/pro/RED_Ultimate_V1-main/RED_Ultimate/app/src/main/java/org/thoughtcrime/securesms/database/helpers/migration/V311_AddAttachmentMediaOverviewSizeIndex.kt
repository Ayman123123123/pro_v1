package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Adds a partial index on attachment (data_size, display_order) to speed up
 * the "all threads" media overview sorted by largest.
 */
@Suppress("ClassName")
object V311_AddAttachmentMediaOverviewSizeIndex : REDDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_media_overview_size ON attachment (data_size DESC, display_order DESC) WHERE quote = 0 AND sticker_pack_id IS NULL AND data_file IS NOT NULL")
  }
}
