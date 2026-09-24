/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.database.Cursor
import com.squareup.wire.ProtoAdapter
import org.signal.core.util.requireBlob
import org.signal.core.util.requireString
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.keyvalue.BackupValues
import com.red.sovereign.keyvalue.RegistrationValues
import com.red.sovereign.keyvalue.protos.ArchiveUploadProgressState

/**
 * Transform non-user friendly store values into less-non-user friendly representations.
 */
object REDStoreTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == KeyValueDatabase.VALUE
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    return when (cursor.requireString(KeyValueDatabase.KEY)) {
      RegistrationValues.RESTORE_DECISION_STATE -> decodeProto(cursor, RestoreDecisionState.ADAPTER)
      BackupValues.KEY_ARCHIVE_UPLOAD_STATE -> decodeProto(cursor, ArchiveUploadProgressState.ADAPTER)
      else -> DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }
  }

  private fun decodeProto(cursor: Cursor, adapter: ProtoAdapter<*>): String? {
    return cursor.requireBlob(KeyValueDatabase.VALUE)?.let { adapter.decode(it) }?.toString()
  }
}
