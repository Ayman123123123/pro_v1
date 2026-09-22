/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Copy data_hash_start to data_hash_end for sticker attachments that have completed transfer.
 */
@Suppress("ClassName")
object V288_CopyStickerDataHashStartToEnd : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      "UPDATE attachment SET data_hash_end = data_hash_start WHERE sticker_pack_id IS NOT NULL AND data_hash_start IS NOT NULL AND data_hash_end IS NULL AND transfer_state = 0"
    )
  }
}
