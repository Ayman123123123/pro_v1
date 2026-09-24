/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.components.settings.app.backups.local

import com.red.sovereign.keyvalue.protos.LocalBackupCreationProgress

/**
 * Immutable state for the on-device backups settings screen.
 *
 * This is intended to be the single source of truth for UI rendering (i.e. a single `StateFlow`
 * emission fully describes what the screen should display).
 */
data class LocalBackupsSettingsState(
  val backupsEnabled: Boolean = false,
  val canTurnOn: Boolean = true,
  val optimizeStorageEnabled: Boolean = false,
  val lastBackupLabel: String? = null,
  val folderDisplayName: String? = null,
  val scheduleTimeLabel: String? = null,
  val progress: LocalBackupCreationProgress = LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle()),
  val isDeleting: Boolean = false,
  val deleteCompleted: Int = 0,
  val deleteTotal: Int = 0
)
