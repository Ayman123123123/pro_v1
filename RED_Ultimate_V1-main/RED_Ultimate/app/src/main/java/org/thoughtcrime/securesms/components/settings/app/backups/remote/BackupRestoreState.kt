/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups.remote

import com.red.sovereign.backup.v2.ArchiveRestoreProgressState

/**
 * State container for BackupStatusData, including the enabled state.
 */
sealed interface BackupRestoreState {
  data object None : BackupRestoreState
  data class Ready(val bytes: String) : BackupRestoreState
  data class Restoring(val state: ArchiveRestoreProgressState) : BackupRestoreState
}
