/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign

import android.net.Uri
import com.red.sovereign.backup.v2.local.ArchiveFileSystem
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore

object PluginCache {
  private var archiveFileSystem: ArchiveFileSystem? = null
  var localBackups: ApiPlugin.LocalBackups? = null

  fun getArchiveFileSystem(): ArchiveFileSystem? {
    if (archiveFileSystem == null) {
      val backupDirectoryUri = REDStore.backup.newLocalBackupsDirectory?.let { Uri.parse(it) }
      if (backupDirectoryUri == null || backupDirectoryUri.path == null) {
        return null
      }

      archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, backupDirectoryUri)
    }
    return archiveFileSystem
  }

  fun clearBackupCache() {
    archiveFileSystem = null
    localBackups = null
  }
}
