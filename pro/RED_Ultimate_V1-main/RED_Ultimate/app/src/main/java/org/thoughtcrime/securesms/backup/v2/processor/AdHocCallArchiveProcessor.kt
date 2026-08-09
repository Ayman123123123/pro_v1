/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.processor

import org.signal.archive.proto.AdHocCall
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupFrameEmitter
import org.signal.core.util.logging.Log
import com.red.sovereign.backup.v2.ExportSkips
import com.red.sovereign.backup.v2.ExportState
import com.red.sovereign.backup.v2.ImportState
import com.red.sovereign.backup.v2.database.getAdhocCallsForBackup
import com.red.sovereign.backup.v2.importer.AdHodCallArchiveImporter
import com.red.sovereign.database.REDDatabase

/**
 * Handles importing/exporting [AdHocCall] frames for an archive.
 */
object AdHocCallArchiveProcessor {

  val TAG = Log.tag(AdHocCallArchiveProcessor::class.java)

  fun export(db: REDDatabase, exportState: ExportState, emitter: BackupFrameEmitter) {
    db.callTable.getAdhocCallsForBackup().use { reader ->
      for (callLog in reader) {
        if (exportState.recipientIds.contains(callLog.recipientId)) {
          emitter.emit(Frame(adHocCall = callLog))
        } else {
          Log.w(TAG, ExportSkips.callWithMissingRecipient(callLog.callTimestamp))
        }
      }
    }
  }

  fun import(call: AdHocCall, importState: ImportState) {
    AdHodCallArchiveImporter.import(call, importState)
  }
}
