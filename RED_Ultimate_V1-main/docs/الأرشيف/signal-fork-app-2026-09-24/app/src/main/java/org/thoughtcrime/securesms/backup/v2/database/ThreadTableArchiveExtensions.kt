/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.database

import com.red.sovereign.backup.v2.ExportState
import com.red.sovereign.backup.v2.exporters.ChatArchiveExporter
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable

fun ThreadTable.getThreadsForBackup(db: REDDatabase, exportState: ExportState, includeImageWallpapers: Boolean): ChatArchiveExporter {
  val notReleaseNoteClause = exportState.releaseNoteRecipientId?.let {
    "AND ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} != $it"
  } ?: ""

  //language=sql
  val query = """
      SELECT
        ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}, 
        ${ThreadTable.RECIPIENT_ID}, 
        ${ThreadTable.PINNED_ORDER}, 
        ${ThreadTable.READ}, 
        ${ThreadTable.ARCHIVED},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MENTION_SETTING}, 
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CHAT_COLORS},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CUSTOM_CHAT_COLORS_ID},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.WALLPAPER}
      FROM ${ThreadTable.TABLE_NAME}
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
      WHERE
        ${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE} NOT IN (${RecipientTable.RecipientType.DISTRIBUTION_LIST.id}, ${RecipientTable.RecipientType.CALL_LINK.id})
        $notReleaseNoteClause
    """
  val cursor = readableDatabase.query(query)

  return ChatArchiveExporter(cursor, db, exportState, includeImageWallpapers)
}
