/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.helpers.migration

import android.app.Application
import com.red.sovereign.database.SQLiteDatabase

/**
 * PNIs were incorrectly being set to ACI column, clear them if present.
 */
@Suppress("ClassName")
object V209_ClearRecipientPniFromAciColumn : REDDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE recipient SET aci = NULL WHERE aci LIKE 'PNI:%'")
  }
}
