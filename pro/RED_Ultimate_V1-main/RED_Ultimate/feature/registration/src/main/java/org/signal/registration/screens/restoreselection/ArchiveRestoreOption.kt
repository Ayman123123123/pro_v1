/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

/**
 * Restore options that may be presented on the archive restore selection screen.
 * The available options are determined by the [org.signal.registration.StorageController].
 */
enum class ArchiveRestoreOption {
  REDSecureBackup,
  LocalBackup,
  DeviceTransfer,
  None
}
