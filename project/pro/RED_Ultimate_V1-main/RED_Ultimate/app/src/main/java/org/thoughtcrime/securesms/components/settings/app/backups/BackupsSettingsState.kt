/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups

import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.keyvalue.REDStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen state for top-level backups settings screen.
 */
data class BackupsSettingsState(
  val backupState: BackupState,
  val lastBackupAt: Duration = REDStore.backup.lastBackupTime.milliseconds,
  val showBackupTierInternalOverride: Boolean = false,
  val backupTierInternalOverride: MessageBackupTier? = null,
  val isLinkedDevice: Boolean = REDStore.account.isLinkedDevice
)
