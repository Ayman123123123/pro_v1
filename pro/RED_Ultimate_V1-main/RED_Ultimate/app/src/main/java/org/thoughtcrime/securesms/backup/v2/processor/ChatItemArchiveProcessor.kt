/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.processor

import org.signal.archive.proto.ChatItem
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupFrameEmitter
import org.signal.core.util.logging.Log
import com.red.sovereign.backup.v2.ExportState
import com.red.sovereign.backup.v2.ImportState
import com.red.sovereign.backup.v2.database.createChatItemInserter
import com.red.sovereign.backup.v2.database.getMessagesForBackup
import com.red.sovereign.backup.v2.importer.ChatItemArchiveImporter
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.RecipientId

/**
 * Handles importing/exporting [ChatItem] frames for an archive.
 */
object ChatItemArchiveProcessor {
  val TAG = Log.tag(ChatItemArchiveProcessor::class.java)

  fun export(db: REDDatabase, exportState: ExportState, selfRecipientId: RecipientId, messageInclusionCutoffTime: Long, cancellationRED: () -> Boolean, emitter: BackupFrameEmitter) {
    db.messageTable.getMessagesForBackup(db, exportState.backupTime, selfRecipientId, messageInclusionCutoffTime, exportState).use { chatItems ->
      var count = 0
      while (chatItems.hasNext()) {
        if (count % 1000 == 0 && cancellationRED()) {
          return@use
        }

        val chatItem: ChatItem? = chatItems.next()
        if (chatItem != null) {
          if (exportState.threadIds.contains(chatItem.chatId)) {
            emitter.emit(Frame(chatItem = chatItem))
          }
        }
        count++
      }
    }
  }

  fun beginImport(importState: ImportState): ChatItemArchiveImporter {
    return REDDatabase.messages.createChatItemInserter(importState)
  }
}
