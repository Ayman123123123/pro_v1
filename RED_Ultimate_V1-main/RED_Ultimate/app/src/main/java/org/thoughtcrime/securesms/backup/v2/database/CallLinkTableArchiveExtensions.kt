/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.database

import org.signal.core.util.select
import com.red.sovereign.database.CallLinkTable

fun CallLinkTable.getCallLinksForBackup(): CallLinkArchiveExporter {
  val cursor = readableDatabase
    .select()
    .from(CallLinkTable.TABLE_NAME)
    .where("${CallLinkTable.ROOT_KEY} NOT NULL")
    .run()

  return CallLinkArchiveExporter(cursor)
}
