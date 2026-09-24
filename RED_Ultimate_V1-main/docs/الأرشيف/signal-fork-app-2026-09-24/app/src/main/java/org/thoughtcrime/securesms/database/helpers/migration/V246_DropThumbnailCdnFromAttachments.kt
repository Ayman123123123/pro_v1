/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * Thumbnails are best effort and assumed to have the same CDN as the full attachment, there is no need to store it in the database.
 */
@Suppress("ClassName")
object V246_DropThumbnailCdnFromAttachments : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment DROP COLUMN archive_thumbnail_cdn")
  }
}
