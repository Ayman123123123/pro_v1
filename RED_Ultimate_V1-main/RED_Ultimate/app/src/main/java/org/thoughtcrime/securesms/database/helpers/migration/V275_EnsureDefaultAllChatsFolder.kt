/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import org.signal.core.util.Base64
import com.red.sovereign.database.SQLiteDatabase
import com.red.sovereign.storage.StorageSyncHelper
import java.util.UUID

/**
 * Ensures that there is a default 'All chat' within chat folders.
 */
object V275_EnsureDefaultAllChatsFolder : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      INSERT INTO chat_folder(position, folder_type, show_individual, show_groups, show_muted, chat_folder_id, storage_service_id)
      SELECT '0', '0', '1', '1', '1', '${UUID.randomUUID()}', '${Base64.encodeWithPadding(StorageSyncHelper.generateKey())}'
      WHERE NOT EXISTS (
        SELECT 1
          FROM chat_folder
          WHERE folder_type = 0
          LIMIT 1
      );
      """.trimIndent()
    )
  }
}
