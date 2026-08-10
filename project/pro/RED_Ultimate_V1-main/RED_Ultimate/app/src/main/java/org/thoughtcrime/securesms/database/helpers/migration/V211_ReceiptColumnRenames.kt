/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 */
@Suppress("ClassName")
object V211_ReceiptColumnRenames : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE message RENAME COLUMN delivery_receipt_count TO has_delivery_receipt")
    db.execSQL("ALTER TABLE message RENAME COLUMN read_receipt_count TO has_read_receipt")
    db.execSQL("ALTER TABLE message RENAME COLUMN viewed_receipt_count TO viewed")

    db.execSQL("ALTER TABLE thread RENAME COLUMN delivery_receipt_count TO has_delivery_receipt")
    db.execSQL("ALTER TABLE thread RENAME COLUMN read_receipt_count TO has_read_receipt")
  }
}
