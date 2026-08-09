/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.drafts

import org.signal.core.util.logging.Log
import com.red.sovereign.components.voice.VoiceNoteDraft
import com.red.sovereign.database.REDDatabase.Companion.drafts
import com.red.sovereign.dependencies.AppDependencies
import java.io.File

object DraftBlobs {

  private val TAG = Log.tag(DraftBlobs::class)

  fun deleteOrphanedDraftFiles(directory: File) {
    val files = directory.listFiles()

    if (files == null || files.size == 0) {
      Log.d(TAG, "No attachment drafts exist. Skipping.")
      return
    }

    val draftDatabase = drafts
    val voiceNoteDrafts = draftDatabase.getAllVoiceNoteDrafts()

    val draftFileNames = voiceNoteDrafts
      .asSequence()
      .map { VoiceNoteDraft.fromDraft(it) }
      .map(VoiceNoteDraft::uri)
      .mapNotNull { AppDependencies.blobs.buildFileName(it) }
      .toList()

    for (file in files) {
      if (!draftFileNames.contains(file.getName())) {
        if (file.delete()) {
          Log.d(TAG, "Deleted orphaned attachment draft: " + file.getName())
        } else {
          Log.d(TAG, "Failed to delete orphaned attachment draft: " + file.getName())
        }
      }
    }
  }
}
